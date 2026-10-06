from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import StrEnum
from typing import Any


class ModelType(StrEnum):
  SUPERVISED = "SUPERVISED"
  ANOMALY = "ANOMALY"


@dataclass(frozen=True, slots=True)
class TrainingRequest:
  dataset_path: str
  artifact_path: str
  target_aliases: tuple[str, ...]
  random_state: int = 42
  test_size: float = 0.25
  anomaly_contamination: float = 0.05
  decision_threshold: float = 0.5
  ignored_features: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class TrainingReport:
  model_version: str
  model_type: ModelType
  artifact_path: str
  rows: int
  feature_count: int
  target_column: str | None
  metrics: dict[str, float]
  dataset_sha256: str
  original_rows: int = 0
  duplicates_removed: int = 0
  invalid_values_replaced: int = 0
  ignored_columns: tuple[str, ...] = ()


@dataclass(slots=True)
class ModelBundle:
  model_version: str
  model_type: ModelType
  pipeline: Any
  feature_columns: tuple[str, ...]
  numeric_columns: tuple[str, ...]
  categorical_columns: tuple[str, ...]
  threshold: float
  metrics: dict[str, float]
  target_column: str | None = None
  important_features: tuple[str, ...] = ()
  anomaly_low_score: float | None = None
  anomaly_high_score: float | None = None
  dataset_sha256: str = ""
  trained_at: str = field(default_factory=lambda: datetime.now(UTC).isoformat())


@dataclass(frozen=True, slots=True)
class ScoringRequest:
  transaction_id: str
  features: dict[str, Any]
  real_fraud: bool | None = None


@dataclass(frozen=True, slots=True)
class ScoringResult:
  transaction_id: str
  real_fraud: bool | None
  predicted_fraud: bool | None
  probability: float
  score_type: str
  risk_level: str
  threshold: float
  classification: str
  model_version: str
  reasons: tuple[str, ...]

  def to_event(self) -> dict[str, Any]:
    return {
      "transactionId": self.transaction_id,
      "realFraud": self.real_fraud,
      "predictedFraud": self.predicted_fraud,
      "probability": self.probability,
      "scoreType": self.score_type,
      "riskLevel": self.risk_level,
      "threshold": self.threshold,
      "classification": self.classification,
      "modelVersion": self.model_version,
      "reasons": list(self.reasons),
    }
