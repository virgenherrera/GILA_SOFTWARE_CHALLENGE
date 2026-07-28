> [INDEX](../INDEX.md) / [User Stories](./) / US-001 --- Project Scaffolding

# US-001 --- Project Scaffolding

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP06 --- Containerization & Documentation](../epics/EP06-containerization-docs.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

As a developer, I want the project skeleton (backend, frontend, Docker, database migrations) bootstrapped with all dependencies pinned, so that feature stories have a working foundation to build upon.

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

- [ ] **AC-001.1: Backend skeleton compiles**
  - **Given** a fresh clone of the repository with no prior build artifacts
  - **When** `clojure -P` is executed in the backend project root
  - **Then** all dependencies resolve successfully, including: `org.clojure/clojure` 1.12.0, `ring/ring-core` 1.12.2, `ring/ring-jetty-adapter` 1.12.2, `metosin/reitit` 0.7.2, `metosin/malli` 0.16.4, `com.github.seancorfield/next.jdbc` 1.3.955, `com.github.seancorfield/honeysql` 2.6.1235, `org.postgresql/postgresql` 42.7.5, `com.zaxxer/HikariCP` 6.2.1, `org.clojure/core.async` 1.7.790, `org.clojure/data.csv` 1.1.0, `org.clojure/data.json` 2.5.1, `org.clojure/tools.logging` 1.3.0, `ch.qos.logback/logback-classic` 1.5.16; and `deps.edn` contains no floating version ranges, no `RELEASE` or `LATEST` markers; and the Reitit router defines a `GET /api/health` endpoint that returns `200 OK` with `{"status": "ok"}`; and Malli coercion middleware is registered in the router middleware stack; and a HikariCP connection pool is configured via `next.jdbc`; and error-envelope middleware is registered that catches unhandled exceptions and returns `{"error": {"code": "INTERNAL_ERROR", "message": "<safe message>"}}`

- [ ] **AC-001.2: Frontend skeleton builds**
  - **Given** a fresh clone of the repository with no prior `node_modules` or `dist/` artifacts
  - **When** `pnpm install --frozen-lockfile && pnpm exec ng build --configuration=production` is executed in the `frontend/` directory
  - **Then** the build completes with zero errors; and `package.json` pins all dependency versions exactly (no `^`, no `~`, no `*`), including `@angular/core` 22.0.0, `@angular/router` 22.0.0, `@angular/forms` 22.0.0, `@angular/common` 22.0.0, `zod` 3.24.4; and the Angular app bootstraps with `provideZonelessChangeDetection()` (no Zone.js import); and `HttpClient` is configured via `provideHttpClient()`; and a routing shell exists with at least one lazy-loaded route; and all components are standalone (no `NgModule` declarations)

- [ ] **AC-001.3: Docker Compose skeleton starts all services**
  - **Given** Docker and Docker Compose are installed on the host machine
  - **When** `docker compose up --build` is executed from the project root
  - **Then** three services start: `db` (PostgreSQL 17.5), `backend` (JDK 21 Eclipse Temurin), `frontend` (Node 22 build + nginx 1.27-alpine serving); and each service declares a health check in `docker-compose.yml`; and `backend` waits for `db` to be healthy before accepting connections; and `frontend` waits for `backend` to be healthy before starting; and all services share a Docker bridge network; and nginx reverse-proxies `/api/*` requests to `backend:3000`; and the application is reachable at `http://localhost:8080` after all health checks pass

- [ ] **AC-001.4: Database migrations run successfully**
  - **Given** the `db` service starts with a fresh, empty `ecommerce` database
  - **When** the 8 SQL migration files (`001-create-products.sql` through `008-create-import-errors.sql`) are executed via `docker-entrypoint-initdb.d/`
  - **Then** all 7 tables are created: `products`, `carts`, `cart_items`, `orders`, `order_items`, `csv_import_jobs`, `import_errors`; and `products` has columns: `sku TEXT PRIMARY KEY`, `name TEXT NOT NULL`, `description TEXT`, `category TEXT`, `price NUMERIC(10,2) NOT NULL CHECK (price > 0)`, `stock INTEGER NOT NULL CHECK (stock >= 0)`, `weight_kg NUMERIC(6,3) CHECK (weight_kg >= 0)`, `search_vector TSVECTOR`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`; and the `products_search_vector_update()` trigger function exists and fires on INSERT or UPDATE; and `GIN` index `idx_products_search_vector` exists on `search_vector`; and all foreign key relationships are established per the data model: `cart_items.cart_id` -> `carts.id`, `cart_items.product_sku` -> `products.sku`, `order_items.order_id` -> `orders.id`, `order_items.product_sku` -> `products.sku`, `orders.cart_id` -> `carts.id`, `import_errors.csv_import_job_id` -> `csv_import_jobs.id`; and all CHECK constraints and UNIQUE constraints are enforced

- [ ] **AC-001.5: Shared validation module exists**
  - **Given** the backend project contains `src/ecommerce/validation.clj`
  - **When** the Malli schemas defined in `validation.clj` are inspected
  - **Then** schemas exist for all Product fields matching the validation contract: `name` (non-empty string after trim, max 255 chars), `sku` (non-empty string, max 50 chars), `description` (optional string, max 2000 chars), `category` (optional string, max 100 chars), `price` (decimal > 0, max 2 decimal places), `stock` (integer >= 0), `weight_kg` (optional decimal >= 0); and the schemas are importable from other namespaces for reuse across product CRUD, CSV import, and search handlers

- [ ] **AC-001.6: Error middleware catches all unhandled exceptions**
  - **Given** the backend application is running with the error-envelope middleware registered
  - **When** any unhandled exception occurs during request processing
  - **Then** the response body is `{"error": {"code": "INTERNAL_ERROR", "message": "<safe human-readable message>"}}` with HTTP status 500; and the response never contains stack traces, exception class names, raw SQL fragments, query plans, file system paths, or internal hostnames; and the error is logged server-side with full diagnostic detail for debugging

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
| `deps.edn` | Clojure dependency map with all libraries at exact pinned versions per tech-stack.md; aliases for `:build`, `:test`, `:lint`, `:fmt` |
| `src/ecommerce/core.clj` | Main entry point; Jetty server bootstrap via `ring-jetty-adapter`; reads port from env or defaults to 3000 |
| `src/ecommerce/router.clj` | Reitit data-driven router; `GET /api/health` endpoint; Malli coercion middleware; JSON content negotiation |
| `src/ecommerce/middleware.clj` | Error-envelope middleware (catches all exceptions, returns `{error: {code, message}}`); security headers middleware; JSON response coercion (no CORS --- same-origin via nginx proxy, see [middleware-pipeline.md](../architecture/middleware-pipeline.md)) |
| `src/ecommerce/db.clj` | HikariCP connection pool configuration via `next.jdbc`; datasource initialization from env vars; migration runner |
| `src/ecommerce/validation.clj` | Shared Malli schemas for all Product fields (name, sku, price, stock, weight_kg, category, description) |
| `resources/migrations/001-create-products.sql` | `CREATE TABLE products` with all columns, constraints, and `idx_products_category` index |
| `resources/migrations/002-create-products-search-trigger.sql` | `products_search_vector_update()` function, trigger, and `idx_products_search_vector` GIN index |
| `resources/migrations/003-create-carts.sql` | `CREATE TABLE carts` with status CHECK constraint and `idx_carts_status` index |
| `resources/migrations/004-create-cart-items.sql` | `CREATE TABLE cart_items` with FK constraints, UNIQUE `(cart_id, product_sku)`, and indexes |
| `resources/migrations/005-create-orders.sql` | `CREATE TABLE orders` with FK to carts, UNIQUE `(cart_id)`, status CHECK, and `idx_orders_status` |
| `resources/migrations/006-create-order-items.sql` | `CREATE TABLE order_items` with FK constraints and indexes |
| `resources/migrations/007-create-csv-import-jobs.sql` | `CREATE TABLE csv_import_jobs` with status CHECK and `idx_csv_import_jobs_status` |
| `resources/migrations/008-create-import-errors.sql` | `CREATE TABLE import_errors` with FK constraints and `idx_import_errors_job_id` |
| `frontend/` | Angular 22 project scaffold: `angular.json`, `tsconfig.json`, `package.json` with pinned deps, app shell with zoneless bootstrap |
| `frontend/src/app/shared/validation/product.schema.ts` | Zod schemas mirroring backend Malli schemas for all Product fields |
| `Dockerfile.backend` | Multi-stage: build (JDK 21, resolve deps, compile uberjar), test (lint + tests), production (JRE 21, uberjar only) |
| `Dockerfile.frontend` | Multi-stage: build (Node 22, pnpm install --frozen-lockfile, ng build), test (lint + vitest), production (nginx 1.27-alpine, static dist only) |
| `docker-compose.yml` | 3 services (db, backend, frontend) with health checks, dependency ordering, shared network |
| `nginx.conf` | Reverse proxy: `/api/*` -> `backend:3000`; serve static Angular build from `/`; gzip compression |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| N/A | This is the initial scaffolding; no pre-existing files to modify |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `health-endpoint-returns-200` | AC-001.1 | `GET /api/health` returns HTTP 200 with body `{"status": "ok"}` |
| `deps-edn-has-no-floating-versions` | AC-001.1 | All dependency coordinates in `deps.edn` specify exact `:mvn/version` strings with no ranges |
| `error-middleware-returns-envelope-shape` | AC-001.1, AC-001.6 | An intentionally triggered exception returns `{"error": {"code": "INTERNAL_ERROR", "message": ...}}` |
| `error-middleware-does-not-leak-stack-trace` | AC-001.6 | Error response body does not contain Java class names, `.clj` file paths, or SQL fragments |
| `error-middleware-does-not-leak-sql` | AC-001.6 | Error response body does not contain `SELECT`, `INSERT`, `UPDATE`, `DELETE`, or `FROM` keywords in raw form |
| `error-middleware-does-not-leak-file-paths` | AC-001.6 | Error response body does not contain `/src/`, `/home/`, or `.clj` substrings |
| `frontend-builds-without-errors` | AC-001.2 | `ng build --configuration=production` exits with code 0 |
| `package-json-has-exact-versions` | AC-001.2 | No dependency version in `package.json` starts with `^`, `~`, or `*` |
| `angular-bootstraps-zoneless` | AC-001.2 | App config includes `provideZonelessChangeDetection()` and no Zone.js import exists |
| `docker-compose-starts-all-services` | AC-001.3 | `docker compose up --build` results in 3 healthy containers |
| `nginx-proxies-api-to-backend` | AC-001.3 | `GET http://localhost:8080/api/health` returns 200 (proxied through nginx) |
| `migrations-create-all-tables` | AC-001.4 | After migration, `SELECT table_name FROM information_schema.tables` includes all 7 tables |
| `products-table-has-correct-constraints` | AC-001.4 | `price > 0` CHECK exists; `stock >= 0` CHECK exists; `sku` is PRIMARY KEY |
| `search-trigger-exists` | AC-001.4 | `products_search_vector_trigger` is registered on `products` table |
| `foreign-keys-enforced` | AC-001.4 | Inserting a `cart_item` with a non-existent `product_sku` raises a foreign key violation |
| `malli-schema-rejects-empty-name` | AC-001.5 | Validating `{:name ""}` against the Product schema returns a validation error for `name` |
| `malli-schema-rejects-negative-price` | AC-001.5 | Validating `{:price -1}` against the Product schema returns a validation error for `price` |
| `malli-schema-rejects-fractional-stock` | AC-001.5 | Validating `{:stock 1.5}` against the Product schema returns a validation error for `stock` |
| `malli-schema-accepts-valid-product` | AC-001.5 | Validating a complete, valid product map passes without errors |
| `zod-schema-rejects-empty-name` | AC-001.5 | Zod `ProductSchema.safeParse({name: ""})` returns `success: false` |
| `zod-schema-rejects-negative-price` | AC-001.5 | Zod `ProductSchema.safeParse({price: -1})` returns `success: false` |
| `zod-schema-accepts-valid-product` | AC-001.5 | Zod `ProductSchema.safeParse(validProduct)` returns `success: true` |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| N/A | --- | --- | Validation module is created but not enforced on endpoints in this story | --- |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | Docker multi-stage build complexity may cause slow iteration and debugging difficulty | Prototype the multi-stage Dockerfile for one service first (backend); validate layer caching works before applying the pattern to frontend |
| MEDIUM | Jetty async compatibility for future SSE (CSV import progress) | Verify that `ring-jetty-adapter` supports `streaming-body` responses during scaffolding; if not, document the adapter switch needed for EP02 |
| MEDIUM | Angular 22 zoneless mode is relatively new; potential for unexpected behavior with third-party libraries | Keep the frontend scaffold minimal; verify `provideZonelessChangeDetection()` works with `HttpClient` and `Router` before adding feature components |
| LOW | Migration ordering sensitivity in `docker-entrypoint-initdb.d/` | Prefix all migration files with zero-padded numbers (001-008) to guarantee lexicographic execution order |

## 10. Out of Scope

- Feature endpoints (CRUD, search, cart, import) --- covered by US-002 through US-014+
- E2E Playwright setup --- covered by a dedicated infrastructure story
- README content --- covered by EP06 documentation story
- Frontend routing beyond the shell (feature routes added per feature story)
- Production optimizations (CDN, caching headers, compression tuning)
- CI/CD pipeline configuration
- Database seeding with sample data

## 11. Notes

- This story produces no user-facing features. Its sole purpose is to establish the foundation that all subsequent stories build upon. It is the only story with no business logic tests (pure scaffolding exception per the testing strategy).
- The Malli validation schemas created here define the single source of truth for product field validation. All subsequent stories (US-002 create, US-003 update, CSV import) import and reuse these schemas rather than defining their own.
- The Zod schemas in `frontend/src/app/shared/validation/product.schema.ts` must mirror the Malli schemas exactly. A contract test (created in a later story) will verify both schemas produce identical accept/reject decisions for a canonical set of inputs.
- Migration files are plain SQL with `IF NOT EXISTS` guards, per the data model's migration strategy. No ORM migration tool is used.

## 12. Related Documents

- [Tech Stack](../architecture/tech-stack.md) --- exact library versions and build pipeline
- [Data Model](../architecture/data-model.md) --- table definitions and migration strategy
- [API Contract](../architecture/api-contract.md) --- error envelope shape and health endpoint
- [Testing Strategy](../architecture/testing-strategy.md) --- scaffolding exception to TDD rule
- [EP06 --- Containerization & Documentation](../epics/EP06-containerization-docs.md) --- parent epic

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
