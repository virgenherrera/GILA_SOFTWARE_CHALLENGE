> [INDEX](../INDEX.md) / [User Stories](./) / US-013 --- Product Search View

# US-013 --- Product Search View

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP05 --- User Interface](../epics/EP05-user-interface.md) |
| Priority | Must Have |
| Status | Ready |
| Estimation | M |

## 2. Story

**As a** Customer (Shopper),
**I want** a search interface with a keyword input, category filter, price range filter, and sort controls,
**so that** I can find products efficiently.

## 3. Definition of Ready

- [x] Domain entity contract frozen
- [x] Interface or API contract frozen
- [x] Input validation rules enumerated with exact boundaries
- [x] Edge cases identified with boundary behavior defined
- [x] Dependencies identified and resolved or deferred
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] API contract endpoints touched by this story are defined
- [x] Role-gate review completed (PO + Dev Lead + SM readiness review 2026-07-28)

## 4. Acceptance Criteria

- [ ] **AC-013.1: Search bar triggers keyword search**
  - **Given** the search page is loaded
  - **When** the Customer types a keyword (e.g., "shoes") into the search bar and submits
  - **Then** the component sends `GET /api/products?q=shoes` via `product.service.ts`
  - **And** the results grid updates to show only products matching the keyword
  - **And** the keyword is displayed back to the user using Angular's built-in auto-escaping (no raw interpolation)

- [ ] **AC-013.2: Category filter populates from catalog**
  - **Given** the search page is loaded
  - **When** the category dropdown is rendered
  - **Then** it is populated with distinct category values obtained from the product catalog
  - **And** an "All Categories" (or equivalent empty) option is available that applies no category filter
  - **And** selecting a category appends `&category=Footwear` to the API query

- [ ] **AC-013.3: Price range filter validates with Zod**
  - **Given** the search page is loaded
  - **When** the Customer enters values into the min and max price inputs
  - **Then** the values are validated using a Zod schema that enforces non-negative decimals
  - **And** valid values append `&priceMin=10&priceMax=100` to the API query
  - **And** invalid values (negative numbers, non-numeric strings) show an inline validation error and do not trigger a request

- [ ] **AC-013.4: Sort controls work for all sort fields and orders**
  - **Given** the search page is loaded with products displayed
  - **When** the Customer selects a sort field (`name`, `price`, or `stock`) and a sort order (`asc` or `desc`)
  - **Then** the API query includes `&sortBy=price&sortOrder=desc` (or the selected combination)
  - **And** the results grid re-renders in the selected order

- [ ] **AC-013.5: Filters are cumulative**
  - **Given** the Customer has entered a keyword, selected a category, and set a price range
  - **When** the search executes
  - **Then** the API request includes all active filters combined: `?q=shoes&category=Footwear&priceMin=10&priceMax=100`
  - **And** the results reflect only products matching ALL active criteria simultaneously

- [ ] **AC-013.6: Results grid shows product details**
  - **Given** the search API returns matching products
  - **When** the results are rendered
  - **Then** each product result displays: `name`, `price`, `stock`, and `category`
  - **And** all text content is rendered through Angular template binding (auto-escaped)

- [ ] **AC-013.7: Pagination controls use paging envelope**
  - **Given** the search results span multiple pages
  - **When** the paging envelope contains `prev` and/or `next` URLs
  - **Then** Previous and Next pagination controls are displayed accordingly
  - **And** clicking Next navigates to the URL provided in `paging.next`
  - **And** clicking Previous navigates to the URL provided in `paging.prev`
  - **And** when `paging.prev` is `null`, the Previous button is disabled
  - **And** when `paging.next` is `null`, the Next button is disabled

- [ ] **AC-013.8: "No results" state shown when search matches nothing**
  - **Given** the Customer performs a search or applies filters
  - **When** the API returns `items: []` with `paging.total: 0`
  - **Then** a clear message such as "No products found" is displayed
  - **And** no error state is triggered (this is a valid empty result, not an error)
  - **And** the filters remain visible so the Customer can adjust their criteria

- [ ] **AC-013.9: Empty search shows full catalog**
  - **Given** the search page is loaded
  - **When** the search bar is empty and no filters are applied
  - **Then** the component sends `GET /api/products` with no `q` parameter
  - **And** the full product catalog is displayed (paginated)

- [ ] **AC-013.10: Invalid price range prevented by Zod validation**
  - **Given** the Customer enters a non-numeric value (e.g., "abc", "$10") in the price min or max input
  - **When** the Zod schema validates the input
  - **Then** the validation fails and an inline error message is shown next to the invalid field
  - **And** no API request is sent until the input is corrected

- [ ] **AC-013.11: XSS in search query safely encoded**
  - **Given** the Customer enters a search query containing HTML/script tags (e.g., `<script>alert('xss')</script>`)
  - **When** the query is displayed back to the user (e.g., "Results for: ...")
  - **Then** the query text is rendered safely via Angular's built-in auto-escaping
  - **And** no script execution or HTML injection occurs

- [ ] **AC-013.12: Loading state while search executes**
  - **Given** the Customer triggers a search or filter change
  - **When** the `resource()` API call is in flight
  - **Then** a loading indicator is visible in the results area
  - **And** the loading state is driven by the `resource()` loading signal
  - **And** the loading indicator disappears when results arrive or an error occurs

- [ ] **AC-013.13: "Add to Cart" button on each result item**
  - **Given** the search results are displayed
  - **When** the Customer views a product in the results grid
  - **Then** each product result includes an "Add to Cart" button
  - **And** clicking the button initiates the add-to-cart flow (linked to cart functionality in US-014)

- [ ] **AC-013.14: Product click navigates to detail view**
  - **Given** the search results are displayed
  - **When** the Customer clicks on a product name or product card (not the "Add to Cart" button)
  - **Then** the router navigates to the product detail view for that product's SKU

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Unit tests green for container and presentational components
- [ ] Integration tests green for service-to-API interaction
- [ ] No regressions in existing test suite
- [ ] Error responses conform to agreed shape
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `frontend/src/app/search/search-page/search-page.component.ts` | Container component; orchestrates search state, API calls via `product.service.ts` using `resource()`, passes data down to presentational children |
| `frontend/src/app/search/search-page/search-page.component.html` | External template for search page container; layout with filters, results, pagination |
| `frontend/src/app/search/search-page/search-page.component.css` | Styles for search page layout |
| `frontend/src/app/search/search-page/search-page.component.spec.ts` | Unit tests for container component |
| `frontend/src/app/search/search-filters/search-filters.component.ts` | Presentational component; keyword input, category dropdown, price range inputs (Zod-validated), sort controls; emits filter change events |
| `frontend/src/app/search/search-filters/search-filters.component.html` | External template for search filters |
| `frontend/src/app/search/search-filters/search-filters.component.css` | Styles for search filters |
| `frontend/src/app/search/search-filters/search-filters.component.spec.ts` | Unit tests for filter component (Zod validation, event emission) |
| `frontend/src/app/search/search-results/search-results.component.ts` | Presentational component; receives product list via input signal, renders grid/list with product details, "Add to Cart" button, and product click navigation |
| `frontend/src/app/search/search-results/search-results.component.html` | External template for search results |
| `frontend/src/app/search/search-results/search-results.component.css` | Styles for search results |
| `frontend/src/app/search/search-results/search-results.component.spec.ts` | Unit tests for results component (rendering, button clicks, navigation) |
| `frontend/src/app/search/search.routes.ts` | Route configuration for search feature; lazy-loaded |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `frontend/src/app/app.routes.ts` | Add lazy-loaded route for `/search` pointing to `search.routes.ts` |

**Note:** Reuses `product.service.ts` from US-011 (same `GET /api/products` endpoint with query parameters).

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `search-bar-sends-keyword-query` | AC-013.1 | Typing "shoes" and submitting sends `GET /api/products?q=shoes` |
| `search-query-displayed-safely` | AC-013.1, AC-013.11 | Query `<script>alert('x')</script>` is rendered as escaped text, not executed |
| `category-dropdown-populated-from-catalog` | AC-013.2 | Dropdown contains distinct categories from API response |
| `category-filter-appends-query-param` | AC-013.2 | Selecting "Footwear" appends `&category=Footwear` to API call |
| `price-min-accepts-valid-decimal` | AC-013.3 | Entering `10.50` passes Zod validation and is included in query |
| `price-min-rejects-negative` | AC-013.3, AC-013.10 | Entering `-5` shows inline error, no API call triggered |
| `price-max-rejects-non-numeric` | AC-013.3, AC-013.10 | Entering `"abc"` shows inline error, no API call triggered |
| `price-rejects-currency-symbol` | AC-013.3, AC-013.10 | Entering `"$10"` shows inline error |
| `sort-by-price-desc` | AC-013.4 | Selecting price/desc appends `&sortBy=price&sortOrder=desc` |
| `sort-by-name-asc` | AC-013.4 | Selecting name/asc appends `&sortBy=name&sortOrder=asc` |
| `sort-by-stock` | AC-013.4 | Selecting stock appends `&sortBy=stock` |
| `filters-are-cumulative` | AC-013.5 | Keyword + category + price range all appear in single API request |
| `result-card-shows-name-price-stock-category` | AC-013.6 | Each rendered result displays all four fields |
| `pagination-next-enabled-when-available` | AC-013.7 | When `paging.next` is non-null, Next button is enabled and navigates to that URL |
| `pagination-prev-disabled-on-first-page` | AC-013.7 | When `paging.prev` is `null`, Previous button is disabled |
| `pagination-prev-enabled-on-later-pages` | AC-013.7 | When `paging.prev` is non-null, Previous button is enabled |
| `no-results-shows-message` | AC-013.8 | When API returns empty items array, "No products found" message is displayed |
| `no-results-is-not-error` | AC-013.8 | Empty result does not trigger error UI or error styling |
| `empty-search-shows-all-products` | AC-013.9 | When search bar is empty, API call has no `q` parameter; full catalog rendered |
| `loading-indicator-shown-during-fetch` | AC-013.12 | While `resource()` loading signal is true, loading indicator is visible |
| `loading-indicator-hidden-after-response` | AC-013.12 | After API response arrives, loading indicator is hidden |
| `add-to-cart-button-present-on-each-result` | AC-013.13 | Every product card has an "Add to Cart" button |
| `add-to-cart-button-emits-event` | AC-013.13 | Clicking "Add to Cart" emits the expected add-to-cart event |
| `product-click-navigates-to-detail` | AC-013.14 | Clicking a product name/card triggers navigation to `/products/:sku` |
| `xss-script-tag-not-executed` | AC-013.11 | Search query with `<script>` tag is rendered as text, no DOM script injection |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `priceMin` | no | decimal | `>= 0`; non-negative decimal | `-5`, `"abc"`, `"$10"` |
| `priceMax` | no | decimal | `>= 0`; non-negative decimal; must be >= priceMin when both present | `-1`, `"free"`, `"$99"` |
| `q` (keyword) | no | string | Free text; sanitized by Angular auto-escaping on display | _(no rejection; any string is a valid search query)_ |
| `sortBy` | no | enum | `name`, `price`, or `stock` | `"date"`, `"rating"` |
| `sortOrder` | no | enum | `asc` or `desc` | `"ascending"`, `"1"` |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| MEDIUM | Category list may grow large if catalog has many distinct categories, causing dropdown usability issues | Limit initial implementation to a simple dropdown; if the list exceeds usability thresholds, consider a searchable select in a future iteration |
| MEDIUM | Rapid filter changes can cause race conditions with multiple in-flight API requests | Use `resource()` signal which cancels stale requests automatically; only the latest response updates the UI |
| LOW | Price range Zod schema may not catch all edge cases (e.g., `priceMin > priceMax`) | Add a cross-field Zod refinement that validates `priceMin <= priceMax` when both values are present |
| LOW | Pagination URLs from the paging envelope may not preserve all active filters | Backend guarantees `prev`/`next` URLs include all active query parameters per the API contract |

## 10. Out of Scope

- Search suggestions or autocomplete as the user types
- Session-remembered filters (filters reset on page reload)
- Faceted search with result counts per facet
- Advanced query syntax (boolean operators, field-specific queries)
- Responsive design (mobile/tablet layout adaptation)
- Product image display in search results
- Infinite scroll as an alternative to pagination

## 11. Notes

- This story reuses `product.service.ts` created in US-011 for the product management views. The same `GET /api/products` endpoint powers both the admin product list and the customer search view, with the search/filter query parameters being the differentiator.
- All components are standalone (no `NgModule`), following Angular 22 conventions.
- The container-presentational split is deliberate: `search-page` owns the state and API interactions; `search-filters` and `search-results` are pure presentational components that receive data via input signals and emit events via output signals.
- The `resource()` API from Angular 22 provides built-in loading/error signal tracking, eliminating the need for manual loading state management.
- Category list population strategy: the category dropdown is populated via the dedicated `GET /api/products/categories` endpoint being added to [API Contract](../architecture/api-contract.md#3-products-api), which returns the distinct, non-empty `category` values currently in use across the catalog. This avoids fetching the full product list solely to derive category options.

## 12. Related Documents

- [EP05 --- User Interface](../epics/EP05-user-interface.md) --- parent epic
- [API Contract --- Products API](../architecture/api-contract.md) --- `GET /api/products` with search/filter query parameters
- [Tech Stack](../architecture/tech-stack.md) --- Angular 22, Zod, standalone components
- [Testing Strategy](../architecture/testing-strategy.md) --- frontend unit testing approach
- [US-001 --- Project Scaffolding](./US-001-project-scaffolding.md) --- frontend scaffold dependency
- [US-008 --- Product Search with Filters, Sort & Pagination](./US-008-product-search.md) --- backend search endpoint dependency
- [US-011 --- Product Management Views](./US-011-product-management-views.md) --- `product.service.ts` reuse
- [US-014 --- Cart & Checkout Views](./US-014-cart-checkout-views.md) --- "Add to Cart" integration

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
