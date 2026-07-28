> [INDEX](../INDEX.md) / [User Stories](./) / US-006 --- CSV Row Validation

# US-006 --- CSV Row Validation

## 1. Metadata

| Field    | Value                           |
| -------- | ------------------------------- |
| Epic     | EP02 --- CSV Import             |
| Priority | Must Have                       |
| Status | Ready                           |

## 2. Story

**As a** Catalog Manager,
**I want** each CSV row validated against the same rules as manual product creation, with specific handling for all known trap types,
**so that** bulk-imported data meets the same integrity and security standards.

## 3. Definition of Ready

- [x] Story follows the INVEST criteria
- [x] Acceptance criteria are testable and unambiguous
- [x] Dependencies identified (US-001 scaffolding, US-002 shared validation, US-005 processing pipeline)
- [x] Shared validation contract reviewed (US-002 Malli schemas)
- [x] API contract reviewed ([API Contract](../architecture/api-contract.md) section 4)
- [x] Data model reviewed ([Data Model](../architecture/data-model.md) sections 2.1, 2.6, 2.7)
- [x] Domain glossary terms aligned (Product, CsvImportJob, ImportError, all value objects)
- [x] All 10 trap types identified and covered by dedicated ACs
- [x] Out of scope items explicitly listed
- [x] Test plan has one dedicated test per trap type

## 4. Acceptance Criteria

### AC-006.1: Shared validation contract --- CSV rows use the same Malli schemas as US-002

**Given** the shared validation module defines Malli schemas for Product fields (name, sku, price, stock, weight_kg, category)
**When** a CSV row is validated during import
**Then** the validator uses the SAME Malli schemas as `POST /api/products` (US-002)
**And** no separate or parallel validation rule set exists for CSV import
**And** any future change to the shared schemas automatically applies to both manual creation and CSV import

### AC-006.2: Malformed price --- row rejected

**Given** a CSV row where the `price` field contains a non-numeric value (e.g., `"free"`, `"$29.99"`, `"abc"`, empty string)
**When** the row is validated
**Then** the row is rejected
**And** an `import_errors` record is created with `field_name = "price"` and an `error_reason` identifying the price validation failure
**And** the row is NOT persisted as a Product

### AC-006.3: Negative stock --- row rejected

**Given** a CSV row where the `stock` field contains a negative integer (e.g., `-5`, `-100`)
**When** the row is validated
**Then** the row is rejected
**And** an `import_errors` record is created with `field_name = "stock"` and `error_reason` identifying the stock constraint violation

### AC-006.4: Empty or whitespace-only name --- row rejected

**Given** a CSV row where the `name` field is empty (`""`), whitespace-only (`"   "`), or missing entirely
**When** the row is validated
**Then** the row is rejected
**And** an `import_errors` record is created with `field_name = "name"` and `error_reason` indicating the name must not be empty
**And** missing name is treated as empty (same rejection path)

### AC-006.5: Missing category --- row accepted

**Given** a CSV row where the `category` field is empty or missing
**When** the row is validated
**Then** the row is accepted (CategoryLabel may be empty per domain model)
**And** the Product is created with an empty/null category
**And** all other fields must still pass their individual validation rules

### AC-006.6: Missing weight --- row accepted

**Given** a CSV row where the `weight_kg` field is empty or missing
**When** the row is validated
**Then** the row is accepted (WeightKg is optional per domain model)
**And** the Product is created with a null weight_kg
**And** all other fields must still pass their individual validation rules

### AC-006.7: Completely empty row --- skipped silently

**Given** a CSV row where every field is empty or the row is entirely blank
**When** the row is encountered during processing
**Then** the row is skipped silently
**And** no `import_errors` record is created for this row
**And** the `rejected_rows` count is NOT incremented
**And** the row does not affect the `accepted_rows` count
**And** the row IS counted toward `total_rows` for reconciliation purposes

### AC-006.8: Duplicate SKU within same file --- second occurrence rejected

**Given** a CSV file contains two or more rows with the same `sku` value
**When** the rows are processed in order
**Then** the first occurrence is accepted (if otherwise valid)
**And** the second and subsequent occurrences are rejected
**And** an `import_errors` record is created with `field_name = "sku"` and `error_reason` indicating duplicate SKU within the same file

### AC-006.9: Duplicate SKU against existing catalog --- upsert

**Given** a CSV row has a `sku` that already exists in the `products` table (from a previous import or manual creation)
**When** the row is validated and all fields pass validation
**Then** the existing Product is updated with the new row's data (upsert behavior)
**And** the row counts as `accepted` (not rejected)
**And** a `ProductUpdated` domain event is implied

### AC-006.10: XSS payload in name --- row rejected

**Given** a CSV row where the `name` field contains a script tag or XSS payload (e.g., `<script>alert('xss')</script>`, `<img onerror=alert(1)>`)
**When** the row is validated
**Then** the row is rejected
**And** an `import_errors` record is created with `field_name = "name"` and `error_reason = "Unsafe content detected"`
**And** the payload is never stored in the `products` table

### AC-006.11: SQL injection payload in name --- row accepted, stored as inert literal

**Given** a CSV row where the `name` field contains a SQL injection payload (e.g., `"'; DROP TABLE products; --"`)
**When** the row is validated
**Then** the row is accepted (if all other fields are valid)
**And** the SQL payload is stored as an inert literal string in the `products.name` column via HoneySQL parameterized queries
**And** the `products` table remains intact (no tables dropped, no data corrupted)
**And** legitimate names containing apostrophes (e.g., `"O'Brien's Widget"`) are NOT false-positive rejected

### AC-006.12: Each rejected row carries specific, actionable reason

**Given** a CSV row fails validation for any reason
**When** the `import_errors` record is created
**Then** the record includes `field_name` (the specific field that failed, or null for row-level errors) and `error_reason` (a human-readable, actionable description)
**And** the `error_reason` is never a generic "invalid row" message

### AC-006.13: Import is atomic per row

**Given** a CSV row passes all validation rules
**When** the row is persisted as a Product
**Then** the entire row is persisted atomically (all fields or nothing)
**And** if persistence fails mid-row (e.g., DB constraint violation), the row is treated as rejected and no partial Product record exists

### AC-006.14: Valid rows imported even when other rows fail

**Given** a CSV file contains a mix of valid and invalid rows
**When** the file is processed
**Then** all valid rows are imported as Products
**And** all invalid rows are rejected with individual reasons
**And** the job status becomes `CompletedWithErrors` (not `Failed`)
**And** the import does NOT roll back valid rows because of invalid ones

### AC-006.15: Row count reconciliation

**Given** a CSV file has been fully processed
**When** the final job status is recorded
**Then** `total_rows = accepted_rows + rejected_rows + skipped_empty_rows`
**And** the count always reconciles --- no row is unaccounted for

## 5. Definition of Done

- [ ] Validator module uses shared Malli schemas from US-002
- [ ] All 10 trap types handled with dedicated logic and tests
- [ ] XSS payloads rejected with "Unsafe content detected"
- [ ] SQL injection payloads stored safely via parameterized queries (not rejected)
- [ ] Legitimate apostrophes in names not false-positive rejected
- [ ] Duplicate SKU within file: second occurrence rejected
- [ ] Duplicate SKU against catalog: upsert behavior
- [ ] Empty rows skipped silently (no ImportError, no rejected count increment)
- [ ] Row count reconciliation verified (total = accepted + rejected + skipped)
- [ ] Per-trap CSV fixture files created in test/fixtures/csv/
- [ ] All acceptance criteria pass automated tests
- [ ] Integration tests run against PostgreSQL (Testcontainers)
- [ ] Code reviewed and merged

## 6. Deliverables

### Files to Create

| File | Purpose |
| ---- | ------- |
| `src/ecommerce/import/validator.clj` | Row-level validation using shared Malli schemas; XSS detection; empty-row detection; duplicate-SKU tracking |
| `test/ecommerce/import/validator_test.clj` | One unit test per trap type (10 tests minimum) |
| `test/fixtures/csv/malformed-price.csv` | 1-3 rows with price traps: "free", "$29.99", "abc" |
| `test/fixtures/csv/negative-stock.csv` | 1-2 rows with negative stock values |
| `test/fixtures/csv/empty-name.csv` | Rows with empty, whitespace-only, and missing names |
| `test/fixtures/csv/missing-category.csv` | Rows with empty/missing category (should pass) |
| `test/fixtures/csv/missing-weight.csv` | Rows with empty/missing weight_kg (should pass) |
| `test/fixtures/csv/empty-rows.csv` | File with interspersed completely empty rows |
| `test/fixtures/csv/duplicate-sku-in-file.csv` | File with two rows sharing the same SKU |
| `test/fixtures/csv/duplicate-sku-in-catalog.csv` | File with a SKU that matches an existing product |
| `test/fixtures/csv/xss-payload.csv` | Row with script tags and event handler payloads in name |
| `test/fixtures/csv/sql-injection.csv` | Row with SQL injection payload in name |
| `test/ecommerce/import/pipeline_integration_test.clj` | Full pipeline integration test against PostgreSQL |

### Files to Modify

| File | Change |
| ---- | ------ |
| `src/ecommerce/import/worker.clj` | Wire validator into go-loop; call validator for each row before persistence |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `shared-schemas-same-as-product-create` | AC-006.1 | Validator references the exact same Malli schema registry as POST /api/products |
| `malformed-price-free-rejected` | AC-006.2 | Row with price="free" is rejected; import_errors.field_name="price" |
| `malformed-price-currency-symbol-rejected` | AC-006.2 | Row with price="$29.99" is rejected; import_errors.field_name="price" |
| `malformed-price-alpha-rejected` | AC-006.2 | Row with price="abc" is rejected; import_errors.field_name="price" |
| `malformed-price-non-numeric-rejected` | AC-006.2 | Row with non-numeric price is rejected; import_errors.field_name="price" |
| `negative-stock-rejected` | AC-006.3 | Row with stock=-5 is rejected; import_errors.field_name="stock" |
| `empty-name-rejected` | AC-006.4 | Row with name="" is rejected; import_errors.field_name="name" |
| `whitespace-name-rejected` | AC-006.4 | Row with name="   " is rejected; import_errors.field_name="name" |
| `missing-name-rejected` | AC-006.4 | Row with missing name field is rejected; import_errors.field_name="name" |
| `missing-category-accepted` | AC-006.5 | Row with empty category is accepted; Product created with null category |
| `missing-weight-accepted` | AC-006.6 | Row with empty weight_kg is accepted; Product created with null weight_kg |
| `empty-row-skipped-silently` | AC-006.7 | Completely empty row creates no ImportError and does not increment rejected_rows |
| `empty-row-not-in-accepted-count` | AC-006.7 | Skipped empty rows do not increment accepted_rows |
| `duplicate-sku-in-file-second-rejected` | AC-006.8 | Second row with same SKU rejected; first row accepted |
| `duplicate-sku-in-file-error-reason` | AC-006.8 | ImportError for duplicate includes field_name="sku" and reason about duplicate |
| `duplicate-sku-catalog-upsert` | AC-006.9 | Row with existing catalog SKU updates the product; counts as accepted |
| `xss-script-tag-rejected` | AC-006.10 | Row with `<script>alert('xss')</script>` in name is rejected |
| `xss-event-handler-rejected` | AC-006.10 | Row with `<img onerror=alert(1)>` in name is rejected |
| `xss-error-reason` | AC-006.10 | ImportError for XSS has error_reason="Unsafe content detected" |
| `sqli-payload-accepted` | AC-006.11 | Row with `'; DROP TABLE products; --` in name is accepted |
| `sqli-stored-as-literal` | AC-006.11 | SQL payload stored literally in products.name; table intact |
| `sqli-apostrophe-no-false-positive` | AC-006.11 | Row with name="O'Brien's Widget" is accepted (not rejected as SQLi) |
| `rejected-row-has-field-and-reason` | AC-006.12 | Every ImportError has non-null error_reason; field_name set for field-level errors |
| `atomic-row-persistence` | AC-006.13 | Failed mid-persist leaves no partial Product record |
| `partial-success-valid-rows-imported` | AC-006.14 | Mix of valid/invalid rows: valid ones persisted, invalid ones rejected |
| `partial-success-status-completed-with-errors` | AC-006.14 | Job status is CompletedWithErrors, not Failed |
| `row-count-reconciliation` | AC-006.15 | total_rows equals accepted_rows + rejected_rows + skipped_empty_rows |

## 8. Validation Rules

These are the SAME rules defined in US-002 and the [API Contract](../architecture/api-contract.md) section 7. The validator module references the shared Malli schema registry --- it does NOT duplicate rules.

| Field | Type | Required | Constraint | Reject on | Accept on |
| ----- | ---- | -------- | ---------- | --------- | --------- |
| `name` | string | yes | Non-empty after trim; max 255 chars; no XSS payloads | `""`, `"   "`, `"\t\n"`, `<script>...</script>` | `"Running Shoes"`, `"A"`, `"O'Brien's Widget"` |
| `sku` | string | yes | Non-empty; unique within file; upsert against catalog; max 50 chars | `""`, second occurrence in same file | `"RS-001"`, existing catalog SKU (upsert) |
| `description` | string | no | May be empty; max 2000 chars | _(nothing unless over max)_ | `""`, omitted, `"Lightweight shoes"` |
| `category` | string | no | May be empty (uncategorized); max 100 chars | _(nothing unless over max)_ | `""`, omitted, `"Footwear"` |
| `price` | decimal | yes | Strictly > 0; no currency symbols; numeric only | `0`, `"free"`, `"$29.99"`, `-1`, `"abc"` | `89.99`, `0.01`, `1` |
| `stock` | integer | yes | >= 0; whole number | `-1`, `-100`, `1.5`, `"abc"` | `0`, `1`, `150` |
| `weight_kg` | decimal | no | >= 0 when present; may be omitted | `-1`, `-0.5` | `0`, `0.35`, omitted |

### Design Decision: XSS vs SQLi Handling

| Threat | Action | Rationale |
| ------ | ------ | --------- |
| XSS payload (script tags, event handlers) | REJECT row | The payload has no legitimate business value as a product name; rejecting is the safe default |
| SQL injection payload | ACCEPT row (store as literal) | HoneySQL structurally prevents injection via parameterized queries; the payload is harmless as stored data. Rejecting would false-positive on legitimate names with apostrophes (e.g., "O'Brien's Widget"). |

If the team later decides SQLi payloads should also be rejected (defense-in-depth), explicit pattern-matching rules must be added that do not false-positive on legitimate apostrophes.

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| High | XSS detection regex is too aggressive, false-positives on legitimate HTML-like content in names | Use a targeted pattern (script tags, event handlers, javascript: URIs) rather than rejecting all angle brackets. Test with edge cases like "Widget <3 pack>" |
| High | Shared schema changes break CSV import without notice | Single schema registry used by both paths; integration test covers both simultaneously |
| Medium | Duplicate SKU detection across large files is slow with linear scan | Use an in-memory set for within-file duplicate tracking; catalog lookups use indexed SKU column |
| Medium | Upsert behavior surprises Catalog Managers who expected rejection | Document upsert behavior clearly in import response; consider a "dry-run" mode in future |
| Low | Empty row detection misses rows with only delimiters (e.g., `,,,,,,`) | Trim all fields; if every field is empty after trim, classify as empty row |
| Low | Row count reconciliation fails if skipped_empty_rows is not tracked separately | Track skipped count explicitly in the worker; verify reconciliation in integration test |

## 10. Out of Scope

- Import results reporting and error pagination (US-007)
- SSE progress streaming
- Dry-run preview mode
- Export of rejected rows as downloadable file
- Batch/bulk insert optimization (rows processed one at a time)
- Custom delimiter or encoding selection
- Row-level retry or re-import of individual failed rows

## 11. Notes

- The validator module (`validator.clj`) is the bridge between the generic shared Malli schemas (US-002) and the CSV-specific concerns (empty row detection, within-file duplicate tracking, XSS screening). It does NOT redefine field-level rules --- it delegates to the shared schema and adds CSV-specific logic on top.
- Within-file duplicate SKU tracking requires the validator to maintain state (a set of seen SKUs) across rows within a single job. This state is scoped to the job and discarded after processing completes.
- The distinction between XSS rejection and SQLi acceptance is a deliberate design decision documented in the Validation Rules section. Both behaviors are tested explicitly.

## 12. Related Documents

- [EP02 --- CSV Import](../epics/EP02-csv-import.md)
- [API Contract --- Section 7: Validation Contract](../architecture/api-contract.md)
- [Data Model --- products, csv_import_jobs, import_errors](../architecture/data-model.md)
- [Domain Glossary --- Product, CsvImportJob, ImportError, all value objects](../domain-glossary.md)
- [US-001 --- Project Scaffolding](./US-001-project-scaffolding.md) (dependency)
- [US-002 --- Shared Validation Module](./US-002-shared-validation.md) (dependency --- provides Malli schemas)
- [US-005 --- CSV Upload & Background Processing Pipeline](./US-005-csv-upload-processing.md) (dependency --- provides worker/channel)
- [US-007 --- Import Results & Error Reporting](./US-007-import-results-reporting.md) (downstream)

## 13. Handoff Files

TBD

## 14. Change Log

| Date | Author | Change |
| ---- | ------ | ------ |
| 2026-07-27 | Refinement Agent | Initial draft |
