import json
import time

import joblib
import pandas as pd

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable


KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"

INPUT_TOPIC = "transactions"
OUTPUT_TOPIC = "fraud-results"

MODEL_PATH = "fraud_model.pkl"
MODEL_COLUMNS_PATH = "model_columns.pkl"


def create_consumer():
    while True:
        try:
            return KafkaConsumer(
                INPUT_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_deserializer=lambda value: json.loads(value.decode("utf-8")),
                auto_offset_reset="earliest",
                group_id="ml-fraud-consumer"
            )
        except NoBrokersAvailable:
            print("Kafka not available yet. Retrying in 5 seconds...")
            time.sleep(5)


def create_producer():
    while True:
        try:
            return KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda value: json.dumps(value).encode("utf-8")
            )
        except NoBrokersAvailable:
            print("Kafka not available yet. Retrying in 5 seconds...")
            time.sleep(5)


def prepare_features(features, model_columns):
    df = pd.DataFrame([features])

    # Converte texto para dummies
    df = pd.get_dummies(df)

    # Alinha as colunas com as colunas usadas no treino
    df = df.reindex(columns=model_columns, fill_value=0)

    return df


def predict_transaction(model, model_columns, transaction):
    transaction_id = transaction["transactionId"]
    real_fraud = transaction.get("realFraud")
    features = transaction["features"]

    df = prepare_features(features, model_columns)

    prediction = model.predict(df)[0]
    probability = model.predict_proba(df)[0][1]

    return {
        "transactionId": transaction_id,
        "realFraud": real_fraud,
        "predictedFraud": bool(prediction),
        "probability": round(float(probability), 4),
        "classification": "Suspicious transaction" if prediction == 1 else "Normal transaction"
    }


def main():
    model = joblib.load(MODEL_PATH)
    model_columns = joblib.load(MODEL_COLUMNS_PATH)

    consumer = create_consumer()
    producer = create_producer()

    print("Python ML consumer is running...")
    print(f"Listening topic: {INPUT_TOPIC}")

    for message in consumer:
        try:
            transaction = message.value

            result = predict_transaction(
                model,
                model_columns,
                transaction
            )

            producer.send(OUTPUT_TOPIC, result)
            producer.flush()

            print("Processed result:", result)

        except Exception as exception:
            print("Error processing transaction:", exception)


if __name__ == "__main__":
    main()