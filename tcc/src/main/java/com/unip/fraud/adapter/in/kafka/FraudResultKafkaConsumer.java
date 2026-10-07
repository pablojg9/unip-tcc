package com.unip.fraud.adapter.in.kafka;

import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import com.unip.fraud.application.validation.Validation;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FraudResultKafkaConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(FraudResultKafkaConsumer.class);
  private static final Map<Boolean, String> CLASSIFICATIONS = Map.of(
      true, "Suspicious transaction",
      false, "Normal transaction"
  );

  private final GoldRepositoryOutPort goldRepositoryOutPort;
  private final ObjectMapper objectMapper;

  @PostConstruct
  public void init() {
    log.info("FraudResultKafkaConsumer bean created successfully");
  }

  public FraudResultKafkaConsumer(
      final GoldRepositoryOutPort goldRepositoryOutPort,
      final ObjectMapper objectMapper) {
    this.goldRepositoryOutPort = goldRepositoryOutPort;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = "${fraud.kafka.results-topic}",
      groupId = "${spring.kafka.consumer.group-id}"
  )
  public void consume(final String message) {
    log.info("Received message from topic fraud-results: {}", message);

    try {
      final FraudScoringMessage scoring = objectMapper.readValue(
          message,
          FraudScoringMessage.class
      );

      final Double probability = probability(scoring);
      final Boolean predictedFraud = scoring.predictedFraud();
      final String riskLevel = stringValue(
          scoring.riskLevel(),
          probability >= 0.7 ? "HIGH" : probability >= 0.3 ? "MEDIUM" : "LOW"
      );
      final List<String> reasons = scoring.reasons() == null
          ? List.of()
          : List.copyOf(scoring.reasons());

      FraudResult result = new FraudResult(
          scoring.transactionId(),
          scoring.realFraud(),
          predictedFraud,
          probability,
          stringValue(scoring.scoreType(), "FRAUD_PROBABILITY"),
          riskLevel,
          scoring.threshold(),
          stringValue(
              scoring.classification(),
              classification(predictedFraud)
          ),
          stringValue(scoring.modelVersion(), "legacy-model"),
          reasons,
          stringValue(scoring.explanation(), String.join(" ", reasons)),
          stringValue(scoring.explanationType(), "DETERMINISTIC"),
          scoring.explanationModel(),
          LocalDateTime.now()
      );

      goldRepositoryOutPort.save(result);

      log.info("Fraud result saved on Gold. transactionId={}", result.transactionId());

    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid fraud result message", exception);
    }
  }

  private String stringValue(final String value, final String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String classification(final Boolean predictedFraud) {
    return Optional.ofNullable(predictedFraud)
        .map(CLASSIFICATIONS::get)
        .orElse("Review required");
  }

  private Double probability(final FraudScoringMessage scoring) {
    final Double rawValue = Optional.ofNullable(scoring.probability())
        .orElse(scoring.riskScore());
    final double value = Optional.ofNullable(rawValue).orElse(0.0);
    final double normalized = value > 1 ? value / 100.0 : value;
    Validation.requireArgument(
        Double.isFinite(normalized) && normalized >= 0 && normalized <= 1,
        "Fraud score must be between 0 and 1, or 0 and 100"
    );
    return normalized;
  }
}
