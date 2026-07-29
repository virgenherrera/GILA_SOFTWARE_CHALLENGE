import { Component, input, output } from '@angular/core';
import { ProductCard } from '../../products/product-card/product-card';
import type { ProductResponse } from '../../shared/validation/product.schema';

@Component({
  selector: 'app-search-results',
  imports: [ProductCard],
  templateUrl: './search-results.html',
  styleUrl: './search-results.css',
})
export class SearchResults {
  readonly products = input<ProductResponse[]>([]);

  readonly view = output<string>();
  readonly addToCart = output<string>();

  protected onView(sku: string): void {
    this.view.emit(sku);
  }

  protected onAddToCart(sku: string): void {
    this.addToCart.emit(sku);
  }
}
