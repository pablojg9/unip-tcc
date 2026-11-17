package com.unip.fraud.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class KafkaTopicConfig {

  private final String transactionsTopic;
  private final String resultsTopic;
  private final String cancellationsTopic;
  private final String trainingRequestsTopic;
  private final String trainingResultsTopic;
  private final String activationRequestsTopic;
  private final String activationResultsTopic;

  public KafkaTopicConfig(
      @Value("${fraud.kafka.transactions-topic}") final String transactionsTopic,
      @Value("${fraud.kafka.results-topic}") final String resultsTopic,
      @Value("${fraud.kafka.scoring-cancellations-topic}") final String cancellationsTopic,
      @Value("${fraud.kafka.training-requests-topic}") final String trainingRequestsTopic,
      @Value("${fraud.kafka.training-results-topic}") final String trainingResultsTopic,
      @Value("${fraud.kafka.activation-requests-topic}") final String activationRequestsTopic,
      @Value("${fraud.kafka.activation-results-topic}") final String activationResultsTopic) {
    this.transactionsTopic = transactionsTopic;
    this.resultsTopic = resultsTopic;
    this.cancellationsTopic = cancellationsTopic;
    this.trainingRequestsTopic = trainingRequestsTopic;
    this.trainingResultsTopic = trainingResultsTopic;
    this.activationRequestsTopic = activationRequestsTopic;
    this.activationResultsTopic = activationResultsTopic;
  }

  @Bean
  public NewTopic transactionsTopic() {
    return TopicBuilder.name(transactionsTopic)
        .partitions(3)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic fraudResultsTopic() {
    return TopicBuilder.name(resultsTopic)
        .partitions(3)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic fraudResultsDeadLetterTopic() {
    return TopicBuilder.name(resultsTopic + ".DLT")
        .partitions(3)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic scoringCancellationsTopic() {
    return TopicBuilder.name(cancellationsTopic).partitions(1).replicas(1)
        .config("cleanup.policy", "compact").build();
  }

  @Bean
  public NewTopic modelTrainingRequestsTopic() {
    return lifecycleTopic(trainingRequestsTopic);
  }

  @Bean
  public NewTopic modelTrainingResultsTopic() {
    return lifecycleTopic(trainingResultsTopic);
  }

  @Bean
  public NewTopic modelActivationRequestsTopic() {
    return lifecycleTopic(activationRequestsTopic);
  }

  @Bean
  public NewTopic modelActivationResultsTopic() {
    return lifecycleTopic(activationResultsTopic);
  }

  private NewTopic lifecycleTopic(final String name) {
    return TopicBuilder.name(name).partitions(1).replicas(1).build();
  }
}
