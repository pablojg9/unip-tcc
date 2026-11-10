import { APP_INITIALIZER } from '@angular/core';
import { AuthService } from './app/auth.service';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { csrfInterceptor } from './app/csrf.interceptor';
import { RootComponent } from './app/root.component';
import { routes } from './app/app.routes';

void bootstrapApplication(RootComponent, {
  providers: [provideHttpClient(withInterceptors([csrfInterceptor]), withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })), provideRouter(routes),
    { provide: APP_INITIALIZER, useFactory: (auth: AuthService) => () => auth.restore(), deps: [AuthService], multi: true }]
}).catch((error: unknown) => console.error(error));
