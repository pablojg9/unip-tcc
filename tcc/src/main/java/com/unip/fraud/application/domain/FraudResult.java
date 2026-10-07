package com.unip.fraud.application.domain;

import java.time.LocalDateTime;
import java.util.List;

public record FraudResult(
    String transactionId,
    Boolean realFraud,
    Boolean predictedFraud,
    Double probability,
    String scoreType,
    String riskLevel,
    Double threshold,
    String classification,
    String modelVersion,
    List<String> reasons,
    String explanation,
    String explanationType,
    String explanationModel,
    LocalDateTime processedAt
){
}
