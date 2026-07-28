# T-014 --- Cart & Checkout Views

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-014 |
| Batch | 3 |
| Epic | EP05 --- Frontend Views |
| Story | [US-014](../../user-stories/US-014-cart-checkout-views.md) |
| Persona | Shopper |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-009 (cart API), T-010 (checkout/orders API) |

## Objective

Implement an Angular 22 cart view (item list, quantities, totals, adjustment), checkout flow (button triggers POST, confirmation on success), order confirmation page, and a cart badge in the header. The cart uses snapshot prices from the API and handles stock conflicts inline.

## Pre-conditions

- [ ] T-009 cart API is complete (GET/POST/PUT/DELETE /api/cart/items)
- [ ] T-010 checkout/orders API is complete (POST /api/checkout, GET /api/orders/:id)
- [ ] Angular scaffold exists (from T-001)
- [ ] Shared layout component exists at `frontend/src/app/shared/layout/`

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/architecture/api-contracts.md | Cart and Checkout sections | Endpoints, request/response shapes, error codes |
| docs/architecture/tech-stack.md | all | Angular 22, Zod, zoneless config |
| docs/user-stories/US-014-cart-checkout-views.md | all | Acceptance criteria |
| frontend/src/app/app.routes.ts | all | Current route config to extend |
| frontend/src/app/shared/layout/ | all | Header component for cart badge |
| docs/domain-glossary.md | all | Domain terms for naming consistency |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| frontend/src/app/cart/cart.service.ts | Service: cart CRUD via /api/cart/items, cart state as signals |
| frontend/src/app/cart/cart-page/cart-page.component.ts | Container: orchestrates cart items, totals, checkout trigger |
| frontend/src/app/cart/cart-page/cart-page.component.html | Template for cart page |
| frontend/src/app/cart/cart-page/cart-page.component.spec.ts | Tests for cart page container |
| frontend/src/app/cart/cart-item/cart-item.component.ts | Presentational: single item row with qty controls and remove |
| frontend/src/app/cart/cart-item/cart-item.component.html | Template for cart item row |
| frontend/src/app/cart/cart-item/cart-item.component.spec.ts | Tests for cart item component |
| frontend/src/app/cart/cart.routes.ts | Lazy-loaded route config for /cart |
| frontend/src/app/checkout/checkout.service.ts | Service: POST /api/checkout, GET /api/orders/:id |
| frontend/src/app/checkout/checkout-page/checkout-page.component.ts | Container: checkout confirmation trigger |
| frontend/src/app/checkout/checkout-page/checkout-page.component.html | Template for checkout page |
| frontend/src/app/checkout/checkout-page/checkout-page.component.spec.ts | Tests for checkout page |
| frontend/src/app/checkout/order-confirmation/order-confirmation.component.ts | Presentational: order ID, status, items, total |
| frontend/src/app/checkout/order-confirmation/order-confirmation.component.html | Template for order confirmation |
| frontend/src/app/checkout/order-confirmation/order-confirmation.component.spec.ts | Tests for order confirmation |
| frontend/src/app/checkout/checkout.routes.ts | Lazy-loaded route config for /checkout |

### Files to Modify

| File | Change |
|------|--------|
| frontend/src/app/app.routes.ts | Add lazy routes for /cart and /checkout |
| frontend/src/app/shared/layout/ | Add cart badge showing item count in header |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Cart items display | Open /cart, verify items show sku, name, qty, price, subtotal | MANUAL | All fields rendered |
| 2 | Grand total | Verify grand total is sum of all subtotals | MANUAL | Correct calculation |
| 3 | Quantity adjustment | Change qty via PUT, verify cart updates | MANUAL | New qty reflected, total recalculated |
| 4 | Qty Zod validation | Enter qty <= 0, verify Zod error shown | MANUAL | Error message, no API call |
| 5 | Remove item | Click remove, verify DELETE sent and item gone | MANUAL | Item removed from cart |
| 6 | 409 stock error | Trigger stock conflict, verify inline error shown | MANUAL | Error on affected item, cart stays open |
| 7 | Empty cart state | Open /cart with no items, verify empty message | MANUAL | "Cart is empty" displayed |
| 8 | Checkout trigger | Click checkout, verify POST /api/checkout sent | MANUAL | Request fires |
| 9 | Success navigation | After successful checkout, verify redirect to confirmation | MANUAL | Navigates to /checkout/confirmation/:id |
| 10 | Stock failure display | Checkout fails on stock, verify which items shown | MANUAL | Affected items listed with error |
| 11 | Order confirmation | Verify order ID, status, items, total shown | MANUAL | All order details rendered |
| 12 | Cart badge | Verify header shows cart item count | MANUAL | Badge updates on add/remove |
| 13 | Snapshot prices | Verify cart shows prices from API, not re-fetched | MANUAL | Prices match cart API response |
| 14 | XSS safety | Product name with HTML tags renders as text | MANUAL | No script execution |
| 15 | Loading states | Verify loading indicators during API calls | MANUAL | Spinner/skeleton shown |
| 16 | Unit tests pass | `docker compose run --rm frontend npx vitest run` | EXE | exit 0 |
| 17 | Lint passes | `docker compose run --rm frontend npx ng lint` | EXE | exit 0 |
| 18 | Format passes | `docker compose run --rm frontend npx prettier --check .` | EXE | exit 0 |

## Boundaries

- NOT in scope: Saved carts or wishlist
- NOT in scope: Multi-device cart sync
- NOT in scope: Order history page
- NOT in scope: Order cancellation
- NOT in scope: Responsive design or mobile layout

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Re-fetch product prices for cart display | Cart API already includes snapshot prices; extra calls waste bandwidth and risk price mismatch | Use prices from the cart API response directly |
| Allow negative quantity in UI | Backend rejects it, wastes a round trip | Validate qty > 0 with Zod before sending PUT |
| Navigate away from cart on stock error | User loses cart context, cannot adjust quantities | Show 409 error inline on affected items, keep cart open |
| Create monolithic cart+checkout component | Violates container-presentational pattern | Separate cart and checkout into distinct modules with dedicated routes |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/cart/ frontend/src/app/checkout/
```

Revert app.routes.ts and shared layout changes manually if other modifications exist in the same commit.

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
- Pipeline: install -> build -> lint -> test:unit -> test:integration
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] cart.service.ts
- [ ] cart-page container component (ts, html, spec)
- [ ] cart-item presentational component (ts, html, spec)
- [ ] cart.routes.ts
- [ ] checkout.service.ts
- [ ] checkout-page container component (ts, html, spec)
- [ ] order-confirmation presentational component (ts, html, spec)
- [ ] checkout.routes.ts
- [ ] app.routes.ts updated with /cart and /checkout routes
- [ ] Cart badge added to shared layout header

### Quality Gates
- [ ] Gate 1: Cart items display correctly
- [ ] Gate 2: Grand total correct
- [ ] Gate 3: Quantity adjustment via PUT
- [ ] Gate 4: Qty Zod validation
- [ ] Gate 5: Remove item via DELETE
- [ ] Gate 6: 409 stock error shown inline
- [ ] Gate 7: Empty cart state
- [ ] Gate 8: Checkout POST fires
- [ ] Gate 9: Success navigates to confirmation
- [ ] Gate 10: Stock failure shows affected items
- [ ] Gate 11: Order confirmation displays details
- [ ] Gate 12: Cart badge in header
- [ ] Gate 13: Snapshot prices used
- [ ] Gate 14: XSS safe
- [ ] Gate 15: Loading states shown
- [ ] Gate 16: Unit tests pass
- [ ] Gate 17: Lint passes
- [ ] Gate 18: Format passes
