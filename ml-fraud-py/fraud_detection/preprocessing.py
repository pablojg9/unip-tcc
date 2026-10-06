from __future__ import annotations

from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler


def build_preprocessor(
    numeric_columns: tuple[str, ...],
    categorical_columns: tuple[str, ...],
    *,
    scale_numeric: bool,
    sparse_output: bool = True,
) -> ColumnTransformer:
  transformers: list[tuple[str, Pipeline, list[str]]] = []
  if numeric_columns:
    numeric_steps: list[tuple[str, object]] = [("imputer", SimpleImputer(strategy="median"))]
    if scale_numeric:
      numeric_steps.append(("scaler", StandardScaler()))
    transformers.append(("numeric", Pipeline(numeric_steps), list(numeric_columns)))
  if categorical_columns:
    transformers.append(
      (
        "categorical",
        Pipeline(
          [
            ("imputer", SimpleImputer(strategy="most_frequent")),
            (
              "one_hot",
              OneHotEncoder(handle_unknown="ignore", sparse_output=sparse_output),
            ),
          ]
        ),
        list(categorical_columns),
      )
    )
  if not transformers:
    raise ValueError("Dataset has no usable feature columns")
  return ColumnTransformer(transformers=transformers, remainder="drop")
