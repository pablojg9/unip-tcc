package com.unip.fraud.adapter.out.persistence.mapper;

import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.domain.FraudReviewCommand;
import com.unip.fraud.application.domain.ImportJob;
import com.unip.fraud.application.domain.ImportStatus;
import com.unip.fraud.application.domain.ReviewDecision;
import com.unip.fraud.application.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMapperTest {

  private final SilverEntityMapper silverMapper = Mappers.getMapper(SilverEntityMapper.class);
  private final GoldEntityMapper goldMapper = Mappers.getMapper(GoldEntityMapper.class);
  private final ImportJobEntityMapper importMapper = Mappers.getMapper(
      ImportJobEntityMapper.class
  );
  private final FraudReviewEntityMapper reviewMapper = Mappers.getMapper(
      FraudReviewEntityMapper.class
  );

  @Test
  void mapsSilverEntityWithoutManualSetters() {
    final UUID importId = UUID.randomUUID();
    final LocalDateTime processedAt = LocalDateTime.now();
    final Transaction transaction = new Transaction(
        "claim-1",
        null,
        new LinkedHashMap<>(Map.of("amount", 1250))
    );

    final var entity = silverMapper.toEntity(
        importId,
        2,
        "claims.xlsx",
        transaction,
        processedAt
    );
    final var claim = silverMapper.toClaimRecord(entity);

    assertThat(claim.transactionId()).isEqualTo("claim-1");
    assertThat(claim.importId()).isEqualTo(importId);
    assertThat(claim.sourceFile()).isEqualTo("claims.xlsx");
    assertThat(claim.data()).containsEntry("amount", 1250);
  }

  @Test
  void mapsGoldEntityAndNormalizesNullReasons() {
    final FraudResult result = new FraudResult(
        "claim-2",
        null,
        true,
        0.91,
        "FRAUD_PROBABILITY",
        "HIGH",
        0.5,
        "Suspicious transaction",
        "model-1",
        null,
        "Explicação local",
        "DETERMINISTIC",
        null,
        LocalDateTime.now()
    );

    final FraudResult mapped = goldMapper.toDomain(goldMapper.toEntity(result));

    assertThat(mapped.reasons()).isEqualTo(List.of());
    assertThat(mapped.probability()).isEqualTo(0.91);
  }

  @Test
  void mapsImportStatusInBothDirections() {
    final ImportJob importJob = new ImportJob(
        UUID.randomUUID(),
        "claims.csv",
        "/tmp/claims.csv",
        "hash",
        ImportStatus.QUEUED,
        0,
        0,
        0,
        null,
        LocalDateTime.now(),
        null,
        null,
        null
    );

    final ImportJob mapped = importMapper.toDomain(importMapper.toEntity(importJob));

    assertThat(mapped).isEqualTo(importJob);
  }

  @Test
  void mapsReviewDecisionInBothDirections() {
    final FraudReviewCommand command = new FraudReviewCommand(
        "claim-3",
        ReviewDecision.FRAUD,
        "Confirmed by analyst",
        "reviewer"
    );
    final LocalDateTime reviewedAt = LocalDateTime.now();

    final var review = reviewMapper.toDomain(reviewMapper.toEntity(command, reviewedAt));

    assertThat(review.transactionId()).isEqualTo("claim-3");
    assertThat(review.decision()).isEqualTo(ReviewDecision.FRAUD);
    assertThat(review.reviewedAt()).isEqualTo(reviewedAt);
  }
}
