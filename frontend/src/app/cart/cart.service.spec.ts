import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CartService } from './cart.service';
import type { Cart } from '../shared/validation/cart.schema';
import type { Order } from '../shared/validation/checkout.schema';

const mockCart: Cart = {
  id: 'cart-1',
  status: 'Active',
  items: [
    {
      product_sku: 'RS-001',
      name: 'Running Shoes',
      quantity: 2,
      unit_price_snapshot: 89.99,
      subtotal: 179.98,
    },
  ],
  total: 179.98,
  created_at: '2026-07-27T14:30:00Z',
  updated_at: '2026-07-27T14:30:00Z',
};

describe('CartService', () => {
  let service: CartService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CartService);
    httpMock = TestBed.inject(HttpTestingController);

    // The constructor hydrates the badge count via an initial GET; flush it as empty so each
    // test starts from a known baseline.
    httpMock.expectOne('/api/cart').flush({ items: [], total: 0 });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should hydrate cartItemCount from the constructor GET /api/cart call', () => {
    // `beforeEach` already instantiated the module (and flushed its own /api/cart request), so a
    // fresh module is needed here to observe the constructor's GET with a *populated* cart.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    const freshService = TestBed.inject(CartService);
    const freshHttpMock = TestBed.inject(HttpTestingController);

    freshHttpMock.expectOne('/api/cart').flush(mockCart);

    expect(freshService.cartItemCount()).toBe(2);

    freshHttpMock.verify();
  });

  it('getCart should GET the cart and update cart/cartItemCount signals', () => {
    let result: Cart | undefined;

    service.getCart().subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/cart');

    expect(req.request.method).toBe('GET');
    req.flush(mockCart);

    expect(result).toEqual(mockCart);
    expect(service.cart()).toEqual(mockCart);
    expect(service.cartItemCount()).toBe(2);
  });

  it('addItem should POST the sku/quantity and update cart state', () => {
    let result: Cart | undefined;

    service.addItem('RS-001', 2).subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/cart/items');

    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ product_sku: 'RS-001', quantity: 2 });
    req.flush(mockCart);

    expect(result).toEqual(mockCart);
    expect(service.cartItemCount()).toBe(2);
  });

  it('updateItemQuantity should PUT the new quantity and update cart state', () => {
    const updatedCart: Cart = {
      ...mockCart,
      items: [{ ...mockCart.items[0], quantity: 3, subtotal: 269.97 }],
      total: 269.97,
    };
    let result: Cart | undefined;

    service.updateItemQuantity('RS-001', 3).subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/cart/items/RS-001');

    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ quantity: 3 });
    req.flush(updatedCart);

    expect(result).toEqual(updatedCart);
    expect(service.cartItemCount()).toBe(3);
  });

  it('removeItem should DELETE the item and update cart state', () => {
    const emptiedCart: Cart = { ...mockCart, items: [], total: 0 };
    let result: Cart | undefined;

    service.removeItem('RS-001').subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/cart/items/RS-001');

    expect(req.request.method).toBe('DELETE');
    req.flush(emptiedCart);

    expect(result).toEqual(emptiedCart);
    expect(service.cartItemCount()).toBe(0);
  });

  it('checkout should POST /api/checkout and reset the local cart state', () => {
    const order: Order = {
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
    let result: Order | undefined;

    // Seed the cart with an item first so we can prove checkout() clears it.
    service.getCart().subscribe();
    httpMock.expectOne('/api/cart').flush(mockCart);

    service.checkout().subscribe((res) => (result = res));

    const req = httpMock.expectOne('/api/checkout');

    expect(req.request.method).toBe('POST');
    req.flush(order);

    expect(result).toEqual(order);
    expect(service.cart()).toEqual({ items: [], total: 0 });
    expect(service.cartItemCount()).toBe(0);
  });
});
