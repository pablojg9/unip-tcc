package com.unip.fraud.application.port.out.repository;

import com.unip.fraud.application.domain.FraudResult;

public interface GoldRepositoryOutPort {
  void save(final FraudResult result);
  long count();
  long countRealFraud();
  long countPredictedFraud();
}
