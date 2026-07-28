> [INDEX](../INDEX.md) / [User Stories](./) / US-014 --- Cart & Checkout Views

# US-014 --- Cart & Checkout Views

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP05 --- User Interface](../epics/EP05-user-interface.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

**As a** Shopper,
**I want** a cart view showing my items with quantities and totals, a checkout flow, and an order confirmation page,
**so that** I can review, adjust, and complete my purchase visually.

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

- [ ] **AC-014.1: Cart view displays all items with details**
  - **Given** the Shopper has items in their cart
  - **When** the cart page is loaded
  - **Then** each cart item displays: `product_sku`, `name`, `quantity`, `unit_price_snapshot`, and computed `subtotal` (quantity * unit_price_snapshot)
  - **And** the data is fetched via `GET /api/cart` through `cart.service.ts`

- [ ] **AC-014.2: Grand total displayed**
  - **Given** the cart contains one or more items
  - **When** the cart view is rendered
  - **Then** the grand total is displayed as the sum of all item subtotals
  - **And** the total matches the `total` field from the `GET /api/cart` response

- [ ] **AC-014.3: Quantity adjustment calls PUT endpoint**
  - **Given** the Shopper views an item in the cart
  - **When** the Shopper changes the quantity using an inline quantity control
  - **Then** the component sends `PUT /api/cart/items/:sku` with the new `{ quantity }` body
  - **And** the cart view refreshes to reflect the updated quantity, subtotal, and grand total

- [ ] **AC-014.4: Quantity validation via Zod**
  - **Given** the Shopper attempts to set an item's quantity
  - **When** the entered value is 0, negative, non-integer, or non-numeric
  - **Then** Zod validation rejects the input
  - **And** an inline error message is shown (e.g., "Quantity must be a positive integer")
  - **And** no API request is sent until the input is corrected

- [ ] **AC-014.5: Remove item calls DELETE endpoint**
  - **Given** the Shopper views an item in the cart
  - **When** the Shopper clicks the remove/delete button for that item
  - **Then** the component sends `DELETE /api/cart/items/:sku`
  - **And** the item is removed from the cart view
  - **And** the grand total is recalculated

- [ ] **AC-014.6: Stock error feedback on 409 INSUFFICIENT_STOCK**
  - **Given** the Shopper updates an item quantity or adds an item
  - **When** the API returns `409 INSUFFICIENT_STOCK`
  - **Then** an inline error message is displayed on the affected item showing the available quantity (from `error.details[].reason`)
  - **And** the cart view remains open for the Shopper to adjust the quantity
  - **And** no navigation occurs

- [ ] **AC-014.7: Empty cart state**
  - **Given** the Shopper's cart contains no items
  - **When** the cart page is loaded
  - **Then** a "Your cart is empty" message is displayed
  - **And** a link or button to "Browse Products" is visible, navigating to the search/catalog view (US-013)

- [ ] **AC-014.8: Checkout button calls POST /api/checkout**
  - **Given** the cart contains one or more items
  - **When** the Shopper clicks the "Checkout" button
  - **Then** the component sends `POST /api/checkout` via `checkout.service.ts`
  - **And** the checkout button shows a loading/disabled state while the request is in flight

- [ ] **AC-014.9: Checkout success navigates to order confirmation**
  - **Given** the checkout API returns `201 Created` with the order details
  - **When** the response is received
  - **Then** the router navigates to the order confirmation page at `/orders/:id`
  - **And** the order confirmation page displays the order details from the response

- [ ] **AC-014.10: Checkout stock failure keeps cart open**
  - **Given** the Shopper clicks "Checkout"
  - **When** the API returns `409 INSUFFICIENT_STOCK` with details listing which items are out of stock
  - **Then** the cart view remains open (no navigation to confirmation)
  - **And** an error message identifies which items have insufficient stock and their available quantities
  - **And** the Shopper can adjust quantities or remove items and retry checkout

- [ ] **AC-014.11: Order confirmation page shows complete order details**
  - **Given** the Shopper has completed a successful checkout
  - **When** the order confirmation page is displayed
  - **Then** the page shows: order `id`, `status` ("Paid"), `placed_at` timestamp, all order items with `product_sku`, `name`, `quantity`, `unit_price`, `line_subtotal`, and the `total_amount`
  - **And** the data is fetched via `GET /api/orders/:id` through `checkout.service.ts` (or passed via router state from the checkout response)

- [ ] **AC-014.12: Cart item count badge in header/nav**
  - **Given** the Shopper has items in their cart
  - **When** any page is displayed
  - **Then** the header/navigation shows a cart icon or link with a badge indicating the number of items in the cart
  - **And** the badge updates when items are added, removed, or quantities change

- [ ] **AC-014.13: Price shown is snapshot price, not current price**
  - **Given** a product was added to the cart at a specific price
  - **When** the cart view is rendered
  - **Then** the `unit_price_snapshot` from the `GET /api/cart` response is displayed
  - **And** this price reflects the price at add-to-cart time, regardless of any subsequent product price changes

- [ ] **AC-014.14: XSS defense via Angular auto-escaping**
  - **Given** a product in the cart has a name containing HTML/script tags (e.g., `<img onerror=alert(1)>`)
  - **When** the cart view renders the product name
  - **Then** the name is displayed as escaped text, not interpreted as HTML
  - **And** no script execution or HTML injection occurs

- [ ] **AC-014.15: Loading states for cart operations and checkout**
  - **Given** the Shopper performs any cart operation (load, add, update, remove, checkout)
  - **When** the API request is in flight
  - **Then** a loading indicator or disabled state is shown for the relevant UI element
  - **And** the loading state clears when the response arrives (success or error)

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Unit tests green for all container and presentational components
- [ ] Unit tests green for `cart.service.ts` and `checkout.service.ts`
- [ ] Integration tests green for service-to-API interaction
- [ ] No regressions in existing test suite
- [ ] Error responses conform to agreed shape
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `frontend/src/app/cart/cart-page/cart-page.component.ts` | Container component; fetches cart via `cart.service.ts`, manages cart state, handles quantity updates, removals, and checkout trigger |
| `frontend/src/app/cart/cart-page/cart-page.component.html` | External template for cart page; item list, totals, checkout button, empty state |
| `frontend/src/app/cart/cart-page/cart-page.component.css` | Styles for cart page layout |
| `frontend/src/app/cart/cart-page/cart-page.component.spec.ts` | Unit tests for cart page container |
| `frontend/src/app/cart/cart-item/cart-item.component.ts` | Presentational component; displays single cart item with quantity control, remove button, subtotal; emits quantity change and remove events |
| `frontend/src/app/cart/cart-item/cart-item.component.html` | External template for cart item |
| `frontend/src/app/cart/cart-item/cart-item.component.css` | Styles for cart item |
| `frontend/src/app/cart/cart-item/cart-item.component.spec.ts` | Unit tests for cart item component |
| `frontend/src/app/cart/cart.service.ts` | HttpClient service; `getCart()`, `addItem(sku, qty)`, `updateQuantity(sku, qty)`, `removeItem(sku)` calling `/api/cart/*` endpoints |
| `frontend/src/app/cart/cart.service.spec.ts` | Unit tests for cart service (HTTP call verification) |
| `frontend/src/app/cart/cart.routes.ts` | Route configuration for cart feature; lazy-loaded |
| `frontend/src/app/checkout/checkout-page/checkout-page.component.ts` | Container component; handles checkout confirmation UI before calling `POST /api/checkout` |
| `frontend/src/app/checkout/checkout-page/checkout-page.component.html` | External template for checkout page |
| `frontend/src/app/checkout/checkout-page/checkout-page.component.css` | Styles for checkout page |
| `frontend/src/app/checkout/checkout-page/checkout-page.component.spec.ts` | Unit tests for checkout page |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.component.ts` | Presentational component; displays order ID, status, placed_at, items with prices, total_amount |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.component.html` | External template for order confirmation |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.component.css` | Styles for order confirmation |
| `frontend/src/app/checkout/order-confirmation/order-confirmation.component.spec.ts` | Unit tests for order confirmation component |
| `frontend/src/app/checkout/checkout.service.ts` | HttpClient service; `checkout()` calling `POST /api/checkout`, `getOrder(id)` calling `GET /api/orders/:id` |
| `frontend/src/app/checkout/checkout.service.spec.ts` | Unit tests for checkout service |
| `frontend/src/app/checkout/checkout.routes.ts` | Route configuration for checkout/orders feature; lazy-loaded |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `frontend/src/app/app.routes.ts` | Add lazy-loaded routes for `/cart` and `/checkout`/`/orders` pointing to their respective route files |
| `frontend/src/app/shared/layout/` | Add cart item count badge to the header/navigation component |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `cart-view-displays-item-details` | AC-014.1 | Each cart item shows product_sku, name, quantity, unit_price_snapshot, and subtotal |
| `cart-view-fetches-via-get-api-cart` | AC-014.1 | Cart page sends `GET /api/cart` on load |
| `grand-total-is-sum-of-subtotals` | AC-014.2 | Displayed total equals sum of all item subtotals |
| `grand-total-matches-api-total` | AC-014.2 | Displayed total matches the `total` field from API response |
| `quantity-change-calls-put-endpoint` | AC-014.3 | Changing quantity to 3 sends `PUT /api/cart/items/:sku` with `{ quantity: 3 }` |
| `quantity-change-updates-subtotal` | AC-014.3 | After quantity update, the item subtotal and grand total refresh |
| `quantity-zero-rejected-by-zod` | AC-014.4 | Entering 0 shows inline error, no API call |
| `quantity-negative-rejected-by-zod` | AC-014.4 | Entering -1 shows inline error, no API call |
| `quantity-decimal-rejected-by-zod` | AC-014.4 | Entering 1.5 shows inline error, no API call |
| `quantity-non-numeric-rejected-by-zod` | AC-014.4 | Entering "abc" shows inline error, no API call |
| `remove-item-calls-delete-endpoint` | AC-014.5 | Clicking remove sends `DELETE /api/cart/items/:sku` |
| `remove-item-updates-cart-view` | AC-014.5 | After removal, the item is no longer displayed and total recalculates |
| `stock-error-409-shows-inline-message` | AC-014.6 | 409 INSUFFICIENT_STOCK response shows available quantity on the affected item |
| `stock-error-keeps-cart-open` | AC-014.6 | After 409, no navigation occurs; cart view remains |
| `empty-cart-shows-message` | AC-014.7 | When cart has no items, "Your cart is empty" is displayed |
| `empty-cart-has-browse-link` | AC-014.7 | Empty cart state includes a link navigating to the product catalog |
| `checkout-button-calls-post-checkout` | AC-014.8 | Clicking "Checkout" sends `POST /api/checkout` |
| `checkout-button-disabled-during-request` | AC-014.8 | While checkout request is in flight, the button is disabled/loading |
| `checkout-success-navigates-to-confirmation` | AC-014.9 | On 201 response, router navigates to `/orders/:id` |
| `checkout-stock-failure-shows-affected-items` | AC-014.10 | On 409, error message lists which items have insufficient stock |
| `checkout-stock-failure-keeps-cart` | AC-014.10 | On 409, cart view remains open for adjustment |
| `order-confirmation-shows-all-details` | AC-014.11 | Confirmation page shows order ID, status "Paid", placed_at, items with prices, total_amount |
| `cart-badge-shows-item-count` | AC-014.12 | Header badge shows the number of items in the cart |
| `cart-badge-updates-on-change` | AC-014.12 | Adding or removing items updates the badge count |
| `price-is-snapshot-not-current` | AC-014.13 | Cart item displays `unit_price_snapshot` from the API response |
| `xss-in-product-name-escaped` | AC-014.14 | Product name `<script>alert(1)</script>` renders as text, not HTML |
| `loading-state-shown-during-cart-load` | AC-014.15 | While cart is loading, loading indicator is visible |
| `loading-state-shown-during-quantity-update` | AC-014.15 | While PUT request is in flight, relevant UI shows loading/disabled state |
| `loading-state-clears-after-response` | AC-014.15 | After API response, loading indicators are removed |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `quantity` (cart item update) | yes | integer | Strictly greater than 0 (`quantity > 0`); must be a whole number | `0`, `-1`, `1.5`, `"abc"`, `""`, `null` |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | Checkout stock failure UX may confuse users if multiple items fail simultaneously | Display a clear per-item breakdown showing which specific items failed and their available quantities |
| MEDIUM | Cart badge requires cross-component state sharing; cart state must be accessible from the header across all routes | Use a shared signal or service at the app level that the header and cart components both reference |
| MEDIUM | Race condition if Shopper rapidly clicks quantity up/down; overlapping PUT requests may cause inconsistent state | Debounce quantity change events; disable the control while a PUT request is in flight |
| LOW | Order confirmation page relies on either router state (lost on refresh) or a separate API call | Implement `GET /api/orders/:id` fallback in `checkout.service.ts` so the confirmation page works on direct navigation or refresh |

## 10. Out of Scope

- Saved carts or persistent cart across sessions (beyond the signed cookie session)
- Wishlist or "save for later" functionality
- Multi-device cart synchronization
- Order history page (listing all past orders)
- Order cancellation or modification after placement
- Responsive design (mobile/tablet layout adaptation)
- Coupon or discount code application

## 11. Notes

- The cart identity is managed via a signed cookie on the backend (per the architecture decisions). The frontend does not need to manage cart IDs explicitly; the browser sends the session cookie automatically with each request.
- The `unit_price_snapshot` is captured at add-to-cart time by the backend. The frontend never calculates this value; it displays what the API returns.
- The order confirmation page should work both when navigated to from the checkout flow (receiving data via router state) and when accessed directly via URL (fetching via `GET /api/orders/:id`). This dual-path approach ensures the page is not broken by a page refresh.
- All components are standalone, following Angular 22 conventions. No `NgModule` declarations.
- The container-presentational pattern is applied: `cart-page` is the container (owns state and API calls); `cart-item` is presentational (receives data via inputs, emits events via outputs).

## 12. Related Documents

- [EP05 --- User Interface](../epics/EP05-user-interface.md) --- parent epic
- [API Contract --- Cart API](../architecture/api-contract.md) --- `GET/POST/PUT/DELETE /api/cart/*`
- [API Contract --- Checkout and Orders API](../architecture/api-contract.md) --- `POST /api/checkout`, `GET /api/orders/:id`
- [Tech Stack](../architecture/tech-stack.md) --- Angular 22, Zod, standalone components
- [Testing Strategy](../architecture/testing-strategy.md) --- frontend testing approach
- [Data Model](../architecture/data-model.md) --- carts, cart_items, orders, order_items tables
- [US-001 --- Project Scaffolding](./US-001-project-scaffolding.md) --- frontend scaffold dependency
- [US-009 --- Cart API](./US-009-cart-api.md) --- backend cart endpoints dependency
- [US-010 --- Checkout & Orders API](./US-010-checkout-orders-api.md) --- backend checkout endpoint dependency
- [US-013 --- Product Search View](./US-013-product-search-view.md) --- "Browse Products" link target

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
