package com.unip.fraud.adapter.out.kafka;

import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class FraudResultKafkaConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(FraudResultKafkaConsumer.class);

  private final GoldRepositoryOutPort goldRepositoryOutPort;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @PostConstruct
  public void init() {
    log.info("FraudResultKafkaConsumer bean created successfully");
  }

  public FraudResultKafkaConsumer(final GoldRepositoryOutPort goldRepositoryOutPort) {
    this.goldRepositoryOutPort = goldRepositoryOutPort;
  }

  @KafkaListener(topics = "fraud-results", groupId = "fraud-api-group-id")
  public void consume(String message) {
    log.info("Received message from topic fraud-results: {}", message);

    try {
      Map<String, Object> json = objectMapper.readValue(message, Map.class);

      FraudResult result = new FraudResult(
          json.get("transactionId").toString(),
          Boolean.valueOf(json.get("realFraud").toString()),
          Boolean.valueOf(json.get("predictedFraud").toString()),
          Double.valueOf(json.get("probability").toString()),
          json.get("classification").toString(),
          LocalDateTime.now()
      );

      goldRepositoryOutPort.save(result);

      log.info("Fraud result saved on Gold. transactionId={}", result.transactionId());

    } catch (Exception exception) {
      log.error("Error processing fraud result message: {}", message, exception);
    }
  }
}
