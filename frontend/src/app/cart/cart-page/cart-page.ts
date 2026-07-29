import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { CartItemRow } from '../cart-item/cart-item';
import type { QuantityChangeEvent } from '../cart-item/cart-item';
import { CartService } from '../cart.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { ApiErrorBody, ApiErrorDetail } from '../../shared/utils/api-error';

@Component({
  selector: 'app-cart-page',
  imports: [CartItemRow, RouterLink],
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.css',
})
export class CartPage {
  private readonly cartService = inject(CartService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly cart = this.cartService.cart;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly itemErrors = signal<Record<string, string>>({});
  protected readonly updatingSku = signal<string | null>(null);
  protected readonly checkingOut = signal(false);
  protected readonly checkoutError = signal<string | null>(null);

  protected readonly formattedTotal = computed(() => this.cart().total.toFixed(2));

  constructor() {
    this.loadCart();
  }

  protected onQuantityChange(event: QuantityChangeEvent): void {
    if (event.quantity <= 0) {
      return;
    }

    this.clearItemError(event.sku);
    this.updatingSku.set(event.sku);

    this.cartService
      .updateItemQuantity(event.sku, event.quantity)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.updatingSku.set(null),
        error: (err: HttpErrorResponse) => {
          this.updatingSku.set(null);
          this.handleItemError(event.sku, err);
        },
      });
  }

  protected onRemove(sku: string): void {
    this.clearItemError(sku);
    this.updatingSku.set(sku);

    this.cartService
      .removeItem(sku)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.updatingSku.set(null),
        error: (err: HttpErrorResponse) => {
          this.updatingSku.set(null);
          this.error.set(extractApiErrorMessage(err));
        },
      });
  }

  protected onCheckout(): void {
    this.checkoutError.set(null);
    this.itemErrors.set({});
    this.checkingOut.set(true);

    this.cartService
      .checkout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.checkingOut.set(false);
          void this.router.navigate(['/checkout/confirmation', order.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.checkingOut.set(false);
          this.handleCheckoutError(err);
        },
      });
  }

  private loadCart(): void {
    this.loading.set(true);
    this.error.set(null);

    this.cartService
      .getCart()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loading.set(false),
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(extractApiErrorMessage(err));
        },
      });
  }

  private handleItemError(sku: string, err: HttpErrorResponse): void {
    if (err.status === 409) {
      const details = this.extractDetails(err);
      const reason = details[0]?.reason ?? extractApiErrorMessage(err);

      this.itemErrors.update((current) => ({ ...current, [sku]: reason }));

      return;
    }

    this.error.set(extractApiErrorMessage(err));
  }

  private handleCheckoutError(err: HttpErrorResponse): void {
    if (err.status === 409) {
      const details = this.extractDetails(err);

      if (details.length > 0) {
        this.itemErrors.set(
          details.reduce<Record<string, string>>(
            (acc, detail) => ({ ...acc, [detail.field]: detail.reason }),
            {},
          ),
        );
        this.checkoutError.set('Some items in your cart have stock issues. Please review below.');

        return;
      }
    }

    this.checkoutError.set(extractApiErrorMessage(err));
  }

  private clearItemError(sku: string): void {
    this.itemErrors.update((current) => {
      if (!(sku in current)) {
        return current;
      }

      const next = { ...current };

      delete next[sku];

      return next;
    });
  }

  private extractDetails(err: HttpErrorResponse): ApiErrorDetail[] {
    const body = err.error as Partial<ApiErrorBody> | null;

    return body?.error?.details ?? [];
  }
}
