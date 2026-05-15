package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.entity.BronzeTransactionRawEntity;
import com.unip.fraud.adapter.out.persistence.repository.BronzeTransactionRawRepository;
import com.unip.fraud.application.port.out.repository.BronzeRepositoryOutPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BronzePersistenceAdapter implements BronzeRepositoryOutPort {

  private final BronzeTransactionRawRepository bronzeTransactionRawRepository;

  public BronzePersistenceAdapter(
      final BronzeTransactionRawRepository bronzeTransactionRawRepository) {
    this.bronzeTransactionRawRepository = bronzeTransactionRawRepository;
  }

  @Override
  public void save(final String fileName, final String rawPayload) {
    final BronzeTransactionRawEntity entity = new BronzeTransactionRawEntity();
    entity.setFileName(fileName);
    entity.setRawPayload(rawPayload);
    entity.setSource("public-dataset");
    entity.setIngestedAt(LocalDateTime.now());
    bronzeTransactionRawRepository.save(entity);
  }

  @Override
  public long count() {
    return bronzeTransactionRawRepository.count();
  }
}
