# T-009 --- Cart Operations

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-009 |
| Batch | 2 |
| Epic | EP04 --- Purchase Workflow |
| Story | [US-009](../../user-stories/US-009-cart-operations.md) |
| Persona | Shopper |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-001, T-002 |

## Objective

Implement cart API endpoints (GET /api/cart, POST /api/cart/items, PUT /api/cart/items/:sku, DELETE /api/cart/items/:sku) with signed cookie identity for anonymous cart tracking, price snapshot capture at add-to-cart time, and stock validation that rejects over-stock requests.

## Pre-conditions

- [ ] T-001 scaffolding complete and Docker Compose running
- [ ] T-002 product creation endpoint functional (products exist in DB)
- [ ] `carts` and `cart_items` tables exist via migrations 004 and 005
- [ ] Shared Malli schemas available in `src/ecommerce/validation.clj`

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-009-cart-operations.md | all | 17 acceptance criteria to implement |
| docs/architecture/api-contract.md | 672-871 | Cart endpoint shapes, error envelope |
| docs/architecture/data-model.md | carts/cart_items sections | carts/cart_items schema, FK constraints |
| docs/architecture/tech-stack.md | all | Libraries for cookie signing |
| src/ecommerce/router.clj | all | Router to add cart routes to |
| src/ecommerce/product/repository.clj | all | Product lookup for stock and price |
| src/ecommerce/validation.clj | all | Shared Malli schemas |
| src/ecommerce/middleware.clj | all | Error middleware integration |
| src/ecommerce/db.clj | all | DB connection for repository layer |
| docs/architecture/middleware-pipeline.md | all | Cart cookie middleware placement (route-level) |
| docs/architecture/security-guidelines.md | all | buddy-sign cookie signing, Path=/api, SameSite=Strict, CSRF protection |
| docs/architecture/error-handling.md | all | INSUFFICIENT_STOCK error code |
| docs/architecture/tdd-workflow.md | all | TDD process reference |
| docs/architecture/testing-strategy.md | all | Test pyramid, security test cases, purchase workflow test matrix |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| src/ecommerce/cart/handler.clj | Cart endpoint handlers: GET, POST, PUT, DELETE |
| src/ecommerce/cart/repository.clj | Cart and cart_items DB operations |
| src/ecommerce/cart/middleware.clj | Signed cookie creation, parsing, and validation |
| test/ecommerce/cart/handler_integration_test.clj | Integration tests: full HTTP round-trip for all cart ops |
| test/ecommerce/cart/repository_test.clj | Unit tests for cart repository operations |

### Files to Modify

| File | Change |
|------|--------|
| src/ecommerce/router.clj | Add cart routes under `/api/cart` |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep04/T-009-cart-operations.md` | EXE | exit 0 |
| 2 | Unit tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 3 | Integration tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 4 | Add to empty cart | POST /api/cart/items with valid product → 200, Set-Cookie with signed cart_id | MANUAL | Cookie created, item in cart |
| 5 | Add existing product | POST same SKU again → quantity increases, snapshot price unchanged | MANUAL | qty incremented, same unit_price_snapshot |
| 6 | Stock exceeded | POST qty > available stock → 409 INSUFFICIENT_STOCK | MANUAL | HTTP 409 with error code |
| 7 | Qty zero rejected | PUT qty=0 → 400 | MANUAL | HTTP 400, use DELETE instead |
| 8 | Qty negative rejected | PUT qty=-1 → 400 | MANUAL | HTTP 400, invalid quantity |
| 9 | View empty cart | GET /api/cart with no items → 200 `{items: [], subtotal: 0, total: 0}` | MANUAL | Empty array, zero totals |
| 10 | Subtotal calculation | Cart with multiple items → subtotal = sum(qty * snapshot_price) | MANUAL | Correct arithmetic |
| 11 | Price snapshot immunity | Add product, change product price, GET cart → cart shows OLD price | MANUAL | Snapshot price unchanged |
| 12 | Delete item | DELETE /api/cart/items/:sku → item removed, 200 | MANUAL | Item no longer in cart |
| 13 | Delete non-existent | DELETE /api/cart/items/:bad-sku → 404 | MANUAL | HTTP 404 |
| 14 | Cookie is signed | Tampered cookie → new cart created (old one inaccessible) | MANUAL | Tampering detection works |
| 15 | Backend lint | `docker compose run --rm backend clojure -M:lint` | EXE | exit 0 |
| 16 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: Checkout flow (T-010)
- NOT in scope: Cart abandonment or expiry
- NOT in scope: Multi-device cart sync
- NOT in scope: Saved/persistent carts for registered users
- NOT in scope: Cart item quantity limits beyond stock
- NOT in scope: Frontend cart UI

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Use unsigned cookies | Tampering allows cart hijacking | Sign cookies with HMAC; HttpOnly, SameSite=Strict |
| Re-fetch product price on cart view | Price changes affect existing carts, violating snapshot guarantee | Store `unit_price_snapshot` at add-to-cart time, read from cart_items |
| Allow qty=0 via PUT | Ambiguous semantics, inconsistent with REST | Reject 400; force explicit DELETE for item removal |
| Auto-cap quantity at stock level | Silent behavior change surprises the user | Reject with 409 INSUFFICIENT_STOCK, tell user available qty |
| Create cart cookie on GET | Unnecessary cookies for browsers just browsing | Create cookie lazily on first POST /api/cart/items only |
| Scope cart cookie to Path=/api/cart | Cookie not sent to /api/checkout per RFC 6265 path-matching; breaks checkout silently | Use Path=/api per security-guidelines.md §3 |

## Rollback Guidance

```bash
git checkout -- src/ecommerce/cart/ test/ecommerce/cart/
git checkout -- src/ecommerce/router.clj
```

This removes all cart code and restores the router to its pre-modification state.

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
- [ ] src/ecommerce/cart/handler.clj
- [ ] src/ecommerce/cart/repository.clj
- [ ] src/ecommerce/cart/middleware.clj
- [ ] test/ecommerce/cart/handler_integration_test.clj
- [ ] test/ecommerce/cart/repository_test.clj
- [ ] src/ecommerce/router.clj (modified)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Unit tests pass
- [ ] Gate 3: Integration tests pass
- [ ] Gate 4: Add to empty cart creates cookie
- [ ] Gate 5: Add existing product increases qty
- [ ] Gate 6: Stock exceeded returns 409
- [ ] Gate 7: Qty zero rejected (400)
- [ ] Gate 8: Qty negative rejected (400)
- [ ] Gate 9: View empty cart (200)
- [ ] Gate 10: Subtotal calculation correct
- [ ] Gate 11: Price snapshot immunity
- [ ] Gate 12: Delete item works
- [ ] Gate 13: Delete non-existent returns 404
- [ ] Gate 14: Cookie is signed
- [ ] Gate 15: Backend lint passes
- [ ] Gate 16: No side effects
