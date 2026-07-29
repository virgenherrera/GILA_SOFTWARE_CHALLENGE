import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { CheckoutService } from '../checkout/checkout.service';
import type { Order } from '../checkout/checkout.service';

export interface CartItem {
  product_sku: string;
  name: string;
  quantity: number;
  unit_price_snapshot: number;
  subtotal: number;
}

/**
 * The backend returns a minimal `{ items: [], total: 0 }` shape when no cart cookie is present
 * yet (see api-contract), so the identity/status/timestamp fields are optional here.
 */
export interface Cart {
  id?: string;
  status?: string;
  items: CartItem[];
  total: number;
  created_at?: string;
  updated_at?: string;
}

const EMPTY_CART: Cart = { items: [], total: 0 };

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly checkoutService = inject(CheckoutService);
  private readonly baseUrl = '/api/cart';

  readonly cart = signal<Cart>(EMPTY_CART);
  readonly cartItemCount = signal(0);

  constructor() {
    this.getCart().subscribe({
      error: () => this.applyCart(EMPTY_CART),
    });
  }

  getCart(): Observable<Cart> {
    return this.http.get<Cart>(this.baseUrl).pipe(tap((cart) => this.applyCart(cart)));
  }

  addItem(sku: string, quantity: number): Observable<Cart> {
    return this.http
      .post<Cart>(`${this.baseUrl}/items`, { product_sku: sku, quantity })
      .pipe(tap((cart) => this.applyCart(cart)));
  }

  updateItemQuantity(sku: string, quantity: number): Observable<Cart> {
    return this.http
      .put<Cart>(`${this.baseUrl}/items/${sku}`, { quantity })
      .pipe(tap((cart) => this.applyCart(cart)));
  }

  removeItem(sku: string): Observable<Cart> {
    return this.http
      .delete<Cart>(`${this.baseUrl}/items/${sku}`)
      .pipe(tap((cart) => this.applyCart(cart)));
  }

  /**
   * Delegates the actual POST /api/checkout call to `CheckoutService` (the single owner of the
   * checkout HTTP contract, also used directly by the order confirmation page) and resets the
   * local cart state/badge on success, since the backend clears the cart cookie's cart on checkout.
   */
  checkout(): Observable<Order> {
    return this.checkoutService.checkout().pipe(tap(() => this.applyCart(EMPTY_CART)));
  }

  private applyCart(cart: Cart): void {
    this.cart.set(cart);
    this.cartItemCount.set(cart.items.reduce((sum, item) => sum + item.quantity, 0));
  }
}
