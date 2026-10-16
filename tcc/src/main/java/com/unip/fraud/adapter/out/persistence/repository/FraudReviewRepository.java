package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.FraudReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudReviewRepository extends JpaRepository<FraudReviewEntity, Long> {
  List<FraudReviewEntity> findAllByTransactionIdOrderByReviewedAtDesc(String transactionId);
}
