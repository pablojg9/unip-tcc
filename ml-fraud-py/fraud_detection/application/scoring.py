from __future__ import annotations

from pathlib import Path
from threading import Lock

import numpy as np
import pandas as pd

from fraud_detection.domain import ModelBundle, ModelType, ScoringRequest, ScoringResult
from fraud_detection.explanation import (
  DeterministicExplanationGenerator,
  ExplanationContext,
  ExplanationGenerator,
)
from fraud_detection.infrastructure.model_repository import JoblibModelRepository
from fraud_detection.normalization import prepare_inference_frame


class FraudScoringService:
  def __init__(
      self,
      bundle: ModelBundle,
      explanation_generator: ExplanationGenerator | None = None,
  ) -> None:
    self._bundle = bundle
    self._explanation_generator = (
      explanation_generator or DeterministicExplanationGenerator()
    )

  def score(self, request: ScoringRequest) -> ScoringResult:
    self._validate(request)
    frame = prepare_inference_frame(
      request.features,
      self._bundle.feature_columns,
      self._bundle.numeric_columns,
      self._bundle.categorical_columns,
    )
    if self._bundle.model_type == ModelType.SUPERVISED:
      probability = float(self._bundle.pipeline.predict_proba(frame)[0][1])
      predicted_fraud: bool | None = probability >= self._bundle.threshold
      score_type = "FRAUD_PROBABILITY"
    else:
      raw_score = float(self._bundle.pipeline.decision_function(frame)[0])
      probability = self._anomaly_probability(raw_score)
      predicted_fraud = None
      score_type = "ANOMALY_SCORE"

    probability = round(float(np.clip(probability, 0.0, 1.0)), 4)
    risk_level = _risk_level(probability)
    classification = _classification(score_type, predicted_fraud, risk_level)
    reasons = self._local_reasons(frame, probability)
    explanation = self._explanation_generator.generate(ExplanationContext(
      probability=probability,
      risk_level=risk_level,
      classification=classification,
      score_type=score_type,
      threshold=self._bundle.threshold,
      reasons=reasons,
    ))
    return ScoringResult(
      transaction_id=request.transaction_id,
      real_fraud=request.real_fraud,
      predicted_fraud=predicted_fraud,
      probability=probability,
      score_type=score_type,
      risk_level=risk_level,
      threshold=self._bundle.threshold,
      classification=classification,
      model_version=self._bundle.model_version,
      reasons=reasons,
      explanation=explanation.text,
      explanation_type=explanation.explanation_type,
      explanation_model=explanation.model,
    )

  def _local_reasons(
      self, frame: pd.DataFrame, probability: float, limit: int = 3
  ) -> tuple[str, ...]:
    columns = list(self._bundle.feature_columns)
    if not columns:
      return ("Não há variáveis disponíveis para explicar o score.",)
    perturbed_rows: list[pd.DataFrame] = []
    for column in columns:
      perturbed = frame.copy()
      perturbed.loc[perturbed.index[0], column] = np.nan
      perturbed_rows.append(perturbed)
    perturbed_frame = pd.concat(perturbed_rows, ignore_index=True)
    if self._bundle.model_type == ModelType.SUPERVISED:
      reference_probabilities = self._bundle.pipeline.predict_proba(perturbed_frame)[:, 1]
    else:
      scores = self._bundle.pipeline.decision_function(perturbed_frame)
      reference_probabilities = np.array([
        self._anomaly_probability(float(score)) for score in scores
      ])
    impacts = [
      (column, probability - float(reference_probability))
      for column, reference_probability in zip(
        columns, reference_probabilities, strict=True
      )
    ]
    meaningful = sorted(
      (item for item in impacts if abs(item[1]) >= 0.0005),
      key=lambda item: abs(item[1]),
      reverse=True,
    )[:limit]
    if not meaningful:
      return (
        "Nenhuma variável isolada alterou materialmente o score em relação ao valor de referência.",
      )
    return tuple(
      f"{_feature_label(column)} {('aumentou' if impact > 0 else 'reduziu')} "
      f"o risco estimado em aproximadamente {abs(impact) * 100:.1f} ponto(s) percentual(is)."
      for column, impact in meaningful
    )

  def _anomaly_probability(self, raw_score: float) -> float:
    low = self._bundle.anomaly_low_score
    high = self._bundle.anomaly_high_score
    if low is None or high is None or high <= low:
      raise ValueError("Anomaly model does not contain valid score calibration")
    return (high - raw_score) / (high - low)

  def _validate(self, request: ScoringRequest) -> None:
    if not request.transaction_id or not request.transaction_id.strip():
      raise ValueError("transactionId is required")
    if len(request.transaction_id) > 64:
      raise ValueError("transactionId cannot exceed 64 characters")
    if not request.features:
      raise ValueError("features must contain at least one attribute")
    if request.real_fraud is not None and not isinstance(request.real_fraud, bool):
      raise ValueError("realFraud must be boolean or null")


class ReloadingFraudScoringService:
  """Reloads the active artifact only when a newly approved model replaces it."""

  def __init__(
      self,
      artifact_path: Path,
      explanation_generator: ExplanationGenerator | None = None,
  ) -> None:
    self._artifact_path = artifact_path.resolve()
    self._repository = JoblibModelRepository()
    self._lock = Lock()
    self._modified_at: int | None = None
    self._delegate: FraudScoringService | None = None
    self._explanation_generator = explanation_generator

  def score(self, request: ScoringRequest) -> ScoringResult:
    return self._current_service().score(request)

  def _current_service(self) -> FraudScoringService:
    try:
      modified_at = self._artifact_path.stat().st_mtime_ns
    except FileNotFoundError as exception:
      raise RuntimeError(
        "No active fraud model. An administrator must train and activate one first."
      ) from exception

    with self._lock:
      if self._delegate is None or self._modified_at != modified_at:
        bundle = self._repository.load(self._artifact_path)
        self._delegate = FraudScoringService(bundle, self._explanation_generator)
        self._modified_at = modified_at
      return self._delegate


def _risk_level(probability: float) -> str:
  if probability >= 0.7:
    return "HIGH"
  if probability >= 0.3:
    return "MEDIUM"
  return "LOW"


def _classification(score_type: str, predicted_fraud: bool | None, risk_level: str) -> str:
  if score_type == "ANOMALY_SCORE":
    return f"{risk_level.title()} anomaly risk - review required"
  return "Suspicious transaction" if predicted_fraud else "Normal transaction"


def _feature_label(value: str) -> str:
  return value.replace("_", " ").strip().capitalize()
