> [INDEX](../INDEX.md) / [User Stories](./) / US-004 --- Delete Product

# US-004 --- Delete Product

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP01 --- Product Management](../epics/EP01-product-management.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

As a Catalog Manager, I want to delete a product from the catalog, so that discontinued or erroneously created items no longer appear.

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

- [ ] **AC-004.1: Delete unreferenced product succeeds**
  - **Given** a product with SKU `"RS-001"` exists in the catalog and is NOT referenced by any `cart_items` or `order_items` rows
  - **When** `DELETE /api/products/RS-001` is called
  - **Then** the response status is `204 No Content` with an empty response body; and the product no longer exists in the `products` table; and a subsequent `GET /api/products/RS-001` returns `404 Not Found`

- [ ] **AC-004.2: Non-existent product returns 404**
  - **Given** no product with SKU `"NONEXISTENT"` exists in the catalog
  - **When** `DELETE /api/products/NONEXISTENT` is called
  - **Then** the response is `404 Not Found` with `{"error": {"code": "NOT_FOUND", "message": "Product not found"}}`

- [ ] **AC-004.3: Product referenced by order_items returns 409 PRODUCT_IN_USE**
  - **Given** a product with SKU `"RS-001"` exists in the catalog; and an order exists with an `order_items` row referencing `product_sku = "RS-001"`
  - **When** `DELETE /api/products/RS-001` is called
  - **Then** the response is `409 Conflict` with `{"error": {"code": "PRODUCT_IN_USE", "message": "Cannot delete product 'RS-001': referenced by existing orders", "details": [{"field": "sku", "reason": "Product is referenced by N order(s)"}]}}` where N is the number of orders referencing this product; and the product remains in the `products` table (not deleted); and the `order_items` rows are unaffected

- [ ] **AC-004.4: Product referenced by cart_items (active cart) returns 409 PRODUCT_IN_USE**
  - **Given** a product with SKU `"RS-001"` exists in the catalog; and a cart with status `"Active"` has a `cart_items` row referencing `product_sku = "RS-001"`
  - **When** `DELETE /api/products/RS-001` is called
  - **Then** the response is `409 Conflict` with `{"error": {"code": "PRODUCT_IN_USE", "message": "Cannot delete product 'RS-001': referenced by existing orders"}}` (the generic message covers both order and cart references); and the product remains in the `products` table; and the `cart_items` row is unaffected

- [ ] **AC-004.5: OrderItems retain historical snapshot after product deletion**
  - **Given** a product with SKU `"TEMP-001"` was previously ordered (creating `order_items` with `unit_price: 49.99`, `quantity: 2`, `line_subtotal: 99.98`); and the product has since been deleted from the catalog (hypothetical --- this can only happen if the FK constraint is relaxed in a future version, or tested via the unreferenced-delete path where the order referenced a different product)
  - **Then** for the purpose of this story: if a product is unreferenced and deleted, the deletion does not cascade to unrelated order_items; and order_items rows for OTHER products remain intact with their original `unit_price` and `line_subtotal` snapshots

- [ ] **AC-004.6: Deleted product no longer appears in search results or listing**
  - **Given** a product with SKU `"RS-001"` exists and is unreferenced
  - **When** `DELETE /api/products/RS-001` is called and returns 204
  - **Then** a subsequent `GET /api/products?q=Running` (search) does not include `"RS-001"` in the results; and a subsequent `GET /api/products` (listing) does not include `"RS-001"` in the items array

- [ ] **AC-004.7: FK violation error never leaks raw SQL constraint name**
  - **Given** a product with SKU `"RS-001"` is referenced by `order_items` or `cart_items`
  - **When** `DELETE /api/products/RS-001` is called and the DELETE fails due to PostgreSQL foreign key violation (error code 23503)
  - **Then** the response body contains `{"error": {"code": "PRODUCT_IN_USE", ...}}` with a clean, human-readable message; and the response never contains the raw PostgreSQL constraint name (e.g., `cart_items_product_sku_fkey` or `order_items_product_sku_fkey`); and the response never contains raw SQL error messages, SQLSTATE codes, or internal identifiers

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
| `test/ecommerce/product/delete_test.clj` | Integration tests against PostgreSQL: successful delete, 404 for non-existent, FK violation with order_items, FK violation with cart_items, verify product absent from listing/search after delete, verify error response shape hides constraint names |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `src/ecommerce/product/handler.clj` | Add `DELETE /api/products/:sku` handler; checks product existence first (404 if missing); attempts DELETE; catches PostgreSQL FK violation (SQLSTATE 23503) and translates to PRODUCT_IN_USE error |
| `src/ecommerce/product/repository.clj` | Add `delete!` function; executes `DELETE FROM products WHERE sku = ?`; catches `PSQLException` with SQLSTATE 23503; queries `order_items` and `cart_items` to count references for the error message; returns structured result (deleted, not-found, or in-use) |
| `src/ecommerce/router.clj` | Add `DELETE /api/products/:sku` route pointing to `product.handler/delete` |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `delete-unreferenced-returns-204` | AC-004.1 | DELETE of unreferenced product returns 204 with empty body |
| `delete-removes-product-from-db` | AC-004.1 | After DELETE, SELECT from products WHERE sku = ? returns zero rows |
| `delete-product-then-get-returns-404` | AC-004.1 | GET /api/products/:sku after DELETE returns 404 |
| `delete-nonexistent-returns-404` | AC-004.2 | DELETE of non-existent SKU returns 404 NOT_FOUND |
| `delete-404-error-shape` | AC-004.2 | 404 response has `{"error": {"code": "NOT_FOUND", "message": "Product not found"}}` |
| `delete-product-with-order-items-returns-409` | AC-004.3 | DELETE of product referenced by order_items returns 409 PRODUCT_IN_USE |
| `delete-order-ref-product-still-exists` | AC-004.3 | After 409, product remains in products table |
| `delete-order-ref-order-items-intact` | AC-004.3 | After 409, order_items rows are unaffected |
| `delete-order-ref-error-includes-count` | AC-004.3 | 409 response details include count of referencing orders |
| `delete-product-with-active-cart-returns-409` | AC-004.4 | DELETE of product referenced by active cart's cart_items returns 409 PRODUCT_IN_USE |
| `delete-cart-ref-product-still-exists` | AC-004.4 | After 409 (cart reference), product remains in products table |
| `delete-cart-ref-cart-items-intact` | AC-004.4 | After 409 (cart reference), cart_items row is unaffected |
| `delete-does-not-cascade-to-other-order-items` | AC-004.5 | Deleting product A does not affect order_items referencing product B |
| `deleted-product-absent-from-search` | AC-004.6 | After DELETE, GET /api/products?q=<name> does not include deleted SKU |
| `deleted-product-absent-from-listing` | AC-004.6 | After DELETE, GET /api/products does not include deleted SKU in items |
| `fk-violation-error-no-constraint-name` | AC-004.7 | 409 PRODUCT_IN_USE response does not contain `_fkey`, `_pkey`, or raw constraint names |
| `fk-violation-error-no-sqlstate` | AC-004.7 | 409 response does not contain `23503` or `SQLSTATE` text |
| `fk-violation-error-no-raw-sql` | AC-004.7 | 409 response does not contain `DELETE FROM`, `INSERT INTO`, or raw SQL keywords |
| `fk-violation-error-shape` | AC-004.7 | 409 response matches `{"error": {"code": "PRODUCT_IN_USE", "message": "...", "details": [...]}}` |
| `delete-idempotent-second-call-404` | AC-004.1, AC-004.2 | Second DELETE on same SKU returns 404 (product already gone) |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `:sku` (path param) | yes | string | Must identify an existing product | Non-existent SKU returns 404 |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | PostgreSQL FK violation error (23503) may be thrown from either `cart_items` or `order_items` reference, and the raw error message varies by which constraint fires first | Catch SQLSTATE 23503 generically; then query both `order_items` and `cart_items` for the given SKU to build an accurate count for the error message, rather than parsing the constraint name from the exception |
| MEDIUM | Race condition: product is checked for references, found clean, then a cart item is added before the DELETE executes | The FK constraint at the database level handles this atomically: if a cart_items row is inserted between the check and the DELETE, the DELETE will fail with 23503 and the handler will return PRODUCT_IN_USE. No application-level locking needed. |
| LOW | Counting references (orders + carts) requires additional queries after the FK violation | Acceptable performance cost: this is an error path, not the happy path. The extra queries happen only when deletion is already blocked. |

## 10. Out of Scope

- Soft delete or `is_active` flag --- this story implements hard delete only, per the domain model
- Cascade delete behaviors --- FK constraints prevent deletion of referenced products
- Warning UI before deleting a product with purchase history --- deferred to v2 per EP01 "Should Have" story
- Bulk delete operations
- Frontend delete confirmation dialog --- covered by EP05 stories
- Archival or tombstone records for deleted products

## 11. Notes

- This is a HARD delete. The product row is physically removed from the `products` table. The FK constraints on `cart_items.product_sku` and `order_items.product_sku` prevent deletion when references exist.
- The handler catches PostgreSQL error code 23503 (foreign key violation) and translates it to the `PRODUCT_IN_USE` error code. It does NOT parse the constraint name from the exception message --- instead, it queries `order_items` and `cart_items` to determine the reference count.
- The EP01 epic lists "Should Have --- warning before delete with purchase history" as a separate story. This is explicitly deferred to v2. The MVP implements the functional block (409 PRODUCT_IN_USE) without the proactive warning UI.
- The `search_vector` column is maintained by a database trigger. Deleting the product row automatically removes it from the GIN index. No additional cleanup is needed.
- Idempotency note: calling DELETE twice on the same SKU returns 204 on the first call and 404 on the second. This is correct REST behavior (the resource no longer exists).

## 12. Related Documents

- [API Contract --- DELETE /api/products/:sku](../architecture/api-contract.md#delete-apiproductssku) --- response shapes including PRODUCT_IN_USE
- [Data Model --- products table](../architecture/data-model.md#21-products) --- hard delete behavior
- [Data Model --- cart_items table](../architecture/data-model.md#23-cart_items) --- FK to products.sku (no cascade)
- [Data Model --- order_items table](../architecture/data-model.md#25-order_items) --- FK to products.sku (no cascade)
- [EP01 --- Product Management](../epics/EP01-product-management.md) --- parent epic; "Should Have" warning deferred
- [US-002 --- Create Product](US-002-create-product.md) --- creates test fixture products
- US-010 (future) --- will test the order reference path end-to-end through the purchase workflow

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
