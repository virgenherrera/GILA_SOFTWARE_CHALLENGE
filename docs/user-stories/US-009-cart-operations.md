> [INDEX](../INDEX.md) / [User Stories](./) / US-009 --- Cart Operations

# US-009 --- Cart Operations

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP04 --- Purchase Workflow](../epics/EP04-purchase-workflow.md) |
| Priority | Must Have |
| Status | Ready |
| Estimation | M |

## 2. Story

As a Shopper, I want to add products to my cart, view the cart, update quantities, and remove items, so that I can assemble my purchase before checkout.

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

- [ ] **AC-009.1: Add valid product to empty cart**
  - **Given** a Shopper with no existing cart (no `cart_id` cookie)
  - **And** a product with SKU `"RS-001"` exists with price `89.99` and stock `150`
  - **When** the Shopper sends `POST /api/cart/items` with body `{ "product_sku": "RS-001", "quantity": 2 }`
  - **Then** a new cart is created with a UUID `cart_id`
  - **And** the response sets a signed, `HttpOnly`, `SameSite=Strict` cookie containing the `cart_id`
  - **And** the response status is `200 OK`
  - **And** the response body is the full cart object with one item: `product_sku` = `"RS-001"`, `quantity` = `2`, `unit_price_snapshot` = `89.99`, `subtotal` = `179.98`
  - **And** `total` = `179.98`

- [ ] **AC-009.2: Add product already in cart increases quantity**
  - **Given** a Shopper has a cart with product `"RS-001"` at quantity `2` and `unit_price_snapshot` = `89.99`
  - **And** product `"RS-001"` has stock `150`
  - **When** the Shopper sends `POST /api/cart/items` with body `{ "product_sku": "RS-001", "quantity": 3 }`
  - **Then** the response status is `200 OK`
  - **And** the existing cart item quantity is now `5`
  - **And** `unit_price_snapshot` remains `89.99` (unchanged from the original add)
  - **And** `subtotal` = `449.95`
  - **And** `total` is recalculated to reflect the updated quantity

- [ ] **AC-009.3: Add quantity exceeding stock is rejected**
  - **Given** a product with SKU `"HL-003"` exists with stock `5`
  - **And** the Shopper has no items in the cart for this product
  - **When** the Shopper sends `POST /api/cart/items` with body `{ "product_sku": "HL-003", "quantity": 10 }`
  - **Then** the response status is `409 Conflict`
  - **And** the error code is `INSUFFICIENT_STOCK`
  - **And** the response includes `details` with available quantity (`5`)
  - **And** no cart item is created

- [ ] **AC-009.4: Add where existing cart qty plus requested qty exceeds stock**
  - **Given** a product with SKU `"HL-003"` exists with stock `5`
  - **And** the Shopper has a cart item for `"HL-003"` with quantity `3`
  - **When** the Shopper sends `POST /api/cart/items` with body `{ "product_sku": "HL-003", "quantity": 4 }`
  - **Then** the response status is `409 Conflict`
  - **And** the error code is `INSUFFICIENT_STOCK`
  - **And** the response includes `details` with available quantity (`5`)
  - **And** the existing cart item quantity remains `3` (unchanged)

- [ ] **AC-009.5: Add non-existent product is rejected**
  - **Given** no product with SKU `"GHOST-999"` exists in the catalog
  - **When** the Shopper sends `POST /api/cart/items` with body `{ "product_sku": "GHOST-999", "quantity": 1 }`
  - **Then** the response status is `404 Not Found`
  - **And** the error code is `NOT_FOUND`
  - **And** the error message is `"Product not found"`

- [ ] **AC-009.6: Add with quantity less than or equal to zero is rejected**
  - **Given** a product with SKU `"RS-001"` exists in the catalog
  - **When** the Shopper sends `POST /api/cart/items` with body `{ "product_sku": "RS-001", "quantity": 0 }`
  - **Then** the response status is `400 Bad Request`
  - **And** the error code is `VALIDATION_ERROR`
  - **And** the `details` array includes `{ "field": "quantity", "reason": "Must be a positive integer" }`
  - **And** the same behavior applies when `quantity` is `-1` or any negative integer

- [ ] **AC-009.7: View cart with items**
  - **Given** a Shopper has a cart with two items:
    - `"RS-001"`: quantity `2`, `unit_price_snapshot` = `89.99`
    - `"HL-003"`: quantity `1`, `unit_price_snapshot` = `34.50`
  - **When** the Shopper sends `GET /api/cart` with the cart cookie
  - **Then** the response status is `200 OK`
  - **And** the response body contains `items` with both items, each including `product_sku`, `name`, `quantity`, `unit_price_snapshot`, and `subtotal`
  - **And** `items[0].subtotal` = `unit_price_snapshot * quantity` (e.g., `179.98`)
  - **And** `items[1].subtotal` = `unit_price_snapshot * quantity` (e.g., `34.50`)
  - **And** `total` = sum of all subtotals (e.g., `214.48`)

- [ ] **AC-009.8: View empty cart**
  - **Given** a Shopper has no `cart_id` cookie, or has a cart with no items
  - **When** the Shopper sends `GET /api/cart`
  - **Then** the response status is `200 OK`
  - **And** the response body contains `items: []` and `total: 0`

- [ ] **AC-009.9: Update quantity (absolute set)**
  - **Given** a Shopper has a cart with product `"RS-001"` at quantity `2`
  - **And** product `"RS-001"` has stock `150`
  - **When** the Shopper sends `PUT /api/cart/items/RS-001` with body `{ "quantity": 5 }`
  - **Then** the response status is `200 OK`
  - **And** the cart item quantity is now `5` (absolute, not `2 + 5`)
  - **And** subtotals and total are recalculated

- [ ] **AC-009.10: Update quantity to zero is rejected**
  - **Given** a Shopper has a cart with product `"RS-001"` at quantity `2`
  - **When** the Shopper sends `PUT /api/cart/items/RS-001` with body `{ "quantity": 0 }`
  - **Then** the response status is `400 Bad Request`
  - **And** the error code is `VALIDATION_ERROR`
  - **And** the error message indicates quantity must be greater than zero (use DELETE to remove)
  - **And** the cart item remains at quantity `2`

- [ ] **AC-009.11: Update quantity exceeding stock is rejected**
  - **Given** a Shopper has a cart with product `"HL-003"` at quantity `2`
  - **And** product `"HL-003"` has stock `5`
  - **When** the Shopper sends `PUT /api/cart/items/HL-003` with body `{ "quantity": 10 }`
  - **Then** the response status is `409 Conflict`
  - **And** the error code is `INSUFFICIENT_STOCK`
  - **And** the response includes available quantity (`5`)
  - **And** the cart item remains at quantity `2`

- [ ] **AC-009.12: Update non-existent cart item is rejected**
  - **Given** a Shopper has a cart with no item for SKU `"GHOST-999"`
  - **When** the Shopper sends `PUT /api/cart/items/GHOST-999` with body `{ "quantity": 1 }`
  - **Then** the response status is `404 Not Found`
  - **And** the error code is `NOT_FOUND`
  - **And** the error message is `"Item not found in cart"`

- [ ] **AC-009.13: Remove item from cart**
  - **Given** a Shopper has a cart with products `"RS-001"` and `"HL-003"`
  - **When** the Shopper sends `DELETE /api/cart/items/RS-001`
  - **Then** the response status is `200 OK`
  - **And** the response body is the updated cart with only `"HL-003"` remaining
  - **And** `total` is recalculated to reflect only the remaining items

- [ ] **AC-009.14: Remove non-existent item is rejected**
  - **Given** a Shopper has a cart with no item for SKU `"GHOST-999"`
  - **When** the Shopper sends `DELETE /api/cart/items/GHOST-999`
  - **Then** the response status is `404 Not Found`
  - **And** the error code is `NOT_FOUND`
  - **And** the error message is `"Item not found in cart"`

- [ ] **AC-009.15: Price snapshot is immune to product price changes**
  - **Given** a Shopper added product `"RS-001"` to their cart when the price was `89.99`
  - **And** the `unit_price_snapshot` was captured as `89.99`
  - **When** an administrator updates the product price of `"RS-001"` to `99.99`
  - **And** the Shopper sends `GET /api/cart`
  - **Then** the cart item for `"RS-001"` still shows `unit_price_snapshot` = `89.99`
  - **And** the `subtotal` is calculated using `89.99`, not `99.99`

- [ ] **AC-009.16: Cart subtotal and total calculation**
  - **Given** a Shopper has a cart with items:
    - `"RS-001"`: quantity `2`, `unit_price_snapshot` = `89.99`
    - `"HL-003"`: quantity `3`, `unit_price_snapshot` = `34.50`
  - **When** the Shopper sends `GET /api/cart`
  - **Then** `items[0].subtotal` = `89.99 * 2` = `179.98`
  - **And** `items[1].subtotal` = `34.50 * 3` = `103.50`
  - **And** `total` = `179.98 + 103.50` = `283.48`

- [ ] **AC-009.17: Error responses never leak internal details**
  - **Given** any cart operation triggers an error (400, 404, 409, or 500)
  - **When** the error response is returned
  - **Then** the response body never contains stack traces, exception class names, raw SQL fragments, query plans, file system paths, or internal hostnames

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
| `src/ecommerce/cart/handler.clj` | Cart endpoints: `GET /api/cart`, `POST /api/cart/items`, `PUT /api/cart/items/:sku`, `DELETE /api/cart/items/:sku`; delegates to repository; returns updated cart on mutations |
| `src/ecommerce/cart/repository.clj` | Cart and CartItem database operations via `next.jdbc`/HoneySQL: create cart, find cart by id, add item (upsert on `cart_id + product_sku`), update item quantity, remove item, get cart with items and computed subtotals/total |
| `src/ecommerce/cart/middleware.clj` | Cart cookie middleware: reads `cart_id` from signed `HttpOnly` `SameSite=Strict` cookie; creates `cart_id` UUID lazily on first `POST /api/cart/items`; injects `cart_id` into request map |
| `test/ecommerce/cart/handler_integration_test.clj` | Integration tests covering all 17 ACs against a real PostgreSQL instance: add, duplicate-add, stock validation, view, update, remove, price snapshot, error shapes |
| `test/ecommerce/cart/repository_test.clj` | Unit tests for repository functions: cart creation, item upsert logic, quantity update, item removal, subtotal/total computation |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `src/ecommerce/router.clj` | Register cart routes under `/api/cart` with Malli coercion: `GET /api/cart`, `POST /api/cart/items`, `PUT /api/cart/items/:sku`, `DELETE /api/cart/items/:sku`; apply cart cookie middleware to cart route group |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `add-item-to-empty-cart-creates-cart-and-sets-cookie` | AC-009.1 | `POST /api/cart/items` returns `200` with cart containing one item; response includes `Set-Cookie` header with `cart_id` UUID, `HttpOnly`, `SameSite=Strict` |
| `add-item-captures-price-snapshot` | AC-009.1 | Cart item `unit_price_snapshot` equals the product's price at add time (`89.99`) |
| `add-existing-product-increases-quantity` | AC-009.2 | Adding `RS-001` with quantity `3` when already in cart with quantity `2` results in quantity `5`; `unit_price_snapshot` unchanged |
| `add-quantity-exceeding-stock-returns-409` | AC-009.3 | `POST /api/cart/items` with quantity `10` for product with stock `5` returns `409` with code `INSUFFICIENT_STOCK` and available quantity in details |
| `add-combined-quantity-exceeding-stock-returns-409` | AC-009.4 | Existing quantity `3` plus requested `4` exceeds stock `5`; returns `409` with `INSUFFICIENT_STOCK` |
| `add-nonexistent-product-returns-404` | AC-009.5 | `POST /api/cart/items` with `product_sku: "GHOST-999"` returns `404` with code `NOT_FOUND` |
| `add-zero-quantity-returns-400` | AC-009.6 | `POST /api/cart/items` with `quantity: 0` returns `400` with code `VALIDATION_ERROR` and field `"quantity"` |
| `add-negative-quantity-returns-400` | AC-009.6 | `POST /api/cart/items` with `quantity: -1` returns `400` with code `VALIDATION_ERROR` |
| `view-cart-returns-items-with-subtotals-and-total` | AC-009.7 | `GET /api/cart` returns all items with computed `subtotal` per item and correct `total` |
| `view-empty-cart-returns-empty-items-and-zero-total` | AC-009.8 | `GET /api/cart` without cookie returns `200` with `items: []` and `total: 0` |
| `update-quantity-sets-absolute-value` | AC-009.9 | `PUT /api/cart/items/RS-001` with `quantity: 5` sets quantity to `5` (not `2 + 5`); subtotals recalculated |
| `update-quantity-to-zero-returns-400` | AC-009.10 | `PUT /api/cart/items/RS-001` with `quantity: 0` returns `400` with code `VALIDATION_ERROR` |
| `update-quantity-exceeding-stock-returns-409` | AC-009.11 | `PUT /api/cart/items/HL-003` with `quantity: 10` for stock `5` returns `409` with `INSUFFICIENT_STOCK` |
| `update-nonexistent-item-returns-404` | AC-009.12 | `PUT /api/cart/items/GHOST-999` returns `404` with code `NOT_FOUND` |
| `remove-item-recalculates-totals` | AC-009.13 | `DELETE /api/cart/items/RS-001` removes item; response cart contains only remaining items with recalculated `total` |
| `remove-nonexistent-item-returns-404` | AC-009.14 | `DELETE /api/cart/items/GHOST-999` returns `404` with code `NOT_FOUND` |
| `price-snapshot-immune-to-product-update` | AC-009.15 | Add product at `$89.99`, update product price to `$99.99`, `GET /api/cart` still shows `unit_price_snapshot: 89.99` |
| `subtotal-equals-price-times-quantity` | AC-009.16 | Each item `subtotal` = `unit_price_snapshot * quantity`; `total` = sum of all subtotals |
| `error-response-does-not-leak-internals` | AC-009.17 | Error responses do not contain stack traces, `.clj` file paths, SQL keywords (`SELECT`, `INSERT`), or internal hostnames |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `product_sku` | yes | string | Must reference an existing product in the catalog | `""`, `"GHOST-999"` (non-existent) |
| `quantity` | yes | integer | Strictly greater than 0 | `0`, `-1`, `1.5`, `""`, `null` |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | Race condition between stock check and cart item insert could allow over-stock quantities in the cart | Stock check and cart item insert/update should happen within a single database transaction; integration test verifies concurrent adds |
| MEDIUM | Cookie-based cart identity may cause issues with cookie size limits or SameSite restrictions across environments | Keep cookie payload minimal (UUID only); document cookie attributes in middleware; test across same-site requests |
| MEDIUM | Price snapshot divergence from current product price may confuse users who leave a cart idle for extended periods | Out of scope for MVP; document as a future enhancement to notify users of price changes |
| LOW | Cart items referencing deleted products may cause inconsistent state | Product deletion is guarded by `PRODUCT_IN_USE` (409) when referenced by carts or orders; FK constraint prevents orphaned references |

## 10. Out of Scope

- Checkout and order creation (covered by US-010)
- Cart abandonment sweep (scheduled cleanup of old Active carts)
- Multi-device cart synchronization (requires authentication)
- Cart merging (anonymous cart merged with authenticated user cart)
- Saved carts or wishlists
- Cart item notes or customization options
- Bulk add-to-cart operations
- Cart expiration TTL enforcement

## 11. Notes

- Cart identity uses a signed cookie carrying a `cart_id` UUID. The cookie is created lazily on the first `POST /api/cart/items` call. Cookie attributes: `HttpOnly`, `SameSite=Strict`.
- Over-stock policy: the server REJECTS the request with `409 INSUFFICIENT_STOCK`. The response includes the available quantity. There is no automatic capping of quantities.
- Setting quantity to `0` via `PUT` is REJECTED with `400` (quantity must be > 0). The client must use `DELETE /api/cart/items/:sku` to remove an item.
- The `unit_price_snapshot` is captured at add-to-cart time and is immune to later changes in `products.price`. This snapshot carries through to the order at checkout (US-010).
- The `subtotal` for each item is computed as `unit_price_snapshot * quantity`. The `total` is the sum of all subtotals.

## 12. Related Documents

- [API Contract --- Cart API](../architecture/api-contract.md#5-cart-api) --- endpoint shapes and error codes
- [Data Model --- carts](../architecture/data-model.md#22-carts) --- table definition
- [Data Model --- cart_items](../architecture/data-model.md#23-cart_items) --- table definition with price snapshot
- [EP04 --- Purchase Workflow](../epics/EP04-purchase-workflow.md) --- parent epic
- [Testing Strategy](../architecture/testing-strategy.md) --- integration test approach
- [US-001 --- Project Scaffolding](US-001-project-scaffolding.md) --- foundation dependency
- [US-010 --- Checkout & Order Creation](US-010-checkout-order.md) --- downstream consumer of cart

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
