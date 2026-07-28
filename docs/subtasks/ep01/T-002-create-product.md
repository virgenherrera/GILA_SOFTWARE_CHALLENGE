# T-002 --- Create Product with Validation & Sanitization

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-002 |
| Batch | 1 |
| Epic | EP01 --- Product Management |
| Story | [US-002](../../user-stories/US-002-create-product.md) |
| Persona | Catalog Manager |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Implement `POST /api/products` with full validation using shared Malli schemas, XSS sanitization, SQL injection neutralization, and multi-error response format. This is the foundational CRUD endpoint that T-003 and T-004 depend on.

## Pre-conditions

- [ ] T-001 scaffolding complete and Docker Compose running
- [ ] Shared validation module (`src/ecommerce/validation.clj`) exists with Malli schemas
- [ ] Products table created via migration 001
- [ ] Health endpoint (`GET /api/health`) returns 200

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-002-create-product.md | all | 13 acceptance criteria to implement |
| docs/architecture/api-contract.md | all | Request/response shapes, error envelope |
| docs/architecture/security-guidelines.md | all | XSS sanitization and SQLi prevention rules |
| docs/architecture/data-model.md | all | Products table schema, constraints |
| src/ecommerce/validation.clj | all | Shared Malli schemas to reuse |
| src/ecommerce/middleware.clj | all | Error middleware to integrate with |
| src/ecommerce/router.clj | all | Router to add POST route to |
| src/ecommerce/db.clj | all | DB connection for repository layer |
| docs/architecture/middleware-pipeline.md | all | Middleware stack where validation and error handling execute |
| docs/architecture/validation-pruning.md | all | Malli closed schema, multi-error collection for product fields |
| docs/architecture/error-handling.md | all | Exception→error code translation (VALIDATION_ERROR, CONFLICT) |
| docs/architecture/security-guidelines.md | all | Input security (XSS rejection, SQL injection via parameterized queries) |
| docs/architecture/tdd-workflow.md | all | TDD process for validation rules and API handlers |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| src/ecommerce/product/handler.clj | POST /api/products handler with validation and sanitization |
| src/ecommerce/product/repository.clj | Database operations: insert product, check SKU uniqueness |
| test/ecommerce/product/validation_test.clj | Unit tests for every validation boundary |
| test/ecommerce/product/handler_integration_test.clj | Integration tests: full HTTP round-trip |

### Files to Modify

| File | Change |
|------|--------|
| src/ecommerce/router.clj | Add `POST /api/products` route pointing to handler |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep01/T-002-create-product.md` | EXE | exit 0 |
| 2 | Unit tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 3 | Integration tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 4 | Empty name rejected | `curl -s -X POST http://localhost:3000/api/products -H 'Content-Type: application/json' -d '{"name":"","sku":"TEST-001","price":10,"stock":5}' -w '%{http_code}'` | EXE | HTTP 400 |
| 5 | Duplicate SKU rejected | Two POSTs with same SKU → second returns 409 | MANUAL | HTTP 409 with DUPLICATE_SKU code |
| 6 | XSS sanitized | POST with `<script>alert(1)</script>` in name → stored without tags | MANUAL | Name stored as `alert(1)` or escaped |
| 7 | SQLi neutralized | POST with `'; DROP TABLE products;--` in name → stored as literal string | MANUAL | No table dropped, string stored literally |
| 8 | Multi-error response | POST with multiple invalid fields → all errors returned in one response | MANUAL | errors array contains all violations |
| 9 | No internal leak | Error responses never contain stack traces or SQL details | MANUAL | Only structured error codes and messages |
| 10 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: Read/list/update/delete endpoints
- NOT in scope: Frontend UI for product creation
- NOT in scope: CSV import endpoint
- NOT in scope: Product search or filtering
- NOT in scope: Category assignment during creation

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Write separate validation rules for CSV and API | Drift between validation paths, double maintenance | Use shared Malli schemas from `validation.clj` for both |
| Catch-all exception handler in endpoint | Hides bugs, returns misleading 500s | Catch specific exceptions (duplicate key, validation) and let middleware handle the rest |
| String concatenation for SQL | SQL injection vulnerability | Use parameterized queries via HoneySQL or next.jdbc |
| Return first error only | Poor UX, forces repeated submissions | Collect all validation errors and return them in a single response |
| Expose PostgreSQL error details | Information disclosure vulnerability | Translate DB errors to domain error codes (e.g., DUPLICATE_SKU) |

## Rollback Guidance

```bash
git checkout -- src/ecommerce/product/ test/ecommerce/product/
git checkout -- src/ecommerce/router.clj
```

This removes the product handler, repository, and tests, and restores the router to its pre-modification state.

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
- [ ] src/ecommerce/product/handler.clj
- [ ] src/ecommerce/product/repository.clj
- [ ] test/ecommerce/product/validation_test.clj
- [ ] test/ecommerce/product/handler_integration_test.clj
- [ ] src/ecommerce/router.clj (modified)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Unit tests pass
- [ ] Gate 3: Integration tests pass
- [ ] Gate 4: Empty name rejected (400)
- [ ] Gate 5: Duplicate SKU rejected (409)
- [ ] Gate 6: XSS sanitized
- [ ] Gate 7: SQLi neutralized
- [ ] Gate 8: Multi-error response
- [ ] Gate 9: No internal leak
- [ ] Gate 10: No side effects
