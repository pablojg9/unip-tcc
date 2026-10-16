export interface DashboardFilter {
  readonly predictedFraud?: boolean;
  readonly realFraud?: boolean;
  readonly minProbability?: number;
  readonly startDate?: string;
  readonly endDate?: string;
  readonly riskLevel?: string;
  readonly modelVersion?: string;
  readonly reviewStatus?: string;
}
