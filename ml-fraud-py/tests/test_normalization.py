import unittest

import pandas as pd

from fraud_detection.normalization import (
  find_target_column,
  normalize_frame_columns,
  normalize_name,
  parse_binary_target,
)
from fraud_detection.quality import clean_training_frame, drop_ignored_features


class NormalizationTest(unittest.TestCase):
  def test_normalizes_names_with_the_same_contract_as_java(self) -> None:
    self.assertEqual("valor_do_sinistro", normalize_name("Valor do Sinistro"))
    self.assertEqual("fraudfound_p", normalize_name("FraudFound_P"))

  def test_rejects_columns_that_collide_after_normalization(self) -> None:
    frame = pd.DataFrame(columns=["Claim ID", "claim-id"])

    with self.assertRaisesRegex(ValueError, "duplicated normalized columns"):
      normalize_frame_columns(frame)

  def test_finds_configurable_target_alias(self) -> None:
    target = find_target_column(
      ["amount", "is_fraud"],
      ("fraudfound_p", "is_fraud"),
    )

    self.assertEqual("is_fraud", target)

  def test_rejects_non_binary_target(self) -> None:
    with self.assertRaisesRegex(ValueError, "Invalid numeric fraud label"):
      parse_binary_target(pd.Series([0, 1, 2]))

  def test_applies_conservative_quality_rules(self) -> None:
    frame = pd.DataFrame({
      "age": [0, 35, 35],
      "month_claimed": ["0", "Jan", "Jan"],
      "amount": [100, 200, 200],
    })

    cleaned, duplicates, invalid = clean_training_frame(frame)

    self.assertEqual(1, duplicates)
    self.assertEqual(2, invalid)
    self.assertTrue(pd.isna(cleaned.loc[0, "age"]))
    self.assertTrue(pd.isna(cleaned.loc[0, "month_claimed"]))

  def test_drops_known_identifiers(self) -> None:
    frame = pd.DataFrame({"PolicyNumber": [1], "amount": [100]})
    frame = normalize_frame_columns(frame)

    cleaned, ignored = drop_ignored_features(frame)

    self.assertEqual(("policynumber",), ignored)
    self.assertEqual(["amount"], list(cleaned.columns))


if __name__ == "__main__":
  unittest.main()
