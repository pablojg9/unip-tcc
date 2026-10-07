from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from fraud_detection.quality import DEFAULT_IGNORED_FEATURES

DEFAULT_TARGET_ALIASES = (
  "fraudfound_p",
  "fraud",
  "is_fraud",
  "fraude",
  "isfraud",
  "label",
)


@dataclass(frozen=True, slots=True)
class Settings:
  artifact_path: Path
  kafka_bootstrap_servers: str
  input_topic: str
  output_topic: str
  consumer_group: str
  target_aliases: tuple[str, ...]
  random_state: int
  test_size: float
  anomaly_contamination: float
  kafka_retry_seconds: float
  message_max_retries: int
  decision_threshold: float = 0.5
  ignored_features: tuple[str, ...] = DEFAULT_IGNORED_FEATURES
  generative_explanation_enabled: bool = False
  generative_explanation_url: str = "http://ollama:11434/api/generate"
  generative_explanation_model: str = "llama3.2:1b"
  generative_explanation_timeout_seconds: float = 8.0
  training_requests_topic: str = "model-training-requests"
  training_results_topic: str = "model-training-results"
  activation_requests_topic: str = "model-activation-requests"
  activation_results_topic: str = "model-activation-results"
  training_consumer_group: str = "ml-model-manager"
  candidate_directory: Path = Path("artifacts/candidates")

  @classmethod
  def from_environment(cls) -> "Settings":
    aliases = tuple(
      value.strip()
      for value in os.getenv(
        "FRAUD_TARGET_ALIASES", ",".join(DEFAULT_TARGET_ALIASES)
      ).split(",")
      if value.strip()
    )
    return cls(
      artifact_path=Path(
        os.getenv("FRAUD_MODEL_PATH", "artifacts/fraud_model.joblib")
      ),
      kafka_bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
      input_topic=os.getenv("KAFKA_TRANSACTIONS_TOPIC", "transactions"),
      output_topic=os.getenv("KAFKA_RESULTS_TOPIC", "fraud-results"),
      consumer_group=os.getenv("KAFKA_CONSUMER_GROUP", "ml-fraud-consumer"),
      target_aliases=aliases,
      random_state=_integer("FRAUD_RANDOM_STATE", 42),
      test_size=_bounded_float("FRAUD_TEST_SIZE", 0.25, minimum=0.05, maximum=0.5),
      anomaly_contamination=_bounded_float(
        "FRAUD_ANOMALY_CONTAMINATION", 0.05, minimum=0.001, maximum=0.5
      ),
      kafka_retry_seconds=_bounded_float(
        "KAFKA_RETRY_SECONDS", 5.0, minimum=0.1, maximum=300.0
      ),
      message_max_retries=_integer("KAFKA_MESSAGE_MAX_RETRIES", 3, minimum=1),
      decision_threshold=_bounded_float(
        "FRAUD_DECISION_THRESHOLD", 0.5, minimum=0.01, maximum=0.99
      ),
      ignored_features=_list(
        "FRAUD_IGNORED_FEATURES", DEFAULT_IGNORED_FEATURES
      ),
      generative_explanation_enabled=_boolean(
        "GENERATIVE_EXPLANATION_ENABLED", False
      ),
      generative_explanation_url=os.getenv(
        "GENERATIVE_EXPLANATION_URL", "http://ollama:11434/api/generate"
      ),
      generative_explanation_model=os.getenv(
        "GENERATIVE_EXPLANATION_MODEL", "llama3.2:1b"
      ),
      generative_explanation_timeout_seconds=_bounded_float(
        "GENERATIVE_EXPLANATION_TIMEOUT_SECONDS", 8.0, minimum=0.1, maximum=60.0
      ),
      training_requests_topic=os.getenv(
        "KAFKA_TRAINING_REQUESTS_TOPIC", "model-training-requests"
      ),
      training_results_topic=os.getenv(
        "KAFKA_TRAINING_RESULTS_TOPIC", "model-training-results"
      ),
      activation_requests_topic=os.getenv(
        "KAFKA_ACTIVATION_REQUESTS_TOPIC", "model-activation-requests"
      ),
      activation_results_topic=os.getenv(
        "KAFKA_ACTIVATION_RESULTS_TOPIC", "model-activation-results"
      ),
      training_consumer_group=os.getenv(
        "KAFKA_TRAINING_CONSUMER_GROUP", "ml-model-manager"
      ),
      candidate_directory=Path(
        os.getenv("FRAUD_CANDIDATE_DIRECTORY", "artifacts/candidates")
      ),
    )


def _integer(name: str, default: int, minimum: int | None = None) -> int:
  value = int(os.getenv(name, str(default)))
  if minimum is not None and value < minimum:
    raise ValueError(f"{name} must be at least {minimum}")
  return value


def _bounded_float(name: str, default: float, minimum: float, maximum: float) -> float:
  value = float(os.getenv(name, str(default)))
  if not minimum <= value <= maximum:
    raise ValueError(f"{name} must be between {minimum} and {maximum}")
  return value


def _list(name: str, default: tuple[str, ...]) -> tuple[str, ...]:
  return tuple(
    value.strip()
    for value in os.getenv(name, ",".join(default)).split(",")
    if value.strip()
  )


def _boolean(name: str, default: bool) -> bool:
  value = os.getenv(name, str(default)).strip().lower()
  if value in {"1", "true", "yes", "sim"}:
    return True
  if value in {"0", "false", "no", "nao", "não"}:
    return False
  raise ValueError(f"{name} must be a boolean")
