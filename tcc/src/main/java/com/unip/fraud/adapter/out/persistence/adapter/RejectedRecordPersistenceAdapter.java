package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.entity.RejectedRecordEntity;
import com.unip.fraud.adapter.out.persistence.repository.RejectedRecordRepository;
import com.unip.fraud.application.port.out.repository.RejectedRecordRepositoryOutPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RejectedRecordPersistenceAdapter implements RejectedRecordRepositoryOutPort {

  private final RejectedRecordRepository rejectedRecordRepository;

  public RejectedRecordPersistenceAdapter(
      final RejectedRecordRepository rejectedRecordRepository) {
    this.rejectedRecordRepository = rejectedRecordRepository;
  }

  @Override
  public void save(String fileName, String rawPayload, String errorReason) {
    RejectedRecordEntity entity = new RejectedRecordEntity();
    entity.setFileName(fileName);
    entity.setRawPayload(rawPayload);
    entity.setErrorReason(errorReason);
    entity.setRejectedAt(LocalDateTime.now());
    rejectedRecordRepository.save(entity);
  }

  @Override
  public long count() {
    return rejectedRecordRepository.count();
  }
}
