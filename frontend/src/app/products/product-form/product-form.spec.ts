import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ProductForm } from './product-form';
import type { ProductResponse } from '../../shared/validation/product.schema';

interface CreatePayload {
  sku: string;
  name: string;
}

interface UpdatePayload {
  sku?: string;
  name: string;
}

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

function setInputValue(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ProductForm', () => {
  let httpMock: HttpTestingController;
  let navigateSpy: ReturnType<typeof vi.fn>;

  function setup(sku: string | null) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap(sku ? { sku } : {})) },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    return TestBed.createComponent(ProductForm);
  }

  afterEach(() => {
    httpMock.verify();
  });

  describe('create mode', () => {
    it('should create with the New Product heading', () => {
      const fixture = setup(null);

      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;

      expect(fixture.componentInstance).toBeTruthy();
      expect(compiled.textContent).toContain('New Product');
    });

    it('should not submit and should show errors when required fields are missing', () => {
      const fixture = setup(null);

      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const form = compiled.querySelector('form') as HTMLFormElement;

      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      httpMock.expectNone((r) => r.method === 'POST');

      expect(compiled.textContent).toContain('SKU is required');
    });

    it('should submit a valid form and navigate to the product list', () => {
      const fixture = setup(null);

      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;

      setInputValue(compiled.querySelector('#sku') as HTMLInputElement, 'RS-002');
      setInputValue(compiled.querySelector('#name') as HTMLInputElement, 'Trail Shoes');
      setInputValue(compiled.querySelector('#category') as HTMLInputElement, 'Footwear');
      setInputValue(compiled.querySelector('#price') as HTMLInputElement, '120');
      setInputValue(compiled.querySelector('#stock') as HTMLInputElement, '10');
      fixture.detectChanges();

      const form = compiled.querySelector('form') as HTMLFormElement;

      form.dispatchEvent(new Event('submit'));

      const req = httpMock.expectOne((r) => r.url === '/api/products' && r.method === 'POST');
      const body = req.request.body as CreatePayload;

      expect(body.sku).toBe('RS-002');
      req.flush({ ...mockProduct, sku: 'RS-002', name: 'Trail Shoes', price: 120, stock: 10 });

      expect(navigateSpy).toHaveBeenCalledWith(['/products']);
    });

    it('should show zod validation errors when the name is missing', () => {
      const fixture = setup(null);

      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;

      setInputValue(compiled.querySelector('#sku') as HTMLInputElement, 'RS-002');
      setInputValue(compiled.querySelector('#category') as HTMLInputElement, 'Footwear');
      setInputValue(compiled.querySelector('#price') as HTMLInputElement, '120');
      setInputValue(compiled.querySelector('#stock') as HTMLInputElement, '10');
      fixture.detectChanges();

      const form = compiled.querySelector('form') as HTMLFormElement;

      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      httpMock.expectNone((r) => r.method === 'POST');
    });

    it('should mark sku as conflicted on a 409 response', () => {
      const fixture = setup(null);

      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;

      setInputValue(compiled.querySelector('#sku') as HTMLInputElement, 'RS-001');
      setInputValue(compiled.querySelector('#name') as HTMLInputElement, 'Trail Shoes');
      setInputValue(compiled.querySelector('#category') as HTMLInputElement, 'Footwear');
      setInputValue(compiled.querySelector('#price') as HTMLInputElement, '120');
      setInputValue(compiled.querySelector('#stock') as HTMLInputElement, '10');
      fixture.detectChanges();

      const form = compiled.querySelector('form') as HTMLFormElement;

      form.dispatchEvent(new Event('submit'));

      const req = httpMock.expectOne((r) => r.url === '/api/products' && r.method === 'POST');

      req.flush(
        { error: { code: 'CONFLICT', message: 'SKU already exists' } },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(compiled.textContent).toContain('SKU already exists');
    });
  });

  describe('edit mode', () => {
    it('should prefill the form and disable the sku field', () => {
      const fixture = setup('RS-001');

      fixture.detectChanges();
      httpMock.expectOne('/api/products/RS-001').flush(mockProduct);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const skuInput = compiled.querySelector('#sku') as HTMLInputElement;

      expect(compiled.textContent).toContain('Edit Product');
      expect(skuInput.value).toBe('RS-001');
      expect(skuInput.disabled).toBe(true);
    });

    it('should submit updates via PUT without sku and navigate to the detail page', () => {
      const fixture = setup('RS-001');

      fixture.detectChanges();
      httpMock.expectOne('/api/products/RS-001').flush(mockProduct);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;

      setInputValue(compiled.querySelector('#name') as HTMLInputElement, 'Updated Shoes');
      fixture.detectChanges();

      const form = compiled.querySelector('form') as HTMLFormElement;

      form.dispatchEvent(new Event('submit'));

      const req = httpMock.expectOne((r) => r.url === '/api/products/RS-001' && r.method === 'PUT');
      const body = req.request.body as UpdatePayload;

      expect(body.sku).toBeUndefined();
      expect(body.name).toBe('Updated Shoes');
      req.flush({ ...mockProduct, name: 'Updated Shoes' });

      expect(navigateSpy).toHaveBeenCalledWith(['/products', 'RS-001']);
    });

    it('should show an error banner when the product fails to load', () => {
      const fixture = setup('RS-001');

      fixture.detectChanges();
      httpMock
        .expectOne('/api/products/RS-001')
        .flush(
          { error: { code: 'INTERNAL_ERROR', message: 'Failed to load' } },
          { status: 500, statusText: 'Error' },
        );
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.textContent).toContain('Failed to load');
    });
  });
});
