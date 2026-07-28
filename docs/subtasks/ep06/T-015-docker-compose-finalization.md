# T-015 --- Docker Compose Multi-Stage Finalization

## Metadata

| Field | Value |
|-------|-------|
| Task ID | T-015 |
| Batch | 3 |
| Epic | EP06 --- Containerization & Docs |
| Story | [US-015](../../user-stories/US-015-docker-compose-finalization.md) |
| Persona | Evaluator |
| Model Tier | standard |
| Priority | Must Have |
| Depends On | T-002 through T-014 (all backend and frontend tasks) |

## Objective

Finalize Docker multi-stage builds and compose configuration so that `docker compose up --build` starts the complete application with all features, all tests pass inside Docker, and no local dependencies (JDK, Node, PostgreSQL) are required on the host machine.

## Pre-conditions

- [ ] ALL backend tasks (T-002 through T-010) are complete
- [ ] ALL frontend tasks (T-011 through T-014) are complete
- [ ] Dockerfile.backend exists (from T-001)
- [ ] Dockerfile.frontend exists (from T-001)
- [ ] docker-compose.yml exists (from T-001)
- [ ] nginx.conf exists (from T-001)

## Context Bundle

| File | Lines | Why Needed |
|------|-------|------------|
| Dockerfile.backend | all | Current backend Dockerfile to finalize |
| Dockerfile.frontend | all | Current frontend Dockerfile to finalize |
| docker-compose.yml | all | Current compose config to finalize |
| nginx.conf | all | Current nginx config to finalize |
| deps.edn | all | Backend dependencies for build stage |
| frontend/package.json | all | Frontend dependencies for build stage |
| docs/architecture/tech-stack.md | all | Exact base image versions |
| docs/architecture/api-contract.md | health endpoint | Health check endpoint for readiness probe |
| docs/architecture/middleware-pipeline.md | all | Verify middleware assembly matches documented stack |
| docs/architecture/pnpm-config.md | all | Frontend Dockerfile pnpm configuration |
| docs/architecture/health-check-strategy.md | all | Docker healthcheck configuration |
| docs/architecture/tdd-workflow.md | all | TDD process reference |

## Deliverables

### Files to Create

| File | Purpose |
|------|---------|
| test/smoke-test.sh | Post-startup smoke test: checks services are up, health endpoint responds, frontend reachable |

### Files to Modify

| File | Change |
|------|--------|
| Dockerfile.backend | Finalize multi-stage: build (uberjar) -> test (run tests) -> prod (JRE only, no JDK) |
| Dockerfile.frontend | Finalize multi-stage: build (ng build) -> test (vitest) -> prod (nginx only, no Node) |
| docker-compose.yml | Final service config: startup order, health checks, internal network, no exposed DB port |
| nginx.conf | Final proxy rules: /api/* -> backend:3000, / -> frontend static files |

## Quality Gates

| # | Gate | Command/Check | Type | Pass Criteria |
|---|------|---------------|------|---------------|
| 1 | Clean start | `docker compose down -v && docker compose up --build -d` | EXE | All 3 services start without error |
| 2 | Migrations run | `docker compose exec postgres psql -U ecommerce -c "\\dt"` | EXE | All 8 migration tables present |
| 3 | Backend multi-stage | Inspect Dockerfile.backend | REVIEW | build: uberjar, test: runs tests, prod: JRE only |
| 4 | Frontend multi-stage | Inspect Dockerfile.frontend | REVIEW | build: ng build, test: vitest, prod: nginx only |
| 5 | Nginx proxy | `curl -sf http://localhost:8080/api/health` | EXE | HTTP 200, proxied to backend:3000 |
| 6 | Frontend reachable | `curl -sf http://localhost:8080` | EXE | HTTP 200, Angular app served |
| 7 | Health checks | `docker compose ps` | EXE | All services show "healthy" status |
| 8 | Startup order | Inspect docker-compose.yml depends_on | REVIEW | db -> backend -> frontend |
| 9 | No host deps | No JDK/Node/npm/PostgreSQL commands used outside Docker | REVIEW | All tooling runs inside containers |
| 10 | No exposed DB | `docker compose port postgres 5432` returns empty | EXE | PostgreSQL not exposed to host |
| 11 | Exact versions | No `latest` tags in Dockerfiles, no floating versions in deps | REVIEW | All versions pinned |
| 12 | Backend tests | `docker compose run --rm backend clojure -M:test` | EXE | exit 0 |
| 13 | Frontend tests | `docker compose run --rm frontend pnpm exec vitest run` | EXE | exit 0 |
| 14 | Smoke test | `bash test/smoke-test.sh` | EXE | exit 0 |
| 15 | Clean rebuild | `docker compose down -v && docker compose up --build -d` | EXE | Idempotent, no stale state |

## Boundaries

- NOT in scope: CI/CD pipeline configuration
- NOT in scope: Production deployment (Kubernetes, ECS, etc.)
- NOT in scope: Horizontal scaling or load balancing
- NOT in scope: SSL/TLS termination
- NOT in scope: Docker image size optimization beyond multi-stage (no distroless, no alpine micro-optimization)

## Anti-patterns

| What | Why It Fails | Do Instead |
|------|-------------|------------|
| COPY node_modules into production image | Bloats image with dev deps, security risk | Only COPY dist/ output into nginx stage |
| Use JDK in production image | Unnecessary 300MB+, attack surface | Use JRE-only base for production stage |
| Expose PostgreSQL port to host | Security risk, evaluator might have local PG conflict | Keep DB on internal Docker network only |
| Use `latest` tags for base images | Non-reproducible builds | Pin exact versions (e.g., `eclipse-temurin:21.0.2-jre-jammy`) |
| Skip health checks | No startup ordering guarantee, services crash silently | Add health checks with proper intervals and retries |

## Rollback Guidance

```bash
git checkout -- Dockerfile.backend Dockerfile.frontend docker-compose.yml nginx.conf
```

Remove the smoke test if created:

```bash
rm -f test/smoke-test.sh
```

## Compact Rules

### PROJECT-TEST
- All tests must pass before any commit
- TDD (Red/Green/Refactor) is the default
- Breaking an existing test is a blocking issue
- Tests map directly to acceptance criteria
- Test evidence is required for DOD

### PROJECT-ANTI-DRIFT
- Scope is defined by the handoff --- work outside boundaries is a violation
- Version pinning: exact versions only
- Dead code MUST be removed

### PROJECT-PIPELINE
- Pipeline: install -> build -> lint -> test:unit -> test:integration
- Failing stage STOPS the pipeline

## Status Protocol

```
Status: [IN_PROGRESS | BLOCKED | DONE | FAILED]
Progress: X/Y items
Blocker: (if applicable)
```

## Progress Tracker

### Deliverables
- [ ] Dockerfile.backend finalized (build -> test -> prod stages)
- [ ] Dockerfile.frontend finalized (build -> test -> prod stages)
- [ ] docker-compose.yml finalized (health checks, startup order, internal network)
- [ ] nginx.conf finalized (/api/* proxy, static file serving)
- [ ] test/smoke-test.sh created and executable

### Quality Gates
- [ ] Gate 1: Clean start succeeds
- [ ] Gate 2: All 8 migrations run
- [ ] Gate 3: Backend multi-stage correct
- [ ] Gate 4: Frontend multi-stage correct
- [ ] Gate 5: Nginx proxies /api/* to backend
- [ ] Gate 6: Frontend reachable at :8080
- [ ] Gate 7: Health checks pass
- [ ] Gate 8: Startup order enforced
- [ ] Gate 9: No host dependencies required
- [ ] Gate 10: PostgreSQL not exposed
- [ ] Gate 11: All versions exact
- [ ] Gate 12: Backend tests pass in Docker
- [ ] Gate 13: Frontend tests pass in Docker
- [ ] Gate 14: Smoke test passes
- [ ] Gate 15: Clean rebuild is idempotent
