export interface TransactionDetail {
  readonly transactionId: string;
  readonly realFraud: boolean | null;
  readonly predictedFraud: boolean | null;
  readonly probability: number;
  readonly scoreType: string;
  readonly riskLevel: string;
  readonly threshold: number | null;
  readonly classification: string;
  readonly modelVersion: string | null;
  readonly reasons: readonly string[];
  readonly explanation: string;
  readonly explanationType: string;
  readonly explanationModel: string | null;
  readonly processedAt: string;
  readonly sourceFile: string | null;
  readonly ingestedAt: string | null;
  readonly features: Readonly<Record<string, unknown>>;
}
