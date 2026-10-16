package com.unip.fraud.application.port.in;

import com.unip.fraud.application.domain.ConfusionMatrix;
import com.unip.fraud.application.domain.DashboardSummary;
import com.unip.fraud.application.domain.DecisionMetrics;

public interface GetDashboardUseCase {

  DashboardSummary getSummary();

  DecisionMetrics getDecisionMetrics(boolean predictedFraud);

  ConfusionMatrix getConfusionMatrix();

}
