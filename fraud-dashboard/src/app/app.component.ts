import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { DashboardService } from './dashboard.service';
import { DashboardSummary } from './dashboard-summary.model';
import { Subscription, interval, startWith, switchMap } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  summary?: DashboardSummary;
  lastUpdated?: Date;
  private subscription?: Subscription;

  constructor(private readonly dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.subscription = interval(30000)
      .pipe(startWith(0), switchMap(() => this.dashboardService.getSummary()))
      .subscribe(summary => {
        this.summary = summary;
        this.lastUpdated = new Date();
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  refresh(): void {
    this.dashboardService.getSummary().subscribe(summary => {
      this.summary = summary;
      this.lastUpdated = new Date();
    });
  }

  get fraudBarWidth(): string {
    const percentage = this.summary?.predictedFraudPercentage ?? 0;
    return `${Math.min(percentage, 100)}%`;
  }

  get status(): string {
    if (!this.summary) return 'Loading';
    if (this.summary.totalGold > 0 && this.summary.totalRejected === 0) return 'Processed successfully';
    if (this.summary.totalRejected > 0) return 'Processed with warnings';
    return 'Waiting for AI results';
  }
}