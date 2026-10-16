import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { DashboardService } from '../dashboard.service';
import { TransactionDetail } from '../transaction-detail.model';
import { claimReference, transactionIdFromRouteKey } from '../claim-reference.util';
import { ClaimService } from '../claims/claim.service';
import { FraudReview, ReviewDecision } from '../claims/fraud-review.model';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.css'
})
export class TransactionDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly dashboardService = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly claimService = inject(ClaimService);
  private readonly formBuilder = inject(FormBuilder);

  readonly featureSearch = new FormControl('', { nonNullable: true });
  readonly reviewForm = this.formBuilder.nonNullable.group({
    decision: ['' as ReviewDecision | '', Validators.required],
    reviewer: ['', [Validators.required, Validators.maxLength(120)]],
    notes: ['', Validators.maxLength(2000)]
  });
  transaction?: TransactionDetail;
  reviews: readonly FraudReview[] = [];
  loading = true;
  notFound = false;
  loadingReviews = false;
  submittingReview = false;
  reviewError = false;
  reviewSuccess = false;

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap(params => this.dashboardService.getResultDetail(
          transactionIdFromRouteKey(params.get('transactionId') ?? '')
        )),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: transaction => {
          this.transaction = transaction;
          this.loading = false;
          this.loadReviewHistory(transaction.transactionId);
        },
        error: () => {
          this.notFound = true;
          this.loading = false;
        }
      });
  }

  get featureEntries(): readonly [string, unknown][] {
    const query = this.featureSearch.value.trim().toLocaleLowerCase('pt-BR');
    return Object.entries(this.transaction?.features ?? {})
      .filter(([key, value]) => !query
        || key.toLocaleLowerCase('pt-BR').includes(query)
        || this.featureValue(value).toLocaleLowerCase('pt-BR').includes(query))
      .sort(([left], [right]) => left.localeCompare(right, 'pt-BR'));
  }

  get probabilityPercentage(): number {
    return Math.min(Math.max((this.transaction?.probability ?? 0) * 100, 0), 100);
  }

  get transactionReference(): string {
    return this.transaction
      ? claimReference(this.transaction.transactionId, this.transaction.processedAt)
      : '';
  }

  submitReview(): void {
    if (!this.transaction || this.reviewForm.invalid) {
      this.reviewForm.markAllAsTouched();
      return;
    }
    const value = this.reviewForm.getRawValue();
    if (!value.decision) {
      return;
    }
    this.submittingReview = true;
    this.reviewError = false;
    this.reviewSuccess = false;
    this.claimService.review(this.transaction.transactionId, {
      decision: value.decision,
      reviewer: value.reviewer.trim(),
      notes: value.notes.trim() || undefined
    })
      .pipe(
        finalize(() => this.submittingReview = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: review => {
          const confirmedFraud = review.decision === 'FRAUD'
            ? true
            : review.decision === 'LEGITIMATE' ? false : null;
          this.transaction = { ...this.transaction!, realFraud: confirmedFraud };
          this.reviews = [review, ...this.reviews];
          this.reviewSuccess = true;
          this.reviewForm.patchValue({ decision: '', notes: '' });
        },
        error: () => this.reviewError = true
      });
  }

  decisionLabel(value: ReviewDecision): string {
    return ({
      FRAUD: 'Fraude confirmada',
      LEGITIMATE: 'Caso legítimo',
      INCONCLUSIVE: 'Inconclusivo'
    } as Record<ReviewDecision, string>)[value];
  }

  fraudLabel(value: boolean | null): string {
    return value === null ? 'Não informado' : value ? 'Fraude' : 'Legítima';
  }

  riskLabel(value: string): string {
    return ({ LOW: 'Baixo', MEDIUM: 'Médio', HIGH: 'Alto' } as Record<string, string>)[value] ?? value;
  }

  featureLabel(key: string): string {
    return key
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/^./, letter => letter.toLocaleUpperCase('pt-BR'));
  }

  featureValue(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return 'Não informado';
    }
    if (typeof value === 'boolean') {
      return value ? 'Sim' : 'Não';
    }
    if (typeof value === 'object') {
      return JSON.stringify(value);
    }
    return String(value);
  }

  private loadReviewHistory(transactionId: string): void {
    this.loadingReviews = true;
    this.claimService.reviewHistory(transactionId)
      .pipe(
        finalize(() => this.loadingReviews = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: reviews => this.reviews = reviews,
        error: () => this.reviews = []
      });
  }
}
