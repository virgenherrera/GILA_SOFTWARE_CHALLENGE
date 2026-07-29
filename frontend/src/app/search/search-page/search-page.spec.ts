import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SearchPage } from './search-page';
import { CartService } from '../../cart/cart.service';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
import type { PagedResponse } from '../../products/product.service';
import type { ProductResponse } from '../../shared/validation/product.schema';
import type { Cart } from '../../shared/validation/cart.schema';

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

function setInputValue(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function setSelectValue(select: HTMLSelectElement, value: string): void {
  select.value = value;
  select.dispatchEvent(new Event('change'));
}

const mockCart: Cart = { items: [], total: 0 };

describe('SearchPage', () => {
  let httpMock: HttpTestingController;
  let navigateSpy: ReturnType<typeof vi.fn>;
  let cartServiceMock: { addItem: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    cartServiceMock = { addItem: vi.fn().mockReturnValue(of(mockCart)) };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CartService, useValue: cartServiceMock },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  async function createAndFlush() {
    const fixture = TestBed.createComponent(SearchPage);

    fixture.detectChanges();

    httpMock
      .expectOne((req) => req.url === '/api/products/categories')
      .flush({ categories: ['Footwear', 'Electronics'] });
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

  it('should normalize the { categories: [...] } envelope into the category dropdown', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;
    const options = Array.from(compiled.querySelectorAll('#category option')).map((option) =>
      option.textContent?.trim(),
    );

    expect(options).toEqual(['All categories', 'Footwear', 'Electronics']);
  });

  it('should announce the result count in a polite aria-live region', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;
    const liveRegion = compiled.querySelector('[aria-live="polite"]');

    expect(liveRegion).not.toBeNull();
    expect(liveRegion?.textContent).toContain('1 product found');
  });

  it('should show a loading state while the request is in flight', () => {
    const fixture = TestBed.createComponent(SearchPage);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Loading products…');

    httpMock.expectOne((req) => req.url === '/api/products/categories').flush({ categories: [] });
    httpMock.expectOne((req) => req.url === '/api/products').flush(mockPage);
  });

  it('should show an error state when the request fails', async () => {
    const fixture = TestBed.createComponent(SearchPage);

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

  it('should show an empty state when there are no results', async () => {
    const fixture = TestBed.createComponent(SearchPage);

    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/products/categories').flush({ categories: [] });
    httpMock
      .expectOne((req) => req.url === '/api/products')
      .flush({ items: [], paging: { page: 1, perPage: 20, total: 0, prev: null, next: null } });
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('No products found');
  });

  it('should debounce keyword input by 300ms before searching', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    vi.useFakeTimers();
    setInputValue(compiled.querySelector('#keyword') as HTMLInputElement, 'shoes');

    httpMock.expectNone((req) => req.url === '/api/products');

    vi.advanceTimersByTime(300);
    await Promise.resolve();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.get('q')).toBe('shoes');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(mockPage);
  });

  it('should request the category filter immediately without debouncing', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    setSelectValue(compiled.querySelector('#category') as HTMLSelectElement, 'Footwear');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.get('category')).toBe('Footwear');
    req.flush(mockPage);
  });

  it('should combine keyword, category, price, and sort filters cumulatively', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    setSelectValue(compiled.querySelector('#category') as HTMLSelectElement, 'Footwear');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/products').flush(mockPage);

    setInputValue(compiled.querySelector('#price-min') as HTMLInputElement, '10');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/products').flush(mockPage);

    setSelectValue(compiled.querySelector('#sort-by') as HTMLSelectElement, 'price');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/products');

    expect(req.request.params.get('category')).toBe('Footwear');
    expect(req.request.params.get('priceMin')).toBe('10');
    expect(req.request.params.get('sortBy')).toBe('price');
    req.flush(mockPage);
  });

  it('should not request when the price range is invalid', async () => {
    const fixture = await createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    // Max alone is a valid range and triggers one request; consume it first.
    setInputValue(compiled.querySelector('#price-max') as HTMLInputElement, '10');
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/products').flush(mockPage);

    // Min now exceeds max: invalid, must not trigger a further request.
    setInputValue(compiled.querySelector('#price-min') as HTMLInputElement, '100');
    fixture.detectChanges();

    httpMock.expectNone((req) => req.url === '/api/products');
  });

  it('should request the next page when Next is clicked', async () => {
    const fixture = TestBed.createComponent(SearchPage);

    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/products/categories').flush({ categories: [] });
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

  it('should navigate to the detail page when View is clicked', async () => {
    const fixture = await createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, 'View')?.click();

    expect(navigateSpy).toHaveBeenCalledWith(['/products', 'RS-001']);
  });

  it('should add the item to the cart when Add to Cart is clicked', async () => {
    const fixture = await createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Add to Cart')?.click();
    await Promise.resolve();
    fixture.detectChanges();

    expect(cartServiceMock.addItem).toHaveBeenCalledWith('RS-001', 1);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Added to cart successfully');
  });

  it('should show an error message when adding to cart fails', async () => {
    const fixture = await createAndFlush();

    cartServiceMock.addItem.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            error: { error: { code: 'INTERNAL_ERROR', message: 'Unable to add item to cart' } },
            status: 500,
            statusText: 'Internal Server Error',
          }),
      ),
    );

    findButtonByText(fixture.nativeElement as HTMLElement, 'Add to Cart')?.click();
    await Promise.resolve();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Unable to add item to cart');
  });
});
