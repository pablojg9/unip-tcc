package com.unip.fraud.application.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record ImportJob(
    UUID id,
    String fileName,
    String storedPath,
    String fileHash,
    ImportStatus status,
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
  public static ImportJob queued(final UUID id, final StoredImportFile file) {
    return new ImportJob(
        id,
        file.fileName(),
        file.path(),
        file.sha256(),
        ImportStatus.QUEUED,
        0,
        0,
        0,
        null,
        LocalDateTime.now(),
        null,
        null,
        null,
        null
    );
  }

  public ImportJobView toView() {
    return new ImportJobView(
        id,
        fileName,
        status.name(),
        totalRows,
        processedRows,
        rejectedRows,
        errorMessage,
        createdAt,
        startedAt,
        finishedAt,
        batchExecutionId,
        scoringCancelledAt
    );
  }
}
