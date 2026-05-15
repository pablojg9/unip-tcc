package com.unip.fraud.adapter.out.kafka;

import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class FraudResultKafkaConsumer {

  private final GoldRepositoryOutPort goldRepositoryOutPort;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FraudResultKafkaConsumer(final GoldRepositoryOutPort goldRepositoryOutPort) {
    this.goldRepositoryOutPort = goldRepositoryOutPort;
  }

  @KafkaListener(topics = "fraud-results", groupId = "fraud-api")
  public void consume(String message) {
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

    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

}
