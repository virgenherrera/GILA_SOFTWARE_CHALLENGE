import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProductService } from '../product.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import type { ProductResponse } from '../../shared/validation/product.schema';

@Component({
  selector: 'app-product-detail',
  imports: [RouterLink],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.css',
})
export class ProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly product = signal<ProductResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const sku = params.get('sku');

      if (sku) {
        this.loadProduct(sku);
      }
    });
  }

  protected onEdit(): void {
    const sku = this.product()?.sku;

    if (!sku) {
      return;
    }

    void this.router.navigate(['/products', sku, 'edit']);
  }

  protected onDelete(): void {
    const sku = this.product()?.sku;

    if (!sku || !window.confirm(`Delete product "${sku}"? This action cannot be undone.`)) {
      return;
    }

    this.productService
      .deleteProduct(sku)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigate(['/products']),
        error: (err: HttpErrorResponse) => this.error.set(extractApiErrorMessage(err)),
      });
  }

  private loadProduct(sku: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.notFound.set(false);

    this.productService
      .getProduct(sku)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (product) => {
          this.product.set(product);
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
