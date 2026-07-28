> [INDEX](../INDEX.md) / [User Stories](./) / US-015 --- Docker Compose Multi-Stage Setup

# US-015 --- Docker Compose Multi-Stage Setup

## 1. Metadata

| Field | Value |
| ----- | ----- |
| Epic | [EP06 --- Containerization & Documentation](../epics/EP06-containerization-docs.md) |
| Priority | Must Have |
| Status | Ready |

## 2. Story

**As an** Evaluator,
**I want** to start the entire application with `docker compose up --build` and verify it is healthy,
**so that** I can exercise every feature without installing any local dependencies.

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

- [ ] **AC-015.1: `docker compose up --build` starts all services without errors**
  - **Given** Docker and Docker Compose are installed on the host machine
  - **When** `docker compose up --build` is executed from the repository root
  - **Then** three services start: `db`, `backend`, and `frontend`
  - **And** no service exits with a non-zero status code
  - **And** the terminal output shows all three services running

- [ ] **AC-015.2: PostgreSQL runs all 8 migrations on first startup**
  - **Given** the `db` service starts with a fresh, empty `ecommerce` database
  - **When** the PostgreSQL container initializes via `docker-entrypoint-initdb.d/`
  - **Then** all 8 SQL migration files (`001-create-products.sql` through `008-create-import-errors.sql`) execute in lexicographic order
  - **And** all 7 tables are created: `products`, `carts`, `cart_items`, `orders`, `order_items`, `csv_import_jobs`, `import_errors`
  - **And** all constraints, indexes, triggers, and foreign keys are established per the data model

- [ ] **AC-015.3: Backend container uses multi-stage Dockerfile**
  - **Given** the `Dockerfile.backend` is a multi-stage build
  - **When** `docker compose up --build` builds the backend image
  - **Then** the build stage uses JDK 21 (Eclipse Temurin) to resolve dependencies and compile the uberjar
  - **And** the test stage runs linting and all backend tests
  - **And** the production stage uses JRE 21 only (no JDK, no source code, no build tools)
  - **And** the final image contains only the uberjar and the JRE runtime

- [ ] **AC-015.4: Frontend container uses multi-stage Dockerfile**
  - **Given** the `Dockerfile.frontend` is a multi-stage build
  - **When** `docker compose up --build` builds the frontend image
  - **Then** the build stage uses Node 22 to run `pnpm install --frozen-lockfile` and `ng build --configuration=production`
  - **And** the test stage runs linting and all frontend tests (vitest)
  - **And** the production stage uses nginx 1.27-alpine to serve the compiled Angular static files
  - **And** the final image contains only nginx and the static build output (no Node.js, no `node_modules`, no source code)

- [ ] **AC-015.5: nginx proxies /api/* to backend**
  - **Given** the frontend nginx container is running
  - **When** a request is made to `http://localhost:8080/api/health`
  - **Then** nginx proxies the request to `backend:3000/api/health`
  - **And** the response is `200 OK` with `{"status": "ok"}`
  - **And** no CORS configuration is required because all requests go through the same origin via the proxy

- [ ] **AC-015.6: Health check endpoints work**
  - **Given** all three services are running
  - **When** the Docker health checks execute
  - **Then** the backend health check (`GET /api/health`) returns HTTP 200
  - **And** the database health check (`pg_isready`) succeeds
  - **And** all services report `healthy` status in `docker compose ps`

- [ ] **AC-015.7: Service startup order enforced via health check depends_on**
  - **Given** `docker-compose.yml` defines `depends_on` with `condition: service_healthy`
  - **When** `docker compose up --build` is executed
  - **Then** `db` starts first and becomes healthy before `backend` begins starting
  - **And** `backend` starts and becomes healthy before `frontend` begins starting
  - **And** no service attempts to connect to a dependency that is not yet healthy

- [ ] **AC-015.8: Application reachable at http://localhost:8080**
  - **Given** all services have started and report healthy status
  - **When** the Evaluator opens `http://localhost:8080` in a browser
  - **Then** the Angular application loads and is interactive
  - **And** API calls from the frontend (e.g., product listing) return data through the nginx proxy

- [ ] **AC-015.9: Clean restart with volume removal works**
  - **Given** the application has been previously started with data in the database
  - **When** `docker compose down -v && docker compose up --build` is executed
  - **Then** all containers stop, all volumes are removed, and fresh containers start
  - **And** the database is re-initialized from scratch (all migrations re-run)
  - **And** the application starts in a clean state with no residual data

- [ ] **AC-015.10: No local tool installation required**
  - **Given** a host machine with only Docker and Docker Compose installed
  - **When** `docker compose up --build` is executed
  - **Then** the build completes successfully without requiring JDK, Node.js, npm, or PostgreSQL client on the host
  - **And** all compilation, testing, and packaging happens inside Docker containers

- [ ] **AC-015.11: All dependency versions are exact**
  - **Given** the project's dependency files (`deps.edn`, `package.json`, Dockerfiles)
  - **When** inspected for version specifications
  - **Then** `deps.edn` uses exact `:mvn/version` strings (no `RELEASE`, no `LATEST`, no ranges)
  - **And** `package.json` uses exact versions (no `^`, no `~`, no `*`)
  - **And** Dockerfiles use exact image tags (e.g., `postgres:17.5`, not `postgres:latest`)

## 5. Definition of Done

- [ ] All ACs pass with automated test evidence
- [ ] Smoke test script validates all services are healthy
- [ ] Multi-stage builds pass all tests inside Docker before producing production images
- [ ] No regressions in existing test suite
- [ ] Clean start (`down -v && up --build`) verified
- [ ] Code reviewed
- [ ] INDEX.md updated

## 6. Deliverables

### Files to Create

| File Path | Contents |
| --------- | -------- |
| `test/smoke-test.sh` | Bash script that verifies all services are healthy after `docker compose up --build`; checks: all 3 containers running, health check endpoints respond, database tables exist, frontend serves HTML, nginx proxy works |

### Files to Modify

| File Path | Change |
| --------- | ------ |
| `Dockerfile.backend` | Finalize multi-stage build with all backend source code; ensure build, test, and production stages work end-to-end with the complete application |
| `Dockerfile.frontend` | Finalize multi-stage build with all frontend source code; ensure build, test, and production stages work end-to-end with the complete application |
| `docker-compose.yml` | Final configuration: health checks with intervals/retries, `depends_on` with `service_healthy` conditions, volume mounts for migrations, network configuration, environment variables |
| `nginx.conf` | Final proxy configuration: `/api/*` proxied to `backend:3000`, static Angular files served from `/`, gzip compression |

## 7. Test Plan

| Test Name | AC | Assertion |
| --------- | -- | --------- |
| `compose-up-starts-three-services` | AC-015.1 | `docker compose ps` shows 3 services running |
| `compose-up-no-exit-codes` | AC-015.1 | No service has exited with a non-zero code |
| `migrations-create-all-tables` | AC-015.2 | `SELECT table_name FROM information_schema.tables WHERE table_schema='public'` returns all 7 tables |
| `migrations-run-in-order` | AC-015.2 | Products table exists with search trigger and GIN index |
| `backend-uberjar-in-production-image` | AC-015.3 | Backend production container has no JDK, only JRE and uberjar |
| `backend-tests-run-in-docker` | AC-015.3 | Build logs show test stage executing and passing |
| `frontend-static-in-production-image` | AC-015.4 | Frontend production container has no Node.js, only nginx and static files |
| `frontend-tests-run-in-docker` | AC-015.4 | Build logs show test stage executing and passing |
| `nginx-proxies-api-health` | AC-015.5 | `curl http://localhost:8080/api/health` returns `{"status": "ok"}` |
| `nginx-serves-angular-app` | AC-015.5 | `curl http://localhost:8080/` returns HTML containing Angular app |
| `health-check-backend-200` | AC-015.6 | `GET /api/health` returns HTTP 200 |
| `health-check-db-ready` | AC-015.6 | `pg_isready` inside db container succeeds |
| `all-services-healthy` | AC-015.6 | `docker compose ps` shows all services as "healthy" |
| `startup-order-db-before-backend` | AC-015.7 | Backend does not start until db health check passes |
| `startup-order-backend-before-frontend` | AC-015.7 | Frontend does not start until backend health check passes |
| `app-reachable-at-8080` | AC-015.8 | `curl http://localhost:8080` returns HTTP 200 with HTML content |
| `api-via-proxy-returns-data` | AC-015.8 | `curl http://localhost:8080/api/products` returns a valid paging envelope |
| `clean-restart-works` | AC-015.9 | After `down -v && up --build`, all services start fresh with no residual data |
| `no-local-jdk-required` | AC-015.10 | Build succeeds on a machine with only Docker installed |
| `no-local-node-required` | AC-015.10 | Build succeeds without Node.js on the host |
| `deps-edn-exact-versions` | AC-015.11 | No floating version ranges in `deps.edn` |
| `package-json-exact-versions` | AC-015.11 | No `^`, `~`, or `*` in `package.json` dependency versions |
| `dockerfile-exact-image-tags` | AC-015.11 | No `:latest` tag in any Dockerfile `FROM` instruction |

## 8. Validation Rules

| Field | Required | Type | Constraint | Reject Examples |
| ----- | -------- | ---- | ---------- | --------------- |
| N/A | --- | --- | No user input in this story | --- |

## 9. Risks

| Severity | Risk | Mitigation |
| -------- | ---- | ---------- |
| HIGH | Multi-stage build failures due to test failures in Docker --- tests must pass before production image is created | Ensure all tests pass locally before finalizing Dockerfiles; CI/CD would catch regressions (out of scope, but document the dependency) |
| MEDIUM | Port conflicts on host machine --- ports 8080 and 5432 must be free | Document required free ports in README; use non-standard ports to reduce conflict likelihood |
| MEDIUM | Docker build cache invalidation --- layer ordering matters for build speed | Order Dockerfile instructions from least-changing (base image, system deps) to most-changing (source code) to maximize cache hits |
| LOW | PostgreSQL `docker-entrypoint-initdb.d/` only runs on first initialization | Document `docker compose down -v` requirement for clean re-initialization; AC-015.9 covers this explicitly |
| LOW | Large Docker images if multi-stage COPY is not precise | Only COPY the specific artifacts needed in each stage (uberjar, dist/ folder), never the entire build context |

## 10. Out of Scope

- CI/CD pipeline configuration (GitHub Actions, Jenkins, etc.)
- Production deployment (cloud hosting, Kubernetes, etc.)
- Horizontal scaling or load balancing
- SSL/TLS certificate configuration
- Environment-specific configuration files (staging, production)
- Docker image size optimization beyond multi-stage basics
- Docker Compose profiles for development vs. production

## 11. Notes

- US-001 creates the initial Docker skeleton (Dockerfiles, docker-compose.yml, nginx.conf). This story (US-015) FINALIZES the Docker setup after all features are implemented, ensuring the multi-stage build pipeline works end-to-end with all source code, all tests passing inside Docker, and the complete application serving correctly.
- The migration execution strategy uses PostgreSQL's `docker-entrypoint-initdb.d/` directory. SQL files are copied into this directory and executed in lexicographic order on first container initialization only. For subsequent starts without volume removal, the existing database persists.
- The smoke test script (`test/smoke-test.sh`) is designed to be run after `docker compose up --build` completes. It verifies the system holistically without requiring any tools beyond `curl` and `docker compose`.
- The nginx proxy eliminates the need for CORS configuration: the frontend and API are served from the same origin (`localhost:8080`), with nginx routing `/api/*` to the backend internally.

## 12. Related Documents

- [EP06 --- Containerization & Documentation](../epics/EP06-containerization-docs.md) --- parent epic
- [Tech Stack](../architecture/tech-stack.md) --- Docker, PostgreSQL, JDK, Node.js, nginx versions
- [Data Model](../architecture/data-model.md) --- migration files and table definitions
- [API Contract](../architecture/api-contract.md) --- health check endpoint
- [Testing Strategy](../architecture/testing-strategy.md) --- tests run inside Docker build
- [US-001 --- Project Scaffolding](./US-001-project-scaffolding.md) --- initial Docker skeleton
- [US-002 --- Product CRUD API](./US-002-product-crud-api.md) through [US-010 --- Checkout & Orders API](./US-010-checkout-orders-api.md) --- all backend features
- [US-011 --- Product Management Views](./US-011-product-management-views.md) through [US-014 --- Cart & Checkout Views](./US-014-cart-checkout-views.md) --- all frontend features
- [US-016 --- README & Decision Documentation](./US-016-readme-documentation.md) --- documents run instructions

## 13. Handoff Files

TBD --- populated during Plan phase.

## 14. Change Log

| Date | Change | Reason |
| ---- | ------ | ------ |
| 2026-07-27 | Initial creation | Refine phase |
