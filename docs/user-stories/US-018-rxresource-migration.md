> [INDEX](../INDEX.md) / [User Stories](./) / US-018 --- rxResource Signal-Based Data Loading

# US-018 --- rxResource Signal-Based Data Loading

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP07 --- Angular 22 Modernization](../epics/EP07-angular-22-modernization.md) |
| Priority | Should Have |
| Status | Ready |
| Estimation | L |

## 2. Story

As a developer maintaining the frontend, I want GET-based data loading in the Product,
Import, and Checkout features to use `rxResource` and Resource signals instead of manual
`subscribe()`/`takeUntilDestroyed()` wiring, so that the codebase matches the
signals-everywhere contract documented in `tech-stack.md` and no longer carries the
subscription-management boilerplate and leak risk of hand-rolled Observable teardown.

## 3. Definition of Ready

- [x] Domain entity contract frozen (Product, ImportJob/ImportError, Order -- all frozen
  by EP01-EP04 API contracts; unaffected by this story)
- [x] Interface or API contract frozen (`docs/architecture/api-contract.md`) -- no
  endpoint shapes change, only the client-side consumption mechanism
- [x] Input validation rules enumerated with exact boundaries -- N/A, this story touches
  no validation logic
- [x] Edge cases identified with boundary behavior defined -- 404 handling
  (product/order), terminal-status polling cutoff (import job) enumerated in ACs below
- [x] Dependencies identified and resolved -- depends on US-017 (schema types already
  consolidated) being complete before migration begins
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] Role-gate review completed (PO + Dev Lead + SM readiness review 2026-07-29)

## 4. Acceptance Criteria

- [ ] **AC-018.1: ProductService GET methods use rxResource**
  - **Given** `ProductService.getProducts()`, `getProduct()`, and `getCategories()`
    currently return `Observable`s consumed via `subscribe()`
  - **When** the migration is complete
  - **Then** each of these three methods (or their component-facing call sites) is
    exposed through `rxResource`, with `.value()`, `.isLoading()`, and `.error()` signals
    available to consumers
  - **And** `createProduct()`, `updateProduct()`, and `deleteProduct()` remain unchanged
    `HttpClient` Observable-returning methods

- [ ] **AC-018.2: ImportService GET methods use rxResource (polling replaced)**
  - **Given** `ImportService.getJobStatus()` and `getJobErrors()` currently return
    `Observable`s, and `import-results.ts` polls status manually via
    `interval`/`switchMap`/`takeWhile`
  - **When** the migration is complete
  - **Then** `getJobStatus`/`getJobErrors` consumption is expressed as `rxResource`
  - **And** the manual polling loop in `import-results.ts` is replaced by `rxResource`'s
    `refetchInterval`, computed as `2000` while the job status is non-terminal and
    `undefined` once the status is one of `Completed`, `CompletedWithErrors`, or `Failed`
    (see `TERMINAL_IMPORT_STATUSES` in `import.service.ts`)
  - **And** `uploadCsv()` remains an unchanged `HttpClient` Observable POST method

- [ ] **AC-018.3: CheckoutService.getOrder() uses rxResource**
  - **Given** `CheckoutService.getOrder()` currently returns an `Observable` consumed
    via `subscribe()` in `order-confirmation.ts`
  - **When** the migration is complete
  - **Then** order retrieval for the confirmation page is expressed as `rxResource`
  - **And** `checkout()` (the POST mutation) remains an unchanged `HttpClient` Observable
    method

- [ ] **AC-018.4: Components consume Resource signals instead of subscribe()**
  - **Given** `product-list.ts`, `product-detail.ts`, `product-form.ts`,
    `search-page.ts`, `import-results.ts`, `import-errors.ts`, and
    `order-confirmation.ts` currently call `.subscribe()` for their GET data loads
  - **When** the migration is complete
  - **Then** none of these components call `.subscribe()` for GET/data-fetching flows
  - **And** each reads resource state through `.value()`, `.isLoading()`, and `.error()`
    (or `computed()` signals derived from them)
  - **And** any required side effect on data arrival (e.g., patching `product-form`'s
    reactive form on load) uses `effect()` watching the resource's `.value()`, not a
    subscription callback

- [ ] **AC-018.5: CartService remains Observable-based (explicit exclusion)**
  - **Given** `CartService` manages global mutable cart state via `signal.set()` calls
    triggered from multiple mutation methods (add/remove/update item, checkout clear)
  - **When** the migration is complete
  - **Then** `CartService` and its consumers (`cart-page.ts`, `cart-item.ts`,
    `search-page.ts`'s add-to-cart flow) are **not** modified to use `rxResource`
  - **And** this exclusion is documented as an explicit architectural decision, not an
    oversight: `resource()`/`rxResource` model pull-based data fetching keyed to a
    request signal, while cart state is push-based and mutated from many call sites,
    which does not map cleanly onto the resource model without a broader redesign

- [ ] **AC-018.6: All existing tests updated and passing**
  - **Given** every component and service touched by AC-018.1 through AC-018.4 has an
    existing `.spec.ts` file with test doubles built around `Observable`/`subscribe()`
    semantics (e.g., `HttpTestingController`, mocked `Observable` returns)
  - **When** the migration is complete
  - **Then** every corresponding `.spec.ts` file is updated to exercise the
    `rxResource`-based implementation (e.g., asserting on `.value()`/`.isLoading()`/
    `.error()` signal state instead of subscription callbacks)
  - **And** `cd frontend && CI=true pnpm exec ng test --configuration=ci` exits `0` with
    no regressions in unrelated suites (cart, checkout mutation, product mutation tests
    untouched by this story)

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Component/service tests green for every file listed in AC-018.1 through AC-018.4
- [ ] No regressions in existing test suite (full `ng test` run, not just touched specs)
- [ ] `rg 'subscribe\('` returns zero matches in `frontend/src/app/products/`,
  `frontend/src/app/search/`, `frontend/src/app/imports/` (excluding `import-upload.ts`),
  and `frontend/src/app/checkout/order-confirmation/`, restricted to non-`.spec.ts` files
- [ ] `rg 'interval|switchMap|takeWhile'` returns zero matches in
  `frontend/src/app/imports/import-results/`
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `frontend/src/app/products/product.service.ts` | GET methods (`getProducts`, `getProduct`, `getCategories`) re-expressed via `rxResource`; mutations unchanged |
| `frontend/src/app/products/product-list/product-list.ts` | Consume resource signals; remove `subscribe()`/`takeUntilDestroyed()` for GET calls |
| `frontend/src/app/products/product-detail/product-detail.ts` | Consume resource signals; 404 handling via `computed()` on resource error |
| `frontend/src/app/products/product-form/product-form.ts` | Edit-mode GET prefill via `rxResource`; form patching via `effect()` |
| `frontend/src/app/search/search-page/search-page.ts` | Debounced search via `toSignal()` feeding an `rxResource` request; remove manual `Subject`/`debounceTime` wiring |
| `frontend/src/app/imports/import.service.ts` | `getJobStatus`/`getJobErrors` re-expressed via `rxResource`; `uploadCsv` unchanged |
| `frontend/src/app/imports/import-results/import-results.ts` | Replace manual polling with `rxResource` `refetchInterval` |
| `frontend/src/app/imports/import-errors/import-errors.ts` | Consume resource signals; remove `subscribe()` |
| `frontend/src/app/checkout/checkout.service.ts` | `getOrder()` re-expressed via `rxResource`; `checkout()` unchanged |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.ts` | Consume resource signals; 404 vs. generic error distinction via `computed()` |
| Corresponding `.spec.ts` files for every file above | Updated to assert against resource signal state |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `product-service-get-methods-use-rxresource` | AC-018.1 | `getProducts`/`getProduct`/`getCategories` expose `rxResource`-shaped state; POST/PUT/DELETE untouched |
| `product-list-consumes-resource-signals` | AC-018.1, AC-018.4 | `product-list.ts` renders from `.value()`; loading/error come from `.isLoading()`/`.error()`; no `subscribe()` |
| `product-detail-404-via-computed` | AC-018.1, AC-018.4 | `product-detail.ts`'s `notFound` is `computed()` from resource error status 404 |
| `product-form-effect-patches-on-load` | AC-018.1, AC-018.4 | `product-form.ts` patches form fields via `effect()` reacting to resource `.value()` in edit mode |
| `search-page-debounced-rxresource` | AC-018.1, AC-018.4 | `search-page.ts` keyword input drives an `rxResource` request signal without manual `Subject`/`debounceTime` |
| `import-results-refetch-interval-replaces-polling` | AC-018.2, AC-018.4 | `import-results.ts` has no `interval`/`switchMap`/`takeWhile`; resource `refetchInterval` is `2000` while non-terminal, `undefined` once terminal |
| `import-errors-consumes-resource-signals` | AC-018.2, AC-018.4 | `import-errors.ts` renders from resource signals, no `subscribe()` |
| `checkout-getOrder-uses-rxresource` | AC-018.3 | `getOrder()` exposed via `rxResource`; `checkout()` unchanged |
| `order-confirmation-404-vs-error-distinction` | AC-018.3, AC-018.4 | `order-confirmation.ts` distinguishes `notFound` from generic `error` via `computed()` on resource error |
| `cart-service-unchanged` | AC-018.5 | `cart.service.ts` diff shows zero changes from this story; `cart-page`/`cart-item` still use `subscribe()` |
| `full-suite-green-no-regressions` | AC-018.6 | `cd frontend && CI=true pnpm exec ng test --configuration=ci` exits `0` |

## 8. Validation Rules

N/A -- this story changes the reactive data-loading mechanism only; no field-level
validation rule, boundary, or API contract shape is affected.

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | `rxResource`'s `refetchInterval` semantics differ subtly from manual `interval`/`takeWhile` (e.g., in-flight request cancellation on unmount, error-state refetch behavior) | Migrate `import-results.ts` last among the three handoffs (T-019) after `rxResource` patterns are proven on the simpler Product/Checkout cases; verify with an explicit test asserting refetch stops once status is terminal |
| MEDIUM | `product-form.ts`'s edit-mode prefill currently runs inside a `subscribe()` callback that also toggles `loading`; moving this to `effect()` risks re-running on every resource re-fetch, not just the first load | Guard the `effect()` body so it patches the form only when the resource value's identity/SKU changes, not on every signal recomputation |
| MEDIUM | `search-page.ts`'s debounced keyword search currently uses a `Subject` fed by user keystrokes; converting to `toSignal()` + `rxResource` must preserve the 300ms debounce and `distinctUntilChanged()` behavior exactly, or search will over-fetch | Keep the debounce/distinct operators on the RxJS side feeding into `toSignal()`; only the resulting resource fetch moves to `rxResource` |
| LOW | Splitting the migration into three handoffs (T-018/T-019/T-020) by feature risks inconsistent resource patterns between them | All three handoffs share the same compact rules and anti-pattern list in this document; T-019 and T-020 explicitly reference T-018's pattern as precedent |

## 10. Out of Scope

- `CartService` rxResource migration -- needs an architectural redesign (see AC-018.5),
  not a mechanical swap; deferred to a future story if ever pursued
- POST/PUT/DELETE mutations across all services -- `resource()`/`rxResource` model
  data fetching, not mutations; these stay as `HttpClient` Observables project-wide
- Backend changes of any kind -- this is a frontend-only client-side refactor
- Template redesign beyond what is required to bind to resource signals instead of
  component signals populated by `subscribe()`
- Real-time/SSE replacement of import status polling -- `refetchInterval` still polls,
  it just does so through the `rxResource` primitive instead of hand-rolled RxJS

## 11. Notes

- This story is split into three independently deliverable handoffs by feature surface
  to keep each unit of work small and testable in isolation:
  [T-018](../subtasks/ep07/T-018-product-rxresource.md) (Product),
  [T-019](../subtasks/ep07/T-019-import-rxresource.md) (Import),
  [T-020](../subtasks/ep07/T-020-checkout-rxresource.md) (Checkout).
- T-018, T-019, and T-020 have no file overlap and can, in principle, be executed in
  parallel; T-019 is called out as higher-risk (see Risks) due to the polling
  replacement and may be sequenced last if a conservative rollout is preferred.
- The `rxResource` API is part of Angular 22's `@angular/core/rxjs-interop` surface,
  consistent with the tech stack already pinned in `docs/architecture/tech-stack.md`.

## 12. Related Documents

- [EP07 --- Angular 22 Modernization](../epics/EP07-angular-22-modernization.md) --- parent epic
- [US-017 --- Zod Schema Consistency](US-017-zod-schema-consistency.md) --- prerequisite story
- [US-011 --- Product Management Views](US-011-product-management-views.md),
  [US-012 --- CSV Import View](US-012-csv-import-view.md),
  [US-014 --- Cart & Checkout Views](US-014-cart-checkout-views.md) --- original stories
  whose views are migrated here
- [Tech Stack](../architecture/tech-stack.md) --- signals-everywhere invariant (line 148)

## 13. Handoff Files

- [T-018 --- Product rxResource Migration](../subtasks/ep07/T-018-product-rxresource.md)
- [T-019 --- Import rxResource Migration](../subtasks/ep07/T-019-import-rxresource.md)
- [T-020 --- Checkout rxResource Migration](../subtasks/ep07/T-020-checkout-rxresource.md)

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-29 | Initial creation | Refine phase -- remediating tech-stack.md signals-everywhere deviation |
