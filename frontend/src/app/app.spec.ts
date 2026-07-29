import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);

    httpMock.expectOne('/api/cart').flush({ items: [], total: 0 });

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);

    httpMock.expectOne('/api/cart').flush({ items: [], total: 0 });
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toContain('E-Commerce');
  });

  it('should render a link to the products page', async () => {
    const fixture = TestBed.createComponent(App);

    httpMock.expectOne('/api/cart').flush({ items: [], total: 0 });
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('a[href="/products"]');

    expect(link?.textContent).toContain('Products');
  });

  it('should render the router outlet', async () => {
    const fixture = TestBed.createComponent(App);

    httpMock.expectOne('/api/cart').flush({ items: [], total: 0 });
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should render a cart link without a badge when the cart is empty', async () => {
    const fixture = TestBed.createComponent(App);

    httpMock.expectOne('/api/cart').flush({ items: [], total: 0 });
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('a[href="/cart"]');

    expect(link?.textContent).toContain('Cart');
    expect(link?.querySelector('span')).toBeNull();
  });

  it('should render the cart badge with the item count once the cart loads', async () => {
    const fixture = TestBed.createComponent(App);

    httpMock.expectOne('/api/cart').flush({
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
    });
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const badge = compiled.querySelector('a[href="/cart"] span');

    expect(badge?.textContent?.trim()).toBe('2');
  });
});
