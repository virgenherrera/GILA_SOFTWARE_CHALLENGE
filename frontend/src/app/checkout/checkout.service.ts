import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Observable } from 'rxjs';

export interface OrderItem {
  product_sku: string;
  name: string;
  quantity: number;
  unit_price: number;
  line_subtotal: number;
}

export interface Order {
  id: string;
  status: string;
  placed_at: string;
  items: OrderItem[];
  total_amount: number;
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  checkout(): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/checkout`, {});
  }

  getOrder(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.baseUrl}/orders/${id}`);
  }
}
