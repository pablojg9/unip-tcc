package com.unip.fraud.adapter.in;

import com.unip.fraud.application.port.in.GetDashboardUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

  private final GetDashboardUseCase getDashboardUseCase;

  public DashboardController(
      final GetDashboardUseCase getDashboardUseCase) {
    this.getDashboardUseCase = getDashboardUseCase;
  }

  @GetMapping("/summary")
  public Map<String, Object> getSummary() {
    return getDashboardUseCase.getSummary();
  }
}