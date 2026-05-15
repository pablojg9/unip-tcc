package com.unip.fraud.application.domain;

import java.time.LocalDateTime;

public record FraudResult(
    String transactionId,
    Boolean realFraud,
    Boolean predictedFraud,
    Double probability,
    String classification,
    LocalDateTime processedAt
){
}
