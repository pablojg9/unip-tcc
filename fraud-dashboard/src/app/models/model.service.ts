import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { ModelTraining } from './model.model';

const API_ROOT = '/process_fraud_automotive/models';
@Injectable({ providedIn: 'root' })
export class ModelService {
  private readonly http = inject(HttpClient);
  list(): Observable<readonly ModelTraining[]> {
    return this.http.get<readonly ModelTraining[]>(API_ROOT);
  }
  train(file: File): Observable<ModelTraining> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.securePost(`${API_ROOT}/train`, body);
  }
  activate(trainingId: string): Observable<ModelTraining> {
    return this.securePost(`${API_ROOT}/${encodeURIComponent(trainingId)}/activate`, null);
  }
  private securePost(url: string, body: FormData | null): Observable<ModelTraining> {
    return this.http.get<{ token: string }>('/process_fraud_automotive/auth/csrf').pipe(
      switchMap(csrf => this.http.post<ModelTraining>(url, body, {
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      }))
    );
  }
}
