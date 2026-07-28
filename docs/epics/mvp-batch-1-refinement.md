> [INDEX](../INDEX.md) / [Epics](./) / MVP Batch 1 --- Refinement Addenda

# MVP Batch 1 --- Refinement Addenda

## Purpose

This document records the engineering decisions made during the Refine phase that
amend, clarify, or extend the original epic definitions and architecture documents.

## Decisions Resolved

### D1. Checkout Price Source (EP04)

Cart's `unit_price_snapshot` carries through to `order_items.unit_price`. Stock (not
price) is re-validated at checkout. EP04 boundary text corrected to reflect snapshot
semantics.

**Affected docs**: `docs/epics/EP04-purchase-workflow.md`

### D2. Duplicate SKU Strategy (EP02)

- Against existing catalog: **UPSERT** (update existing product with CSV row data)
- Within same import file: **REJECT second occurrence** (first row wins)

### D3. Delete Product with References (EP01)

Hard delete. FK violation returns **409 CONFLICT** with error code `PRODUCT_IN_USE`.
No soft delete, no `is_active` flag.

**Affected docs**: `docs/architecture/api-contract.md` (409 response added to DELETE)

### D4. Cart Identity (EP04)

Signed cookie carrying `cart_id` UUID. Created lazily on first `POST /api/cart/items`.
Cookie attributes: `HttpOnly`, `SameSite=Strict`. No session table --- the cookie IS
the session.

### D5. Cart Over-Stock Policy (EP04)

**REJECT** with 409 `INSUFFICIENT_STOCK`. Response includes `available` quantity.
No automatic capping to available stock.

### D6. Quantity Update to Zero (EP04)

**REJECTED** with 400 `VALIDATION_ERROR`. Quantity must be > 0. Use `DELETE` to
remove items from cart.

### D7. Sort vs. Relevance Rank (EP03)

- `q` present + `sortBy` explicit → `sortBy` wins
- `q` present + no `sortBy` → relevance rank (`ts_rank DESC`)
- No `q` → default sort by `name ASC`

### D8. Checkout Concurrency (EP04)

`SELECT ... FOR UPDATE` on product rows inside checkout transaction. Prevents
race condition where two concurrent checkouts both read stale stock.

### D9. Field Max Lengths (EP01)

| Field | Max Length |
|-------|-----------|
| name | 255 |
| sku | 50 |
| description | 2000 |
| category | 100 |

**Affected docs**: `docs/architecture/api-contract.md` (constraints added)

### D10. Price Precision (EP01)

Max 2 decimal places. Values with 3+ decimals REJECTED (not rounded). `29.999` → 400.

**Affected docs**: `docs/architecture/api-contract.md` (reject example added)

### D11. SKU Case Sensitivity (EP01)

Case-sensitive (PostgreSQL `TEXT` default). `SKU-1` ≠ `sku-1`.

### D12. Deferred State Transitions (EP04)

Cart `Abandoned` and Order `Fulfilled` transitions deferred to v2.

### D13. CSV Schema Mismatch (EP02)

Missing required columns → Job status `Failed`, not per-row errors.

### D14. Migration Execution (EP06)

PostgreSQL `docker-entrypoint-initdb.d/`. SQL files copied into init directory.
Numbered sequentially (001-008).

### D15. SQLi vs XSS Handling (EP01/EP02)

- **XSS**: REJECTED --- no legitimate business value as product name
- **SQLi**: ACCEPTED (if other fields valid) --- HoneySQL prevents injection
  structurally via parameterized queries. Names with apostrophes are valid.

### D16. US-002 Merged into US-008

Original US-002 (Read & List Products) merged into US-008 (Product Search). One
endpoint serves both listing and search via query parameters.

### D17. Global Error Sanitization

Cross-cutting: no error response ever leaks stack traces, SQL fragments, or file
paths. Added as explicit AC in every backend story.

### D18. Shared Validation Module

US-001 creates the shared Malli validation module. All stories reuse it. No parallel
rule sets.

## MVP Story List (16 stories)

### IN --- v1

| ID | Title | Epic | Priority |
|----|-------|------|----------|
| US-001 | Project Scaffolding | EP06 | Must Have |
| US-002 | Create Product with Validation & Sanitization | EP01 | Must Have |
| US-003 | Update Product | EP01 | Must Have |
| US-004 | Delete Product | EP01 | Must Have |
| US-005 | CSV Upload & Background Processing Pipeline | EP02 | Must Have |
| US-006 | CSV Row Validation | EP02 | Must Have |
| US-007 | Import Results & Error Reporting | EP02 | Must Have |
| US-008 | Product Search with Filters, Sort & Pagination | EP03 | Must Have |
| US-009 | Cart Operations | EP04 | Must Have |
| US-010 | Checkout & Order Creation | EP04 | Must Have |
| US-011 | Product Management Views | EP05 | Must Have |
| US-012 | CSV Import View | EP05 | Must Have |
| US-013 | Product Search View | EP05 | Must Have |
| US-014 | Cart & Checkout Views | EP05 | Must Have |
| US-015 | Docker Compose Multi-Stage Setup | EP06 | Must Have |
| US-016 | README & Decision Documentation | EP06 | Must Have |

### DEFERRED --- v2+

| Item | Epic | Reason |
|------|------|--------|
| Duplicate product as template | EP01 | Could Have --- evaluator does not test for it |
| Warning before delete with purchase history | EP01 | Should Have --- FK reference already prevents data loss |
| Real-time progress via SSE | EP02 | Could Have --- polling on GET /api/imports/:id suffices |
| Dry-run preview | EP02 | Could Have --- not in challenge requirements |
| Export/download skipped rows | EP02 | Should Have --- errors endpoint covers it in API form |
| Search suggestions as-you-type | EP03 | Could Have --- UX polish |
| Session-remembered filters | EP03 | Could Have --- UX polish |
| Responsive layout | EP05 | Should Have --- Low UX weight in evaluation |
| Cart Abandoned state transition | EP04 | Not in challenge requirements |
| Order Fulfilled state transition | EP04 | Not in challenge requirements |

## Implementation Order

```
Phase 0: US-001 (Scaffolding)
Phase 1: US-002 (Create) → US-003 (Update) + US-004 (Delete) parallel
Phase 2 parallel branches:
  A: US-008 (Search)
  B: US-005 → US-006 → US-007 (CSV pipeline)
  C: US-009 → US-010 (Cart → Checkout)
Phase 3 parallel: US-011, US-012, US-013, US-014 (Frontend)
Phase 4: US-015 (finalize Docker) → US-016 (README)
```
