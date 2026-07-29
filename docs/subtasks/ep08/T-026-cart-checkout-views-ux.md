# T-026 --- Cart, Checkout & Order Views UX Overhaul

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-026 |
| Epic | EP08 --- UI/UX Overhaul |
| Story | [US-021](../../user-stories/US-021-component-ux.md) |
| Persona | Angular A11y & Responsive Specialist |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-023 (design tokens must exist first) |

## Objective

Apply accessibility, responsive, and visual polish improvements to the 4 cart,
checkout, and order components: cart-page, cart-item, checkout-page,
order-confirmation. PASS when both data tables have scope/caption, the cart table
transforms to stacked cards on mobile, cart stepper buttons meet 44px touch target,
"Remove" buttons have contextual aria-labels, the order table transforms to cards on
mobile, checkout-page has a proper heading, and all tests are green; FAIL otherwise.

## Pre-conditions

- [ ] T-023 is DONE --- design tokens exist, color classes replaced
- [ ] `cart-page.html` table `<th>` lacks `scope="col"`, no `<caption>`
  (confirmed: lines 23-29)
- [ ] `cart-item.html` stepper buttons are `h-7 w-7` (28px --- below 44px)
  (confirmed: lines 14, 29)
- [ ] `cart-item.html` "Remove" button has no product context (confirmed: line 43)
- [ ] `order-confirmation.html` table `<th>` lacks `scope="col"`, no `<caption>`
  (confirmed: lines 44-51)
- [ ] `checkout-page.html` has no heading --- just a `<p>` and a link (confirmed:
  9 lines total)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/cart/cart-page/cart-page.html` | 1-69 (full) | Table scope/caption, mobile card layout, aria-live for checkout messages |
| `frontend/src/app/cart/cart-page/cart-page.ts` | full | Component logic, signal bindings |
| `frontend/src/app/cart/cart-page/cart-page.spec.ts` | full | Add a11y tests |
| `frontend/src/app/cart/cart-item/cart-item.html` | 1-50 (full) | Touch targets on stepper, aria-label on Remove button |
| `frontend/src/app/cart/cart-item/cart-item.ts` | full | item() signal for aria-label binding |
| `frontend/src/app/cart/cart-item/cart-item.spec.ts` | full | Add a11y tests |
| `frontend/src/app/checkout/checkout-page/checkout-page.html` | 1-10 (full) | Add heading, wrap in card |
| `frontend/src/app/checkout/checkout-page/checkout-page.spec.ts` | full | Update tests |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.html` | 1-78 (full) | Table scope/caption, mobile card layout, add heading |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.ts` | full | Component logic |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.spec.ts` | full | Add a11y tests |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/cart/cart-page/cart-page.html` | Add `<caption class="sr-only">Shopping cart items</caption>` inside `<table>`. Add `scope="col"` to all `<th>` (lines 25-29). Add `<h2>` heading "Your Cart" above the table. Mobile layout: use `hidden md:block` on the table wrapper and add a mobile `@for` loop below it (`md:hidden`) rendering each item as a `<div>` card with `<dl>` for labels/values. Add `aria-live="polite"` on the checkout error container |
| `frontend/src/app/cart/cart-page/cart-page.spec.ts` | Add test: table has caption and scope attributes |
| `frontend/src/app/cart/cart-item/cart-item.html` | Stepper buttons: `h-7 w-7` → `h-11 w-11 sm:h-7 sm:w-7` (44px mobile, 28px desktop). "Remove" button: add `[attr.aria-label]="'Remove ' + item().name + ' from cart'"`. Remove button touch target: `px-2 py-1` → `px-3 py-2.5 sm:px-2 sm:py-1` |
| `frontend/src/app/cart/cart-item/cart-item.spec.ts` | Add test: Remove button has aria-label with product name |
| `frontend/src/app/checkout/checkout-page/checkout-page.html` | Add `<h2 class="text-xl font-semibold text-surface-900">Checkout</h2>` heading. Wrap content in a card div with border and padding |
| `frontend/src/app/checkout/checkout-page/checkout-page.spec.ts` | Add test: heading exists |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.html` | Add `<h2 class="text-xl font-semibold text-surface-900">Order Confirmation</h2>` heading (before the success banner). Add `<caption class="sr-only">Order items</caption>` inside `<table>`. Add `scope="col"` to all `<th>` (lines 46-50). Mobile layout: `hidden md:block` on table, mobile `@for` with `<dl>` cards below (`md:hidden`). Touch targets on any links/buttons |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.spec.ts` | Add test: table has caption and scope, heading exists |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep08/T-026-cart-checkout-views-ux.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | Cart table caption | `rg '<caption' frontend/src/app/cart/cart-page/cart-page.html` | at least 1 match |
| 4 | Cart table scope | `rg 'scope="col"' frontend/src/app/cart/cart-page/cart-page.html` | at least 3 matches |
| 5 | Cart stepper touch target | `rg 'h-11\|w-11' frontend/src/app/cart/cart-item/cart-item.html` | at least 2 matches |
| 6 | Remove aria-label | `rg 'aria-label.*Remove\|aria-label.*remove' frontend/src/app/cart/cart-item/cart-item.html` | at least 1 match |
| 7 | Order table caption | `rg '<caption' frontend/src/app/checkout/order-confirmation/order-confirmation.html` | at least 1 match |
| 8 | Order table scope | `rg 'scope="col"' frontend/src/app/checkout/order-confirmation/order-confirmation.html` | at least 3 matches |
| 9 | Checkout heading | `rg '<h2' frontend/src/app/checkout/checkout-page/checkout-page.html` | at least 1 match |
| 10 | Order heading | `rg '<h2' frontend/src/app/checkout/order-confirmation/order-confirmation.html` | at least 1 match |
| 11 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: Product views (product-card, product-list, product-detail,
  product-form) --- T-024
- NOT in scope: Search or import views --- T-025
- NOT in scope: Design tokens or global styles --- T-023
- NOT in scope: CartService or CheckoutService logic --- only template/presentation
  changes
- NOT in scope: Dark mode

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Hide table on mobile with no alternative | Mobile users lose access to cart data | Show stacked card layout on mobile, table on desktop |
| Use `display: none` on `<caption>` | Some screen readers skip hidden captions | Use `sr-only` class (visually hidden but accessible) |
| Add `aria-label="Remove"` without product name | Screen reader hears "Remove, button" N times | Dynamic: `[attr.aria-label]="'Remove ' + item().name + ' from cart'"` |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/cart/
git checkout -- frontend/src/app/checkout/
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

- [x] `cart-page.html` (table scope/caption, mobile cards, aria-live)
- [x] `cart-page.spec.ts` (table a11y test)
- [x] `cart-item.html` (touch targets, Remove aria-label)
- [x] `cart-item.spec.ts` (aria-label test)
- [x] `checkout-page.html` (heading, card wrapper)
- [x] `checkout-page.spec.ts` (heading test)
- [x] `order-confirmation.html` (heading, table scope/caption, mobile cards)
- [x] `order-confirmation.spec.ts` (table a11y + heading test)

### Quality Gates

- [x] Gate 1: Handoff exists
- [x] Gate 2: Frontend tests pass (157→164 after T-025, all green)
- [x] Gate 3: Cart table caption (sr-only "Shopping cart items")
- [x] Gate 4: Cart table scope (5 scope="col")
- [x] Gate 5: Cart stepper touch target (h-11 w-11 sm:h-7 sm:w-7)
- [x] Gate 6: Remove aria-label ("Remove {name} from cart")
- [x] Gate 7: Order table caption (sr-only "Order items")
- [x] Gate 8: Order table scope (5 scope="col")
- [x] Gate 9: Checkout heading (h2)
- [x] Gate 10: Order heading (h2)
- [x] Gate 11: No side effects (8 files in cart/ + checkout/)
