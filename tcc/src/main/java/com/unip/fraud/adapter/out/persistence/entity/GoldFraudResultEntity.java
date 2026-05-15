package com.unip.fraud.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gold_fraud_result")
@Getter
@Setter
public class GoldFraudResultEntity {

  @Id
  private String transactionId;

  private Boolean realFraud;
  private Boolean predictedFraud;
  private Double probability;
  private String classification;
  private LocalDateTime processedAt;
}
