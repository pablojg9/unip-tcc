package com.unip.fraud.adapter.in.kafka;

import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.domain.FraudResultFilter;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudResultKafkaConsumerTest {

  @Test
  void keepsConfirmedFraudUnknownForAnomalyScores() {
    final FakeGoldRepository repository = new FakeGoldRepository();
    final FraudResultKafkaConsumer consumer = new FraudResultKafkaConsumer(
        repository,
        new ObjectMapper()
    );

    consumer.consume("""
        {
          "transactionId": "claim-1",
          "realFraud": null,
          "predictedFraud": null,
          "riskScore": 82.5,
          "scoreType": "ANOMALY_SCORE",
          "riskLevel": "HIGH",
          "modelVersion": "anomaly-1",
          "reasons": ["Unusual amount"],
          "explanation": "O valor atípico elevou o risco e requer revisão humana.",
          "explanationType": "GENERATIVE",
          "explanationModel": "llama3.2:1b"
        }
        """);

    assertThat(repository.saved.realFraud()).isNull();
    assertThat(repository.saved.predictedFraud()).isNull();
    assertThat(repository.saved.probability()).isEqualTo(0.825);
    assertThat(repository.saved.scoreType()).isEqualTo("ANOMALY_SCORE");
    assertThat(repository.saved.reasons()).containsExactly("Unusual amount");
    assertThat(repository.saved.explanationType()).isEqualTo("GENERATIVE");
    assertThat(repository.saved.explanationModel()).isEqualTo("llama3.2:1b");
  }

  @Test
  void rejectsScoresOutsideTheSupportedPercentageRange() {
    final FraudResultKafkaConsumer consumer = new FraudResultKafkaConsumer(
        new FakeGoldRepository(),
        new ObjectMapper()
    );

    assertThatThrownBy(() -> consumer.consume("""
        {
          "transactionId": "claim-invalid",
          "riskScore": 150
        }
        """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid fraud result message")
        .hasRootCauseMessage("Fraud score must be between 0 and 1, or 0 and 100");
  }

  private static final class FakeGoldRepository implements GoldRepositoryOutPort {
    private FraudResult saved;

    @Override
    public void save(FraudResult result) {
      saved = result;
    }

    @Override
    public long count() {
      return saved == null ? 0 : 1;
    }

    @Override
    public long countRealFraud() {
      return 0;
    }

    @Override
    public long countPredictedFraud() {
      return 0;
    }

    @Override
    public long countByRiskLevel(String riskLevel) {
      return 0;
    }

    @Override
    public long countPendingReview() {
      return 0;
    }

    @Override
    public PageResponse<FraudResult> findByFilter(
        FraudResultFilter filter,
        int page,
        int size
    ) {
      final List<FraudResult> values = saved == null ? List.of() : List.of(saved);
      return new PageResponse<>(values, page, size, values.size(), 1);
    }

    @Override
    public Optional<FraudResult> findByTransactionId(String transactionId) {
      return Optional.ofNullable(saved);
    }

    @Override
    public void updateConfirmedFraud(String transactionId, Boolean confirmedFraud) {
      // Not needed by this adapter test.
    }
  }
}
