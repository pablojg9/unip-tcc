import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <a class="skip-link" href="#main-content">Pular para o conteúdo</a>
    <header class="app-header">
      <div class="header-inner">
        <a class="brand" routerLink="/" aria-label="Ir para o painel">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M12 3 4.8 6v5.3c0 4.5 2.9 8.6 7.2 9.7 4.3-1.1 7.2-5.2 7.2-9.7V6L12 3Z"/><path d="m8.8 12 2.1 2.1 4.5-4.6"/></svg>
          </span>
          <span>
            <strong>Fraud<span>Guard</span></strong>
            <small>Central de inteligência</small>
          </span>
        </a>

        <nav aria-label="Navegação principal">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 13h6V4H4v9Zm0 7h6v-4H4v4Zm10 0h6v-9h-6v9Zm0-16v4h6V4h-6Z"/></svg>
            Painel
          </a>
          <a routerLink="/imports" routerLinkActive="active">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5M5 14v5h14v-5"/></svg>
            Analisar
          </a>
          <a routerLink="/claims" routerLinkActive="active">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16v14H4V5Zm4 4h8M8 13h5"/></svg>
            Sinistros
          </a>
          <a routerLink="/models" routerLinkActive="active">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 4 7v10l8 4 8-4V7l-8-4Zm-8 4 8 4 8-4M12 11v10"/></svg>
            Modelos
          </a>
        </nav>

        <div class="account-area">
          <small>{{ auth.user ? auth.user.username : 'Modo de visualização' }}</small>
          <button class="button secondary" type="button" *ngIf="!auth.user" (click)="loginOpen = true">Entrar</button>
          <button class="button secondary" type="button" *ngIf="auth.user" (click)="auth.logout()" [disabled]="auth.loggingOut">{{ auth.loggingOut ? 'Saindo…' : 'Sair' }}</button>
        </div>
        <button type="button" class="theme-toggle button secondary" (click)="toggleTheme()" [attr.aria-pressed]="darkMode" aria-label="Alternar tema claro e escuro">{{ darkMode ? '☀ Tema claro' : '☾ Tema escuro' }}</button>

        <div class="workspace-status" aria-label="Estado do ambiente">
          <i aria-hidden="true"></i>
          <span><small>Ambiente produtivo</small><strong>Operacional</strong></span>
        </div>
      </div>
    </header>

    <div class="login-backdrop" *ngIf="loginOpen">
      <form class="panel login-panel" (ngSubmit)="login()" aria-label="Entrar na conta">
        <h2>Entrar no FraudGuard</h2><p>Seu cargo determina as ações disponíveis.</p>
        <label>Usuário<input name="username" [(ngModel)]="username" required autocomplete="username"></label>
        <label>Senha<input name="password" [(ngModel)]="password" required type="password" autocomplete="current-password"></label>
        <p class="alert error" *ngIf="loginError" role="alert">{{ loginError }}</p>
        <button class="button" [disabled]="loggingIn || !username || !password">{{ loggingIn ? 'Entrando…' : 'Entrar' }}</button>
        <button class="button ghost" type="button" (click)="loginOpen = false; password = ''">Cancelar</button>
      </form>
    </div>
    <div id="main-content" tabindex="-1"><router-outlet></router-outlet></div>

    <footer class="app-footer">
      <span>FraudGuard · Inteligência de risco automotivo</span>
      <span>Pipeline auditável · Bronze → Silver → Gold</span>
    </footer>
  `
})
export class RootComponent {
  readonly auth = inject(AuthService);
  get loginOpen(): boolean { return this.auth.loginOpen; }
  set loginOpen(value: boolean) { this.auth.loginOpen = value; }
  username = '';
  password = '';
  loginError = '';
  loggingIn = false;
  login(): void {
    if (this.loggingIn) return;
    this.loggingIn = true;
    this.loginError = '';
    this.auth.login(this.username, this.password).subscribe({
      next: () => { this.password = ''; this.loggingIn = false; this.loginOpen = false; },
      error: error => { this.password = ''; this.loggingIn = false; this.loginError = error.status === 401 ? 'Usuário ou senha inválidos.' : error.status === 403 ? 'Falha na validação de segurança. Atualize a página e tente novamente.' : 'Não foi possível entrar. Verifique a conexão com o servidor.'; }
    });
  }
  darkMode = false;

  constructor() {
    try {
      const saved = localStorage.getItem('fraudguard-theme');
      this.darkMode = saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    } catch { /* Use the default theme when storage is unavailable. */ }
    this.applyTheme();
  }

  toggleTheme(): void {
    this.darkMode = !this.darkMode;
    this.applyTheme();
    try { localStorage.setItem('fraudguard-theme', this.darkMode ? 'dark' : 'light'); } catch { }
  }

  private applyTheme(): void {
    document.documentElement.dataset['theme'] = this.darkMode ? 'dark' : 'light';
  }
}
