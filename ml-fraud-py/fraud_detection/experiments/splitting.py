from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import pandas as pd
from sklearn.model_selection import train_test_split

from fraud_detection.config import DEFAULT_TARGET_ALIASES
from fraud_detection.infrastructure.dataset import PandasDatasetReader
from fraud_detection.normalization import (
  find_target_column,
  normalize_frame_columns,
  normalize_name,
  parse_binary_target,
)
from fraud_detection.quality import (
  DEFAULT_IGNORED_FEATURES,
  clean_training_frame,
  drop_ignored_features,
)


@dataclass(frozen=True, slots=True)
class DatasetSplitConfig:
  dataset_path: Path
  output_directory: Path
  target_column: str | None = None
  target_aliases: tuple[str, ...] = DEFAULT_TARGET_ALIASES
  ignored_features: tuple[str, ...] = DEFAULT_IGNORED_FEATURES
  random_state: int = 42
  test_size: float = 0.25


def split_dataset(config: DatasetSplitConfig) -> dict[str, Any]:
  _validate_config(config)
  source = normalize_frame_columns(
    PandasDatasetReader().read(config.dataset_path.resolve())
  )
  if source.empty:
    raise ValueError("Dataset is empty")

  target_column = _resolve_target_column(
    list(source.columns), config.target_column, config.target_aliases
  )
  target = parse_binary_target(source[target_column])
  labeled = target.notna()
  unlabeled_rows = int((~labeled).sum())
  frame = source.loc[labeled].reset_index(drop=True)
  target = target.loc[labeled].astype(int).reset_index(drop=True)
  if target.nunique() != 2:
    raise ValueError("Fraud target must contain both legitimate and fraud examples")

  cleaned, duplicates_removed, invalid_values_replaced = clean_training_frame(frame)
  target = parse_binary_target(cleaned[target_column]).astype(int)
  if int(target.value_counts().min()) < 2:
    raise ValueError("Each fraud class must contain at least 2 examples")
  features, ignored_columns = drop_ignored_features(
    cleaned.drop(columns=[target_column]), config.ignored_features
  )
  if features.empty:
    raise ValueError("Dataset has no usable feature columns after identifier removal")
  prepared = features.copy()
  prepared[target_column] = cleaned[target_column].to_numpy()

  train, test = train_test_split(
    prepared,
    test_size=config.test_size,
    random_state=config.random_state,
    stratify=target,
  )
  train = train.reset_index(drop=True)
  test = test.reset_index(drop=True)

  output = config.output_directory.resolve()
  output.mkdir(parents=True, exist_ok=True)
  train_path = output / "train.csv"
  test_path = output / "test.csv"
  train.to_csv(train_path, index=False)
  test.to_csv(test_path, index=False)

  train_target = parse_binary_target(train[target_column]).astype(int)
  test_target = parse_binary_target(test[target_column]).astype(int)
  summary = {
    "dataset": str(config.dataset_path.resolve()),
    "targetColumn": target_column,
    "originalRows": len(source),
    "unlabeledRowsRemoved": unlabeled_rows,
    "duplicatesRemoved": duplicates_removed,
    "invalidValuesReplaced": invalid_values_replaced,
    "ignoredColumns": list(ignored_columns),
    "trainRows": len(train),
    "testRows": len(test),
    "trainFrauds": int(train_target.sum()),
    "testFrauds": int(test_target.sum()),
    "trainPath": str(train_path),
    "testPath": str(test_path),
    "configuration": {
      **asdict(config),
      "dataset_path": str(config.dataset_path.resolve()),
      "output_directory": str(output),
    },
  }
  (output / "split_summary.json").write_text(
    json.dumps(summary, indent=2, ensure_ascii=False),
    encoding="utf-8",
  )
  return summary


def resolve_target_column(
    columns: list[str], requested: str | None, aliases: tuple[str, ...]
) -> str:
  return _resolve_target_column(columns, requested, aliases)


def _resolve_target_column(
    columns: list[str], requested: str | None, aliases: tuple[str, ...]
) -> str:
  if requested:
    normalized = normalize_name(requested)
    if normalized not in columns:
      raise ValueError(
        f"Target column '{requested}' was not found after normalization as '{normalized}'"
      )
    return normalized
  detected = find_target_column(columns, aliases)
  if detected is None:
    raise ValueError(
      "A binary fraud target is required; use --target-column when its name is not a known alias"
    )
  return detected


def _validate_config(config: DatasetSplitConfig) -> None:
  if not config.dataset_path.is_file():
    raise FileNotFoundError(f"Dataset not found: {config.dataset_path}")
  if not 0 < config.test_size < 1:
    raise ValueError("Test size must be between 0 and 1")
