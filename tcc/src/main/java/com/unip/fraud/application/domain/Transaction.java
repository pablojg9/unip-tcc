package com.unip.fraud.application.domain;

import java.util.Map;

public record Transaction(
    String transactionId,
    Boolean realFraud,
    Map<String, Object> features
) {
}