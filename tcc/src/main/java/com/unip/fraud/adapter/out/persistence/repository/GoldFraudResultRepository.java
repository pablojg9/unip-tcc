package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.GoldFraudResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GoldFraudResultRepository
    extends JpaRepository<GoldFraudResultEntity, String>,
    JpaSpecificationExecutor<GoldFraudResultEntity> {

  long countByRealFraudTrue();

  long countByPredictedFraudTrue();

  long countByPredictedFraudFalse();

  long countByPredictedFraudIsNull();

  long countByRealFraudIsNull();

  long countByRiskLevel(final String riskLevel);

}
