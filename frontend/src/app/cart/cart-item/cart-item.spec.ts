import { TestBed } from '@angular/core/testing';
import { CartItemRow } from './cart-item';
import type { QuantityChangeEvent } from './cart-item';
import { findButtonByText } from '../../shared/testing/dom-test-utils';
import type { CartItem } from '../cart.service';

const mockItem: CartItem = {
  product_sku: 'RS-001',
  name: 'Running Shoes',
  quantity: 2,
  unit_price_snapshot: 89.99,
  subtotal: 179.98,
};

describe('CartItemRow', () => {
  function setup(item: CartItem = mockItem, stockError: string | null = null) {
    const fixture = TestBed.createComponent(CartItemRow);

    fixture.componentRef.setInput('item', item);
    fixture.componentRef.setInput('stockError', stockError);
    fixture.detectChanges();

    return fixture;
  }

  it('should create', () => {
    const fixture = setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render sku, name, unit price, quantity, and subtotal', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('RS-001');
    expect(compiled.textContent).toContain('Running Shoes');
    expect(compiled.textContent).toContain('89.99');
    expect(compiled.textContent).toContain('179.98');
    expect((compiled.querySelector('input') as HTMLInputElement).value).toBe('2');
  });

  it('should not render a stock error by default', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')).toBeNull();
  });

  it('should render an inline stock error when provided', () => {
    const fixture = setup(mockItem, 'Only 1 unit available');
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain(
      'Only 1 unit available',
    );
  });

  it('should emit quantityChange with quantity + 1 when the increment button is clicked', () => {
    const fixture = setup();
    const emitted: QuantityChangeEvent[] = [];

    fixture.componentInstance.quantityChange.subscribe((event) => emitted.push(event));
    findButtonByText(fixture.nativeElement as HTMLElement, '+')?.click();

    expect(emitted).toEqual([{ sku: 'RS-001', quantity: 3 }]);
  });

  it('should emit quantityChange with quantity - 1 when the decrement button is clicked', () => {
    const fixture = setup();
    const emitted: QuantityChangeEvent[] = [];

    fixture.componentInstance.quantityChange.subscribe((event) => emitted.push(event));
    findButtonByText(fixture.nativeElement as HTMLElement, '−')?.click();

    expect(emitted).toEqual([{ sku: 'RS-001', quantity: 1 }]);
  });

  it('should not emit quantityChange when decrementing below 1', () => {
    const fixture = setup({ ...mockItem, quantity: 1 });
    const compiled = fixture.nativeElement as HTMLElement;

    expect(findButtonByText(compiled, '−')?.disabled).toBe(true);
  });

  it('should emit quantityChange when a valid positive integer is typed into the input', () => {
    const fixture = setup();
    const emitted: QuantityChangeEvent[] = [];

    fixture.componentInstance.quantityChange.subscribe((event) => emitted.push(event));
    const input = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;

    input.value = '5';
    input.dispatchEvent(new Event('change'));

    expect(emitted).toEqual([{ sku: 'RS-001', quantity: 5 }]);
  });

  it('should not emit quantityChange when the input value is zero or not an integer', () => {
    const fixture = setup();
    const emitted: QuantityChangeEvent[] = [];

    fixture.componentInstance.quantityChange.subscribe((event) => emitted.push(event));
    const input = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;

    input.value = '0';
    input.dispatchEvent(new Event('change'));
    input.value = '1.5';
    input.dispatchEvent(new Event('change'));

    expect(emitted).toEqual([]);
  });

  it('should emit remove with the sku when the Remove button is clicked', () => {
    const fixture = setup();
    const emitted: string[] = [];

    fixture.componentInstance.remove.subscribe((sku) => emitted.push(sku));
    findButtonByText(fixture.nativeElement as HTMLElement, 'Remove')?.click();

    expect(emitted).toEqual(['RS-001']);
  });

  it('should disable controls when updating is true', () => {
    const fixture = TestBed.createComponent(CartItemRow);

    fixture.componentRef.setInput('item', mockItem);
    fixture.componentRef.setInput('updating', true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(findButtonByText(compiled, '+')?.disabled).toBe(true);
    expect(findButtonByText(compiled, 'Remove')?.disabled).toBe(true);
  });
});
