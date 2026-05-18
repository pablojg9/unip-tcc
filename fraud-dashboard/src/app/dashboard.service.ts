import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { DashboardSummary } from './dashboard-summary.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly apiUrl = '/process_fraud_automotive/dashboard/summary';

  constructor(private readonly http: HttpClient) {}

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(this.apiUrl).pipe(
      catchError(error => {
        console.error('Dashboard API error', error);
        return of({
          totalBronze: 0,
          totalSilver: 0,
          totalGold: 0,
          totalRejected: 0,
          realFraudsFromDataset: 0,
          predictedFraudsByAi: 0,
          predictedFraudPercentage: 0.0
        });
      })
    );
  }
}