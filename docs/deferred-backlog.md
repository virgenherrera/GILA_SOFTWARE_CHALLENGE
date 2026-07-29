> [INDEX](INDEX.md) / Deferred Backlog

# Deferred Backlog

Persistent artifact capturing ideas and stories excluded from the current iteration,
with rationale. Initialized during Capture, updated during Discover and Refine, and
consumed during the next iteration's Capture phase.

## Backlog

| ID | Item | Originating Phase | Reason | Category | Reconsider When | Related Deps |
| ---- | ---- | ------------------ | ------ | -------- | ----------------- | ------------- |
| DF-001 | Duplicate product as template (EP01) | Refine | Could Have --- evaluator does not test for it | low-value | v2 iteration or user feedback requests it | none |
| DF-002 | Warning before delete with purchase history (EP01) | Refine | Should Have --- FK reference already prevents data loss | low-value | User feedback reports confusion from the hard-delete FK conflict response | none |
| DF-003 | Real-time progress via SSE for CSV import (EP02) | Refine | Could Have --- polling on `GET /api/imports/:id` suffices at current volume | time | SSE infrastructure is added, or import volume grows beyond what polling can serve responsively | none |
| DF-004 | Dry-run preview for CSV import (EP02) | Refine | Could Have --- not in challenge requirements | low-value | v2 iteration or user feedback requests it | none |
| DF-005 | Export/download skipped rows (EP02) | Refine | Should Have --- the import errors endpoint already covers this in API form | low-value | User feedback requests a downloadable file instead of an API response | none |
| DF-006 | Search suggestions as-you-type (EP03) | Refine | Could Have --- UX polish, not required by the challenge | time | v2 iteration or user feedback requests it | none |
| DF-007 | Session-remembered search filters (EP03) | Refine | Could Have --- UX polish, not required by the challenge | time | v2 iteration or user feedback requests it | none |
| DF-008 | Responsive layout (EP05) | Refine | Should Have --- Low UX weight in evaluation criteria | time | v2 iteration targeting mobile/tablet users | none |
| DF-009 | Cart `Abandoned` state transition (EP04) | Refine | Not required by the challenge; no retention-window enforcement needed for the evaluation scope | scope-creep | v2 iteration introduces cart retention policies | none |
| DF-010 | Order `Fulfilled` state transition (EP04) | Refine | Not required by the challenge; no fulfillment tracking needed for the evaluation scope | scope-creep | v2 iteration introduces fulfillment tracking or payment declines | none |

## Categories

- `time` --- valuable but does not fit in this iteration
- `dependency` --- requires something that does not exist yet
- `risk` --- requires investigation or spike first
- `low-value` --- does not justify the cost now
- `scope-creep` --- not part of the original problem statement

## Notes

- All ten items above originated from the same source: the Deferred --- v2+ table in
  [MVP Batch 1 --- Refinement Addenda](epics/mvp-batch-1-refinement.md) and
  [INDEX.md](INDEX.md#deferred--v2). This file is the canonical, structured record;
  the tables in those documents summarize the same decisions for quick reference.
- No item currently carries the `dependency` category, so no automatic reconsideration
  trigger from the dependency DAG applies yet.
