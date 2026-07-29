# T-025 --- Search & Import Views UX Overhaul

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-025 |
| Epic | EP08 --- UI/UX Overhaul |
| Story | [US-021](../../user-stories/US-021-component-ux.md) |
| Persona | Angular A11y & Responsive Specialist |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-023 (design tokens), T-024 (product-card component API stable) |

## Objective

Apply accessibility, responsive, and visual polish improvements to the 6 search and
import components: search-page, search-results, search-filters, import-upload,
import-results, import-errors. The critical dedup: search-results MUST reuse
`app-product-card` instead of duplicating its markup (~35 lines). PASS when
search-results uses `<app-product-card>`, import-errors table has scope/caption,
filter inputs have labels, stats grid is responsive, import-errors table transforms
to cards on mobile, and all tests are green; FAIL otherwise.

## Pre-conditions

- [ ] T-023 is DONE --- design tokens exist, color classes replaced
- [ ] T-024 is DONE or in parallel --- `product-card` component accepts `[product]`
  input and emits `(view)`, `(edit)`, `(delete)` events (confirmed: existing API)
- [ ] `search-results.html` duplicates product-card markup (confirmed: lines 6-43
  are near-identical to product-card.html)
- [ ] `search-filters.html` keyword input has no `<label>`, uses placeholder only
  (confirmed: line 6)
- [ ] `import-errors.html` table `<th>` lacks `scope="col"`, no `<caption>`
  (confirmed: lines 17-22)
- [ ] `import-results.html` stats grid is fixed `grid-cols-3` with no breakpoint
  (confirmed: line 35)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/search/search-results/search-results.html` | 1-46 (full) | Replace inline card markup with `<app-product-card>` |
| `frontend/src/app/search/search-results/search-results.ts` | full | Add ProductCardComponent import, adapt events |
| `frontend/src/app/search/search-results/search-results.spec.ts` | full | Update tests for component reuse |
| `frontend/src/app/search/search-filters/search-filters.html` | 1-72 (full) | Add sr-only labels, responsive layout |
| `frontend/src/app/search/search-filters/search-filters.spec.ts` | full | Add label tests |
| `frontend/src/app/search/search-page/search-page.html` | full | Add aria-live region for search results count |
| `frontend/src/app/search/search-page/search-page.ts` | full | May need signal for result count announcement |
| `frontend/src/app/search/search-page/search-page.spec.ts` | full | Update tests |
| `frontend/src/app/imports/import-results/import-results.html` | 1-61 (full) | Stats grid responsive, aria-live for polling |
| `frontend/src/app/imports/import-results/import-results.ts` | full | May need aria-live signal |
| `frontend/src/app/imports/import-results/import-results.spec.ts` | full | Update tests |
| `frontend/src/app/imports/import-errors/import-errors.html` | 1-66 (full) | Table scope/caption, mobile card layout |
| `frontend/src/app/imports/import-errors/import-errors.spec.ts` | full | Update tests |
| `frontend/src/app/imports/import-upload/import-upload.html` | full | Minor a11y polish if needed |
| `frontend/src/app/products/product-card/product-card.ts` | full | Reference only --- read to understand the component API for reuse in search-results. DO NOT modify this file |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/search/search-results/search-results.html` | Replace the entire `<article>` block (lines 6-43) with `<app-product-card [product]="product" (view)="onView(product.sku)" (addToCart)="onAddToCart(product.sku)" />`. Note: product-card currently has (view)/(edit)/(delete) events --- search-results uses (view)/(addToCart). If product-card does not have an (addToCart) output, keep an inline "Add to Cart" button below the card OR add a slot/output to product-card. Prefer the simpler approach |
| `frontend/src/app/search/search-results/search-results.ts` | Import `ProductCardComponent` in the `imports` array |
| `frontend/src/app/search/search-results/search-results.spec.ts` | Update tests: mock ProductCardComponent or import it, test events |
| `frontend/src/app/search/search-filters/search-filters.html` | Add sr-only `<label for="keyword">Search</label>` before the keyword input. Add `id="keyword"` to the input. Add sr-only `<label for="category">Category</label>` before category select. Add `id="category"` to select. Add sr-only `<label for="price-min">` and `<label for="price-max">`. Add sr-only `<label for="sort-by">`. Filters container: `flex flex-wrap gap-3` → `flex flex-col gap-3 sm:flex-wrap sm:flex-row` |
| `frontend/src/app/search/search-filters/search-filters.spec.ts` | Add test: inputs have associated labels |
| `frontend/src/app/search/search-page/search-page.html` | Add `<div aria-live="polite" class="sr-only">` that announces result count on change |
| `frontend/src/app/search/search-page/search-page.spec.ts` | Update tests for aria-live |
| `frontend/src/app/imports/import-errors/import-errors.html` | Add `<caption class="sr-only">Rejected import rows</caption>` inside `<table>`. Add `scope="col"` to all `<th>`. Mobile: wrap table in a div that shows table on md:+ and a stacked card layout on mobile using `@for` with `<dl>` elements. Touch targets on pagination: `py-1.5` → `py-2.5 sm:py-1.5` |
| `frontend/src/app/imports/import-errors/import-errors.spec.ts` | Add test: table has caption and scope |
| `frontend/src/app/imports/import-results/import-results.html` | Stats grid: `grid-cols-3` → `grid-cols-1 sm:grid-cols-3`. Add `aria-live="polite"` on the status badge container so polling updates are announced |
| `frontend/src/app/imports/import-results/import-results.spec.ts` | Update tests for responsive grid and aria-live |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep08/T-025-search-import-views-ux.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | search-results uses product-card | `rg 'app-product-card' frontend/src/app/search/search-results/search-results.html` | at least 1 match |
| 4 | Table caption | `rg '<caption' frontend/src/app/imports/import-errors/import-errors.html` | at least 1 match |
| 5 | Table scope | `rg 'scope="col"' frontend/src/app/imports/import-errors/import-errors.html` | at least 3 matches |
| 6 | Filter labels | `rg '<label' frontend/src/app/search/search-filters/search-filters.html` | at least 3 matches |
| 7 | Stats grid responsive | `rg 'grid-cols-1' frontend/src/app/imports/import-results/import-results.html` | at least 1 match |
| 8 | aria-live | `rg 'aria-live' frontend/src/app/imports/import-results/import-results.html` | at least 1 match |
| 9 | No side effects | `git diff --stat` | Only files listed in Deliverables (+ product-card only if adding addToCart output) |

## Boundaries

- NOT in scope: Modifying `product-card.html` or `product-card.ts` UNLESS adding a
  single `(addToCart)` output is needed for search-results reuse --- minimal change only
- NOT in scope: Product views (product-list, product-detail, product-form) --- T-024
- NOT in scope: Cart, checkout, or order views --- T-026
- NOT in scope: Design tokens or global styles --- T-023
- NOT in scope: Dark mode

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Copy product-card markup into search-results "with modifications" | Perpetuates the exact duplication this task fixes | Import and reuse `<app-product-card>`, adapting events as needed |
| Hide table on mobile with `hidden md:table` and show nothing | Mobile users lose data | Show a stacked card layout on mobile using `@for` + `<dl>` |
| Add `aria-live="assertive"` for polling | Interrupts screen reader mid-sentence every 2 seconds | Use `aria-live="polite"` which waits for a pause |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/search/
git checkout -- frontend/src/app/imports/
```

## Compact Rules

### PROJECT-TEST

- AXIOM-ECHO: every code change runs the Echo System before commit
- All tests must pass before any commit
- TDD Cycle (Red/Green/Refactor) is mandatory
- Breaking an existing test is a blocking issue

### PROJECT-TDD

- Red: write test -> run -> MUST fail -> verify failure is assertion not syntax
- Green: write MINIMUM code -> run -> MUST pass -> full suite -> no regressions
- Refactor: apply SOLID/KISS/DRY/YAGNI -> after EACH refactor: full suite -> if fail: REVERT

### PROJECT-ANTI-DRIFT

- AXIOM-HANDOFF: no code without an approved handoff file
- Scope is defined by the handoff -- work outside boundaries is a violation
- Dead code and unused dependencies MUST be removed

## Status Protocol

```text
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables

- [x] `search-results.html` (reuse product-card)
- [x] `search-results.ts` (import ProductCardComponent)
- [x] `search-results.spec.ts` (updated tests)
- [x] `search-filters.html` (sr-only labels, responsive layout)
- [x] `search-filters.spec.ts` (label tests)
- [x] `search-page.html` (aria-live for result count)
- [x] `search-page.spec.ts` (aria-live test)
- [x] `import-errors.html` (table scope/caption, mobile cards, touch targets)
- [x] `import-errors.spec.ts` (table a11y test)
- [x] `import-results.html` (responsive stats grid, aria-live)
- [x] `import-results.spec.ts` (responsive test)
- [x] `import-results.ts` (STATUS_CLASSES raw colors → theme tokens) — bonus

### Quality Gates

- [x] Gate 1: Handoff exists
- [x] Gate 2: Frontend tests pass (164/164 green)
- [x] Gate 3: search-results uses product-card
- [x] Gate 4: Table caption (sr-only "Rejected import rows")
- [x] Gate 5: Table scope (4 scope="col")
- [x] Gate 6: Filter labels (5 sr-only labels)
- [x] Gate 7: Stats grid responsive (grid-cols-1 sm:grid-cols-3)
- [x] Gate 8: aria-live (polite on status container)
- [x] Gate 9: No side effects (13 files in search/ + imports/)
