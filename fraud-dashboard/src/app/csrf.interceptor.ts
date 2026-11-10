import { inject } from '@angular/core';
import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { switchMap } from 'rxjs';

// Obtain a current token for API writes, including multipart uploads.
export const csrfInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/process_fraud_automotive/')
      || ['GET', 'HEAD', 'OPTIONS'].includes(request.method)
      || request.headers.has('X-XSRF-TOKEN')) return next(request);
  return inject(HttpClient).get<{ token: string }>('/process_fraud_automotive/auth/csrf').pipe(
    switchMap(csrf => next(request.clone({ setHeaders: { 'X-XSRF-TOKEN': csrf.token } })))
  );
};
