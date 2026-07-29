import { TestBed } from '@angular/core/testing';
import { SearchFiltersPanel } from './search-filters';
import type { NonKeywordSearchFilters } from '../search.types';

function setInputValue(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function setSelectValue(select: HTMLSelectElement, value: string): void {
  select.value = value;
  select.dispatchEvent(new Event('change'));
}

describe('SearchFiltersPanel', () => {
  function setup(categories: string[] = ['Footwear', 'Electronics']) {
    const fixture = TestBed.createComponent(SearchFiltersPanel);

    fixture.componentRef.setInput('categories', categories);
    fixture.detectChanges();

    return fixture;
  }

  it('should create', () => {
    const fixture = setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should associate every filter control with a label', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const ids = ['keyword', 'category', 'price-min', 'price-max', 'sort-by'];

    for (const id of ids) {
      expect(compiled.querySelector(`label[for="${id}"]`)).not.toBeNull();
    }
  });

  it('should render an option for each category', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const options = Array.from(compiled.querySelectorAll('#category option')).map((option) =>
      option.textContent?.trim(),
    );

    expect(options).toEqual(['All categories', 'Footwear', 'Electronics']);
  });

  it('should emit keywordChange on every keystroke', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const emitted: string[] = [];

    fixture.componentInstance.keywordChange.subscribe((value) => emitted.push(value));
    setInputValue(compiled.querySelector('#keyword') as HTMLInputElement, 'shoes');

    expect(emitted).toEqual(['shoes']);
  });

  it('should emit filtersChange with the selected category', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const emitted: NonKeywordSearchFilters[] = [];

    fixture.componentInstance.filtersChange.subscribe((value) => emitted.push(value));
    setSelectValue(compiled.querySelector('#category') as HTMLSelectElement, 'Footwear');

    expect(emitted).toEqual([
      {
        category: 'Footwear',
        priceMin: undefined,
        priceMax: undefined,
        sortBy: 'name',
        sortOrder: 'asc',
      },
    ]);
  });

  it('should emit filtersChange with a valid price range', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const emitted: NonKeywordSearchFilters[] = [];

    fixture.componentInstance.filtersChange.subscribe((value) => emitted.push(value));
    setInputValue(compiled.querySelector('#price-min') as HTMLInputElement, '10');
    setInputValue(compiled.querySelector('#price-max') as HTMLInputElement, '100');

    expect(emitted.at(-1)).toEqual({
      category: undefined,
      priceMin: 10,
      priceMax: 100,
      sortBy: 'name',
      sortOrder: 'asc',
    });
  });

  it('should not emit a new filtersChange and should show an inline error when min exceeds max', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const emitted: NonKeywordSearchFilters[] = [];

    // Max alone is a valid range (no min to compare against yet), so it emits once.
    setInputValue(compiled.querySelector('#price-max') as HTMLInputElement, '10');
    fixture.componentInstance.filtersChange.subscribe((value) => emitted.push(value));

    // Min now exceeds max: invalid, must not emit a further change.
    setInputValue(compiled.querySelector('#price-min') as HTMLInputElement, '100');
    fixture.detectChanges();

    expect(emitted).toEqual([]);
    expect(compiled.textContent).toContain('Minimum price must not be greater than maximum price.');
  });

  it('should emit filtersChange with the selected sortBy field', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const emitted: NonKeywordSearchFilters[] = [];

    fixture.componentInstance.filtersChange.subscribe((value) => emitted.push(value));
    setSelectValue(compiled.querySelector('#sort-by') as HTMLSelectElement, 'price');

    expect(emitted.at(-1)?.sortBy).toBe('price');
  });

  it('should toggle sortOrder and emit filtersChange with the new order', () => {
    const fixture = setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const emitted: NonKeywordSearchFilters[] = [];

    fixture.componentInstance.filtersChange.subscribe((value) => emitted.push(value));
    (compiled.querySelector('#sort-order-toggle') as HTMLButtonElement).click();

    expect(emitted.at(-1)?.sortOrder).toBe('desc');
  });
});
