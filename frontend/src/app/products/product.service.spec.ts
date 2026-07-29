import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductService } from './product.service';
import type { PagedResponse } from './product.service';
import type { ProductResponse } from '../shared/validation/product.schema';

const mockProduct: ProductResponse = {
  sku: 'RS-001',
  name: 'Running Shoes',
  description: 'Lightweight running shoes',
  category: 'Footwear',
  price: 89.99,
  stock: 150,
  weight_kg: 0.35,
  created_at: '2026-07-27T14:30:00Z',
  updated_at: '2026-07-27T14:30:00Z',
};

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getProducts should GET the paged list with query params', async () => {
    const mockResponse: PagedResponse<ProductResponse> = {
      items: [mockProduct],
      paging: { page: 1, perPage: 20, total: 1, prev: null, next: null },
    };

    const resource = TestBed.runInInjectionContext(() =>
      service.getProducts(() => ({ page: 1, perPage: 20, q: 'shoes', category: 'Footwear' })),
    );

    TestBed.tick();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/products' && r.method === 'GET' && r.params.get('q') === 'shoes',
    );

    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('perPage')).toBe('20');
    expect(req.request.params.get('category')).toBe('Footwear');
    req.flush(mockResponse);
    await Promise.resolve();
    TestBed.tick();

    expect(resource.value()).toEqual(mockResponse);
  });

  it('getProducts should omit undefined and empty params', () => {
    TestBed.runInInjectionContext(() => service.getProducts(() => ({ page: 1 })));

    TestBed.tick();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('category')).toBe(false);
    req.flush({ items: [], paging: { page: 1, perPage: 20, total: 0, prev: null, next: null } });
  });

  it('getCategories should GET the category list', async () => {
    const resource = TestBed.runInInjectionContext(() => service.getCategories());

    TestBed.tick();

    const req = httpMock.expectOne('/api/products/categories');

    expect(req.request.method).toBe('GET');
    req.flush({ categories: ['Footwear', 'Electronics'] });
    await Promise.resolve();
    TestBed.tick();

    expect(resource.value()).toEqual(['Footwear', 'Electronics']);
  });

  it('getProduct should GET a single product by sku', async () => {
    const resource = TestBed.runInInjectionContext(() => service.getProduct(() => 'RS-001'));

    TestBed.tick();

    const req = httpMock.expectOne('/api/products/RS-001');

    expect(req.request.method).toBe('GET');
    req.flush(mockProduct);
    await Promise.resolve();
    TestBed.tick();

    expect(resource.value()).toEqual(mockProduct);
  });

  it('getProduct should stay idle when sku is undefined', () => {
    const resource = TestBed.runInInjectionContext(() => service.getProduct(() => undefined));

    TestBed.tick();

    httpMock.expectNone(() => true);
    expect(resource.value()).toBeUndefined();
  });

  it('createProduct should POST the new product', () => {
    const payload = {
      sku: 'RS-002',
      name: 'Trail Shoes',
      price: 120,
      category: 'Footwear',
      stock: 10,
    };
    let result: ProductResponse | undefined;

    service.createProduct(payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/products');

    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ ...mockProduct, ...payload });

    expect(result?.sku).toBe('RS-002');
  });

  it('updateProduct should PUT without sku in the body', () => {
    const payload = { name: 'Updated Name', price: 99.99, category: 'Footwear', stock: 5 };
    let result: ProductResponse | undefined;

    service.updateProduct('RS-001', payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/products/RS-001');

    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    expect((req.request.body as { sku?: string }).sku).toBeUndefined();
    req.flush({ ...mockProduct, ...payload });

    expect(result?.name).toBe('Updated Name');
  });

  it('deleteProduct should DELETE the product by sku', () => {
    let completed = false;

    service.deleteProduct('RS-001').subscribe(() => (completed = true));

    const req = httpMock.expectOne('/api/products/RS-001');

    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });
});
