package com.unip.fraud.application.port.out.repository;

import com.unip.fraud.application.domain.ConfusionMatrix;
import com.unip.fraud.application.domain.DecisionMetrics;
import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.domain.FraudResultFilter;
import com.unip.fraud.application.domain.PageResponse;

import java.util.Optional;

public interface GoldRepositoryOutPort {
  void save(final FraudResult result);

  long count();

  long countRealFraud();

  long countPredictedFraud();

  long countPredictedLegitimate();

  long countByRiskLevel(final String riskLevel);

  long countPendingPrediction();

  long countPendingReview();

  DecisionMetrics getDecisionMetrics(boolean predictedFraud);

  ConfusionMatrix getConfusionMatrix();

  PageResponse<FraudResult> findByFilter(final FraudResultFilter filter, final int page, final int size);

  Optional<FraudResult> findByTransactionId(final String transactionId);

  void updateConfirmedFraud(final String transactionId, final Boolean confirmedFraud);

}
