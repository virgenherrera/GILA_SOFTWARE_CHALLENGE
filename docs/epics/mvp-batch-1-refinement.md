> [INDEX](../INDEX.md) / [Epics](./) / MVP Batch 1 --- Refinement Addenda

# MVP Batch 1 --- Refinement Addenda

## Purpose

This document records the engineering decisions made during the Refine phase that
amend, clarify, or extend the original epic definitions and architecture documents. It
follows the Engineering Addenda template defined in `AGENTS.md`.

## 1. Batch Info

| Field | Value |
| ----- | ----- |
| Batch number | 1 |
| Scope | MVP v1 --- 16 user stories across 6 epics (EP01--EP06): Product Management, CSV Import, Product Search, Purchase Workflow, User Interface, Containerization & Documentation |
| Refined by | AI-assisted scrum team (refinement ceremony, 8 roles) with human PO approval at MIM |

## 2. Prerequisites

- [x] Architecture docs complete (12/12 architecture documents, see [INDEX.md](../INDEX.md#architecture))
- [x] DOR satisfied (acceptance criteria embedded in each of the 16 MVP stories; see [batch-1-plan.md](../subtasks/batch-1-plan.md#5-definition-of-done--batch) for the batch-level Definition of Done)
- [x] MVP cut approved (16 v1 stories confirmed Must Have; 10 deferred items recorded in [docs/deferred-backlog.md](../deferred-backlog.md))

## 3. Dependencies

| Package/Tool | Version | Target |
| ------------ | ------- | ------ |
| `org.clojure/clojure` | 1.12.0 | Backend (`deps.edn`) |
| Eclipse Temurin JDK | 21 LTS | Backend runtime (`Dockerfile.backend`) |
| `ring/ring-core` + `ring/ring-jetty-adapter` | 1.12.2 | Backend HTTP server (`deps.edn`) |
| `metosin/reitit` | 0.7.2 | Backend routing (`deps.edn`) |
| `metosin/malli` | 0.16.4 | Backend validation (`deps.edn`) |
| `com.github.seancorfield/next.jdbc` | 1.3.955 | Backend data access (`deps.edn`) |
| `com.github.seancorfield/honeysql` | 2.6.1235 | Backend SQL generation (`deps.edn`) |
| `org.postgresql/postgresql` | 42.7.5 | Backend JDBC driver (`deps.edn`) |
| `com.zaxxer/HikariCP` | 6.2.1 | Backend connection pool (`deps.edn`) |
| `org.clojure/core.async` | 1.7.790 | Backend CSV import pipeline (`deps.edn`) |
| `org.clojure/data.csv` | 1.1.0 | Backend CSV parsing (`deps.edn`) |
| `buddy/buddy-sign` | 3.4.0 | Backend cart cookie signing (`deps.edn`) |
| `lambdaisland/kaocha` | 1.91.1392 | Backend test runner (`deps.edn`, `:test` alias) |
| `clj-test-containers/clj-test-containers` | 0.7.4 | Backend integration tests (`deps.edn`, `:test` alias) |
| `@angular/core` + `@angular/router` + `@angular/forms` | 22.0.0 | Frontend framework (`package.json`) |
| `zod` | 3.24.4 | Frontend validation (`package.json`) |
| `vitest` | 3.2.1 | Frontend test runner (`package.json`, dev) |
| PostgreSQL | 17.5 | Database (`docker-compose.yml`, service `db`) |
| nginx | 1.27-alpine | Frontend static server (`Dockerfile.frontend`, production stage) |
| Docker Compose | v2 | Orchestration (`docker-compose.yml`) |
| Playwright | v1.62.0-noble | E2E test runner (`docker-compose.yml`, service `playwright`, test only) |

Full version pins and rationale: [Tech Stack](../architecture/tech-stack.md).

## 4. Implementation Order

1. **Phase 0 --- Foundation**: US-001 Project Scaffolding (monorepo structure, shared
   Malli validation module, Docker base, CI skeleton).
2. **Phase 1 --- Core Entity**: US-002 Create Product with Validation & Sanitization
   (establishes the Product entity, schema, and validation rules all later stories
   depend on).
3. **Phase 2 --- CRUD Completion (parallel)**: US-003 Update Product and US-004 Delete
   Product (both depend only on US-002; independent of each other).
4. **Phase 3 --- Backend Features (three parallel branches)**:
   - Branch A --- Search: US-008 Product Search with Filters, Sort & Pagination.
   - Branch B --- CSV Pipeline (sequential): US-005 CSV Upload & Background Processing →
     US-006 CSV Row Validation → US-007 Import Results & Error Reporting.
   - Branch C --- Purchase Workflow (sequential): US-009 Cart Operations → US-010
     Checkout & Order Creation.
5. **Phase 4 --- Frontend Views (parallel)**: US-011 Product Management Views, US-012
   CSV Import View, US-013 Product Search View (depends on US-011 reusing
   `product.service.ts`), US-014 Cart & Checkout Views.
6. **Phase 5 --- Packaging**: US-015 Docker Compose Multi-Stage Setup (requires all
   application code from Phases 1--4 complete).
7. **Phase 6 --- Documentation**: US-016 README & Decision Documentation (requires
   US-015 finalized).

Critical path: US-001 → US-002 → US-009 → US-010 → US-014 → US-015 → US-016.
Full task-level dependency graph and wave-by-wave rationale:
[batch-1-plan.md](../subtasks/batch-1-plan.md#3-dependency-graph-mermaid-dag).

## 5. Risks

### HIGH

- **Checkout race condition under concurrent load (EP04)**
  - **Problem**: two concurrent checkouts against the same product could both read
    stale stock and both succeed, overselling inventory.
  - **Why it matters**: overselling is a direct data-integrity and financial-correctness
    failure --- the exact class of defect this challenge evaluates for.
  - **Mitigation**: `SELECT ... FOR UPDATE` on product rows inside the checkout
    transaction (D8), serializing concurrent checkouts against the same SKU.

### MEDIUM

- **SQL injection surface in free-text fields (EP01/EP02)**
  - **Problem**: product name, description, and category accept arbitrary text,
    including the CSV's seeded SQL-injection payloads.
  - **Why it matters**: if any query path bypasses parameterization, injected input
    could alter or exfiltrate data.
  - **Mitigation**: HoneySQL generates all SQL structurally from Clojure data (D15);
    no raw string concatenation into queries is permitted anywhere in the codebase.
- **CSV schema mismatch silently degrading to partial data (EP02)**
  - **Problem**: a CSV missing required columns could otherwise be processed row by
    row, producing confusing partial results instead of a clear failure.
  - **Why it matters**: silent partial processing hides a structural problem behind
    what looks like ordinary per-row validation noise.
  - **Mitigation**: a missing required column fails the whole job immediately with
    status `Failed`, not per-row errors (D13).
- **Cart identity spoofing or hijacking via cookie (EP04)**
  - **Problem**: the cart cookie is the sole session mechanism; a weak or unsigned
    cookie could let an attacker guess or tamper with another customer's cart.
  - **Why it matters**: a hijacked cart could expose or manipulate another user's
    in-progress purchase.
  - **Mitigation**: HMAC-SHA256-signed cookie (`buddy-sign`) with `HttpOnly` and
    `SameSite=Strict` attributes (D4); no session table exists to attack separately.

### LOW

- **Duplicate SKU ambiguity across and within CSV files (EP02)**
  - **Problem**: without an explicit rule, it is unclear whether a duplicate SKU
    should overwrite, be rejected, or be silently ignored.
  - **Why it matters**: ambiguous duplicate handling risks silent data corruption
    (unintended overwrites) or silent data loss (unintended drops).
  - **Mitigation**: explicit rule --- UPSERT against the existing catalog, REJECT the
    second occurrence within the same file, first row wins (D2).
- **Accidental data loss on product delete (EP01)**
  - **Problem**: a hard delete of a product still referenced by historical orders
    could be requested by an operator unaware of the reference.
  - **Why it matters**: deleting a product with order history would corrupt the
    historical financial record.
  - **Mitigation**: the foreign key constraint blocks the delete outright, returning
    `409 CONFLICT` with error code `PRODUCT_IN_USE` (D3); no soft-delete flag needed.

## 6. Related Documents

| Document | Path | Relationship |
| -------- | ---- | ------------ |
| Batch 1 --- MVP Implementation Plan | [subtasks/batch-1-plan.md](../subtasks/batch-1-plan.md) | Task breakdown, dependency graph, and execution waves consuming this addenda |
| Tech Stack | [architecture/tech-stack.md](../architecture/tech-stack.md) | Source of the Dependencies table (Section 3) |
| API Contract | [architecture/api-contract.md](../architecture/api-contract.md) | Amended by decisions D1, D3, D9, D10 |
| Data Model | [architecture/data-model.md](../architecture/data-model.md) | Referenced by decisions D3, D11 |
| EP01 --- Product Management | [EP01-product-management.md](EP01-product-management.md) | Amended by decisions D3, D9, D10, D11, D15 |
| EP02 --- CSV Import | [EP02-csv-import.md](EP02-csv-import.md) | Amended by decisions D2, D13, D15 |
| EP03 --- Product Search | [EP03-product-search.md](EP03-product-search.md) | Amended by decision D7 |
| EP04 --- Purchase Workflow | [EP04-purchase-workflow.md](EP04-purchase-workflow.md) | Amended by decisions D1, D4, D5, D6, D8, D12 |
| Deferred Backlog | [../deferred-backlog.md](../deferred-backlog.md) | Canonical record of the deferred items listed in Appendix B |

## Appendix A --- Resolved Decisions (18)

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
| ----- | ---------- |
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

## Appendix B --- MVP Story List (16 stories)

### IN --- v1

| ID | Title | Epic | Priority |
| -- | ----- | ---- | -------- |
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

Ten items were deferred out of this batch. The canonical record --- with category and
reconsideration criteria --- lives in [docs/deferred-backlog.md](../deferred-backlog.md)
(DF-001 through DF-010).
