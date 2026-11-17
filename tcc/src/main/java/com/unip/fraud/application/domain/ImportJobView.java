package com.unip.fraud.application.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record ImportJobView(
    UUID importId,
    String fileName,
    String status,
    long totalRows,
    long processedRows,
    long rejectedRows,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long batchExecutionId,
    LocalDateTime scoringCancelledAt
) {
}
