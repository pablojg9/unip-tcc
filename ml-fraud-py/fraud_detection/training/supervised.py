from __future__ import annotations

from datetime import UTC, datetime

import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

from fraud_detection.domain import ModelBundle, ModelType
from fraud_detection.normalization import coerce_feature_types
from fraud_detection.preprocessing import build_preprocessor


class SupervisedTrainingStrategy:
    def __init__(
        self,
        random_state: int,
        test_size: float,
        decision_threshold: float = 0.5,
    ) -> None:
        self._random_state = random_state
        self._test_size = test_size
        self._decision_threshold = decision_threshold

    def train(
        self,
        features: pd.DataFrame,
        target: pd.Series | None,
        target_column: str | None,
    ) -> ModelBundle:
        if target is None or target_column is None:
            raise ValueError("Supervised training requires a target column")
        labeled = target.notna()
        features = features.loc[labeled].reset_index(drop=True)
        target = target.loc[labeled].astype(int).reset_index(drop=True)
        if len(features) < 8:
            raise ValueError("Supervised training requires at least 8 labeled rows")
        if target.nunique() != 2:
            raise ValueError("Fraud target must contain both legitimate and fraud examples")
        class_counts = target.value_counts()
        if class_counts.min() < 2:
            raise ValueError("Each fraud class must contain at least 2 examples")

        typed, numeric, categorical = coerce_feature_types(features)
        train_x, test_x, train_y, test_y = train_test_split(
            typed,
            target,
            test_size=self._test_size,
            random_state=self._random_state,
            stratify=target,
        )
        pipeline = Pipeline(
            [
                ("preprocessor", build_preprocessor(numeric, categorical, scale_numeric=False)),
                (
                    "model",
                    RandomForestClassifier(
                        n_estimators=300,
                        class_weight="balanced_subsample",
                        min_samples_leaf=2,
                        random_state=self._random_state,
                        n_jobs=-1,
                    ),
                ),
            ]
        )
        pipeline.fit(train_x, train_y)
        probabilities = pipeline.predict_proba(test_x)[:, 1]
        predicted = probabilities >= self._decision_threshold
        true_negative, false_positive, false_negative, true_positive = confusion_matrix(
            test_y, predicted, labels=[0, 1]
        ).ravel()
        metrics = {
            "accuracy": float(accuracy_score(test_y, predicted)),
            "precision": float(precision_score(test_y, predicted, zero_division=0)),
            "recall": float(recall_score(test_y, predicted, zero_division=0)),
            "f1": float(f1_score(test_y, predicted, zero_division=0)),
            "pr_auc": float(average_precision_score(test_y, probabilities)),
            "true_negative": float(true_negative),
            "false_positive": float(false_positive),
            "false_negative": float(false_negative),
            "true_positive": float(true_positive),
        }
        if test_y.nunique() == 2:
            metrics["roc_auc"] = float(roc_auc_score(test_y, probabilities))

        # Evaluate on unseen data, then refit the persisted model on every
        # labeled row so production does not discard the holdout examples.
        pipeline.fit(typed, target)

        return ModelBundle(
            model_version=f"supervised-rf-{datetime.now(UTC):%Y%m%d%H%M%S}",
            model_type=ModelType.SUPERVISED,
            pipeline=pipeline,
            feature_columns=tuple(typed.columns),
            numeric_columns=numeric,
            categorical_columns=categorical,
            threshold=self._decision_threshold,
            metrics=metrics,
            target_column=target_column,
            important_features=_important_features(pipeline),
        )


def _important_features(pipeline: Pipeline, limit: int = 5) -> tuple[str, ...]:
    preprocessor = pipeline.named_steps["preprocessor"]
    model = pipeline.named_steps["model"]
    names = preprocessor.get_feature_names_out()
    ordered = sorted(
        zip(names, model.feature_importances_, strict=True),
        key=lambda item: item[1],
        reverse=True,
    )
    return tuple(name.split("__", 1)[-1] for name, importance in ordered[:limit] if importance > 0)
