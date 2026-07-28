> [INDEX](../INDEX.md) / [Architecture](./) / API Contract

# API Contract

This document defines the HTTP API contract for the e-commerce application. It is the
single source of truth from which both Malli (Clojure backend) and Zod (Angular frontend)
schemas are built. Every request body, response shape, error code, and validation rule
documented here is authoritative --- the backend enforces them as the security boundary,
and the frontend mirrors them for immediate user feedback.

## 1. Overview

### Base URL

All endpoints are served under the `/api` prefix. The frontend's nginx reverse proxy
forwards requests matching `/api/*` to the Clojure backend on port 3000.

```
Base URL: /api
Content-Type: application/json (unless stated otherwise)
```

### Timestamps

All timestamps use ISO 8601 in UTC with the `Z` suffix:

```
2026-07-27T14:30:00Z
```

### Paging Envelope

Every `GET` endpoint that returns a collection wraps its results in this envelope. There
are no exceptions --- unbounded result sets do not exist in this API.

```json
{
  "items": [],
  "paging": {
    "page": 1,
    "perPage": 20,
    "total": 150,
    "prev": null,
    "next": "/api/products?page=2&perPage=20"
  }
}
```

| Field | Type | Description |
| ----- | ---- | ----------- |
| `items` | array | The items for the current page |
| `paging.page` | integer | Current page number (1-indexed) |
| `paging.perPage` | integer | Items per page (default: 20) |
| `paging.total` | integer | Total number of items across all pages |
| `paging.prev` | string or null | Relative URL for the previous page (preserves all active query parameters); `null` if on first page |
| `paging.next` | string or null | Relative URL for the next page (preserves all active query parameters); `null` if on last page |

Pagination query parameters: `?page=1&perPage=20`

- `page` defaults to `1` when omitted.
- `perPage` defaults to `20` when omitted. Maximum value: `100`.
- `page <= 0` returns `400 Bad Request`.
- `perPage` values above the server-defined maximum (100) return `400 Bad Request`.
- Requesting a `page` beyond the last page returns `200 OK` with an empty `items` array,
  the correct `paging.total`, and `paging.prev`/`paging.next` set appropriately.
- `prev` and `next` URLs always preserve any active query parameters (filters, sort,
  search term) so the client can follow them without reconstructing the query.

## 2. Standard Error Response

All error responses use this shape. The `details` array is present only when field-level
information is available (e.g., validation errors); it is omitted for errors that apply
to the request as a whole.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable message",
    "details": [
      { "field": "price", "reason": "Must be a positive number" }
    ]
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
| ---- | ----------- | ----------- |
| `VALIDATION_ERROR` | 400 | One or more fields failed validation; `details` lists each field and reason |
| `NOT_FOUND` | 404 | The requested resource does not exist |
| `CONFLICT` | 409 | A uniqueness constraint was violated (e.g., duplicate SKU) |
| `INSUFFICIENT_STOCK` | 409 | One or more cart items exceed available stock at checkout time |
| `INTERNAL_ERROR` | 500 | An unexpected server error; no stack traces, SQL fragments, or file paths are leaked |

### Security Invariant

Error responses **never** include:

- Stack traces or exception class names
- Raw SQL fragments or query plans
- File system paths or internal hostnames
- Raw user input echoed back without sanitization

## 3. Products API

Covers EP01 (Product Management) and EP03 (Product Search).

### Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| `GET` | `/api/products` | List products (paginated, with search and filters) |
| `GET` | `/api/products/:sku` | Get a single product by SKU |
| `POST` | `/api/products` | Create a new product |
| `PUT` | `/api/products/:sku` | Update an existing product |
| `DELETE` | `/api/products/:sku` | Delete a product |

---

### GET /api/products

Returns a paginated, searchable, filterable list of products.

#### Query Parameters

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `page` | integer | `1` | Page number (1-indexed) |
| `perPage` | integer | `20` | Items per page (max: 100) |
| `q` | string | _(none)_ | Full-text search across `name`, `description`, and `sku` |
| `category` | string | _(none)_ | Filter by exact category match |
| `priceMin` | decimal | _(none)_ | Minimum price (inclusive) |
| `priceMax` | decimal | _(none)_ | Maximum price (inclusive) |
| `sortBy` | enum | `name` | Sort field: `name`, `price`, or `stock` |
| `sortOrder` | enum | `asc` | Sort direction: `asc` or `desc` |

#### Response --- 200 OK

```json
{
  "items": [
    {
      "sku": "RS-001",
      "name": "Running Shoes",
      "description": "Lightweight running shoes",
      "category": "Footwear",
      "price": 89.99,
      "stock": 150,
      "weight_kg": 0.35,
      "created_at": "2026-07-27T14:30:00Z",
      "updated_at": "2026-07-27T14:30:00Z"
    }
  ],
  "paging": {
    "page": 1,
    "perPage": 20,
    "total": 1,
    "prev": null,
    "next": null
  }
}
```

#### Response --- 400 Bad Request

Returned when filter parameters are malformed (e.g., `priceMin=abc`).

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid filter parameters",
    "details": [
      { "field": "priceMin", "reason": "Must be a valid decimal number" }
    ]
  }
}
```

#### Behavior Notes

- When `q` is empty or omitted, all products are returned (subject to filters).
- When no filters are applied and the catalog is empty, the response is a valid Paging
  envelope with an empty `data` array and `paging.total` of `0`.
- `q` searches across `name`, `description`, and `sku` using PostgreSQL full-text search
  (`tsvector`/`tsquery`).
- Filters are cumulative: applying `category` AND `priceMin` returns only products
  matching both conditions.
- Sort is always applied after filtering; applying a filter never resets the sort, and
  applying a sort never bypasses an active filter.

---

### GET /api/products/:sku

Returns a single product identified by its SKU.

#### Response --- 200 OK

```json
{
  "sku": "RS-001",
  "name": "Running Shoes",
  "description": "Lightweight running shoes",
  "category": "Footwear",
  "price": 89.99,
  "stock": 150,
  "weight_kg": 0.35,
  "created_at": "2026-07-27T14:30:00Z",
  "updated_at": "2026-07-27T14:30:00Z"
}
```

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Product not found"
  }
}
```

---

### POST /api/products

Creates a new product in the catalog.

#### Request Body

```json
{
  "name": "Running Shoes",
  "sku": "RS-001",
  "description": "Lightweight running shoes",
  "category": "Footwear",
  "price": 89.99,
  "stock": 150,
  "weight_kg": 0.35
}
```

| Field | Type | Required | Constraints |
| ----- | ---- | -------- | ----------- |
| `name` | string | yes | Non-empty after trim; whitespace-only is rejected |
| `sku` | string | yes | Non-empty; unique across catalog; immutable after creation |
| `description` | string | no | May be empty or omitted |
| `category` | string | no | May be empty or omitted (uncategorized) |
| `price` | decimal | yes | Strictly greater than 0 |
| `stock` | integer | yes | Greater than or equal to 0 |
| `weight_kg` | decimal | no | Greater than or equal to 0 when present; may be omitted |

#### Response --- 201 Created

```json
{
  "sku": "RS-001",
  "name": "Running Shoes",
  "description": "Lightweight running shoes",
  "category": "Footwear",
  "price": 89.99,
  "stock": 150,
  "weight_kg": 0.35,
  "created_at": "2026-07-27T14:30:00Z",
  "updated_at": "2026-07-27T14:30:00Z"
}
```

#### Response --- 400 Bad Request

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Product validation failed",
    "details": [
      { "field": "price", "reason": "Must be a positive number" },
      { "field": "name", "reason": "Must not be empty" }
    ]
  }
}
```

#### Response --- 409 Conflict

Returned when the SKU already exists in the catalog.

```json
{
  "error": {
    "code": "CONFLICT",
    "message": "A product with SKU 'RS-001' already exists"
  }
}
```

---

### PUT /api/products/:sku

Updates an existing product. The SKU in the URL identifies the product; the SKU field
must not appear in the request body (it is immutable after creation).

#### Request Body

```json
{
  "name": "Running Shoes v2",
  "description": "Updated lightweight running shoes",
  "category": "Footwear",
  "price": 94.99,
  "stock": 120,
  "weight_kg": 0.34
}
```

| Field | Type | Required | Constraints |
| ----- | ---- | -------- | ----------- |
| `name` | string | yes | Non-empty after trim; whitespace-only is rejected |
| `description` | string | no | May be empty or omitted |
| `category` | string | no | May be empty or omitted (uncategorized) |
| `price` | decimal | yes | Strictly greater than 0 |
| `stock` | integer | yes | Greater than or equal to 0 |
| `weight_kg` | decimal | no | Greater than or equal to 0 when present; may be omitted |

#### Response --- 200 OK

Returns the updated product (same shape as GET /api/products/:sku).

```json
{
  "sku": "RS-001",
  "name": "Running Shoes v2",
  "description": "Updated lightweight running shoes",
  "category": "Footwear",
  "price": 94.99,
  "stock": 120,
  "weight_kg": 0.34,
  "created_at": "2026-07-27T14:30:00Z",
  "updated_at": "2026-07-27T15:00:00Z"
}
```

#### Response --- 400 Bad Request

Same shape as POST validation errors.

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Product not found"
  }
}
```

---

### DELETE /api/products/:sku

Deletes a product from the catalog. Existing OrderItems that reference this product
retain their historical snapshot and are not affected.

#### Response --- 204 No Content

Empty body on successful deletion.

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Product not found"
  }
}
```

## 4. CSV Import API

Covers EP02 (CSV Import). The import runs as a background job using `core.async`
channels; the HTTP request returns immediately with a job ID, and the client monitors
progress via SSE.

### Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| `POST` | `/api/imports` | Upload a CSV file (multipart/form-data) |
| `GET` | `/api/imports/:id` | Get import job status |
| `GET` | `/api/imports/:id/progress` | SSE stream for real-time progress |
| `GET` | `/api/imports/:id/errors` | Get import errors (paginated) |

---

### POST /api/imports

Uploads a CSV file and starts a background import job.

#### Request

Content-Type: `multipart/form-data`

| Field | Type | Description |
| ----- | ---- | ----------- |
| `file` | file | The CSV file to import |

#### Response --- 202 Accepted

The file was received and the import job has been queued for background processing.

```json
{
  "job_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "Pending",
  "message": "Import job created. Monitor progress at /api/imports/f47ac10b-58cc-4372-a567-0e02b2c3d479/progress"
}
```

#### Response --- 400 Bad Request

Returned when the file is missing, not a CSV, or cannot be parsed at all.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid file: expected a CSV file"
  }
}
```

---

### GET /api/imports/:id

Returns the current state of an import job.

#### Response --- 200 OK

```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "source_filename": "products.csv",
  "status": "CompletedWithErrors",
  "started_at": "2026-07-27T14:30:00Z",
  "completed_at": "2026-07-27T14:30:05Z",
  "total_rows": 100,
  "accepted_rows": 92,
  "rejected_rows": 8
}
```

| Field | Type | Description |
| ----- | ---- | ----------- |
| `id` | string (UUID) | Job identifier |
| `source_filename` | string | Original filename of the uploaded CSV |
| `status` | enum | `Pending`, `Processing`, `Completed`, `CompletedWithErrors`, or `Failed` |
| `started_at` | timestamp | When processing began; `null` if still `Pending` |
| `completed_at` | timestamp | When processing finished; `null` if still in progress |
| `total_rows` | integer | Total data rows in the CSV (excluding header) |
| `accepted_rows` | integer | Rows successfully imported |
| `rejected_rows` | integer | Rows that failed validation |

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Import job not found"
  }
}
```

---

### GET /api/imports/:id/progress

Opens a Server-Sent Events (SSE) stream that delivers real-time progress updates as the
import job processes each row.

#### Response --- 200 OK

Content-Type: `text/event-stream`

**Row progress event:**

```
event: row
data: {"row_number": 5, "status": "accepted", "sku": "RS-005"}
```

```
event: row
data: {"row_number": 6, "status": "rejected", "field": "price", "reason": "Must be a positive number"}
```

```
event: row
data: {"row_number": 7, "status": "skipped", "reason": "Empty row"}
```

**Completion event:**

```
event: complete
data: {"status": "CompletedWithErrors", "total_rows": 100, "accepted_rows": 92, "rejected_rows": 8}
```

| SSE Event | Fields | Description |
| --------- | ------ | ----------- |
| `row` | `row_number`, `status` (`accepted`, `rejected`, `skipped`), `sku` (if accepted), `field` (if rejected), `reason` (if rejected/skipped) | Emitted after each row is processed |
| `complete` | `status`, `total_rows`, `accepted_rows`, `rejected_rows` | Emitted once when the job finishes |

#### Behavior Notes

- The `row_number` is 1-indexed and includes the header row in the count, matching what a
  human sees when opening the CSV in a spreadsheet.
- If the client connects after the job has already completed, the server sends a single
  `complete` event and closes the stream.

#### Response --- 404 Not Found

Returned if the import job ID does not exist. Standard error response (JSON, not SSE).

---

### GET /api/imports/:id/errors

Returns paginated import errors for a specific job.

#### Query Parameters

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `page` | integer | `1` | Page number (1-indexed) |
| `perPage` | integer | `20` | Items per page (max: 100) |

#### Response --- 200 OK

```json
{
  "items": [
    {
      "row_number": 6,
      "raw_row_data": ",,,,,,",
      "field_name": null,
      "error_reason": "Empty row"
    },
    {
      "row_number": 12,
      "raw_row_data": "Sneakers,SN-001,,Footwear,$29.99,50,0.4",
      "field_name": "price",
      "error_reason": "Must be a positive number; currency symbols are not accepted"
    },
    {
      "row_number": 18,
      "raw_row_data": "Widget,WD-001,,Tools,-5,100,0.2",
      "field_name": "price",
      "error_reason": "Must be a positive number"
    }
  ],
  "paging": {
    "page": 1,
    "perPage": 20,
    "total": 8,
    "prev": null,
    "next": null
  }
}
```

| Field | Type | Description |
| ----- | ---- | ----------- |
| `row_number` | integer | 1-indexed row number from the original CSV file |
| `raw_row_data` | string | The original CSV row as a string (sanitized for display; no executable content) |
| `field_name` | string or null | The specific field that caused rejection; `null` for row-level errors (e.g., empty row) |
| `error_reason` | string | Human-readable explanation of why the row was rejected |

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Import job not found"
  }
}
```

## 5. Cart API

Covers the cart portion of EP04 (Purchase Workflow). The cart is session-scoped --- no
authentication is required for this challenge. The server identifies the cart by session
(cookie or equivalent).

### Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| `GET` | `/api/cart` | Get current cart |
| `POST` | `/api/cart/items` | Add item to cart |
| `PUT` | `/api/cart/items/:sku` | Update item quantity |
| `DELETE` | `/api/cart/items/:sku` | Remove item from cart |

---

### GET /api/cart

Returns the current session's cart contents with computed subtotals and grand total.

#### Response --- 200 OK

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "Active",
  "items": [
    {
      "product_sku": "RS-001",
      "name": "Running Shoes",
      "quantity": 2,
      "unit_price_snapshot": 89.99,
      "subtotal": 179.98
    },
    {
      "product_sku": "HL-003",
      "name": "Hiking Lantern",
      "quantity": 1,
      "unit_price_snapshot": 34.50,
      "subtotal": 34.50
    }
  ],
  "total": 214.48,
  "created_at": "2026-07-27T14:30:00Z",
  "updated_at": "2026-07-27T14:35:00Z"
}
```

| Field | Type | Description |
| ----- | ---- | ----------- |
| `id` | string (UUID) | Cart identifier |
| `status` | enum | `Active`, `CheckedOut`, or `Abandoned` |
| `items` | array | List of CartItem objects |
| `items[].product_sku` | string | SKU of the product |
| `items[].name` | string | Product name (for display convenience) |
| `items[].quantity` | integer | Quantity in cart |
| `items[].unit_price_snapshot` | decimal | Price captured when item was added |
| `items[].subtotal` | decimal | `unit_price_snapshot * quantity` |
| `total` | decimal | Sum of all item subtotals |
| `created_at` | timestamp | When the cart was created |
| `updated_at` | timestamp | When the cart was last modified |

#### Behavior Notes

- If no cart exists for the current session, the response is an empty cart with
  `items: []` and `total: 0`.

---

### POST /api/cart/items

Adds an item to the current session's cart. If the product is already in the cart, its
quantity is increased by the specified amount.

#### Request Body

```json
{
  "product_sku": "RS-001",
  "quantity": 2
}
```

| Field | Type | Required | Constraints |
| ----- | ---- | -------- | ----------- |
| `product_sku` | string | yes | Must reference an existing product |
| `quantity` | integer | yes | Strictly greater than 0 |

#### Response --- 200 OK

Returns the updated cart (same shape as GET /api/cart).

#### Response --- 400 Bad Request

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid cart item",
    "details": [
      { "field": "quantity", "reason": "Must be a positive integer" }
    ]
  }
}
```

#### Response --- 404 Not Found

Returned when the referenced product SKU does not exist.

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Product not found"
  }
}
```

#### Response --- 409 Conflict

Returned when the requested quantity (including any already in the cart) exceeds available stock.

```json
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "Requested quantity exceeds available stock",
    "details": [
      { "field": "quantity", "reason": "Only 5 units available for SKU 'RS-001'" }
    ]
  }
}
```

---

### PUT /api/cart/items/:sku

Sets the quantity of an item already in the cart. This is an absolute set, not a delta.

#### Request Body

```json
{
  "quantity": 3
}
```

| Field | Type | Required | Constraints |
| ----- | ---- | -------- | ----------- |
| `quantity` | integer | yes | Strictly greater than 0 |

#### Response --- 200 OK

Returns the updated cart (same shape as GET /api/cart).

#### Response --- 404 Not Found

Returned when the item is not in the cart.

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Item not found in cart"
  }
}
```

#### Response --- 409 Conflict

Returned when the requested quantity exceeds available stock (same shape as POST
/api/cart/items stock error).

---

### DELETE /api/cart/items/:sku

Removes an item from the cart entirely.

#### Response --- 200 OK

Returns the updated cart (same shape as GET /api/cart).

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Item not found in cart"
  }
}
```

## 6. Checkout and Orders API

Covers the checkout and order portion of EP04 (Purchase Workflow).

### Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| `POST` | `/api/checkout` | Place an order from the current cart |
| `GET` | `/api/orders/:id` | Get order details |

---

### POST /api/checkout

Converts the current cart into an order. This endpoint performs the following operations
atomically within a database transaction:

1. **Stock re-validation**: every CartItem's quantity is checked against the product's
   current stock. If any item exceeds availability, the entire checkout is rejected.
2. **Simulated payment**: the payment always succeeds (no external provider).
3. **Stock decrement**: each product's stock is decremented by the ordered quantity.
4. **Order creation**: an Order with OrderItems is created from the cart contents, using
   prices re-validated at checkout time.
5. **Cart transition**: the cart status changes from `Active` to `CheckedOut`.

#### Request Body

No request body required. The checkout operates on the current session's cart.

#### Response --- 201 Created

```json
{
  "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "Paid",
  "placed_at": "2026-07-27T14:40:00Z",
  "items": [
    {
      "product_sku": "RS-001",
      "name": "Running Shoes",
      "quantity": 2,
      "unit_price": 89.99,
      "line_subtotal": 179.98
    },
    {
      "product_sku": "HL-003",
      "name": "Hiking Lantern",
      "quantity": 1,
      "unit_price": 34.50,
      "line_subtotal": 34.50
    }
  ],
  "total_amount": 214.48
}
```

| Field | Type | Description |
| ----- | ---- | ----------- |
| `id` | string (UUID) | Order identifier |
| `status` | enum | `Paid` (simulated payment always succeeds) |
| `placed_at` | timestamp | When the order was placed |
| `items` | array | List of OrderItem objects |
| `items[].product_sku` | string | SKU of the product |
| `items[].name` | string | Product name at time of purchase |
| `items[].quantity` | integer | Quantity purchased |
| `items[].unit_price` | decimal | Price at time of purchase |
| `items[].line_subtotal` | decimal | `unit_price * quantity` |
| `total_amount` | decimal | Sum of all line subtotals |

#### Response --- 400 Bad Request

Returned when the cart is empty.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Cannot checkout an empty cart"
  }
}
```

#### Response --- 409 Conflict

Returned when one or more items exceed available stock at checkout time.

```json
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "One or more items exceed available stock",
    "details": [
      { "field": "RS-001", "reason": "Requested 10 but only 5 available" }
    ]
  }
}
```

---

### GET /api/orders/:id

Returns the details of a completed order. Orders are immutable once placed.

#### Response --- 200 OK

Same shape as the POST /api/checkout 201 response.

#### Response --- 404 Not Found

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Order not found"
  }
}
```

## 7. Validation Contract

These are the exact field-level validation rules that both Malli (backend) and Zod
(frontend) must enforce identically. The backend is **authoritative** --- it rejects any
request that fails validation regardless of what the frontend accepted. The frontend
mirrors these rules for immediate user feedback.

### Product Fields

#### `name` (ProductName)

| Rule | Detail |
| ---- | ------ |
| Required | yes |
| Type | string |
| Trim | Leading and trailing whitespace is trimmed before validation |
| Non-empty | After trimming, the result must be a non-empty string |
| Whitespace-only | Rejected --- treated as empty after trim |
| Sanitization | Encoded for safe rendering; never interpolated directly into queries |

**Reject examples:** `""`, `"   "`, `"\t\n"`

**Accept examples:** `"Running Shoes"`, `"A"`, `"Widget (3-pack)"`

#### `sku` (SKU)

| Rule | Detail |
| ---- | ------ |
| Required | yes |
| Type | string |
| Non-empty | Must be a non-empty string |
| Uniqueness | Must be unique across all Product records in the catalog |
| Immutability | Cannot be changed after creation (POST only; ignored or rejected in PUT) |

**Reject examples:** `""`, a SKU that already exists in the catalog (CONFLICT, not
VALIDATION_ERROR)

**Accept examples:** `"RS-001"`, `"widget-v2"`, `"ABC123"`

#### `description`

| Rule | Detail |
| ---- | ------ |
| Required | no |
| Type | string |
| Empty allowed | yes --- an empty string or omission is valid |
| Sanitization | Encoded for safe rendering |

**Accept examples:** `"Lightweight running shoes"`, `""`, omitted entirely

#### `category` (CategoryLabel)

| Rule | Detail |
| ---- | ------ |
| Required | no |
| Type | string |
| Empty allowed | yes --- an empty string or omission is valid (product is uncategorized) |
| Enumeration | No fixed enumeration in v1; free-text label |
| Comparison | Case-sensitive unless a story states otherwise |

**Accept examples:** `"Footwear"`, `"Electronics"`, `""`, omitted entirely

#### `price` (Price)

| Rule | Detail |
| ---- | ------ |
| Required | yes |
| Type | decimal |
| Minimum | Strictly greater than 0 (`price > 0`) |
| Precision | Exact decimal with two fractional digits; never binary floating-point |
| Currency symbols | Not accepted; input must be a raw numeric value |

**Reject examples:** `0`, `0.00`, `-1`, `-0.01`, `"free"`, `"$29.99"`, `""`, `null`,
`"abc"`, `"29.99USD"`

**Accept examples:** `89.99`, `0.01`, `1`, `1000.00`

#### `stock` (Stock)

| Rule | Detail |
| ---- | ------ |
| Required | yes |
| Type | integer |
| Minimum | Greater than or equal to 0 (`stock >= 0`) |
| Absent value | Invalid --- must not be silently defaulted to zero |
| Fractional | Rejected --- must be a whole number |

**Reject examples:** `-1`, `-100`, `1.5`, `""`, `null`, `"abc"`

**Accept examples:** `0`, `1`, `150`, `10000`

#### `weight_kg` (WeightKg)

| Rule | Detail |
| ---- | ------ |
| Required | no |
| Type | decimal |
| Minimum | Greater than or equal to 0 when present (`weight_kg >= 0`) |
| Absent value | Valid --- a missing weight does not invalidate the product |

**Reject examples:** `-1`, `-0.5`, `"abc"`

**Accept examples:** `0`, `0.35`, `12.5`, omitted entirely

### Validation Rule Summary Table

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| `name` | yes | string | Non-empty after trim | `""`, `"   "` |
| `sku` | yes | string | Non-empty, unique | `""`, duplicate |
| `description` | no | string | _(none)_ | _(nothing rejected)_ |
| `category` | no | string | _(none)_ | _(nothing rejected)_ |
| `price` | yes | decimal | `> 0` | `0`, `"free"`, `"$29.99"`, negative |
| `stock` | yes | integer | `>= 0` | `-1`, `1.5`, empty |
| `weight_kg` | no | decimal | `>= 0` when present | `-1` |

## Related Documents

- [Project Brief](../project-brief.md)
- [Domain Glossary](../domain-glossary.md) --- entity definitions and value object constraints
- [Tech Stack](./tech-stack.md) --- Malli (backend) and Zod (frontend) validation libraries
- [EP01 --- Product Management](../epics/EP01-product-management.md) --- CRUD operations
- [EP02 --- CSV Import](../epics/EP02-csv-import.md) --- bulk import pipeline
- [EP03 --- Product Search](../epics/EP03-product-search.md) --- search and filtering
- [EP04 --- Purchase Workflow](../epics/EP04-purchase-workflow.md) --- cart and checkout
