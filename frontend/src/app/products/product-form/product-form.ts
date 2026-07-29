import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
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

  private readonly paramMap = toSignal(this.route.paramMap);
  protected readonly sku = computed(() => this.paramMap()?.get('sku') ?? undefined);
  protected readonly isEditMode = computed(() => this.sku() !== undefined);

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

  protected readonly productResource = this.productService.getProduct(() => this.sku());
  protected readonly loading = computed(() => this.productResource.isLoading());

  constructor() {
    effect(() => {
      const sku = this.sku();

      if (sku === undefined) {
        this.form.reset({ sku: '', name: '', description: '', price: 0, category: '', stock: 0 });
        this.form.controls.sku.enable();

        return;
      }

      this.form.controls.sku.setValue(sku);
      this.form.controls.sku.disable();
    });

    effect(() => {
      if (this.productResource.error()) {
        return;
      }

      const product = this.productResource.value();

      if (!product) {
        return;
      }

      this.form.patchValue({
        sku: product.sku,
        name: product.name,
        description: product.description ?? '',
        price: product.price,
        category: product.category,
        stock: product.stock,
      });
    });

    effect(() => {
      const err = this.productResource.error();

      this.apiError.set(err ? extractApiErrorMessage(err as HttpErrorResponse) : null);
    });
  }

  protected async onSubmit(): Promise<void> {
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
      await this.submitEdit(result.data);

      return;
    }

    await this.submitCreate(raw.sku, result.data);
  }

  private async submitCreate(
    sku: string,
    data: ReturnType<typeof CreateProductSchema.parse>,
  ): Promise<void> {
    try {
      await firstValueFrom(this.productService.createProduct({ sku, ...data }));
      void this.router.navigate(['/products']);
    } catch (err) {
      this.handleApiError(err as HttpErrorResponse);
    }
  }

  private async submitEdit(data: ReturnType<typeof UpdateProductSchema.parse>): Promise<void> {
    const sku = this.sku();

    if (!sku) {
      this.submitting.set(false);

      return;
    }

    try {
      const updated = await firstValueFrom(this.productService.updateProduct(sku, data));
      void this.router.navigate(['/products', updated.sku]);
    } catch (err) {
      this.handleApiError(err as HttpErrorResponse);
    }
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
}
