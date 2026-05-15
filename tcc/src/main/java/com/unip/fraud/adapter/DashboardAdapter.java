package com.unip.fraud.adapter;

import com.unip.fraud.application.port.in.GetDashboardUseCase;
import com.unip.fraud.application.port.out.repository.BronzeRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.RejectedRecordRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.SilverRepositoryOutPort;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DashboardAdapter implements GetDashboardUseCase {
  private final BronzeRepositoryOutPort bronzeRepositoryOutPort;
  private final SilverRepositoryOutPort silverRepositoryOutPort;
  private final GoldRepositoryOutPort goldRepositoryOutPort;
  private final RejectedRecordRepositoryOutPort rejectedRecordRepositoryOutPort;

  public DashboardAdapter(
      final BronzeRepositoryOutPort bronzeRepositoryOutPort,
      final SilverRepositoryOutPort silverRepositoryOutPort,
      final GoldRepositoryOutPort goldRepositoryOutPort,
      final RejectedRecordRepositoryOutPort rejectedRecordRepositoryOutPort) {
    this.bronzeRepositoryOutPort = bronzeRepositoryOutPort;
    this.silverRepositoryOutPort = silverRepositoryOutPort;
    this.goldRepositoryOutPort = goldRepositoryOutPort;
    this.rejectedRecordRepositoryOutPort = rejectedRecordRepositoryOutPort;
  }

  @Override
  public Map<String, Object> getSummary() {
    final long totalGold = goldRepositoryOutPort.count();
    final long predictedFrauds = goldRepositoryOutPort.countPredictedFraud();

    final double fraudPercentage = totalGold == 0 ? 0 : (predictedFrauds * 100.0) / totalGold;

    return Map.of(
        "totalBronze", bronzeRepositoryOutPort.count(),
        "totalSilver", silverRepositoryOutPort.count(),
        "totalGold", totalGold,
        "totalRejected", rejectedRecordRepositoryOutPort.count(),
        "realFraudsFromDataset", goldRepositoryOutPort.countRealFraud(),
        "predictedFraudsByAi", predictedFrauds,
        "predictedFraudPercentage", fraudPercentage
    );
  }

}
