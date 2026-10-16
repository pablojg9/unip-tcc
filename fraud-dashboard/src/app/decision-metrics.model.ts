export interface ReasonMetric {
  readonly reason: string;
  readonly occurrences: number;
}

export interface DecisionMetrics {
  readonly predictedFraud: boolean;
  readonly total: number;
  readonly percentage: number;
  readonly averageProbability: number;
  readonly lowRisk: number;
  readonly mediumRisk: number;
  readonly highRisk: number;
  readonly predominantModel: string;
  readonly topReasons: readonly ReasonMetric[];
}
