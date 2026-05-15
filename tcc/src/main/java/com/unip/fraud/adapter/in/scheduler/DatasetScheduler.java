package com.unip.fraud.adapter.in.scheduler;

import com.unip.fraud.application.port.in.ProcessDatasetUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DatasetScheduler {
  private final ProcessDatasetUseCase processDatasetUseCase;

  public DatasetScheduler(
      final ProcessDatasetUseCase processDatasetUseCase) {
    this.processDatasetUseCase = processDatasetUseCase;
  }

  @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
  public void run() {
    processDatasetUseCase.processDatasets();
  }
}
