import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, of, switchMap } from 'rxjs';

import { ImportJob } from '../imports/import.model';
import { ImportService } from '../imports/import.service';
import { Claim, ClaimFilter, DatasetColumn } from './claim.model';
import { ClaimService } from './claim.service';
import { claimReference, transactionRouteKey } from '../claim-reference.util';

@Component({
  selector: 'app-claims',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './claims.component.html',
  styleUrl: './claims.component.css'
})
export class ClaimsComponent implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly importService = inject(ImportService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly filterForm = this.formBuilder.nonNullable.group({
    importId: [''],
    search: [''],
    field: [{ value: '', disabled: true }],
    value: [{ value: '', disabled: true }]
  });
  readonly detailFilter = this.formBuilder.nonNullable.control('');

  imports: readonly ImportJob[] = [];
  schema: readonly DatasetColumn[] = [];
  claims: readonly Claim[] = [];
  selectedClaim?: Claim;
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;
  loading = true;
  loadError = false;

  ngOnInit(): void {
    this.importService.list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => this.imports = response.content,
        error: () => this.imports = []
      });

    this.filterForm.controls.importId.valueChanges
      .pipe(
        switchMap(importId => this.loadSchema(importId)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(schema => {
        this.schema = schema;
        this.filterForm.controls.field.reset('', { emitEvent: false });
        this.filterForm.controls.value.reset('', { emitEvent: false });
        schema.length > 0
          ? this.filterForm.controls.field.enable({ emitEvent: false })
          : this.filterForm.controls.field.disable({ emitEvent: false });
        this.filterForm.controls.value.disable({ emitEvent: false });
      });

    this.filterForm.controls.field.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(field => field
        ? this.filterForm.controls.value.enable({ emitEvent: false })
        : this.filterForm.controls.value.disable({ emitEvent: false }));

    const requestedImport = this.route.snapshot.queryParamMap.get('importId') ?? '';
    if (requestedImport) {
      this.filterForm.patchValue({ importId: requestedImport });
    }
    this.loadClaims();
  }

  get selectedEntries(): readonly [string, unknown][] {
    const query = this.detailFilter.value.trim().toLocaleLowerCase('pt-BR');
    return Object.entries(this.selectedClaim?.data ?? {})
      .filter(([key, value]) => !query
        || key.toLocaleLowerCase('pt-BR').includes(query)
        || this.formatValue(value).toLocaleLowerCase('pt-BR').includes(query))
      .sort(([left], [right]) => left.localeCompare(right, 'pt-BR'));
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.selectedClaim = undefined;
    this.loadClaims();
  }

  clearFilters(): void {
    this.filterForm.reset();
    this.schema = [];
    this.filterForm.controls.field.disable({ emitEvent: false });
    this.filterForm.controls.value.disable({ emitEvent: false });
    this.currentPage = 0;
    this.selectedClaim = undefined;
    this.loadClaims();
  }

  selectClaim(claim: Claim): void {
    this.selectedClaim = claim;
    this.detailFilter.reset();
  }

  closeDetail(): void {
    this.selectedClaim = undefined;
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadClaims();
    }
  }

  fraudLabel(value: boolean | null): string {
    return value === null ? 'Sem rótulo' : value ? 'Fraude' : 'Legítimo';
  }

  claimReference(claim: Claim): string {
    return claimReference(claim.transactionId, claim.processedAt);
  }

  transactionRoute(claim: Claim): string {
    return transactionRouteKey(claim.transactionId);
  }

  formatLabel(value: string): string {
    return value
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/^./, letter => letter.toLocaleUpperCase('pt-BR'));
  }

  formatValue(value: unknown): string {
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

  preview(claim: Claim): string {
    return Object.entries(claim.data)
      .slice(0, 3)
      .map(([key, value]) => `${this.formatLabel(key)}: ${this.formatValue(value)}`)
      .join(' · ');
  }

  private loadClaims(): void {
    this.loading = true;
    this.loadError = false;
    this.claimService.list(this.buildFilter(), this.currentPage, this.pageSize)
      .pipe(
        finalize(() => this.loading = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: response => {
          this.claims = response.content;
          this.currentPage = response.page;
          this.totalElements = response.totalElements;
          this.totalPages = response.totalPages;
        },
        error: () => this.loadError = true
      });
  }

  private loadSchema(importId: string): Observable<readonly DatasetColumn[]> {
    return importId
      ? this.claimService.schema(importId).pipe(catchError(() => of([])))
      : of([]);
  }

  private buildFilter(): ClaimFilter {
    const value = this.filterForm.getRawValue();
    return {
      importId: value.importId || undefined,
      search: value.search.trim() || undefined,
      field: value.field || undefined,
      value: value.field && value.value.trim() ? value.value.trim() : undefined
    };
  }
}
