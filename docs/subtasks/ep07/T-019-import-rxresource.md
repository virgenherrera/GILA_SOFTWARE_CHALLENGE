# T-019 --- Import rxResource Migration

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-019 |
| Batch | 3 |
| Epic | EP07 --- Angular 22 Modernization |
| Story | [US-018](../../user-stories/US-018-rxresource-migration.md) |
| Persona | Angular Signals/Resource Migration Specialist |
| Model Tier | standard |
| Priority | Should Have |
| Depends On | US-017 (done) |

## Objective

Migrate `ImportService`'s GET methods (`getJobStatus`, `getJobErrors`) to `rxResource`,
and replace the manual `interval`/`switchMap`/`takeWhile` polling in
`import-results.ts` with `rxResource`'s `refetchInterval`. PASS when the polling
operators are gone, zero `subscribe()` calls remain for GET data loading in
`import-results.ts`/`import-errors.ts`, and all tests are green; FAIL otherwise.

## Pre-conditions

- [ ] US-017 (Zod schema consistency) is DONE -- `import.schema.ts` already exports
  `ImportJob`, `ImportStatus`, `ImportError`
- [ ] `frontend/src/app/imports/import.service.ts` currently returns `Observable`s for
  all three methods (confirmed by reading the file)
- [ ] `TERMINAL_IMPORT_STATUSES` (`Completed`, `CompletedWithErrors`, `Failed`) is
  exported from `import.service.ts` and usable to derive the refetch cutoff

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| `frontend/src/app/imports/import.service.ts` | 1-47 (full file) | Service to migrate: 2 GET methods to `rxResource`, `uploadCsv` stays Observable; also defines `TERMINAL_IMPORT_STATUSES` |
| `frontend/src/app/imports/import-results/import-results.ts` | 1-85 (full file) | Contains the polling logic (`interval`/`switchMap`/`takeWhile`) that must be replaced by `refetchInterval` -- the highest-impact change in this task |
| `frontend/src/app/imports/import-errors/import-errors.ts` | 1-80 (full file) | Consumes `getJobErrors`; has its own pagination state (`page` signal) |

## Deliverables

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/app/imports/import.service.ts` | Replace `getJobStatus`/`getJobErrors` with `rxResource`-based equivalents; keep `uploadCsv` as an `HttpClient` Observable POST |
| `frontend/src/app/imports/import-results/import-results.ts` | Remove `interval`/`switchMap`/`takeWhile` entirely; drive job status via `rxResource` with a `computed()`-derived `refetchInterval`: `2000` while status is non-terminal, `undefined` once terminal |
| `frontend/src/app/imports/import-results/import-results.spec.ts` | Update to assert against resource signal state and refetch-interval behavior (e.g., fake timers verifying no further requests after terminal status) |
| `frontend/src/app/imports/import-errors/import-errors.ts` | Remove `subscribe()`; consume `.value()`/`.isLoading()`/`.error()`; page changes feed the resource's request signal |
| `frontend/src/app/imports/import-errors/import-errors.spec.ts` | Update to assert against resource signal state |

## Quality Gates

| # | Gate | Command | Pass Criteria |
|---|------|---------|----------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep07/T-019-import-rxresource.md` | exit 0 |
| 2 | Frontend tests pass | `cd frontend && CI=true pnpm exec ng test --configuration=ci` | exit 0 |
| 3 | Frontend lint | `cd frontend && pnpm exec ng lint` | exit 0 |
| 4 | Polling operators removed | `! rg -q -e interval -e switchMap -e takeWhile frontend/src/app/imports/import-results/ --type ts` | exit 0 (no matches) |
| 5 | No subscribe() outside upload | `! rg -q 'subscribe\(' frontend/src/app/imports/ --type ts --glob '!*.spec.ts' --glob '!*upload*'` | exit 0 (no matches) |
| 6 | uploadCsv unchanged | `rg 'uploadCsv' frontend/src/app/imports/import.service.ts` | still returns `Observable<UploadResponse>` via `this.http.post` |
| 7 | No side effects | `git diff --stat` | Only files listed in Deliverables |

## Boundaries

- NOT in scope: `uploadCsv()` -- a POST mutation triggering a new import job; stays as
  an `HttpClient` Observable, no `resource()` semantics apply to mutations
- NOT in scope: Backend SSE or push-based status delivery -- this handoff only removes
  the *client-side* polling implementation via `rxResource`'s `refetchInterval`; it does
  not change the polling protocol itself or touch any backend endpoint
- NOT in scope: `import-upload.ts` -- it only calls `uploadCsv()` (a mutation), it has no
  GET methods and is therefore untouched by this migration
- NOT in scope: `ProductService`/`CheckoutService` migrations -- covered by T-018 and
  T-020 respectively, kept separate to avoid file overlap between parallel handoffs

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|---------------|------------|
| Keep `interval`/`switchMap`/`takeWhile` running alongside `rxResource` "as a fallback" | Two competing polling mechanisms double-fetch and defeat the purpose of this migration | `refetchInterval` fully replaces the manual loop -- delete it, do not keep it dormant |
| Hardcode the refetch interval as a fixed `2000` regardless of job status | Never stops polling after the job reaches a terminal state, leaking a perpetual timer | Use a `computed()` signal: non-terminal status → `2000`, terminal status → `undefined` (which stops `rxResource` from refetching) |
| Compute terminal status inline with a duplicated status list | Diverges from `TERMINAL_IMPORT_STATUSES` if either list is edited later | Import and reuse `TERMINAL_IMPORT_STATUSES` from `import.service.ts` in the `computed()` |
| Test the refetch behavior only by asserting the resource fetched once | Does not prove polling actually stops -- the highest-risk regression this task can introduce | Use fake timers: assert additional fetches occur while non-terminal, then assert no further fetch occurs after the resource reports a terminal status |

## Rollback Guidance

```bash
git checkout -- frontend/src/app/imports/import.service.ts
git checkout -- frontend/src/app/imports/import-results/
git checkout -- frontend/src/app/imports/import-errors/
```

This restores the service and both components (plus specs) to the pre-migration
Observable/subscribe()/manual-polling implementation.

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

- [x] `frontend/src/app/imports/import.service.ts` (GET methods migrated to `rxResource`;
  also required updating `import.service.spec.ts`, not listed above but a direct consequence
  of the public API change)
- [x] `frontend/src/app/imports/import-results/import-results.ts` (+ spec, polling removed)
- [x] `frontend/src/app/imports/import-errors/import-errors.ts` (+ spec)

### Quality Gates

- [x] Gate 1: Handoff exists
- [ ] Gate 2: Frontend tests pass -- BLOCKED at the whole-repo command level: `ng test`
  compiles the entire application, and `frontend/src/app/products/product.service.ts` +
  `frontend/src/app/search/search-page/search-page.ts` (T-018 scope, not touched here) use an
  incorrect `rxResource` API (`request`/`loader`) that does not exist in Angular 22.0.0 (verified
  against `node_modules/@angular/core/types/rxjs-interop.d.ts`; the real keys are `params`/
  `stream`). Scoped verification with
  `CI=true pnpm exec ng test --configuration=ci --include "src/app/imports/**/*.spec.ts"`
  passes 26/26 with exit 0, proving this task's deliverables are correct in isolation. See
  engram `architecture/rxresource-api-angular22` for full details.
- [x] Gate 3: Frontend lint passes (`pnpm exec eslint src/app/imports/` -- 0 problems; the
  handoff's literal `ng lint` command does not exist as an ng target in this repo)
- [x] Gate 4: Polling operators removed from `import-results/`
- [x] Gate 5: Zero `subscribe()` outside `import-upload.ts`
- [x] Gate 6: `uploadCsv` unchanged
- [x] Gate 7: No side effects (`git diff --stat` touches only the 3 deliverable files plus
  `import.service.spec.ts`, a necessary consequence of the service's API change)
