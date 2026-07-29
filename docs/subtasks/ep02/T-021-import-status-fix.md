# T-021 --- Import Status Semantics Fix

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-021 |
| Epic | EP02 --- CSV Import |
| Story | [US-019](../../user-stories/US-019-import-status-ux.md) |
| Persona | Backend Bug Fix Specialist |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | none |

## Objective

Fix the import job status logic so that "Failed" is reserved for real process
failures (exceptions, unparseable CSV) and all-rows-rejected imports get
"CompletedWithErrors" instead. PASS when the status mapping is corrected, existing
pipeline integration tests are updated, and all backend tests are green; FAIL
otherwise.

## Pre-conditions

- [ ] `src/ecommerce/import/worker.clj` line 137 currently maps
  `(zero? (:accepted results))` to `"Failed"` (confirmed by reading the file)
- [ ] `test/ecommerce/import/pipeline_integration_test.clj` asserts `"Failed"`
  at lines 87, 232, 245 (some may need updating)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `src/ecommerce/import/worker.clj` | 70-146 | `process-job` function with the status logic to fix (line 137) and the parse error path (lines 76-89) |
| `test/ecommerce/import/pipeline_integration_test.clj` | full | Integration tests that assert "Failed" status --- must update assertions for all-rejected scenarios while keeping "Failed" for parse error scenarios |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `src/ecommerce/import/worker.clj` | Line 137: change `"Failed"` to `"CompletedWithErrors"` for the `(zero? (:accepted results))` case. The parse error path (line 89) and the catch block (line 165) remain `"Failed"` --- those are real failures |
| `test/ecommerce/import/pipeline_integration_test.clj` | Update test assertions at lines 87, 232, 245: tests for all-rows-rejected scenarios must assert `"CompletedWithErrors"` instead of `"Failed"`. Tests for parse errors or exceptions must still assert `"Failed"` |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep02/T-021-import-status-fix.md` | exit 0 |
| 2 | Backend tests pass | `DOCKER_CONFIG=/tmp/docker-config docker compose run --rm backend clojure -M:test` | exit 0 |
| 3 | Status logic correct | `rg '(zero? (:accepted results))' src/ecommerce/import/worker.clj` | followed by `"CompletedWithErrors"`, not `"Failed"` |
| 4 | Parse error still Failed | `rg '"Failed"' src/ecommerce/import/worker.clj` | appears only in the parse error path (line ~89) and the catch block (line ~165) |
| 5 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: Frontend display changes --- covered by T-022
- NOT in scope: Adding new import statuses (e.g., "AllRejected") --- use existing
  `CompletedWithErrors` which already has frontend support
- NOT in scope: Changing the parse error path (lines 76-89) or the exception catch
  block (lines 159-168) --- those correctly use "Failed" for real failures
- NOT in scope: Modifying any other status transitions (Pending, Processing)

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Remove "Failed" status entirely | Parse errors and unhandled exceptions need a distinct terminal status | Keep "Failed" for the two real-failure paths (parse error + catch block), only change the all-rejected case |
| Add a new status like "AllRejected" | Not in the existing enum, would require frontend changes, DB migration consideration | Use "CompletedWithErrors" which already exists and renders correctly |

## Rollback Guidance

```bash
git checkout -- src/ecommerce/import/worker.clj
git checkout -- test/ecommerce/import/pipeline_integration_test.clj
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

- [ ] `src/ecommerce/import/worker.clj` (status logic fixed)
- [ ] `test/ecommerce/import/pipeline_integration_test.clj` (assertions updated)

### Quality Gates

- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Backend tests pass
- [ ] Gate 3: Status logic correct
- [ ] Gate 4: Parse error still "Failed"
- [ ] Gate 5: No side effects
