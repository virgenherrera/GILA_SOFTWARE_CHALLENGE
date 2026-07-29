import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CheckoutPage } from './checkout-page';

describe('CheckoutPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CheckoutPage);

    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render a Checkout heading', () => {
    const fixture = TestBed.createComponent(CheckoutPage);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h2')?.textContent).toContain('Checkout');
  });

  it('should render a link back to the cart', () => {
    const fixture = TestBed.createComponent(CheckoutPage);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('a[href="/cart"]');

    expect(link?.textContent).toContain('Go to Cart');
  });
});
