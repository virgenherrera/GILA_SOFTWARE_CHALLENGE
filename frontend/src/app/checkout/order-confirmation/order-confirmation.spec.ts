import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OrderConfirmation } from './order-confirmation';
import type { Order } from '../../shared/validation/checkout.schema';

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

describe('OrderConfirmation', () => {
  let httpMock: HttpTestingController;

  function setup(id = 'order-1') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id })) } },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);

    return TestBed.createComponent(OrderConfirmation);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/orders/order-1').flush(mockOrder);
    await fixture.whenStable();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show a loading state before the order resolves', () => {
    const fixture = setup();

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="status"]')?.textContent).toContain('Loading order');
    httpMock.expectOne('/api/orders/order-1').flush(mockOrder);
  });

  it('should render order id, status, items, and total once loaded', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/orders/order-1').flush(mockOrder);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('order-1');
    expect(compiled.textContent).toContain('Paid');
    expect(compiled.textContent).toContain('Running Shoes');
    expect(compiled.textContent).toContain('RS-001');
    expect(compiled.textContent).toContain('179.98');
  });

  it('should render an Order Confirmation heading', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/orders/order-1').flush(mockOrder);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h2')?.textContent).toContain('Order Confirmation');
  });

  it('should render the order items table with an accessible caption and column scope', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/orders/order-1').flush(mockOrder);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const table = compiled.querySelector('table');
    const caption = table?.querySelector('caption');
    const headers = Array.from(table?.querySelectorAll('th') ?? []);

    expect(caption?.textContent).toContain('Order items');
    expect(headers.length).toBeGreaterThan(0);
    expect(headers.every((th) => th.getAttribute('scope') === 'col')).toBe(true);
  });

  it('should show a not-found state on 404', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/orders/order-1')
      .flush(
        { error: { code: 'NOT_FOUND', message: 'Order not found' } },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Order not found.');
    expect(compiled.textContent).not.toContain('Server exploded');
  });

  it('should show an error state on other failures', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock
      .expectOne('/api/orders/order-1')
      .flush(
        { error: { code: 'INTERNAL_ERROR', message: 'Server exploded' } },
        { status: 500, statusText: 'Error' },
      );
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Server exploded');
    expect(compiled.textContent).not.toContain('Order not found.');
  });

  it('should render a link back to products', async () => {
    const fixture = setup();

    fixture.detectChanges();
    httpMock.expectOne('/api/orders/order-1').flush(mockOrder);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('a[href="/products"]');

    expect(link?.textContent).toContain('Back to Products');
  });
});
