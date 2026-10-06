import unittest

import pandas as pd

from fraud_detection.experiments.evaluation import _build_estimator, _metrics


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


if __name__ == "__main__":
  unittest.main()
