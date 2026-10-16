import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PagedResponse } from '../page/paged-response.model';
import { DashboardFilter } from './dashboard-filter.model';
import { DashboardSummary } from './dashboard-summary.model';
import { FraudResult } from './fraud-result.model';
import { TransactionDetail } from './transaction-detail.model';
import { DecisionMetrics } from './decision-metrics.model';
import { ConfusionMatrix } from './confusion-matrix.model';

const API_ROOT = '/process_fraud_automotive/dashboard';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${API_ROOT}/summary`);
  }

  getDecisionMetrics(predictedFraud: boolean): Observable<DecisionMetrics> {
    return this.http.get<DecisionMetrics>(`${API_ROOT}/decision-details`, {
      params: new HttpParams().set('predictedFraud', predictedFraud)
    });
  }

  getConfusionMatrix(): Observable<ConfusionMatrix> {
    return this.http.get<ConfusionMatrix>(`${API_ROOT}/confusion-matrix`);
  }

  getResults(
    filter: DashboardFilter,
    page = 0,
    size = 10
  ): Observable<PagedResponse<FraudResult>> {
    const params = this.toParams(filter)
      .set('page', page)
      .set('size', size);

    return this.http.get<PagedResponse<FraudResult>>(`${API_ROOT}/results`, { params });
  }

  getResultDetail(transactionId: string): Observable<TransactionDetail> {
    return this.http.get<TransactionDetail>(
      `${API_ROOT}/results/${encodeURIComponent(transactionId)}`
    );
  }

  private toParams(filter: DashboardFilter): HttpParams {
    return Object.entries(filter).reduce(
      (params, [key, value]) => value === undefined || value === ''
        ? params
        : params.set(key, String(value)),
      new HttpParams()
    );
  }
}
