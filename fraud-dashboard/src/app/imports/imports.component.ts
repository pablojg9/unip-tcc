import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, filter, merge, Subject, timer, exhaustMap, timeout } from 'rxjs';

import { AuthService } from '../auth.service';
import { ImportJob, ImportStatus, RejectedRecord } from './import.model';
import { ImportService } from './import.service';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './imports.component.html',
  styleUrl: './imports.component.css'
})
export class ImportsComponent implements OnInit {
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  private readonly importService = inject(ImportService);
  private readonly destroyRef = inject(DestroyRef);

  imports: readonly ImportJob[] = [];
  selectedFile?: File;
  selectedImport?: ImportJob;
  errors: readonly RejectedRecord[] = [];
  loading = true;
  uploading = false;
  loadError = false;
  uploadError = '';
  uploadSuccess = '';
  private readonly auth = inject(AuthService);
  private readonly refreshRequests = new Subject<void>();

  private readonly statusLabels: Readonly<Record<ImportStatus, string>> = {
    QUEUED: 'Na fila',
    PROCESSING: 'Processando',
    COMPLETED: 'Concluído',
    COMPLETED_WITH_WARNINGS: 'Concluído com alertas',
    FAILED: 'Falhou'
  };

  ngOnInit(): void {
    merge(timer(0, 5000), this.refreshRequests)
      .pipe(
        filter(() => !this.auth.loginOpen && !this.uploading && !document.hidden
          && !document.activeElement?.matches('input, textarea, select')),
        exhaustMap(() => this.importService.list(0, 50).pipe(
          catchError(() => {
            this.loadError = true;
            this.loading = false;
            return EMPTY;
          })
        )),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(response => {
        this.imports = response.content;
        this.loading = false;
        this.loadError = false;
        this.synchronizeSelection();
      });
  }

  refreshImports(): void { this.refreshRequests.next(); }
  trackImport(_index: number, job: ImportJob): string { return job.importId; }

  chooseFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.item(0) ?? undefined;
    this.uploadError = '';
  }

  upload(): void {
    if (this.uploading) return;
    if (!this.selectedFile) {
      this.uploadError = 'Selecione um arquivo CSV, XLS ou XLSX antes de iniciar a análise.';
      return;
    }
    this.uploading = true;
    this.uploadError = '';
    this.uploadSuccess = '';
    this.importService.upload(this.selectedFile)
      .pipe(
        timeout(120000),
        finalize(() => this.uploading = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: job => {
          this.uploadSuccess = 'Arquivo recebido. Acompanhe o processamento na lista abaixo.';
          this.imports = [job, ...this.imports.filter(item => item.importId !== job.importId)];
          this.selectedImport = job;
          this.selectedFile = undefined;
          if (this.fileInput) {
            this.fileInput.nativeElement.value = '';
          }
        },
        error: error => this.uploadError = this.uploadFailure(error)
      });
  }

  selectImport(job: ImportJob): void {
    this.selectedImport = job;
    this.errors = [];
    if (job.rejectedRows > 0) {
      this.importService.errors(job.importId, 0, 20)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: response => this.errors = response.content,
          error: () => this.errors = []
        });
    }
  }

  retry(job: ImportJob): void {
    this.importService.retry(job.importId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.imports = this.imports.map(item => item.importId === updated.importId ? updated : item);
          this.selectedImport = updated;
        },
        error: error => this.uploadError = error?.error?.message ?? 'Não foi possível reiniciar esta importação.'
      });
  }

  statusLabel(status: ImportStatus): string {
    return this.statusLabels[status];
  }

  progress(job: ImportJob): number {
    if (job.totalRows === 0) {
      return job.status === 'COMPLETED' ? 100 : 0;
    }
    return Math.min(((job.processedRows + job.rejectedRows) / job.totalRows) * 100, 100);
  }

  fileSize(file: File): string {
    const megabytes = file.size / 1024 / 1024;
    return `${megabytes.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} MB`;
  }

  private uploadFailure(error: { status?: number; error?: { message?: string; detail?: string } }): string {
    if (error.status === 401) return 'Sua sessão expirou. Entre novamente.';
    if (error.status === 403) return 'O servidor bloqueou a validação de segurança ou a permissão do envio.';
    if (error.status === 413) return 'O arquivo ultrapassa o tamanho permitido pelo servidor.';
    if (error.status === 409) return error.error?.message || error.error?.detail || 'Este arquivo já foi importado.';
    if (!error.status) return 'Não foi possível conectar ao servidor para enviar o arquivo.';
    return error.error?.message || error.error?.detail || `Falha no envio do arquivo (HTTP ${error.status}).`;
  }

  errorPreview(record: RejectedRecord): string {
    return Object.entries(record.originalData)
      .slice(0, 2)
      .map(([key, value]) => `${key}: ${String(value ?? '—')}`)
      .join(' · ');
  }

  private synchronizeSelection(): void {
    const selectedId = this.selectedImport?.importId;
    if (selectedId) {
      this.selectedImport = this.imports.find(item => item.importId === selectedId);
    }
  }
}
