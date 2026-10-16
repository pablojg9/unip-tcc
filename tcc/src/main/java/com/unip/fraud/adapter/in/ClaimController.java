package com.unip.fraud.adapter.in;

import com.unip.fraud.application.domain.ClaimFilter;
import com.unip.fraud.application.domain.ClaimRecord;
import com.unip.fraud.application.domain.FraudReview;
import com.unip.fraud.application.domain.FraudReviewCommand;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.domain.ReviewDecision;
import com.unip.fraud.application.port.in.GetClaimsUseCase;
import com.unip.fraud.application.port.in.ReviewFraudUseCase;
import com.unip.fraud.application.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/claims")
public class ClaimController {

  private final GetClaimsUseCase getClaimsUseCase;
  private final ReviewFraudUseCase reviewFraudUseCase;

  public ClaimController(
      final GetClaimsUseCase getClaimsUseCase,
      final ReviewFraudUseCase reviewFraudUseCase) {
    this.getClaimsUseCase = getClaimsUseCase;
    this.reviewFraudUseCase = reviewFraudUseCase;
  }

  @GetMapping
  public PageResponse<ClaimRecord> list(
      @RequestParam(required = false) final UUID importId,
      @RequestParam(required = false) final String search,
      @RequestParam(required = false) final String field,
      @RequestParam(required = false) final String value,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {
    return getClaimsUseCase.getClaims(
        new ClaimFilter(importId, search, field, value),
        page,
        size
    );
  }

  @GetMapping("/{transactionId}")
  public ClaimRecord detail(@PathVariable final String transactionId) {
    return getClaimsUseCase.getClaim(transactionId)
        .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
  }

  @PostMapping("/{transactionId}/reviews")
  public FraudReview review(
      @PathVariable final String transactionId,
      @Valid @RequestBody final ReviewRequest request) {
    return reviewFraudUseCase.review(new FraudReviewCommand(
        transactionId,
        request.decision(),
        request.notes(),
        request.reviewer()
    ));
  }

  @GetMapping("/{transactionId}/reviews")
  public List<FraudReview> reviewHistory(@PathVariable final String transactionId) {
    return reviewFraudUseCase.getHistory(transactionId);
  }

  public record ReviewRequest(
      @NotNull ReviewDecision decision,
      @Size(max = 2000) String notes,
      @Size(max = 120) String reviewer
  ) {
  }
}
