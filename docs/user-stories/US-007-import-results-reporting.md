> [INDEX](../INDEX.md) / [User Stories](./) / US-007 --- Import Results & Error Reporting

# US-007 --- Import Results & Error Reporting

## 1. Metadata

| Field    | Value                           |
| -------- | ------------------------------- |
| Epic     | EP02 --- CSV Import             |
| Priority | Must Have                       |
| Status | Ready                           |

## 2. Story

**As a** Catalog Manager,
**I want** a summary report after import and a paginated list of errors with specific reasons,
**so that** I can act on rejected data.

## 3. Definition of Ready

- [x] Story follows the INVEST criteria
- [x] Acceptance criteria are testable and unambiguous
- [x] Dependencies identified (US-005 processing pipeline, US-006 row validation)
- [x] API contract reviewed ([API Contract](../architecture/api-contract.md) sections 4.3, 4.4)
- [x] Data model reviewed ([Data Model](../architecture/data-model.md) sections 2.6, 2.7)
- [x] Paging envelope contract reviewed ([API Contract](../architecture/api-contract.md) section 1)
- [x] Domain glossary terms aligned (CsvImportJob, ImportError, ImportRowReference)
- [x] Out of scope items explicitly listed
- [x] Test plan covers every acceptance criterion

## 4. Acceptance Criteria

### AC-007.1: GET /api/imports/:id returns summary with counts

**Given** a CSV import job exists with a known `job_id` and has been fully or partially processed
**When** the Catalog Manager sends `GET /api/imports/:id`
**Then** the response status is `200 OK`
**And** the response body contains `status`, `total_rows`, `accepted_rows`, `rejected_rows`
**And** `total_rows`, `accepted_rows`, and `rejected_rows` are integers reflecting the current processing state

### AC-007.2: Status distinguishes Completed vs CompletedWithErrors vs Failed

**Given** a CSV import job has finished processing
**When** the Catalog Manager sends `GET /api/imports/:id`
**Then** the `status` field is one of:
- `"Completed"` --- all rows were accepted, zero rejected
- `"CompletedWithErrors"` --- processing finished but one or more rows were rejected
- `"Failed"` --- the file itself was unreadable or structurally unparseable

**And** the status value matches the conditions exactly (e.g., a job with 0 rejected rows is never `"CompletedWithErrors"`)

### AC-007.3: GET /api/imports/:id/errors returns paginated errors

**Given** a CSV import job exists and has one or more `import_errors` records
**When** the Catalog Manager sends `GET /api/imports/:id/errors`
**Then** the response status is `200 OK`
**And** the response body uses the standard paging envelope: `items`, `paging.page`, `paging.perPage`, `paging.total`, `paging.prev`, `paging.next`
**And** `paging.perPage` defaults to `20` when not specified
**And** `paging.page` defaults to `1` when not specified

### AC-007.4: Each error includes row_number, field_name, error_reason, raw_row_data

**Given** a CSV import job has rejected rows
**When** the Catalog Manager retrieves errors via `GET /api/imports/:id/errors`
**Then** each item in the `items` array contains:
- `row_number` --- 1-indexed integer, header row included in the count (matches what a human sees in a spreadsheet)
- `field_name` --- string identifying the specific field that failed, or `null` for row-level errors (e.g., empty row, though empty rows are skipped per US-006 and do not appear here)
- `error_reason` --- human-readable, actionable description of the failure
- `raw_row_data` --- the original CSV row as a string

### AC-007.5: raw_row_data is sanitized for display

**Given** a rejected CSV row contained a script tag, event handler, or other executable content in any field
**When** the error is returned via `GET /api/imports/:id/errors`
**Then** the `raw_row_data` field is sanitized so that no executable content can be rendered by a browser
**And** sanitization preserves the data's diagnostic value (the Catalog Manager can still identify which row and what the original content was)

### AC-007.6: Error responses never leak internal details

**Given** any error occurs during error retrieval (database failure, unexpected exception)
**When** the error response is returned to the client
**Then** the response body never contains stack traces, exception class names, raw SQL fragments, file system paths, or internal hostnames
**And** the `error.message` is a safe, human-readable description

### AC-007.7: Errors paginated with standard paging envelope

**Given** a CSV import job has more errors than the `perPage` limit
**When** the Catalog Manager sends `GET /api/imports/:id/errors?page=1&perPage=5`
**Then** the response contains at most 5 items
**And** `paging.total` reflects the total number of errors for this job
**And** `paging.next` contains the URL for page 2 (e.g., `/api/imports/:id/errors?page=2&perPage=5`)
**And** `paging.prev` is `null` on page 1
**And** on the last page, `paging.next` is `null` and `paging.prev` points to the previous page
**And** requesting a page beyond the last page returns `200 OK` with empty `items` array and correct `paging.total`

### AC-007.8: Non-existent job returns 404

**Given** no CSV import job exists with the requested ID
**When** the Catalog Manager sends `GET /api/imports/:id` or `GET /api/imports/:id/errors`
**Then** the response status is `404 Not Found`
**And** the response body contains `error.code` equal to `"NOT_FOUND"` and `error.message` equal to `"Import job not found"`

## 5. Definition of Done

- [ ] `GET /api/imports/:id` returns summary with status and counts
- [ ] `GET /api/imports/:id/errors` returns paginated errors with standard paging envelope
- [ ] Each error item includes row_number, field_name, error_reason, raw_row_data
- [ ] raw_row_data is sanitized for display (no executable content)
- [ ] Status correctly distinguishes Completed / CompletedWithErrors / Failed
- [ ] 404 returned for non-existent job IDs on both endpoints
- [ ] No stack traces, SQL, or file paths leak in error responses
- [ ] Paging envelope preserves query params in prev/next URLs
- [ ] All acceptance criteria pass automated tests
- [ ] Integration tests run against PostgreSQL (Testcontainers)
- [ ] Code reviewed and merged

## 6. Deliverables

### Files to Create

| File | Purpose |
| ---- | ------- |
| `src/ecommerce/import/error_repository.clj` | ImportError queries: paginated list by job_id, count by job_id |
| `test/ecommerce/import/error_reporting_integration_test.clj` | Integration tests for error listing, pagination, sanitization, and 404 handling |

### Files to Modify

| File | Change |
| ---- | ------ |
| `src/ecommerce/import/handler.clj` | Add `GET /api/imports/:id/errors` route handler with pagination support |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `get-job-summary-returns-counts` | AC-007.1 | GET /api/imports/:id returns status, total_rows, accepted_rows, rejected_rows |
| `get-job-summary-counts-accurate` | AC-007.1 | Counts match actual import_errors and products records in DB |
| `status-completed-when-zero-errors` | AC-007.2 | Job with all rows accepted has status "Completed" |
| `status-completed-with-errors-when-some-rejected` | AC-007.2 | Job with mix of accepted/rejected has status "CompletedWithErrors" |
| `status-failed-when-unparseable` | AC-007.2 | Job with unparseable file has status "Failed" |
| `status-never-misclassified` | AC-007.2 | Job with 0 rejected is never "CompletedWithErrors"; job with errors is never "Completed" |
| `errors-paginated-default-page-size` | AC-007.3 | GET /api/imports/:id/errors without params returns page=1, perPage=20 |
| `errors-paging-envelope-shape` | AC-007.3 | Response has items, paging.page, paging.perPage, paging.total, paging.prev, paging.next |
| `error-item-has-row-number` | AC-007.4 | Each error item has row_number as 1-indexed integer |
| `error-item-has-field-name` | AC-007.4 | Each error item has field_name (string or null for row-level errors) |
| `error-item-has-error-reason` | AC-007.4 | Each error item has error_reason as human-readable string |
| `error-item-has-raw-row-data` | AC-007.4 | Each error item has raw_row_data as string |
| `row-number-includes-header` | AC-007.4 | Row number matches 1-indexed position including header (row 2 = first data row) |
| `raw-row-data-xss-sanitized` | AC-007.5 | raw_row_data containing `<script>` is sanitized; no executable content in response |
| `raw-row-data-preserves-diagnostic-value` | AC-007.5 | Sanitized raw_row_data still identifies the original row content |
| `error-response-no-stack-trace` | AC-007.6 | Forced internal error response contains no stack trace |
| `error-response-no-sql-fragments` | AC-007.6 | Error responses contain no raw SQL or query plans |
| `error-response-no-file-paths` | AC-007.6 | Error responses contain no file system paths |
| `errors-pagination-next-prev` | AC-007.7 | Page 1 has prev=null, next=/api/imports/:id/errors?page=2&perPage=5 |
| `errors-pagination-last-page` | AC-007.7 | Last page has next=null, prev points to previous page |
| `errors-pagination-beyond-last` | AC-007.7 | Page beyond last returns 200 with empty items and correct total |
| `errors-pagination-custom-per-page` | AC-007.7 | ?perPage=5 returns at most 5 items per page |
| `nonexistent-job-summary-404` | AC-007.8 | GET /api/imports/:nonexistent returns 404 NOT_FOUND |
| `nonexistent-job-errors-404` | AC-007.8 | GET /api/imports/:nonexistent/errors returns 404 NOT_FOUND |

## 8. Validation Rules

| Field | Type | Required | Constraint | Error Response |
| ----- | ---- | -------- | ---------- | -------------- |
| `:id` (path param) | UUID | yes | Must be a valid UUID referencing an existing csv_import_jobs record | 404: "Import job not found" |
| `page` (query param) | integer | no | >= 1; defaults to 1 | 400: VALIDATION_ERROR if page <= 0 |
| `perPage` (query param) | integer | no | 1-100; defaults to 20 | 400: VALIDATION_ERROR if outside range |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| Medium | raw_row_data sanitization strips too much, losing diagnostic value | Use HTML entity encoding (not removal) for angle brackets; preserve the original text structure |
| Medium | Large number of errors (thousands) causes slow pagination queries | Index `import_errors` on `(csv_import_job_id, row_number)` for efficient ordered pagination |
| Low | Paging envelope prev/next URLs do not preserve all query params | Build URLs programmatically from the current request's query params; test with multiple params |
| Low | Concurrent reads of job status while worker is still processing show inconsistent counts | Counts are updated atomically per row; reads reflect a consistent snapshot at query time |

## 10. Out of Scope

- CSV export or download of error rows
- SSE progress streaming (deferred to v2)
- Re-import functionality (upload corrected rows only)
- Bulk error dismissal or acknowledgment
- Error filtering or search within a job's errors
- Email notification when import completes

## 11. Notes

- The `raw_row_data` field stores the original CSV row exactly as parsed. When returned via the API, it is sanitized for display by encoding HTML special characters (e.g., `<` becomes `&lt;`). This preserves the diagnostic value while preventing XSS if the response is rendered in a browser.
- Row numbers are 1-indexed with the header row counted (matching spreadsheet conventions). The first data row is row 2.
- Empty rows that were skipped during processing (per US-006 AC-006.7) do NOT appear in the errors list --- they were never recorded as ImportError entries.

## 12. Related Documents

- [EP02 --- CSV Import](../epics/EP02-csv-import.md)
- [API Contract --- Section 4: CSV Import API](../architecture/api-contract.md)
- [API Contract --- Section 1: Paging Envelope](../architecture/api-contract.md)
- [Data Model --- csv_import_jobs, import_errors](../architecture/data-model.md)
- [Domain Glossary --- CsvImportJob, ImportError, ImportRowReference](../domain-glossary.md)
- [US-005 --- CSV Upload & Background Processing Pipeline](./US-005-csv-upload-processing.md) (dependency)
- [US-006 --- CSV Row Validation](./US-006-csv-row-validation.md) (dependency)

## 13. Handoff Files

TBD

## 14. Change Log

| Date | Author | Change |
| ---- | ------ | ------ |
| 2026-07-27 | Refinement Agent | Initial draft |
