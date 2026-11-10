package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.mapper.SilverEntityMapper;
import com.unip.fraud.adapter.out.persistence.entity.GoldFraudResultEntity;
import com.unip.fraud.adapter.out.persistence.entity.SilverTransactionEntity;
import com.unip.fraud.adapter.out.persistence.repository.GoldFraudResultRepository;
import com.unip.fraud.adapter.out.persistence.repository.SilverTransactionRepository;
import com.unip.fraud.application.domain.ClaimFilter;
import com.unip.fraud.application.domain.ClaimRecord;
import com.unip.fraud.application.domain.DatasetColumn;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.port.out.repository.ClaimQueryRepositoryOutPort;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ClaimQueryPersistenceAdapter implements ClaimQueryRepositoryOutPort {

  private final SilverTransactionRepository repository;
  private final GoldFraudResultRepository goldRepository;
  private final EntityManager entityManager;
  private final SilverEntityMapper mapper;

  public ClaimQueryPersistenceAdapter(
      final SilverTransactionRepository repository,
      final GoldFraudResultRepository goldRepository,
      final EntityManager entityManager,
      final SilverEntityMapper mapper) {
    this.repository = repository;
    this.goldRepository = goldRepository;
    this.entityManager = entityManager;
    this.mapper = mapper;
  }

  @Override
  public PageResponse<ClaimRecord> find(
      final ClaimFilter filter,
      final int page,
      final int size) {
    final Page<SilverTransactionEntity> result = repository.findDynamic(
        filter.importId() == null ? null : filter.importId().toString(),
        blankToNull(filter.search()),
        blankToNull(filter.field()),
        blankToNull(filter.value()),
        PageRequest.of(page, size)
    );
    final Map<String, GoldFraudResultEntity> predictions = goldRepository.findAllById(
        result.getContent().stream().map(SilverTransactionEntity::getTransactionId).toList()
    ).stream().collect(Collectors.toMap(
        prediction -> prediction.getTransactionId(),
        prediction -> prediction
    ));
    return new PageResponse<>(
        result.getContent().stream()
            .map(entity -> toClaimRecord(entity, predictions.get(entity.getTransactionId())))
            .toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages()
    );
  }

  @Override
  public Optional<ClaimRecord> findByTransactionId(final String transactionId) {
    return repository.findById(transactionId).map(entity -> toClaimRecord(
        entity, goldRepository.findById(transactionId).orElse(null)
    ));
  }

  private ClaimRecord toClaimRecord(
      final SilverTransactionEntity entity,
      final GoldFraudResultEntity prediction) {
    final ClaimRecord claim = mapper.toClaimRecord(entity);
    return new ClaimRecord(
        claim.transactionId(), claim.importId(), claim.rowNumber(), claim.sourceFile(),
        claim.confirmedFraud(), prediction == null ? null : prediction.getPredictedFraud(),
        prediction == null ? null : prediction.getClassification(),
        claim.data(), claim.processedAt()
    );
  }

  @Override
  public List<DatasetColumn> findSchema(final UUID importId) {
    @SuppressWarnings("unchecked")
    final List<Object[]> rows = entityManager.createNativeQuery("""
        SELECT
          schema.normalized_name,
          MIN(schema.original_name),
          MIN(schema.inferred_type),
          MIN(schema.column_role)
        FROM bronze.dataset_schema schema
        WHERE schema.import_id = :importId
        GROUP BY schema.normalized_name
        ORDER BY schema.normalized_name
        """)
        .setParameter("importId", importId)
        .getResultList();
    return rows.stream()
        .map(row -> new DatasetColumn(
            String.valueOf(row[0]),
            String.valueOf(row[1]),
            String.valueOf(row[2]),
            String.valueOf(row[3])
        ))
        .toList();
  }

  private String blankToNull(final String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
