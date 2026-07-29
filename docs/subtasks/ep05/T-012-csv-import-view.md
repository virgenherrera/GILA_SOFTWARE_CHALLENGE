# T-012 --- CSV Import View

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-012 |
| Batch | 2 |
| Epic | EP05 --- User Interface |
| Story | [US-012](../../user-stories/US-012-csv-import-view.md) |
| Persona | Administrator |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-005, T-006, T-007 |

## Objective

Implement Angular 22 CSV import interface with file upload triggering POST /api/imports (multipart), polling-based status tracking via GET /api/imports/:id, results summary with visual status distinction, and a paginated error table showing row-level import failures.

## Pre-conditions

- [ ] T-005 CSV upload/processing API functional
- [ ] T-006 CSV row validation API functional
- [ ] T-007 import results reporting API functional
- [ ] Angular 22 scaffold exists from T-001
- [ ] Backend running and accessible via Docker Compose

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/user-stories/US-012-csv-import-view.md | all | Acceptance criteria for import UI |
| docs/architecture/api-contract.md | Section 4 (CSV Import API) | Import API shapes, multipart upload, status enum |
| docs/architecture/tech-stack.md | Section 3 (Frontend), Section 7 (CSV Import Pipeline) | Angular 22 conventions, polling approach |
| frontend/src/app/app.routes.ts | all | Existing routes to extend |
| frontend/angular.json | all | Build/test configuration |
| docs/architecture/error-handling.md | Section 2 (Exception → Error Code Mapping) | API error response shape for import error display |
| docs/architecture/security-guidelines.md | Section 6 (Input Security --- XSS) | Display of raw_row_data must be safely escaped |
| docs/architecture/tdd-workflow.md | Section 5 (Frontend TDD) | TDD process for Angular components |
| docs/architecture/pnpm-config.md | Section 6 (Commands) | pnpm configuration reference |
| docs/architecture/testing-strategy.md | Section 2 (Test Pyramid), Section 3 (What to Test per Epic --- EP05), Section 5 (Security Test Cases) | Test pyramid, CSV import test matrix, security test cases |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| frontend/src/app/imports/import-upload/import-upload.component.ts | Container: file input + upload trigger |
| frontend/src/app/imports/import-upload/import-upload.component.html | Template for upload form |
| frontend/src/app/imports/import-upload/import-upload.component.css | Styles for upload form |
| frontend/src/app/imports/import-upload/import-upload.component.spec.ts | Tests for upload component |
| frontend/src/app/imports/import-results/import-results.component.ts | Container: polling status + results summary |
| frontend/src/app/imports/import-results/import-results.component.html | Template for results display |
| frontend/src/app/imports/import-results/import-results.component.css | Styles for results display |
| frontend/src/app/imports/import-results/import-results.component.spec.ts | Tests for results component |
| frontend/src/app/imports/import-errors/import-errors.component.ts | Presentational: paginated error table |
| frontend/src/app/imports/import-errors/import-errors.component.html | Template for error table |
| frontend/src/app/imports/import-errors/import-errors.component.css | Styles for error table |
| frontend/src/app/imports/import-errors/import-errors.component.spec.ts | Tests for error table component |
| frontend/src/app/imports/import.service.ts | HTTP service: upload, poll status, fetch errors |
| frontend/src/app/imports/import.routes.ts | Import feature routes (upload, results) |
| frontend/src/app/imports/import.service.spec.ts | Tests for import service |

### Files to Modify

| File | Change |
|------|--------|
| frontend/src/app/app.routes.ts | Add lazy-loaded import routes |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep05/T-012-csv-import-view.md` | EXE | exit 0 |
| 2 | Frontend tests pass | `docker compose run --rm frontend pnpm exec vitest run` | EXE | exit 0 |
| 3 | Frontend lint | `docker compose run --rm frontend pnpm exec ng lint` | EXE | exit 0 |
| 4 | File input accepts CSV | File input only allows .csv files | MANUAL | accept=".csv" attribute |
| 5 | Upload triggers POST | Select file, click upload -> POST /api/imports multipart | MANUAL | Request sent, import ID returned |
| 6 | Polls status | After upload -> component polls GET /api/imports/:id | MANUAL | Repeated requests at 1-2s interval |
| 7 | Pending state | Import just created -> "Pending" status shown | MANUAL | Visual pending indicator |
| 8 | Processing state | Import being processed -> "Processing" status shown | MANUAL | Visual processing indicator |
| 9 | Done state | Import complete -> final results summary shown | MANUAL | Polling stops, summary displayed |
| 10 | Results summary | Shows total, created, updated, failed counts | MANUAL | All counts visible and correct |
| 11 | Visual distinction | Success green, warnings amber, errors red | MANUAL | Color coding present |
| 12 | Error table paginated | Many errors -> paginated table with navigation | MANUAL | Pagination controls work |
| 13 | Error row data safe | Error data with HTML chars -> rendered as text (Angular encoding) | MANUAL | No raw HTML injection |
| 14 | No errors message | Import with 0 errors -> "All rows imported successfully" | MANUAL | Success message shown |
| 15 | Navigate to products | After import -> link/button to product list | MANUAL | Navigation works |
| 16 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: SSE real-time progress (deferred to v2) --- polling GET /api/imports/:id (backed by T-005/T-007) is the chosen v1 mechanism per api-contract.md §4
- NOT in scope: Drag-and-drop file upload --- a standard file input satisfies AC-012's upload requirement; drag-and-drop is a UX enhancement, not a stated criterion
- NOT in scope: Re-import failed rows --- the documented recovery path is re-uploading a full corrected file (T-005/T-006), not a per-row retry UI
- NOT in scope: CSV download of error report --- the paginated error table already surfaces every field needed to fix the source file; export is unrequested
- NOT in scope: Multiple simultaneous uploads UI --- no acceptance criterion requires concurrent uploads from a single user session; one active import at a time is sufficient
- NOT in scope: Upload progress bar (file transfer %) --- distinct from job-processing status (Pending/Processing/Completed); no acceptance criterion requires transfer-percentage feedback

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Use SSE for status updates | Deferred to v2, adds unnecessary complexity | Poll GET /api/imports/:id at 1-2 second interval |
| Poll too frequently (< 1s) | Unnecessary server load, potential rate limiting | Use 1-2 second polling interval with clearInterval on completion |
| Display raw HTML from error data | XSS vulnerability if error contains user input | Rely on Angular's built-in template encoding (default behavior) |
| Use Zone.js | Contradicts zoneless requirement | Use signals, computed(), resource() for reactivity |
| Keep polling after completion | Wasted requests, potential memory leak | Stop polling when status is done or failed |
| Use NgModule | Angular 22 standalone-only architecture | Use standalone components with imports array |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/imports/
git checkout -- frontend/src/app/app.routes.ts
```

This removes all import view components, service, routes, and restores the root routes.

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
- Pipeline: install -> build -> lint -> test:unit -> test:integration -> test:e2e
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] frontend/src/app/imports/import-upload/ (component + spec)
- [ ] frontend/src/app/imports/import-results/ (component + spec)
- [ ] frontend/src/app/imports/import-errors/ (component + spec)
- [ ] frontend/src/app/imports/import.service.ts
- [ ] frontend/src/app/imports/import.service.spec.ts
- [ ] frontend/src/app/imports/import.routes.ts
- [ ] frontend/src/app/app.routes.ts (modified)

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Frontend tests pass
- [ ] Gate 3: Frontend lint passes
- [ ] Gate 4: File input accepts CSV
- [ ] Gate 5: Upload triggers POST multipart
- [ ] Gate 6: Polls status at interval
- [ ] Gate 7: Pending state displayed
- [ ] Gate 8: Processing state displayed
- [ ] Gate 9: Done state with results
- [ ] Gate 10: Results summary counts
- [ ] Gate 11: Visual distinction (green/amber/red)
- [ ] Gate 12: Error table paginated
- [ ] Gate 13: Error row data safe (XSS)
- [ ] Gate 14: No errors success message
- [ ] Gate 15: Navigate to products
- [ ] Gate 16: No side effects
