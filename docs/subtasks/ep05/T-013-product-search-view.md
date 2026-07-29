# T-013 --- Product Search View

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-013 |
| Batch | 3 |
| Epic | EP05 --- Frontend Views |
| Story | [US-013](../../user-stories/US-013-product-search-view.md) |
| Persona | Customer |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-008, T-011 |

## Objective

Implement an Angular 22 search interface with keyword input, category filter dropdown, price range inputs, sort controls, paginated results grid, and an add-to-cart button per item. The view consumes the existing GET /api/products endpoint via the product.service.ts created in T-011.

## Pre-conditions

- [ ] T-008 backend search API is complete and returns paged results
- [ ] Angular scaffold exists (from T-001)
- [ ] `frontend/src/app/products/product.service.ts` exists (from T-011)
- [ ] GET /api/products accepts query params: q, category, priceMin, priceMax, sortBy, sortOrder, page, perPage

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/architecture/api-contract.md | Section 3 (Products API --- GET /api/products query parameters: q, category, priceMin, priceMax, sortBy, sortOrder), Section 1 (Overview --- Paging Envelope) | Query params, paging envelope, response shape |
| docs/architecture/tech-stack.md | Section 3 (Frontend) | Angular 22 version, Zod version, zoneless config |
| docs/user-stories/US-013-product-search-view.md | all | Acceptance criteria |
| frontend/src/app/products/product.service.ts | all | Existing service to reuse for API calls |
| frontend/src/app/app.routes.ts | all | Current route config to extend |
| docs/domain-glossary.md | Entities, Conventions | Domain terms for naming consistency |
| docs/architecture/error-handling.md | Section 2 (Exception → Error Code Mapping) | API error response shape for search errors |
| docs/architecture/security-guidelines.md | Section 6 (Input Security --- XSS) | Search input must not render as HTML |
| docs/architecture/tdd-workflow.md | Section 5 (Frontend TDD) | TDD process for Angular components |
| docs/architecture/pnpm-config.md | Section 6 (Commands) | pnpm configuration reference |
| docs/architecture/testing-strategy.md | Section 2 (Test Pyramid), Section 3 (What to Test per Epic --- EP05) | Test pyramid, search view test matrix |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| frontend/src/app/search/search-page/search-page.component.ts | Container component: orchestrates filters, results, pagination |
| frontend/src/app/search/search-page/search-page.component.html | Template for search page layout |
| frontend/src/app/search/search-page/search-page.component.spec.ts | Tests for search page container |
| frontend/src/app/search/search-filters/search-filters.component.ts | Presentational: keyword input, category dropdown, price range, sort |
| frontend/src/app/search/search-filters/search-filters.component.html | Template for filter controls |
| frontend/src/app/search/search-filters/search-filters.component.spec.ts | Tests for filter component |
| frontend/src/app/search/search-results/search-results.component.ts | Presentational: product grid with add-to-cart buttons |
| frontend/src/app/search/search-results/search-results.component.html | Template for results grid |
| frontend/src/app/search/search-results/search-results.component.spec.ts | Tests for results component |
| frontend/src/app/search/search.routes.ts | Lazy-loaded route config for /search |

### Files to Modify

| File | Change |
|------|--------|
| frontend/src/app/app.routes.ts | Add lazy route for /search pointing to search.routes.ts |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Search triggers API | Type keyword, verify GET /api/products?q=... fires | MANUAL | Request sent with query param |
| 2 | Category filter | Select category, verify &category= param sent | MANUAL | Filtered results returned |
| 3 | Price range validation | Enter invalid range (min > max), verify Zod error shown | MANUAL | Error message displayed, no API call |
| 4 | Sort controls | Select sort option, verify `&sortBy=` and `&sortOrder=` params sent | MANUAL | Results re-ordered |
| 5 | Cumulative filters | Apply keyword + category + price, verify all params sent | MANUAL | All filters combine correctly |
| 6 | Results grid | Verify product cards show name, price, category | MANUAL | All fields rendered |
| 7 | Pagination | Navigate pages, verify &page= param changes | MANUAL | Correct page of results shown |
| 8 | No results state | Search nonsense term, verify empty state shown | MANUAL | "No results" message displayed |
| 9 | Empty search | Clear all filters, verify all products shown | MANUAL | Full product list returned |
| 10 | XSS safety | Search `<script>alert(1)</script>`, verify encoded | MANUAL | No script execution, query safely encoded |
| 11 | Loading state | Verify resource() loading signal shown during fetch | MANUAL | Loading indicator visible |
| 12 | Add-to-cart button | Verify button present on each product card | MANUAL | Button rendered per item |
| 13 | Product click | Click product, verify navigation to detail view | MANUAL | Route navigates to /products/:id |
| 14 | Debounce | Type quickly, verify only one API call after 300ms pause | MANUAL | Single request after debounce |
| 15 | Unit tests pass | `docker compose run --rm frontend pnpm exec vitest run` | EXE | exit 0 |
| 16 | Lint passes | `docker compose run --rm frontend pnpm exec ng lint` | EXE | exit 0 |
| 17 | Format passes | `docker compose run --rm frontend pnpm exec prettier --check .` | EXE | exit 0 |

## Boundaries

- NOT in scope: Search suggestions or autocomplete --- the backend (T-008) does not expose a suggestions endpoint; no acceptance criterion requires predictive input
- NOT in scope: Session-remembered filters (filters reset on navigation) --- no acceptance criterion requires persisting filter state; each visit to the search page starts from a clean state
- NOT in scope: Faceted search (count per category) --- the backend (T-008) does not compute per-category counts; adding it is an unrequested aggregation feature
- NOT in scope: Responsive design or mobile layout --- no acceptance criterion requires mobile support; the evaluator uses a desktop browser

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Create a separate product service | Duplicates existing code from T-011 | Reuse `product.service.ts` from T-011 |
| No debounce on keyup | Floods backend with requests on every keystroke | Add 300ms debounce on keyword input |
| Submit invalid price range | Wastes API call, confuses user | Validate with Zod before sending request |
| Subscribe to observables manually | Leaks subscriptions, not zoneless idiomatic | Use resource() and signals for async data |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/search/
```

Revert app.routes.ts changes manually if other routes were added in the same commit.

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
- [ ] search-page container component (ts, html, spec)
- [ ] search-filters presentational component (ts, html, spec)
- [ ] search-results presentational component (ts, html, spec)
- [ ] search.routes.ts
- [ ] app.routes.ts updated with /search route

### Quality Gates
- [ ] Gate 1: Search triggers API call
- [ ] Gate 2: Category filter works
- [ ] Gate 3: Price range Zod validation
- [ ] Gate 4: Sort controls work
- [ ] Gate 5: Cumulative filters combine
- [ ] Gate 6: Results grid renders correctly
- [ ] Gate 7: Pagination works
- [ ] Gate 8: No results state shown
- [ ] Gate 9: Empty search shows all
- [ ] Gate 10: XSS safely encoded
- [ ] Gate 11: Loading state via resource()
- [ ] Gate 12: Add-to-cart button present
- [ ] Gate 13: Product click navigates to detail
- [ ] Gate 14: Debounce 300ms on keyup
- [ ] Gate 15: Unit tests pass
- [ ] Gate 16: Lint passes
- [ ] Gate 17: Format passes
