> [INDEX](../INDEX.md) / [User Stories](./) / US-012 --- CSV Import View

# US-012 --- CSV Import View

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP05 --- User Interface](../epics/EP05-user-interface.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

As an Administrator, I want a CSV import interface with file upload and a results/errors summary, so that I can run a bulk import and understand what succeeded, what failed, and why.

## 3. Definition of Ready

- [x] Domain entity contract frozen
- [x] Interface or API contract frozen
- [x] Input validation rules enumerated with exact boundaries
- [x] Edge cases identified with boundary behavior defined
- [x] Dependencies identified and resolved or deferred
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] API contract endpoints touched by this story are defined

## 4. Acceptance Criteria

- [ ] **AC-012.1: Upload form accepts CSV files**
  - **Given** the Administrator navigates to the CSV import page
  - **When** the page loads
  - **Then** a file input is displayed that accepts only `.csv` files (via `accept=".csv"` attribute)
  - **And** a submit/upload button is present
  - **And** the submit button is disabled until a file is selected

- [ ] **AC-012.2: Upload triggers import and shows job ID**
  - **Given** the Administrator has selected a valid `.csv` file
  - **When** the Administrator clicks the upload/submit button
  - **Then** the file is sent via `POST /api/imports` as `multipart/form-data`
  - **And** on `202 Accepted`, the UI displays "Import started" along with the `job_id` from the response
  - **And** the UI transitions to the results/polling view automatically

- [ ] **AC-012.3: Polls for import status until completion**
  - **Given** the import job has been created with `job_id`
  - **When** the UI transitions to the results view
  - **Then** the UI polls `GET /api/imports/:id` at a regular interval (e.g., every 2 seconds)
  - **And** polling continues while `status` is `Pending` or `Processing`
  - **And** polling stops when `status` is `Completed`, `CompletedWithErrors`, or `Failed`

- [ ] **AC-012.4: Processing state shown with progress indication**
  - **Given** the import job is in `Pending` or `Processing` status
  - **When** the UI polls and receives the current status
  - **Then** the current status is displayed to the user: "Pending" (waiting to start), "Processing" (in progress)
  - **And** a visual indicator (spinner or progress bar) shows that work is in progress
  - **And** the indicator updates with each poll response

- [ ] **AC-012.5: Results summary shows row counts**
  - **Given** the import job has completed (any terminal status)
  - **When** the final poll response is received
  - **Then** the UI displays: `total_rows` (total data rows processed), `accepted_rows` (successfully imported), and `rejected_rows` (failed validation)
  - **And** the counts are clearly labeled

- [ ] **AC-012.6: Visual distinction between completion statuses**
  - **Given** the import job has reached a terminal status
  - **When** the results are displayed
  - **Then** `Completed` status (all rows accepted) uses a success visual style (e.g., green indicator)
  - **And** `CompletedWithErrors` status (some rows rejected) uses a warning visual style (e.g., yellow/amber indicator)
  - **And** `Failed` status (job-level failure) uses an error visual style (e.g., red indicator)

- [ ] **AC-012.7: Errors table shows paginated import errors**
  - **Given** the import job has `rejected_rows` > 0 (status `CompletedWithErrors`)
  - **When** the results view is displayed
  - **Then** an errors table is shown with columns: row number, field name, and error reason
  - **And** the errors are loaded via `GET /api/imports/:id/errors` with pagination
  - **And** pagination controls are shown if errors exceed one page
  - **And** each error row shows: `row_number`, `field_name` (or "Row-level error" if `null`), and `error_reason`

- [ ] **AC-012.8: Raw row data displayed safely**
  - **Given** an import error has `raw_row_data` containing potentially dangerous content (e.g., `"<script>alert('xss')</script>,SKU-001,,Cat,$10,5,0.5"`)
  - **When** the error row is displayed in the errors table
  - **Then** the raw row data is rendered as literal text, not as HTML
  - **And** no script content is executed (Angular's automatic output encoding prevents XSS)

- [ ] **AC-012.9: Upload failure shows error message**
  - **Given** the Administrator attempts to upload a file
  - **When** the upload fails (network error, `400 Bad Request` for invalid file, server error)
  - **Then** a user-facing error message is displayed (e.g., "Upload failed. Please ensure the file is a valid CSV." or "An error occurred. Please try again.")
  - **And** the raw API error response is never shown to the user
  - **And** the file input is reset so the user can try again

- [ ] **AC-012.10: All rows imported successfully message**
  - **Given** the import job completed with `status` = `Completed` and `rejected_rows` = `0`
  - **When** the results view is displayed
  - **Then** the message "All rows imported successfully" is shown
  - **And** the errors table is not displayed (or is hidden)

- [ ] **AC-012.11: Navigation back to product list**
  - **Given** the import results are displayed (any terminal status)
  - **When** the user wants to return to the catalog
  - **Then** a link or button "Back to Products" is visible
  - **And** clicking it navigates to the product list page

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Component tests green for upload, results, and errors components
- [ ] No regressions in existing test suite
- [ ] Error responses displayed as user-facing messages
- [ ] XSS defense verified with script-containing raw row data
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `frontend/src/app/imports/import-upload/import-upload.component.ts` | Container component: file input with `.csv` filter, upload button, sends `POST /api/imports` as multipart, shows "Import started" with job ID, transitions to results view; standalone, zoneless, uses signals |
| `frontend/src/app/imports/import-upload/import-upload.component.html` | Template: file input, upload button (disabled until file selected), loading state during upload, error message display |
| `frontend/src/app/imports/import-upload/import-upload.component.css` | Styles for upload form and states |
| `frontend/src/app/imports/import-results/import-results.component.ts` | Container component: polls `GET /api/imports/:id` until terminal status, displays processing state with spinner, shows results summary (total/accepted/rejected rows), visual status distinction (green/amber/red); standalone, zoneless |
| `frontend/src/app/imports/import-results/import-results.component.html` | Template: status indicator, progress spinner, row counts summary, status-specific styling, "Back to Products" link, errors section |
| `frontend/src/app/imports/import-results/import-results.component.css` | Styles for results summary, status indicators, and progress states |
| `frontend/src/app/imports/import-errors/import-errors.component.ts` | Presentational component: receives paginated errors via `input()`, renders errors table with row_number, field_name, error_reason columns; emits page change events via `output()`; standalone |
| `frontend/src/app/imports/import-errors/import-errors.component.html` | Template: errors table with pagination controls, "All rows imported successfully" message when no errors |
| `frontend/src/app/imports/import-errors/import-errors.component.css` | Styles for errors table and pagination |
| `frontend/src/app/imports/import.service.ts` | Service: `HttpClient` calls to `POST /api/imports` (multipart), `GET /api/imports/:id` (status), `GET /api/imports/:id/errors` (paginated errors); returns typed observables |
| `frontend/src/app/imports/import.routes.ts` | Feature routes: `/imports/new` (upload), `/imports/:id` (results); lazy-loaded |
| `frontend/src/app/imports/import-upload/import-upload.component.spec.ts` | Component tests: file input accepts `.csv`, submit disabled without file, upload calls API, error display on failure |
| `frontend/src/app/imports/import-results/import-results.component.spec.ts` | Component tests: polls until terminal status, displays correct status styling, shows row counts, navigates to products |
| `frontend/src/app/imports/import-errors/import-errors.component.spec.ts` | Component tests: renders error rows, pagination works, XSS content rendered as text, "All rows imported" message when no errors |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `frontend/src/app/app.routes.ts` | Add lazy-loaded route for imports feature: `{ path: 'imports', loadChildren: () => import('./imports/import.routes') }` |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `upload-form-accepts-csv-only` | AC-012.1 | File input has `accept=".csv"` attribute; submit button is disabled until a file is selected |
| `upload-sends-multipart-and-shows-job-id` | AC-012.2 | Selecting file and clicking upload sends `POST /api/imports` as `multipart/form-data`; on `202`, displays "Import started" with `job_id`; transitions to results view |
| `polls-until-terminal-status` | AC-012.3 | After upload, UI polls `GET /api/imports/:id`; polling continues while `Pending`/`Processing`; stops on `Completed`/`CompletedWithErrors`/`Failed` |
| `processing-state-shows-spinner` | AC-012.4 | While status is `Pending` or `Processing`, a spinner or progress indicator is visible; status label updates with each poll |
| `results-summary-shows-row-counts` | AC-012.5 | On terminal status, UI displays `total_rows`, `accepted_rows`, and `rejected_rows` with labels |
| `completed-status-shows-green-indicator` | AC-012.6 | `Completed` status renders with success visual style (green) |
| `completed-with-errors-shows-amber-indicator` | AC-012.6 | `CompletedWithErrors` status renders with warning visual style (amber/yellow) |
| `failed-status-shows-red-indicator` | AC-012.6 | `Failed` status renders with error visual style (red) |
| `errors-table-shows-paginated-errors` | AC-012.7 | Errors table displays `row_number`, `field_name`, `error_reason` columns; pagination controls appear when errors exceed one page |
| `errors-table-handles-null-field-name` | AC-012.7 | When `field_name` is `null`, the column shows "Row-level error" instead of blank |
| `raw-row-data-rendered-as-text` | AC-012.8 | Raw row data containing `<script>` tags is rendered as literal text; no script execution |
| `upload-failure-shows-user-error` | AC-012.9 | On upload failure (`400` or network error), a user-facing error message is shown; raw error is hidden; file input resets |
| `all-rows-imported-message-shown` | AC-012.10 | When `status` = `Completed` and `rejected_rows` = `0`, "All rows imported successfully" is shown; errors table is hidden |
| `back-to-products-link-navigates` | AC-012.11 | "Back to Products" link is visible on results page; clicking it navigates to `/products` |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `file` (upload input) | yes | file | Must be a `.csv` file; submitted as `multipart/form-data` | No file selected, non-CSV file (`.txt`, `.xlsx`) |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| MEDIUM | Polling interval too aggressive may overload server on large imports; too slow may frustrate users | Use a 2-second interval as default; consider exponential backoff if processing takes more than 30 seconds; keep polling logic in the service layer for easy adjustment |
| MEDIUM | Large error lists (thousands of rejected rows) may cause performance issues in the errors table | Use server-side pagination (`GET /api/imports/:id/errors?page=N&perPage=20`); never load all errors at once; virtual scrolling is out of scope but noted as a future optimization |
| LOW | File input `accept` attribute can be bypassed by the user (browser enforcement only) | Backend validates the file is a valid CSV; frontend `accept` is a convenience hint, not a security boundary |
| LOW | Polling continues if user navigates away, causing unnecessary API calls | Unsubscribe from polling observable on component destroy (`takeUntilDestroyed()` or `DestroyRef`) |

## 10. Out of Scope

- SSE real-time progress streaming (deferred to v2; MVP uses polling)
- Drag-and-drop file upload
- Re-importing failed rows (upload correction workflow)
- CSV download of errors for offline review
- Multiple simultaneous uploads UI
- Import history listing (past imports)
- File size validation or preview before upload
- Upload progress bar (HTTP upload progress, not import progress)

## 11. Notes

- SSE real-time progress (`GET /api/imports/:id/progress`) is DEFERRED to v2. The MVP uses polling on `GET /api/imports/:id` to track job status. The polling interval should be configurable in the service to allow easy migration to SSE later.
- All components follow Angular 22 conventions: zoneless, standalone, signals for state, `resource()` where applicable for async data.
- The Container-Presentational pattern is followed: `import-upload` and `import-results` are smart containers that inject `ImportService`; `import-errors` is a presentational component that receives data via `input()` and emits events via `output()`.
- Polling must be properly cleaned up when the component is destroyed to prevent memory leaks and unnecessary API calls. Use Angular's `DestroyRef` or `takeUntilDestroyed()`.
- The errors table always uses server-side pagination via the standard paging envelope from `GET /api/imports/:id/errors`.

## 12. Related Documents

- [API Contract --- CSV Import API](../architecture/api-contract.md#4-csv-import-api) --- endpoint shapes, job status values, error format
- [Data Model --- csv_import_jobs](../architecture/data-model.md#26-csv_import_jobs) --- job status state machine
- [Data Model --- import_errors](../architecture/data-model.md#27-import_errors) --- error record structure
- [EP05 --- User Interface](../epics/EP05-user-interface.md) --- parent epic
- [EP02 --- CSV Import](../epics/EP02-csv-import.md) --- backend import pipeline
- [Testing Strategy](../architecture/testing-strategy.md) --- component testing approach
- [US-001 --- Project Scaffolding](US-001-project-scaffolding.md) --- frontend scaffold dependency
- [US-005 --- CSV Upload](US-005-csv-upload.md) --- backend upload endpoint (must exist)
- [US-006 --- CSV Processing](US-006-csv-processing.md) --- backend processing pipeline (must exist)
- [US-007 --- Import Status](US-007-import-status.md) --- backend status/errors endpoints (must exist)

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
