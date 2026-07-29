> [INDEX](../INDEX.md) / [EP02](../epics/EP02-csv-import.md) / US-019

# US-019 --- Import Status Accuracy & Upload UX

## Metadata

| Field | Value |
|-------|-------|
| Epic | EP02 --- CSV Import |
| Priority | Must Have |
| Estimation | S |
| Status | Ready |

## Story

As a user importing products via CSV, I want the import status to accurately
reflect what happened and the upload interface to clearly indicate where to
click, so that I can trust the system's feedback and complete the import flow
without confusion.

## Bug Description

Two issues identified during review:

1. **Backend status semantics**: `worker.clj` line 137 sets status to `"Failed"`
   when zero rows are accepted but the process completed normally (all rows
   rejected by validation). `"Failed"` should mean the process crashed or the CSV
   was unparseable --- not that all rows had validation errors. Correct status is
   `"CompletedWithErrors"`.

2. **Frontend display gaps**:
   - `import-results.html` only shows row stats (total/accepted/rejected) for
     `Completed` and `CompletedWithErrors`, hiding useful information when status
     is `Failed` (a real parse error still records rejected_rows)
   - `import-upload.html` uses a bare `<input type="file">` with no visual
     affordance --- users cannot tell where to click to select a file

## Acceptance Criteria

- [ ] **AC-019.1: All-rejected imports get CompletedWithErrors**
  - **Given** a CSV where every data row fails validation
  - **When** the import job finishes processing
  - **Then** the job status is `"CompletedWithErrors"`, not `"Failed"`

- [ ] **AC-019.2: Failed status reserved for real failures**
  - **Given** a CSV with invalid structure (wrong headers, unparseable format)
  - **When** the import job attempts processing
  - **Then** the job status is `"Failed"` and a PARSE_ERROR is recorded

- [ ] **AC-019.3: Stats visible for all terminal statuses**
  - **Given** an import job with any terminal status (Completed, CompletedWithErrors, Failed)
  - **When** the user views Import Results
  - **Then** the row stats (total/accepted/rejected) are displayed whenever the
    values are non-zero

- [ ] **AC-019.4: File input has clear visual affordance**
  - **Given** the Import Products page
  - **When** the user views the upload area
  - **Then** there is a visually distinct drop zone with icon, instructional text,
    and a clear click target

## Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Backend tests updated for new status logic
- [ ] Frontend tests updated for display changes
- [ ] No regressions in existing test suite
- [ ] INDEX.md updated

## Handoff Files

- [T-021](../subtasks/ep02/T-021-import-status-fix.md) --- Backend status semantics
- [T-022](../subtasks/ep02/T-022-import-upload-ux.md) --- Frontend display + upload UX
