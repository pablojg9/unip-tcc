package com.unip.fraud.application.service;

import com.unip.fraud.adapter.out.persistence.entity.ImportJobEntity;
import com.unip.fraud.adapter.out.persistence.repository.ImportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoringCancellationServiceTest {

  @Test
  void publishesIdsBeforeMarkingTheImportCancelled() {
    final UUID importId = UUID.randomUUID();
    final ImportJobRepository imports = mock(ImportJobRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final ImportJobEntity job = ImportJobEntity.builder()
        .id(importId).fileName("claims.csv").status("COMPLETED")
        .createdAt(LocalDateTime.now()).build();
    when(imports.findById(importId)).thenReturn(Optional.of(job));
    when(jdbc.queryForList(anyString(), eq(String.class), eq(importId)))
        .thenReturn(List.of("claim-1", "claim-2"));
    when(kafka.send(eq("scoring-cancellations"), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var result = new ScoringCancellationService(
        imports, jdbc, kafka, new ObjectMapper(), "scoring-cancellations"
    ).cancel(importId);

    assertThat(result.scoringCancelledAt()).isNotNull();
    final var order = inOrder(kafka, jdbc);
    order.verify(kafka).send(eq("scoring-cancellations"),
        eq(importId + ":0"), org.mockito.ArgumentMatchers.contains("claim-1"));
    order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE ops.transaction_outbox"),
        eq(importId));
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("scoring_cancelled_at"),
        eq(result.scoringCancelledAt()), eq(importId));
  }
}
