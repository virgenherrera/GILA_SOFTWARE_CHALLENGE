import { Component, computed, input, output } from '@angular/core';
import type { CartItem } from '../../shared/validation/cart.schema';

export interface QuantityChangeEvent {
  sku: string;
  quantity: number;
}

/**
 * Named `CartItemRow` (rather than `CartItem`) to avoid colliding with the `CartItem` model
 * interface from `cart.service.ts` — the same naming convention used for
 * `SearchFiltersPanel` vs. the `SearchFilters` type in the search feature.
 */
@Component({
  selector: 'app-cart-item',
  templateUrl: './cart-item.html',
  styleUrl: './cart-item.css',
})
export class CartItemRow {
  readonly item = input.required<CartItem>();
  readonly stockError = input<string | null>(null);
  readonly updating = input(false);

  readonly quantityChange = output<QuantityChangeEvent>();
  readonly remove = output<string>();

  protected readonly formattedUnitPrice = computed(() =>
    this.item().unit_price_snapshot.toFixed(2),
  );
  protected readonly formattedSubtotal = computed(() => this.item().subtotal.toFixed(2));

  protected onDecrement(): void {
    const next = this.item().quantity - 1;

    if (next < 1) {
      return;
    }

    this.emitQuantityChange(next);
  }

  protected onIncrement(): void {
    this.emitQuantityChange(this.item().quantity + 1);
  }

  protected onQuantityInput(value: string): void {
    const parsed = Number(value);

    if (!Number.isInteger(parsed) || parsed <= 0) {
      return;
    }

    this.emitQuantityChange(parsed);
  }

  protected onRemove(): void {
    this.remove.emit(this.item().product_sku);
  }

  private emitQuantityChange(quantity: number): void {
    this.quantityChange.emit({ sku: this.item().product_sku, quantity });
  }
}
