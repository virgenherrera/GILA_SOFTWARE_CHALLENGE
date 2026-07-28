> [INDEX](../INDEX.md) / [User Stories](./) / US-010 --- Checkout & Order Creation

# US-010 --- Checkout & Order Creation

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP04 --- Purchase Workflow](../epics/EP04-purchase-workflow.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

As a Shopper, I want to place an order through simulated checkout, and as the Business, I want stock decremented atomically with race condition protection, so that purchases are reliable and inventory is accurate.

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

- [ ] **AC-010.1: Checkout with valid cart creates order**
  - **Given** a Shopper has an `Active` cart with items:
    - `"RS-001"`: quantity `2`, `unit_price_snapshot` = `89.99`
    - `"HL-003"`: quantity `1`, `unit_price_snapshot` = `34.50`
  - **And** product `"RS-001"` has stock `150` and product `"HL-003"` has stock `20`
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the response status is `201 Created`
  - **And** the response body contains an order with: `id` (UUID), `status` = `"Paid"`, `placed_at` (ISO 8601 timestamp), `items` with `product_sku`, `name`, `quantity`, `unit_price`, `line_subtotal` for each item, and `total_amount` = sum of all `line_subtotal` values
  - **And** `items[0].unit_price` = `89.99` (from cart snapshot), `items[0].line_subtotal` = `179.98`
  - **And** `items[1].unit_price` = `34.50` (from cart snapshot), `items[1].line_subtotal` = `34.50`
  - **And** `total_amount` = `214.48`

- [ ] **AC-010.2: Stock re-validated at checkout time**
  - **Given** a Shopper has a cart with product `"RS-001"` at quantity `5`
  - **And** product `"RS-001"` currently has stock `5`
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the checkout succeeds because the current stock (`5`) is sufficient for the requested quantity (`5`)
  - **And** after checkout, product `"RS-001"` stock is `0`

- [ ] **AC-010.3: Insufficient stock at checkout returns 409**
  - **Given** a Shopper has a cart with product `"RS-001"` at quantity `10`
  - **And** product `"RS-001"` currently has stock `5` (stock decreased since the item was added to cart)
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the response status is `409 Conflict`
  - **And** the error code is `INSUFFICIENT_STOCK`
  - **And** the `details` array includes per-item information: SKU `"RS-001"`, requested quantity `10`, available quantity `5`

- [ ] **AC-010.4: Partial stock failure triggers atomic rollback**
  - **Given** a Shopper has a cart with two items:
    - `"RS-001"`: quantity `2` (stock = `150`, sufficient)
    - `"HL-003"`: quantity `10` (stock = `5`, insufficient)
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the response status is `409 Conflict`
  - **And** NO order is created in the database
  - **And** NO stock is decremented for any product (including `"RS-001"` which had sufficient stock)
  - **And** the cart remains in `Active` status and is still modifiable

- [ ] **AC-010.5: Stock decremented on successful checkout**
  - **Given** product `"RS-001"` has stock `150` and product `"HL-003"` has stock `20`
  - **And** a Shopper has a cart with `"RS-001"` at quantity `2` and `"HL-003"` at quantity `1`
  - **When** the Shopper sends `POST /api/checkout` and it succeeds
  - **Then** product `"RS-001"` stock is now `148` (150 - 2)
  - **And** product `"HL-003"` stock is now `19` (20 - 1)

- [ ] **AC-010.6: Cart transitions to CheckedOut on success**
  - **Given** a Shopper has an `Active` cart with items
  - **When** the checkout succeeds
  - **Then** the cart status changes from `Active` to `CheckedOut`
  - **And** subsequent `POST /api/cart/items`, `PUT /api/cart/items/:sku`, and `DELETE /api/cart/items/:sku` requests against this cart are rejected (cart is no longer modifiable)

- [ ] **AC-010.7: Empty cart checkout returns 400**
  - **Given** a Shopper has a cart with no items, or has no cart at all
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the response status is `400 Bad Request`
  - **And** the error code is `VALIDATION_ERROR`
  - **And** the error message is `"Cannot checkout an empty cart"`

- [ ] **AC-010.8: Concurrent checkout race condition prevented**
  - **Given** product `"RS-001"` has stock `5`
  - **And** two Shoppers each have a cart with `"RS-001"` at quantity `4`
  - **When** both Shoppers send `POST /api/checkout` simultaneously
  - **Then** exactly one checkout succeeds with `201 Created` and the other fails with `409 INSUFFICIENT_STOCK`
  - **And** after both requests complete, product `"RS-001"` stock is `1` (5 - 4), not negative
  - **And** only one order is created
  - **And** the mechanism is `SELECT ... FOR UPDATE` on product rows inside the checkout transaction

- [ ] **AC-010.9: Order is immutable after placement**
  - **Given** an order was created via checkout with `id` = `"b2c3d4e5-..."`
  - **When** `GET /api/orders/b2c3d4e5-...` is called at any later time
  - **Then** the response always returns the same data: same `items`, same `unit_price` values, same `total_amount`, same `status`
  - **And** there is no API endpoint to modify an existing order

- [ ] **AC-010.10: Order unit_price from cart snapshot, not current product price**
  - **Given** a Shopper added product `"RS-001"` to cart when the price was `89.99`
  - **And** the `unit_price_snapshot` in the cart item is `89.99`
  - **And** an administrator later updated the product price to `99.99`
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the order item `unit_price` is `89.99` (from cart snapshot)
  - **And** `line_subtotal` is calculated using `89.99`, not `99.99`

- [ ] **AC-010.11: Deleted product in cart at checkout time**
  - **Given** a Shopper has a cart with product `"RS-001"`
  - **And** the product `"RS-001"` has been deleted from the catalog (or its stock has been set to `0`)
  - **When** the Shopper sends `POST /api/checkout`
  - **Then** the response status is `409 Conflict`
  - **And** the error code is `INSUFFICIENT_STOCK` (product no longer available)
  - **And** no order is created

- [ ] **AC-010.12: GET order returns full details**
  - **Given** an order exists with `id` = `"b2c3d4e5-..."`
  - **When** `GET /api/orders/b2c3d4e5-...` is called
  - **Then** the response status is `200 OK`
  - **And** the response body contains: `id`, `status` = `"Paid"`, `placed_at`, `items` (each with `product_sku`, `name`, `quantity`, `unit_price`, `line_subtotal`), and `total_amount`

- [ ] **AC-010.13: Non-existent order returns 404**
  - **Given** no order exists with `id` = `"00000000-0000-0000-0000-000000000000"`
  - **When** `GET /api/orders/00000000-0000-0000-0000-000000000000` is called
  - **Then** the response status is `404 Not Found`
  - **And** the error code is `NOT_FOUND`
  - **And** the error message is `"Order not found"`

- [ ] **AC-010.14: Error responses never leak internal details**
  - **Given** any checkout or order operation triggers an error (400, 404, 409, or 500)
  - **When** the error response is returned
  - **Then** the response body never contains stack traces, exception class names, raw SQL fragments, query plans, file system paths, or internal hostnames

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Unit tests green for domain logic and validation
- [ ] Integration tests green against real dependencies
- [ ] Concurrent checkout race condition test passes reliably
- [ ] No regressions in existing test suite
- [ ] Error responses conform to agreed shape
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `src/ecommerce/checkout/handler.clj` | Checkout endpoints: `POST /api/checkout` (delegates to service layer), `GET /api/orders/:id` (delegates to order repository); returns order response shape |
| `src/ecommerce/checkout/service.clj` | Atomic checkout logic: within a single DB transaction performs stock re-validation with `SELECT ... FOR UPDATE` on product rows, simulated payment (always succeeds), stock decrement, order + order_items creation from cart snapshot prices, cart status transition to `CheckedOut`; rolls back entirely on any failure |
| `src/ecommerce/order/repository.clj` | Order and OrderItem database operations via `next.jdbc`/HoneySQL: create order with items, find order by id with items, compute line_subtotals and total_amount |
| `test/ecommerce/checkout/service_test.clj` | Unit tests for checkout service logic: stock validation, atomic rollback on partial failure, price snapshot carrythrough, cart status transition, stock decrement arithmetic |
| `test/ecommerce/checkout/handler_integration_test.clj` | Integration tests against real PostgreSQL: all 14 ACs including concurrent checkout race condition test using multiple threads with `SELECT FOR UPDATE` verification |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `src/ecommerce/router.clj` | Register checkout and order routes: `POST /api/checkout`, `GET /api/orders/:id`; apply cart cookie middleware to checkout route |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `checkout-valid-cart-creates-order-with-paid-status` | AC-010.1 | `POST /api/checkout` returns `201` with order containing `status: "Paid"`, items with `unit_price` from cart snapshot, correct `line_subtotal` per item, and correct `total_amount` |
| `checkout-revalidates-stock-at-checkout-time` | AC-010.2 | Cart with quantity `5`, product stock `5`: checkout succeeds; product stock is now `0` |
| `checkout-insufficient-stock-returns-409-with-details` | AC-010.3 | Cart quantity `10`, product stock `5`: returns `409` with code `INSUFFICIENT_STOCK` and details including SKU, requested, and available quantities |
| `checkout-partial-failure-rolls-back-all-changes` | AC-010.4 | Two items in cart: one sufficient, one insufficient; returns `409`; no order created; no stock decremented for either product; cart remains `Active` |
| `checkout-decrements-stock-by-purchased-quantity` | AC-010.5 | Product stock `150`, cart quantity `2`: after checkout, stock is `148` |
| `checkout-transitions-cart-to-checked-out` | AC-010.6 | After successful checkout, cart status is `CheckedOut`; subsequent cart mutations are rejected |
| `checkout-empty-cart-returns-400` | AC-010.7 | Empty cart (or no cart): `POST /api/checkout` returns `400` with code `VALIDATION_ERROR` and message `"Cannot checkout an empty cart"` |
| `concurrent-checkout-only-one-succeeds` | AC-010.8 | Two threads checkout same product (stock `5`, each wants `4`): exactly one gets `201`, the other gets `409`; final stock is `1`; only one order exists |
| `order-is-immutable-after-placement` | AC-010.9 | Create order, `GET /api/orders/:id` twice at different times: both responses are identical |
| `order-unit-price-from-cart-snapshot-not-current-price` | AC-010.10 | Add product at `$89.99`, update price to `$99.99`, checkout: order `unit_price` is `89.99` |
| `checkout-deleted-product-returns-409` | AC-010.11 | Product in cart is deleted (or stock set to `0`): checkout returns `409` with `INSUFFICIENT_STOCK` |
| `get-order-returns-full-details` | AC-010.12 | `GET /api/orders/:id` returns order with `id`, `status`, `placed_at`, `items` (with `product_sku`, `name`, `quantity`, `unit_price`, `line_subtotal`), and `total_amount` |
| `get-nonexistent-order-returns-404` | AC-010.13 | `GET /api/orders/00000000-...` returns `404` with code `NOT_FOUND` |
| `checkout-error-does-not-leak-internals` | AC-010.14 | Error responses do not contain stack traces, `.clj` file paths, SQL keywords, or internal hostnames |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| _(no request body for POST /api/checkout)_ | --- | --- | Cart must be non-empty and in `Active` status; all items must have sufficient stock | Empty cart, `CheckedOut` cart |
| `id` (path param for GET /api/orders/:id) | yes | string (UUID) | Must be a valid UUID referencing an existing order | `""`, `"not-a-uuid"`, non-existent UUID |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| CRITICAL | Concurrent checkout race condition allows overselling: two sessions read same stock, both pass validation, both decrement | `SELECT ... FOR UPDATE` on product rows inside the checkout transaction serializes concurrent access; integration test with concurrent threads verifies only one succeeds |
| HIGH | Atomic transaction complexity: partial failure must roll back all changes (stock, order, cart status) completely | Entire checkout logic wrapped in a single `jdbc/with-transaction`; any exception triggers automatic rollback; test verifies no side effects on failure |
| MEDIUM | Deleted product during checkout: product referenced by cart no longer exists at checkout time | Stock re-validation step treats missing product as `INSUFFICIENT_STOCK` (quantity `0` available); explicit handling in service layer |
| LOW | Order total calculation precision: floating-point arithmetic on prices could produce rounding errors | `NUMERIC(10,2)` in PostgreSQL ensures exact decimal arithmetic; `line_subtotal` computed and persisted in DB, not in application code |

## 10. Out of Scope

- Order cancellation and refund workflows
- Order `Fulfilled` state transition (delivery tracking)
- Real payment provider integration (Stripe, PayPal, etc.)
- Order history listing (GET /api/orders with pagination)
- Receipt or invoice generation (PDF, email)
- Order modification after placement
- Multiple payment methods or split payments
- Tax or shipping cost calculation

## 11. Notes

- The checkout is atomic: stock re-validation, simulated payment, stock decrement, order creation, and cart status transition all happen within a single database transaction. Any failure at any step causes a complete rollback.
- Concurrency control uses `SELECT ... FOR UPDATE` on product rows inside the checkout transaction. This pessimistic locking strategy serializes concurrent checkouts that touch the same products, preventing race conditions where two sessions read stale stock values.
- The price source for `order_items.unit_price` is the cart's `unit_price_snapshot`, NOT re-fetched from `products.price`. This preserves the price the Shopper saw when adding to cart.
- Payment is always simulated and always succeeds. The order status transitions from `Pending` to `Paid` within the same transaction.
- Cart status transitions from `Active` to `CheckedOut` on successful checkout. A checked-out cart is no longer modifiable.
- The concurrent checkout test is critical for this story and must use actual concurrent threads (not sequential requests) to verify the `SELECT FOR UPDATE` mechanism works under contention.

## 12. Related Documents

- [API Contract --- Checkout and Orders API](../architecture/api-contract.md#6-checkout-and-orders-api) --- endpoint shapes and error codes
- [Data Model --- orders](../architecture/data-model.md#24-orders) --- table definition
- [Data Model --- order_items](../architecture/data-model.md#25-order_items) --- table definition
- [EP04 --- Purchase Workflow](../epics/EP04-purchase-workflow.md) --- parent epic
- [Testing Strategy](../architecture/testing-strategy.md) --- concurrency testing approach
- [US-001 --- Project Scaffolding](US-001-project-scaffolding.md) --- foundation dependency
- [US-002 --- Product CRUD](US-002-product-crud.md) --- products must exist
- [US-009 --- Cart Operations](US-009-cart-operations.md) --- cart must exist for checkout

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
