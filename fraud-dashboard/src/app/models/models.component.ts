import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, filter, merge, Subject, timer, exhaustMap } from 'rxjs';

import { AuthService } from '../auth.service';
import { ModelTraining, ModelTrainingStatus } from './model.model';
import { ModelService } from './model.service';

@Component({
  selector: 'app-models',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './models.component.html',
  styleUrl: './models.component.css'
})
export class ModelsComponent implements OnInit {
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  private readonly service = inject(ModelService);
  private readonly destroyRef = inject(DestroyRef);

  models: readonly ModelTraining[] = [];
  selectedFile?: File;
  readonly auth = inject(AuthService);
  loading = true;
  submitting = false;
  activatingId = '';
  errorMessage = '';
  private readonly refreshRequests = new Subject<void>();
  private loaded = false;

  ngOnInit(): void {
    merge(timer(0, 10_000), this.refreshRequests)
      .pipe(
        filter(() => !this.auth.loginOpen && !document.hidden && !this.submitting && !this.activatingId
          && !document.activeElement?.matches('input, select, textarea')),
        exhaustMap(() => this.service.list().pipe(
          catchError(() => {
            this.loading = false;
            if (!this.loaded) this.errorMessage = 'Não foi possível consultar os modelos.';
            return EMPTY;
          })
        )),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(models => {
        // Reuse unchanged rows so polling does not restart animations or disturb focus.
        this.models = models.map(model => {
          const previous = this.models.find(item => item.trainingId === model.trainingId);
          return previous && JSON.stringify(previous) === JSON.stringify(model) ? previous : model;
        });
        this.loaded = true;
        this.loading = false;
      });
  }

  refreshModels(): void { this.refreshRequests.next(); }
  trackModel(_index: number, model: ModelTraining): string { return model.trainingId; }
  trackMetric(_index: number, metric: [string, number]): string { return metric[0]; }

  get activeModel(): ModelTraining | undefined {
    return this.models.find(model => model.active);
  }

  chooseFile(event: Event): void {
    this.selectedFile = (event.target as HTMLInputElement).files?.item(0) ?? undefined;
    this.errorMessage = '';
  }

  train(): void {
    if (!this.selectedFile || !this.auth.user?.canManageModels || this.submitting) {
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.service.train(this.selectedFile)
      .pipe(
        finalize(() => this.submitting = false),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: model => {
          this.models = [model, ...this.models.filter(item => item.trainingId !== model.trainingId)];
          this.selectedFile = undefined;
          if (this.fileInput) {
            this.fileInput.nativeElement.value = '';
          }
        },
        error: error => this.errorMessage = this.modelError(error, 'solicitar o treinamento')
      });
  }

  activate(model: ModelTraining): void {
    if (!this.auth.user?.canManageModels || this.activatingId) {
      return;
    }
    this.activatingId = model.trainingId;
    this.errorMessage = '';
    this.service.activate(model.trainingId)
      .pipe(
        finalize(() => this.activatingId = ''),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: updated => this.models = this.models.map(item =>
          item.trainingId === updated.trainingId ? updated : item
        ),
        error: error => this.errorMessage = this.modelError(error, 'ativar este modelo')
      });
  }

  private modelError(error: { status?: number; error?: { message?: string; detail?: string } }, action: string): string {
    if (error.status === 401) return 'Sua sessão expirou. Entre novamente para continuar.';
    if (error.status === 403) return 'Solicitação bloqueada pela validação de segurança ou permissão. Entre novamente e tente outra vez.';
    if (error.status === 413) return 'O arquivo excede o tamanho permitido pelo servidor.';
    if (!error.status) return 'Não foi possível conectar ao servidor.';
    return error.error?.message || error.error?.detail || `Não foi possível ${action} (HTTP ${error.status}).`;
  }

  statusLabel(status: ModelTrainingStatus): string {
    return ({
      QUEUED: 'Na fila',
      TRAINING: 'Treinando',
      READY: 'Pronto para ativar',
      ACTIVATING: 'Ativando',
      ACTIVE: 'Ativo',
      FAILED: 'Falhou',
      ACTIVATION_FAILED: 'Falha na ativação'
    } as Record<ModelTrainingStatus, string>)[status];
  }

  modelTypeLabel(type: string | null): string {
    return type === 'SUPERVISED' ? 'Supervisionado' : type === 'ANOMALY' ? 'Anomalias' : '—';
  }

  metricEntries(model: ModelTraining): readonly [string, number][] {
    return Object.entries(model.metrics);
  }

  metricLabel(name: string): string {
    return ({ accuracy: 'Acurácia', precision: 'Precisão', recall: 'Recall', f1: 'F1', roc_auc: 'ROC AUC' } as Record<string, string>)[name] ?? name;
  }

  fileSize(file: File): string {
    return `${(file.size / 1024 / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} MB`;
  }
}
