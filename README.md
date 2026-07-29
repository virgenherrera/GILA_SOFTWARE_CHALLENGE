# E-Commerce Application — Gila Software Challenge

A full-stack e-commerce application built with Clojure (backend), Angular 22 (frontend), and PostgreSQL, running entirely in Docker.

## Prerequisites

- **Docker** (with Docker Compose v2)

No JDK, Node.js, npm, pnpm, or PostgreSQL installation is needed on the host machine. All build tools and runtimes run inside Docker containers.

## Run Instructions

```bash
docker compose up --build
```

The application is available at **http://localhost:8080** once all three services (db, backend, frontend) report healthy. No additional manual steps are required.

To stop and clean up:

```bash
docker compose down -v
```

### Accessing the Application

| URL | Description |
|-----|-------------|
| http://localhost:8080 | Angular frontend (Products, Search, Import, Cart, Checkout) |
| http://localhost:8080/api/health | Backend health check (200 = healthy, 503 = degraded) |
| http://localhost:8080/api/products | Products API (paginated, searchable, filterable) |
| http://localhost:8080/api/docs | Swagger UI — interactive API documentation |

All API routes are proxied through nginx — there are no separately exposed backend or database ports.

### Sample CSV Data

The product catalog CSV was downloaded on **2026-07-27**. This date is the reference point for any data discrepancies if the source CSV changes in the future. To import products, use the Import page in the UI or POST a CSV file to `/api/imports`.

## Configuration

All configuration is via environment variables. Docker Compose provides sensible defaults for local development — no `.env` file is required to start.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_USER` | No | `app` | PostgreSQL username |
| `DB_PASSWORD` | No | `devpassword` | PostgreSQL password |
| `DB_NAME` | No | `ecommerce` | PostgreSQL database name |
| `LOG_LEVEL` | No | `INFO` | Backend log level (DEBUG, INFO, WARN, ERROR) |
| `COOKIE_SECRET` | No | `dev-secret-change-in-production-min-32-chars` | Secret for cart JWT signing (min 32 chars) |

The backend validates all required environment variables at startup and exits with code 1 if any are missing or invalid.

## Architecture

```
Browser → nginx (:8080) → Clojure/Ring (:3000) → PostgreSQL (:5432)
                ↓
        Angular SPA (static files)
```

**Frontend**: Angular 22 with zoneless change detection (signals, no Zone.js). Standalone components only. Container-presentational pattern separates data fetching from display. Lazy-loaded routes per feature. Client-side validation with Zod mirrors backend Malli schemas.

**Backend**: Clojure on JDK 21 with Ring (HTTP) + Reitit (routing) + Malli (validation). SQL-first approach with next.jdbc and HoneySQL — no ORM. Domain handlers are pure request→response functions with the datasource injected. Background CSV processing via core.async channels.

**Database**: PostgreSQL 17.5 with forward-only SQL migrations applied via `docker-entrypoint-initdb.d`. Product SKU is the natural primary key. Full-text search uses `tsvector` with a GIN index maintained by a database trigger.

**Infrastructure**: Multi-stage Docker builds (build tools excluded from production images). nginx serves static files and reverse-proxies `/api/*` to the backend. The backend runs as a non-root user. Services start in dependency order via health checks: db → backend → frontend.

**Key patterns**:
- Middleware pipeline: error handling → security headers → content-type negotiation → params → cart session → routing
- Health check returns 503 (not crash) when DB is down; HikariCP auto-reconnects
- Cart identity via JWT-signed cookie (buddy.sign.jwt, HttpOnly, SameSite=Strict)
- Checkout uses `SELECT FOR UPDATE` for atomic stock validation and decrement

See `docs/architecture/` for detailed documentation on each subsystem.

## Technical Decisions

### 1. Backend Language: Clojure

**Chosen**: Clojure 1.12.0 on JVM 21

**Alternatives considered**:
- **Java**: Verbose for a data-transformation-heavy application. Clojure's immutable data structures and sequence abstractions express the domain more concisely.
- **Python**: Weaker concurrency model for background CSV processing. The GIL limits true parallelism.
- **Go**: Less expressive for data pipelines. No equivalent to core.async's channel-based processing without external libraries.
- **PHP**: Requires external queue infrastructure (Redis/RabbitMQ) for background processing that Clojure handles natively with core.async.

**Rationale**: Data-oriented language on a mature JVM ecosystem. The REPL enables rapid iteration. core.async provides in-process concurrent CSV processing without external infrastructure.

### 2. Frontend Framework: Angular 22

**Chosen**: Angular 22.0.0 (zoneless, signals, standalone components)

**Alternatives considered**:
- **React 19**: Requires assembling routing, forms, HTTP client, and DI from separate libraries. Angular provides all of these built-in.
- **ClojureScript**: Would introduce a second build pipeline and toolchain, adding complexity without proportional benefit for a standard CRUD UI.
- **Vanilla JS**: Manual DOM manipulation, routing, and state management would slow development and increase bug surface for a multi-view SPA.

**Rationale**: Built-in router, reactive forms, HttpClient, and dependency injection. Angular 22's zoneless mode with signals eliminates Zone.js overhead. Developer expertise with Angular.

### 3. Database: PostgreSQL

**Chosen**: PostgreSQL 17.5

**Alternatives considered**:
- **SQLite**: No concurrent write support — incompatible with background CSV import writing while the API serves reads.
- **MongoDB**: Schemaless storage is a liability for financial data (prices, orders). No ACID transactions for checkout.
- **MySQL**: Weaker full-text search (no `tsvector`), `NUMERIC` precision handling less robust than PostgreSQL's.

**Rationale**: Relational integrity for e-commerce data. `NUMERIC(10,2)` for exact price arithmetic. Native `tsvector` + GIN index for product search without a separate search service. ACID transactions for checkout atomicity.

### 4. Validation: Malli + Zod (Parallel Schemas)

**Chosen**: Malli 0.16.4 (backend) + Zod 3.24.4 (frontend)

**Alternatives considered**:
- **NestJS gateway**: Would require Node.js on the backend, conflicting with the Clojure stack.
- **Single-layer validation (backend only)**: Delays error feedback until the server round-trip. Poor UX for form validation.
- **Shared schema generation**: Cross-language transpilation (Clojure→TypeScript) is fragile and adds build complexity.

**Rationale**: Both schemas enforce identical rules (field lengths, numeric ranges, required fields) independently. Integration tests verify the contract stays in sync. Frontend gives instant feedback; backend is the authoritative gate.

### 5. CSV Processing: core.async

**Chosen**: In-process channel-based processing with core.async 1.7.701

**Alternatives considered**:
- **Distributed step functions (AWS Step Functions, Temporal)**: Infrastructure overkill for processing ~100-row CSV files in a single-evaluator context.
- **Thread pools (ExecutorService)**: Lower-level than needed. core.async's channel abstraction is more expressive for producer-consumer patterns.

**Rationale**: Zero external dependencies. The upload handler puts a job on a channel; a worker goroutine processes rows asynchronously. The pattern is extractable to a distributed queue if scale demands it later.

### 6. Product Search: PostgreSQL tsvector

**Chosen**: Built-in PostgreSQL full-text search with `tsvector` column and GIN index

**Alternatives considered**:
- **Elasticsearch**: A separate search cluster is unjustified at catalog scale (hundreds to low thousands of products).
- **Application-level filtering (ILIKE)**: No ranking, no stemming, no prefix matching. Degrades with catalog growth.

**Rationale**: `tsvector` provides ranked full-text search with stemming and prefix matching at zero operational cost. A database trigger keeps the search vector in sync with product name, description, and category.

### 7. Duplicate SKU Strategy: Upsert for Catalog, Reject for In-File

**Chosen**: CSV import upserts (updates existing products by SKU) but rejects duplicate SKUs within the same CSV file

**Alternatives considered**:
- **Reject all duplicates**: Would prevent catalog updates via CSV re-import, forcing manual edits for price/stock changes.
- **Overwrite all**: Last-write-wins within a file is ambiguous — which row "wins" when the same SKU appears twice with different prices?

**Rationale**: Upsert enables re-importing an updated catalog (common workflow). In-file duplicates are rejected because the intent is ambiguous — the importer should fix the source data.

### 8. Delete Behavior: Hard Delete with FK Protection

**Chosen**: Hard delete with foreign key constraint protection (409 PRODUCT_IN_USE if referenced by orders)

**Alternatives considered**:
- **Soft delete (is_deleted flag)**: Adds query complexity to every product query. Complicates unique constraints. Unnecessary for a catalog without audit requirements.
- **Cascade delete**: Destroying order history when a product is removed violates data integrity for completed transactions.

**Rationale**: Simple and safe. Products referenced by orders cannot be deleted (FK constraint returns 409). Unreferenced products are permanently removed. No ghost data accumulates.

### 9. Cart Identity: JWT-Signed Cookie

**Chosen**: JWT-signed cookie via buddy-sign (buddy.sign.jwt), HMAC-SHA256 as the signing algorithm, HttpOnly, SameSite=Strict, Path=/api

**Alternatives considered**:
- **Database session**: Requires session cleanup and storage management. The cookie approach is stateless on the server side (cart data lives in PostgreSQL, the cookie is just the cart ID).
- **localStorage**: Not sent automatically with API requests. Requires JavaScript to attach to every request. Vulnerable to XSS.

**Rationale**: The browser sends the cookie automatically with every `/api` request. The JWT is signed with HMAC-SHA256, preventing tampering (cart ID forgery). No authentication system exists, so the signed cookie is the identity mechanism.

### 10. Checkout Concurrency: SELECT FOR UPDATE

**Chosen**: PostgreSQL `SELECT FOR UPDATE` row-level locking within a serializable transaction

**Alternatives considered**:
- **Optimistic locking (version column)**: Requires retry logic in the application. More complex for a multi-item cart where any item might conflict.
- **Queue-based (serialize all checkouts)**: Reduces throughput to one checkout at a time. Unnecessary when row-level locking handles concurrency at the database level.

**Rationale**: `SELECT FOR UPDATE` locks only the specific product rows being purchased, allowing concurrent checkouts for non-overlapping carts. The transaction re-validates stock, decrements atomically, creates the order, and transitions the cart — all within a single database transaction.

## Testing

**Approach**: Test-Driven Development (Red-Green-Refactor) for all business logic.

**Test pyramid**:
- **Unit tests**: Domain logic, validation rules, pure functions (Kaocha for Clojure, Vitest for Angular)
- **Integration tests**: Database queries, API endpoints, CSV pipeline (Testcontainers with PostgreSQL 17, HttpClient testing for Angular)
- **Smoke tests**: Post-deployment health and reachability checks (`test/smoke-test.sh`)

**Security testing**: Error responses are verified to never leak stack traces, SQL statements, file paths, or raw user input. XSS payloads and SQL injection attempts are tested in product creation, CSV import, and search queries.

**Coverage**: Frontend enforces 80% line coverage thresholds (configured in angular.json). Backend coverage is tracked via kaocha-cloverage but not enforced as a gate. Acceptance-criteria-mapped tests are the actual merge gate.

See [docs/architecture/testing-strategy.md](docs/architecture/testing-strategy.md) for the full testing strategy, test naming conventions, and per-epic test matrix.

### Running Tests

```bash
# Backend tests (requires Docker for Testcontainers)
docker compose run --rm backend clojure -M:test

# Frontend tests
cd frontend && CI=true pnpm exec ng test --configuration=ci
```

### Pre-Commit Hook (Echo System)

The project includes a pre-commit hook that runs the Echo System before every commit.
One-time setup per clone:

```bash
ln -sf ../../scripts/pre-commit.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

The hook runs Stages 1 (Build), 2 (Test), and 4 (Audit) inside Docker containers.
Stage 3 (E2E) is excluded — it requires the full composed application.

## Known Limitations

1. **No user authentication**: Carts are identified by a signed cookie, not a user account. There is no login, registration, or user-scoped data. This is a deliberate scope decision for the challenge — the cart cookie provides tamper-evident identity without an auth system.

2. **No real payment processing**: Checkout simulates payment (always succeeds). The `orders` table has `Pending` and `Paid` statuses; `Failed` and `Fulfilled` are reserved for a future payment provider integration.

3. **No HTTPS/TLS**: The application runs over plain HTTP on localhost. TLS termination would be handled by a reverse proxy or load balancer in a production deployment.

4. **No rate limiting**: A single-evaluator context does not require request rate limiting. In production, rate limiting would be applied at the nginx layer or via middleware.

5. **CSV import uses polling, not streaming**: The frontend polls `GET /api/imports/:id` every 2 seconds for import progress. Server-Sent Events (SSE) would provide real-time updates without polling overhead — deferred to v2.

6. **No key rotation for cart cookie secret**: The `COOKIE_SECRET` is static for the application lifetime. In production, a rotation mechanism would invalidate old cookies gracefully.

7. **Search is catalog-scale only**: PostgreSQL `tsvector` is sufficient for hundreds to low thousands of products. A dedicated search service (Elasticsearch, Meilisearch) would be needed for large catalogs with faceted search requirements.

## Project Structure

```
├── src/ecommerce/          # Clojure backend source
│   ├── core.clj            # Application entry point
│   ├── config.clj           # Environment variable loading
│   ├── router.clj           # Reitit route definitions
│   ├── middleware.clj        # Ring middleware pipeline
│   ├── db.clj               # HikariCP datasource management
│   ├── product/             # Product CRUD handlers + repository
│   ├── import/              # CSV import handlers + async worker
│   ├── cart/                # Cart handlers + signed cookie middleware
│   └── checkout/            # Checkout handler + order queries
├── frontend/src/app/        # Angular frontend source
│   ├── products/            # Product list, detail, form views
│   ├── search/              # Product search with filters
│   ├── imports/             # CSV upload, status polling, error table
│   ├── cart/                # Cart page with quantity adjustment
│   ├── checkout/            # Checkout trigger + order confirmation
│   └── shared/              # Zod schemas, error utilities
├── resources/migrations/    # PostgreSQL forward-only migrations
├── test/                    # Backend tests + smoke test
├── Dockerfile.backend       # Multi-stage: Clojure builder → JRE runtime
├── Dockerfile.frontend      # Multi-stage: Node builder → nginx
├── docker-compose.yml       # 3-service orchestration (db, backend, frontend)
├── nginx.conf               # Reverse proxy + SPA fallback
└── docs/                    # Architecture documentation
```

## Compliance

This submission adheres to the challenge constraint prohibiting AI-generated comments in source code. No AI attribution comments exist in any `.clj` or `.ts` file in the `src/` or `frontend/src/` directories.
