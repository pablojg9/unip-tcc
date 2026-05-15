package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.GoldFraudResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoldFraudResultRepository extends JpaRepository<GoldFraudResultEntity, Long> {

  long countByRealFraudTrue();

  long countByPredictedFraudTrue();
}
