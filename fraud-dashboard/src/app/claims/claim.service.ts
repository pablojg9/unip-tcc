import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PagedResponse } from '../../page/paged-response.model';
import { Claim, ClaimFilter, DatasetColumn } from './claim.model';
import { FraudReview, FraudReviewRequest } from './fraud-review.model';

const CLAIMS_API = '/process_fraud_automotive/claims';
const IMPORTS_API = '/process_fraud_automotive/imports';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);

  list(filter: ClaimFilter, page = 0, size = 20): Observable<PagedResponse<Claim>> {
    const params = Object.entries(filter).reduce(
      (current, [key, value]) => value ? current.set(key, value) : current,
      new HttpParams().set('page', page).set('size', size)
    );
    return this.http.get<PagedResponse<Claim>>(CLAIMS_API, { params });
  }

  schema(importId: string): Observable<readonly DatasetColumn[]> {
    return this.http.get<readonly DatasetColumn[]>(
      `${IMPORTS_API}/${encodeURIComponent(importId)}/schema`
    );
  }

  review(transactionId: string, request: FraudReviewRequest): Observable<FraudReview> {
    return this.http.post<FraudReview>(
      `${CLAIMS_API}/${encodeURIComponent(transactionId)}/reviews`,
      request
    );
  }

  reviewHistory(transactionId: string): Observable<readonly FraudReview[]> {
    return this.http.get<readonly FraudReview[]>(
      `${CLAIMS_API}/${encodeURIComponent(transactionId)}/reviews`
    );
  }
}
