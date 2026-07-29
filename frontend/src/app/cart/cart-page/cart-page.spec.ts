import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { CartPage } from './cart-page';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
import type { Cart } from '../../shared/validation/cart.schema';
import type { Order } from '../../shared/validation/checkout.schema';

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

const emptyCart: Cart = { items: [], total: 0 };

describe('CartPage', () => {
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

  /**
   * Creating `CartPage` triggers two GET /api/cart requests: one from `CartService`'s own
   * constructor (fired the first time the root-provided service is resolved) and one from
   * `CartPage.loadCart()`. Both are flushed with the same response here.
   */
  function createAndFlush(cart: Cart = mockCart) {
    const fixture = TestBed.createComponent(CartPage);

    fixture.detectChanges();
    httpMock.match('/api/cart').forEach((req) => req.flush(cart));
    fixture.detectChanges();

    return fixture;
  }

  it('should create', () => {
    const fixture = createAndFlush();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render cart items and the grand total', () => {
    const fixture = createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('RS-001');
    expect(compiled.textContent).toContain('Running Shoes');
    expect(compiled.textContent).toContain('89.99');
    expect(compiled.textContent).toContain('179.98');
  });

  it('should show the empty cart state when there are no items', () => {
    const fixture = createAndFlush(emptyCart);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Your cart is empty');
  });

  it('should show a loading state before the cart resolves', () => {
    const fixture = TestBed.createComponent(CartPage);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="status"]')?.textContent).toContain('Loading cart');
    httpMock.match('/api/cart').forEach((req) => req.flush(emptyCart));
  });

  it('should show an error state when the cart fails to load', () => {
    const fixture = TestBed.createComponent(CartPage);

    fixture.detectChanges();

    const reqs = httpMock.match('/api/cart');

    reqs[0].flush(emptyCart);
    reqs[1].flush(
      { error: { code: 'INTERNAL_ERROR', message: 'Something went wrong' } },
      { status: 500, statusText: 'Internal Server Error' },
    );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Something went wrong');
  });

  it('should update the item quantity via PUT when the increment button is clicked', () => {
    const fixture = createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, '+')?.click();

    const req = httpMock.expectOne((r) => r.url === '/api/cart/items/RS-001' && r.method === 'PUT');

    expect(req.request.body).toEqual({ quantity: 3 });
    req.flush({
      ...mockCart,
      items: [{ ...mockCart.items[0], quantity: 3, subtotal: 269.97 }],
      total: 269.97,
    });
  });

  it('should show an inline stock error on 409 when updating quantity', () => {
    const fixture = createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, '+')?.click();

    const req = httpMock.expectOne((r) => r.url === '/api/cart/items/RS-001' && r.method === 'PUT');

    req.flush(
      {
        error: {
          code: 'INSUFFICIENT_STOCK',
          message: 'Insufficient stock',
          details: [{ field: 'quantity', reason: 'Only 2 units available' }],
        },
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Only 2 units available');
  });

  it('should remove the item via DELETE when Remove is clicked', () => {
    const fixture = createAndFlush();

    findButtonByText(fixture.nativeElement as HTMLElement, 'Remove')?.click();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/cart/items/RS-001' && r.method === 'DELETE',
    );

    req.flush(emptyCart);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Your cart is empty');
  });

  it('should checkout and navigate to the order confirmation on success', () => {
    const fixture = createAndFlush();

    vi.useFakeTimers();
    findButtonByText(fixture.nativeElement as HTMLElement, 'Proceed to Checkout')?.click();
    vi.advanceTimersByTime(1500);
    vi.useRealTimers();

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

    httpMock.expectOne((r) => r.url === '/api/checkout' && r.method === 'POST').flush(order);

    expect(navigateSpy).toHaveBeenCalledWith(['/checkout/confirmation', 'order-1']);
  });

  it('should render the cart table with an accessible caption and column scope', () => {
    const fixture = createAndFlush();
    const compiled = fixture.nativeElement as HTMLElement;
    const table = compiled.querySelector('table');
    const caption = table?.querySelector('caption');
    const headers = Array.from(table?.querySelectorAll('th') ?? []);

    expect(caption?.textContent).toContain('Shopping cart items');
    expect(headers.length).toBeGreaterThan(0);
    expect(headers.every((th) => th.getAttribute('scope') === 'col')).toBe(true);
  });

  it('should show which items have stock issues on checkout 409', () => {
    const fixture = createAndFlush();

    vi.useFakeTimers();
    findButtonByText(fixture.nativeElement as HTMLElement, 'Proceed to Checkout')?.click();
    vi.advanceTimersByTime(1500);
    vi.useRealTimers();

    httpMock
      .expectOne((r) => r.url === '/api/checkout' && r.method === 'POST')
      .flush(
        {
          error: {
            code: 'INSUFFICIENT_STOCK',
            message: 'Insufficient stock',
            details: [{ field: 'RS-001', reason: 'Requested 10 but only 5 available' }],
          },
        },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('stock issues');
    expect(compiled.textContent).toContain('Requested 10 but only 5 available');
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
