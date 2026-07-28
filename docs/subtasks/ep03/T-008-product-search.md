# T-008 --- Product Search with Filters, Sort & Pagination

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-008 |
| Batch | 1 |
| Epic | EP03 --- Product Search |
| Story | [US-008](../../user-stories/US-008-product-search.md) |
| Persona | Customer |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Implement `GET /api/products` (paginated listing with full-text search, category filter, price range, sort controls) and `GET /api/products/:sku` (single product by SKU). Uses PostgreSQL tsvector/tsquery with GIN index for search, HoneySQL for query building, and the standard paging envelope for results.

## Pre-conditions

- [ ] T-001 scaffolding complete (deps.edn, router, middleware, db module exist)
- [ ] T-002 complete (products table populated, POST /api/products works)
- [ ] Products table has `search_vector` TSVECTOR column with GIN index
- [ ] Products table has trigger function `products_search_vector_update()` maintaining search_vector
- [ ] HoneySQL available in deps.edn
- [ ] Docker Compose runs (`docker compose up -d` exits cleanly)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-008-product-search.md | all | All 18 acceptance criteria |
| docs/architecture/api-contract.md | 108-200 | GET /api/products and GET /api/products/:sku contracts |
| docs/architecture/api-contract.md | 36-66 | Standard paging envelope specification |
| docs/architecture/data-model.md | 27-50 | Products table schema with search_vector column |
| docs/architecture/data-model.md | 189-246 | Full-text search: trigger, GIN index, query pattern |
| src/ecommerce/product/handler.clj | all | Existing handler to extend with GET endpoints |
| src/ecommerce/product/repository.clj | all | Existing repository to add search query functions |
| src/ecommerce/db.clj | all | Database connection pool usage |
| docs/architecture/middleware-pipeline.md | all | Query parameter coercion via Reitit middleware |
| docs/architecture/validation-pruning.md | all | Query parameter validation (priceMin, priceMax, sortBy) |
| docs/architecture/error-handling.md | all | Exception→error code translation for search errors |
| docs/architecture/security-guidelines.md | all | Search input security (script content in search queries) |
| docs/architecture/tdd-workflow.md | all | TDD process reference |
| docs/architecture/testing-strategy.md | all | Test pyramid, security test cases, search-specific test matrix |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| `src/ecommerce/product/search.clj` | HoneySQL query builder: tsvector queries, filter clauses, sort ordering, pagination |
| `test/ecommerce/product/search_test.clj` | Unit tests for query builder: filter combinations, sort precedence, param validation |
| `test/ecommerce/product/search_integration_test.clj` | Integration tests against seeded PostgreSQL: full-text search, pagination, filters, security |

### Files to Modify

| File | Change |
|------|--------|
| `src/ecommerce/product/handler.clj` | Add/update GET /api/products with search/filter/sort params; GET /api/products/:sku |
| `src/ecommerce/product/repository.clj` | Add search query functions; integrate HoneySQL query builder from search.clj |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Default listing | `docker compose run --rm backend clojure -M:test` | EXE | page=1, perPage=20, sortBy=name, sortOrder=asc |
| 2 | Single product by SKU | `docker compose run --rm backend clojure -M:test` | EXE | GET /api/products/:sku returns 200 or 404 |
| 3 | Full-text search | `docker compose run --rm backend clojure -M:test` | EXE | ?q=running matches via tsvector/plainto_tsquery |
| 4 | Category filter | `docker compose run --rm backend clojure -M:test` | EXE | ?category=Footwear returns exact match only |
| 5 | Price range filter | `docker compose run --rm backend clojure -M:test` | EXE | ?priceMin=10&priceMax=100 with inclusive bounds |
| 6 | Sort controls | `docker compose run --rm backend clojure -M:test` | EXE | sortBy=price/stock/name with sortOrder=asc/desc |
| 7 | Relevance ranking | `docker compose run --rm backend clojure -M:test` | EXE | q+no sortBy -> ts_rank DESC; q+sortBy -> sortBy wins; no q -> name ASC |
| 8 | Cumulative filters | `docker compose run --rm backend clojure -M:test` | EXE | category+priceRange+search applied with AND logic |
| 9 | Empty catalog | `docker compose run --rm backend clojure -M:test` | EXE | Returns 200, items=[], paging.total=0 |
| 10 | Page beyond last | `docker compose run --rm backend clojure -M:test` | EXE | Returns 200, items=[], prev points to last valid page |
| 11 | Invalid params 400 | `docker compose run --rm backend clojure -M:test` | EXE | page=0, perPage=200, priceMin=abc all return 400 |
| 12 | XSS in query sanitized | `docker compose run --rm backend clojure -M:test` | EXE | Script tags treated as plain text by plainto_tsquery |
| 13 | SQLi in query neutralized | `docker compose run --rm backend clojure -M:test` | EXE | SQL payload parameterized; products table intact |
| 14 | Paging preserves params | `docker compose run --rm backend clojure -M:test` | EXE | next/prev URLs include q, category, priceMin, priceMax, sortBy, sortOrder |
| 15 | No internal details leaked | `docker compose run --rm backend clojure -M:test` | EXE | Error responses contain no stack traces, SQL, or file paths |
| 16 | All tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 17 | No side effects | `git diff --stat` | EXE | Only expected files modified |

## Boundaries

- NOT in scope: Search suggestions or autocomplete
- NOT in scope: Session-remembered filters (last-used filters within a session)
- NOT in scope: Faceted search (showing category counts alongside results)
- NOT in scope: Full-text search highlighting (bold matching terms in results)
- NOT in scope: Result caching layer
- NOT in scope: Fuzzy or typo-tolerant search

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Use LIKE for full-text search | No stemming, no ranking, poor performance at scale | Use tsvector/tsquery with plainto_tsquery and ts_rank |
| Use string interpolation in SQL | SQL injection vulnerability | Use HoneySQL parameterized queries exclusively |
| Forget to preserve query params in paging URLs | Client loses filter/sort state when navigating pages | Build prev/next URLs programmatically from all current params |
| Use to_tsquery instead of plainto_tsquery | Requires operator syntax from user; crashes on special chars | Use plainto_tsquery for free-form user input |
| Default to relevance ranking when no q is present | No meaningful ranking without search term | Default to name ASC when no search term is active |
| Ignore priceMin > priceMax case | Returns empty results without explanation | Validate and return 400 with field-level reason |

## Rollback Guidance

```bash
# Revert handler and repository changes; remove search module
git checkout -- src/ecommerce/product/handler.clj src/ecommerce/product/repository.clj
rm -f src/ecommerce/product/search.clj
rm -f test/ecommerce/product/search_test.clj
rm -f test/ecommerce/product/search_integration_test.clj
```

## Compact Rules

### PROJECT-TEST
- All tests must pass before any commit
- TDD (Red/Green/Refactor) is the default
- Breaking an existing test is a blocking issue
- Tests map directly to acceptance criteria
- Test evidence is required for DOD

### PROJECT-ANTI-DRIFT
- Scope is defined by the handoff --- work outside boundaries is a violation
- Version pinning: exact versions only
- Dead code MUST be removed

### PROJECT-PIPELINE
- Pipeline: install -> build -> lint -> test:unit -> test:integration -> test:e2e
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] src/ecommerce/product/search.clj
- [ ] test/ecommerce/product/search_test.clj
- [ ] test/ecommerce/product/search_integration_test.clj
- [ ] src/ecommerce/product/handler.clj (modified)
- [ ] src/ecommerce/product/repository.clj (modified)

### Quality Gates
- [ ] Gate 1: Default listing
- [ ] Gate 2: Single product by SKU
- [ ] Gate 3: Full-text search
- [ ] Gate 4: Category filter
- [ ] Gate 5: Price range filter
- [ ] Gate 6: Sort controls
- [ ] Gate 7: Relevance ranking
- [ ] Gate 8: Cumulative filters
- [ ] Gate 9: Empty catalog
- [ ] Gate 10: Page beyond last
- [ ] Gate 11: Invalid params 400
- [ ] Gate 12: XSS in query sanitized
- [ ] Gate 13: SQLi in query neutralized
- [ ] Gate 14: Paging preserves params
- [ ] Gate 15: No internal details leaked
- [ ] Gate 16: All tests pass
- [ ] Gate 17: No side effects
