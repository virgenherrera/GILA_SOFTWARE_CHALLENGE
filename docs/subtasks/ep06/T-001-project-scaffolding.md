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
| docs/architecture/tech-stack.md | all | Exact versions and libraries to use |
| docs/architecture/database-schema.md | all | All 7 tables and their DDL |
| docs/architecture/api-contracts.md | all | Health endpoint contract, error envelope shape |
| docs/architecture/security-guidelines.md | all | Middleware requirements, sanitization approach |
| docs/user-stories/US-001-project-scaffolding.md | all | Acceptance criteria for scaffolding |
| docs/domain-glossary.md | all | Domain terms for naming consistency |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| deps.edn | Clojure dependency manifest with pinned versions |
| src/ecommerce/core.clj | Application entry point, server startup |
| src/ecommerce/router.clj | Reitit router with /api/health route |
| src/ecommerce/middleware.clj | Error handling, security headers, content-type middleware (no CORS --- same-origin via nginx, see [middleware-pipeline.md](../../architecture/middleware-pipeline.md)) |
| src/ecommerce/db.clj | Database connection pool (HikariCP) and migration runner |
| src/ecommerce/validation.clj | Shared Malli schemas for product, cart, order |
| resources/migrations/001-create-products.sql | Products table DDL |
| resources/migrations/002-create-categories.sql | Categories table DDL |
| resources/migrations/003-create-product-categories.sql | Product-categories join table DDL |
| resources/migrations/004-create-carts.sql | Carts table DDL |
| resources/migrations/005-create-cart-items.sql | Cart items table with FK to products |
| resources/migrations/006-create-orders.sql | Orders table DDL |
| resources/migrations/007-create-order-items.sql | Order items table with FK to products |
| resources/migrations/008-create-import-jobs.sql | Import jobs table DDL |
| frontend/ | Angular 22 scaffold (ng new, zoneless) |
| frontend/src/app/shared/validation/product.schema.ts | Shared product validation (mirrors Malli schemas) |
| Dockerfile.backend | Multi-stage Clojure build |
| Dockerfile.frontend | Multi-stage Angular build with nginx |
| docker-compose.yml | 3 services: backend, frontend, postgres |
| nginx.conf | Frontend reverse proxy config |

### Files to Modify

| File | Change |
|------|--------|
| .gitignore | Add Clojure, Node, Docker ignore patterns |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Handoff exists | `test -f docs/subtasks/ep06/T-001-project-scaffolding.md` | EXE | exit 0 |
| 2 | Docker builds | `docker compose up --build -d` | EXE | All 3 services start without error |
| 3 | Health endpoint | `curl -sf http://localhost:3000/api/health` | EXE | HTTP 200 with JSON body |
| 4 | Tables created | `docker compose exec postgres psql -U ecommerce -c "\\dt"` | EXE | 7 tables listed |
| 5 | No floating versions | `grep -E 'LATEST\|RELEASE' deps.edn` | EXE | No matches (exit 1) |
| 6 | Angular builds | `docker compose run --rm frontend npx ng build` | EXE | exit 0, zero errors |
| 7 | Backend tests pass | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 8 | No side effects | `git diff --stat` | EXE | Only expected files |

## Boundaries

- NOT in scope: Feature endpoints (CRUD, search, cart, import)
- NOT in scope: E2E Playwright setup
- NOT in scope: README content beyond placeholder
- NOT in scope: Frontend routing beyond app shell
- NOT in scope: Seed data or sample records

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| Use LATEST or RELEASE in deps.edn | Non-reproducible builds, breaks CI | Pin exact versions (e.g., `ring/ring-core {:mvn/version "1.12.1"}`) |
| Install JDK or Node locally | Breaks portability, "works on my machine" | Use Docker for all builds and runtime |
| Use Zone.js in Angular | Contradicts Angular 22 zoneless requirement | Use `provideExperimentalZonelessChangeDetection()` |
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
- Pipeline stages: install → build → lint → test:unit → test:integration
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
- [ ] src/ecommerce/db.clj
- [ ] src/ecommerce/validation.clj
- [ ] resources/migrations/001-008.sql (8 files)
- [ ] frontend/ (Angular scaffold)
- [ ] frontend/src/app/shared/validation/product.schema.ts
- [ ] Dockerfile.backend
- [ ] Dockerfile.frontend
- [ ] docker-compose.yml
- [ ] nginx.conf

### Quality Gates
- [ ] Gate 1: Handoff exists
- [ ] Gate 2: Docker builds
- [ ] Gate 3: Health endpoint returns 200
- [ ] Gate 4: 7 tables created
- [ ] Gate 5: No floating versions
- [ ] Gate 6: Angular builds cleanly
- [ ] Gate 7: Backend tests pass
- [ ] Gate 8: No side effects
