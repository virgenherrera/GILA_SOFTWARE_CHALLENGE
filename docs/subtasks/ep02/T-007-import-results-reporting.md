# T-007 --- Import Results & Error Reporting

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-007 |
| Batch | 1 |
| Epic | EP02 --- CSV Import |
| Story | [US-007](../../user-stories/US-007-import-results-reporting.md) |
| Persona | Catalog Manager |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Implement `GET /api/imports/:id` summary enhancement and `GET /api/imports/:id/errors` with paginated error list using the standard paging envelope. Each error includes row_number, field_name, error_reason, and sanitized raw_row_data.

## Pre-conditions

- [ ] T-006 complete (import errors stored in `import_errors` table after validation)
- [ ] `csv_import_jobs` table populated with status and counts after imports
- [ ] `import_errors` table has records with row_number, field_name, error_reason, raw_row_data
- [ ] Paging envelope pattern established in the codebase (from T-008 or shared utility)
- [ ] Docker Compose runs (`docker compose up -d` exits cleanly)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-007-import-results-reporting.md | all | All 8 acceptance criteria |
| docs/architecture/api-contract.md | 36-66 | Standard paging envelope specification |
| docs/architecture/api-contract.md | 551-613 | GET /api/imports/:id/errors contract |
| docs/architecture/data-model.md | 136-154 | csv_import_jobs table schema |
| docs/architecture/data-model.md | 155-172 | import_errors table schema |
| src/ecommerce/import/handler.clj | all | Existing handler to extend with errors endpoint |
| src/ecommerce/import/repository.clj | all | Existing repository patterns for job queries |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| `src/ecommerce/import/error_repository.clj` | ImportError queries: paginated list by job_id, count by job_id |
| `test/ecommerce/import/error_reporting_integration_test.clj` | Integration tests for error listing, pagination, sanitization, and 404 handling |

### Files to Modify

| File | Change |
|------|--------|
| `src/ecommerce/import/handler.clj` | Add GET /api/imports/:id/errors route handler with pagination support |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Summary returns counts | `docker compose run --rm backend clojure -M:test` | EXE | GET /api/imports/:id returns status, total_rows, accepted_rows, rejected_rows |
| 2 | Status distinction | `docker compose run --rm backend clojure -M:test` | EXE | Completed (0 errors), CompletedWithErrors (some errors), Failed (unparseable) |
| 3 | Paginated errors | `docker compose run --rm backend clojure -M:test` | EXE | GET /api/imports/:id/errors returns items + paging envelope |
| 4 | Error item shape | `docker compose run --rm backend clojure -M:test` | EXE | Each item has row_number, field_name, error_reason, raw_row_data |
| 5 | raw_row_data sanitized | `docker compose run --rm backend clojure -M:test` | EXE | Script tags in raw_row_data are HTML-entity encoded |
| 6 | Non-existent job 404 | `docker compose run --rm backend clojure -M:test` | EXE | Both summary and errors endpoints return 404 for unknown job |
| 7 | Paging preserves params | `docker compose run --rm backend clojure -M:test` | EXE | prev/next URLs preserve page and perPage params |
| 8 | Page beyond last | `docker compose run --rm backend clojure -M:test` | EXE | Returns 200 with empty items and correct paging.total |
| 9 | No internal details leaked | `docker compose run --rm backend clojure -M:test` | EXE | Error responses contain no stack traces, SQL, or file paths |
| 10 | All tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 11 | No side effects | `git diff --stat` | EXE | Only expected files modified |

## Boundaries

- NOT in scope: CSV export or download of rejected rows
- NOT in scope: SSE progress streaming (deferred to v2)
- NOT in scope: Re-import functionality (upload corrected rows only)
- NOT in scope: Import listing endpoint (GET /api/imports without :id --- not in API contract)
- NOT in scope: Bulk error dismissal or acknowledgment
- NOT in scope: Error filtering or search within a job's errors

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Return unsanitized raw_row_data | XSS vulnerability if rendered in browser | Encode HTML entities (`<` -> `&lt;`, `>` -> `&gt;`) |
| Remove content from raw_row_data for sanitization | Loses diagnostic value; manager cannot identify original row | Use encoding (not removal); preserve the original text structure |
| Use offset-based pagination for very large error sets | Performance degrades with high offsets | Acceptable for MVP scale; index on (csv_import_job_id, row_number) |
| Return generic "invalid row" error reasons | Not actionable; manager cannot fix the CSV | Return field-specific reasons with the exact validation failure |
| Build prev/next URLs without preserving query params | Client loses pagination state; broken navigation | Programmatically build URLs from current request query params |
| Forget 404 check on errors endpoint | Returns empty list instead of 404 for non-existent job | Check job existence first; return 404 if not found |

## Rollback Guidance

```bash
# Revert error repository and handler changes
git checkout -- src/ecommerce/import/handler.clj
rm -f src/ecommerce/import/error_repository.clj
rm -f test/ecommerce/import/error_reporting_integration_test.clj
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
- [ ] src/ecommerce/import/error_repository.clj
- [ ] test/ecommerce/import/error_reporting_integration_test.clj
- [ ] src/ecommerce/import/handler.clj (modified)

### Quality Gates
- [ ] Gate 1: Summary returns counts
- [ ] Gate 2: Status distinction
- [ ] Gate 3: Paginated errors
- [ ] Gate 4: Error item shape
- [ ] Gate 5: raw_row_data sanitized
- [ ] Gate 6: Non-existent job 404
- [ ] Gate 7: Paging preserves params
- [ ] Gate 8: Page beyond last
- [ ] Gate 9: No internal details leaked
- [ ] Gate 10: All tests pass
- [ ] Gate 11: No side effects
