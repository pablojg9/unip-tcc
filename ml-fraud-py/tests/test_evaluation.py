import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import pandas as pd

from fraud_detection.experiments.evaluation import (
  _build_estimator,
  _metrics,
  _recommended_threshold,
)
from fraud_detection.experiments.splitting import DatasetSplitConfig, split_dataset


class EvaluationTest(unittest.TestCase):
  def test_builds_and_scores_smote_pipeline(self) -> None:
    features = pd.DataFrame({
      "amount": [100, 120, 130, 140, 900, 950, 980, 990, 1010, 1100, 1150, 1200],
      "region": ["rural"] * 4 + ["urban"] * 8,
    })
    target = pd.Series([0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1])

    estimator = _build_estimator(
      features,
      target,
      "logistic_regression",
      "smote",
      random_state=42,
      folds=3,
    )
    estimator.fit(features, target)
    probabilities = estimator.predict_proba(features)[:, 1]
    result = _metrics(target, probabilities >= 0.5, probabilities)

    self.assertIn("pr_auc", result)
    self.assertIn("true_positive", result)
    self.assertGreaterEqual(result["pr_auc"], 0)
    self.assertLessEqual(result["pr_auc"], 1)

  def test_selects_threshold_from_training_metrics(self) -> None:
    threshold = _recommended_threshold([
      {"threshold": 0.2, "precision": 0.4, "recall": 1.0, "f1": 0.57},
      {"threshold": 0.6, "precision": 0.8, "recall": 0.8, "f1": 0.8},
    ])

    self.assertEqual(0.6, threshold)

  def test_splits_multi_sheet_excel_with_explicit_target(self) -> None:
    with TemporaryDirectory() as temporary_directory:
      root = Path(temporary_directory)
      dataset = root / "arbitrary-claims.xlsx"
      first = self._frame(0, 20)
      second = self._frame(20, 40)
      with pd.ExcelWriter(dataset) as writer:
        first.to_excel(writer, sheet_name="Janeiro", index=False)
        second.to_excel(writer, sheet_name="Fevereiro", index=False)

      summary = split_dataset(DatasetSplitConfig(
        dataset_path=dataset,
        output_directory=root / "split",
        target_column="Resultado da Auditoria",
        test_size=0.25,
      ))
      train = pd.read_csv(root / "split" / "train.csv")
      test = pd.read_csv(root / "split" / "test.csv")

      self.assertEqual(30, len(train))
      self.assertEqual(10, len(test))
      self.assertEqual(5, test["resultado_da_auditoria"].sum())
      self.assertNotIn("policynumber", train.columns)
      self.assertEqual("resultado_da_auditoria", summary["targetColumn"])
      self.assertTrue((root / "split" / "split_summary.json").is_file())

  def _frame(self, start: int, end: int) -> pd.DataFrame:
    return pd.DataFrame({
      "PolicyNumber": range(1000 + start, 1000 + end),
      "Valor do Sinistro": range(start, end),
      "Região": ["Norte" if index % 2 else "Sul" for index in range(start, end)],
      "Resultado da Auditoria": [index % 2 for index in range(start, end)],
    })


if __name__ == "__main__":
  unittest.main()
