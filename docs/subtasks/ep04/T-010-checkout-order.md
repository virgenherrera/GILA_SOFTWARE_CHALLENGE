# T-010 --- Checkout & Order Creation

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-010 |
| Batch | 2 |
| Epic | EP04 --- Purchase Workflow |
| Story | [US-010](../../user-stories/US-010-checkout-order.md) |
| Persona | Shopper / Business |
| Model Tier | reasoning |
| Priority | Must Have |
| Depends On | T-009 |

## Objective

Implement atomic checkout (POST /api/checkout) with SELECT FOR UPDATE concurrency control, stock re-validation at checkout time, simulated payment (always succeeds), stock decrement, and order creation. Implement order retrieval (GET /api/orders/:id). The checkout must be fully atomic: either all steps succeed or the entire transaction rolls back with no side effects.

## Pre-conditions

- [ ] T-009 cart operations complete and functional
- [ ] `orders` and `order_items` tables exist via migrations 006 and 007
- [ ] Products have stock values populated
- [ ] Cart with items can be created via POST /api/cart/items

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-010-checkout-order.md | all | Acceptance criteria for checkout and orders |
| docs/architecture/api-contract.md | all | Checkout/order endpoint shapes |
| docs/architecture/data-model.md | all | orders/order_items schema, cart status enum |
| docs/architecture/tech-stack.md | all | Transaction support, JDBC details |
| src/ecommerce/cart/handler.clj | all | Cart retrieval for checkout |
| src/ecommerce/cart/repository.clj | all | Cart data access, status update |
| src/ecommerce/product/repository.clj | all | Product stock lookup and decrement |
| src/ecommerce/router.clj | all | Router to add checkout/order routes |
| src/ecommerce/db.clj | all | DB connection, transaction support |
| src/ecommerce/middleware.clj | all | Error middleware integration |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| src/ecommerce/checkout/handler.clj | POST /api/checkout handler |
| src/ecommerce/checkout/service.clj | Atomic checkout logic: validate, lock, pay, decrement, create order |
| src/ecommerce/order/repository.clj | Order and order_items DB operations, GET by ID |
| test/ecommerce/checkout/service_test.clj | Unit tests for checkout service logic |
| test/ecommerce/checkout/handler_integration_test.clj | Integration tests including concurrent checkout race test |

### Files to Modify

| File | Change |
|------|--------|
| src/ecommerce/router.clj | Add `POST /api/checkout` and `GET /api/orders/:id` routes |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep04/T-010-checkout-order.md` | EXE | exit 0 |
| 2 | Unit tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 3 | Integration tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 4 | Checkout success | POST /api/checkout with valid cart -> 201 with order details | MANUAL | Order ID, items, total returned |
| 5 | Stock re-validated | Reduce stock after cart add, checkout -> 409 if insufficient | MANUAL | INSUFFICIENT_STOCK per item |
| 6 | Atomic rollback | Partial failure (e.g., item 2 of 3 fails stock) -> no order, no stock change | MANUAL | DB unchanged after failure |
| 7 | Stock decremented | Successful checkout -> product stock reduced by cart quantities | MANUAL | Stock = original - purchased |
| 8 | Cart status transition | Successful checkout -> cart status = CheckedOut | MANUAL | Cart no longer Active |
| 9 | Empty cart rejected | POST /api/checkout with empty cart -> 400 | MANUAL | HTTP 400 |
| 10 | Concurrent checkout | Two threads checkout same product (stock=1) -> one 201, one 409 | MANUAL | Exactly one succeeds |
| 11 | Order price from snapshot | Order unit_price matches cart unit_price_snapshot, not current product price | MANUAL | Prices match snapshot |
| 12 | GET order | GET /api/orders/:id -> 200 with order details | MANUAL | Order with items returned |
| 13 | GET non-existent order | GET /api/orders/:bad-id -> 404 | MANUAL | HTTP 404 |
| 14 | Order immutable | Order data does not change after creation | MANUAL | GET returns same data |
| 15 | Backend lint | `docker compose run --rm backend clojure -M:lint` | EXE | exit 0 |
| 16 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: Order cancellation or refund
- NOT in scope: Order Fulfilled status transition
- NOT in scope: Real payment provider integration
- NOT in scope: Order history listing (GET /api/orders)
- NOT in scope: Receipt generation or email
- NOT in scope: Frontend checkout UI

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Read stock outside the transaction | Race condition: stock changes between read and write | Read stock inside transaction with SELECT ... FOR UPDATE |
| Optimistic locking (version column check) | Requires retry loops, harder to reason about | Pessimistic locking with SELECT ... FOR UPDATE |
| Use cart snapshot price for stock validation | Price and stock are independent; stock check needs current stock | Use current stock for validation, snapshot price only for order amounts |
| Non-atomic multi-step checkout | Partial state: order created but stock not decremented | Single DB transaction wrapping all steps |
| Allow checkout on already-checked-out cart | Double ordering, stock accounting error | Check cart status = Active before proceeding |

## Rollback Guidance

```bash
git checkout -- src/ecommerce/checkout/ src/ecommerce/order/ test/ecommerce/checkout/
git checkout -- src/ecommerce/router.clj
```

This removes checkout service, order repository, all tests, and restores the router.

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
- [ ] src/ecommerce/checkout/handler.clj
- [ ] src/ecommerce/checkout/service.clj
- [ ] src/ecommerce/order/repository.clj
- [ ] test/ecommerce/checkout/service_test.clj
- [ ] test/ecommerce/checkout/handler_integration_test.clj
- [ ] src/ecommerce/router.clj (modified)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Unit tests pass
- [ ] Gate 3: Integration tests pass
- [ ] Gate 4: Checkout success (201)
- [ ] Gate 5: Stock re-validated at checkout
- [ ] Gate 6: Atomic rollback on partial failure
- [ ] Gate 7: Stock decremented on success
- [ ] Gate 8: Cart status transitions to CheckedOut
- [ ] Gate 9: Empty cart rejected (400)
- [ ] Gate 10: Concurrent checkout race test
- [ ] Gate 11: Order price from snapshot
- [ ] Gate 12: GET order returns details
- [ ] Gate 13: GET non-existent order (404)
- [ ] Gate 14: Order immutable after placement
- [ ] Gate 15: Backend lint passes
- [ ] Gate 16: No side effects
