export interface DashboardSummary {
  totalBronze: number;
  totalSilver: number;
  totalGold: number;
  totalRejected: number;
  realFraudsFromDataset: number;
  predictedFraudsByAi: number;
  predictedFraudPercentage: number;
}