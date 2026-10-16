package com.unip.fraud.application.domain;

public record DashboardSummary(
    long totalBronze,
    long totalSilver,
    long totalGold,
    long totalRejected,
    long realFraudsFromDataset,
    long predictedFraudsByAi,
    long predictedLegitimateByAi,
    long pendingPrediction,
    double predictedFraudPercentage,
    long lowRisk,
    long mediumRisk,
    long highRisk,
    long pendingReview
) {
}
