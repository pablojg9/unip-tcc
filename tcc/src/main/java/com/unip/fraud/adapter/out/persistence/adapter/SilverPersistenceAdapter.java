package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.entity.SilverTransactionEntity;
import com.unip.fraud.adapter.out.persistence.repository.SilverTransactionRepository;
import com.unip.fraud.application.domain.Transaction;
import com.unip.fraud.application.port.out.repository.SilverRepositoryOutPort;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
public class SilverPersistenceAdapter implements SilverRepositoryOutPort {

  private final SilverTransactionRepository silverTransactionRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public SilverPersistenceAdapter(
      final SilverTransactionRepository silverTransactionRepository) {
    this.silverTransactionRepository = silverTransactionRepository;
  }

  @Override
  public void save(final String fileName, final Transaction rawPayload) {
    try {
      final SilverTransactionEntity entity = new SilverTransactionEntity();
      entity.setTransactionId(rawPayload.transactionId());
      entity.setFileName(fileName);
      entity.setRealFraud(rawPayload.realFraud());
      entity.setFeaturesJson(objectMapper.writeValueAsString(rawPayload.features()));
      entity.setProcessedAt(LocalDateTime.now());
      silverTransactionRepository.save(entity);

    } catch (Exception exception) {
      throw new RuntimeException("Error saving silver transaction", exception);
    }
  }

  @Override
  public long count() {
    return silverTransactionRepository.count();
  }
}
