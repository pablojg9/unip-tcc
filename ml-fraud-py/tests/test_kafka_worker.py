from pathlib import Path
import unittest
from unittest.mock import patch

from fraud_detection.config import DEFAULT_TARGET_ALIASES, Settings
from fraud_detection.domain import ScoringResult
from fraud_detection.infrastructure.kafka import (
  IncomingMessage, KafkaScoringWorker, ScoringCancellationRegistry,
)


class KafkaScoringWorkerTest(unittest.TestCase):
  def test_loads_existing_cancellations_before_scoring_starts(self) -> None:
    class ControlConsumer:
      def __init__(self):
        self.offset = 0

      def assign(self, partitions):
        self.partition = partitions[0]

      def seek_to_beginning(self, partition):
        self.offset = 0

      def end_offsets(self, partitions):
        return {self.partition: 1}

      def position(self, partition):
        return self.offset

      def poll(self, **kwargs):
        if self.offset:
          return {}
        self.offset = 1
        return {self.partition: [type("Record", (), {
          "value": {"transactionIds": ["claim-1"]},
        })()]}

    with patch("fraud_detection.infrastructure.kafka.KafkaConsumer", return_value=ControlConsumer()):
      registry = ScoringCancellationRegistry("localhost:9092", "scoring-cancellations")

    self.assertTrue(registry.contains("claim-1"))
    self.assertFalse(registry.contains("claim-2"))

  def test_skips_cancelled_transaction_without_scoring_or_publishing(self) -> None:
    scorer = FlakyScorer(failures=0)
    worker = KafkaScoringWorker(settings(max_retries=1), scorer)
    consumer = FiniteConsumer([type("Record", (), {
      "key": b"claim-1", "value": message().value,
      "topic": "transactions", "partition": 0, "offset": 1,
    })()])
    producer = FakeProducer()
    worker._connect_consumer = lambda: consumer
    worker._connect_producer = lambda: producer
    with patch("fraud_detection.infrastructure.kafka.ScoringCancellationRegistry") as registry:
      registry.return_value.contains.return_value = True
      worker.run_forever()

    self.assertEqual(0, scorer.calls)
    self.assertEqual(1, consumer.commits)
    self.assertEqual([], producer.sent)

  def test_retries_the_same_record_before_committing(self) -> None:
    scorer = FlakyScorer(failures=2)
    worker = KafkaScoringWorker(settings(max_retries=3), scorer)
    consumer = FakeConsumer()
    producer = FakeProducer()

    with self.assertLogs("fraud_detection.infrastructure.kafka", level="ERROR"):
      worker._process_with_retry(message(), consumer, producer)

    self.assertEqual(3, scorer.calls)
    self.assertEqual(1, consumer.commits)
    self.assertEqual("fraud-results", producer.sent[0][0])

  def test_sends_poison_record_to_dead_letter_topic(self) -> None:
    scorer = FlakyScorer(failures=10)
    worker = KafkaScoringWorker(settings(max_retries=2), scorer)
    consumer = FakeConsumer()
    producer = FakeProducer()

    with self.assertLogs("fraud_detection.infrastructure.kafka", level="ERROR"):
      worker._process_with_retry(message(), consumer, producer)

    self.assertEqual(2, scorer.calls)
    self.assertEqual(1, consumer.commits)
    self.assertEqual("transactions.DLT", producer.sent[0][0])
    self.assertEqual("temporary failure", producer.sent[0][2]["error"])


class FlakyScorer:
  def __init__(self, failures: int) -> None:
    self.failures = failures
    self.calls = 0

  def score(self, request) -> ScoringResult:
    self.calls += 1
    if self.calls <= self.failures:
      raise RuntimeError("temporary failure")
    return ScoringResult(
      transaction_id=request.transaction_id,
      real_fraud=request.real_fraud,
      predicted_fraud=False,
      probability=0.1,
      score_type="FRAUD_PROBABILITY",
      risk_level="LOW",
      threshold=0.5,
      classification="Normal transaction",
      model_version="test-model",
      reasons=(),
    )


class FakeConsumer:
  def __init__(self) -> None:
    self.commits = 0

  def commit(self) -> None:
    self.commits += 1


class FiniteConsumer(FakeConsumer):
  def __init__(self, messages) -> None:
    super().__init__()
    self.messages = messages

  def __iter__(self):
    return iter(self.messages)


class FakeFuture:
  def get(self, timeout: int) -> None:
    return None


class FakeProducer:
  def __init__(self) -> None:
    self.sent = []

  def send(self, topic, key, value) -> FakeFuture:
    self.sent.append((topic, key, value))
    return FakeFuture()


def settings(max_retries: int) -> Settings:
  return Settings(
    artifact_path=Path("artifact.joblib"),
    kafka_bootstrap_servers="localhost:9092",
    input_topic="transactions",
    output_topic="fraud-results",
    cancellations_topic="scoring-cancellations",
    consumer_group="test",
    target_aliases=DEFAULT_TARGET_ALIASES,
    random_state=42,
    test_size=0.25,
    anomaly_contamination=0.05,
    kafka_retry_seconds=0,
    message_max_retries=max_retries,
  )


def message() -> IncomingMessage:
  return IncomingMessage(
    key=b"claim-1",
    value={"transactionId": "claim-1", "features": {"amount": 100}},
    topic="transactions",
    partition=0,
    offset=1,
  )


if __name__ == "__main__":
  unittest.main()
