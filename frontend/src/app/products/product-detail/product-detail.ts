import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ProductService } from '../product.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';

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

  private readonly paramMap = toSignal(this.route.paramMap);
  private readonly sku = computed(() => this.paramMap()?.get('sku') ?? undefined);

  protected readonly error = signal<string | null>(null);

  protected readonly productResource = this.productService.getProduct(() => this.sku());

  protected readonly product = computed(() => this.productResource.value() ?? null);
  protected readonly loading = computed(() => this.productResource.isLoading());
  protected readonly notFound = computed(
    () => (this.productResource.error() as HttpErrorResponse | undefined)?.status === 404,
  );

  constructor() {
    effect(() => {
      const err = this.productResource.error() as HttpErrorResponse | undefined;

      if (!err || err.status === 404) {
        this.error.set(null);

        return;
      }

      this.error.set(extractApiErrorMessage(err));
    });
  }

  protected onEdit(): void {
    const sku = this.product()?.sku;

    if (!sku) {
      return;
    }

    void this.router.navigate(['/products', sku, 'edit']);
  }

  protected async onDelete(): Promise<void> {
    const sku = this.product()?.sku;

    if (!sku || !window.confirm(`Delete product "${sku}"? This action cannot be undone.`)) {
      return;
    }

    try {
      await firstValueFrom(this.productService.deleteProduct(sku));
      void this.router.navigate(['/products']);
    } catch (err) {
      this.error.set(extractApiErrorMessage(err as HttpErrorResponse));
    }
  }
}
