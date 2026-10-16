package com.unip.fraud.application.service;

import com.unip.fraud.application.domain.FraudReview;
import com.unip.fraud.application.domain.FraudReviewCommand;
import com.unip.fraud.application.exception.ResourceNotFoundException;
import com.unip.fraud.application.port.in.ReviewFraudUseCase;
import com.unip.fraud.application.port.out.repository.FraudReviewRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.SilverRepositoryOutPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.unip.fraud.application.validation.Validation.requireArgument;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
public class FraudReviewService implements ReviewFraudUseCase {

  private final FraudReviewRepositoryOutPort reviewRepository;
  private final SilverRepositoryOutPort silverRepository;
  private final GoldRepositoryOutPort goldRepository;

  public FraudReviewService(
      final FraudReviewRepositoryOutPort reviewRepository,
      final SilverRepositoryOutPort silverRepository,
      final GoldRepositoryOutPort goldRepository) {
    this.reviewRepository = reviewRepository;
    this.silverRepository = silverRepository;
    this.goldRepository = goldRepository;
  }

  @Override
  @Transactional
  public FraudReview review(final FraudReviewCommand command) {
    validate(command);
    silverRepository.findByTransactionId(command.transactionId())
        .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    final Boolean confirmedFraud = command.decision().confirmedFraud();
    final FraudReview review = reviewRepository.save(command);
    silverRepository.updateConfirmedFraud(command.transactionId(), confirmedFraud);
    goldRepository.updateConfirmedFraud(command.transactionId(), confirmedFraud);
    return review;
  }

  @Override
  public List<FraudReview> getHistory(final String transactionId) {
    requireArgument(nonNull(transactionId) && !transactionId.isBlank(), "Transaction id is required");
    return reviewRepository.findByTransactionId(transactionId);
  }

  private void validate(final FraudReviewCommand command) {
    requireArgument(nonNull(command), "Review command is required");
    requireArgument(nonNull(command.transactionId()) && !command.transactionId().isBlank(), "Transaction id is required");
    requireArgument(nonNull(command.decision()), "Review decision is required");
    requireArgument(isNull(command.notes()) || command.notes().length() <= 2000, "Review notes cannot exceed 2000 characters");
    requireArgument(isNull(command.reviewer()) || command.reviewer().length() <= 120, "Reviewer cannot exceed 120 characters");
    requireArgument(command.transactionId().length() <= 64, "Transaction id cannot exceed 64 characters");
  }
}
