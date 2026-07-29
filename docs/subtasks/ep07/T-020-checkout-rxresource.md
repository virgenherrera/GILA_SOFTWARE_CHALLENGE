# T-020 --- Checkout rxResource Migration

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-020 |
| Batch | 3 |
| Epic | EP07 --- Angular 22 Modernization |
| Story | [US-018](../../user-stories/US-018-rxresource-migration.md) |
| Persona | Angular Signals/Resource Migration Specialist |
| Model Tier | standard |
| Priority | Should Have |
| Depends On | US-017 (done) |

## Objective

Migrate `CheckoutService.getOrder()` to `rxResource` and update `order-confirmation.ts`
to consume Resource signals instead of `subscribe()`, while preserving the existing
404-vs-generic-error distinction. PASS when zero `subscribe()` calls remain in
`order-confirmation.ts` for GET data loading, the 404/error distinction still works, and
all tests are green; FAIL otherwise.

## Pre-conditions

- [ ] US-017 (Zod schema consistency) is DONE -- `checkout.schema.ts` already exports
  `Order`
- [ ] `frontend/src/app/checkout/checkout.service.ts` currently returns `Observable`s for
  both `checkout()` and `getOrder()` (confirmed by reading the file)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/checkout/checkout.service.ts` | 1-18 (full file) | Service to migrate: `getOrder()` to `rxResource`; `checkout()` (POST mutation) stays Observable |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.ts` | 1-65 (full file) | Consumes `getOrder()`; has the `notFound` vs. `error` distinction (404 vs. generic) and a `formattedTotal` `computed()` to preserve |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/checkout/checkout.service.ts` | Replace `getOrder()` with an `rxResource`-based equivalent; keep `checkout()` as an `HttpClient` Observable POST |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.ts` | Remove `subscribe()`/`takeUntilDestroyed()` for the order load; consume `.value()`/`.isLoading()`/`.error()`; `notFound` becomes a `computed()` checking resource error status `=== 404`; `formattedTotal` stays a `computed()`, now derived from the resource's `.value()` |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.spec.ts` | Update to assert against resource signal state, including both the 404 and generic-error branches |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep07/T-020-checkout-rxresource.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | Frontend lint | `cd frontend && pnpm exec ng lint` | exit 0 |
| 4 | No subscribe() in order-confirmation | `! rg -q 'subscribe\(' frontend/src/app/checkout/order-confirmation/ --type ts --glob '!*.spec.ts'` | exit 0 (no matches) |
| 5 | checkout() unchanged | `rg 'checkout\(\)' frontend/src/app/checkout/checkout.service.ts` | still returns `Observable<Order>` via `this.http.post` |
| 6 | 404 distinction preserved | Test asserts `notFound()` is `true` on a 404 response and `error()` is set (non-null) on a 500 response, and the two states render different messages | EXE (unit test) |
| 7 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: `checkout()` -- the POST mutation that creates the order; stays as an
  `HttpClient` Observable, no `resource()` semantics apply to mutations
- NOT in scope: `CartService` -- a completely separate service with its own signal-based
  state management (see US-018 AC-018.5); not touched by this checkout-only handoff
- NOT in scope: Cart page components (`cart-page.ts`, `cart-item.ts`) -- they only use
  `CartService`, which is excluded from this epic's resource migration
- NOT in scope: `ProductService`/`ImportService` migrations -- covered by T-018 and
  T-019 respectively, kept separate to avoid file overlap between parallel handoffs

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Collapse `notFound` and `error` into a single generic error signal | Loses the distinct 404 messaging that AC-018.4/original US-014 behavior depends on | Derive both as separate `computed()` signals from the resource's `.error()`, checking `status === 404` for `notFound` and "any other error" for `error` |
| Call `subscribe()` on `checkout()`'s result from within `order-confirmation.ts` | `order-confirmation.ts` only reads order data by ID from the route; checkout is triggered from `cart-page.ts`, not here -- subscribing here would be dead/misplaced code | Leave `checkout()` invocation exactly where it already lives (`cart-page.ts`); `order-confirmation.ts` only ever reads via `getOrder()` |
| Recompute `formattedTotal` from a local copy of the order signal | Creates a second source of truth that can drift from the resource's `.value()` | Derive `formattedTotal` directly from the resource's `.value()` via `computed()`, exactly as it currently derives from the local `order` signal |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/checkout/checkout.service.ts
git checkout -- frontend/src/app/checkout/order-confirmation/
```

This restores the service and the order-confirmation component (plus spec) to the
pre-migration Observable/subscribe() implementation.

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

- [ ] `frontend/src/app/checkout/checkout.service.ts` (`getOrder()` migrated)
- [ ] `frontend/src/app/checkout/order-confirmation/order-confirmation.ts` (+ spec)

### Quality Gates

- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Frontend tests pass
- [ ] Gate 3: Frontend lint passes
- [ ] Gate 4: Zero `subscribe()` in `order-confirmation/`
- [ ] Gate 5: `checkout()` unchanged
- [ ] Gate 6: 404 vs. generic error distinction preserved
- [ ] Gate 7: No side effects
