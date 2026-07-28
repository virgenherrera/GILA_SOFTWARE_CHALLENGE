# T-004 --- Delete Product

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-004 |
| Batch | 1 |
| Epic | EP01 --- Product Management |
| Story | [US-004](../../user-stories/US-004-delete-product.md) |
| Persona | Catalog Manager |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Implement `DELETE /api/products/:sku` with hard delete semantics. When a product is referenced by `cart_items` or `order_items` (FK constraint), return `409 PRODUCT_IN_USE` with a clean, translated error -- never expose raw PostgreSQL constraint names or SQL details.

## Pre-conditions

- [ ] T-002 complete: `POST /api/products` works for creating test data
- [ ] Products table has FK references from `cart_items` (migration 005) and `order_items` (migration 007)
- [ ] Docker Compose running with all 3 services healthy
- [ ] Error middleware from T-001 translates exceptions to structured responses

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-004-delete-product.md | all | 7 acceptance criteria to implement |
| docs/architecture/api-contracts.md | all | DELETE response shape, error codes |
| docs/architecture/database-schema.md | all | FK constraints from cart_items and order_items to products |
| src/ecommerce/product/handler.clj | all | Existing handler to add DELETE endpoint to |
| src/ecommerce/product/repository.clj | all | Existing repository to add delete function to |
| src/ecommerce/middleware.clj | all | Error middleware for exception translation |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| test/ecommerce/product/delete_test.clj | Unit tests for delete logic and FK error handling |
| test/ecommerce/product/delete_integration_test.clj | Integration tests: full HTTP round-trip for DELETE |

### Files to Modify

| File | Change |
|------|--------|
| src/ecommerce/product/handler.clj | Add DELETE /api/products/:sku handler |
| src/ecommerce/product/repository.clj | Add `delete-product` function with FK violation detection |
| src/ecommerce/router.clj | Add `DELETE /api/products/:sku` route |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep01/T-004-delete-product.md` | EXE | exit 0 |
| 2 | Unit tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 3 | Integration tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 4 | 204 for unreferenced | DELETE product with no cart/order references → 204 | MANUAL | HTTP 204, empty body |
| 5 | 404 for non-existent | DELETE `/api/products/NONEXISTENT-SKU` → 404 | MANUAL | HTTP 404 with PRODUCT_NOT_FOUND |
| 6 | 409 for order reference | DELETE product referenced by order_items → 409 | MANUAL | HTTP 409 with PRODUCT_IN_USE |
| 7 | 409 for cart reference | DELETE product referenced by cart_items → 409 | MANUAL | HTTP 409 with PRODUCT_IN_USE |
| 8 | No SQL leak | 409 response contains domain error code, not constraint name | MANUAL | No `fk_cart_items_product` or `23503` in response |
| 9 | Absent from search | After successful delete, product not returned by any query | MANUAL | SELECT by SKU returns empty |
| 10 | Existing tests unbroken | All T-002 and T-003 tests still pass | EXE | `docker compose run --rm backend clojure -M:test` exit 0 |
| 11 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: Soft delete or `is_active` flag
- NOT in scope: Cascade delete of cart/order items
- NOT in scope: Warning UI before delete confirmation
- NOT in scope: Proactive warning endpoint ("this product is in X carts")
- NOT in scope: Batch delete endpoint

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Catch all SQL exceptions generically | Masks the specific FK violation, returns misleading 500 | Catch PostgreSQL error code `23503` (foreign_key_violation) specifically |
| Expose constraint names in error response | Information disclosure; leaks DB schema to clients | Translate to domain code `PRODUCT_IN_USE` with user-friendly message |
| Use soft delete with `is_active` flag | Out of scope; adds query complexity to every product lookup | Hard delete; let FK constraints enforce referential integrity |
| Pre-check FKs before DELETE | Race condition between check and delete; extra queries | Attempt DELETE, catch FK violation, translate error |
| Return 200 with body on success | DELETE convention is 204 No Content | Return 204 with empty body |

## Rollback Guidance

```bash
git checkout -- src/ecommerce/product/handler.clj src/ecommerce/product/repository.clj src/ecommerce/router.clj
rm -f test/ecommerce/product/delete_test.clj test/ecommerce/product/delete_integration_test.clj
```

This restores handler, repository, and router to their pre-T-004 state and removes the delete test files.

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
- Pipeline stages: install → build → lint → test:unit → test:integration
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] test/ecommerce/product/delete_test.clj
- [ ] test/ecommerce/product/delete_integration_test.clj
- [ ] src/ecommerce/product/handler.clj (modified -- DELETE added)
- [ ] src/ecommerce/product/repository.clj (modified -- delete fn added)
- [ ] src/ecommerce/router.clj (modified -- DELETE route added)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Unit tests pass
- [ ] Gate 3: Integration tests pass
- [ ] Gate 4: 204 for unreferenced product
- [ ] Gate 5: 404 for non-existent product
- [ ] Gate 6: 409 for order_items reference
- [ ] Gate 7: 409 for cart_items reference
- [ ] Gate 8: No SQL leak in error response
- [ ] Gate 9: Absent from search after delete
- [ ] Gate 10: Existing tests unbroken
- [ ] Gate 11: No side effects
