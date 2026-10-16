package com.unip.fraud.application.port.out.repository;

import com.unip.fraud.application.domain.FraudReview;
import com.unip.fraud.application.domain.FraudReviewCommand;

import java.util.List;

public interface FraudReviewRepositoryOutPort {
  FraudReview save(final FraudReviewCommand command);

  List<FraudReview> findByTransactionId(final String transactionId);
}
