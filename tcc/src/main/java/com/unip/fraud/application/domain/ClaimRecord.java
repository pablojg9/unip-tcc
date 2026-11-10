package com.unip.fraud.application.domain;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ClaimRecord(
    String transactionId,
    UUID importId,
    long rowNumber,
    String sourceFile,
    Boolean confirmedFraud,
    Boolean predictedFraud,
    String classification,
    Map<String, Object> data,
    LocalDateTime processedAt
) {
}
