> [INDEX](../INDEX.md) / [User Stories](./) / US-011 --- Product Management Views

# US-011 --- Product Management Views

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP05 --- User Interface](../epics/EP05-user-interface.md) |
| Priority | Must Have |
| Status | Ready |
| Estimation | M |

## 2. Story

As a user, I want a product list view, detail view, and create/edit form with inline validation feedback, so that I can manage and browse the catalog through a visual interface.

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

- [ ] **AC-011.1: Product list page shows paginated table**
  - **Given** the catalog contains products
  - **When** the user navigates to the product list page
  - **Then** a table is displayed with columns: name, SKU, price, stock, and category
  - **And** the data is loaded via `GET /api/products` using Angular `resource()` for async state management
  - **And** the table shows the first page of results (default 20 items per page)

- [ ] **AC-011.2: Pagination controls use paging envelope**
  - **Given** the catalog contains more products than one page (`paging.total` > `paging.perPage`)
  - **When** the user views the product list
  - **Then** pagination controls are visible showing current page, total pages, and navigation buttons
  - **And** clicking "Next" loads `paging.next` URL
  - **And** clicking "Previous" loads `paging.prev` URL
  - **And** "Previous" is disabled on the first page (`paging.prev` is `null`)
  - **And** "Next" is disabled on the last page (`paging.next` is `null`)

- [ ] **AC-011.3: Product detail view shows all fields**
  - **Given** a product with SKU `"RS-001"` exists in the catalog
  - **When** the user clicks on the product row in the list (or navigates to `/products/RS-001`)
  - **Then** the detail view displays all product fields: name, SKU, description, category, price (formatted as currency), stock, weight_kg, created_at, and updated_at
  - **And** the data is loaded via `GET /api/products/RS-001` using `resource()`

- [ ] **AC-011.4: Create product form with Zod validation**
  - **Given** the user navigates to the create product form
  - **When** the user fills in the form fields: name, SKU, description (optional), category (optional), price, stock, weight_kg (optional)
  - **And** the user submits the form with valid data
  - **Then** the form data is validated against the Zod schema before submission
  - **And** `POST /api/products` is called with the validated data
  - **And** on `201 Created`, a success toast/notification is shown
  - **And** the user is navigated back to the product list

- [ ] **AC-011.5: Form validation shows inline errors**
  - **Given** the user is on the create or edit product form
  - **When** a field fails Zod validation (e.g., name is empty, price is negative, stock is fractional)
  - **Then** the invalid field shows a red border
  - **And** a field-level error message is displayed below the input (e.g., `"Must not be empty"`, `"Must be a positive number"`)
  - **And** the validation rules match the backend Malli schemas exactly:
    - `name`: non-empty after trim, max 255 characters
    - `sku`: non-empty, max 50 characters
    - `description`: max 2000 characters (optional)
    - `category`: max 100 characters (optional)
    - `price`: strictly greater than 0, max 2 decimal places
    - `stock`: integer, greater than or equal to 0
    - `weight_kg`: greater than or equal to 0 when present (optional)
  - **And** the submit button is disabled while any validation error exists

- [ ] **AC-011.6: Edit product form with pre-populated fields**
  - **Given** a product with SKU `"RS-001"` exists
  - **When** the user navigates to the edit form for `"RS-001"`
  - **Then** all fields are pre-populated with the product's current values (loaded via `GET /api/products/RS-001`)
  - **And** the SKU field is displayed but disabled/read-only (SKU is immutable after creation)
  - **And** on valid submission, `PUT /api/products/RS-001` is called with the updated data
  - **And** on `200 OK`, a success toast/notification is shown
  - **And** the user is navigated back to the product list

- [ ] **AC-011.7: Delete product with confirmation**
  - **Given** a product with SKU `"RS-001"` exists
  - **When** the user clicks the delete button for `"RS-001"`
  - **Then** a confirmation dialog is shown asking "Are you sure you want to delete this product?"
  - **And** if the user confirms, `DELETE /api/products/RS-001` is called
  - **And** on `204 No Content`, the product is removed from the list and a success notification is shown
  - **And** on `409 PRODUCT_IN_USE`, a user-facing error message is shown: "Cannot delete this product because it is referenced by existing orders" (not the raw API error)

- [ ] **AC-011.8: XSS defense-in-depth**
  - **Given** a product exists with name `"<script>alert('xss')</script>Shoes"`
  - **When** the product is displayed in the list view, detail view, or form
  - **Then** the script content is rendered as literal text, not executed
  - **And** Angular's automatic output encoding is verified to be active (no use of `innerHTML` or `bypassSecurityTrust*`)

- [ ] **AC-011.9: Loading states during API calls**
  - **Given** the user navigates to the product list or detail view
  - **When** the API call is in progress (`resource()` is in `loading` state)
  - **Then** a skeleton placeholder or spinner is displayed
  - **And** the loading indicator disappears when data arrives
  - **And** no empty or partial content flickers before the loading state activates

- [ ] **AC-011.10: Error states on API failure**
  - **Given** the user navigates to a view that requires API data
  - **When** the API call fails (network error, 500, etc.)
  - **Then** a user-facing error message is displayed (e.g., "Failed to load products. Please try again.")
  - **And** the raw error response or stack trace is never shown to the user

- [ ] **AC-011.11: Empty catalog state**
  - **Given** the catalog contains no products (`paging.total` = `0`)
  - **When** the user views the product list
  - **Then** the message "No products found" is displayed instead of an empty table
  - **And** a link or button to "Create a product" is available

- [ ] **AC-011.12: Success feedback on create and update**
  - **Given** the user successfully creates or updates a product
  - **When** the API responds with `201 Created` or `200 OK`
  - **Then** a toast or notification is displayed confirming the action (e.g., "Product created successfully")
  - **And** the user is automatically navigated back to the product list
  - **And** the list reflects the newly created or updated product

- [ ] **AC-011.13: Contract test verifies Zod and Malli schema parity**
  - **Given** the Zod schema in `frontend/src/app/shared/validation/product.schema.ts` and the Malli schema in `src/ecommerce/validation.clj`
  - **When** a canonical set of test inputs is validated against both schemas
  - **Then** both schemas produce identical accept/reject decisions for every input
  - **And** the canonical inputs include boundary values: empty name, whitespace name, negative price, zero price, fractional stock, missing required fields, valid product

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Component tests green for list and form components
- [ ] Contract test green for Zod/Malli schema parity
- [ ] No regressions in existing test suite
- [ ] Error responses displayed as user-facing messages
- [ ] XSS defense verified with script-containing product names
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `frontend/src/app/products/product-list/product-list.component.ts` | Container component: injects `ProductService`, uses `resource()` for paginated product data, handles page navigation, emits delete events; standalone, zoneless |
| `frontend/src/app/products/product-list/product-list.component.html` | Template: product table with name/SKU/price/stock/category columns, pagination controls, loading skeleton, empty state, error state |
| `frontend/src/app/products/product-list/product-list.component.css` | Styles for product list table, pagination, loading and empty states |
| `frontend/src/app/products/product-detail/product-detail.component.ts` | Container component: loads single product via `resource()` from route param SKU, displays all fields; standalone, zoneless |
| `frontend/src/app/products/product-form/product-form.component.ts` | Container component: handles both create and edit modes; uses Zod schema for validation; reactive form with signal-based state; submits via `ProductService`; navigates to list on success; standalone, zoneless |
| `frontend/src/app/products/product-card/product-card.component.ts` | Presentational component: receives product data via `input()`, emits actions via `output()` (view, edit, delete); stateless display of product summary |
| `frontend/src/app/products/product.service.ts` | Service: `HttpClient` calls to `GET /api/products`, `GET /api/products/:sku`, `POST /api/products`, `PUT /api/products/:sku`, `DELETE /api/products/:sku`; returns typed observables |
| `frontend/src/app/products/product.routes.ts` | Feature routes: `/products` (list), `/products/new` (create), `/products/:sku` (detail), `/products/:sku/edit` (edit); lazy-loaded |
| `frontend/src/app/shared/validation/product.schema.ts` | Zod schemas mirroring backend Malli schemas: `ProductCreateSchema`, `ProductUpdateSchema` with all field validations (name, sku, price, stock, etc.) |
| `frontend/src/app/products/product-list/product-list.component.spec.ts` | Component tests: renders products table, pagination controls work, empty state shown, loading state shown, delete confirmation flow, error state shown |
| `frontend/src/app/products/product-form/product-form.component.spec.ts` | Component tests: inline validation errors shown for invalid fields, valid submission calls API, edit mode pre-populates fields, SKU field read-only in edit mode, success navigation |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `frontend/src/app/app.routes.ts` | Add lazy-loaded route for products feature: `{ path: 'products', loadChildren: () => import('./products/product.routes') }` |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `product-list-renders-paginated-table` | AC-011.1 | Product list component renders a table with columns: name, SKU, price, stock, category; data loaded from `GET /api/products` |
| `pagination-controls-navigate-pages` | AC-011.2 | Next/Previous buttons call correct `paging.next`/`paging.prev` URLs; Previous disabled on first page; Next disabled on last page |
| `product-detail-shows-all-fields` | AC-011.3 | Detail view displays all product fields: name, SKU, description, category, price, stock, weight_kg, created_at, updated_at |
| `create-form-submits-valid-product` | AC-011.4 | Valid form submission calls `POST /api/products`; on `201`, shows success toast and navigates to list |
| `form-shows-inline-validation-errors` | AC-011.5 | Empty name shows red border + "Must not be empty"; negative price shows "Must be a positive number"; fractional stock shows validation error; submit disabled while errors exist |
| `form-validates-name-max-length` | AC-011.5 | Name exceeding 255 characters shows validation error |
| `form-validates-price-decimal-places` | AC-011.5 | Price with more than 2 decimal places shows validation error |
| `edit-form-prepopulates-and-disables-sku` | AC-011.6 | Edit form loads product data into fields; SKU field is disabled/read-only; submission calls `PUT /api/products/:sku` |
| `delete-shows-confirmation-and-handles-409` | AC-011.7 | Delete click shows confirmation dialog; on confirm, calls `DELETE`; on `204`, removes from list; on `409 PRODUCT_IN_USE`, shows user-facing message |
| `xss-content-rendered-as-text` | AC-011.8 | Product name `"<script>alert('xss')</script>Shoes"` renders as literal text in list and detail views; no script execution |
| `loading-state-shows-skeleton` | AC-011.9 | While `resource()` is loading, a skeleton or spinner is visible; content appears after data arrives |
| `api-failure-shows-error-message` | AC-011.10 | On API failure, a user-facing error message is displayed; raw error is never shown |
| `empty-catalog-shows-no-products-message` | AC-011.11 | When `paging.total` = `0`, "No products found" message is displayed with a "Create a product" link |
| `success-toast-and-navigation-on-create` | AC-011.12 | After `201 Created`, toast appears and user is navigated to product list |
| `success-toast-and-navigation-on-update` | AC-011.12 | After `200 OK` on update, toast appears and user is navigated to product list |
| `zod-malli-schema-contract-test` | AC-011.13 | Canonical inputs validated against Zod schema produce same accept/reject as Malli schema; boundary values tested: empty name, whitespace, negative price, zero price, fractional stock |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `name` | yes | string | Non-empty after trim; max 255 characters; whitespace-only rejected | `""`, `"   "`, `"\t\n"`, string > 255 chars |
| `sku` | yes (create only) | string | Non-empty; max 50 characters; immutable after creation (disabled in edit) | `""`, string > 50 chars |
| `description` | no | string | Max 2000 characters | string > 2000 chars |
| `category` | no | string | Max 100 characters | string > 100 chars |
| `price` | yes | decimal | Strictly greater than 0; max 2 decimal places | `0`, `-1`, `"$29.99"`, `29.999` |
| `stock` | yes | integer | Greater than or equal to 0; must be whole number | `-1`, `1.5`, `""` |
| `weight_kg` | no | decimal | Greater than or equal to 0 when present | `-1`, `-0.5` |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | Zod and Malli schemas diverge, causing frontend to accept inputs that backend rejects (or vice versa) | Contract test (AC-011.13) validates both schemas against canonical inputs produce identical accept/reject decisions; run as part of CI |
| MEDIUM | Angular 22 zoneless mode with `resource()` may exhibit unexpected behavior in loading/error state transitions | Start with a minimal `resource()` integration in product list; verify loading and error signal states work before applying to all views |
| MEDIUM | Form validation UX: showing errors too eagerly (on first render) or too late (only on submit) frustrates users | Show validation errors on blur (field touched) and on submit; do not show errors for untouched fields |
| LOW | Delete confirmation dialog may be bypassed by programmatic API calls | Delete is a backend-enforced operation with `PRODUCT_IN_USE` protection; dialog is UX-only safeguard |

## 10. Out of Scope

- CSV import UI (covered by US-012)
- Product search and filter UI (covered by US-013)
- Responsive layout and mobile optimization
- Accessibility compliance (WCAG AA/AAA)
- Internationalization (i18n) and localization
- Product image upload or gallery
- Bulk product operations (multi-select delete, bulk edit)
- Advanced form features (auto-save drafts, undo)

## 11. Notes

- All components follow Angular 22 conventions: zoneless with `provideZonelessChangeDetection()`, signals for state, `computed()` for derived values, `resource()` for async API calls.
- Components are standalone (no `NgModule` declarations) with external templates (`templateUrl`) and external styles (`styleUrls`).
- The Container-Presentational pattern is enforced: `product-list`, `product-detail`, and `product-form` are smart containers that inject services; `product-card` is a presentational component that uses `input()`/`output()` only.
- Zod schemas mirror backend Malli schemas exactly. The contract test validates schema parity using a shared set of canonical test inputs.
- The product form operates in two modes (create/edit) determined by the route: `/products/new` for create (all fields editable), `/products/:sku/edit` for edit (SKU disabled).

## 12. Related Documents

- [API Contract --- Products API](../architecture/api-contract.md#3-products-api) --- endpoint shapes and error codes
- [API Contract --- Validation Contract](../architecture/api-contract.md#7-validation-contract) --- field-level rules for Zod/Malli parity
- [EP05 --- User Interface](../epics/EP05-user-interface.md) --- parent epic
- [Testing Strategy](../architecture/testing-strategy.md) --- component and contract testing approach
- [US-001 --- Project Scaffolding](US-001-project-scaffolding.md) --- frontend scaffold dependency
- [US-002 --- Create Product with Validation & Sanitization](US-002-create-product.md) --- backend API for products (must exist)

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
