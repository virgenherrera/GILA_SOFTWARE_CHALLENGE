> [INDEX](../INDEX.md) / [User Stories](./) / US-008 --- Product Search with Filters, Sort & Pagination

# US-008 --- Product Search with Filters, Sort & Pagination

## 1. Metadata

| Field    | Value                                              |
| -------- | -------------------------------------------------- |
| Epic     | EP03 --- Product Search (also serves EP01 listing) |
| Priority | Must Have                                          |
| Status | Ready                                              |
| Estimation | M                                                |

## 2. Story

**As a** Customer,
**I want to** search products by keyword, filter by category and price range, sort results, and browse paginated pages,
**so that** I can find products efficiently.

This story MERGES the original US-002 (Read & List Products) because `GET /api/products` serves both listing and search via query parameters.

## 3. Definition of Ready

- [x] Story follows the INVEST criteria
- [x] Acceptance criteria are testable and unambiguous
- [x] Dependencies identified (US-001 scaffolding, US-002 products must exist to search)
- [x] API contract reviewed ([API Contract](../architecture/api-contract.md) section 3)
- [x] Data model reviewed ([Data Model](../architecture/data-model.md) sections 2.1, 4)
- [x] Full-text search implementation reviewed (tsvector/tsquery, plainto_tsquery, ts_rank)
- [x] Paging envelope contract reviewed ([API Contract](../architecture/api-contract.md) section 1)
- [x] Domain glossary terms aligned (Product, CategoryLabel, Price, SKU)
- [x] Sort vs relevance precedence rules defined
- [x] Out of scope items explicitly listed
- [x] Test plan covers every acceptance criterion
- [x] Role-gate review completed (PO + Dev Lead + SM readiness review 2026-07-28)

## 4. Acceptance Criteria

### AC-008.1: Default listing with no params returns paginated products

**Given** the catalog contains one or more products
**When** a Customer sends `GET /api/products` with no query parameters
**Then** the response status is `200 OK`
**And** the response body uses the standard paging envelope with `items`, `paging.page`, `paging.perPage`, `paging.total`, `paging.prev`, `paging.next`
**And** defaults are: `page=1`, `perPage=20`, `sortBy=name`, `sortOrder=asc`
**And** products are sorted by name ascending

### AC-008.2: Get single product by SKU

**Given** a product with SKU `"RS-001"` exists in the catalog
**When** a Customer sends `GET /api/products/RS-001`
**Then** the response status is `200 OK`
**And** the response body contains the full product object (sku, name, description, category, price, stock, weight_kg, created_at, updated_at)

**Given** no product exists with SKU `"NONEXISTENT"`
**When** a Customer sends `GET /api/products/NONEXISTENT`
**Then** the response status is `404 Not Found`
**And** the response body contains `error.code = "NOT_FOUND"`

### AC-008.3: Full-text search with ?q=keyword

**Given** the catalog contains products with names, descriptions, and SKUs
**When** a Customer sends `GET /api/products?q=running`
**Then** the response contains products whose `name`, `description`, or `sku` match the search term via PostgreSQL `tsvector`/`tsquery`
**And** the search uses `plainto_tsquery` (free-form user input, no special operator syntax required)
**And** results are ranked by relevance (`ts_rank` DESC) when no explicit `sortBy` is provided

### AC-008.4: Category filter with ?category=X

**Given** the catalog contains products in categories "Footwear", "Electronics", and uncategorized
**When** a Customer sends `GET /api/products?category=Footwear`
**Then** the response contains only products where `category` exactly matches `"Footwear"` (case-sensitive)
**And** uncategorized products and products in other categories are excluded

### AC-008.5: Price range filter with ?priceMin=X&priceMax=Y

**Given** the catalog contains products with prices ranging from 5.00 to 500.00
**When** a Customer sends `GET /api/products?priceMin=10&priceMax=100`
**Then** the response contains only products where `price >= 10` AND `price <= 100` (inclusive bounds)
**And** products outside this range are excluded

### AC-008.6: Sort by field and direction

**Given** the catalog contains multiple products
**When** a Customer sends `GET /api/products?sortBy=price&sortOrder=desc`
**Then** the response products are sorted by `price` in descending order
**And** valid `sortBy` values are: `name`, `price`, `stock`
**And** valid `sortOrder` values are: `asc`, `desc`

### AC-008.7: Sort vs relevance precedence

**Given** a Customer searches with `?q=shoes`
**When** no explicit `sortBy` is provided
**Then** results are ordered by relevance rank (`ts_rank` DESC)

**Given** a Customer searches with `?q=shoes&sortBy=price`
**When** an explicit `sortBy` is provided alongside `q`
**Then** `sortBy` wins --- results are ordered by price, not relevance

**Given** a Customer sends `GET /api/products` with no `q` and no `sortBy`
**When** no search term is active
**Then** results are ordered by the default sort: `name ASC`

### AC-008.8: Filters are cumulative (AND logic)

**Given** the catalog contains products across categories and price ranges
**When** a Customer sends `GET /api/products?category=Footwear&priceMin=50&priceMax=150`
**Then** the response contains only products matching ALL filters simultaneously (category = Footwear AND price between 50 and 150)
**And** applying a sort never bypasses an active filter

### AC-008.9: Empty catalog returns 200 with empty items

**Given** the catalog contains zero products
**When** a Customer sends `GET /api/products`
**Then** the response status is `200 OK`
**And** the response body has `items: []` and `paging.total: 0`
**And** `paging.prev` and `paging.next` are both `null`

### AC-008.10: Page beyond last returns 200 with empty items

**Given** the catalog contains 5 products and `perPage=20`
**When** a Customer sends `GET /api/products?page=2`
**Then** the response status is `200 OK`
**And** `items` is an empty array
**And** `paging.total` is `5`
**And** `paging.prev` points to page 1
**And** `paging.next` is `null`

### AC-008.11: Invalid page params return 400

**Given** a Customer sends a request with invalid pagination parameters
**When** `page=0`, `page=-1`, `perPage=200` (exceeds max 100), `page=abc`, or `perPage=abc`
**Then** the response status is `400 Bad Request`
**And** the response body contains `error.code = "VALIDATION_ERROR"` with field-level details

### AC-008.12: Malformed price range returns 400

**Given** a Customer sends a request with invalid price range parameters
**When** `priceMin=abc`, `priceMax=abc`, or `priceMin` is greater than `priceMax`
**Then** the response status is `400 Bad Request`
**And** the response body contains `error.code = "VALIDATION_ERROR"` with a field-level reason identifying the problematic parameter

### AC-008.13: Category filter with empty-category products

**Given** the catalog contains products with `category = "Footwear"` and products with `category = ""` or `category = NULL`
**When** a Customer sends `GET /api/products?category=Footwear`
**Then** only products with `category = "Footwear"` are returned
**And** products with empty or null category are excluded

**When** a Customer sends `GET /api/products` (no category filter)
**Then** all products are returned, including those with empty or null category

### AC-008.14: XSS payload in search query is sanitized

**Given** a Customer sends `GET /api/products?q=<script>alert('xss')</script>`
**When** the search is processed
**Then** the query is passed through `plainto_tsquery` which treats it as plain text
**And** the response never contains executable script content
**And** any echoed search term in the response is safely encoded

### AC-008.15: SQLi payload in search query is neutralized

**Given** a Customer sends `GET /api/products?q='; DROP TABLE products; --`
**When** the search is processed
**Then** the query is parameterized via `plainto_tsquery` (HoneySQL parameterized queries)
**And** the `products` table remains intact
**And** the response is either empty results or a valid product list (no SQL execution)

### AC-008.16: Script content in "no results" context is safely encoded

**Given** a Customer searches with a query containing script content and no products match
**When** the empty result is returned
**Then** the response body does not echo the search term as executable content
**And** any error or "no results" messaging is safely encoded

### AC-008.17: Paging envelope preserves all active query params

**Given** a Customer sends `GET /api/products?q=shoes&category=Footwear&priceMin=50&page=1&perPage=5`
**When** the response includes `paging.next`
**Then** the `next` URL preserves all active query parameters: `q`, `category`, `priceMin`, `page`, `perPage`, `sortBy`, `sortOrder`
**And** only `page` changes between `prev` and `next` URLs

### AC-008.18: Error responses never leak internal details

**Given** any error occurs during search or product retrieval
**When** the error response is returned
**Then** the response body never contains stack traces, exception class names, raw SQL fragments, file system paths, or internal hostnames

## 5. Definition of Done

- [ ] `GET /api/products` supports pagination, search, filters, and sort
- [ ] `GET /api/products/:sku` returns single product or 404
- [ ] Full-text search via tsvector/tsquery with plainto_tsquery
- [ ] Category filter (exact match, case-sensitive)
- [ ] Price range filter (inclusive bounds)
- [ ] Sort by name, price, or stock; asc or desc
- [ ] Sort vs relevance precedence implemented correctly
- [ ] Cumulative filters (AND logic)
- [ ] Paging envelope with prev/next preserving all query params
- [ ] Empty catalog and page-beyond-last return valid responses
- [ ] Invalid params return 400 with field-level reasons
- [ ] XSS and SQLi payloads in search query are neutralized
- [ ] No stack traces, SQL, or file paths leak in error responses
- [ ] All acceptance criteria pass automated tests
- [ ] Integration tests run against PostgreSQL with seeded data
- [ ] Code reviewed and merged

## 6. Deliverables

### Files to Create

| File | Purpose |
| ---- | ------- |
| `src/ecommerce/product/search.clj` | Search/filter/sort query builder with HoneySQL; constructs tsvector queries, filter clauses, sort ordering |
| `test/ecommerce/product/search_test.clj` | Unit tests for query builder: filter combinations, sort precedence, parameter validation |
| `test/ecommerce/product/search_integration_test.clj` | Integration tests against seeded PostgreSQL: full-text search, pagination, filters, security payloads |

### Files to Modify

| File | Change |
| ---- | ------ |
| `src/ecommerce/product/handler.clj` | Add/update `GET /api/products` with search/filter/sort params; `GET /api/products/:sku` |
| `src/ecommerce/product/repository.clj` | Add search query functions; integrate HoneySQL query builder from search.clj |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `default-listing-paginated` | AC-008.1 | GET /api/products returns page=1, perPage=20, sortBy=name, sortOrder=asc |
| `default-listing-sorted-by-name-asc` | AC-008.1 | Products are returned in alphabetical order by name |
| `default-listing-paging-envelope` | AC-008.1 | Response has items, paging.page, paging.perPage, paging.total, paging.prev, paging.next |
| `get-product-by-sku-200` | AC-008.2 | GET /api/products/RS-001 returns 200 with full product |
| `get-product-by-sku-404` | AC-008.2 | GET /api/products/NONEXISTENT returns 404 NOT_FOUND |
| `fulltext-search-by-name` | AC-008.3 | ?q=running returns products with "running" in name |
| `fulltext-search-by-description` | AC-008.3 | ?q=lightweight returns products with "lightweight" in description |
| `fulltext-search-by-sku` | AC-008.3 | ?q=RS-001 returns product with matching SKU |
| `fulltext-search-relevance-ranking` | AC-008.3 | Results without explicit sortBy are ranked by ts_rank DESC |
| `category-filter-exact-match` | AC-008.4 | ?category=Footwear returns only Footwear products |
| `category-filter-case-sensitive` | AC-008.4 | ?category=footwear returns nothing if catalog has "Footwear" |
| `category-filter-excludes-other` | AC-008.4 | ?category=Footwear excludes Electronics and uncategorized |
| `price-range-inclusive-bounds` | AC-008.5 | ?priceMin=10&priceMax=100 includes products at exactly 10.00 and 100.00 |
| `price-range-excludes-outside` | AC-008.5 | Products below priceMin or above priceMax are excluded |
| `price-min-only` | AC-008.5 | ?priceMin=50 returns products with price >= 50 (no upper bound) |
| `price-max-only` | AC-008.5 | ?priceMax=100 returns products with price <= 100 (no lower bound) |
| `sort-by-price-desc` | AC-008.6 | ?sortBy=price&sortOrder=desc returns products in price descending order |
| `sort-by-stock-asc` | AC-008.6 | ?sortBy=stock&sortOrder=asc returns products in stock ascending order |
| `sort-by-name-desc` | AC-008.6 | ?sortBy=name&sortOrder=desc returns products in reverse alphabetical order |
| `search-with-no-sort-uses-relevance` | AC-008.7 | ?q=shoes without sortBy uses ts_rank DESC |
| `search-with-explicit-sort-overrides-relevance` | AC-008.7 | ?q=shoes&sortBy=price uses price order, not relevance |
| `no-search-no-sort-defaults-name-asc` | AC-008.7 | No q, no sortBy defaults to name ASC |
| `cumulative-filters-and-logic` | AC-008.8 | ?category=Footwear&priceMin=50&priceMax=150 returns intersection |
| `cumulative-filters-with-sort` | AC-008.8 | Filters + sort both applied simultaneously |
| `cumulative-filters-with-search` | AC-008.8 | ?q=shoes&category=Footwear returns intersection of search and filter |
| `empty-catalog-returns-200` | AC-008.9 | Empty catalog returns 200, items=[], total=0 |
| `empty-catalog-no-prev-next` | AC-008.9 | Empty catalog has prev=null, next=null |
| `page-beyond-last-returns-200` | AC-008.10 | Page 2 of 5-item catalog returns 200, items=[], total=5, prev points to page 1 |
| `invalid-page-zero-returns-400` | AC-008.11 | ?page=0 returns 400 VALIDATION_ERROR |
| `invalid-page-negative-returns-400` | AC-008.11 | ?page=-1 returns 400 VALIDATION_ERROR |
| `invalid-per-page-over-max-returns-400` | AC-008.11 | ?perPage=200 returns 400 VALIDATION_ERROR |
| `invalid-page-non-numeric-returns-400` | AC-008.11 | ?page=abc returns 400 VALIDATION_ERROR |
| `malformed-price-min-returns-400` | AC-008.12 | ?priceMin=abc returns 400 with field-level reason |
| `malformed-price-max-returns-400` | AC-008.12 | ?priceMax=abc returns 400 with field-level reason |
| `price-min-greater-than-max-returns-400` | AC-008.12 | ?priceMin=100&priceMax=50 returns 400 with field-level reason |
| `category-filter-excludes-empty-category` | AC-008.13 | ?category=Footwear excludes products with null/empty category |
| `no-category-filter-includes-empty-category` | AC-008.13 | No category filter returns all products including empty-category ones |
| `xss-in-search-query-sanitized` | AC-008.14 | ?q=<script>alert('xss')</script> does not produce executable content |
| `xss-search-uses-plainto-tsquery` | AC-008.14 | XSS payload treated as plain text by plainto_tsquery |
| `sqli-in-search-query-neutralized` | AC-008.15 | ?q='; DROP TABLE products; -- does not drop table |
| `sqli-search-parameterized` | AC-008.15 | SQL payload is parameterized; products table intact after query |
| `no-results-script-safely-encoded` | AC-008.16 | Empty result for XSS query does not echo executable content |
| `paging-preserves-query-params` | AC-008.17 | next URL includes q, category, priceMin, priceMax, sortBy, sortOrder |
| `paging-only-changes-page-param` | AC-008.17 | prev and next URLs differ only in page value |
| `error-no-stack-trace` | AC-008.18 | Forced error response contains no stack trace |
| `error-no-sql-fragments` | AC-008.18 | Error responses contain no SQL fragments |

## 8. Validation Rules

| Field | Type | Required | Constraint | Error Response |
| ----- | ---- | -------- | ---------- | -------------- |
| `page` | integer | no | >= 1; defaults to 1 | 400: VALIDATION_ERROR "page must be >= 1" |
| `perPage` | integer | no | 1-100; defaults to 20 | 400: VALIDATION_ERROR "perPage must be between 1 and 100" |
| `q` | string | no | Free-form text; passed to plainto_tsquery | No validation error; empty/omitted means no search filter |
| `category` | string | no | Exact match, case-sensitive | No validation error; empty/omitted means no category filter |
| `priceMin` | decimal | no | Must be numeric; >= 0; must be <= priceMax if both present | 400: VALIDATION_ERROR with field-level reason |
| `priceMax` | decimal | no | Must be numeric; >= 0; must be >= priceMin if both present | 400: VALIDATION_ERROR with field-level reason |
| `sortBy` | enum | no | One of: name, price, stock; defaults to name | 400: VALIDATION_ERROR "Invalid sort field" |
| `sortOrder` | enum | no | One of: asc, desc; defaults to asc | 400: VALIDATION_ERROR "Invalid sort direction" |
| `:sku` (path param) | string | yes | Must reference an existing product | 404: NOT_FOUND "Product not found" |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| High | Full-text search performance degrades with large catalog | GIN index on search_vector; plainto_tsquery avoids complex query plans; monitor query times |
| Medium | plainto_tsquery silently drops stop words, surprising users | Document search behavior; consider using websearch_to_tsquery in future for phrase support |
| Medium | Category filter case-sensitivity confuses users | Document case-sensitive behavior; consider case-insensitive option in future iteration |
| Low | Paging envelope URL construction misses edge cases with special characters in query params | URL-encode all param values in prev/next; test with special characters |
| Low | Sort by stock exposes inventory levels to customers | Stock is already in the product response; sort does not expose additional data |
| Low | priceMin and priceMax with very high precision cause rounding issues | Price stored as NUMERIC(10,2); query params coerced to 2 decimal places |

## 10. Out of Scope

- Search suggestions / autocomplete as the user types
- Session-remembered filters (last-used filters remembered within a session)
- Faceted search (showing category counts alongside results)
- Full-text search highlighting (bold matching terms in results)
- Fuzzy / typo-tolerant search
- Search analytics or query logging
- Product image search or visual search

## 11. Notes

- This story merges the original US-002 (Read & List Products) because `GET /api/products` is the single endpoint that serves both catalog listing (no `q`) and search (with `q`). The behavior differences (default sort vs relevance rank) are controlled by query parameter presence, not by separate endpoints.
- `plainto_tsquery` is used instead of `to_tsquery` because it accepts free-form user input without requiring tsquery operator syntax (e.g., `&`, `|`, `!`). This keeps the search box a plain text field.
- The `search_vector` column is maintained by a PostgreSQL trigger function (see [Data Model section 4](../architecture/data-model.md)), not by application code. The application only reads from it via the GIN index.
- Sort vs relevance precedence rule: when `q` is present and `sortBy` is explicitly provided, `sortBy` takes priority over relevance ranking. When `q` is present and `sortBy` is NOT provided, results are ranked by `ts_rank DESC`. When no `q` is present, the default sort is `name ASC`.

## 12. Related Documents

- [EP03 --- Product Search](../epics/EP03-product-search.md)
- [EP01 --- Product Management](../epics/EP01-product-management.md) (listing is part of EP01 scope)
- [API Contract --- Section 3: Products API](../architecture/api-contract.md)
- [API Contract --- Section 1: Paging Envelope](../architecture/api-contract.md)
- [Data Model --- products, search_vector, GIN index](../architecture/data-model.md)
- [Domain Glossary --- Product, CategoryLabel, Price, SKU](../domain-glossary.md)
- [US-001 --- Project Scaffolding](./US-001-project-scaffolding.md) (dependency)
- [US-002 --- Create Product with Validation & Sanitization](./US-002-create-product.md) (dependency --- products must exist to search)

## 13. Handoff Files

TBD

## 14. Change Log

| Date | Author | Change |
| ---- | ------ | ------ |
| 2026-07-27 | Refinement Agent | Initial draft |
