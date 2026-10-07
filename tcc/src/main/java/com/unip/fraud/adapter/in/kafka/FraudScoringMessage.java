package com.unip.fraud.adapter.in.kafka;

import java.util.List;

public record FraudScoringMessage(
    String transactionId,
    Boolean realFraud,
    Boolean predictedFraud,
    Double probability,
    Double riskScore,
    String scoreType,
    String riskLevel,
    Double threshold,
    String classification,
    String modelVersion,
    List<String> reasons,
    String explanation,
    String explanationType,
    String explanationModel
) {
}
