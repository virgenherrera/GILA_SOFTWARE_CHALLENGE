import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CheckoutService } from '../checkout.service';
import type { Order } from '../checkout.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';

@Component({
  selector: 'app-order-confirmation',
  imports: [RouterLink],
  templateUrl: './order-confirmation.html',
  styleUrl: './order-confirmation.css',
})
export class OrderConfirmation {
  private readonly route = inject(ActivatedRoute);
  private readonly checkoutService = inject(CheckoutService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly order = signal<Order | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);

  protected readonly formattedTotal = computed(
    () => this.order()?.total_amount.toFixed(2) ?? '0.00',
  );

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');

      if (id) {
        this.loadOrder(id);
      }
    });
  }

  private loadOrder(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.notFound.set(false);

    this.checkoutService
      .getOrder(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);

          if (err.status === 404) {
            this.notFound.set(true);

            return;
          }

          this.error.set(extractApiErrorMessage(err));
        },
      });
  }
}
