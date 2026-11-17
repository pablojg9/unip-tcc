package com.unip.fraud.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_job", schema = "ops")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImportJobEntity {

  @Id
  private UUID id;

  @Column(length = 255, nullable = false)
  private String fileName;
  @Column(columnDefinition = "TEXT", nullable = false)
  private String storedPath;
  @Column(length = 64, nullable = false)
  private String fileHash;

  @Column(length = 32, nullable = false)
  private String status;
  private long totalRows;
  private long processedRows;
  private long rejectedRows;
  @Column(columnDefinition = "TEXT")
  private String errorMessage;
  @Column(nullable = false)
  private LocalDateTime createdAt;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private Long batchExecutionId;
  private LocalDateTime scoringCancelledAt;

  public void markProcessing(final LocalDateTime startedAt) {
    this.status = "PROCESSING";
    this.startedAt = startedAt;
  }

  public void markQueued() {
    this.status = "QUEUED";
    this.errorMessage = null;
    this.finishedAt = null;
  }

  public void linkBatchExecution(final long batchExecutionId) {
    this.batchExecutionId = batchExecutionId;
  }

  public void markFinished(
      final String status,
      final long processedRows,
      final long rejectedRows,
      final String errorMessage,
      final LocalDateTime finishedAt) {
    this.status = status;
    this.processedRows = processedRows;
    this.rejectedRows = rejectedRows;
    this.totalRows = processedRows + rejectedRows;
    this.errorMessage = errorMessage;
    this.finishedAt = finishedAt;
  }
}
