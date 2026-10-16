CREATE INDEX IF NOT EXISTS idx_gold_fraud_prediction_decisions
    ON gold.fraud_prediction (predicted_fraud, confirmed_fraud);

CREATE INDEX IF NOT EXISTS idx_gold_fraud_prediction_category
    ON gold.fraud_prediction (predicted_fraud, risk_level, model_version);
