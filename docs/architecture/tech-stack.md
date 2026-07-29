> [INDEX](../INDEX.md) / [Architecture](./) / Tech Stack

# Tech Stack

Technology choices, exact versions, rationale, and alternatives considered for the
Gila Software e-commerce challenge. Every runtime and build tool lives exclusively
inside Docker containers --- nothing is installed on the developer's host machine.

## 1. Stack Overview

| Layer | Technology | Version | Purpose |
| ----- | ---------- | ------- | ------- |
| Backend language | Clojure | 1.12.0 | Business logic, API, CSV pipeline |
| Backend runtime | JDK (Eclipse Temurin) | 21 LTS | JVM runtime for Clojure |
| Web server | Ring + Reitit | Ring 1.12.2, Reitit 0.7.2 | HTTP handling, data-driven routing |
| Validation (BE) | Malli | 0.16.4 | Data-driven schema validation |
| Database access | next.jdbc + HoneySQL | next.jdbc 1.3.955, HoneySQL 2.6.1243 | SQL-first data access, no ORM |
| Async processing | core.async | 1.7.701 | Background job queue for CSV import |
| Build tool | tools.build | 0.9.2 | Clojure compilation, uberjar packaging |
| Frontend framework | Angular | 22.0.0 | User interface (zoneless, strict, signals) |
| Frontend runtime | Node.js | 24 | Build tooling (in container only) |
| Bundler | Angular CLI (esbuild) | 22.0.0 | Frontend build and dev server |
| Validation (FE) | Zod | 3.24.4 | Client-side schema validation |
| Database | PostgreSQL | 17.5 | Relational data store |
| Reverse proxy | nginx | 1.27-alpine | Serve frontend static assets |
| Containerization | Docker + Compose | Compose v2 | Orchestration, zero-local-install |

## 2. Backend

### Language and Runtime

**Clojure 1.12.0** on **JDK 21 LTS** (Eclipse Temurin distribution).

Clojure is chosen from the allowed set (Java, Clojure, Python, PHP, Go) because:

- **Data-oriented**: Clojure's immutable data structures and sequence abstractions are a
  natural fit for a CSV import pipeline that transforms rows through validation stages.
- **JVM ecosystem**: access to mature JDBC drivers, connection pooling (HikariCP), and
  production-grade HTTP servers (Jetty) without reinventing infrastructure.
- **REPL-driven development**: fast feedback loops offset the tight 3-day deadline.
- **Concurrency primitives**: `core.async` channels provide structured background
  processing without external job queue dependencies.

The JDK is installed only inside the Docker build container. The developer's host machine
has no JVM, no Clojure CLI, no Leiningen.

### Libraries

| Library | Version | Purpose |
| ------- | ------- | ------- |
| `org.clojure/clojure` | 1.12.0 | Language runtime |
| `ring/ring-core` | 1.12.2 | HTTP request/response abstraction |
| `ring/ring-jetty-adapter` | 1.12.2 | Embedded Jetty HTTP server |
| `metosin/reitit` | 0.7.2 | Data-driven routing, middleware, coercion |
| `metosin/malli` | 0.16.4 | Data-driven schemas, validation, transformation |
| `com.github.seancorfield/next.jdbc` | 1.3.955 | JDBC wrapper, connection pooling |
| `com.github.seancorfield/honeysql` | 2.6.1235 | SQL generation from Clojure data structures |
| `org.postgresql/postgresql` | 42.7.5 | PostgreSQL JDBC driver |
| `com.zaxxer/HikariCP` | 6.2.1 | Connection pool |
| `org.clojure/core.async` | 1.7.701 | Async channels for background processing |
| `org.clojure/data.csv` | 1.1.0 | CSV parsing |
| `org.clojure/data.json` | 2.5.1 | JSON serialization |
| `org.clojure/tools.logging` | 1.3.0 | Logging facade |
| `ch.qos.logback/logback-classic` | 1.5.16 | Logging implementation |
| `metosin/muuntaja` | 0.6.11 | Content negotiation (JSON-only, see [Middleware Pipeline](middleware-pipeline.md)) |
| `buddy/buddy-sign` | 3.4.0 | HMAC-SHA256 cookie signing for cart identity (see [Security Guidelines](security-guidelines.md)) |
| `metosin/ring-swagger-ui` | 5.9.0 | Swagger UI 5.x static assets for API docs (see [API Docs Strategy](api-docs-strategy.md)) |

> **Logging configuration** (`logback-classic`): configuration via
> `resources/logback.xml`. Root log level is configurable via `LOG_LEVEL` env
> var (default: INFO). No file appenders --- all output goes to stdout for
> Docker log capture.

Test/dev dependencies (`:test` alias in `deps.edn`):

| Library | Version | Purpose |
| ------- | ------- | ------- |
| `lambdaisland/kaocha` | 1.91.1392 | Test runner with metadata-based filtering |
| `lambdaisland/kaocha-cloverage` | 1.1.89 | Code coverage integration for Kaocha |
| `clj-test-containers/clj-test-containers` | 0.7.4 | Testcontainers for ephemeral PostgreSQL in integration tests |

All versions are pinned exactly in `deps.edn` --- no floating ranges, no `RELEASE` or
`LATEST` markers.

### Alternatives Considered

| Alternative | Why Not |
| ----------- | ------- |
| **Java (Spring Boot)** | Viable but verbose for a 3-day prototype. Spring's annotation-heavy model increases boilerplate without proportional benefit at this scale. |
| **Python (FastAPI)** | Strong for rapid prototyping, but weaker concurrency model for background CSV processing. No JVM ecosystem access. |
| **Go (Gin/Echo)** | Excellent for concurrent services, but less expressive for data transformation pipelines. No REPL-driven development. |
| **PHP (Laravel)** | Mature CRUD framework, but async background processing requires external queue (Redis/RabbitMQ), adding infrastructure complexity. |

## 3. Frontend

### Framework and Runtime

**Angular 22.0.0** (zoneless, strict mode, signals, resources) on **Node.js 24**.

Angular is chosen over ClojureScript and other JavaScript frameworks because:

- **Zoneless + Signals**: Angular 22 with `provideZonelessChangeDetection()` eliminates
  Zone.js entirely. Signals provide fine-grained reactivity without manual subscription
  management. `resource()` encapsulates async loading/error/value states for API calls.
- **Strict mode**: `strictTemplates`, `strictInjectionParameters`, and
  `strictPropertyInitialization` catch errors at compile time, reducing runtime surprises.
- **Zod integration**: Zod schemas plug directly into Angular reactive forms for
  client-side validation that mirrors the backend's Malli schemas. No wrapper library
  needed --- Zod's `.parse()` and `.safeParse()` work naturally with Angular's form
  control validators.
- **Built-in routing, forms, HTTP**: Angular's standard library covers routing,
  reactive forms, and HTTP client out of the box --- no separate libraries to evaluate,
  version-pin, and maintain.
- **Developer expertise**: the candidate has deep Angular experience, which under a
  3-day deadline is the strongest argument for any framework choice.

Node.js is installed only inside the Docker build container. The developer's host machine
has no Node.js, no pnpm.

### Libraries

| Library | Version | Purpose |
| ------- | ------- | ------- |
| `@angular/core` | 22.0.0 | Framework core (signals, resource, DI) |
| `@angular/router` | 22.0.0 | Client-side routing |
| `@angular/forms` | 22.0.0 | Reactive forms with validation |
| `@angular/common` | 22.0.0 | Common directives, pipes, HTTP client |
| `@angular/platform-browser` | 22.0.0 | Browser platform bootstrap |
| `zod` | 3.24.4 | Schema validation (shared contract with backend) |

Dev dependencies:

| Library | Version | Purpose |
| ------- | ------- | ------- |
| `@angular/cli` | 22.0.0 | Build tool, dev server, scaffolding |
| `@angular/build` | 22.0.0 | esbuild-based build system |
| `vitest` | 3.2.1 | Unit and component testing |
| `@analogjs/vitest-angular` | 1.18.0 | Angular + Vitest integration |
| `eslint` | 9.27.0 | Linting |
| `angular-eslint` | 19.6.0 | Angular-specific lint rules |

All versions are pinned exactly in `package.json` --- no `^`, no `~`, no `*`.

### Angular Architecture Conventions

- **Zoneless**: `provideZonelessChangeDetection()` in app config. No Zone.js import.
- **Signals everywhere**: component state uses `signal()`, computed state uses
  `computed()`, async data uses `resource()`. No `subscribe()` calls in components.
- **Standalone components**: all components are standalone (no NgModules).
- **External templates**: always `templateUrl`, never inline `template`.
- **External styles**: always `styleUrls`, never inline `styles`.
- **Container-Presentational pattern**: smart containers inject services and hold state;
  presentational components receive data via `input()` and emit via `output()`.

### Alternatives Considered

| Alternative | Why Not |
| ----------- | ------- |
| **React 19 + Vite** | Viable, but requires assembling routing, state management, and form handling from separate libraries. Angular provides these built-in, reducing evaluation and wiring time under a 3-day deadline. |
| **ClojureScript (Reagent/Re-frame)** | Valid per constraints, but adds a second Clojure build pipeline and limits access to the broader JavaScript ecosystem. |
| **Vanilla JavaScript** | No framework overhead, but manual DOM management and state handling would slow UI development for the required CRUD + search + cart + import workflows. |

## 4. Database

### Engine and Version

**PostgreSQL 17.5**, running as a Docker container alongside the application.

### Rationale

- **Structured domain model**: Products, Orders, Carts, and ImportErrors have well-defined
  fields and relationships (see [Domain Glossary](../domain-glossary.md)). A relational
  database with foreign keys enforces these relationships at the storage layer.
- **Decimal precision**: PostgreSQL's `NUMERIC` type handles monetary values without
  floating-point drift, satisfying the domain constraint that prices are exact decimals
  with two fractional digits.
- **Full-text search**: PostgreSQL's built-in `tsvector`/`tsquery` provides product search
  (EP03) without introducing a separate search engine. At the expected catalog size
  (hundreds to low thousands of products) this is more than sufficient.
- **Parameterized queries**: HoneySQL generates parameterized SQL structurally, preventing
  SQL injection at the query-construction level rather than relying on manual escaping.
- **ACID transactions**: row-level import atomicity (each CSV row is fully persisted or not
  at all) and stock decrement during checkout both require transactional guarantees.

### Alternatives Considered

| Alternative | Why Not |
| ----------- | ------- |
| **SQLite** | Simpler deployment (single file), but no concurrent write support limits background CSV import while the API serves requests. |
| **MongoDB** | Flexible schema, but the domain model is well-defined and relational. Schemaless storage trades away structural guarantees that are central to the challenge's evaluation criteria. |
| **MySQL/MariaDB** | Functionally equivalent for this use case, but PostgreSQL's `tsvector` full-text search is more capable, and its `NUMERIC` handling is more precise by default. |

## 5. Build Pipeline & Quality Gates

Four sequential stages. Each stage is a gate --- a failure at any point aborts the
pipeline. All stages run inside Docker containers. Stage 4 (Audit) is **NON-WAIVABLE**
per AGENTS.md AXIOM-ECHO --- every project must define `{audit_command}`.

```mermaid
flowchart LR
    BUILD["Stage 1: BUILD"] --> TEST["Stage 2: TEST"] --> E2E["Stage 3: E2E"] --> AUDIT["Stage 4: AUDIT"]

    style BUILD fill:#3b82f6,color:#fff
    style TEST fill:#f59e0b,color:#fff
    style E2E fill:#22c55e,color:#fff
    style AUDIT fill:#ef4444,color:#fff
```

### Stage 1: BUILD (artifacts)

Compile and produce all deliverable artifacts. Nothing runs, nothing is tested --- just
build. If it does not compile, everything stops here.

| Artifact | Backend Command | Frontend Command |
| -------- | --------------- | ---------------- |
| Application | `clojure -T:build uber` | `pnpm exec ng build --configuration=production` |
| API docs | (generated from API contract at build time) | --- |
| Dependencies | `clojure -P` (resolve + cache) | `pnpm install --frozen-lockfile` (exact lockfile) |

**Gate**: all artifacts produced without errors. The backend uberjar exists, the frontend
`dist/` directory contains the compiled application, dependencies resolved cleanly.

### Stage 2: TEST (static + dynamic)

Two sub-stages, executed in order. Static analysis runs first (cheapest, fastest
feedback). Dynamic tests run second.

#### 2a. Static Analysis

| Check | Backend Command | Frontend Command |
| ----- | --------------- | ---------------- |
| Lint | `clojure -M:lint` (clj-kondo `2024.08.01`) | `pnpm exec ng lint` (eslint `9.27.0` + angular-eslint `19.6.0`) |
| Format | `clojure -M:fmt --check` (cljfmt `0.13.0`) | `pnpm exec prettier --check .` (prettier `3.4.2`) |

**Gate**: zero lint errors, zero format violations. Warnings are allowed but tracked.

| Dev Tool | Version | Ecosystem | Purpose |
| -------- | ------- | --------- | ------- |
| `clj-kondo/clj-kondo` | 2024.08.01 | Clojure (`:lint` alias in `deps.edn`) | Static analysis, linting |
| `weavejester/cljfmt` | 0.13.0 | Clojure (`:fmt` alias in `deps.edn`) | Format checking/enforcement |
| `prettier` | 3.4.2 | Node.js (`devDependencies` in `package.json`) | Format checking/enforcement |

All three are pinned exactly --- same version-policy guarantee as the runtime and
library dependency tables above. Compliance is verified by Stage 4 (Audit, Section 5).

#### 2b. Dynamic Tests (unit + integration)

All tests live in a single suite and are distinguished by **naming convention**, not by
separate suite configurations. This enables flexible filtering:

| Convention | Backend (Clojure) | Frontend (Angular/Vitest) |
| ---------- | ----------------- | ------------------------- |
| Unit test | Namespace suffix: `*-test` (e.g., `product.validation-test`) | File suffix: `*.spec.ts` (e.g., `product-form.spec.ts`) |
| Integration test | Namespace suffix: `*-integration-test` (e.g., `product.repository-integration-test`) | File suffix: `*.integration.spec.ts` (e.g., `product-api.integration.spec.ts`) |

**Commands**:

```bash
# Run ALL tests (unit + integration)
clojure -M:test                          # backend
pnpm exec vitest run                     # frontend

# Filter: unit only (skip namespaces tagged ^:integration)
clojure -M:test --skip-meta :integration                           # backend
pnpm exec vitest run --exclude '**/*.integration.spec.ts'          # frontend

# Filter: integration only (focus on namespaces tagged ^:integration)
clojure -M:test --focus-meta :integration                          # backend
pnpm exec vitest run '**/*.integration.spec.ts'                    # frontend
```

> **Note**: backend filtering requires `^:integration` metadata on integration test namespace
> forms. The naming convention (`*-integration-test`) is for human readability; the metadata
> tag is what Kaocha filters on.

**Gate**: all tests pass. Zero failures, zero skipped (skipped tests are treated as
incomplete work, not as acceptable state).

### Stage 3: E2E (Playwright)

A single Playwright suite exercises the full application stack end-to-end. This stage
requires the complete Docker Compose environment running (backend + frontend + database).

```bash
pnpm exec playwright test
```

**Gate**: all E2E tests pass against the fully composed application.

### Stage 4: AUDIT (dependency security + version policy)

Verifies two independent concerns: known-vulnerability scanning of every dependency, and
compliance with the version policy (Section 2/3: exact versions only, no floating
ranges, LTS runtimes). This stage is **NON-WAIVABLE** --- it runs even when Stage 3 (E2E)
is skipped or unavailable.

| Check | Backend Command | Frontend Command |
| ----- | --------------- | ----------------- |
| Outdated/vulnerable deps | `clojure -M:outdated` (antq) | `pnpm audit --audit-level=moderate` |
| Available upgrades | `clojure -M:outdated` (antq, same run) | `pnpm outdated` |
| Version policy check | `deps.edn` has no `RELEASE`/`LATEST`/range markers (grep-verified) | `package.json` has no `^`, `~`, `*` markers (grep-verified) |

**Composite command** (`{audit_command}`): runs both ecosystems' audits sequentially and
fails on the first non-zero exit.

```bash
docker compose run --rm backend clojure -M:outdated && \
  docker compose run --rm frontend pnpm audit --audit-level=moderate && \
  docker compose run --rm frontend pnpm outdated --long
```

**Gate**: zero known vulnerabilities at `moderate` severity or above, and zero version
policy violations (no floating/range version specifiers in `deps.edn` or
`package.json`). `antq` and `pnpm outdated` reporting available-but-not-yet-adopted
upgrades does NOT fail the gate --- only unresolved vulnerabilities and policy
violations do.

### Pipeline Summary

| Stage | What | Gate Condition | Type |
| ----- | ---- | -------------- | ---- |
| 1. BUILD | Compile app, API docs, artifacts | All artifacts produced | EXE |
| 2a. TEST static | Lint + format | Zero errors | EXE |
| 2b. TEST dynamic | Unit + integration (by name) | All pass, zero skipped | EXE |
| 3. E2E | Playwright full-stack | All pass | EXE |
| 4. AUDIT | Dependency vulnerability scan + version policy | Zero vulnerabilities, zero policy violations | EXE |

Every gate is `EXE` (deterministic, automatable, copy-pasteable shell command). No
subjective `MAN` gates exist in the build pipeline. Stage 4 (Audit) is NON-WAIVABLE
per AGENTS.md AXIOM-ECHO --- unlike Stage 3 (E2E), it cannot be omitted with
justification.

### Pre-Commit Hook (Structural Enforcement of AXIOM-ECHO)

Per AGENTS.md, the pre-commit hook MUST run the Echo System and exit non-zero on
failure --- this is structural enforcement, not a suggestion the developer can skip.

**Tool choice**: a plain POSIX shell script, not Husky. The zero-local-install
guarantee (Section 6) means the host has no Node.js and no `npm`/`pnpm` outside
Docker, so a Husky install step (`npx husky init`) would violate that guarantee
before a single container exists. A shell script committed to the repo and wired
into `.git/hooks/pre-commit` has no host dependency beyond `git` and `docker`,
which are already required to run the project at all.

`scripts/pre-commit.sh`:

```bash
#!/usr/bin/env sh
# Structural enforcement of AXIOM-ECHO: runs the Echo System (Stages 1-4)
# before allowing a commit. Exits non-zero on the first failing stage.
set -e

echo "Echo System: Stage 1 (BUILD)"
docker compose run --rm backend clojure -T:build uber
docker compose run --rm frontend pnpm exec ng build --configuration=production

echo "Echo System: Stage 2a (STATIC ANALYSIS)"
docker compose run --rm backend clojure -M:lint
docker compose run --rm backend clojure -M:fmt --check
docker compose run --rm frontend pnpm exec ng lint
docker compose run --rm frontend pnpm exec prettier --check .

echo "Echo System: Stage 2b (DYNAMIC TESTS)"
docker compose run --rm backend clojure -M:test
docker compose run --rm frontend pnpm exec vitest run

echo "Echo System: Stage 4 (AUDIT)"
docker compose run --rm backend clojure -M:outdated
docker compose run --rm frontend pnpm audit --audit-level=moderate

echo "Echo System: all gates green --- commit allowed"
```

**Installation** (one-time, per clone --- documented in `README.md`):

```bash
ln -sf ../../scripts/pre-commit.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

**Rules**:

- `set -e` ensures the script exits non-zero on the first failing command --- `git commit`
  is aborted and no gate can be silently skipped.
- Stage 3 (E2E) is intentionally excluded from the pre-commit hook: it requires the full
  Compose stack running (`docker compose up`), which is too slow for a per-commit gate.
  Stage 3 still runs in the full pipeline (Section 9) before merge/delivery.
- `scripts/pre-commit.sh` is the single source of truth for the hook body; `.git/hooks/`
  is never committed (git does not track it), so the symlink step above is required
  once per clone.

## 6. Docker Architecture

### Zero-Local-Install Guarantee

The developer's host machine requires only Docker and Docker Compose. No JDK, no
Clojure CLI, no Node.js, no pnpm, no PostgreSQL client. Every tool runs inside a
container.

### Multi-Stage Dockerfiles

#### Backend (`Dockerfile.backend`)

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build
# Install Clojure CLI, resolve deps, compile, run tests, build uberjar

# Stage 2: Test
FROM build AS test
# Run lint + unit tests + integration tests
# If any test fails, the build stops here

# Stage 3: Production
FROM eclipse-temurin:21-jre-jammy AS production
# Copy only the uberjar from build stage
# Minimal JRE image, no build tools, no source code
# curl is installed explicitly for the Compose healthcheck (see below); the base
# jre-jammy image does not ship it
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Run as non-root user
RUN useradd -r app
USER app
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Key design decisions:
- **JDK in build, JRE in production**: the production image contains no compiler, no
  Clojure CLI, no source code --- only the uberjar and the minimal JRE.
- **Non-root production user**: the production stage runs as a non-root user
  (`RUN useradd -r app && USER app`), limiting blast radius if the process is
  compromised.
- **Test stage as gate**: tests run inside the Docker build. A failing test prevents the
  production image from being created.
- **Layer caching**: `deps.edn` is copied and resolved before source code, so dependency
  resolution is cached across builds when only source changes.
- **curl installed explicitly**: `eclipse-temurin:21-jre-jammy` does not include `curl`
  by default; it is installed as a single minimal `apt-get` layer solely to satisfy the
  Docker Compose `healthcheck` (see [Health Check Strategy](health-check-strategy.md)),
  which runs `curl -sf http://localhost:3000/api/health` inside the `backend` container.

#### Frontend (`Dockerfile.frontend`)

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS build
# pnpm install --frozen-lockfile (exact versions), ng lint, vitest, ng build --configuration=production

# Stage 2: Test
FROM build AS test
# Run lint + unit tests
# If any test fails, the build stops here

# Stage 3: Production
FROM nginx:1.27-alpine AS production
# Copy only dist/frontend/browser/ from build stage
# No Node.js, no pnpm, no source code in production
EXPOSE 80
```

Key design decisions:
- **nginx serves static assets**: the production frontend image is a pure static file
  server. Angular compiles to static HTML/JS/CSS --- no Node.js runtime in production.
- **Alpine base**: minimal image size for both build and production stages.
- **esbuild**: Angular 22 uses esbuild by default, producing optimized bundles with
  tree-shaking and dead code elimination.

### Docker Compose Topology

The Compose file declares four services. Three are the **production stack**; the fourth
(`playwright`) is a **test-only** service used exclusively in Stage 3 (E2E) and is never
part of the running application:

```yaml
services:
  db:          # PostgreSQL 17.5                                    (production)
  backend:     # Clojure uberjar on JRE 21                          (production)
  frontend:    # nginx serving static Angular build                 (production)
  playwright:  # E2E test runner (mcr.microsoft.com/playwright)      (test only, Stage 3)
```

```mermaid
flowchart LR
    USER([Browser]) --> FE[frontend\nnginx:80]
    FE -->|/api/*\nproxy_pass| BE[backend\njetty:3000]
    BE --> DB[(db\nPostgreSQL:5432)]
```

- **Single command**: `docker compose up --build` starts the 3 production services
  (`db`, `backend`, `frontend`). `playwright` is not started by this command --- it is
  invoked separately for Stage 3 E2E (see [Stage 3: E2E Commands](#stage-3-e2e-commands)).
- **Health checks**: each service declares a health check so Compose can enforce startup
  order (`backend` waits for `db` healthy; `frontend` waits for `backend` healthy).
- **Networking**: all services share a Docker bridge network. The frontend's nginx proxies
  `/api/*` requests to the backend, avoiding CORS configuration.
- **Database volume**: optional named volume for PostgreSQL data persistence. Omitted by
  default for a clean evaluation experience (each `docker compose up` starts fresh).
- **Docker socket mount (test command only)**: the docker.sock mount is passed via the
  test command (`docker compose run -v /var/run/docker.sock:/var/run/docker.sock backend
  clojure -M:test`), NOT in the `backend` service definition. It exists solely so
  Testcontainers can launch ephemeral PostgreSQL instances for integration tests. The
  production backend container MUST NOT have access to the Docker daemon.
- **Playwright container**: `mcr.microsoft.com/playwright:v1.62.0-noble` with `ipc: host`
  (Chromium requires shared memory). Connects to frontend via Compose DNS (`http://frontend:80`).
  `depends_on: frontend: condition: service_healthy` ensures the frontend is ready.

### Configuration & Environment Variables

| Variable | Service | Required | Default | Validation |
|---|---|---|---|---|
| `POSTGRES_USER` | db | yes | `app` | non-empty |
| `POSTGRES_PASSWORD` | db, backend | yes | none | non-empty |
| `POSTGRES_DB` | db | yes | `ecommerce` | non-empty |
| `DB_HOST` | backend | yes | `db` | non-empty |
| `DB_PORT` | backend | no | `5432` | integer 1–65535 |
| `DB_NAME` | backend | yes | `ecommerce` | non-empty |
| `DB_USER` | backend | yes | `app` | non-empty |
| `DB_PASSWORD` | backend | yes | none | non-empty |
| `CART_COOKIE_SECRET` | backend | yes | none (dev default in compose) | non-blank, ≥32 chars |
| `PORT` | backend | no | `3000` | integer 1–65535 |
| `LOG_LEVEL` | backend | no | `INFO` | one of: TRACE, DEBUG, INFO, WARN, ERROR |

All required variables with no default MUST be validated at startup by
`src/ecommerce/config.clj`. The service MUST exit with code 1 and a message naming the
missing/invalid variable if validation fails. This is the single source of truth for
configuration --- all other docs reference this table.

The `.env → docker-compose (env_file) → container environment` flow: a `.env` file at
project root is loaded by Docker Compose automatically. `.env` is gitignored;
`.env.example` is committed with safe development defaults and comments marking
required-no-default vars.

> **Note**: `PORT` is coupled to nginx.conf `proxy_pass`, the Dockerfile `EXPOSE`, and
> the compose healthcheck. Changing it requires updating all three. For this project,
> 3000 is the canonical port.

## 7. CSV Import Pipeline

### Design: Background Job Queue within the Same Service

The CSV import runs as a background job inside the Clojure backend process, using
`core.async` channels to decouple the HTTP request from row-by-row processing.

```mermaid
sequenceDiagram
    participant UI as Browser
    participant API as API Handler
    participant CH as core.async Channel
    participant W as Worker (go-loop)
    participant DB as PostgreSQL

    UI->>API: POST /api/imports (multipart CSV)
    API->>DB: Create CsvImportJob (Pending)
    API->>CH: Put job-id + parsed rows onto channel
    API-->>UI: 202 Accepted {job_id}

    UI->>API: GET /api/imports/{id}/progress (SSE)
    API-->>UI: SSE stream opened

    loop Each row
        CH->>W: Take row from channel
        W->>W: Validate (Malli schemas)
        W->>W: Sanitize (XSS/SQLi screening)
        W->>DB: INSERT or UPDATE Product
        W->>DB: UPDATE CsvImportJob progress
        W-->>UI: SSE event {row, status, errors}
    end

    W->>DB: Finalize CsvImportJob (Completed | CompletedWithErrors)
    W-->>UI: SSE event {complete, summary}
```

**Flow**:

1. The client uploads the CSV via `POST /api/imports`. The API handler parses the file,
   creates a `CsvImportJob` record in `Pending` state, and puts the parsed rows onto a
   `core.async` channel. It returns `202 Accepted` with the job ID immediately.
2. The client opens an SSE connection to `GET /api/imports/{id}/progress`.
3. A `go-loop` worker takes rows from the channel one at a time, validates each row
   against Malli schemas, screens for security payloads, and either persists the product
   or records an `ImportError`. After each row, progress is pushed to the SSE stream.
4. When all rows are processed, the job transitions to `Completed` or
   `CompletedWithErrors` and the SSE stream sends a final summary event.

**Why this design**:

- **No external dependencies**: no Redis, no RabbitMQ, no separate worker process. The
  entire pipeline runs inside the same JVM process using Clojure's built-in concurrency.
- **Responsive UI**: SSE delivers real-time progress without polling. The browser knows
  exactly which row is being processed and can display errors as they occur.
- **Adequate for scale**: the sample CSV has approximately 100 rows. Even a file with
  10,000 rows would process in seconds on a single `go-loop`. The overhead of distributed
  infrastructure is not justified at this volume.

### Alternative Considered: Distributed Step Functions

A distributed pipeline (e.g., AWS Step Functions + Lambda, or a separate worker service
with a message queue) was explicitly considered and deferred.

| Aspect | Current Design | Distributed Alternative |
| ------ | -------------- | ---------------------- |
| Infrastructure | Zero additional services | Message queue + worker + orchestrator |
| Latency | Sub-second per row | Network hop per step |
| Failure isolation | Per-row within same process | Per-step across services |
| Observability | SSE + job record | Distributed tracing required |
| Scale ceiling | ~10K rows/minute (single JVM) | Horizontally scalable |
| Operational cost | Zero (included in backend) | Queue hosting + worker instances |

**Rationale for deferral**: current volume (~100 rows) does not justify the infrastructure
cost. The design supports extraction to independent workers if scale demands it --- the
`core.async` channel is an internal interface that can be replaced with a message queue
consumer without changing the validation or persistence logic.

## 8. Validation Strategy

### The Problem

The user wanted Zod validation on both frontend and backend to ensure consistent rules.
The natural approach would be a shared validation layer (e.g., NestJS as an API gateway
running Zod on the server side). However, Node.js is not in the allowed backend language
list (Java, Clojure, Python, PHP, Go), so a NestJS gateway is not viable.

### The Solution: Parallel Schema Validation

Malli (Clojure, backend) and Zod (JavaScript, frontend) both follow the same philosophy:
**data-driven, composable schemas defined as plain data structures**. They do not share
code, but they enforce the same validation contract.

```
                     Validation Contract
                    (documented in API spec)
                   ┌───────────────────────┐
                   │  name: non-empty       │
                   │  sku: non-empty, unique │
                   │  price: > 0, decimal    │
                   │  stock: >= 0, integer   │
                   │  category: non-empty    │
                   │  weight_kg: >= 0, dec.  │
                   └────────┬───────────────┘
                            │
              ┌─────────────┼─────────────┐
              │                           │
        ┌─────▼─────┐             ┌───────▼───────┐
        │  Zod (FE)  │             │  Malli (BE)   │
        │  JS schema │             │  CLJ schema   │
        │  fast UX   │             │  authoritative│
        └────────────┘             └───────────────┘
```

**How it works**:

1. The **validation contract** is documented in the API specification with exact rules for
   each field (see [Validation Rules](api-contract.md#7-validation-contract)).
2. The **frontend** implements these rules as Zod schemas integrated with Angular reactive
   forms. Zod's `.safeParse()` drives custom validators on form controls, providing
   immediate field-level feedback. Forms disable submit until the schema passes.
3. The **backend** implements the same rules as Malli schemas. The backend is
   **authoritative** --- it rejects any request that fails validation regardless of what
   the frontend accepted. This is the security boundary.
4. A **contract test** verifies that a canonical set of valid and invalid inputs produces
   the same accept/reject decision from both Zod and Malli schemas, catching drift.

**Why not share code**:

- Clojure and JavaScript cannot share runtime validation code without a transpilation
  layer (e.g., compiling Malli to JSON Schema, then generating Zod from JSON Schema).
  This adds build complexity and a fragile code generation step.
- Documenting the contract and testing both sides against it is simpler, more transparent,
  and produces an artifact (the contract document) that the challenge evaluators can read.

### Alternative Considered: NestJS API Gateway

| Aspect | Chosen (Malli + Zod) | NestJS Gateway |
| ------ | -------------------- | -------------- |
| Constraint compliance | Clojure backend (allowed) | Node.js backend (not allowed) |
| Validation consistency | Contract-tested parallel schemas | Single Zod schema on both sides |
| Architecture complexity | One backend service | Two backend services (gateway + Clojure) |
| Latency | Direct API call | Extra network hop through gateway |

**Verdict**: NestJS is blocked by the language constraint. Even if it were allowed, adding
a gateway service for validation alone introduces architectural complexity (two services,
inter-service communication, two Dockerfiles, two health checks) that is not justified
when contract-tested parallel schemas achieve the same validation guarantee.

## 9. Key Commands & Quality Gate Echoes

All commands run inside Docker containers. The pipeline echoes gate results at each
stage boundary so failures are immediately visible.

### Docker Build Pipeline (full run)

```mermaid
flowchart TD
    subgraph "Stage 1: BUILD"
        B1[Backend: resolve deps]
        B2[Backend: compile + uberjar]
        B3[Frontend: pnpm install --frozen-lockfile]
        B4[Frontend: ng build --prod]
        B5[API docs: generate]
        B1 --> B2
        B3 --> B4
    end

    subgraph "Stage 2: TEST"
        subgraph "2a: Static"
            S1[Backend: clj-kondo lint]
            S2[Backend: cljfmt check]
            S3[Frontend: ng lint]
            S4[Frontend: prettier check]
        end
        subgraph "2b: Dynamic"
            D1[Backend: clojure -M:test]
            D2[Frontend: vitest run]
        end
        S1 --> D1
        S2 --> D1
        S3 --> D2
        S4 --> D2
    end

    subgraph "Stage 3: E2E"
        E1[docker compose up]
        E2[Playwright test suite]
        E1 --> E2
    end

    subgraph "Stage 4: AUDIT"
        A1[Backend: clojure -M:outdated / antq]
        A2[Frontend: pnpm audit]
        A3[Frontend: pnpm outdated]
        A2 --> A3
    end

    B2 --> S1
    B4 --> S3
    D1 --> E1
    D2 --> E1
    E2 --> A1
    E2 --> A2

    style B2 fill:#3b82f6,color:#fff
    style B4 fill:#3b82f6,color:#fff
    style S1 fill:#f59e0b,color:#fff
    style S3 fill:#f59e0b,color:#fff
    style D1 fill:#f59e0b,color:#fff
    style D2 fill:#f59e0b,color:#fff
    style E2 fill:#22c55e,color:#fff
    style A1 fill:#ef4444,color:#fff
    style A2 fill:#ef4444,color:#fff
```

### Gate Echo Protocol

After each stage completes, the pipeline prints a gate echo summarizing the result.
This mirrors the AGENTS.md PDC (Post-Delegation Checkpoint) pattern applied to CI.

```text
═══════════════════════════════════════════════════
GATE: Stage 1 — BUILD
───────────────────────────────────────────────────
  [PASS] Backend uberjar:      target/app.jar (12.3 MB)
  [PASS] Frontend dist:        dist/frontend/browser/ (1.8 MB)
  [PASS] API docs:             docs/api/ generated
───────────────────────────────────────────────────
VERDICT: STAGE 1 CLEAR — proceeding to TEST
═══════════════════════════════════════════════════

═══════════════════════════════════════════════════
GATE: Stage 2a — STATIC ANALYSIS
───────────────────────────────────────────────────
  [PASS] clj-kondo:            0 errors, 0 warnings
  [PASS] cljfmt:               0 format violations
  [PASS] ng lint (eslint):     0 errors, 0 warnings
  [PASS] prettier:             0 format violations
───────────────────────────────────────────────────
VERDICT: STAGE 2a CLEAR — proceeding to dynamic tests
═══════════════════════════════════════════════════

═══════════════════════════════════════════════════
GATE: Stage 2b — DYNAMIC TESTS
───────────────────────────────────────────────────
  [PASS] Backend tests:        42 passed, 0 failed, 0 skipped
  [PASS] Frontend tests:       28 passed, 0 failed, 0 skipped
───────────────────────────────────────────────────
VERDICT: STAGE 2 CLEAR — proceeding to E2E
═══════════════════════════════════════════════════

═══════════════════════════════════════════════════
GATE: Stage 3 — E2E
───────────────────────────────────────────────────
  [PASS] Playwright:           8 passed, 0 failed
───────────────────────────────────────────────────
VERDICT: STAGE 3 CLEAR — proceeding to AUDIT
═══════════════════════════════════════════════════

═══════════════════════════════════════════════════
GATE: Stage 4 — AUDIT
───────────────────────────────────────────────────
  [PASS] clojure -M:outdated:  0 vulnerable, 3 outdated (non-blocking)
  [PASS] pnpm audit:           0 vulnerabilities (moderate+)
  [PASS] pnpm outdated:        1 outdated (non-blocking)
  [PASS] Version policy:       0 floating/range specifiers in deps.edn, package.json
───────────────────────────────────────────────────
VERDICT: ALL GATES CLEAR — pipeline complete
═══════════════════════════════════════════════════
```

Any `[FAIL]` in a gate echo stops the pipeline immediately. The echo format is machine
parseable (prefix `[PASS]` or `[FAIL]`) for CI integration.

### Run the Application

```bash
docker compose up --build
```

The application is available at `http://localhost:8080` after all health checks pass.

### Stage 1: Build Commands

```bash
# Backend: resolve deps + build uberjar
docker compose run --rm backend clojure -P
docker compose run --rm backend clojure -T:build uber

# Frontend: install + build
docker compose run --rm frontend pnpm install --frozen-lockfile
docker compose run --rm frontend pnpm exec ng build --configuration=production
```

### Stage 2a: Static Analysis Commands

```bash
# Backend
docker compose run --rm backend clojure -M:lint
docker compose run --rm backend clojure -M:fmt --check

# Frontend
docker compose run --rm frontend pnpm exec ng lint
docker compose run --rm frontend pnpm exec prettier --check .
```

### Stage 2b: Dynamic Test Commands

```bash
# All tests (unit + integration)
docker compose run --rm backend clojure -M:test
docker compose run --rm frontend pnpm exec vitest run

# Filter: unit only (skip ^:integration namespaces / integration spec files)
docker compose run --rm backend clojure -M:test --skip-meta :integration
docker compose run --rm frontend pnpm exec vitest run --exclude '**/*.integration.spec.ts'

# Filter: integration only (focus on ^:integration namespaces / integration spec files)
docker compose run --rm backend clojure -M:test --focus-meta :integration
docker compose run --rm frontend pnpm exec vitest run '**/*.integration.spec.ts'
```

### Stage 3: E2E Commands

```bash
# Requires full stack running (docker compose up)
docker compose run --rm playwright pnpm exec playwright test
```

### Stage 4: Audit Commands

```bash
# Backend: outdated + vulnerable dependency report (antq)
docker compose run --rm backend clojure -M:outdated

# Frontend: known-vulnerability scan (fails at moderate severity or above)
docker compose run --rm frontend pnpm audit --audit-level=moderate

# Frontend: outdated dependency report (non-blocking, informational)
docker compose run --rm frontend pnpm outdated --long
```

### Database Commands

```bash
docker compose exec db psql -U app -d ecommerce
docker compose down -v && docker compose up --build
```

### Command Reference (for handoff substitution)

| Variable | Command |
| -------- | ------- |
| `{build_command}` | `docker compose up --build` |
| `{build_command_be}` | `docker compose run --rm backend clojure -T:build uber` |
| `{build_command_fe}` | `docker compose run --rm frontend pnpm exec ng build --configuration=production` |
| `{lint_command}` | `docker compose run --rm backend clojure -M:lint` |
| `{lint_command_fe}` | `docker compose run --rm frontend pnpm exec ng lint` |
| `{fmt_command}` | `docker compose run --rm backend clojure -M:fmt --check` |
| `{fmt_command_fe}` | `docker compose run --rm frontend pnpm exec prettier --check .` |
| `{test_command}` | `docker compose run --rm backend clojure -M:test` |
| `{test_command_fe}` | `docker compose run --rm frontend pnpm exec vitest run` |
| `{e2e_command}` | `docker compose run --rm playwright pnpm exec playwright test` |
| `{audit_command}` | `docker compose run --rm backend clojure -M:outdated && docker compose run --rm frontend pnpm audit --audit-level=moderate && docker compose run --rm frontend pnpm outdated --long` |
| `{audit_command_be}` | `docker compose run --rm backend clojure -M:outdated` |
| `{audit_command_fe}` | `docker compose run --rm frontend pnpm audit --audit-level=moderate` |

## 10. Epic Dependency DAG

Epic-and-capability-level dependency mapping (Dependency Mapping gate, Architect phase
per AGENTS.md). This is the coarse-grained DAG; it is refined to story-level at the
Dependency Ordering gate during Refine/Plan, and to task-level in each batch plan.

### Epic-Level Dependencies

```mermaid
flowchart TD
    EP06A["EP06: Infrastructure<br/>(Docker scaffolding, DB schema)"] --> EP01["EP01: Product Management<br/>(Product CRUD + entity)"]

    EP01 --> EP02["EP02: CSV Import"]
    EP01 --> EP03["EP03: Product Search"]
    EP01 --> EP04["EP04: Purchase Workflow"]

    EP01 --> EP05["EP05: User Interface<br/>(Angular frontend)"]
    EP02 --> EP05
    EP03 --> EP05
    EP04 --> EP05
    EP06A --> EP05

    EP05 --> EP06B["EP06: Docker Finalization<br/>+ README"]

    style EP06A fill:#64748b,color:#fff
    style EP06B fill:#64748b,color:#fff
    style EP01 fill:#3b82f6,color:#fff
    style EP02 fill:#f59e0b,color:#fff
    style EP03 fill:#f59e0b,color:#fff
    style EP04 fill:#f59e0b,color:#fff
    style EP05 fill:#22c55e,color:#fff
```

**Reading the graph**:

- **EP06 (Infrastructure)** runs first: Docker Compose skeleton, database schema, and CI
  scaffolding. Nothing else can start until containers and the schema exist.
- **EP01 (Product Management)** is the load-bearing epic: it owns the Product entity and
  its CRUD API. EP02, EP03, EP04, and EP05 all depend on EP01 being in place before their
  own work can begin, because they all read or write Product records.
- **EP02 (CSV Import)**, **EP03 (Product Search)**, and **EP04 (Purchase Workflow)** are
  mutually independent --- they share no code with each other, only with EP01. They can be
  built in parallel once EP01 lands.
- **EP05 (User Interface)** depends on all four backend epics (EP01-EP04): the Angular
  frontend needs the Product CRUD API, the CSV import endpoint, the search endpoint, and
  the cart/checkout endpoints all to exist before their corresponding views can be wired
  up. It also depends on EP06's initial scaffolding (the frontend Docker service and
  nginx proxy configuration).
- **EP06 (Docker Finalization + README)** is a second, later phase of EP06: once EP05
  (the frontend) is complete, the Compose topology is finalized (health checks, service
  dependencies, `.env.example`) and the README is written against the fully working
  system. It is split from EP06's initial scaffolding phase because it can only be
  written accurately after every other epic is functional.

### Capability-Level Shared Dependency: the Product Entity

```mermaid
flowchart LR
    EP01["EP01: Product Management<br/>(owns schema + repository)"] -->|"provides Product<br/>entity + repository"| PE[("Product entity")]
    PE -->|"read/write"| EP02["EP02: CSV Import"]
    PE -->|"read (filtered)"| EP03["EP03: Product Search"]
    PE -->|"read + stock decrement"| EP04["EP04: Purchase Workflow"]

    style EP01 fill:#3b82f6,color:#fff
    style PE fill:#f59e0b,color:#000
```

The Product entity (schema, repository namespace, and Malli/Zod validation contract) is
defined once in EP01 and consumed --- never redefined --- by EP02, EP03, and EP04:

| Epic | Relationship to Product entity |
| ---- | ------------------------------- |
| EP01 | Owns the schema, repository, and CRUD API. Source of truth. |
| EP02 | Writes (insert/update) via the same repository functions EP01 exposes; does not redefine validation rules. |
| EP03 | Reads via `tsvector`/`tsquery` search queries against the same table; no schema changes. |
| EP04 | Reads for cart/order line items and decrements `stock` transactionally; no schema changes. |

This is the shared entity contract required by the Dependency Mapping gate: any change to
the Product schema in EP01 is a breaking change for EP02, EP03, and EP04, and must be
coordinated as such rather than treated as an isolated EP01 change.

## 11. Non-Functional Requirement Targets

Measurable targets, not qualitative claims. These replace prior wording such as "in
seconds" or "sufficient" with concrete, testable thresholds.

| NFR | Target | Applies To |
| --- | ------ | ---------- |
| API response time (CRUD) | < 200ms p95 | Product CRUD endpoints (EP01) |
| API response time (search/CSV) | < 500ms p95 | Search queries (EP03), CSV row processing (EP02) |
| CSV import throughput | 1,000 rows in < 10s | Background import job (EP02) |
| Test coverage | > 80% line coverage | Backend (Kaocha + Cloverage) and frontend (Vitest) |
| Health check response time | < 50ms | `/api/health` endpoint (see [Health Check Strategy](health-check-strategy.md)) |

These targets are verified by the Dynamic Tests stage (Section 5, Stage 2b) and, for
response-time targets, by dedicated performance assertions in the integration test
suite. A target that cannot be met MUST be renegotiated via the ADR process (see
AGENTS.md) before being silently downgraded to a qualitative claim.

## 12. Alternatives Considered

Consolidated decision table covering all major architectural choices.

| Decision | Chosen | Alternative | Why Not |
| -------- | ------ | ----------- | ------- |
| Backend language | Clojure | Java (Spring Boot) | More verbose, slower iteration for 3-day deadline |
| Backend language | Clojure | Python (FastAPI) | Weaker concurrency model for background CSV processing |
| Backend language | Clojure | Go (Gin/Echo) | Less expressive for data transformation pipelines |
| Backend language | Clojure | PHP (Laravel) | Requires external queue for async processing |
| Frontend framework | Angular 22 (zoneless, signals) | React 19 + Vite | Angular provides routing, forms, HTTP built-in; candidate has deep expertise; fewer deps to manage |
| Frontend framework | Angular 22 | ClojureScript (Reagent) | Second build pipeline, limited ecosystem access under deadline |
| Frontend framework | Angular 22 | Vanilla JS | Manual DOM management slows UI development |
| Database | PostgreSQL | SQLite | No concurrent write support for background import |
| Database | PostgreSQL | MongoDB | Schemaless storage trades structural guarantees |
| Database | PostgreSQL | MySQL | PostgreSQL has better full-text search and NUMERIC handling |
| Validation (BE) | Malli | clojure.spec | Malli has better error messages, transformation, and JSON Schema interop |
| Validation (FE) | Zod | Yup | Zod has better TypeScript inference and stricter parsing |
| Validation gateway | Parallel schemas | NestJS API gateway | Node.js not in allowed backend languages; extra service complexity |
| CSV processing | core.async (in-process) | Distributed step functions | ~100 rows does not justify infrastructure cost; extractable later |
| CSV progress | SSE | WebSockets | SSE is simpler for server-to-client unidirectional updates |
| CSV progress | SSE | Polling | SSE provides real-time updates without repeated requests |
| SQL generation | HoneySQL | Raw SQL strings | HoneySQL prevents injection structurally and composes queries as data |
| SQL generation | HoneySQL | ORM (e.g., Korma) | SQL-first approach gives full control; ORM abstractions leak at edges |
| Build tool | tools.build | Leiningen | tools.build is the official Clojure build tool; deps.edn is simpler than project.clj |
| Product search | PostgreSQL tsvector | Elasticsearch | Separate service not justified at catalog scale; PostgreSQL built-in is sufficient |
| Logging | Logback | log4j2 | Logback is the SLF4J reference implementation; simpler configuration |
| Connection pool | HikariCP | c3p0 | HikariCP is faster, more reliable, and the de facto standard |
