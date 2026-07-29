import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { ProductCard } from '../product-card/product-card';
import { ProductService } from '../product.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { Paging, ProductResponse } from '../../shared/validation/product.schema';

const DEFAULT_PER_PAGE = 20;

@Component({
  selector: 'app-product-list',
  imports: [ProductCard, RouterLink],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList {
  private readonly productService = inject(ProductService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly products = signal<ProductResponse[]>([]);
  protected readonly paging = signal<Paging | null>(null);
  protected readonly categories = signal<string[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly page = signal(1);
  protected readonly searchTerm = signal('');
  protected readonly selectedCategory = signal('');

  constructor() {
    this.productService
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => this.categories.set(categories),
        error: () => this.categories.set([]),
      });

    this.loadProducts();
  }

  protected onSearchTermChange(value: string): void {
    this.searchTerm.set(value);
    this.page.set(1);
    this.loadProducts();
  }

  protected onCategoryChange(value: string): void {
    this.selectedCategory.set(value);
    this.page.set(1);
    this.loadProducts();
  }

  protected onPrevPage(): void {
    if (!this.paging()?.prev) {
      return;
    }

    this.page.update((current) => Math.max(1, current - 1));
    this.loadProducts();
  }

  protected onNextPage(): void {
    if (!this.paging()?.next) {
      return;
    }

    this.page.update((current) => current + 1);
    this.loadProducts();
  }

  protected onView(sku: string): void {
    void this.router.navigate(['/products', sku]);
  }

  protected onEdit(sku: string): void {
    void this.router.navigate(['/products', sku, 'edit']);
  }

  protected onDelete(sku: string): void {
    if (!window.confirm(`Delete product "${sku}"? This action cannot be undone.`)) {
      return;
    }

    this.productService
      .deleteProduct(sku)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loadProducts(),
        error: (err: HttpErrorResponse) => this.error.set(extractApiErrorMessage(err)),
      });
  }

  private loadProducts(): void {
    this.loading.set(true);
    this.error.set(null);

    this.productService
      .getProducts({
        page: this.page(),
        perPage: DEFAULT_PER_PAGE,
        q: this.searchTerm() || undefined,
        category: this.selectedCategory() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.products.set(response.items);
          this.paging.set(response.paging);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(extractApiErrorMessage(err));
          this.loading.set(false);
        },
      });
  }
}
