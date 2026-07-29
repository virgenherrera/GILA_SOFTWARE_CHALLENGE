import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CheckoutService } from './checkout.service';
import type { Order } from './checkout.service';

const mockOrder: Order = {
  id: 'order-1',
  status: 'Paid',
  placed_at: '2026-07-28T10:00:00Z',
  items: [
    {
      product_sku: 'RS-001',
      name: 'Running Shoes',
      quantity: 2,
      unit_price: 89.99,
      line_subtotal: 179.98,
    },
  ],
  total_amount: 179.98,
};

describe('CheckoutService', () => {
  let service: CheckoutService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CheckoutService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('checkout should POST /api/checkout with no body and return the created order', () => {
    let result: Order | undefined;

    service.checkout().subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/checkout');

    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(mockOrder);

    expect(result).toEqual(mockOrder);
  });

  it('getOrder should GET the order by id', () => {
    let result: Order | undefined;

    service.getOrder('order-1').subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/orders/order-1');

    expect(req.request.method).toBe('GET');
    req.flush(mockOrder);

    expect(result).toEqual(mockOrder);
  });
});
