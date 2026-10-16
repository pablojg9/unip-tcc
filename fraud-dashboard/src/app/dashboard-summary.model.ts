export interface DashboardSummary {
  readonly totalBronze: number;
  readonly totalSilver: number;
  readonly totalGold: number;
  readonly totalRejected: number;
  readonly realFraudsFromDataset: number;
  readonly predictedFraudsByAi: number;
  readonly predictedLegitimateByAi: number;
  readonly pendingPrediction: number;
  readonly predictedFraudPercentage: number;
  readonly lowRisk: number;
  readonly mediumRisk: number;
  readonly highRisk: number;
  readonly pendingReview: number;
}
