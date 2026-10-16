package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.mapper.FraudReviewEntityMapper;
import com.unip.fraud.adapter.out.persistence.repository.FraudReviewRepository;
import com.unip.fraud.application.domain.FraudReview;
import com.unip.fraud.application.domain.FraudReviewCommand;
import com.unip.fraud.application.port.out.repository.FraudReviewRepositoryOutPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class FraudReviewPersistenceAdapter implements FraudReviewRepositoryOutPort {

  private final FraudReviewRepository repository;
  private final FraudReviewEntityMapper mapper;

  public FraudReviewPersistenceAdapter(
      final FraudReviewRepository repository,
      final FraudReviewEntityMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @Override
  public FraudReview save(final FraudReviewCommand command) {
    return mapper.toDomain(repository.save(
        mapper.toEntity(command, LocalDateTime.now())
    ));
  }

  @Override
  public List<FraudReview> findByTransactionId(final String transactionId) {
    return repository.findAllByTransactionIdOrderByReviewedAtDesc(transactionId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
