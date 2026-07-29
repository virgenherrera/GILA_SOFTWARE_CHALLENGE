import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { ProductList } from './product-list';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
import type { PagedResponse } from '../product.service';
import type { ProductResponse } from '../../shared/validation/product.schema';

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

const mockPage: PagedResponse<ProductResponse> = {
  items: [mockProduct],
  paging: { page: 1, perPage: 20, total: 1, prev: null, next: null },
};

describe('ProductList', () => {
  let httpMock: HttpTestingController;
  let navigateSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    httpMock = TestBed.inject(HttpTestingController);
    navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  async function createAndFlush() {
    const fixture = TestBed.createComponent(ProductList);

    fixture.detectChanges();

    httpMock
      .expectOne((req) => req.url === '/api/products/categories')
      .flush({ categories: ['Footwear'] });
    httpMock.expectOne((req) => req.url === '/api/products').flush(mockPage);
    await fixture.whenStable();

    return fixture;
  }

  it('should create', async () => {
    const fixture = await createAndFlush();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the fetched products', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Running Shoes');
  });

  it('should show an empty state when there are no products', async () => {
    const fixture = TestBed.createComponent(ProductList);

    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/products/categories').flush({ categories: [] });
    httpMock
      .expectOne((req) => req.url === '/api/products')
      .flush({ items: [], paging: { page: 1, perPage: 20, total: 0, prev: null, next: null } });
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('No products found.');
  });

  it('should show an error state when the request fails', async () => {
    const fixture = TestBed.createComponent(ProductList);

    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/products/categories').flush({ categories: [] });
    httpMock
      .expectOne((req) => req.url === '/api/products')
      .flush(
        { error: { code: 'INTERNAL_ERROR', message: 'Something went wrong' } },
        { status: 500, statusText: 'Internal Server Error' },
      );
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Something went wrong');
  });

  it('should request the next page when Next is clicked', async () => {
    const fixture = TestBed.createComponent(ProductList);

    fixture.detectChanges();
    httpMock
      .expectOne((req) => req.url === '/api/products/categories')
      .flush({ categories: ['Footwear'] });
    httpMock
      .expectOne((req) => req.url === '/api/products')
      .flush({
        items: [mockProduct],
        paging: { page: 1, perPage: 20, total: 40, prev: null, next: '/api/products?page=2' },
      });
    await fixture.whenStable();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Next')?.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.get('page')).toBe('2');
    req.flush(mockPage);
  });

  it('should confirm and delete a product', async () => {
    const fixture = await createAndFlush();

    vi.spyOn(window, 'confirm').mockReturnValue(true);

    findButtonByText(fixture.nativeElement as HTMLElement, 'Delete')?.click();

    const deleteReq = httpMock.expectOne(
      (r) => r.url === '/api/products/RS-001' && r.method === 'DELETE',
    );

    deleteReq.flush(null);
    await Promise.resolve();
    fixture.detectChanges();

    httpMock.expectOne((req) => req.url === '/api/products').flush(mockPage);
  });

  it('should not delete when the confirmation is declined', async () => {
    const fixture = await createAndFlush();

    vi.spyOn(window, 'confirm').mockReturnValue(false);

    findButtonByText(fixture.nativeElement as HTMLElement, 'Delete')?.click();

    httpMock.expectNone((r) => r.method === 'DELETE');
  });

  it('should navigate to the detail page when View is clicked', async () => {
    const fixture = await createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, 'View')?.click();

    expect(navigateSpy).toHaveBeenCalledWith(['/products', 'RS-001']);
  });

  it('should navigate to the edit page when Edit is clicked', async () => {
    const fixture = await createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Edit')?.click();

    expect(navigateSpy).toHaveBeenCalledWith(['/products', 'RS-001', 'edit']);
  });

  it('should re-fetch with the search term when the search input changes', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;
    const searchInput = compiled.querySelector('input[type="search"]') as HTMLInputElement;

    searchInput.value = 'shoes';
    searchInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.get('q')).toBe('shoes');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(mockPage);
  });

  it('should associate the search input with a label', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;
    const searchInput = compiled.querySelector('input[type="search"]') as HTMLInputElement;
    const label = compiled.querySelector(`label[for="${searchInput.id}"]`);

    expect(searchInput.id).toBeTruthy();
    expect(label).toBeTruthy();
  });

  it('should re-fetch with the selected category when the category select changes', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;
    const select = compiled.querySelector('select') as HTMLSelectElement;

    select.value = 'Footwear';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.get('category')).toBe('Footwear');
    req.flush(mockPage);
  });
});
