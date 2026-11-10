import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { finalize, firstValueFrom, map, switchMap, tap } from 'rxjs';

type CurrentUser = { username: string; canManageModels: boolean };
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  user?: CurrentUser;
  loginOpen = false;
  loggingOut = false;
  get headers(): HttpHeaders { return new HttpHeaders(); }
  async restore(): Promise<void> {
    try {
      await firstValueFrom(this.http.get('/process_fraud_automotive/auth/csrf'));
      this.user = await firstValueFrom(this.http.get<CurrentUser>('/process_fraud_automotive/auth/me'));
    } catch { this.user = undefined; }
  }
  login(username: string, password: string) {
    const authorization = `Basic ${btoa(unescape(encodeURIComponent(`${username}:${password}`)))}`;
    return this.http.get<{ token: string }>('/process_fraud_automotive/auth/csrf').pipe(
      switchMap(csrf => this.http.post<CurrentUser>('/process_fraud_automotive/auth/login', null, {
        headers: new HttpHeaders({ Authorization: authorization, 'X-XSRF-TOKEN': csrf.token })
      })),
      switchMap(user => this.http.get('/process_fraud_automotive/auth/csrf').pipe(map(() => user))),
      tap(user => { this.user = user; })
    );
  }
  logout(): void {
    if (this.loggingOut) return;
    this.loggingOut = true;
    this.http.get<{ token: string }>('/process_fraud_automotive/auth/csrf').pipe(
      switchMap(csrf => this.http.post('/process_fraud_automotive/auth/logout', null, {
        headers: new HttpHeaders({ 'X-XSRF-TOKEN': csrf.token })
      })),
      finalize(() => this.loggingOut = false)
    ).subscribe({
      next: () => {
        this.user = undefined;
        this.loginOpen = false;
      },
      error: () => window.alert('Não foi possível encerrar a sessão. Verifique a conexão e tente novamente.')
    });
  }
}
