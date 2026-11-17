export type ImportStatus =
  | 'QUEUED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_WARNINGS'
  | 'FAILED';

export interface ImportJob {
  readonly importId: string;
  readonly fileName: string;
  readonly status: ImportStatus;
  readonly totalRows: number;
  readonly processedRows: number;
  readonly rejectedRows: number;
  readonly errorMessage: string | null;
  readonly createdAt: string;
  readonly startedAt: string | null;
  readonly finishedAt: string | null;
  readonly batchExecutionId: number | null;
  readonly scoringCancelledAt: string | null;
}

export interface ImportScoringProgress {
  readonly scoredRows: number;
  readonly withoutScoreRows: number;
}

export interface RejectedRecord {
  readonly id: number;
  readonly importId: string;
  readonly fileName: string;
  readonly rowNumber: number;
  readonly originalData: Readonly<Record<string, unknown>>;
  readonly errorReason: string;
  readonly rejectedAt: string;
}
