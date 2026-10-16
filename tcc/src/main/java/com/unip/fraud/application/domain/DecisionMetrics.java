package com.unip.fraud.application.domain;

import java.util.List;

public record DecisionMetrics(
    boolean predictedFraud,
    long total,
    double percentage,
    double averageProbability,
    long lowRisk,
    long mediumRisk,
    long highRisk,
    String predominantModel,
    List<ReasonMetric> topReasons
) {
}
