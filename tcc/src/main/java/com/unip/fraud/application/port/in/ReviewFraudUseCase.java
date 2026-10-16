package com.unip.fraud.application.port.in;

import com.unip.fraud.application.domain.FraudReview;
import com.unip.fraud.application.domain.FraudReviewCommand;

import java.util.List;

public interface ReviewFraudUseCase {
  FraudReview review(FraudReviewCommand command);

  List<FraudReview> getHistory(String transactionId);
}
