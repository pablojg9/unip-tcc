export interface Claim {
  readonly transactionId: string;
  readonly importId: string;
  readonly rowNumber: number;
  readonly sourceFile: string;
  readonly confirmedFraud: boolean | null;
  readonly predictedFraud: boolean | null;
  readonly classification: string | null;
  readonly data: Readonly<Record<string, unknown>>;
  readonly processedAt: string;
}

export interface DatasetColumn {
  readonly name: string;
  readonly originalName: string;
  readonly type: string;
  readonly role: 'FEATURE' | 'LABEL' | string;
}

export interface ClaimFilter {
  readonly importId?: string;
  readonly search?: string;
  readonly field?: string;
  readonly value?: string;
}
