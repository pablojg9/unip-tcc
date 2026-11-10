package com.unip.fraud.adapter.out.persistence.mapper;

import com.unip.fraud.adapter.out.persistence.entity.SilverTransactionEntity;
import com.unip.fraud.application.domain.ClaimRecord;
import com.unip.fraud.application.domain.ProcessedTransaction;
import com.unip.fraud.application.domain.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(config = PersistenceMapperConfig.class)
public interface SilverEntityMapper {

  @Mapping(target = "transactionId", source = "transaction.transactionId")
  @Mapping(target = "realFraud", source = "transaction.realFraud")
  @Mapping(target = "features", source = "transaction.features")
  SilverTransactionEntity toEntity(
      final UUID importId,
      final long rowNumber,
      final String fileName,
      final Transaction transaction,
      final LocalDateTime processedAt);

  @Mapping(target = "sourceFile", source = "fileName")
  ProcessedTransaction toProcessedTransaction(final SilverTransactionEntity entity);

  @Mapping(target = "rowNumber", defaultValue = "0L")
  @Mapping(target = "sourceFile", source = "fileName")
  @Mapping(target = "confirmedFraud", source = "realFraud")
  @Mapping(target = "predictedFraud", ignore = true)
  @Mapping(target = "classification", ignore = true)
  @Mapping(target = "data", source = "features")
  ClaimRecord toClaimRecord(final SilverTransactionEntity entity);
}
