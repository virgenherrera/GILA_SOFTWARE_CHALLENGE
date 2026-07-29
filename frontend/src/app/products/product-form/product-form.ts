import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import type { ZodError } from 'zod';
import { ProductService } from '../product.service';
import { extractApiErrorMessage } from '../../shared/utils/api-error';
import { CreateProductSchema, UpdateProductSchema } from '../../shared/validation/product.schema';

@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './product-form.html',
  styleUrl: './product-form.css',
})
export class ProductForm {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly isEditMode = signal(false);
  protected readonly sku = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly apiError = signal<string | null>(null);

  protected readonly heading = computed(() => (this.isEditMode() ? 'Edit Product' : 'New Product'));

  // Validators.* are static, stateless functions (no `this` usage) — safe to reference unbound,
  // which is exactly how Angular's own docs use them.
  /* eslint-disable @typescript-eslint/unbound-method */
  protected readonly form = this.fb.nonNullable.group({
    sku: ['', [Validators.required, Validators.maxLength(50)]],
    name: ['', [Validators.required, Validators.maxLength(256)]],
    description: [''],
    price: [0, [Validators.required, Validators.min(0.01), Validators.max(99999.99)]],
    category: ['', [Validators.required, Validators.maxLength(100)]],
    stock: [0, [Validators.required, Validators.min(0)]],
  });
  /* eslint-enable @typescript-eslint/unbound-method */

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const sku = params.get('sku');

      if (sku) {
        this.isEditMode.set(true);
        this.sku.set(sku);
        this.form.controls.sku.setValue(sku);
        this.form.controls.sku.disable();
        this.loadProduct(sku);

        return;
      }

      this.isEditMode.set(false);
      this.sku.set(null);
      this.form.reset({ sku: '', name: '', description: '', price: 0, category: '', stock: 0 });
      this.form.controls.sku.enable();
    });
  }

  protected onSubmit(): void {
    this.apiError.set(null);
    this.form.markAllAsTouched();

    if (this.form.controls.sku.invalid) {
      return;
    }

    const raw = this.form.getRawValue();
    const payload = {
      name: raw.name,
      description: raw.description.trim() === '' ? undefined : raw.description,
      price: Number(raw.price),
      category: raw.category,
      stock: Number(raw.stock),
    };

    const schema = this.isEditMode() ? UpdateProductSchema : CreateProductSchema;
    const result = schema.safeParse(payload);

    if (!result.success) {
      this.applyZodErrors(result.error);

      return;
    }

    this.submitting.set(true);

    if (this.isEditMode()) {
      this.submitEdit(result.data);

      return;
    }

    this.submitCreate(raw.sku, result.data);
  }

  private submitCreate(sku: string, data: ReturnType<typeof CreateProductSchema.parse>): void {
    this.productService
      .createProduct({ sku, ...data })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigate(['/products']),
        error: (err: HttpErrorResponse) => this.handleApiError(err),
      });
  }

  private submitEdit(data: ReturnType<typeof UpdateProductSchema.parse>): void {
    const sku = this.sku();

    if (!sku) {
      this.submitting.set(false);

      return;
    }

    this.productService
      .updateProduct(sku, data)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => void this.router.navigate(['/products', updated.sku]),
        error: (err: HttpErrorResponse) => this.handleApiError(err),
      });
  }

  private handleApiError(err: HttpErrorResponse): void {
    this.submitting.set(false);

    const message = extractApiErrorMessage(err);

    this.apiError.set(message);

    if (err.status === 409) {
      this.form.controls.sku.setErrors({
        ...(this.form.controls.sku.errors ?? {}),
        conflict: message,
      });
    }
  }

  private applyZodErrors(error: ZodError): void {
    for (const issue of error.issues) {
      const field = issue.path[0];

      if (typeof field === 'string' && field in this.form.controls) {
        const control = this.form.get(field);

        control?.setErrors({ ...(control.errors ?? {}), zod: issue.message });
      }
    }
  }

  private loadProduct(sku: string): void {
    this.loading.set(true);
    this.apiError.set(null);

    this.productService
      .getProduct(sku)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (product) => {
          this.form.patchValue({
            sku: product.sku,
            name: product.name,
            description: product.description ?? '',
            price: product.price,
            category: product.category,
            stock: product.stock,
          });
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.apiError.set(extractApiErrorMessage(err));
        },
      });
  }
}
