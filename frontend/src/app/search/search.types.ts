/**
 * Search filter state shared between the search page container, the filters panel, and the
 * product query params sent to `ProductService.getProducts()`.
 *
 * This lives in its own file (rather than colocated in `search-filters.ts`, as most other
 * feature interfaces are colocated with their service/component in this codebase) because the
 * `search-filters` component's class name would otherwise collide with this type name.
 */
export interface SearchFilters {
  q?: string;
  category?: string;
  priceMin?: number;
  priceMax?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/** The subset of `SearchFilters` emitted by the filters panel (keyword is emitted separately, debounced). */
export type NonKeywordSearchFilters = Omit<SearchFilters, 'q'>;
