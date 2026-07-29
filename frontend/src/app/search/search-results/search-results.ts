import { Component, input, output } from '@angular/core';
import type { ProductResponse } from '../../shared/validation/product.schema';

@Component({
  selector: 'app-search-results',
  templateUrl: './search-results.html',
  styleUrl: './search-results.css',
})
export class SearchResults {
  readonly products = input<ProductResponse[]>([]);

  readonly view = output<string>();
  readonly addToCart = output<string>();

  protected formatPrice(price: number): string {
    return price.toFixed(2);
  }

  protected onView(sku: string): void {
    this.view.emit(sku);
  }

  protected onAddToCart(sku: string): void {
    this.addToCart.emit(sku);
  }
}
