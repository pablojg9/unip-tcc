package com.unip.fraud.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "rejected_record")
@Getter
@Setter
public class RejectedRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String fileName;

  @Column(columnDefinition = "TEXT")
  private String rawPayload;

  private String errorReason;
  private LocalDateTime rejectedAt;
}
