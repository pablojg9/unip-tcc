package com.unip.fraud.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "silver_transaction")
@Getter
@Setter
public class SilverTransactionEntity {

  @Id
  private String transactionId;

  @Column(columnDefinition = "TEXT")
  private String featuresJson;

  private Boolean realFraud;
  private String fileName;
  private LocalDateTime processedAt;
}
