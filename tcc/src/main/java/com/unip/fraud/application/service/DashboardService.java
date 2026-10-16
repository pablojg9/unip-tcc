package com.unip.fraud.application.service;

import com.unip.fraud.application.domain.ConfusionMatrix;
import com.unip.fraud.application.domain.DashboardSummary;
import com.unip.fraud.application.domain.DecisionMetrics;
import com.unip.fraud.application.port.in.GetDashboardUseCase;
import com.unip.fraud.application.port.out.repository.BronzeRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.RejectedRecordRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.SilverRepositoryOutPort;
import org.springframework.stereotype.Service;

@Service
public class DashboardService implements GetDashboardUseCase {

  private final BronzeRepositoryOutPort bronzeRepository;
  private final SilverRepositoryOutPort silverRepository;
  private final GoldRepositoryOutPort goldRepository;
  private final RejectedRecordRepositoryOutPort rejectedRepository;

  public DashboardService(
      final BronzeRepositoryOutPort bronzeRepository,
      final SilverRepositoryOutPort silverRepository,
      final GoldRepositoryOutPort goldRepository,
      final RejectedRecordRepositoryOutPort rejectedRepository) {
    this.bronzeRepository = bronzeRepository;
    this.silverRepository = silverRepository;
    this.goldRepository = goldRepository;
    this.rejectedRepository = rejectedRepository;
  }

  @Override
  public DashboardSummary getSummary() {
    final long totalGold = goldRepository.count();
    final long predictedFrauds = goldRepository.countPredictedFraud();
    final double percentage = totalGold == 0
        ? 0
        : predictedFrauds * 100.0 / totalGold;
    return new DashboardSummary(
        bronzeRepository.count(),
        silverRepository.count(),
        totalGold,
        rejectedRepository.count(),
        goldRepository.countRealFraud(),
        predictedFrauds,
        goldRepository.countPredictedLegitimate(),
        goldRepository.countPendingPrediction(),
        percentage,
        goldRepository.countByRiskLevel("LOW"),
        goldRepository.countByRiskLevel("MEDIUM"),
        goldRepository.countByRiskLevel("HIGH"),
        goldRepository.countPendingReview()
    );
  }

  @Override
  public DecisionMetrics getDecisionMetrics(final boolean predictedFraud) {
    return goldRepository.getDecisionMetrics(predictedFraud);
  }

  @Override
  public ConfusionMatrix getConfusionMatrix() {
    return goldRepository.getConfusionMatrix();
  }
}
