import { Component, computed, effect, inject, signal } from '@angular/core';
import type { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Router, RouterLink } from '@angular/router';
import { ProductCard } from '../product-card/product-card';
import { ProductService } from '../product.service';
import type { ProductQueryParams } from '../product.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';

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

  protected readonly page = signal(1);
  protected readonly searchTerm = signal('');
  protected readonly selectedCategory = signal('');
  protected readonly error = signal<string | null>(null);

  private readonly queryParams = computed<ProductQueryParams>(() => ({
    page: this.page(),
    perPage: DEFAULT_PER_PAGE,
    q: this.searchTerm() || undefined,
    category: this.selectedCategory() || undefined,
  }));

  protected readonly productsResource = this.productService.getProducts(() => this.queryParams());
  protected readonly categoriesResource = this.productService.getCategories();

  protected readonly products = computed(() => this.productsResource.value()?.items ?? []);
  protected readonly paging = computed(() => this.productsResource.value()?.paging ?? null);
  protected readonly categories = computed(() =>
    this.categoriesResource.error() ? [] : (this.categoriesResource.value() ?? []),
  );
  protected readonly loading = computed(() => this.productsResource.isLoading());

  constructor() {
    effect(() => {
      const err = this.productsResource.error();

      this.error.set(err ? extractApiErrorMessage(err as HttpErrorResponse) : null);
    });
  }

  protected onSearchTermChange(value: string): void {
    this.searchTerm.set(value);
    this.page.set(1);
  }

  protected onCategoryChange(value: string): void {
    this.selectedCategory.set(value);
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

  protected onEdit(sku: string): void {
    void this.router.navigate(['/products', sku, 'edit']);
  }

  protected async onDelete(sku: string): Promise<void> {
    if (!window.confirm(`Delete product "${sku}"? This action cannot be undone.`)) {
      return;
    }

    try {
      await firstValueFrom(this.productService.deleteProduct(sku));
      this.productsResource.reload();
    } catch (err) {
      this.error.set(extractApiErrorMessage(err as HttpErrorResponse));
    }
  }
}
