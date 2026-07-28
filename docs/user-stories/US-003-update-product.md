> [INDEX](../INDEX.md) / [User Stories](./) / US-003 --- Update Product

# US-003 --- Update Product

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP01 --- Product Management](../epics/EP01-product-management.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

As a Catalog Manager, I want to update an existing product's fields, so that I can correct mistakes or reflect changes in price, stock, or description.

## 3. Definition of Ready

- [x] Domain entity contract frozen
- [x] Interface or API contract frozen
- [x] Input validation rules enumerated with exact boundaries
- [x] Edge cases identified with boundary behavior defined
- [x] Dependencies identified and resolved or deferred
- [x] Test plan exists with test names mapped to ACs
- [x] Out-of-scope items listed
- [x] API contract endpoints touched by this story are defined

## 4. Acceptance Criteria

- [ ] **AC-003.1: Valid update succeeds**
  - **Given** a product with SKU `"RS-001"` exists in the catalog with `price: 89.99` and `stock: 150`
  - **When** `PUT /api/products/RS-001` is called with `{"name": "Running Shoes v2", "description": "Updated lightweight running shoes", "category": "Footwear", "price": 94.99, "stock": 120, "weight_kg": 0.34}`
  - **Then** the response status is `200 OK`; and the response body contains the updated product with `sku: "RS-001"`, `name: "Running Shoes v2"`, `price: 94.99`, `stock: 120`; and `updated_at` is later than the original `updated_at`; and `created_at` remains unchanged from the original value

- [ ] **AC-003.2: Same validation rules as create are enforced**
  - **Given** a product with SKU `"RS-001"` exists in the catalog
  - **When** `PUT /api/products/RS-001` is called with `name` set to `""` (empty string)
  - **Then** the response is `400 Bad Request` with a validation error for `name` (same rule as AC-002.2)
  - **When** `PUT /api/products/RS-001` is called with `price` set to `0`
  - **Then** the response is `400 Bad Request` with a validation error for `price` (same rule as AC-002.4)
  - **When** `PUT /api/products/RS-001` is called with `stock` set to `-1`
  - **Then** the response is `400 Bad Request` with a validation error for `stock` (same rule as AC-002.5)
  - **When** `PUT /api/products/RS-001` is called with `price` set to `29.999`
  - **Then** the response is `400 Bad Request` with a validation error for `price` (max 2 decimal places, same rule as AC-002.4)
  - **When** `PUT /api/products/RS-001` is called with `name` set to a 256-character string
  - **Then** the response is `400 Bad Request` with a validation error for `name` (max 255 chars)
  - **When** `PUT /api/products/RS-001` is called with `weight_kg` set to `-1`
  - **Then** the response is `400 Bad Request` with a validation error for `weight_kg`
  - **When** `PUT /api/products/RS-001` is called with multiple invalid fields
  - **Then** the response includes ALL field errors simultaneously (same behavior as AC-002.11)

- [ ] **AC-003.3: SKU is immutable --- ignored or rejected in request body**
  - **Given** a product with SKU `"RS-001"` exists in the catalog
  - **When** `PUT /api/products/RS-001` is called with a request body that includes `"sku": "NEW-SKU"`
  - **Then** the `sku` field in the request body is ignored; the product retains SKU `"RS-001"`; the response body shows `sku: "RS-001"`; and no product with SKU `"NEW-SKU"` is created
  - **When** `PUT /api/products/RS-001` is called with a request body that includes `"sku": "RS-001"` (same as URL)
  - **Then** the request succeeds normally; the redundant SKU in the body is ignored

- [ ] **AC-003.4: Non-existent product returns 404**
  - **Given** no product with SKU `"NONEXISTENT"` exists in the catalog
  - **When** `PUT /api/products/NONEXISTENT` is called with a valid update payload
  - **Then** the response is `404 Not Found` with `{"error": {"code": "NOT_FOUND", "message": "Product not found"}}`

- [ ] **AC-003.5: XSS and SQL injection payloads are sanitized identically to create path**
  - **Given** a product with SKU `"RS-001"` exists in the catalog
  - **When** `PUT /api/products/RS-001` is called with `name` set to `"<script>alert(document.cookie)</script>"`
  - **Then** the payload is sanitized or rejected identically to the behavior defined in AC-002.9
  - **When** `PUT /api/products/RS-001` is called with `name` set to `"Robert'); DROP TABLE products;--"`
  - **Then** the payload is stored as an inert literal via parameterized query; the `products` table is intact (same behavior as AC-002.10)
  - **When** `PUT /api/products/RS-001` is called with `description` set to `"<img src=x onerror=alert(1)>"`
  - **Then** the description is sanitized before storage (same behavior as AC-002.13)

- [ ] **AC-003.6: Updating price does NOT affect existing cart item snapshots**
  - **Given** a product with SKU `"RS-001"` exists with `price: 89.99`; and a cart item references `"RS-001"` with `unit_price_snapshot: 89.99`
  - **When** `PUT /api/products/RS-001` is called with `price: 94.99`
  - **Then** the product's `price` is updated to `94.99` in the `products` table; and the `cart_items` row for `"RS-001"` still has `unit_price_snapshot: 89.99` (snapshot is immune to price changes)

- [ ] **AC-003.7: Error responses never leak internal details**
  - **Given** the backend is running
  - **When** any error occurs during product update (validation failure, not found, server error)
  - **Then** the error response body never contains stack traces, exception class names, raw SQL fragments, query plans, file system paths, or internal hostnames

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
| `test/ecommerce/product/update_test.clj` | Unit tests for update-specific logic (SKU immutability, reuse of shared validation) and integration tests against PostgreSQL (update persistence, 404 handling, snapshot immunity, security payloads) |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `src/ecommerce/product/handler.clj` | Add `PUT /api/products/:sku` handler; validate input via shared Malli schemas (same as create); strip or ignore `sku` from request body; call repository `update!` |
| `src/ecommerce/product/repository.clj` | Add `update!` function using HoneySQL; returns nil when SKU not found (handler translates to 404); uses `SET updated_at = now()` |
| `src/ecommerce/router.clj` | Add `PUT /api/products/:sku` route pointing to `product.handler/update` with Malli request coercion |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `valid-update-returns-200` | AC-003.1 | PUT with valid payload returns 200 with updated fields |
| `updated-at-changes-on-update` | AC-003.1 | `updated_at` in response is later than original `updated_at` |
| `created-at-unchanged-on-update` | AC-003.1 | `created_at` remains identical after update |
| `update-persisted-to-db` | AC-003.1 | After PUT, SELECT from products confirms updated values |
| `update-empty-name-rejected` | AC-003.2 | `name: ""` on update returns 400 (reuses shared validation) |
| `update-whitespace-name-rejected` | AC-003.2 | `name: "   "` on update returns 400 |
| `update-name-exceeding-255-rejected` | AC-003.2 | 256-char name on update returns 400 |
| `update-price-zero-rejected` | AC-003.2 | `price: 0` on update returns 400 |
| `update-price-negative-rejected` | AC-003.2 | `price: -1` on update returns 400 |
| `update-price-three-decimals-rejected` | AC-003.2 | `price: 29.999` on update returns 400 |
| `update-stock-negative-rejected` | AC-003.2 | `stock: -1` on update returns 400 |
| `update-weight-negative-rejected` | AC-003.2 | `weight_kg: -1` on update returns 400 |
| `update-multiple-errors-returned` | AC-003.2 | Multiple invalid fields return all errors simultaneously |
| `sku-in-body-ignored` | AC-003.3 | `"sku": "NEW-SKU"` in body is ignored; product retains original SKU |
| `sku-in-body-same-as-url-accepted` | AC-003.3 | `"sku": "RS-001"` in body does not cause error |
| `no-product-with-new-sku-created` | AC-003.3 | After update with `"sku": "NEW-SKU"` in body, no product `"NEW-SKU"` exists |
| `update-nonexistent-returns-404` | AC-003.4 | PUT to non-existent SKU returns 404 NOT_FOUND |
| `update-404-error-shape` | AC-003.4 | 404 response has `{"error": {"code": "NOT_FOUND", "message": "Product not found"}}` |
| `update-xss-in-name-neutralized` | AC-003.5 | `<script>` tag in name is sanitized on update path |
| `update-sqli-in-name-neutralized` | AC-003.5 | SQLi payload stored as literal; products table intact |
| `update-xss-in-description-sanitized` | AC-003.5 | `<img onerror>` in description is encoded on update path |
| `price-change-does-not-affect-cart-snapshot` | AC-003.6 | After price update, cart_items.unit_price_snapshot remains at original value |
| `update-error-no-stack-trace` | AC-003.7 | Error responses contain no Java class names or file paths |
| `update-error-no-sql-leak` | AC-003.7 | Error responses contain no raw SQL fragments |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `name` | yes | string | Non-empty after trim; max 255 chars | `""`, `"   "`, `"\t\n"`, 256+ chars |
| `sku` | ignored | --- | Immutable; stripped from request body before validation | Any value (silently ignored) |
| `description` | no | string | Max 2000 chars | 2001+ chars |
| `category` | no | string | Max 100 chars | 101+ chars |
| `price` | yes | decimal | `> 0`; max 2 decimal places | `0`, `-1`, `"free"`, `"$29.99"`, `29.999` |
| `stock` | yes | integer | `>= 0` | `-1`, `1.5`, absent, `"abc"` |
| `weight_kg` | no | decimal | `>= 0` when present | `-1`, `-0.5` |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| MEDIUM | Race condition between price update and concurrent cart addition (cart reads old price, product updates, cart persists old snapshot) | This is acceptable behavior by design: the `unit_price_snapshot` captures the price at add-to-cart time. Document this as intentional, not a bug. |
| MEDIUM | Validation logic drift between create and update paths if not sharing the same schema | Both handlers must import and use the shared Malli schemas from `validation.clj`. The update handler uses the same schema as create, minus the SKU field. Add a test that exercises the same invalid inputs against both endpoints. |
| LOW | `updated_at` precision may differ between application timestamp and database `now()` | Use database-side `now()` in the UPDATE SQL, not application-side timestamp generation, to ensure `updated_at` is consistent with the database clock |

## 10. Out of Scope

- Cart recalculation when product price changes (cart retains its snapshot; this is by design)
- Price history tracking or audit trail of price changes
- Partial update (PATCH semantics) --- PUT requires all fields
- Frontend product edit form UI --- covered by EP05 stories
- Optimistic concurrency control (no `If-Match` / ETag mechanism in v1)

## 11. Notes

- The update handler reuses the exact same Malli validation schema as the create handler, with one difference: the `sku` field is stripped from the request body before validation. The SKU comes exclusively from the URL path parameter.
- The `updated_at` column is set to `now()` in the SQL UPDATE statement, not passed from the client. This ensures the timestamp reflects the actual moment of persistence.
- The API contract specifies that `PUT /api/products/:sku` is a full replacement: all required fields must be present in the request body. Omitting `name`, `price`, or `stock` results in a validation error, not a partial update.
- The snapshot immunity test (AC-003.6) requires a cart item to exist referencing the product. This test depends on the cart infrastructure created in US-001 migrations but does not depend on the cart API (US-010+). The test seeds the `cart_items` row directly via SQL.

## 12. Related Documents

- [API Contract --- PUT /api/products/:sku](../architecture/api-contract.md#put-apiproductssku) --- request/response shapes
- [API Contract --- Validation Contract](../architecture/api-contract.md#7-validation-contract) --- field-level rules (same as create)
- [Data Model --- products table](../architecture/data-model.md#21-products) --- `updated_at` column behavior
- [Data Model --- cart_items table](../architecture/data-model.md#23-cart_items) --- `unit_price_snapshot` immutability
- [EP01 --- Product Management](../epics/EP01-product-management.md) --- parent epic
- [US-002 --- Create Product](US-002-create-product.md) --- shares validation rules and sanitization contract

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
