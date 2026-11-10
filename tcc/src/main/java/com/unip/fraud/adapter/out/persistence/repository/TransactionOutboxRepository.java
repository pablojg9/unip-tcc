package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.TransactionOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TransactionOutboxRepository extends JpaRepository<TransactionOutboxEntity, UUID> {
  @Query(value = """
      SELECT *
      FROM ops.transaction_outbox
      WHERE status = 'PENDING'
      ORDER BY created_at
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<TransactionOutboxEntity> findPendingForUpdate(@Param("limit") int limit);
  boolean existsByAggregateIdAndEventType(final String aggregateId, final String eventType);
}
