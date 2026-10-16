package com.unip.fraud.adapter.in;

import com.unip.fraud.application.domain.ConfusionMatrix;
import com.unip.fraud.application.domain.DecisionMetrics;
import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.domain.FraudResultDetail;
import com.unip.fraud.application.domain.FraudResultFilter;
import com.unip.fraud.application.domain.DashboardSummary;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.port.in.GetDashboardUseCase;
import com.unip.fraud.application.port.in.GetFraudResultDetailUseCase;
import com.unip.fraud.application.port.in.GetFraudResultsUseCase;
import com.unip.fraud.application.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

  private final GetDashboardUseCase getDashboardUseCase;
  private final GetFraudResultsUseCase getFraudResultsUseCase;
  private final GetFraudResultDetailUseCase getFraudResultDetailUseCase;

  public DashboardController(
      final GetDashboardUseCase getDashboardUseCase,
      final GetFraudResultsUseCase getFraudResultsUseCase,
      final GetFraudResultDetailUseCase getFraudResultDetailUseCase) {
    this.getDashboardUseCase = getDashboardUseCase;
    this.getFraudResultsUseCase = getFraudResultsUseCase;
    this.getFraudResultDetailUseCase = getFraudResultDetailUseCase;
  }

  @GetMapping("/summary")
  public DashboardSummary getSummary() {
    return getDashboardUseCase.getSummary();
  }

  @GetMapping("/decision-details")
  public DecisionMetrics getDecisionMetrics(
      @RequestParam final boolean predictedFraud) {
    return getDashboardUseCase.getDecisionMetrics(predictedFraud);
  }

  @GetMapping("/confusion-matrix")
  public ConfusionMatrix getConfusionMatrix() {
    return getDashboardUseCase.getConfusionMatrix();
  }

  @GetMapping("/results")
  public PageResponse<FraudResult> getResults(
      @RequestParam(required = false) final Boolean predictedFraud,
      @RequestParam(required = false) final Boolean realFraud,
      @RequestParam(required = false) final Double minProbability,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startDate,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endDate,
      @RequestParam(required = false) final String riskLevel,
      @RequestParam(required = false) final String modelVersion,
      @RequestParam(required = false) final String reviewStatus,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size) {
    final FraudResultFilter filter = new FraudResultFilter(
        predictedFraud,
        realFraud,
        minProbability,
        startDate,
        endDate,
        riskLevel,
        modelVersion,
        reviewStatus
    );

    return getFraudResultsUseCase.getResults(filter, page, size);
  }

  @GetMapping("/results/{transactionId}")
  public FraudResultDetail getResultDetail(@PathVariable final String transactionId) {
    return getFraudResultDetailUseCase.getResultDetail(transactionId)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
  }
}
