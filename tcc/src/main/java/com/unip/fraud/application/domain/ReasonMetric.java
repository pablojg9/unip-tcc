package com.unip.fraud.application.domain;

public record ReasonMetric(
    String reason,
    long occurrences
) {
}
