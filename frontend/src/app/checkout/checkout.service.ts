import { Injectable, inject } from '@angular/core';
import type { ResourceRef, Signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import type { Observable } from 'rxjs';
import type { Order } from '../shared/validation/checkout.schema';

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  checkout(): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/checkout`, {});
  }

  getOrder(id: Signal<string | undefined>): ResourceRef<Order | undefined> {
    return rxResource({
      params: id,
      stream: ({ params }) => this.http.get<Order>(`${this.baseUrl}/orders/${params}`),
    });
  }
}
