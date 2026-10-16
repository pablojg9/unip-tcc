package com.unip.fraud.application.domain;

import java.time.LocalDateTime;

public record FraudResultFilter(
    Boolean predictedFraud,
    Boolean realFraud,
    Double minProbability,
    LocalDateTime startDate,
    LocalDateTime endDate,
    String riskLevel,
    String modelVersion,
    String reviewStatus
) {
}
