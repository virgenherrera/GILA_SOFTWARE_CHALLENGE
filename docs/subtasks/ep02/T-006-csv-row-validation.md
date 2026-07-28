# T-006 --- CSV Row Validation

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-006 |
| Batch | 1 |
| Epic | EP02 --- CSV Import |
| Story | [US-006](../../user-stories/US-006-csv-row-validation.md) |
| Persona | Catalog Manager |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Implement row-level validation for CSV import using shared Malli schemas from the validation module. Handle all 10 trap types (malformed price, negative stock, empty name, missing category, missing weight, empty row, duplicate SKU in-file, duplicate SKU in catalog, XSS payload, SQL injection payload) with specific error reporting per row.

## Pre-conditions

- [ ] T-005 processing pipeline exists (worker.clj with go-loop, handler.clj, parser.clj)
- [ ] Shared validation module (`src/ecommerce/validation.clj`) exists with Malli schemas
- [ ] Import worker go-loop exists and can process rows from channel
- [ ] Database tables `products`, `csv_import_jobs`, `import_errors` exist
- [ ] Docker Compose runs (`docker compose up -d` exits cleanly)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-006-csv-row-validation.md | all | All 15 acceptance criteria and 10 trap types |
| docs/architecture/api-contract.md | 938-1047 | Validation contract (shared field rules) |
| docs/architecture/data-model.md | 27-50 | Products table schema (field types, constraints) |
| docs/architecture/data-model.md | 155-172 | import_errors table schema |
| src/ecommerce/validation.clj | all | Shared Malli schemas to reuse (not duplicate) |
| src/ecommerce/import/worker.clj | all | Go-loop to wire validator into |
| src/ecommerce/import/repository.clj | all | Import job repository for status updates |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| `src/ecommerce/import/validator.clj` | Row-level validation using shared Malli schemas; XSS detection; empty-row detection; duplicate-SKU tracking |
| `test/ecommerce/import/validator_test.clj` | One unit test per trap type (10+ tests minimum) |
| `test/fixtures/csv/malformed-price.csv` | Rows with price traps: "free", "$29.99", "abc" |
| `test/fixtures/csv/negative-stock.csv` | Rows with negative stock values |
| `test/fixtures/csv/empty-name.csv` | Rows with empty, whitespace-only, and missing names |
| `test/fixtures/csv/missing-category.csv` | Rows with empty/missing category (should pass) |
| `test/fixtures/csv/missing-weight.csv` | Rows with empty/missing weight_kg (should pass) |
| `test/fixtures/csv/empty-rows.csv` | File with interspersed completely empty rows |
| `test/fixtures/csv/duplicate-sku-in-file.csv` | File with two rows sharing the same SKU |
| `test/fixtures/csv/duplicate-sku-in-catalog.csv` | File with a SKU that matches an existing product |
| `test/fixtures/csv/xss-payload.csv` | Rows with script tags and event handler payloads in name |
| `test/fixtures/csv/sql-injection.csv` | Rows with SQL injection payload in name |
| `test/fixtures/csv/mixed-traps.csv` | File with multiple trap types for integration testing |
| `test/fixtures/csv/valid.csv` | File with all valid rows for baseline testing |
| `test/ecommerce/import/pipeline_integration_test.clj` | Full pipeline integration test (upload -> validate -> persist) against PostgreSQL |

### Files to Modify

| File | Change |
|------|--------|
| `src/ecommerce/import/worker.clj` | Wire validator into go-loop; call validator for each row before persistence |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Malformed price rejected | `docker compose run --rm backend clojure -M:test` | EXE | Row with price="free" rejected; import_errors.field_name="price" |
| 2 | Negative stock rejected | `docker compose run --rm backend clojure -M:test` | EXE | Row with stock=-5 rejected; import_errors.field_name="stock" |
| 3 | Empty name rejected | `docker compose run --rm backend clojure -M:test` | EXE | Row with name="" rejected; import_errors.field_name="name" |
| 4 | Missing category accepted | `docker compose run --rm backend clojure -M:test` | EXE | Row with empty category accepted; Product created with null category |
| 5 | Missing weight accepted | `docker compose run --rm backend clojure -M:test` | EXE | Row with empty weight_kg accepted; Product created with null weight_kg |
| 6 | Empty rows skipped | `docker compose run --rm backend clojure -M:test` | EXE | Empty row creates no ImportError; rejected_rows NOT incremented |
| 7 | Duplicate SKU in-file | `docker compose run --rm backend clojure -M:test` | EXE | Second occurrence rejected; first accepted |
| 8 | Duplicate SKU in catalog | `docker compose run --rm backend clojure -M:test` | EXE | Existing catalog SKU triggers upsert; counts as accepted |
| 9 | XSS rejected | `docker compose run --rm backend clojure -M:test` | EXE | Script tag in name rejected with "Unsafe content detected" |
| 10 | SQLi accepted as literal | `docker compose run --rm backend clojure -M:test` | EXE | SQL payload stored literally in products.name; table intact |
| 11 | Shared validation contract | `docker compose run --rm backend clojure -M:test` | EXE | Validator references same Malli schemas as POST /api/products |
| 12 | Row count reconciliation | `docker compose run --rm backend clojure -M:test` | EXE | total_rows = accepted + rejected + skipped reconciles |
| 13 | All tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 14 | No side effects | `git diff --stat` | EXE | Only expected files modified |

## Boundaries

- NOT in scope: Import results reporting UI or error listing endpoint (that is T-007)
- NOT in scope: SSE progress streaming
- NOT in scope: Dry-run preview mode
- NOT in scope: Export of rejected rows as downloadable file
- NOT in scope: Custom delimiter or encoding selection
- NOT in scope: Row-level retry or re-import of individual failed rows

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Create separate validation schemas for CSV | Drift between manual and CSV validation; double maintenance | Reuse shared Malli schemas from validation.clj |
| Reject apostrophe names as SQLi (e.g., "O'Brien's Widget") | False positive; legitimate business data rejected | Only reject XSS (script tags, event handlers); accept SQLi via parameterized queries |
| Count empty rows in rejected count | Violates AC-006.7; skewed error reporting | Track skipped count separately; empty rows are silent skips |
| Use aggressive XSS regex that rejects angle brackets | False positive on names like "Widget <3 pack>" | Target specific patterns: script tags, event handlers, javascript: URIs |
| Stateless duplicate-SKU detection | Cannot detect duplicates within the same file | Maintain an in-memory set of seen SKUs per job; discard after job completes |
| Roll back valid rows when invalid rows exist | Violates AC-006.14; partial success is expected | Process each row independently; valid rows persist even when others fail |

## Rollback Guidance

```bash
# Revert validator and modified worker
git checkout -- src/ecommerce/import/worker.clj
rm -f src/ecommerce/import/validator.clj
rm -rf test/ecommerce/import/validator_test.clj
rm -rf test/ecommerce/import/pipeline_integration_test.clj
rm -rf test/fixtures/csv/
```

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
- [ ] src/ecommerce/import/validator.clj
- [ ] test/ecommerce/import/validator_test.clj
- [ ] test/fixtures/csv/malformed-price.csv
- [ ] test/fixtures/csv/negative-stock.csv
- [ ] test/fixtures/csv/empty-name.csv
- [ ] test/fixtures/csv/missing-category.csv
- [ ] test/fixtures/csv/missing-weight.csv
- [ ] test/fixtures/csv/empty-rows.csv
- [ ] test/fixtures/csv/duplicate-sku-in-file.csv
- [ ] test/fixtures/csv/duplicate-sku-in-catalog.csv
- [ ] test/fixtures/csv/xss-payload.csv
- [ ] test/fixtures/csv/sql-injection.csv
- [ ] test/fixtures/csv/mixed-traps.csv
- [ ] test/fixtures/csv/valid.csv
- [ ] test/ecommerce/import/pipeline_integration_test.clj
- [ ] src/ecommerce/import/worker.clj (modified)

### Quality Gates
- [ ] Gate 1: Malformed price rejected
- [ ] Gate 2: Negative stock rejected
- [ ] Gate 3: Empty name rejected
- [ ] Gate 4: Missing category accepted
- [ ] Gate 5: Missing weight accepted
- [ ] Gate 6: Empty rows skipped
- [ ] Gate 7: Duplicate SKU in-file rejected
- [ ] Gate 8: Duplicate SKU in catalog upserts
- [ ] Gate 9: XSS rejected
- [ ] Gate 10: SQLi accepted as literal
- [ ] Gate 11: Shared validation contract confirmed
- [ ] Gate 12: Row count reconciliation
- [ ] Gate 13: All tests pass
- [ ] Gate 14: No side effects
