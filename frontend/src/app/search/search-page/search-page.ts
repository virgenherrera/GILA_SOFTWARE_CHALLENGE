import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, firstValueFrom } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { SearchFiltersPanel } from '../search-filters/search-filters';
import { SearchResults } from '../search-results/search-results';
import { ProductService } from '../../products/product.service';
import type { ProductQueryParams } from '../../products/product.service';
import { CartService } from '../../cart/cart.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { NonKeywordSearchFilters } from '../search.types';

const DEFAULT_PER_PAGE = 20;
const KEYWORD_DEBOUNCE_MS = 300;

const DEFAULT_NON_KEYWORD_FILTERS: NonKeywordSearchFilters = {
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
  private readonly keyword$ = new Subject<string>();

  private readonly keyword = toSignal(
    this.keyword$.pipe(debounceTime(KEYWORD_DEBOUNCE_MS), distinctUntilChanged()),
    { initialValue: '' },
  );

  protected readonly error = signal<string | null>(null);
  protected readonly cartMessage = signal<string | null>(null);

  protected readonly page = signal(1);
  protected readonly nonKeywordFilters = signal<NonKeywordSearchFilters>({
    ...DEFAULT_NON_KEYWORD_FILTERS,
  });

  private readonly queryParams = computed<ProductQueryParams>(() => {
    const filters = this.nonKeywordFilters();

    return {
      page: this.page(),
      perPage: DEFAULT_PER_PAGE,
      q: this.keyword() || undefined,
      category: filters.category,
      priceMin: filters.priceMin,
      priceMax: filters.priceMax,
      sortBy: filters.sortBy,
      sortOrder: filters.sortOrder,
    };
  });

  protected readonly productsResource = this.productService.getProducts(() => this.queryParams());
  protected readonly categoriesResource = this.productService.getCategories();

  protected readonly categories = computed(() =>
    this.categoriesResource.error() ? [] : (this.categoriesResource.value() ?? []),
  );
  protected readonly products = computed(() => this.productsResource.value()?.items ?? []);
  protected readonly paging = computed(() => this.productsResource.value()?.paging ?? null);
  protected readonly loading = computed(() => this.productsResource.isLoading());

  protected readonly resultCountMessage = computed(() => {
    if (this.loading() || this.error()) {
      return '';
    }

    const count = this.paging()?.total ?? this.products().length;

    return `${count} product${count === 1 ? '' : 's'} found`;
  });

  constructor() {
    effect(() => {
      this.keyword();
      this.page.set(1);
    });

    effect(() => {
      const err = this.productsResource.error();

      this.error.set(err ? extractApiErrorMessage(err as HttpErrorResponse) : null);
    });
  }

  protected onKeywordChange(value: string): void {
    this.keyword$.next(value);
  }

  protected onFiltersChange(partial: NonKeywordSearchFilters): void {
    this.nonKeywordFilters.update((current) => ({ ...current, ...partial }));
    this.page.set(1);
  }

  protected onPrevPage(): void {
    if (!this.paging()?.prev) {
      return;
    }

    this.page.update((current) => Math.max(1, current - 1));
  }

  protected onNextPage(): void {
    if (!this.paging()?.next) {
      return;
    }

    this.page.update((current) => current + 1);
  }

  protected onView(sku: string): void {
    void this.router.navigate(['/products', sku]);
  }

  protected async onAddToCart(sku: string): Promise<void> {
    try {
      await firstValueFrom(this.cartService.addItem(sku, 1));
      this.cartMessage.set('Added to cart successfully.');
      setTimeout(() => this.cartMessage.set(null), 3000);
    } catch (err) {
      this.cartMessage.set(extractApiErrorMessage(err as HttpErrorResponse));
    }
  }
}
