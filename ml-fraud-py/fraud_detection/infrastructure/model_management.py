from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Any

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable

from fraud_detection.application.training import ModelTrainingService
from fraud_detection.config import Settings
from fraud_detection.domain import TrainingRequest
from fraud_detection.infrastructure.dataset import PandasDatasetReader
from fraud_detection.infrastructure.model_repository import JoblibModelRepository

logger = logging.getLogger(__name__)


class KafkaModelManagementWorker:
  def __init__(self, settings: Settings) -> None:
    self._settings = settings
    self._repository = JoblibModelRepository()
    self._training_service = ModelTrainingService(
      PandasDatasetReader(), self._repository
    )

  def run_forever(self) -> None:
    consumer = self._connect_consumer()
    producer = self._connect_producer()
    logger.info("Model manager is waiting for training and activation requests")
    for message in consumer:
      try:
        if message.topic == self._settings.training_requests_topic:
          self._train(message.value, producer)
        else:
          self._activate(message.value, producer)
        consumer.commit()
      except Exception:
        logger.exception("Could not process model management message")

  def _train(self, payload: dict[str, Any], producer: KafkaProducer) -> None:
    training_id = self._required_text(payload, "trainingId")
    dataset_path = Path(self._required_text(payload, "datasetPath")).resolve()
    artifact_path = self._candidate_path(training_id)
    try:
      report = self._training_service.train(TrainingRequest(
        dataset_path=str(dataset_path),
        artifact_path=str(artifact_path),
        target_aliases=self._settings.target_aliases,
        random_state=self._settings.random_state,
        test_size=self._settings.test_size,
        anomaly_contamination=self._settings.anomaly_contamination,
        decision_threshold=self._settings.decision_threshold,
        ignored_features=self._settings.ignored_features,
      ))
      result = {
        "trainingId": training_id,
        "status": "READY",
        "modelType": report.model_type.value,
        "modelVersion": report.model_version,
        "artifactPath": report.artifact_path,
        "datasetSha256": report.dataset_sha256,
        "rows": report.rows,
        "featureCount": report.feature_count,
        "targetColumn": report.target_column,
        "metrics": report.metrics,
      }
    except Exception as exception:
      logger.exception("Training %s failed", training_id)
      result = {
        "trainingId": training_id,
        "status": "FAILED",
        "error": str(exception),
      }
    self._publish(producer, self._settings.training_results_topic, training_id, result)

  def _activate(self, payload: dict[str, Any], producer: KafkaProducer) -> None:
    training_id = self._required_text(payload, "trainingId")
    try:
      bundle = self._repository.load(self._candidate_path(training_id))
      self._repository.save(bundle, self._settings.artifact_path.resolve())
      result = {
        "trainingId": training_id,
        "status": "ACTIVE",
        "modelVersion": bundle.model_version,
      }
    except Exception as exception:
      logger.exception("Activation %s failed", training_id)
      result = {
        "trainingId": training_id,
        "status": "ACTIVATION_FAILED",
        "error": str(exception),
      }
    self._publish(producer, self._settings.activation_results_topic, training_id, result)

  def _candidate_path(self, training_id: str) -> Path:
    return self._settings.candidate_directory.resolve() / f"{training_id}.joblib"

  def _publish(
      self, producer: KafkaProducer, topic: str, key: str, value: dict[str, Any]
  ) -> None:
    producer.send(topic, key=key.encode(), value=value).get(timeout=30)

  def _required_text(self, payload: dict[str, Any], name: str) -> str:
    value = payload.get(name)
    if not isinstance(value, str) or not value.strip():
      raise ValueError(f"{name} is required")
    return value.strip()

  def _connect_consumer(self) -> KafkaConsumer:
    while True:
      try:
        return KafkaConsumer(
          self._settings.training_requests_topic,
          self._settings.activation_requests_topic,
          bootstrap_servers=self._settings.kafka_bootstrap_servers,
          value_deserializer=lambda value: json.loads(value.decode("utf-8")),
          auto_offset_reset="earliest",
          enable_auto_commit=False,
          group_id=self._settings.training_consumer_group,
        )
      except NoBrokersAvailable:
        logger.warning("Kafka is unavailable; retrying model manager connection")
        time.sleep(self._settings.kafka_retry_seconds)

  def _connect_producer(self) -> KafkaProducer:
    while True:
      try:
        return KafkaProducer(
          bootstrap_servers=self._settings.kafka_bootstrap_servers,
          acks="all",
          retries=5,
          value_serializer=lambda value: json.dumps(
            value, ensure_ascii=False, allow_nan=False
          ).encode("utf-8"),
        )
      except NoBrokersAvailable:
        logger.warning("Kafka is unavailable; retrying model manager connection")
        time.sleep(self._settings.kafka_retry_seconds)
