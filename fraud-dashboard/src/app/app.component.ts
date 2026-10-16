import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, forkJoin, interval, startWith, switchMap } from 'rxjs';

import { DashboardFilter } from './dashboard-filter.model';
import { DashboardSummary } from './dashboard-summary.model';
import { DashboardService } from './dashboard.service';
import { FraudResult } from './fraud-result.model';
import { claimReference, transactionRouteKey } from './claim-reference.util';
import { DecisionMetrics } from './decision-metrics.model';
import { ConfusionMatrix } from './confusion-matrix.model';

type BooleanFilter = '' | 'true' | 'false';
type DecisionCategory = 'fraud' | 'legitimate';

interface ReasonInsight {
  readonly label: string;
  readonly count: number;
}

interface MetricCard {
  readonly label: string;
  readonly value: number;
  readonly helper: string;
  readonly tone: string;
  readonly layer: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);

  readonly filterForm = this.formBuilder.nonNullable.group({
    predictedFraud: ['' as BooleanFilter],
    realFraud: ['' as BooleanFilter],
    minProbability: [''],
    riskLevel: [''],
    modelVersion: [''],
    startDate: [''],
    endDate: [''],
    reviewStatus: ['']
  });

  summary?: DashboardSummary;
  results: readonly FraudResult[] = [];
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;
  lastUpdated?: Date;
  loadingSummary = true;
  loadingResults = true;
  summaryError = false;
  resultsError = false;
  selectedDecision?: DecisionCategory;
  decisionDetailResults: readonly FraudResult[] = [];
  loadingDecisionDetails = false;
  decisionDetailsError = false;
  decisionMetrics?: DecisionMetrics;
  confusionMatrix?: ConfusionMatrix;
  private decisionDetailRequest = 0;

  ngOnInit(): void {
    interval(60_000)
      .pipe(
        startWith(0),
        switchMap(() => {
          this.loadingSummary = true;
          return forkJoin({
            summary: this.dashboardService.getSummary(),
            confusionMatrix: this.dashboardService.getConfusionMatrix()
          }).pipe(
            catchError(() => {
              this.summaryError = true;
              return EMPTY;
            }),
            finalize(() => this.loadingSummary = false)
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(data => {
        this.summary = data.summary;
        this.confusionMatrix = data.confusionMatrix;
        this.summaryError = false;
        this.lastUpdated = new Date();
      });

    this.loadResults();
  }

  get metrics(): readonly MetricCard[] {
    const data = this.summary;
    return [
      { label: 'Dados recebidos', value: data?.totalBronze ?? 0, helper: 'Linhas que entraram no pipeline', tone: 'bronze', layer: 'Bronze' },
      { label: 'Sinistros válidos', value: data?.totalSilver ?? 0, helper: 'Registros limpos e normalizados', tone: 'silver', layer: 'Silver' },
      { label: 'Análises concluídas', value: data?.totalGold ?? 0, helper: 'Decisões produzidas pelo modelo', tone: 'gold', layer: 'Gold' },
      { label: 'Dados rejeitados', value: data?.totalRejected ?? 0, helper: 'Registros que exigem correção', tone: 'danger', layer: 'Atenção' }
    ];
  }

  get status(): string {
    if (this.loadingSummary) {
      return 'Sincronizando';
    }
    if (this.summaryError) {
      return 'Backend indisponível';
    }
    if ((this.summary?.totalRejected ?? 0) > 0) {
      return 'Atenção necessária';
    }
    return (this.summary?.totalGold ?? 0) > 0 ? 'Operação saudável' : 'Aguardando análises';
  }

  get riskTotal(): number {
    return (this.summary?.lowRisk ?? 0)
      + (this.summary?.mediumRisk ?? 0)
      + (this.summary?.highRisk ?? 0);
  }

  get predictedFraudCount(): number {
    return this.summary?.predictedFraudsByAi ?? 0;
  }

  get pendingDecisionCount(): number {
    return this.summary?.pendingPrediction ?? 0;
  }

  get legitimateCount(): number {
    return this.summary?.predictedLegitimateByAi ?? 0;
  }

  get decisionTotal(): number {
    return this.predictedFraudCount + this.legitimateCount + this.pendingDecisionCount;
  }

  get fraudDistributionGradient(): string {
    const fraudEnd = this.decisionPercentage(this.predictedFraudCount);
    const legitimateEnd = fraudEnd + this.decisionPercentage(this.legitimateCount);
    return `conic-gradient(#f06d80 0% ${fraudEnd}%, #45d5a3 ${fraudEnd}% ${legitimateEnd}%, #718198 ${legitimateEnd}% 100%)`;
  }

  get selectedDecisionLabel(): string {
    return this.selectedDecision === 'fraud' ? 'Fraude' : 'Legítimo';
  }

  get selectedDecisionCount(): number {
    return this.decisionMetrics?.total ?? (this.selectedDecision === 'fraud'
      ? this.predictedFraudCount
      : this.legitimateCount);
  }

  get detailAverageScore(): number {
    return (this.decisionMetrics?.averageProbability ?? 0) * 100;
  }

  get detailPrimaryModel(): string {
    return this.decisionMetrics?.predominantModel ?? 'Não informado';
  }

  get detailReasons(): readonly ReasonInsight[] {
    return (this.decisionMetrics?.topReasons ?? [])
      .map(reason => ({ label: reason.reason, count: reason.occurrences }));
  }

  get criticalDecisionResults(): readonly FraudResult[] {
    return [...this.decisionDetailResults]
      .sort((left, right) => right.probability - left.probability)
      .slice(0, 5);
  }

  refresh(): void {
    this.loadSummary();
    this.loadResults();
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.loadResults();
  }

  clearFilters(): void {
    this.filterForm.reset();
    this.currentPage = 0;
    this.loadResults();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadResults();
    }
  }

  riskPercentage(value: number): number {
    return this.riskTotal === 0 ? 0 : (value / this.riskTotal) * 100;
  }

  modelMetricPercentage(value: number): number {
    return Math.min(Math.max(value * 100, 0), 100);
  }

  probability(result: FraudResult): number {
    return Math.min(Math.max(result.probability * 100, 0), 100);
  }

  decisionPercentage(value: number): number {
    return this.decisionTotal === 0 ? 0 : (value / this.decisionTotal) * 100;
  }

  claimReference(result: FraudResult): string {
    return claimReference(result.transactionId, result.processedAt);
  }

  transactionRoute(result: FraudResult): string {
    return transactionRouteKey(result.transactionId);
  }

  showDecisionDetails(category: DecisionCategory): void {
    const request = ++this.decisionDetailRequest;
    const predictedFraud = category === 'fraud';
    this.selectedDecision = category;
    this.decisionDetailResults = [];
    this.loadingDecisionDetails = true;
    this.decisionDetailsError = false;

    forkJoin({
      metrics: this.dashboardService.getDecisionMetrics(predictedFraud),
      results: this.dashboardService.getResults({ predictedFraud }, 0, 100)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => {
          if (request !== this.decisionDetailRequest) {
            return;
          }
          this.decisionMetrics = response.metrics;
          this.decisionDetailResults = response.results.content;
          this.loadingDecisionDetails = false;
        },
        error: () => {
          if (request !== this.decisionDetailRequest) {
            return;
          }
          this.decisionDetailsError = true;
          this.loadingDecisionDetails = false;
        }
      });

    setTimeout(() => document.getElementById('decision-details')
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
  }

  closeDecisionDetails(): void {
    this.decisionDetailRequest += 1;
    this.selectedDecision = undefined;
    this.decisionMetrics = undefined;
    this.decisionDetailResults = [];
    this.loadingDecisionDetails = false;
    this.decisionDetailsError = false;
  }

  viewDecisionResults(): void {
    if (!this.selectedDecision) {
      return;
    }
    this.filterForm.controls.predictedFraud.setValue(
      this.selectedDecision === 'fraud' ? 'true' : 'false'
    );
    this.applyFilters();
    document.getElementById('processed-results')?.scrollIntoView({ behavior: 'smooth' });
  }

  detailRiskCount(riskLevel: string): number {
    if (!this.decisionMetrics) {
      return 0;
    }
    return ({
      LOW: this.decisionMetrics.lowRisk,
      MEDIUM: this.decisionMetrics.mediumRisk,
      HIGH: this.decisionMetrics.highRisk
    } as Record<string, number>)[riskLevel] ?? 0;
  }

  detailRiskPercentage(riskLevel: string): number {
    return !this.decisionMetrics || this.decisionMetrics.total === 0
      ? 0
      : this.detailRiskCount(riskLevel) * 100 / this.decisionMetrics.total;
  }

  openReviewQueue(): void {
    this.filterForm.reset();
    this.filterForm.controls.reviewStatus.setValue('PENDING');
    this.applyFilters();
    document.getElementById('processed-results')?.scrollIntoView({ behavior: 'smooth' });
  }

  booleanLabel(value: boolean | null): string {
    return value === null ? 'Não informado' : value ? 'Sim' : 'Não';
  }

  fraudLabel(value: boolean | null): string {
    return value === null ? 'Aguardando' : value ? 'Fraude' : 'Legítimo';
  }

  riskLabel(value: string): string {
    return ({ LOW: 'Baixo', MEDIUM: 'Médio', HIGH: 'Alto' } as Record<string, string>)[value] ?? value;
  }

  private loadSummary(): void {
    this.loadingSummary = true;
    forkJoin({
      summary: this.dashboardService.getSummary(),
      confusionMatrix: this.dashboardService.getConfusionMatrix()
    })
      .pipe(
        finalize(() => this.loadingSummary = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: data => {
          this.summary = data.summary;
          this.confusionMatrix = data.confusionMatrix;
          this.summaryError = false;
          this.lastUpdated = new Date();
        },
        error: () => this.summaryError = true
      });
  }

  private loadResults(): void {
    this.loadingResults = true;
    this.resultsError = false;
    this.dashboardService.getResults(this.buildFilter(), this.currentPage, this.pageSize)
      .pipe(
        finalize(() => this.loadingResults = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: response => {
          this.results = response.content;
          this.currentPage = response.page;
          this.pageSize = response.size;
          this.totalElements = response.totalElements;
          this.totalPages = response.totalPages;
        },
        error: () => this.resultsError = true
      });
  }

  private buildFilter(): DashboardFilter {
    const value = this.filterForm.getRawValue();
    const probability = Number(value.minProbability);
    return {
      predictedFraud: this.toBoolean(value.predictedFraud),
      realFraud: this.toBoolean(value.realFraud),
      minProbability: value.minProbability === '' ? undefined : probability / 100,
      riskLevel: value.riskLevel || undefined,
      modelVersion: value.modelVersion.trim() || undefined,
      reviewStatus: value.reviewStatus || undefined,
      startDate: value.startDate || undefined,
      endDate: value.endDate || undefined
    };
  }

  private toBoolean(value: BooleanFilter): boolean | undefined {
    return value === '' ? undefined : value === 'true';
  }
}
