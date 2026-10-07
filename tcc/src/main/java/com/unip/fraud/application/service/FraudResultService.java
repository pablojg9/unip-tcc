package com.unip.fraud.application.service;

import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.domain.FraudResultDetail;
import com.unip.fraud.application.domain.FraudResultFilter;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.domain.ProcessedTransaction;
import com.unip.fraud.application.port.in.GetFraudResultDetailUseCase;
import com.unip.fraud.application.port.in.GetFraudResultsUseCase;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.SilverRepositoryOutPort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static com.unip.fraud.application.validation.Validation.requireArgument;
import static java.util.Objects.isNull;

@Service
public class FraudResultService implements GetFraudResultsUseCase, GetFraudResultDetailUseCase {

  private final GoldRepositoryOutPort goldRepository;
  private final SilverRepositoryOutPort silverRepository;

  public FraudResultService(
      final GoldRepositoryOutPort goldRepository,
      final SilverRepositoryOutPort silverRepository) {
    this.goldRepository = goldRepository;
    this.silverRepository = silverRepository;
  }

  @Override
  public PageResponse<FraudResult> getResults(final FraudResultFilter filter, final int page, final int size) {
    requireArgument(isNull(filter.minProbability()) || filter.minProbability() >= 0 && filter.minProbability() <= 1, "Minimum probability must be between 0 and 1");
    requireArgument(isNull(filter.startDate())  || isNull(filter.endDate()) || !filter.startDate().isAfter(filter.endDate()), "Start date must not be after end date");
    return goldRepository.findByFilter(filter, Math.max(page, 0), Math.clamp(size, 1, 100));
  }

  @Override
  public Optional<FraudResultDetail> getResultDetail(final String transactionId) {
    return goldRepository.findByTransactionId(transactionId).map(result -> {
      final Optional<ProcessedTransaction> transaction =
          silverRepository.findByTransactionId(transactionId);
      return new FraudResultDetail(
          result.transactionId(),
          result.realFraud(),
          result.predictedFraud(),
          result.probability(),
          result.scoreType(),
          result.riskLevel(),
          result.threshold(),
          result.classification(),
          result.modelVersion(),
          result.reasons(),
          result.explanation(),
          result.explanationType(),
          result.explanationModel(),
          result.processedAt(),
          transaction.map(ProcessedTransaction::sourceFile).orElse(null),
          transaction.map(ProcessedTransaction::processedAt).orElse(null),
          transaction.map(ProcessedTransaction::features).orElseGet(Map::of)
      );
    });
  }
}
