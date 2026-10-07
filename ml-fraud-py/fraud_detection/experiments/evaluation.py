from __future__ import annotations

import json
import math
import os
from dataclasses import asdict, dataclass
from pathlib import Path
from tempfile import gettempdir
from typing import Any

_cache_root = Path(gettempdir()) / "fraudguard-plot-cache"
_cache_root.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(_cache_root / "matplotlib"))
os.environ.setdefault("XDG_CACHE_HOME", str(_cache_root))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
from imblearn.over_sampling import SMOTE
from imblearn.pipeline import Pipeline as ImbalancedPipeline
from sklearn.base import clone
from sklearn.ensemble import RandomForestClassifier
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
  ConfusionMatrixDisplay,
  accuracy_score,
  average_precision_score,
  confusion_matrix,
  f1_score,
  precision_recall_curve,
  precision_score,
  recall_score,
  roc_auc_score,
  roc_curve,
)
from sklearn.model_selection import (
  StratifiedKFold,
  cross_val_predict,
  cross_validate,
  train_test_split,
)
from sklearn.pipeline import Pipeline
from sklearn.tree import DecisionTreeClassifier

from fraud_detection.config import DEFAULT_TARGET_ALIASES
from fraud_detection.infrastructure.dataset import PandasDatasetReader
from fraud_detection.normalization import (
  coerce_feature_types,
  normalize_frame_columns,
  parse_binary_target,
)
from fraud_detection.preprocessing import build_preprocessor
from fraud_detection.quality import (
  DEFAULT_IGNORED_FEATURES,
  clean_training_frame,
  drop_ignored_features,
)
from fraud_detection.experiments.splitting import resolve_target_column


@dataclass(frozen=True, slots=True)
class EvaluationConfig:
  dataset_path: Path
  output_directory: Path
  target_column: str | None = None
  target_aliases: tuple[str, ...] = DEFAULT_TARGET_ALIASES
  ignored_features: tuple[str, ...] = DEFAULT_IGNORED_FEATURES
  random_state: int = 42
  test_size: float = 0.25
  folds: int = 5
  threshold: float | None = None


@dataclass(slots=True)
class _BestRun:
  score: float = -1.0
  name: str = ""
  estimator: Any = None
  treatment: str = ""
  model: str = ""
  balancing: str = ""
  train_x: pd.DataFrame | None = None
  train_y: pd.Series | None = None
  test_x: pd.DataFrame | None = None
  test_y: pd.Series | None = None
  folds: int = 0
  row: dict[str, Any] | None = None


def run_evaluation(config: EvaluationConfig) -> dict[str, Any]:
  _validate_config(config)
  output = config.output_directory.resolve()
  plots = output / "plots"
  plots.mkdir(parents=True, exist_ok=True)

  source = normalize_frame_columns(
    PandasDatasetReader().read(config.dataset_path.resolve())
  )
  if source.empty:
    raise ValueError("Dataset is empty")
  target_column = resolve_target_column(
    list(source.columns), config.target_column, config.target_aliases
  )

  treated, duplicates_removed, invalid_values_replaced = clean_training_frame(source)
  variants = {"raw": source.copy(), "treated": treated}
  rows: list[dict[str, Any]] = []
  threshold_rows: list[dict[str, Any]] = []
  ignored_by_variant: dict[str, list[str]] = {}
  best = _BestRun()

  for treatment, frame in variants.items():
    features, target, ignored = _prepare_variant(
      frame, target_column, config.ignored_features
    )
    ignored_by_variant[treatment] = list(ignored)
    train_x, test_x, train_y, test_y = train_test_split(
      features,
      target,
      test_size=config.test_size,
      random_state=config.random_state,
      stratify=target,
    )
    folds = _safe_fold_count(train_y, config.folds)
    cross_validation = StratifiedKFold(
      n_splits=folds,
      shuffle=True,
      random_state=config.random_state,
    )

    for model_name in ("logistic_regression", "decision_tree", "random_forest"):
      for balancing in ("none", "class_weight", "smote"):
        estimator = _build_estimator(
          train_x,
          train_y,
          model_name,
          balancing,
          config.random_state,
          folds,
        )
        scoring = {
          "accuracy": "accuracy",
          "precision": "precision",
          "recall": "recall",
          "f1": "f1",
          "roc_auc": "roc_auc",
          "pr_auc": "average_precision",
        }
        cross_validation_result = cross_validate(
          estimator,
          train_x,
          train_y,
          scoring=scoring,
          cv=cross_validation,
          n_jobs=1,
          error_score="raise",
        )
        run_name = f"{treatment}-{model_name}-{balancing}"
        row: dict[str, Any] = {
          "treatment": treatment,
          "model": model_name,
          "balancing": balancing,
          "rows": len(features),
          "features": len(features.columns),
          "folds": folds,
          "selected": False,
        }
        for metric_name in scoring:
          values = cross_validation_result[f"test_{metric_name}"]
          row[f"cv_{metric_name}_mean"] = float(values.mean())
          row[f"cv_{metric_name}_std"] = float(values.std())
        rows.append(row)
        selection_score = row["cv_pr_auc_mean"]
        if selection_score > best.score:
          best = _BestRun(
            score=selection_score,
            name=run_name,
            estimator=estimator,
            treatment=treatment,
            model=model_name,
            balancing=balancing,
            train_x=train_x,
            train_y=train_y,
            test_x=test_x,
            test_y=test_y,
            folds=folds,
            row=row,
          )

  if (
      best.estimator is None
      or best.train_x is None
      or best.train_y is None
      or best.test_x is None
      or best.test_y is None
      or best.row is None
  ):
    raise RuntimeError("Evaluation did not produce a valid model candidate")

  best.row["selected"] = True
  selection_cv = StratifiedKFold(
    n_splits=best.folds,
    shuffle=True,
    random_state=config.random_state,
  )
  out_of_fold_probabilities = cross_val_predict(
    clone(best.estimator),
    best.train_x,
    best.train_y,
    cv=selection_cv,
    method="predict_proba",
    n_jobs=1,
  )[:, 1]
  threshold_rows = _threshold_table(
    best.train_y, out_of_fold_probabilities, best.name
  )
  selected_threshold = (
    config.threshold
    if config.threshold is not None
    else _recommended_threshold(threshold_rows)
  )
  fitted = clone(best.estimator).fit(best.train_x, best.train_y)
  probabilities = fitted.predict_proba(best.test_x)[:, 1]
  predicted = probabilities >= selected_threshold
  holdout_metrics = _metrics(best.test_y, predicted, probabilities)
  final_result = {
    "treatment": best.treatment,
    "model": best.model,
    "balancing": best.balancing,
    "threshold": selected_threshold,
    **holdout_metrics,
    **{
      key: value
      for key, value in best.row.items()
      if key.startswith("cv_")
    },
  }
  _save_diagnostic_plot(
    best.test_y,
    predicted,
    probabilities,
    plots / f"{best.name}.png",
    best.name,
  )
  fitted_best = _BestRun(
    score=best.score,
    name=best.name,
    estimator=fitted,
    test_x=best.test_x,
    test_y=best.test_y,
  )

  results = pd.DataFrame(rows).sort_values(
    ["cv_pr_auc_mean", "cv_recall_mean", "cv_f1_mean"], ascending=False
  )
  results.to_csv(output / "metrics.csv", index=False)
  pd.DataFrame([final_result]).to_csv(output / "final_metrics.csv", index=False)
  pd.DataFrame(threshold_rows).to_csv(output / "thresholds.csv", index=False)
  _save_feature_importance(
    fitted_best, output / "feature_importance.csv", config.random_state
  )
  _save_comparison_plot(results, output / "model_comparison.png")

  summary = {
    "dataset": str(config.dataset_path.resolve()),
    "targetColumn": target_column,
    "originalRows": len(source),
    "duplicatesRemoved": duplicates_removed,
    "invalidValuesReplaced": invalid_values_replaced,
    "ignoredColumns": ignored_by_variant,
    "bestRun": best.name,
    "bestCvPrAuc": best.score,
    "selectedThreshold": selected_threshold,
    "thresholdSelection": (
      "configured" if config.threshold is not None else "maximum out-of-fold training F1"
    ),
    "holdoutMetrics": holdout_metrics,
    "configuration": {
      **asdict(config),
      "dataset_path": str(config.dataset_path.resolve()),
      "output_directory": str(output),
    },
  }
  (output / "summary.json").write_text(
    json.dumps(summary, indent=2, ensure_ascii=False),
    encoding="utf-8",
  )
  return summary


def _prepare_variant(
    frame: pd.DataFrame,
    target_column: str,
    ignored_features: tuple[str, ...],
) -> tuple[pd.DataFrame, pd.Series, tuple[str, ...]]:
  target = parse_binary_target(frame[target_column])
  labeled = target.notna()
  target = target.loc[labeled].astype(int).reset_index(drop=True)
  features = frame.loc[labeled].drop(columns=[target_column]).reset_index(drop=True)
  features, ignored = drop_ignored_features(features, ignored_features)
  features, _, _ = coerce_feature_types(features)
  if features.empty:
    raise ValueError("Dataset has no usable feature columns after identifier removal")
  if target.nunique() != 2:
    raise ValueError("Fraud target must contain both legitimate and fraud examples")
  return features, target, ignored


def _build_estimator(
    features: pd.DataFrame,
    target: pd.Series,
    model_name: str,
    balancing: str,
    random_state: int,
    folds: int,
) -> Pipeline | ImbalancedPipeline:
  _, numeric, categorical = coerce_feature_types(features)
  scale = model_name == "logistic_regression"
  preprocessor = build_preprocessor(
    numeric,
    categorical,
    scale_numeric=scale,
    sparse_output=False,
  )
  class_weight = "balanced" if balancing == "class_weight" else None
  if model_name == "logistic_regression":
    model = LogisticRegression(
      max_iter=2000,
      class_weight=class_weight,
      random_state=random_state,
    )
  elif model_name == "decision_tree":
    model = DecisionTreeClassifier(
      min_samples_leaf=2,
      class_weight=class_weight,
      random_state=random_state,
    )
  else:
    model = RandomForestClassifier(
      n_estimators=200,
      min_samples_leaf=2,
      class_weight=class_weight,
      random_state=random_state,
      n_jobs=-1,
    )
  if balancing == "smote":
    minority = int(target.value_counts().min())
    smallest_training_fold = minority - math.ceil(minority / folds)
    if smallest_training_fold < 2:
      raise ValueError(
        "SMOTE cross-validation requires at least 3 examples in the minority class"
      )
    neighbors = min(5, smallest_training_fold - 1)
    return ImbalancedPipeline([
      ("preprocessor", preprocessor),
      ("smote", SMOTE(random_state=random_state, k_neighbors=neighbors)),
      ("model", model),
    ])
  return Pipeline([("preprocessor", preprocessor), ("model", model)])


def _metrics(
    target: pd.Series,
    predicted: Any,
    probabilities: Any,
) -> dict[str, float]:
  true_negative, false_positive, false_negative, true_positive = confusion_matrix(
    target, predicted, labels=[0, 1]
  ).ravel()
  return {
    "accuracy": float(accuracy_score(target, predicted)),
    "precision": float(precision_score(target, predicted, zero_division=0)),
    "recall": float(recall_score(target, predicted, zero_division=0)),
    "f1": float(f1_score(target, predicted, zero_division=0)),
    "roc_auc": float(roc_auc_score(target, probabilities)),
    "pr_auc": float(average_precision_score(target, probabilities)),
    "true_negative": int(true_negative),
    "false_positive": int(false_positive),
    "false_negative": int(false_negative),
    "true_positive": int(true_positive),
  }


def _threshold_table(target: pd.Series, probabilities: Any, run: str) -> list[dict[str, Any]]:
  precision, recall, thresholds = precision_recall_curve(target, probabilities)
  rows = []
  for index, threshold in enumerate(thresholds):
    rows.append({
      "run": run,
      "threshold": float(threshold),
      "precision": float(precision[index]),
      "recall": float(recall[index]),
      "f1": float(
        0.0
        if precision[index] + recall[index] == 0
        else 2 * precision[index] * recall[index] / (precision[index] + recall[index])
      ),
    })
  return rows


def _recommended_threshold(rows: list[dict[str, Any]]) -> float:
  if not rows:
    raise ValueError("Could not select a decision threshold")
  best = max(rows, key=lambda row: (row["f1"], row["recall"], row["precision"]))
  return float(best["threshold"])


def _save_diagnostic_plot(
    target: pd.Series,
    predicted: Any,
    probabilities: Any,
    path: Path,
    title: str,
) -> None:
  figure, axes = plt.subplots(1, 3, figsize=(15, 4.5))
  ConfusionMatrixDisplay.from_predictions(target, predicted, ax=axes[0], colorbar=False)
  axes[0].set_title("Matriz de confusão")
  false_positive_rate, true_positive_rate, _ = roc_curve(target, probabilities)
  axes[1].plot(false_positive_rate, true_positive_rate)
  axes[1].plot([0, 1], [0, 1], linestyle="--", color="gray")
  axes[1].set(xlabel="Falso positivo", ylabel="Verdadeiro positivo", title="Curva ROC")
  precision, recall, _ = precision_recall_curve(target, probabilities)
  axes[2].plot(recall, precision)
  axes[2].set(xlabel="Recall", ylabel="Precisão", title="Curva precisão-recall")
  figure.suptitle(title)
  figure.tight_layout()
  figure.savefig(path, dpi=160)
  plt.close(figure)


def _save_feature_importance(best: _BestRun, path: Path, random_state: int) -> None:
  if best.estimator is None or best.test_x is None or best.test_y is None:
    return
  importance = permutation_importance(
    best.estimator,
    best.test_x,
    best.test_y,
    scoring="average_precision",
    n_repeats=10,
    random_state=random_state,
    n_jobs=1,
  )
  pd.DataFrame({
    "feature": best.test_x.columns,
    "importance_mean": importance.importances_mean,
    "importance_std": importance.importances_std,
  }).sort_values("importance_mean", ascending=False).to_csv(path, index=False)


def _save_comparison_plot(results: pd.DataFrame, path: Path) -> None:
  top = results.head(12).copy()
  labels = (
    top["treatment"] + " | " + top["model"] + " | " + top["balancing"]
  )
  figure, axis = plt.subplots(figsize=(12, 7))
  positions = range(len(top))
  axis.barh(list(positions), top["cv_pr_auc_mean"], label="CV PR-AUC")
  axis.scatter(
    top["cv_recall_mean"],
    list(positions),
    color="#d62728",
    label="CV Recall",
    zorder=3,
  )
  axis.set_yticks(list(positions), labels)
  axis.invert_yaxis()
  axis.set_xlim(0, 1)
  axis.set_title("Melhores configurações por PR-AUC")
  axis.legend()
  figure.tight_layout()
  figure.savefig(path, dpi=160)
  plt.close(figure)


def _safe_fold_count(target: pd.Series, requested: int) -> int:
  minority = int(target.value_counts().min())
  folds = min(requested, minority)
  if folds < 2:
    raise ValueError("Each fraud class must contain at least 2 examples")
  return folds


def _validate_config(config: EvaluationConfig) -> None:
  if not config.dataset_path.is_file():
    raise FileNotFoundError(f"Dataset not found: {config.dataset_path}")
  if not 0 < config.test_size < 1:
    raise ValueError("Test size must be between 0 and 1")
  if config.threshold is not None and not 0 < config.threshold < 1:
    raise ValueError("Threshold must be between 0 and 1")
  if config.folds < 2:
    raise ValueError("At least 2 folds are required")
