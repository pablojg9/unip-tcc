from pathlib import Path
import unittest
from unittest.mock import Mock

from fraud_detection.config import DEFAULT_TARGET_ALIASES, Settings
from fraud_detection.domain import ModelType, TrainingReport
from fraud_detection.infrastructure.model_management import KafkaModelManagementWorker


class ModelManagementTest(unittest.TestCase):
  def test_publishes_java_contract_in_camel_case(self) -> None:
    worker = KafkaModelManagementWorker(_settings())
    worker._training_service = Mock()
    worker._training_service.train.return_value = TrainingReport(
      model_version="rf-test",
      model_type=ModelType.SUPERVISED,
      artifact_path="candidate.joblib",
      rows=24,
      feature_count=3,
      target_column="fraudfound_p",
      metrics={"f1": 0.75},
      dataset_sha256="abc",
    )
    producer = FakeProducer()

    worker._train({"trainingId": "train-1", "datasetPath": "data.csv"}, producer)

    payload = producer.sent[0][2]
    self.assertEqual(3, payload["featureCount"])
    self.assertEqual("fraudfound_p", payload["targetColumn"])
    self.assertNotIn("feature_count", payload)
    self.assertNotIn("target_column", payload)


class FakeFuture:
  def get(self, timeout: int) -> None:
    return None


class FakeProducer:
  def __init__(self) -> None:
    self.sent = []

  def send(self, topic, key, value) -> FakeFuture:
    self.sent.append((topic, key, value))
    return FakeFuture()


def _settings() -> Settings:
  return Settings(
    artifact_path=Path("artifact.joblib"),
    kafka_bootstrap_servers="localhost:9092",
    input_topic="transactions",
    output_topic="fraud-results",
    consumer_group="test",
    target_aliases=DEFAULT_TARGET_ALIASES,
    random_state=42,
    test_size=0.25,
    anomaly_contamination=0.05,
    kafka_retry_seconds=0,
    message_max_retries=3,
  )


if __name__ == "__main__":
  unittest.main()
