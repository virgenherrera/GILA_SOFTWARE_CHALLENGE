> [INDEX](../INDEX.md) / [User Stories](./) / US-002 --- Create Product with Validation & Sanitization

# US-002 --- Create Product with Validation & Sanitization

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP01 --- Product Management](../epics/EP01-product-management.md) |
| Priority | Must Have |
| Status | Ready |
| Estimation | S |

## 2. Story

As a Catalog Manager, I want to create a new product through the API with full validation and sanitization, so that only well-formed, safe data enters the catalog.

## 3. Definition of Ready

- [x] Domain entity contract frozen
- [x] Interface or API contract frozen
- [x] Input validation rules enumerated with exact boundaries
- [x] Edge cases identified with boundary behavior defined
- [x] Dependencies identified and resolved or deferred
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] API contract endpoints touched by this story are defined
- [x] Role-gate review completed (PO + Dev Lead + SM readiness review 2026-07-28)

## 4. Acceptance Criteria

- [ ] **AC-002.1: Valid product created successfully**
  - **Given** the backend is running and the database is empty
  - **When** `POST /api/products` is called with a valid payload: `{"name": "Running Shoes", "sku": "RS-001", "description": "Lightweight running shoes", "category": "Footwear", "price": 89.99, "stock": 150, "weight_kg": 0.35}`
  - **Then** the response status is `201 Created`; and the response body contains all submitted fields plus server-generated `created_at` and `updated_at` timestamps in ISO 8601 UTC format; and the product is persisted in the `products` table with exact values matching the request

- [ ] **AC-002.2: Name validation rejects invalid inputs**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `name` set to `""` (empty string)
  - **Then** the response is `400 Bad Request` with `{"error": {"code": "VALIDATION_ERROR", "message": "Product validation failed", "details": [{"field": "name", "reason": "Must not be empty"}]}}`
  - **When** `POST /api/products` is called with `name` set to `"   "` (whitespace-only)
  - **Then** the response is `400 Bad Request` with a validation error for `name` indicating it must not be empty (whitespace-only is treated as empty after trim)
  - **When** `POST /api/products` is called with `name` set to `"\t\n"` (tab and newline only)
  - **Then** the response is `400 Bad Request` with a validation error for `name`
  - **When** `POST /api/products` is called with `name` set to a 256-character string
  - **Then** the response is `400 Bad Request` with a validation error for `name` indicating it exceeds the 255 character maximum

- [ ] **AC-002.3: SKU validation and uniqueness**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `sku` set to `""` (empty string)
  - **Then** the response is `400 Bad Request` with a validation error for `sku`
  - **Given** a product with SKU `"RS-001"` already exists in the catalog
  - **When** `POST /api/products` is called with `sku` set to `"RS-001"`
  - **Then** the response is `409 Conflict` with `{"error": {"code": "CONFLICT", "message": "A product with SKU 'RS-001' already exists"}}`
  - **When** `POST /api/products` is called with `sku` set to a 51-character string
  - **Then** the response is `400 Bad Request` with a validation error for `sku` indicating it exceeds the 50 character maximum
  - **Given** a product with SKU `"rs-001"` exists
  - **When** `POST /api/products` is called with `sku` set to `"RS-001"`
  - **Then** the response is `201 Created` (SKU comparison is case-sensitive; `"RS-001"` and `"rs-001"` are distinct)

- [ ] **AC-002.4: Price validation rejects invalid inputs**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `price` set to `0`
  - **Then** the response is `400 Bad Request` with a validation error for `price` indicating it must be a positive number
  - **When** `POST /api/products` is called with `price` set to `-1`
  - **Then** the response is `400 Bad Request` with a validation error for `price`
  - **When** `POST /api/products` is called with `price` set to `"free"`
  - **Then** the response is `400 Bad Request` with a validation error for `price`
  - **When** `POST /api/products` is called with `price` set to `"$29.99"`
  - **Then** the response is `400 Bad Request` with a validation error for `price` (currency symbols not accepted)
  - **When** `POST /api/products` is called with `price` set to `"abc"`
  - **Then** the response is `400 Bad Request` with a validation error for `price`
  - **When** `POST /api/products` is called with `price` set to `29.999`
  - **Then** the response is `400 Bad Request` with a validation error for `price` indicating max 2 decimal places

- [ ] **AC-002.5: Stock validation rejects invalid inputs**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `stock` set to `-1`
  - **Then** the response is `400 Bad Request` with a validation error for `stock`
  - **When** `POST /api/products` is called with `stock` set to `1.5`
  - **Then** the response is `400 Bad Request` with a validation error for `stock` indicating it must be a whole number
  - **When** `POST /api/products` is called with `stock` absent from the payload
  - **Then** the response is `400 Bad Request` with a validation error for `stock` (must not be silently defaulted to zero)

- [ ] **AC-002.6: Weight validation rejects negative values**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `weight_kg` set to `-1`
  - **Then** the response is `400 Bad Request` with a validation error for `weight_kg`
  - **When** `POST /api/products` is called with `weight_kg` set to `-0.5`
  - **Then** the response is `400 Bad Request` with a validation error for `weight_kg`
  - **When** `POST /api/products` is called with `weight_kg` omitted from the payload
  - **Then** the response is `201 Created` (weight is optional; omission is valid)

- [ ] **AC-002.7: Category accepts empty and optional values**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `category` set to `""` (empty string)
  - **Then** the response is `201 Created` (empty category means uncategorized)
  - **When** `POST /api/products` is called with `category` omitted from the payload
  - **Then** the response is `201 Created`
  - **When** `POST /api/products` is called with `category` set to a 101-character string
  - **Then** the response is `400 Bad Request` with a validation error for `category` indicating it exceeds the 100 character maximum

- [ ] **AC-002.8: Description accepts empty and optional values**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `description` set to `""` (empty string)
  - **Then** the response is `201 Created`
  - **When** `POST /api/products` is called with `description` omitted from the payload
  - **Then** the response is `201 Created`
  - **When** `POST /api/products` is called with `description` set to a 2001-character string
  - **Then** the response is `400 Bad Request` with a validation error for `description` indicating it exceeds the 2000 character maximum

- [ ] **AC-002.9: XSS payload in name is sanitized or rejected**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `name` set to `"<script>alert(document.cookie)</script>"`
  - **Then** the payload is either rejected at validation or sanitized (encoded/escaped) before storage; and querying the product back via `GET /api/products/:sku` never returns the raw `<script>` tag as executable markup; and the stored value in the database is either absent (rejected) or encoded (e.g., `&lt;script&gt;...`)

- [ ] **AC-002.10: SQL injection payload in name is neutralized**
  - **Given** the backend is running and the `products` table exists with existing data
  - **When** `POST /api/products` is called with `name` set to `"Robert'); DROP TABLE products;--"`
  - **Then** the payload is stored as an inert literal string via parameterized query; and the `products` table is intact and unaffected after the operation; and querying `SELECT count(*) FROM products` returns the expected count (no rows lost)

- [ ] **AC-002.11: Multiple validation errors returned simultaneously**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `{"name": "", "sku": "", "price": -1, "stock": -1}`
  - **Then** the response is `400 Bad Request` with `details` array containing validation errors for ALL four fields (`name`, `sku`, `price`, `stock`), not just the first field that failed

- [ ] **AC-002.12: Error responses never leak internal details**
  - **Given** the backend is running
  - **When** any validation error or server error occurs during product creation
  - **Then** the error response body never contains stack traces, exception class names, raw SQL fragments, query plans, file system paths, or internal hostnames

- [ ] **AC-002.13: Free-text fields are sanitized for safe rendering**
  - **Given** the backend is running
  - **When** `POST /api/products` is called with `description` set to `"<img src=x onerror=alert(1)>"` and `category` set to `"<b>Bold</b>"`
  - **Then** the stored values in the database are encoded for safe rendering (HTML entities escaped); and querying the product back returns the sanitized values, never raw HTML/JavaScript

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Unit tests green for domain logic and validation
- [ ] Integration tests green against real dependencies
- [ ] No regressions in existing test suite
- [ ] Error responses conform to agreed shape
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `src/ecommerce/product/handler.clj` | `POST /api/products` endpoint handler; validates input via shared Malli schemas; calls repository for persistence; returns 201/400/409 responses in error-envelope format |
| `src/ecommerce/product/repository.clj` | Product DB operations; `insert!` function using HoneySQL + `next.jdbc`; catches PostgreSQL unique constraint violation (error code 23505) and translates to CONFLICT response |
| `test/ecommerce/product/validation_test.clj` | Unit tests for all product field validation rules; one test per boundary condition |
| `test/ecommerce/product/handler_integration_test.clj` | Integration tests against a real PostgreSQL instance (Testcontainers); tests full HTTP request/response cycle |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `src/ecommerce/router.clj` | Add `POST /api/products` route pointing to `product.handler/create` with Malli request coercion |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `valid-product-returns-201` | AC-002.1 | POST with valid payload returns 201 with all fields + timestamps |
| `valid-product-persisted-to-db` | AC-002.1 | After POST, SELECT from products table returns matching row |
| `empty-name-rejected` | AC-002.2 | `name: ""` returns 400 with field error for `name` |
| `whitespace-only-name-rejected` | AC-002.2 | `name: "   "` returns 400 with field error for `name` |
| `tab-newline-name-rejected` | AC-002.2 | `name: "\t\n"` returns 400 with field error for `name` |
| `name-exceeding-255-chars-rejected` | AC-002.2 | 256-char name returns 400 with field error for `name` |
| `name-at-255-chars-accepted` | AC-002.2 | 255-char name returns 201 |
| `empty-sku-rejected` | AC-002.3 | `sku: ""` returns 400 with field error for `sku` |
| `duplicate-sku-returns-409` | AC-002.3 | Second POST with same SKU returns 409 CONFLICT |
| `sku-exceeding-50-chars-rejected` | AC-002.3 | 51-char SKU returns 400 with field error for `sku` |
| `sku-at-50-chars-accepted` | AC-002.3 | 50-char SKU returns 201 |
| `sku-case-sensitive-distinct` | AC-002.3 | `"RS-001"` and `"rs-001"` are treated as different SKUs |
| `price-zero-rejected` | AC-002.4 | `price: 0` returns 400 with field error for `price` |
| `price-negative-rejected` | AC-002.4 | `price: -1` returns 400 with field error for `price` |
| `price-string-free-rejected` | AC-002.4 | `price: "free"` returns 400 with field error for `price` |
| `price-currency-symbol-rejected` | AC-002.4 | `price: "$29.99"` returns 400 with field error for `price` |
| `price-non-numeric-rejected` | AC-002.4 | `price: "abc"` returns 400 with field error for `price` |
| `price-three-decimals-rejected` | AC-002.4 | `price: 29.999` returns 400 with field error for `price` |
| `price-two-decimals-accepted` | AC-002.4 | `price: 29.99` returns 201 |
| `price-one-cent-accepted` | AC-002.4 | `price: 0.01` returns 201 |
| `price-whole-number-accepted` | AC-002.4 | `price: 1` returns 201 |
| `stock-negative-rejected` | AC-002.5 | `stock: -1` returns 400 with field error for `stock` |
| `stock-fractional-rejected` | AC-002.5 | `stock: 1.5` returns 400 with field error for `stock` |
| `stock-absent-rejected` | AC-002.5 | Payload without `stock` key returns 400 with field error for `stock` |
| `stock-zero-accepted` | AC-002.5 | `stock: 0` returns 201 |
| `weight-negative-rejected` | AC-002.6 | `weight_kg: -1` returns 400 with field error for `weight_kg` |
| `weight-negative-fractional-rejected` | AC-002.6 | `weight_kg: -0.5` returns 400 with field error for `weight_kg` |
| `weight-omitted-accepted` | AC-002.6 | Payload without `weight_kg` returns 201 |
| `weight-zero-accepted` | AC-002.6 | `weight_kg: 0` returns 201 |
| `category-empty-accepted` | AC-002.7 | `category: ""` returns 201 |
| `category-omitted-accepted` | AC-002.7 | Payload without `category` returns 201 |
| `category-exceeding-100-chars-rejected` | AC-002.7 | 101-char category returns 400 |
| `category-at-100-chars-accepted` | AC-002.7 | 100-char category returns 201 |
| `description-empty-accepted` | AC-002.8 | `description: ""` returns 201 |
| `description-omitted-accepted` | AC-002.8 | Payload without `description` returns 201 |
| `description-exceeding-2000-chars-rejected` | AC-002.8 | 2001-char description returns 400 |
| `description-at-2000-chars-accepted` | AC-002.8 | 2000-char description returns 201 |
| `xss-in-name-neutralized` | AC-002.9 | `<script>alert(1)</script>` in name is sanitized or rejected; GET returns no raw script tag |
| `xss-img-onerror-neutralized` | AC-002.9 | `<img src=x onerror=alert(1)>` in name is sanitized or rejected |
| `sqli-in-name-neutralized` | AC-002.10 | `'; DROP TABLE products;--` stored as literal; products table intact |
| `sqli-or-true-neutralized` | AC-002.10 | `' OR '1'='1` stored as literal; no extra rows returned |
| `multiple-errors-returned-simultaneously` | AC-002.11 | Invalid name + sku + price + stock returns all four field errors in `details` |
| `error-response-no-stack-trace` | AC-002.12 | Error response body does not contain Java class names or `.clj` paths |
| `error-response-no-sql-leak` | AC-002.12 | Error response body does not contain raw SQL keywords in query context |
| `description-xss-sanitized` | AC-002.13 | `<img src=x onerror=alert(1)>` in description is encoded before storage |
| `category-html-sanitized` | AC-002.13 | `<b>Bold</b>` in category is encoded before storage |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `name` | yes | string | Non-empty after trim; max 255 chars | `""`, `"   "`, `"\t\n"`, 256+ chars |
| `sku` | yes | string | Non-empty, unique, case-sensitive; max 50 chars | `""`, duplicate SKU |
| `description` | no | string | Max 2000 chars | 2001+ chars |
| `category` | no | string | Max 100 chars | 101+ chars |
| `price` | yes | decimal | `> 0`; max 2 decimal places | `0`, `-1`, `"free"`, `"$29.99"`, `29.999` |
| `stock` | yes | integer | `>= 0` | `-1`, `1.5`, absent, `"abc"` |
| `weight_kg` | no | decimal | `>= 0` when present | `-1`, `-0.5` |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | Sanitization strategy not yet finalized (encode vs. reject for XSS payloads) | Decide during implementation: encoding is more permissive (allows legitimate angle brackets in product names), rejection is safer. Document the chosen approach in the handler. Both approaches must prevent stored XSS. |
| MEDIUM | Malli coercion may silently coerce `"29.99"` (string) to `29.99` (number), masking type validation | Verify Malli's JSON coercion behavior for decimal fields; add explicit tests for string-wrapped numeric values |
| MEDIUM | PostgreSQL unique constraint error (23505) message format may vary across driver versions | Catch by SQLSTATE code, not by message string; test against the actual PostgreSQL JDBC driver version pinned in deps.edn |
| LOW | Simultaneous creation of same SKU by two requests (race condition) | PostgreSQL UNIQUE constraint handles this at the DB level; the duplicate is rejected with 23505 regardless of application-level checks |

## 10. Out of Scope

- Read/List/Update/Delete product operations --- covered by US-003, US-004, and subsequent stories
- Frontend product creation form UI --- covered by EP05 stories
- CSV import path --- covered by EP02 stories
- Search indexing --- handled automatically by the `products_search_vector_trigger` created in US-001
- Pagination of product listing
- Authentication or authorization

## 11. Notes

- The handler must use the shared Malli schemas from `src/ecommerce/validation.clj` (created in US-001), not define its own validation rules. This ensures the single-source-of-truth contract is enforced.
- The `created_at` and `updated_at` fields are set by the database defaults (`now()`), not by the application. The handler must not accept or pass these fields from the request body.
- The SKU is immutable after creation. This AC is not tested here (it applies to update in US-003), but the handler must not set any expectation that SKU can be changed post-creation.
- All database operations use HoneySQL for query generation, which produces parameterized SQL structurally. This is the primary defense against SQL injection --- the handler never concatenates user input into query strings.

## 12. Related Documents

- [API Contract --- POST /api/products](../architecture/api-contract.md#post-apiproducts) --- request/response shapes
- [API Contract --- Validation Contract](../architecture/api-contract.md#7-validation-contract) --- field-level rules
- [Data Model --- products table](../architecture/data-model.md#21-products) --- column types and constraints
- [Testing Strategy --- Security Test Cases](../architecture/testing-strategy.md#5-security-test-cases) --- XSS and SQLi test requirements
- [EP01 --- Product Management](../epics/EP01-product-management.md) --- parent epic acceptance boundaries

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
