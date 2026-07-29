import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map } from 'rxjs';
import type { Observable } from 'rxjs';
import type {
  CreateProduct,
  Paging,
  ProductResponse,
  UpdateProduct,
} from '../shared/validation/product.schema';

export interface PagedResponse<T> {
  items: T[];
  paging: Paging;
}

export interface ProductQueryParams {
  page?: number;
  perPage?: number;
  q?: string;
  category?: string;
  priceMin?: number;
  priceMax?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * Payload accepted by POST /api/products. The backend's create contract requires `sku` in the
 * body (unlike PUT), while `CreateProduct` (from the Zod schema) intentionally omits it because
 * `sku` is validated separately as a plain form field, not through the Zod create schema.
 */
export type CreateProductPayload = CreateProduct & { sku: string };

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/products';

  getProducts(params: ProductQueryParams = {}): Observable<PagedResponse<ProductResponse>> {
    let httpParams = new HttpParams();
    const entries = Object.entries(params) as [string, string | number | undefined][];

    for (const [key, value] of entries) {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, value);
      }
    }

    return this.http.get<PagedResponse<ProductResponse>>(this.baseUrl, { params: httpParams });
  }

  getCategories(): Observable<string[]> {
    return this.http
      .get<{ categories: string[] }>(`${this.baseUrl}/categories`)
      .pipe(map((response) => response.categories));
  }

  getProduct(sku: string): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.baseUrl}/${sku}`);
  }

  createProduct(data: CreateProductPayload): Observable<ProductResponse> {
    return this.http.post<ProductResponse>(this.baseUrl, data);
  }

  updateProduct(sku: string, data: UpdateProduct): Observable<ProductResponse> {
    return this.http.put<ProductResponse>(`${this.baseUrl}/${sku}`, data);
  }

  deleteProduct(sku: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${sku}`);
  }
}
