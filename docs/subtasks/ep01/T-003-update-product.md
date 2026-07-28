# T-003 --- Update Product

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-003 |
| Batch | 1 |
| Epic | EP01 --- Product Management |
| Story | [US-003](../../user-stories/US-003-update-product.md) |
| Persona | Catalog Manager |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Implement `PUT /api/products/:sku` with shared Malli validation, SKU immutability enforcement, and cart snapshot immunity. A price change on a product must not retroactively alter existing cart item snapshots.

## Pre-conditions

- [ ] T-002 complete: `POST /api/products` works and returns created products
- [ ] Shared validation module (`src/ecommerce/validation.clj`) exists and is used by T-002
- [ ] Products table has data (can create via POST)
- [ ] Docker Compose running with all 3 services healthy

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-003-update-product.md | all | 7 acceptance criteria to implement |
| docs/architecture/api-contract.md | all | PUT request/response shape, error codes |
| docs/architecture/data-model.md | all | Products table, cart_items snapshot columns |
| src/ecommerce/product/handler.clj | all | Existing handler to add PUT endpoint to |
| src/ecommerce/product/repository.clj | all | Existing repository to add update function to |
| src/ecommerce/validation.clj | all | Shared schemas for validation reuse |
| docs/architecture/validation-pruning.md | all | Malli closed schema, payload pruning on PUT body |
| docs/architecture/error-handling.md | all | Exception→error code translation |
| docs/architecture/security-guidelines.md | all | XSS screening, SQLi prevention, cookie config, security headers |
| docs/architecture/tdd-workflow.md | all | TDD process reference |
| docs/architecture/testing-strategy.md | all | Test pyramid, security test cases, TDD workflow |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| test/ecommerce/product/update_test.clj | Unit tests for update validation and SKU immutability |
| test/ecommerce/product/update_integration_test.clj | Integration tests: full HTTP round-trip for PUT |

### Files to Modify

| File | Change |
|------|--------|
| src/ecommerce/product/handler.clj | Add PUT /api/products/:sku handler |
| src/ecommerce/product/repository.clj | Add `update-product` function with SKU-based lookup |
| src/ecommerce/router.clj | Add `PUT /api/products/:sku` route |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep01/T-003-update-product.md` | EXE | exit 0 |
| 2 | Unit tests pass | `docker compose run --rm backend clojure -M:test --skip-meta :integration` | EXE | exit 0 |
| 3 | Integration tests pass | `docker compose run --rm backend clojure -M:test --focus-meta :integration` | EXE | exit 0 |
| 4 | SKU immutability | PUT `/api/products/:sku` with a body containing a different `sku` field → rejected | MANUAL | HTTP 400 BAD_REQUEST; the URL `:sku` parameter is always authoritative and the body `sku` field, if present, must match or be absent |
| 5 | 404 for non-existent | PUT to `/api/products/NONEXISTENT-SKU` → 404 | MANUAL | HTTP 404 with NOT_FOUND |
| 6 | Validation reuse | No duplicate Malli schemas in update handler | REVIEW | Same schemas as POST handler |
| 7 | Cart snapshot immune | Update product price → existing cart_items retain original snapshot price | MANUAL | cart_items.unit_price_snapshot unchanged |
| 8 | XSS sanitized on update | PUT with `<script>alert(1)</script>` in name → rejected or safely encoded per security-guidelines.md | MANUAL | Payload is either rejected at validation (400) or safely encoded on output; never tag-stripped |
| 9 | SQLi neutralized on update | PUT with `'; DROP TABLE products;--` in name → stored as inert string via parameterized query | MANUAL | No table dropped, string stored literally per security-guidelines.md |
| 10 | No internal leak | Error responses from the update endpoint never contain stack traces, raw SQL fragments, file paths, or internal hostnames | MANUAL | Only structured error codes and messages |
| 11 | Existing tests unbroken | All T-002 tests still pass after changes | EXE | `docker compose run --rm backend clojure -M:test` exit 0 |
| 12 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: Cart recalculation when product price changes
- NOT in scope: Price history tracking or audit log
- NOT in scope: SKU rename or migration functionality
- NOT in scope: Bulk update endpoint
- NOT in scope: Frontend UI for product editing

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Allow SKU in request body to change product identity | SKU is the immutable business key; changing it breaks referential integrity | Ignore SKU in body or reject if it differs from URL param |
| Duplicate validation logic from POST handler | Two divergent validation paths, maintenance burden | Call the same shared Malli schemas used by POST |
| Update cart_items when product price changes | Violates snapshot pricing model; customers see price changes after adding to cart | Cart items store a snapshot at add-time; product updates do not cascade |
| Return 200 with old data | Confusing; client cannot confirm update took effect | Return 200 with the updated product entity |

## Rollback Guidance

```bash
git checkout -- src/ecommerce/product/handler.clj src/ecommerce/product/repository.clj src/ecommerce/router.clj
rm -f test/ecommerce/product/update_test.clj test/ecommerce/product/update_integration_test.clj
```

This restores handler, repository, and router to their T-002 state and removes the update test files.

## Compact Rules

### PROJECT-TEST
- All tests must pass before any commit
- New features require corresponding tests
- TDD (Red/Green/Refactor) is the default
- Breaking an existing test is a blocking issue
- Tests map directly to acceptance criteria
- Test evidence is required for DOD

### PROJECT-ANTI-DRIFT
- Scope is defined by the handoff — work outside boundaries is a violation
- Version pinning: exact versions only (no floating ranges)
- Dead code and unused dependencies MUST be removed

### PROJECT-PIPELINE
- Pipeline stages: install → build → lint → test:unit → test:integration → test:e2e
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] test/ecommerce/product/update_test.clj
- [ ] test/ecommerce/product/update_integration_test.clj
- [ ] src/ecommerce/product/handler.clj (modified — PUT added)
- [ ] src/ecommerce/product/repository.clj (modified — update fn added)
- [ ] src/ecommerce/router.clj (modified — PUT route added)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Unit tests pass
- [ ] Gate 3: Integration tests pass
- [ ] Gate 4: SKU immutability enforced
- [ ] Gate 5: 404 for non-existent product
- [ ] Gate 6: Validation reuse confirmed
- [ ] Gate 7: Cart snapshot immune
- [ ] Gate 8: XSS sanitized on update
- [ ] Gate 9: SQLi neutralized on update
- [ ] Gate 10: No internal leak
- [ ] Gate 11: Existing tests unbroken
- [ ] Gate 12: No side effects
