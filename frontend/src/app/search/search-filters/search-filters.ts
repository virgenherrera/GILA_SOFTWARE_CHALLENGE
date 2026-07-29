import { Component, computed, input, output, signal } from '@angular/core';
import type { NonKeywordSearchFilters } from '../search.types';

const SORT_FIELDS = ['name', 'price', 'stock'] as const;

export type SortField = (typeof SORT_FIELDS)[number];

@Component({
  selector: 'app-search-filters',
  templateUrl: './search-filters.html',
  styleUrl: './search-filters.css',
})
export class SearchFiltersPanel {
  readonly categories = input<string[]>([]);

  readonly keywordChange = output<string>();
  readonly filtersChange = output<NonKeywordSearchFilters>();

  protected readonly sortFields = SORT_FIELDS;

  protected readonly keyword = signal('');
  protected readonly category = signal('');
  protected readonly priceMin = signal<number | null>(null);
  protected readonly priceMax = signal<number | null>(null);
  protected readonly sortBy = signal<SortField>('name');
  protected readonly sortOrder = signal<'asc' | 'desc'>('asc');

  protected readonly priceRangeInvalid = computed(() => {
    const min = this.priceMin();
    const max = this.priceMax();

    return min !== null && max !== null && min > max;
  });

  protected onKeywordInput(value: string): void {
    this.keyword.set(value);
    this.keywordChange.emit(value);
  }

  protected onCategoryChange(value: string): void {
    this.category.set(value);
    this.emitFiltersIfValid();
  }

  protected onPriceMinInput(value: string): void {
    this.priceMin.set(value === '' ? null : Number(value));
    this.emitFiltersIfValid();
  }

  protected onPriceMaxInput(value: string): void {
    this.priceMax.set(value === '' ? null : Number(value));
    this.emitFiltersIfValid();
  }

  protected onSortByChange(value: string): void {
    this.sortBy.set(value as SortField);
    this.emitFiltersIfValid();
  }

  protected onToggleSortOrder(): void {
    this.sortOrder.update((current) => (current === 'asc' ? 'desc' : 'asc'));
    this.emitFiltersIfValid();
  }

  private emitFiltersIfValid(): void {
    if (this.priceRangeInvalid()) {
      return;
    }

    this.filtersChange.emit({
      category: this.category() || undefined,
      priceMin: this.priceMin() ?? undefined,
      priceMax: this.priceMax() ?? undefined,
      sortBy: this.sortBy(),
      sortOrder: this.sortOrder(),
    });
  }
}
