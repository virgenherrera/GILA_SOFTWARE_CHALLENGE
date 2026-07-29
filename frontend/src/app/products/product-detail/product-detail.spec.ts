import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ProductDetail } from './product-detail';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
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

describe('ProductDetail', () => {
  let httpMock: HttpTestingController;
  let navigateSpy: ReturnType<typeof vi.fn>;

  function setup(sku = 'RS-001') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ sku })) },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    return TestBed.createComponent(ProductDetail);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/products/RS-001').flush(mockProduct);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the product once loaded', () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/products/RS-001').flush(mockProduct);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Running Shoes');
    expect(compiled.textContent).toContain('Footwear');
  });

  it('should show a not-found state on 404', () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/products/RS-001')
      .flush(
        { error: { code: 'NOT_FOUND', message: 'Product not found' } },
        { status: 404, statusText: 'Not Found' },
      );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Product not found.');
  });

  it('should show an error state on other failures', () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/products/RS-001')
      .flush(
        { error: { code: 'INTERNAL_ERROR', message: 'Server exploded' } },
        { status: 500, statusText: 'Error' },
      );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Server exploded');
  });

  it('should navigate to edit when the Edit button is clicked', () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/products/RS-001').flush(mockProduct);
    fixture.detectChanges();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Edit')?.click();

    expect(navigateSpy).toHaveBeenCalledWith(['/products', 'RS-001', 'edit']);
  });

  it('should delete and navigate back to the list after confirmation', () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/products/RS-001').flush(mockProduct);
    fixture.detectChanges();
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    findButtonByText(fixture.nativeElement as HTMLElement, 'Delete')?.click();

    httpMock.expectOne('/api/products/RS-001').flush(null);

    expect(navigateSpy).toHaveBeenCalledWith(['/products']);
  });
});
