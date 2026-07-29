# T-001 --- Project Scaffolding

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-001 |
| Batch | 1 |
| Epic | EP06 --- Containerization & Docs |
| Story | [US-001](../../user-stories/US-001-project-scaffolding.md) |
| Persona | Developer |
| Model Tier | standard |
| Priority | Must Have |

## Objective

Bootstrap the entire project skeleton: Clojure backend (Ring/Reitit), Angular 22 zoneless frontend, Docker Compose with 3 services (backend, frontend, postgres), 8 database migration files, shared Malli validation module, and error middleware. Every subsequent task depends on this foundation being solid.

## Pre-conditions

- [ ] Empty repository with `docs/` directory already committed
- [ ] Docker and Docker Compose available on the host machine
- [ ] No prior application code exists in the repository

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| docs/architecture/tech-stack.md | Section 1 (Stack Overview), Section 2 (Backend), Section 3 (Frontend), Section 6 (Docker Architecture) | Exact versions and libraries to use |
| docs/architecture/data-model.md | Section 2 (Table Definitions --- all 7 tables) | All 7 tables and their DDL |
| docs/architecture/api-contract.md | Section 1 (Overview --- Health Check Endpoint), Section 2 (Standard Error Response) | Health endpoint contract, error envelope shape |
| docs/architecture/security-guidelines.md | Section 2 (Cart Cookie --- Signed Identity), Section 5 (Security Headers), Section 6 (Input Security), Section 9 (Dependency --- buddy-sign) | buddy-sign dependency, security headers middleware, cookie config, sanitization approach |
| docs/user-stories/US-001-project-scaffolding.md | all | Acceptance criteria for scaffolding |
| docs/domain-glossary.md | Entities, Value Objects, Conventions | Domain terms for naming consistency |
| docs/architecture/middleware-pipeline.md | Section 2 (Middleware Stack), Section 6 (Putting It All Together) | Middleware stack ordering and assembly |
| docs/architecture/validation-pruning.md | Section 2 (Malli Schema Design --- Closed Maps) | Malli closed schema configuration |
| docs/architecture/error-handling.md | Section 3 (Custom Exception Middleware) | Exception middleware setup |
| docs/architecture/tdd-workflow.md | Section 7 (When TDD Doesn't Apply) | TDD process for implementation tasks |
| docs/architecture/api-docs-strategy.md | Section 4 (Setup) | OpenAPI/Swagger UI route setup |
| docs/architecture/pnpm-config.md | Section 2 (package.json Configuration), Section 5 (Docker Integration) | pnpm configuration for frontend scaffold |
| docs/architecture/health-check-strategy.md | Health Endpoint Contract, Shutdown Behavior | Health endpoint contract and behavior |
| docs/architecture/testing-strategy.md | Section 2 (Test Pyramid), Section 3 (What to Test per Epic), Section 5 (Security Test Cases), Section 7 (TDD Workflow) | Test pyramid, per-epic test matrix, security test cases, TDD scaffolding exception |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| deps.edn | Clojure dependency manifest with pinned versions; aliases for :build, :test, :lint, :fmt |
| src/ecommerce/core.clj | Application entry point, server startup; shutdown hook (stop Jetty, close HikariCP pool, flush logs) |
| src/ecommerce/router.clj | Reitit router with /api/health route |
| src/ecommerce/middleware.clj | Error handling, security headers, content-type middleware (no CORS --- same-origin via nginx, see [middleware-pipeline.md](../../architecture/middleware-pipeline.md)) |
| src/ecommerce/config.clj | Reads and validates ALL env vars at startup before Jetty binds; every required var must be non-nil AND non-blank; PORT/DB_PORT parse as integers in 1–65535; on failure logs "Missing or invalid environment variable: <NAME>" and calls (System/exit 1). Exposes validated config map to db.clj and core.clj. References canonical env var table in tech-stack.md §6. |
| src/ecommerce/db.clj | Database connection pool via next.jdbc; HikariCP connection pool configured from validated config map (see config.clj); no direct env var reads; HikariCP configured with initializationFailTimeout=-1 (lazy init), connectionTimeout=5000 (fail fast on pool exhaustion) |
| src/ecommerce/validation.clj | Shared Malli schemas for product, cart, order |
| resources/logback.xml | Logback configuration: console appender (stdout only, no file rotation --- Docker captures logs); root level from `${LOG_LEVEL:-INFO}`; HikariCP and Jetty loggers at WARN to suppress housekeeping noise |
| resources/migrations/001-create-products.sql | Create `products` table, `search_vector` column, and `idx_products_category` |
| resources/migrations/002-create-products-search-trigger.sql | Create `products_search_vector_update()` function, its trigger, and `idx_products_search_vector` (GIN) |
| resources/migrations/003-create-carts.sql | Create `carts` table and `idx_carts_status` |
| resources/migrations/004-create-cart-items.sql | Create `cart_items` table, its `UNIQUE (cart_id, product_sku)` constraint, and supporting indexes |
| resources/migrations/005-create-orders.sql | Create `orders` table, `UNIQUE (cart_id)`, and `idx_orders_status` |
| resources/migrations/006-create-order-items.sql | Create `order_items` table and supporting indexes |
| resources/migrations/007-create-csv-import-jobs.sql | Create `csv_import_jobs` table and `idx_csv_import_jobs_status` |
| resources/migrations/008-create-import-errors.sql | Create `import_errors` table and `idx_import_errors_job_id` |
| frontend/ | Angular 22 scaffold (ng new, zoneless) |
| frontend/src/app/shared/validation/product.schema.ts | Shared product validation (mirrors Malli schemas) |
| Dockerfile.backend | Multi-stage Clojure build |
| Dockerfile.frontend | Multi-stage Angular build with nginx |
| docker-compose.yml | 3 services (backend, frontend, db) with health checks, dependency ordering, shared network; db service mounts ./resources/migrations into /docker-entrypoint-initdb.d for migration execution; NO docker.sock mount in the backend service definition --- Testcontainers gets the socket via the test command only (`docker compose run -v /var/run/docker.sock:/var/run/docker.sock backend clojure -M:test`) |
| nginx.conf | Frontend reverse proxy config |
| .dockerignore | Excludes .git, node_modules, dist, docs, *.md, .env* from Docker build context |
| .env.example | All env vars from tech-stack.md §6 with safe dev defaults and comments; committed to repo (unlike .env which is gitignored) |
| README.md | Placeholder with project name and "See docs/ for documentation" |

### Files to Modify

| File | Change |
|------|--------|
| .gitignore | Add Clojure, Node, Docker ignore patterns |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep06/T-001-project-scaffolding.md` | EXE | exit 0 |
| 2 | Docker builds | `docker compose up --build -d` | EXE | All 3 services start without error |
| 3 | Health endpoint | `docker compose exec backend curl -sf http://localhost:3000/api/health` | EXE | HTTP 200 with JSON body (backend port is not published to the host; curl is in the image per tech-stack.md) |
| 4 | Tables created | `docker compose exec db psql -U app -d ecommerce -c "\dt"` | EXE | 7 tables listed |
| 5 | No floating versions | `grep -E 'LATEST|RELEASE' deps.edn` | EXE | No matches (exit 1) |
| 6 | Angular builds | `docker compose run --rm frontend pnpm exec ng build` | EXE | exit 0, zero errors |
| 7 | Backend tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 8 | No side effects | `git diff --stat` | EXE | Only expected files |
| 9 | Backend lint | `docker compose run --rm backend clojure -M:lint` | EXE | exit 0, no lint errors |
| 10 | Backend format | `docker compose run --rm backend clojure -M:fmt --check` | EXE | exit 0, no format violations |
| 11 | Frontend lint | `docker compose run --rm frontend pnpm exec ng lint` | EXE | exit 0, no lint errors |
| 12 | Frontend format | `docker compose run --rm frontend pnpm exec prettier --check .` | EXE | exit 0, no format violations |
| 13 | Env validation fail-fast | `docker compose run --rm -e DB_PASSWORD= backend java -jar app.jar` | EXE | Exits non-zero, output names DB_PASSWORD |

## Boundaries

- NOT in scope: Feature endpoints (CRUD, search, cart, import) --- these depend on the scaffolding existing first; each is delivered as its own dedicated task (T-002 onward) per the epic DAG
- NOT in scope: E2E Playwright setup --- requires the full application stack (every epic) to be meaningful; added in T-015 once every feature exists
- NOT in scope: README content (placeholder README.md is a deliverable of this task) --- the full README (T-016) must document the final, working system; only a placeholder is needed now
- NOT in scope: Frontend routing beyond app shell --- no feature views exist yet to route to; routes are added incrementally by each EP05 task
- NOT in scope: Seed data or sample records --- not required by any acceptance criterion; would add maintenance burden without evaluation value

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Use LATEST or RELEASE in deps.edn | Non-reproducible builds, breaks CI | Pin exact versions (e.g., `ring/ring-core {:mvn/version "1.12.1"}`) |
| Install JDK or Node locally | Breaks portability, "works on my machine" | Use Docker for all builds and runtime |
| Use Zone.js in Angular | Contradicts Angular 22 zoneless requirement | Use `provideZonelessChangeDetection()` |
| Single monolithic migration file | Hard to reason about, impossible to roll back selectively | One file per table, numbered sequentially |
| Hardcode DB credentials | Security risk, inflexible | Use environment variables via docker-compose.yml |

## Rollback Guidance

```bash
git checkout -- . && docker compose down -v
```

This removes all generated files and tears down containers including volumes (database data).

## Compact Rules

### PROJECT-TEST
- All tests must pass before any commit
- New features require corresponding tests
- TDD (Red/Green/Refactor) is the default
- Breaking an existing test is a blocking issue
- Tests map directly to acceptance criteria
- Test evidence is required for DOD

### PROJECT-ANTI-DRIFT
- Scope is defined by the handoff — work outside boundaries is a violation
- Version pinning: exact versions only (no floating ranges)
- Dead code and unused dependencies MUST be removed

### PROJECT-PIPELINE
- Pipeline stages: install → build → lint → test:unit → test:integration → test:e2e
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] deps.edn
- [ ] src/ecommerce/core.clj
- [ ] src/ecommerce/router.clj
- [ ] src/ecommerce/middleware.clj
- [ ] src/ecommerce/config.clj
- [ ] src/ecommerce/db.clj
- [ ] src/ecommerce/validation.clj
- [ ] resources/logback.xml
- [ ] resources/migrations/001-008.sql (8 files)
- [ ] frontend/ (Angular scaffold)
- [ ] frontend/src/app/shared/validation/product.schema.ts
- [ ] Dockerfile.backend
- [ ] Dockerfile.frontend
- [ ] docker-compose.yml
- [ ] nginx.conf
- [ ] .dockerignore
- [ ] .env.example
- [ ] README.md

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Docker builds
- [ ] Gate 3: Health endpoint returns 200
- [ ] Gate 4: 7 tables created
- [ ] Gate 5: No floating versions
- [ ] Gate 6: Angular builds cleanly
- [ ] Gate 7: Backend tests pass
- [ ] Gate 8: No side effects
- [ ] Gate 9: Backend lint passes
- [ ] Gate 10: Backend format passes
- [ ] Gate 11: Frontend lint passes
- [ ] Gate 12: Frontend format passes
- [ ] Gate 13: Env validation fail-fast
