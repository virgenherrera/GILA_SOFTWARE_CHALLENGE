> [INDEX](../INDEX.md) / Remediation / REM-001

# REM-001: Scrum Review Build-Readiness Remediation

**Type**: Self-Destructive Handoff
**Status**: SELF-DESTRUCTED | 2026-07-28
**Triggered by**: Scrum Team Review 2026-07-28 (PO + Dev Lead + SM)
**Verdict at trigger**: NOT READY (2/3 reviewers)

## Self-Destruct Protocol

This handoff is self-destructive: its purpose is to eliminate the conditions that
triggered its creation. When all quality gates pass:

1. Update this file's Status to: `SELF-DESTRUCTED | {date}`
2. The pre-conditions (scrum review findings) are no longer true
3. This file persists in git history as audit trail — do NOT delete
4. The self-destruction IS the completion signal

## Objective

Resolve all 10 CRITICAL and 12 WARNING findings from the scrum team readiness review
so the project clears the Build Readiness Gate. PASS/FAIL: zero CRITICAL findings
remain when re-evaluated against the same MIM gates.

## Pre-Conditions (= the findings that must become false)

### CRITICAL (10)

| ID | Finding | Source |
|----|---------|--------|
| C01 | SSE endpoint in api-contract but descoped in stories | PO |
| C02 | Health check shape contradicts across phases | PO+SM |
| C03 | Category filter has no backing endpoint | PO |
| C04 | File overlap Wave 2: T-003/T-004 | DL+SM |
| C05 | File overlap Wave 4: T-011–T-014 on app.routes.ts | DL+SM |
| C06 | T-013 query params don't match API contract | DL |
| C07 | Echo System Stage 4 (audit_command) missing | DL+SM |
| C08 | Epic-level dependency DAG absent | DL+SM |
| C09 | Zero estimates across all 16 stories | DL |
| C10 | DOR item 7 (role-gate review) fails all 16 stories | SM |

### WARNING (12)

| ID | Finding | Source |
|----|---------|--------|
| W01 | Context bundle line ranges missing (all 16 handoffs) | DL |
| W02 | Boundary rationale missing (all 16 handoffs) | DL+SM |
| W03 | Engineering Addenda template not followed | SM |
| W04 | deferred-backlog.md does not exist | SM |
| W05 | Competitive Landscape table missing | SM |
| W06 | Quality gates degrade to unrunnable prose | DL |
| W07 | 12 broken cross-reference links in 8 stories | PO+DL+SM |
| W08 | AC-004.5 self-contradictory | PO |
| W09 | error-handling.md vs api-contract.md code mismatch | DL |
| W10 | Dev tools not version-pinned | DL |
| W11 | No measurable NFR targets | PO |
| W12 | Batch critical path miscounted | DL |

## Execution Waves

### Wave 1 — Parallel (no file overlap)

| Agent | Scope | Files | Fixes |
|-------|-------|-------|-------|
| A1: Arch Reconciliation | C01,C02,C03,W09 | api-contract.md, health-check-strategy.md, error-handling.md | Remove SSE v2, add /categories, unify health check, fix error codes |
| A2: Tech Stack Hardening | C07,C08,W10,W11 | tech-stack.md | Add audit_command, pre-commit hook, pin dev tools, add epic DAG, add NFR table |
| A3: Process Artifacts | W03,W04,W05,C10(partial) | deferred-backlog.md (new), project-brief.md, domain-glossary.md, mvp-batch-1-refinement.md | Create backlog, add project type, competitive landscape, rewrite addenda |
| A4: Story Fixes | C09,C10,W07,W08 | US-001–US-016 | Add estimates, fix AC-004.5, fix cross-refs, update health ACs, role-gate review |

### Wave 2 — After Wave 1 (depends on Wave 1 outputs)

| Agent | Scope | Files | Fixes |
|-------|-------|-------|-------|
| A5: Batch Plan | C04,C05,W12 | batch-1-plan.md | Serialize T-003/T-004, fix Wave 4 deps, correct critical path, add capacity |
| A6: Handoff Backfill | C06,W01,W02,W06 | T-001–T-016 | Fix T-013 params, add line ranges, boundary rationale, quality gate specificity |

### Wave 3 — Verification + Self-Destruct

| Agent | Scope | Fixes |
|-------|-------|-------|
| A7: Gate Verification | All findings | Re-check each finding, mark SELF-DESTRUCTED if all pass |

## Design Decisions Made

| Decision | Rationale |
|----------|-----------|
| Health check: architecture version wins (rich shape with DB state) | Docker depends_on: service_healthy requires DB connectivity info |
| Category filter: add GET /api/products/categories endpoint | Simpler than modifying US-013; paginated product list can't provide complete categories |
| SSE: remove from v1 contract, add "v2 Deferred" note | Stories unanimously descope it; keeping it misleads implementers |

## Quality Gates

| Gate | Finding | Verification | Type |
|------|---------|-------------|------|
| G01 | C01 | SSE endpoint marked deferred or removed in api-contract.md | DOC |
| G02 | C02 | Health check shape identical in api-contract.md, US-001, US-015 | DOC |
| G03 | C03 | GET /api/products/categories exists in api-contract.md | DOC |
| G04 | C04 | T-003/T-004 NOT in same parallel wave in batch-1-plan.md | DOC |
| G05 | C05 | T-011–T-014 app.routes.ts conflict resolved in batch-1-plan.md | DOC |
| G06 | C06 | T-013 query params match api-contract.md naming | DOC |
| G07 | C07 | {audit_command} defined in tech-stack.md Echo System table | DOC |
| G08 | C08 | Epic dependency DAG exists in tech-stack.md | DOC |
| G09 | C09 | All 16 stories have S/M/L estimate in Metadata | DOC |
| G10 | C10 | DOR item 7 addressed in all 16 stories | DOC |

## Boundaries

- Do NOT add new user stories or change MVP scope — reconcile existing docs only
- Do NOT modify any source code — this is documentation-only remediation
- Do NOT change the tech stack or architectural decisions — only document what's missing
- Do NOT re-run the full scrum review — verify specific gates only

## Rollback Guidance

All changes are to committed markdown files. Rollback: `git checkout HEAD -- docs/`

## Progress Tracker

### Wave 1
- [x] A1: Architecture Reconciliation (C01, C02, C03, W09) — api-contract.md: SSE deferred, categories endpoint added, SERVICE_UNAVAILABLE added. health-check-strategy.md + error-handling.md already consistent.
- [x] A2: Tech Stack Hardening (C07, C08, W10, W11) — tech-stack.md: audit_command, pre-commit hook, dev tool pins, epic DAG (2 Mermaid), NFR targets table. 794→1032 lines.
- [x] A3: Process Artifacts (W03, W04, W05) — deferred-backlog.md created, project type added, competitive landscape added, refinement doc rewritten to addenda template (18 decisions preserved as appendix).
- [x] A4: Story Fixes (C09, C10, W07, W08) — All 16 stories: S/M/L estimates, AC-004.5 rewritten, health check ACs updated (rich shape), 12 cross-ref links fixed, DOR item 7 checked, US-013 category note updated.

### Wave 2
- [x] A5: Batch Plan (C04, C05, W12) — T-003/T-004 serialized, Wave 4 split into 4a/4b (T-011 first, then T-012/T-013/T-014 parallel), critical path corrected to 8, capacity assessment added.
- [x] A6: Handoff Backfill (C06, W01, W02, W06) — T-013 query params fixed (priceMin/priceMax/perPage/sortBy+sortOrder), boundary rationale added to all 16, T-015 metadata link fixed, quality gates made specific (--focus :namespace), context bundles upgraded to section references.

### Wave 3
- [x] A7: Gate Verification + Self-Destruct — all 10 CRITICAL gates (G01–G10) re-verified PASS; handoff self-destructed.

### Status
```
Status: SELF-DESTRUCTED | 2026-07-28
Progress: 7/7 agents complete
Wave: 3 of 3
```
