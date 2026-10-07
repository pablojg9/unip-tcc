# Fraud detection service

Python service responsible for model training and Kafka inference. The Java
backend remains responsible for ingestion, medallion persistence and human
review.

## Architecture

```text
CLI / Kafka adapter
       |
       v
application services -> domain <- ports
       ^                            ^
       |                            |
dataset, Joblib and Kafka adapters  training strategies
```

Applied patterns:

- Hexagonal architecture and dependency inversion around datasets and model storage.
- Strategy + Factory to select supervised classification or anomaly detection.
- Repository for atomic, versioned model artifact persistence.
- Pipeline for identical preprocessing during training and inference.
- Consumer retry with manual Kafka offset commit and dead-letter topic.

## Training modes

If one configured target alias exists, the service validates binary labels and
trains a balanced Random Forest. Metrics are calculated on a stratified holdout,
then the production model is refitted on all labeled rows.

If the target does not exist, it trains an Isolation Forest. Its percentage is
an anomaly risk relative to the training population, not proof of fraud. For
that reason `predictedFraud` is `null` and the result requires human review.

CSV, XLS and XLSX are accepted. Column names are normalized with the same rules
as the Java service. Missing inference columns are imputed, future columns are
ignored until retraining, and a SHA-256 hash links each model to its dataset.
Every saved artifact also has a `*.metadata.json` manifest containing its
version, metrics, threshold and feature schema for audit or synchronization
with the Java `ml.model_registry` table.

For each score, the service calculates local evidence by replacing one feature
at a time with its pipeline reference value and measuring the score change.
When generative explanations are enabled, only those evidence sentences, the
score, risk and threshold are sent to the local Ollama model; raw claim fields
and identifiers are not included. A deterministic explanation is used when the
generator is disabled or unavailable, so explanation failures never interrupt
fraud scoring.

## Commands

From `ml-fraud-py`:

```bash
python -m pip install -r requirements.txt
python -m fraud_detection train --dataset ../fraud_scenario_1.csv
python -m fraud_detection consume
python -m fraud_detection serve
python -m fraud_detection inspect --dataset ../fraud_scenario_1.csv
python -m fraud_detection split --dataset ../sinistros.xlsx --output-directory split-data
python -m fraud_detection evaluate --dataset ../sinistros.xlsx --output-directory results
python -m unittest discover -s tests -v
```

The `split` command accepts CSV, XLS or XLSX (including multiple non-empty
worksheets), applies the quality and identifier rules, and writes stratified
`train.csv` and `test.csv` files. Known fraud target names are detected
automatically. For any other name, pass `--target-column "Column name"`; extra
identifier columns can be repeated with `--ignore-column "Column name"`.

The offline `evaluate` command requires a labeled dataset and compares Logistic
Regression, Decision Tree and Random Forest with no balancing, class weights and
SMOTE. It selects the best configuration using stratified cross-validation only
on the training partition. It then selects a threshold from out-of-fold training
predictions and evaluates the winner once on the untouched holdout. It writes
`metrics.csv`, `final_metrics.csv`, `thresholds.csv`, `feature_importance.csv`,
confusion/ROC/precision-recall plots and a JSON summary. Pass `--threshold` to
use a fixed value instead of automatic selection. This experiment does not
replace or activate the production model.

Before training, exact duplicate rows are removed, invalid age/calendar zero
markers are converted to missing values, and identifier columns are excluded.
The ignored column list is configurable and is applied to both production
training and offline evaluation.

The compatibility scripts remain available:

```bash
python train-model.py --dataset ../fraud_scenario_1.csv
python consumer.py
python consult-column.py --dataset ../fraud_scenario_1.csv
```

## Configuration

| Variable | Default |
| --- | --- |
| `FRAUD_MODEL_PATH` | `artifacts/fraud_model.joblib` |
| `FRAUD_TARGET_ALIASES` | `fraudfound_p,fraud,is_fraud,fraude,isfraud,label` |
| `FRAUD_RANDOM_STATE` | `42` |
| `FRAUD_TEST_SIZE` | `0.25` |
| `FRAUD_DECISION_THRESHOLD` | `0.5` |
| `FRAUD_IGNORED_FEATURES` | Known claim, policy, insured and representative IDs |
| `FRAUD_ANOMALY_CONTAMINATION` | `0.05` |
| `GENERATIVE_EXPLANATION_ENABLED` | `false` |
| `GENERATIVE_EXPLANATION_URL` | `http://ollama:11434/api/generate` |
| `GENERATIVE_EXPLANATION_MODEL` | `llama3.2:1b` |
| `GENERATIVE_EXPLANATION_TIMEOUT_SECONDS` | `8` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `KAFKA_TRANSACTIONS_TOPIC` | `transactions` |
| `KAFKA_RESULTS_TOPIC` | `fraud-results` |
| `KAFKA_CONSUMER_GROUP` | `ml-fraud-consumer` |
| `KAFKA_MESSAGE_MAX_RETRIES` | `3` |
| `KAFKA_TRAINING_REQUESTS_TOPIC` | `model-training-requests` |
| `KAFKA_TRAINING_RESULTS_TOPIC` | `model-training-results` |
| `KAFKA_ACTIVATION_REQUESTS_TOPIC` | `model-activation-requests` |
| `KAFKA_ACTIVATION_RESULTS_TOPIC` | `model-activation-results` |
| `FRAUD_CANDIDATE_DIRECTORY` | `artifacts/candidates` |

Messages that cannot be scored after the configured retries are published to
`transactions.DLT`. Results use the contract expected by the Java consumer:
`transactionId`, `realFraud`, `predictedFraud`, `probability`, `scoreType`,
`riskLevel`, `threshold`, `classification`, `modelVersion`, `reasons`,
`explanation`, `explanationType` and `explanationModel`.

## Local generative explanations

The normal stack keeps generative explanations disabled to avoid requiring a
large model download. To enable the optional local Ollama profile:

```bash
docker compose --profile generative-ai up -d ollama
docker compose --profile generative-ai exec ollama ollama pull llama3.2:1b
GENERATIVE_EXPLANATION_ENABLED=true docker compose --profile generative-ai up --build
```

`GENERATIVE` identifies an Ollama response; `DETERMINISTIC` means the feature
is disabled, and `FALLBACK` records that the local generator was unavailable.
