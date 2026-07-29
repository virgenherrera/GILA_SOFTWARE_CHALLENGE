# T-022 --- Import UI: Stats Display & Upload UX

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-022 |
| Epic | EP02 --- CSV Import / EP05 --- User Interface |
| Story | [US-019](../../user-stories/US-019-import-status-ux.md) |
| Persona | Angular Frontend UX Specialist |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | none (can run in parallel with T-021) |

## Objective

Fix two import UI issues: (1) show row stats and error table for ALL terminal
statuses including "Failed", not just Completed/CompletedWithErrors; (2) replace
the bare `<input type="file">` with a styled drop zone that clearly communicates
where to click. PASS when stats render for any terminal status with non-zero
counts, the file input has a visible drop zone with icon and instructional text,
and all tests are green; FAIL otherwise.

## Pre-conditions

- [ ] `import-results.html` line 30 currently restricts stats to
  `Completed || CompletedWithErrors` only (confirmed by reading the file)
- [ ] `import-upload.html` line 6-12 is a bare `<input type="file">` with no
  visual styling (confirmed by reading the file)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/imports/import-results/import-results.html` | 1-60 (full) | Template to fix: expand stats condition to include "Failed" |
| `frontend/src/app/imports/import-results/import-results.ts` | 1-80 (full) | Component logic --- may need a computed for "hasStats" |
| `frontend/src/app/imports/import-results/import-results.spec.ts` | full | Tests to update for new display logic |
| `frontend/src/app/imports/import-upload/import-upload.html` | 1-38 (full) | Template to restyle: replace bare input with drop zone |
| `frontend/src/app/imports/import-upload/import-upload.ts` | full | Component logic --- may need onDragOver/onDrop handlers |
| `frontend/src/app/imports/import-upload/import-upload.spec.ts` | full | Tests to update for new upload UX |
| `frontend/src/app/imports/import-upload/import-upload.css` | full | Currently empty --- add drop zone styles here |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/imports/import-results/import-results.html` | Line 30: expand condition to show stats for any terminal status where total_rows > 0 or rejected_rows > 0; line 47: keep "Failed" message but show it ABOVE the stats, not instead of them |
| `frontend/src/app/imports/import-results/import-results.spec.ts` | Add test: Failed status with non-zero rejected_rows renders stats grid and error table |
| `frontend/src/app/imports/import-upload/import-upload.html` | Replace bare `<input type="file">` with a hidden input + styled drop zone div: dashed border, upload icon (inline SVG or CSS), "Click to select a CSV file or drag and drop" text, file name display on selection |
| `frontend/src/app/imports/import-upload/import-upload.ts` | Add drop zone click handler (triggers hidden input), optional dragover/drop handlers for drag-and-drop |
| `frontend/src/app/imports/import-upload/import-upload.css` | Drop zone styles: dashed border, hover state, drag-active state |
| `frontend/src/app/imports/import-upload/import-upload.spec.ts` | Add test: clicking drop zone triggers file input; drop zone renders with instructional text |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep02/T-022-import-upload-ux.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | Stats render for Failed | Test asserts: given a job with status "Failed" and rejected_rows > 0, the stats grid (total/accepted/rejected) is rendered | EXE (unit test) |
| 4 | Drop zone has affordance | `rg 'Click to select\|drag.*drop\|drop.*zone' frontend/src/app/imports/import-upload/import-upload.html` | matches at least one |
| 5 | No bare file input visible | The `<input type="file">` element has `display: none` or `class="hidden"` or equivalent | DOC |
| 6 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: Backend status logic --- covered by T-021
- NOT in scope: Drag-and-drop from OS file manager is a nice-to-have, not required;
  the MUST is a clear click target. If drag-and-drop is trivial to add alongside the
  click handler, include it; if not, skip it
- NOT in scope: Restyling other pages (product-list, search, cart) --- only import
  upload and import results
- NOT in scope: The error table component (`import-errors.ts`) itself --- it already
  renders correctly; this task only ensures it appears for "Failed" status

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Use a third-party file upload library | Adds a dependency for something achievable with 20 lines of CSS + a click handler | Style the native input with CSS (hide it, forward clicks from the drop zone div) |
| Remove the "Failed" error message entirely | Real parse failures DO need an error message | Keep the message for "Failed" status, but show stats alongside it when they exist |
| Make the drop zone only work with drag-and-drop | Most users click, not drag | The primary action is click-to-select; drag-and-drop is optional enhancement |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/imports/import-results/
git checkout -- frontend/src/app/imports/import-upload/
```

## Compact Rules

### PROJECT-TEST

- AXIOM-ECHO: every code change runs the Echo System before commit
- All tests must pass before any commit
- TDD Cycle (Red/Green/Refactor) is mandatory
- Breaking an existing test is a blocking issue

### PROJECT-TDD

- Red: write test → run → MUST fail → verify failure is assertion not syntax
- Green: write MINIMUM code → run → MUST pass → full suite → no regressions
- Refactor: apply SOLID/KISS/DRY/YAGNI → after EACH refactor: full suite → if fail: REVERT

### PROJECT-ANTI-DRIFT

- AXIOM-HANDOFF: no code without an approved handoff file
- Scope is defined by the handoff -- work outside boundaries is a violation
- Dead code and unused dependencies MUST be removed

## Status Protocol

```text
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables

- [ ] `import-results.html` (stats visible for all terminal statuses)
- [ ] `import-results.spec.ts` (Failed + stats test)
- [ ] `import-upload.html` (styled drop zone)
- [ ] `import-upload.ts` (click/drop handlers)
- [ ] `import-upload.css` (drop zone styles)
- [ ] `import-upload.spec.ts` (drop zone tests)

### Quality Gates

- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Frontend tests pass
- [ ] Gate 3: Stats render for Failed status
- [ ] Gate 4: Drop zone has affordance text
- [ ] Gate 5: No bare file input visible
- [ ] Gate 6: No side effects
