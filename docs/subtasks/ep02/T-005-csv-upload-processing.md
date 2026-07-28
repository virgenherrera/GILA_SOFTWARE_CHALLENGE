# T-005 --- CSV Upload & Background Processing Pipeline

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-005 |
| Batch | 1 |
| Epic | EP02 --- CSV Import |
| Story | [US-005](../../user-stories/US-005-csv-upload-processing.md) |
| Persona | Catalog Manager |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-001, T-002 |

## Objective

Implement `POST /api/imports` for CSV file upload with background processing via `core.async` go-loop, and `GET /api/imports/:id` for job status tracking. The HTTP response returns immediately (202) while rows are processed asynchronously.

## Pre-conditions

- [ ] T-001 scaffolding complete (deps.edn, router, middleware, db module exist)
- [ ] T-002 shared validation module (`src/ecommerce/validation.clj`) exists
- [ ] Docker Compose runs (`docker compose up -d` exits cleanly)
- [ ] Database tables `csv_import_jobs` and `import_errors` exist (migrations 007, 008)
- [ ] Ring multipart middleware is available in deps.edn

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/architecture/api-contract.md | 468-546 | POST /api/imports and GET /api/imports/:id contracts |
| docs/architecture/data-model.md | 140-184 | csv_import_jobs and import_errors table schemas |
| docs/architecture/testing-strategy.md | all | Test pyramid, CSV trap types, security test cases |
| docs/user-stories/US-005-csv-upload-processing.md | all | All 9 acceptance criteria |
| docs/architecture/tech-stack.md | all | Library versions (core.async, clojure.data.csv, Ring) |
| src/ecommerce/router.clj | all | Current route definitions to extend |
| src/ecommerce/middleware.clj | all | Error handling middleware patterns |
| src/ecommerce/db.clj | all | Database connection pool usage |
| docs/architecture/middleware-pipeline.md | all | Multipart middleware placement (route-level only on POST /api/imports) |
| docs/architecture/validation-pruning.md | all | Malli validation rules for CSV row fields |
| docs/architecture/error-handling.md | all | Import error handling and error response shape |
| docs/architecture/security-guidelines.md | all | XSS/SQL injection handling in CSV rows |
| docs/architecture/tdd-workflow.md | all | TDD process for CSV parsing |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| `src/ecommerce/import/handler.clj` | POST /api/imports (multipart upload), GET /api/imports/:id route handlers |
| `src/ecommerce/import/worker.clj` | core.async go-loop worker that processes jobs from the channel |
| `src/ecommerce/import/parser.clj` | CSV parsing with clojure.data.csv; structural validation (encoding, delimiters) |
| `src/ecommerce/import/repository.clj` | CsvImportJob CRUD: create, update-status, find-by-id |
| `test/ecommerce/import/parser_test.clj` | Unit tests for CSV parsing (valid, empty, corrupt files) |
| `test/ecommerce/import/handler_integration_test.clj` | Integration tests for upload and status endpoints against PostgreSQL |

### Files to Modify

| File | Change |
|------|--------|
| `src/ecommerce/router.clj` | Register import routes under `/api/imports` |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Upload returns 202 | `docker compose run --rm backend clojure -M:test` | EXE | POST with valid CSV returns 202 with `job_id` and `status: "Pending"` |
| 2 | Job status transitions | `docker compose run --rm backend clojure -M:test` | EXE | Pending -> Processing -> Completed/CompletedWithErrors verified |
| 3 | Non-CSV rejected 400 | `docker compose run --rm backend clojure -M:test` | EXE | POST with .json file returns 400 VALIDATION_ERROR |
| 4 | GET status returns counts | `docker compose run --rm backend clojure -M:test` | EXE | GET /api/imports/:id returns id, status, total/accepted/rejected counts |
| 5 | Non-existent job 404 | `docker compose run --rm backend clojure -M:test` | EXE | GET /api/imports/:nonexistent returns 404 NOT_FOUND |
| 6 | Background processing | `docker compose run --rm backend clojure -M:test` | EXE | HTTP 202 returns before any rows are processed |
| 7 | Concurrent imports | `docker compose run --rm backend clojure -M:test` | EXE | Two simultaneous uploads produce distinct job_ids with independent counts |
| 8 | No internal details leaked | `docker compose run --rm backend clojure -M:test` | EXE | Error responses contain no stack traces, SQL, or file paths |
| 9 | All tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 10 | No side effects | `git diff --stat` | EXE | Only expected files modified |

## Boundaries

- NOT in scope: SSE progress streaming (GET /api/imports/:id/progress). Note: api-contract.md §4 references SSE for progress monitoring. SSE is deferred to v2; this task uses polling via GET /api/imports/:id instead.
- NOT in scope: Dry-run preview of import results before committing
- NOT in scope: Row-level validation logic (that is T-006)
- NOT in scope: Import error listing endpoint (GET /api/imports/:id/errors --- that is T-007)
- NOT in scope: Import history listing (GET /api/imports without :id)
- NOT in scope: File size limits configuration UI

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Block HTTP thread waiting for CSV processing | Breaks the 202 async contract; clients hang | Use core.async channel; return 202 immediately |
| Create job record after channel put | If app crashes between put and DB write, job is lost | Create DB record FIRST, then put on channel |
| Use synchronous processing | Violates AC-005.7; client waits for all rows | Use core.async go-loop for async row processing |
| Read entire CSV into memory | Large files exhaust memory | Stream rows via clojure.data.csv lazy sequence |
| Hardcode content-type check as string equals | Misses valid CSV content types | Check file extension (.csv) and content type (text/csv) |
| Single unbounded channel buffer | Under high load, memory exhausts | Use bounded buffer; reject with 503 when full |

## Rollback Guidance

```bash
# Revert all files created/modified by this task
git checkout -- src/ecommerce/import/ src/ecommerce/router.clj
rm -rf src/ecommerce/import/ test/ecommerce/import/
```

No database rollback needed --- tables were created by T-001 migrations.

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
- Pipeline: install → build → lint → test:unit → test:integration → test:e2e
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] src/ecommerce/import/handler.clj
- [ ] src/ecommerce/import/worker.clj
- [ ] src/ecommerce/import/parser.clj
- [ ] src/ecommerce/import/repository.clj
- [ ] test/ecommerce/import/parser_test.clj
- [ ] test/ecommerce/import/handler_integration_test.clj
- [ ] src/ecommerce/router.clj (modified)

### Quality Gates
- [ ] Gate 1: Upload returns 202
- [ ] Gate 2: Job status transitions
- [ ] Gate 3: Non-CSV rejected 400
- [ ] Gate 4: GET status returns counts
- [ ] Gate 5: Non-existent job 404
- [ ] Gate 6: Background processing confirmed
- [ ] Gate 7: Concurrent imports work
- [ ] Gate 8: No internal details leaked
- [ ] Gate 9: All tests pass
- [ ] Gate 10: No side effects
