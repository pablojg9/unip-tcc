package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.entity.TransactionOutboxEntity;
import com.unip.fraud.adapter.out.persistence.mapper.TransactionOutboxEntityMapper;
import com.unip.fraud.adapter.out.persistence.repository.TransactionOutboxRepository;
import com.unip.fraud.application.domain.PendingTransactionEvent;
import com.unip.fraud.application.domain.Transaction;
import com.unip.fraud.application.port.out.producer.TransactionOutboxOutPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionOutboxPersistenceAdapter implements TransactionOutboxOutPort {

  private final TransactionOutboxRepository repository;
  private final TransactionOutboxEntityMapper mapper;

  public TransactionOutboxPersistenceAdapter(
      final TransactionOutboxRepository repository,
      final TransactionOutboxEntityMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @Override
  public void enqueue(final Transaction transaction) {
    Optional.of(transaction)
        .filter(value -> !repository.existsByAggregateIdAndEventType(
            value.transactionId(),
            "TransactionReadyForScoring"
        ))
        .map(this::newOutboxEntity)
        .ifPresent(repository::save);
  }

  @Override
  public List<PendingTransactionEvent> findPending(final int limit) {
    return repository.findPendingForUpdate(
        Math.clamp(limit, 1, 500)
    ).stream().map(mapper::toDomain).toList();
  }

  @Override
  public void markPublished(final UUID eventId) {
    final TransactionOutboxEntity entity = required(eventId);
    entity.markPublished(LocalDateTime.now());
    repository.save(entity);
  }

  @Override
  public void markFailed(final UUID eventId, final String error) {
    final TransactionOutboxEntity entity = required(eventId);
    entity.registerFailure(error);
    repository.save(entity);
  }

  private TransactionOutboxEntity newOutboxEntity(final Transaction transaction) {
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", transaction.transactionId());
    payload.put("realFraud", transaction.realFraud());
    payload.put("features", transaction.features());
    return mapper.toEntity(
        UUID.randomUUID(),
        transaction.transactionId(),
        "TransactionReadyForScoring",
        payload,
        "PENDING",
        LocalDateTime.now()
    );
  }

  private TransactionOutboxEntity required(final UUID eventId) {
    return repository.findById(eventId)
        .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
  }
}
