import { TestBed } from '@angular/core/testing';
import { SearchResults } from './search-results';
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

describe('SearchResults', () => {
  function setup(products: ProductResponse[] = [mockProduct]) {
    const fixture = TestBed.createComponent(SearchResults);

    fixture.componentRef.setInput('products', products);
    fixture.detectChanges();

    return fixture;
  }

  it('should create', () => {
    const fixture = setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render a card for each product', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('RS-001');
    expect(compiled.textContent).toContain('Running Shoes');
    expect(compiled.textContent).toContain('Footwear');
    expect(compiled.textContent).toContain('89.99');
    expect(compiled.textContent).toContain('150');
  });

  it('should show an out-of-stock badge when stock is zero', () => {
    const fixture = setup([{ ...mockProduct, stock: 0 }]);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Out of stock');
  });

  it('should show the empty state when there are no products', () => {
    const fixture = setup([]);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('No products found');
  });

  it('should emit view with the sku when View is clicked', () => {
    const fixture = setup();
    const emitted: string[] = [];

    fixture.componentInstance.view.subscribe((sku) => emitted.push(sku));
    findButtonByText(fixture.nativeElement as HTMLElement, 'View')?.click();

    expect(emitted).toEqual(['RS-001']);
  });

  it('should emit addToCart with the sku when Add to Cart is clicked', () => {
    const fixture = setup();
    const emitted: string[] = [];

    fixture.componentInstance.addToCart.subscribe((sku) => emitted.push(sku));
    findButtonByText(fixture.nativeElement as HTMLElement, 'Add to Cart')?.click();

    expect(emitted).toEqual(['RS-001']);
  });

  it('should not use innerHTML to render product fields (XSS safety)', () => {
    const xssProduct: ProductResponse = {
      ...mockProduct,
      name: '<img src=x onerror="window.__xss=true">',
    };
    const fixture = setup([xssProduct]);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('img')).toBeNull();
    expect(compiled.textContent).toContain('<img src=x onerror="window.__xss=true">');
  });
});
