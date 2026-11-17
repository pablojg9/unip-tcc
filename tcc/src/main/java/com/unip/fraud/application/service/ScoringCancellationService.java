package com.unip.fraud.application.service;

import com.unip.fraud.adapter.out.persistence.entity.ImportJobEntity;
import com.unip.fraud.adapter.out.persistence.repository.ImportJobRepository;
import com.unip.fraud.application.domain.ImportJobView;
import com.unip.fraud.application.domain.ImportStatus;
import com.unip.fraud.application.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.unip.fraud.application.validation.Validation.requireArgument;

@Service
public class ScoringCancellationService {
  private static final int CHUNK_SIZE = 250;

  private final ImportJobRepository imports;
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;
  private final String topic;

  public ScoringCancellationService(
      final ImportJobRepository imports,
      final JdbcTemplate jdbc,
      final KafkaTemplate<String, String> kafka,
      final ObjectMapper mapper,
      @Value("${fraud.kafka.scoring-cancellations-topic}") final String topic) {
    this.imports = imports;
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.mapper = mapper;
    this.topic = topic;
  }

  @Transactional
  public ImportJobView cancel(final UUID importId) {
    final ImportJobEntity job = imports.findById(importId)
        .orElseThrow(() -> new ResourceNotFoundException("Import not found"));
    requireArgument(
        job.getStatus().equals(ImportStatus.COMPLETED.name())
            || job.getStatus().equals(ImportStatus.COMPLETED_WITH_WARNINGS.name()),
        "Wait for import to finish before cancelling its scoring"
    );
    if (job.getScoringCancelledAt() != null) {
      return view(job);
    }

    final List<String> transactionIds = jdbc.queryForList("""
        SELECT transaction_id FROM silver.claim WHERE import_id = ? ORDER BY row_number
        """, String.class, importId);
    // The control events cover messages already published to Kafka, including old
    // imports whose transaction payload does not contain an importId.
    try {
      for (int start = 0; start < transactionIds.size(); start += CHUNK_SIZE) {
        final List<String> chunk = transactionIds.subList(
            start, Math.min(start + CHUNK_SIZE, transactionIds.size()));
        final String payload = mapper.writeValueAsString(Map.of(
            "importId", importId.toString(), "transactionIds", chunk));
        kafka.send(topic, importId + ":" + start, payload).get(30, TimeUnit.SECONDS);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Could not notify scoring worker of cancellation", exception);
    }

    jdbc.update("""
        UPDATE ops.transaction_outbox event SET status = 'CANCELLED'
        FROM silver.claim claim
        WHERE event.aggregate_id = claim.transaction_id
          AND claim.import_id = ? AND event.status = 'PENDING'
        """, importId);
    final LocalDateTime cancelledAt = LocalDateTime.now();
    jdbc.update("UPDATE ops.import_job SET scoring_cancelled_at = ? WHERE id = ?",
        cancelledAt, importId);
    return new ImportJobView(
        job.getId(), job.getFileName(), job.getStatus(), job.getTotalRows(),
        job.getProcessedRows(), job.getRejectedRows(), job.getErrorMessage(),
        job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(),
        job.getBatchExecutionId(), cancelledAt
    );
  }

  private ImportJobView view(final ImportJobEntity job) {
    return new ImportJobView(
        job.getId(), job.getFileName(), job.getStatus(), job.getTotalRows(),
        job.getProcessedRows(), job.getRejectedRows(), job.getErrorMessage(),
        job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(),
        job.getBatchExecutionId(), job.getScoringCancelledAt()
    );
  }
}
