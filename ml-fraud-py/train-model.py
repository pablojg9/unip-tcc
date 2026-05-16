import pandas as pd
import joblib

from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score


DATASET_PATH = "/Users/pablo/Documents/java-projects/unip-tcc/fraud_scenario_1.csv"
MODEL_PATH = "fraud_model.pkl"
MODEL_COLUMNS_PATH = "model_columns.pkl"
TARGET_COLUMN = "FraudFound_P"


def main():
    df = pd.read_csv(DATASET_PATH)

    print("Columns found:")
    print(df.columns.tolist())

    if TARGET_COLUMN not in df.columns:
        raise Exception(f"Target column '{TARGET_COLUMN}' not found.")

    X = df.drop(TARGET_COLUMN, axis=1)
    y = df[TARGET_COLUMN]

    # Converte colunas categóricas/texto para números
    X = pd.get_dummies(X)

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.2,
        random_state=42
    )

    model = RandomForestClassifier(
        n_estimators=100,
        random_state=42
    )

    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)

    print("Model metrics:")
    print("Accuracy:", accuracy_score(y_test, y_pred))
    print("Precision:", precision_score(y_test, y_pred, zero_division=0))
    print("Recall:", recall_score(y_test, y_pred, zero_division=0))
    print("F1-score:", f1_score(y_test, y_pred, zero_division=0))

    joblib.dump(model, MODEL_PATH)
    joblib.dump(X.columns.tolist(), MODEL_COLUMNS_PATH)

    print("Model saved:", MODEL_PATH)
    print("Model columns saved:", MODEL_COLUMNS_PATH)


if __name__ == "__main__":
    main()