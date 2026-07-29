> [INDEX](../INDEX.md) / [User Stories](./) / US-005 --- CSV Upload & Background Processing Pipeline

# US-005 --- CSV Upload & Background Processing Pipeline

## 1. Metadata

| Field    | Value                           |
| -------- | ------------------------------- |
| Epic     | EP02 --- CSV Import             |
| Priority | Must Have                       |
| Status | Ready                           |
| Estimation | L                             |

## 2. Story

**As a** Catalog Manager,
**I want to** upload a CSV file and have it processed as a background job,
**so that** I get an immediate response and can check progress later.

## 3. Definition of Ready

- [x] Story follows the INVEST criteria
- [x] Acceptance criteria are testable and unambiguous
- [x] Dependencies identified (US-001 scaffolding, US-002 shared validation module)
- [x] API contract reviewed ([API Contract](../architecture/api-contract.md) section 4)
- [x] Data model reviewed ([Data Model](../architecture/data-model.md) sections 2.6, 2.7)
- [x] Domain glossary terms aligned (CsvImportJob, ImportError, status lifecycle)
- [x] Out of scope items explicitly listed
- [x] Test plan covers every acceptance criterion
- [x] Role-gate review completed (PO + Dev Lead + SM readiness review 2026-07-28)

## 4. Acceptance Criteria

### AC-005.1: Upload valid CSV file returns 202 Accepted

**Given** a Catalog Manager has a valid CSV file with a header row and at least one data row
**When** the manager sends `POST /api/imports` with the file as `multipart/form-data`
**Then** the response status is `202 Accepted`
**And** the response body contains `job_id` (UUID), `status` equal to `"Pending"`, and a `message` with the progress URL
**And** a `csv_import_jobs` record exists in the database with the returned `job_id` and `status = 'Pending'`

### AC-005.2: Job status transitions through Pending, Processing, Completed/CompletedWithErrors

**Given** a CSV import job has been created via `POST /api/imports`
**When** the background worker picks up the job
**Then** the job status transitions from `Pending` to `Processing` (with `started_at` set)
**And** after all rows are processed, the status transitions to `Completed` (if zero rejected rows) or `CompletedWithErrors` (if one or more rejected rows)
**And** `completed_at` is set on the terminal transition
**And** no other status transitions are possible from `Completed` or `CompletedWithErrors`

### AC-005.3: Upload non-CSV or missing file returns 400

**Given** a request to `POST /api/imports`
**When** the request body is missing the `file` field, or the uploaded file is not a CSV (e.g., `.json`, `.xlsx`, `.txt` without CSV structure)
**Then** the response status is `400 Bad Request`
**And** the response body contains `error.code` equal to `"VALIDATION_ERROR"` and `error.message` describing the expected file type
**And** no `csv_import_jobs` record is created

### AC-005.4: Completely unparseable CSV results in Failed status

**Given** a Catalog Manager uploads a file that passes the file-type check but is structurally unparseable (wrong delimiter, binary content, corrupt encoding)
**When** the background worker attempts to parse the file
**Then** the job status transitions to `Failed` (not `CompletedWithErrors`)
**And** `completed_at` is set
**And** no `import_errors` records are created (the file could not be parsed into rows at all)

### AC-005.5: GET /api/imports/:id returns current job status with counts

**Given** a CSV import job exists with a known `job_id`
**When** the manager sends `GET /api/imports/:id`
**Then** the response status is `200 OK`
**And** the response body contains `id`, `source_filename`, `status`, `started_at`, `completed_at`, `total_rows`, `accepted_rows`, `rejected_rows`
**And** the counts reflect the current processing state (may be partial if still `Processing`)

### AC-005.6: Non-existent job ID returns 404

**Given** no CSV import job exists with the requested ID
**When** the manager sends `GET /api/imports/:id` with a non-existent UUID
**Then** the response status is `404 Not Found`
**And** the response body contains `error.code` equal to `"NOT_FOUND"` and `error.message` equal to `"Import job not found"`

### AC-005.7: Background processing uses core.async channel

**Given** a valid CSV file is uploaded via `POST /api/imports`
**When** the handler creates the job record and places work on the core.async channel
**Then** the HTTP response (202) returns to the client before any CSV rows are validated or persisted
**And** the go-loop worker picks up the job from the channel and processes rows asynchronously

### AC-005.8: Multiple concurrent imports process independently

**Given** two Catalog Managers upload CSV files at approximately the same time
**When** both `POST /api/imports` requests complete
**Then** each receives a distinct `job_id`
**And** each job processes its own rows independently without interference
**And** `GET /api/imports/:id` for each job reflects only that job's counts and status

### AC-005.9: Error responses never leak internal details

**Given** any error occurs during upload or job status retrieval
**When** the error response is returned to the client
**Then** the response body never contains stack traces, exception class names, raw SQL fragments, file system paths, or internal hostnames
**And** the `error.message` is a safe, human-readable description

## 5. Definition of Done

- [ ] `POST /api/imports` endpoint accepts multipart/form-data and returns 202
- [ ] `GET /api/imports/:id` endpoint returns current job status
- [ ] Background worker processes jobs via core.async go-loop
- [ ] Job status transitions follow the CsvImportJob state machine
- [ ] Job record is created in DB before channel put (crash safety)
- [ ] Ring async/streaming-body configured for future SSE support
- [ ] All acceptance criteria pass automated tests
- [ ] Integration tests run against PostgreSQL (Testcontainers)
- [ ] No stack traces, SQL, or file paths leak in error responses
- [ ] Code reviewed and merged

## 6. Deliverables

### Files to Create

| File | Purpose |
| ---- | ------- |
| `src/ecommerce/import/handler.clj` | POST /api/imports, GET /api/imports/:id route handlers |
| `src/ecommerce/import/worker.clj` | core.async go-loop worker that processes jobs from the channel |
| `src/ecommerce/import/parser.clj` | CSV parsing with clojure.data.csv; structural validation |
| `src/ecommerce/import/repository.clj` | CsvImportJob CRUD operations (create, update status, find by ID) |
| `test/ecommerce/import/parser_test.clj` | Unit tests for CSV parsing (valid, empty, corrupt files) |
| `test/ecommerce/import/handler_integration_test.clj` | Integration tests for upload and status endpoints against PG |

### Files to Modify

| File | Change |
| ---- | ------ |
| `src/ecommerce/core.clj` (or router) | Register import routes under `/api/imports` |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `upload-valid-csv-returns-202` | AC-005.1 | POST with valid CSV returns 202 with job_id and status "Pending" |
| `upload-creates-db-record` | AC-005.1 | After POST, csv_import_jobs table contains a record with matching job_id |
| `job-transitions-pending-to-processing` | AC-005.2 | After worker picks up job, status becomes "Processing" and started_at is set |
| `job-transitions-to-completed` | AC-005.2 | After all valid rows processed, status is "Completed" with correct counts |
| `job-transitions-to-completed-with-errors` | AC-005.2 | After mix of valid/invalid rows, status is "CompletedWithErrors" |
| `upload-no-file-returns-400` | AC-005.3 | POST without file field returns 400 VALIDATION_ERROR |
| `upload-non-csv-returns-400` | AC-005.3 | POST with .json file returns 400 VALIDATION_ERROR |
| `upload-no-file-creates-no-record` | AC-005.3 | After rejected upload, no csv_import_jobs record exists |
| `unparseable-csv-sets-failed-status` | AC-005.4 | Binary/corrupt file results in job status "Failed" |
| `failed-job-has-no-import-errors` | AC-005.4 | A "Failed" job has zero import_errors records |
| `get-job-status-returns-200` | AC-005.5 | GET /api/imports/:id for existing job returns 200 with all fields |
| `get-job-status-reflects-counts` | AC-005.5 | Response counts match actual accepted/rejected/total in DB |
| `get-nonexistent-job-returns-404` | AC-005.6 | GET /api/imports/:nonexistent returns 404 NOT_FOUND |
| `http-response-before-processing` | AC-005.7 | 202 response arrives before any row validation begins |
| `worker-processes-via-channel` | AC-005.7 | Job is placed on core.async channel; go-loop picks it up asynchronously |
| `concurrent-imports-independent` | AC-005.8 | Two simultaneous uploads produce distinct job_ids with independent counts |
| `error-no-stack-trace` | AC-005.9 | Forced internal error response contains no stack trace or SQL |
| `error-no-file-paths` | AC-005.9 | Error responses contain no file system paths |

## 8. Validation Rules

| Field | Type | Required | Constraint | Error Response |
| ----- | ---- | -------- | ---------- | -------------- |
| `file` (multipart) | file | yes | Must be present in multipart/form-data | 400: "Missing file" |
| `file` content type | string | yes | Must be a CSV file (text/csv or .csv extension) | 400: "Invalid file: expected a CSV file" |
| `file` structure | CSV | yes | Must be parseable by clojure.data.csv (valid delimiters, encoding) | Job status "Failed" |
| `:id` (path param) | UUID | yes | Must be a valid UUID format | 400 or 404 depending on context |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| High | Worker crash mid-processing leaves job in "Processing" forever | Implement a timeout or heartbeat mechanism; job record created before channel put ensures recoverability |
| Medium | Large CSV file exhausts memory if read entirely into memory | Stream CSV rows via clojure.data.csv lazy sequence; process one row at a time in go-loop |
| Medium | core.async channel buffer fills up under high concurrent load | Use a bounded buffer with backpressure; reject uploads when queue is full with 503 |
| Low | File upload size exceeds server limits | Configure Ring middleware with max file size; return 400 for oversized uploads |
| Low | Race condition between job creation and channel put | Create DB record first (before channel put); if channel put fails, mark job as Failed |

## 10. Out of Scope

- SSE progress streaming (GET /api/imports/:id/progress) --- deferred to v2
- Dry-run preview of import results before committing
- Row-level validation logic (handled by US-006)
- Import error reporting and pagination (handled by US-007)
- File size limits configuration UI
- Import job cancellation
- Import history listing (GET /api/imports without :id)

## 11. Notes

- The worker uses `core.async/go-loop` to process rows one at a time from the channel. This is a deliberate design choice: it keeps backpressure simple, avoids concurrent DB writes per job, and makes row-order deterministic for duplicate-SKU-within-file detection (US-006).
- Ring async/streaming-body is configured at this stage to prepare the response infrastructure for future SSE support. US-005 does NOT implement the SSE endpoint itself.
- The job record is always created in the database BEFORE the work is placed on the core.async channel. This ensures that if the application crashes between these two operations, the job exists in "Pending" state and can be recovered or marked as "Failed" on restart.

## 12. Related Documents

- [EP02 --- CSV Import](../epics/EP02-csv-import.md)
- [API Contract --- Section 4: CSV Import API](../architecture/api-contract.md)
- [Data Model --- csv_import_jobs, import_errors](../architecture/data-model.md)
- [Domain Glossary --- CsvImportJob, ImportError](../domain-glossary.md)
- [Tech Stack](../architecture/tech-stack.md)
- [US-001 --- Project Scaffolding](./US-001-project-scaffolding.md) (dependency)
- [US-002 --- Create Product with Validation & Sanitization](./US-002-create-product.md) (dependency)
- [US-006 --- CSV Row Validation](./US-006-csv-row-validation.md) (downstream)
- [US-007 --- Import Results & Error Reporting](./US-007-import-results-reporting.md) (downstream)

## 13. Handoff Files

TBD

## 14. Change Log

| Date | Author | Change |
| ---- | ------ | ------ |
| 2026-07-27 | Refinement Agent | Initial draft |
