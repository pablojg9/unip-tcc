export type ReviewDecision = 'FRAUD' | 'LEGITIMATE' | 'INCONCLUSIVE';

export interface FraudReview {
  readonly id: number;
  readonly transactionId: string;
  readonly decision: ReviewDecision;
  readonly notes: string | null;
  readonly reviewer: string | null;
  readonly reviewedAt: string;
}

export interface FraudReviewRequest {
  readonly decision: ReviewDecision;
  readonly notes?: string;
  readonly reviewer?: string;
}
