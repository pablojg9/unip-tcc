package com.unip.fraud.adapter.in.kafka;

import com.unip.fraud.application.domain.Transaction;
import com.unip.fraud.application.port.out.producer.TransactionProducerInPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TransactionKafkaProducer implements TransactionProducerInPort {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TransactionKafkaProducer(
      final KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void send(final Transaction transaction) {
    try {
      final Map<String, Object> event = new LinkedHashMap<>();
      event.put("transactionId", transaction.transactionId());
      event.put("realFraud", transaction.realFraud());
      event.put("features", transaction.features());

      String json = objectMapper.writeValueAsString(event);
      kafkaTemplate.send("transactions", transaction.transactionId(), json);
      System.out.println("Transaction sent to Kafka: " + json);
    } catch (Exception exception) {
      throw new RuntimeException(
          "Error sending transaction to Kafka",
          exception
      );
    }
  }
}
