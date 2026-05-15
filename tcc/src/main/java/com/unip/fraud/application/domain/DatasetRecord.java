package com.unip.fraud.application.domain;

public record DatasetRecord(
    String fileName,
    String[] columns,
    String[] values,
    String rawLine
) {
}
