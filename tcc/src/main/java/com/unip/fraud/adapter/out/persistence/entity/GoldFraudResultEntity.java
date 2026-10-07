package com.unip.fraud.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "fraud_prediction", schema = "gold")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GoldFraudResultEntity {

  @Id
  @Column(length = 64, nullable = false)
  private String transactionId;

  @Column(name = "confirmed_fraud")
  private Boolean realFraud;
  private Boolean predictedFraud;
  @Column(name = "risk_score", nullable = false)
  private Double probability;
  @Column(length = 32, nullable = false)
  private String scoreType;

  @Column(length = 16, nullable = false)
  private String riskLevel;
  private Double threshold;
  private String classification;
  @Column(length = 64)
  private String modelVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private List<String> reasons;

  @Column(columnDefinition = "text", nullable = false)
  private String explanation;

  @Column(length = 32, nullable = false)
  private String explanationType;

  @Column(length = 120)
  private String explanationModel;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public void confirmFraud(final Boolean confirmedFraud) {
    this.realFraud = confirmedFraud;
  }
}
