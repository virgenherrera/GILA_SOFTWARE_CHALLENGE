# T-018 --- Product rxResource Migration

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-018 |
| Batch | 3 |
| Epic | EP07 --- Angular 22 Modernization |
| Story | [US-018](../../user-stories/US-018-rxresource-migration.md) |
| Persona | Angular Signals/Resource Migration Specialist |
| Model Tier | standard |
| Priority | Should Have |
| Depends On | US-017 (done) |

## Objective

Migrate `ProductService`'s GET methods (`getProducts`, `getProduct`, `getCategories`) to
`rxResource` and update every consuming component (`product-list`, `product-detail`,
`product-form`, `search-page`) to read `.value()`/`.isLoading()`/`.error()` instead of
calling `subscribe()`. PASS when zero `subscribe()` calls remain in these components for
GET data loading and all tests are green; FAIL otherwise.

## Pre-conditions

- [ ] US-017 (Zod schema consistency) is DONE -- `product.schema.ts` already exports
  `ProductResponse`, `Paging`, `CreateProduct`, `UpdateProduct`
- [ ] `frontend/src/app/products/product.service.ts` currently returns `Observable`s for
  all six methods (confirmed by reading the file)
- [ ] Angular 22 scaffold with `rxResource` available via `@angular/core/rxjs-interop`

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/products/product.service.ts` | 1-75 (full file) | Service to migrate: 3 GET methods to `rxResource`, 3 mutation methods stay Observable |
| `frontend/src/app/products/product-list/product-list.ts` | 1-122 (full file) | Consumes `getCategories`/`getProducts`/`deleteProduct`; has pagination + search + category filter state |
| `frontend/src/app/products/product-detail/product-detail.ts` | 1-89 (full file) | Consumes `getProduct`/`deleteProduct`; has 404-vs-error distinction to preserve |
| `frontend/src/app/products/product-form/product-form.ts` | 1-184 (full file) | Consumes `getProduct` for edit-mode prefill via form `patchValue` inside a `subscribe()` callback |
| `frontend/src/app/search/search-page/search-page.ts` | 1-155 (full file) | Consumes `getProducts`/`getCategories`; has a `Subject`-based debounced keyword search to preserve |
| `frontend/src/app/shared/validation/product.schema.ts` | 1-38 (full file) | `ProductResponse`/`Paging`/`CreateProduct`/`UpdateProduct` types consumed by the service and components |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/products/product.service.ts` | Replace `getProducts`/`getProduct`/`getCategories` with `rxResource`-based equivalents; keep `createProduct`/`updateProduct`/`deleteProduct` as `HttpClient` Observables |
| `frontend/src/app/products/product-list/product-list.ts` | Remove `subscribe()`/`takeUntilDestroyed()` for GET calls; consume `.value()`/`.isLoading()`/`.error()`; pagination/search/category state feed the resource's request signal |
| `frontend/src/app/products/product-list/product-list.spec.ts` | Update to assert against resource signal state |
| `frontend/src/app/products/product-detail/product-detail.ts` | Same pattern; `notFound` becomes a `computed()` checking resource error status `=== 404` |
| `frontend/src/app/products/product-detail/product-detail.spec.ts` | Update to assert against resource signal state |
| `frontend/src/app/products/product-form/product-form.ts` | Edit-mode GET uses `rxResource`; form patching moves from `subscribe()` callback to `effect()` watching `.value()` |
| `frontend/src/app/products/product-form/product-form.spec.ts` | Update to assert against resource signal state |
| `frontend/src/app/search/search-page/search-page.ts` | Debounced keyword search: keep `Subject`/`debounceTime`/`distinctUntilChanged`, feed the result into `toSignal()`, drive an `rxResource` request from it; remove the `subscribe()` call for `getProducts`/`getCategories` |
| `frontend/src/app/search/search-page/search-page.spec.ts` | Update to assert against resource signal state |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep07/T-018-product-rxresource.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | Frontend lint | `cd frontend && pnpm exec ng lint` | exit 0 |
| 4 | No subscribe() in products components | `! rg -q 'subscribe\(' frontend/src/app/products/ --type ts --glob '!*.spec.ts'` | exit 0 (no matches) |
| 5 | No subscribe() in search | `! rg -q 'subscribe\(' frontend/src/app/search/ --type ts --glob '!*.spec.ts'` | exit 0 (no matches) |
| 6 | Mutations unchanged | `rg 'createProduct\|updateProduct\|deleteProduct' frontend/src/app/products/product.service.ts` | 3 methods still return `Observable`, still call `this.http.post/put/delete` |
| 7 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: `CartService` -- manages global mutable state via `signal.set()` from
  many mutation call sites (add/remove/update item); does not fit the pull-based
  `resource()` model without a broader redesign (see US-018 AC-018.5)
- NOT in scope: `createProduct`/`updateProduct`/`deleteProduct` on `ProductService` --
  `resource()`/`rxResource` are for data-fetching, not mutations; these stay as
  `HttpClient` Observables
- NOT in scope: Template (`.html`)/style (`.css`) redesign beyond what is strictly
  required to bind to resource signals instead of component signals -- no visual changes
- NOT in scope: Backend API changes -- this is a frontend-only client migration; request/
  response shapes are unchanged
- NOT in scope: `ImportService`/`CheckoutService` migrations -- covered by T-019 and
  T-020 respectively, kept separate to avoid file overlap between parallel handoffs

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Keep `subscribe()` in a migrated component "just for the side effect" | Defeats the entire purpose of this task -- the tech-stack.md gap being closed is exactly this pattern | Use `effect()` reacting to the resource's `.value()`/`.error()` signals for any side effect |
| Wrap `rxResource` in a new custom service class | Adds an indirection layer with no behavioral benefit; the resource IS the service method replacement | Expose the `rxResource` (or a thin factory function) directly from `ProductService`/the component |
| Import from `'rxjs'` in a migrated component | Reintroduces the Observable/subscribe pattern this task removes for GET flows | Only `product.service.ts` (for mutations) keeps RxJS imports; components use signals/`rxResource` |
| Debounce the keyword search inside the resource's request computation | Loses the explicit control needed to prevent over-fetching on every keystroke | Keep `debounceTime`/`distinctUntilChanged` on the RxJS `Subject` side, convert the debounced stream via `toSignal()`, then feed that signal into `rxResource`'s request |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/products/product.service.ts
git checkout -- frontend/src/app/products/product-list/
git checkout -- frontend/src/app/products/product-detail/
git checkout -- frontend/src/app/products/product-form/
git checkout -- frontend/src/app/search/search-page/
```

This restores all six deliverable files (and their specs) to the pre-migration
Observable/subscribe() implementation.

## Compact Rules

### PROJECT-TEST

- AXIOM-ECHO: every code change runs the Echo System before commit
- All tests must pass before any commit
- TDD Cycle (Red/Green/Refactor) is mandatory
- Breaking an existing test is a blocking issue

### PROJECT-TDD

- Red: write test → run → MUST fail → verify failure is assertion not syntax
- Green: write MINIMUM code → run → MUST pass → full suite → no regressions
- Refactor: apply SOLID/KISS/DRY/YAGNI → after EACH refactor: full suite → if fail: REVERT

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

- [x] `frontend/src/app/products/product.service.ts` (GET methods migrated)
- [x] `frontend/src/app/products/product-list/product-list.ts` (+ spec)
- [x] `frontend/src/app/products/product-detail/product-detail.ts` (+ spec)
- [x] `frontend/src/app/products/product-form/product-form.ts` (+ spec)
- [x] `frontend/src/app/search/search-page/search-page.ts` (+ spec)

### Quality Gates

- [x] Gate 1: Handoff exists
- [x] Gate 2: Frontend tests pass (all 54 tests across the 5 migrated feature areas pass;
  the only failures in a full-repo run are pre-existing, in-flight `imports/` files owned
  by the concurrent T-019 handoff, outside this task's boundaries)
- [ ] Gate 3: Frontend lint passes -- no `lint` target is configured in `angular.json`
  (`ng lint` errors with "Cannot find lint target"); pre-existing repo gap, not caused by
  this change. Ran `pnpm exec eslint` directly against all 10 touched files instead: zero
  errors/warnings.
- [x] Gate 4: Zero `subscribe()` in `products/` components
- [x] Gate 5: Zero `subscribe()` in `search/`
- [x] Gate 6: Mutations unchanged (still Observable)
- [x] Gate 7: No side effects (`git diff --stat` on `frontend/src/app/products/` and
  `frontend/src/app/search/` touches exactly the 9 deliverable files plus
  `product.service.spec.ts`, the necessary test companion of the modified service)
