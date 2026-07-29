import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { SearchFiltersPanel } from '../search-filters/search-filters';
import { SearchResults } from '../search-results/search-results';
import { ProductService } from '../../products/product.service';
import { CartService } from '../../cart/cart.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { NonKeywordSearchFilters, SearchFilters } from '../search.types';
import type { Paging, ProductResponse } from '../../shared/validation/product.schema';

const DEFAULT_PER_PAGE = 20;
const KEYWORD_DEBOUNCE_MS = 300;

const DEFAULT_FILTERS: SearchFilters = {
  q: undefined,
  category: undefined,
  priceMin: undefined,
  priceMax: undefined,
  sortBy: 'name',
  sortOrder: 'asc',
};

@Component({
  selector: 'app-search-page',
  imports: [SearchFiltersPanel, SearchResults],
  templateUrl: './search-page.html',
  styleUrl: './search-page.css',
})
export class SearchPage {
  private readonly productService = inject(ProductService);
  private readonly cartService = inject(CartService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly keyword$ = new Subject<string>();

  protected readonly categories = signal<string[]>([]);
  protected readonly products = signal<ProductResponse[]>([]);
  protected readonly paging = signal<Paging | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly cartMessage = signal<string | null>(null);

  protected readonly page = signal(1);
  protected readonly filters = signal<SearchFilters>({ ...DEFAULT_FILTERS });

  constructor() {
    this.keyword$
      .pipe(
        debounceTime(KEYWORD_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((q) => {
        this.filters.update((current) => ({ ...current, q: q || undefined }));
        this.page.set(1);
        this.loadProducts();
      });

    this.loadCategories();
    this.loadProducts();
  }

  protected onKeywordChange(value: string): void {
    this.keyword$.next(value);
  }

  protected onFiltersChange(partial: NonKeywordSearchFilters): void {
    this.filters.update((current) => ({ ...current, ...partial }));
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

  protected onAddToCart(sku: string): void {
    this.cartService
      .addItem(sku, 1)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.cartMessage.set('Added to cart successfully.');
          setTimeout(() => this.cartMessage.set(null), 3000);
        },
        error: (err: HttpErrorResponse) => {
          this.cartMessage.set(extractApiErrorMessage(err));
        },
      });
  }

  private loadCategories(): void {
    this.productService
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => this.categories.set(categories),
        error: () => this.categories.set([]),
      });
  }

  private loadProducts(): void {
    this.loading.set(true);
    this.error.set(null);

    const current = this.filters();

    this.productService
      .getProducts({
        page: this.page(),
        perPage: DEFAULT_PER_PAGE,
        q: current.q,
        category: current.category,
        priceMin: current.priceMin,
        priceMax: current.priceMax,
        sortBy: current.sortBy,
        sortOrder: current.sortOrder,
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
