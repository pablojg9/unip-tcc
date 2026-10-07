package com.unip.fraud.application.domain;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public record FraudResultDetail(
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
    LocalDateTime processedAt,
    String sourceFile,
    LocalDateTime ingestedAt,
    Map<String, Object> features
) {
}
