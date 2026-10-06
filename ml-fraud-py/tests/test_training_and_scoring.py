import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import pandas as pd

from fraud_detection.application.scoring import FraudScoringService
from fraud_detection.application.training import ModelTrainingService
from fraud_detection.config import DEFAULT_TARGET_ALIASES
from fraud_detection.domain import ModelType, ScoringRequest, TrainingRequest
from fraud_detection.infrastructure.dataset import PandasDatasetReader
from fraud_detection.infrastructure.model_repository import JoblibModelRepository


class TrainingAndScoringTest(unittest.TestCase):
  def setUp(self) -> None:
    self.temporary_directory = TemporaryDirectory()
    self.root = Path(self.temporary_directory.name)
    self.repository = JoblibModelRepository()
    self.training = ModelTrainingService(PandasDatasetReader(), self.repository)

  def tearDown(self) -> None:
    self.temporary_directory.cleanup()

  def test_trains_supervised_model_and_scores_dynamic_features(self) -> None:
    dataset = self.root / "labeled.csv"
    self._training_frame(include_target=True).to_csv(dataset, index=False)
    artifact = self.root / "supervised.joblib"

    report = self.training.train(self._request(dataset, artifact))
    bundle = self.repository.load(artifact)
    result = FraudScoringService(bundle).score(
      ScoringRequest(
        transaction_id="claim-1",
        real_fraud=None,
        features={
          "Amount": 9000,
          "Region": "urban",
          "Future Column": "ignored until retraining",
        },
      )
    )

    self.assertEqual(ModelType.SUPERVISED, report.model_type)
    self.assertEqual("fraudfound_p", report.target_column)
    self.assertEqual(64, len(report.dataset_sha256))
    self.assertEqual("FRAUD_PROBABILITY", result.score_type)
    self.assertIsInstance(result.predicted_fraud, bool)
    self.assertGreaterEqual(result.probability, 0)
    self.assertLessEqual(result.probability, 1)
    self.assertEqual(report.model_version, result.model_version)
    manifest = json.loads(
      artifact.with_suffix(".joblib.metadata.json").read_text(encoding="utf-8")
    )
    self.assertEqual(report.model_version, manifest["modelVersion"])
    self.assertEqual(report.dataset_sha256, manifest["datasetSha256"])
    self.assertEqual(
      {
        "transactionId",
        "realFraud",
        "predictedFraud",
        "probability",
        "scoreType",
        "riskLevel",
        "threshold",
        "classification",
        "modelVersion",
        "reasons",
      },
      set(result.to_event()),
    )

  def test_trains_anomaly_model_when_target_is_absent(self) -> None:
    dataset = self.root / "unlabeled.csv"
    self._training_frame(include_target=False).to_csv(dataset, index=False)
    artifact = self.root / "anomaly.joblib"

    report = self.training.train(self._request(dataset, artifact))
    bundle = self.repository.load(artifact)
    result = FraudScoringService(bundle).score(
      ScoringRequest(
        transaction_id="claim-2",
        features={"Amount": 999999, "Region": "unknown"},
      )
    )

    self.assertEqual(ModelType.ANOMALY, report.model_type)
    self.assertIsNone(report.target_column)
    self.assertEqual("ANOMALY_SCORE", result.score_type)
    self.assertIsNone(result.predicted_fraud)
    self.assertGreaterEqual(result.probability, 0)
    self.assertLessEqual(result.probability, 1)
    self.assertIn("review required", result.classification.lower())

  def test_requires_transaction_id_and_features(self) -> None:
    dataset = self.root / "unlabeled.csv"
    self._training_frame(include_target=False).to_csv(dataset, index=False)
    artifact = self.root / "anomaly.joblib"
    self.training.train(self._request(dataset, artifact))
    scorer = FraudScoringService(self.repository.load(artifact))

    with self.assertRaisesRegex(ValueError, "transactionId is required"):
      scorer.score(ScoringRequest(transaction_id="", features={"amount": 1}))
    with self.assertRaisesRegex(ValueError, "features must contain"):
      scorer.score(ScoringRequest(transaction_id="claim-3", features={}))

  def test_removes_identifiers_applies_quality_rules_and_uses_threshold(self) -> None:
    dataset = self.root / "quality.csv"
    frame = self._training_frame(include_target=True)
    frame["PolicyNumber"] = range(1000, 1000 + len(frame))
    frame["RepNumber"] = range(2000, 2000 + len(frame))
    frame.loc[0, "Customer Age"] = 0
    frame = pd.concat([frame, frame.iloc[[1]]], ignore_index=True)
    frame.to_csv(dataset, index=False)
    artifact = self.root / "quality.joblib"

    report = self.training.train(TrainingRequest(
      dataset_path=str(dataset),
      artifact_path=str(artifact),
      target_aliases=DEFAULT_TARGET_ALIASES,
      decision_threshold=0.35,
    ))
    bundle = self.repository.load(artifact)

    self.assertEqual(25, report.original_rows)
    self.assertEqual(1, report.duplicates_removed)
    self.assertEqual(1, report.invalid_values_replaced)
    self.assertEqual(("policynumber", "repnumber"), report.ignored_columns)
    self.assertNotIn("policynumber", bundle.feature_columns)
    self.assertNotIn("repnumber", bundle.feature_columns)
    self.assertEqual(0.35, bundle.threshold)
    self.assertIn("pr_auc", bundle.metrics)

  def _request(self, dataset: Path, artifact: Path) -> TrainingRequest:
    return TrainingRequest(
      dataset_path=str(dataset),
      artifact_path=str(artifact),
      target_aliases=DEFAULT_TARGET_ALIASES,
      random_state=42,
      test_size=0.25,
      anomaly_contamination=0.1,
    )

  def _training_frame(self, include_target: bool) -> pd.DataFrame:
    rows = []
    for index in range(24):
      fraud = index % 2
      row = {
        "Amount": 1000 + fraud * 8000 + index,
        "Region": "urban" if fraud else "rural",
        "Customer Age": 20 + index,
      }
      if include_target:
        row["FraudFound_P"] = fraud
      rows.append(row)
    return pd.DataFrame(rows)


if __name__ == "__main__":
  unittest.main()
