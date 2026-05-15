package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.entity.GoldFraudResultEntity;
import com.unip.fraud.adapter.out.persistence.repository.GoldFraudResultRepository;
import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import org.springframework.stereotype.Component;

@Component
public class GoldPersistenceAdapter implements GoldRepositoryOutPort {

  private final GoldFraudResultRepository goldFraudResultRepository;

  public GoldPersistenceAdapter(
      final GoldFraudResultRepository goldFraudResultRepository) {
    this.goldFraudResultRepository = goldFraudResultRepository;
  }

  @Override
  public void save(final FraudResult result) {
    final GoldFraudResultEntity entity = new GoldFraudResultEntity();
    entity.setTransactionId(result.transactionId());
    entity.setRealFraud(result.realFraud());
    entity.setPredictedFraud(result.predictedFraud());
    entity.setProbability(result.probability());
    entity.setClassification(result.classification());
    entity.setProcessedAt(result.processedAt());
    goldFraudResultRepository.save(entity);

  }

  @Override
  public long count() {
    return goldFraudResultRepository.count();
  }

  @Override
  public long countRealFraud() {
    return goldFraudResultRepository.countByRealFraudTrue();
  }

  @Override
  public long countPredictedFraud() {
    return goldFraudResultRepository.countByPredictedFraudTrue();
  }
}
