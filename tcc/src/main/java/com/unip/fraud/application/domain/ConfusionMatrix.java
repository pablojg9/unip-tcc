package com.unip.fraud.application.domain;

public record ConfusionMatrix(
    long truePositive,
    long falsePositive,
    long trueNegative,
    long falseNegative,
    long evaluated,
    double accuracy,
    double precision,
    double recall,
    double f1Score
) {
}
