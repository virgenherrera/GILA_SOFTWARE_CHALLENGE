import { TestBed } from '@angular/core/testing';
import { ProductCard } from './product-card';
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

describe('ProductCard', () => {
  function setup(product: ProductResponse = mockProduct) {
    const fixture = TestBed.createComponent(ProductCard);

    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();

    return fixture;
  }

  it('should create', () => {
    const fixture = setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render product summary fields', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('RS-001');
    expect(compiled.textContent).toContain('Running Shoes');
    expect(compiled.textContent).toContain('Footwear');
    expect(compiled.textContent).toContain('89.99');
    expect(compiled.textContent).toContain('150');
  });

  it('should show an out-of-stock badge when stock is zero', () => {
    const fixture = setup({ ...mockProduct, stock: 0 });
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Out of stock');
  });

  it('should emit view with the sku when the View button is clicked', () => {
    const fixture = setup();
    const emitted: string[] = [];

    fixture.componentInstance.view.subscribe((sku) => emitted.push(sku));
    findButtonByText(fixture.nativeElement as HTMLElement, 'View')?.click();

    expect(emitted).toEqual(['RS-001']);
  });

  it('should emit edit with the sku when the Edit button is clicked', () => {
    const fixture = setup();
    const emitted: string[] = [];

    fixture.componentInstance.edit.subscribe((sku) => emitted.push(sku));
    findButtonByText(fixture.nativeElement as HTMLElement, 'Edit')?.click();

    expect(emitted).toEqual(['RS-001']);
  });

  it('should emit delete with the sku when the Delete button is clicked', () => {
    const fixture = setup();
    const emitted: string[] = [];

    fixture.componentInstance.delete.subscribe((sku) => emitted.push(sku));
    findButtonByText(fixture.nativeElement as HTMLElement, 'Delete')?.click();

    expect(emitted).toEqual(['RS-001']);
  });
});
