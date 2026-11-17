from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass
from typing import Any

from kafka import KafkaConsumer, KafkaProducer, TopicPartition
from kafka.errors import NoBrokersAvailable

from fraud_detection.application.scoring import FraudScoringService
from fraud_detection.config import Settings
from fraud_detection.domain import ScoringRequest

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class IncomingMessage:
  key: bytes | None
  value: dict[str, Any]
  topic: str
  partition: int
  offset: int


class KafkaScoringWorker:
  def __init__(self, settings: Settings, scoring_service: FraudScoringService) -> None:
    self._settings = settings
    self._scoring_service = scoring_service

  def run_forever(self) -> None:
    while True:
      try:
        cancellations = ScoringCancellationRegistry(
          self._settings.kafka_bootstrap_servers,
          self._settings.cancellations_topic,
        )
        break
      except NoBrokersAvailable:
        logger.warning("Kafka is unavailable; retrying in %.1f seconds",
                       self._settings.kafka_retry_seconds)
        time.sleep(self._settings.kafka_retry_seconds)
    consumer = self._connect_consumer()
    producer = self._connect_producer()
    logger.info("Fraud scoring worker listening to %s", self._settings.input_topic)
    for raw_message in consumer:
      cancellations.refresh()
      message = IncomingMessage(
        key=raw_message.key,
        value=raw_message.value,
        topic=raw_message.topic,
        partition=raw_message.partition,
        offset=raw_message.offset,
      )
      transaction_id = message.value.get("transactionId") if isinstance(message.value, dict) else None
      if cancellations.contains(transaction_id):
        consumer.commit()
        logger.debug("Skipped cancelled transaction %s", transaction_id)
        continue
      self._process_with_retry(message, consumer, producer)


  def _process_with_retry(self, message: IncomingMessage, consumer, producer) -> None:
    for attempt in range(1, self._settings.message_max_retries + 1):
      try:
        result = self._process(message.value)
        producer.send(
          self._settings.output_topic,
          key=message.key,
          value=result,
        ).get(timeout=30)
        consumer.commit()
        logger.info("Scored transaction %s", result["transactionId"])
        return
      except Exception as exception:  # Kafka loop must isolate individual messages.
        logger.exception(
          "Could not score message %s/%s/%s (attempt %s)",
          message.topic,
          message.partition,
          message.offset,
          attempt,
        )
        if attempt < self._settings.message_max_retries:
          time.sleep(self._settings.kafka_retry_seconds)
          continue
        producer.send(
          f"{self._settings.input_topic}.DLT",
          key=message.key,
          value={
            "source": message.value,
            "error": str(exception),
            "topic": message.topic,
            "partition": message.partition,
            "offset": message.offset,
          },
        ).get(timeout=30)
        consumer.commit()

  def _process(self, payload: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(payload, dict):
      raise ValueError("Kafka payload must be a JSON object")
    transaction_id = payload.get("transactionId")
    features = payload.get("features")
    real_fraud = payload.get("realFraud")
    if not isinstance(transaction_id, str):
      raise ValueError("transactionId must be a string")
    if not isinstance(features, dict):
      raise ValueError("features must be a JSON object")
    if real_fraud is not None and not isinstance(real_fraud, bool):
      raise ValueError("realFraud must be boolean or null")
    result = self._scoring_service.score(
      ScoringRequest(
        transaction_id=transaction_id,
        real_fraud=real_fraud,
        features=features,
      )
    )
    return result.to_event()

  def _connect_consumer(self) -> KafkaConsumer:
    while True:
      try:
        return KafkaConsumer(
          self._settings.input_topic,
          bootstrap_servers=self._settings.kafka_bootstrap_servers,
          key_deserializer=lambda value: value,
          value_deserializer=lambda value: json.loads(value.decode("utf-8")),
          auto_offset_reset="earliest",
          enable_auto_commit=False,
          group_id=self._settings.consumer_group,
        )
      except NoBrokersAvailable:
        logger.warning(
          "Kafka is unavailable; retrying in %.1f seconds",
          self._settings.kafka_retry_seconds,
        )
        time.sleep(self._settings.kafka_retry_seconds)

  def _connect_producer(self) -> KafkaProducer:
    while True:
      try:
        return KafkaProducer(
          bootstrap_servers=self._settings.kafka_bootstrap_servers,
          acks="all",
          retries=5,
          key_serializer=lambda value: value,
          value_serializer=lambda value: json.dumps(
            value, ensure_ascii=False, allow_nan=False
          ).encode("utf-8"),
        )
      except NoBrokersAvailable:
        logger.warning(
          "Kafka is unavailable; retrying in %.1f seconds",
          self._settings.kafka_retry_seconds,
        )
        time.sleep(self._settings.kafka_retry_seconds)


class ScoringCancellationRegistry:
  """Keeps the durable cancellation topic in memory before consuming claims."""

  def __init__(self, bootstrap_servers: str, topic: str) -> None:
    self._consumer = KafkaConsumer(
      bootstrap_servers=bootstrap_servers,
      enable_auto_commit=False,
      group_id=None,
      value_deserializer=lambda value: json.loads(value.decode("utf-8")),
    )
    self._partition = TopicPartition(topic, 0)
    self._consumer.assign([self._partition])
    self._consumer.seek_to_beginning(self._partition)
    self._cancelled: set[str] = set()
    self._next_refresh = 0.0
    end = self._consumer.end_offsets([self._partition])[self._partition]
    while self._consumer.position(self._partition) < end:
      self._drain(1000)
    logger.info("Loaded %s cancelled transaction IDs", len(self._cancelled))

  def contains(self, transaction_id: object) -> bool:
    return isinstance(transaction_id, str) and transaction_id in self._cancelled

  def refresh(self) -> None:
    now = time.monotonic()
    if now < self._next_refresh:
      return
    self._next_refresh = now + 1.0
    self._drain(100)

  def _drain(self, timeout_ms: int) -> None:
    while True:
      batches = self._consumer.poll(timeout_ms=timeout_ms, max_records=1000)
      if not batches:
        return
      for records in batches.values():
        for record in records:
          ids = record.value.get("transactionIds", [])
          if isinstance(ids, list):
            self._cancelled.update(value for value in ids if isinstance(value, str))
      timeout_ms = 0
