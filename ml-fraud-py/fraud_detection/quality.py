from __future__ import annotations

import pandas as pd

from fraud_detection.normalization import normalize_name


DEFAULT_IGNORED_FEATURES = (
  "id",
  "claim_id",
  "transaction_id",
  "id_sinistro",
  "numero_apolice",
  "segurado_id",
  "policy_number",
  "policynumber",
  "rep_number",
  "repnumber",
)

_AGE_COLUMNS = {"age", "customer_age", "idade", "idade_segurado"}
_CALENDAR_COLUMNS = {
  "month",
  "month_claimed",
  "day_of_week",
  "day_of_week_claimed",
  "mes",
  "mes_sinistro",
  "dia_semana",
}
_MISSING_MARKERS = {"", "0", "0.0", "nan", "none", "null", "n/a", "na"}


def clean_training_frame(frame: pd.DataFrame) -> tuple[pd.DataFrame, int, int]:
  """Apply conservative domain rules used by production training."""
  original_rows = len(frame)
  result = frame.drop_duplicates().reset_index(drop=True).copy()
  invalid_values = 0

  for column in result.columns:
    if column in _AGE_COLUMNS:
      numeric = pd.to_numeric(result[column], errors="coerce")
      invalid = result[column].notna() & ((numeric <= 0) | (numeric > 120))
      invalid_values += int(invalid.sum())
      result.loc[invalid, column] = pd.NA
    if column in _CALENDAR_COLUMNS:
      normalized = result[column].astype("string").str.strip().str.lower()
      invalid = result[column].notna() & normalized.isin(_MISSING_MARKERS)
      invalid_values += int(invalid.sum())
      result.loc[invalid, column] = pd.NA

  return result, original_rows - len(result), invalid_values


def drop_ignored_features(
    frame: pd.DataFrame,
    ignored_features: tuple[str, ...] = DEFAULT_IGNORED_FEATURES,
) -> tuple[pd.DataFrame, tuple[str, ...]]:
  ignored = {normalize_name(column) for column in ignored_features}
  matches = tuple(column for column in frame.columns if column in ignored)
  return frame.drop(columns=list(matches), errors="ignore"), matches
