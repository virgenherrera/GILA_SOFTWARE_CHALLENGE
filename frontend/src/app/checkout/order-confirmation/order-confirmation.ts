import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { CheckoutService } from '../checkout.service';
import type { Order } from '../../shared/validation/checkout.schema';
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

  private readonly orderId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id') ?? undefined)),
  );

  private readonly orderResource = this.checkoutService.getOrder(this.orderId);

  protected readonly loading = computed(() => this.orderResource.isLoading());

  protected readonly order = computed<Order | null>(() =>
    this.orderResource.hasValue() ? this.orderResource.value() : null,
  );

  protected readonly formattedTotal = computed(() =>
    this.orderResource.hasValue() ? this.orderResource.value().total_amount.toFixed(2) : '0.00',
  );

  protected readonly notFound = computed(() => {
    const err = this.orderResource.error();

    return err instanceof HttpErrorResponse && err.status === 404;
  });

  protected readonly error = computed(() => {
    const err = this.orderResource.error();

    if (err instanceof HttpErrorResponse && err.status !== 404) {
      return extractApiErrorMessage(err);
    }

    return null;
  });
}
