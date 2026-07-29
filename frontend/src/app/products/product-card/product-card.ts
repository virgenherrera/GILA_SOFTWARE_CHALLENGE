import { Component, computed, input, output } from '@angular/core';
import type { ProductResponse } from '../../shared/validation/product.schema';

@Component({
  selector: 'app-product-card',
  templateUrl: './product-card.html',
  styleUrl: './product-card.css',
})
export class ProductCard {
  readonly product = input.required<ProductResponse>();

  readonly view = output<string>();
  readonly edit = output<string>();
  readonly delete = output<string>();

  protected readonly formattedPrice = computed(() => this.product().price.toFixed(2));
  protected readonly isOutOfStock = computed(() => this.product().stock === 0);

  protected onView(): void {
    this.view.emit(this.product().sku);
  }

  protected onEdit(): void {
    this.edit.emit(this.product().sku);
  }

  protected onDelete(): void {
    this.delete.emit(this.product().sku);
  }
}
