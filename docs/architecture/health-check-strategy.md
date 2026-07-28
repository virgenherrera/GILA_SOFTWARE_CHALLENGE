> [INDEX](../INDEX.md) / [Architecture](./) / Health Check Strategy

# Health Check Strategy

## Problem: Zombie Service Anti-Pattern

A zombie service is a process that responds to health probes ("I'm alive") while
being unable to serve real requests because a critical dependency (the database)
is unreachable. The orchestrator sees a healthy container; every actual request
fails. The user experiences a broken application with no automatic recovery.

## Decision: 503 + Stay Alive + HikariCP Reconnection

The backend process does NOT crash when the database becomes unreachable. Instead:

1. The `/api/health` endpoint returns **503 Service Unavailable** with a body
   that identifies the failing dependency
2. The process stays alive. HikariCP evicts dead connections and re-establishes
   them automatically when the database returns; no application-level retry loop
   is implemented. `initializationFailTimeout` is set to `-1` (lazy init --- pool
   creation never blocks startup). The `/api/health` SELECT 1 probe (2s timeout)
   is the readiness signal. Pool sizing uses HikariCP defaults (max 10); no env
   vars.
3. Docker Compose's `healthcheck` detects the 503 and marks the container
   **unhealthy**
4. `restart: unless-stopped` acts as a safety net for truly unrecoverable crashes
   (segfault, OOM), not for transient dependency failures

### Why NOT crash on DB failure

| Approach | Problem |
|----------|---------|
| Crash on every DB hiccup | Causes restart loops when DB is temporarily down. Restarting the backend cannot fix a down database. Compounds the outage. |
| Report healthy regardless | Zombie service. Traffic keeps arriving at a process that will 500 every request. |
| **Report 503 + stay alive** | Accurately signals degraded state. Process is ready to recover the moment DB returns. No restart storm. |

### Industry Precedent

| Framework | Implementation |
|-----------|---------------|
| Spring Boot Actuator | DB checks in `readiness` group, excluded from `liveness`. Returns 503 when DB is down, process stays alive. |
| Go (heptiolabs/healthcheck) | Separate `/live` (process OK) and `/ready` (dependencies OK). 503 on failed dependency. |
| Kubernetes | Liveness probes NEVER check dependencies. Readiness probes check dependencies and remove from load balancer on failure. |

### Docker Compose Specifics

Docker Compose does NOT auto-restart a container just because `healthcheck`
reports unhealthy. Restart policies (`on-failure`, `unless-stopped`) trigger
only on process **exit**, not on unhealthy status. This means:

- Crashing to "force a restart" is the only way Compose restarts, but it
  creates crash-loops when the DB stays down
- The healthcheck status is informational in Compose (no load balancer to
  drain traffic from)
- `depends_on: condition: service_healthy` prevents startup ordering issues
  but does not help at runtime

### Startup vs Runtime

| Scenario | Behavior |
|----------|----------|
| **Startup**: DB not ready yet | HikariCP pool is created lazily (`initializationFailTimeout=-1`), so startup never blocks on the DB. `depends_on: service_healthy` should prevent this, but defense-in-depth. |
| **Runtime**: DB goes down | `/api/health` returns 503. Process stays alive. HikariCP evicts dead connections and re-establishes them automatically. When DB returns, health flips back to 200. |
| **Unrecoverable**: Bad config, missing env vars | Process exits with non-zero code. `restart: unless-stopped` recycles the container. Logs the specific failure. |

## Health Endpoint Contract

> **Note**: US-001's AC-001.1 uses simplified `{"status": "ok"}` for initial scaffolding.
> The full contract documented here supersedes that simplified version once
> health-check infrastructure is complete.

### `GET /api/health`

**200 OK** --- all dependencies healthy:

```json
{
  "status": "healthy",
  "uptime_seconds": 12345,
  "db": {
    "status": "connected",
    "latency_ms": 2
  }
}
```

**503 Service Unavailable** --- one or more dependencies degraded:

```json
{
  "status": "degraded",
  "uptime_seconds": 12345,
  "db": {
    "status": "disconnected",
    "error": "Connection refused"
  }
}
```

### DB Check Implementation

The health check executes `SELECT 1` against the connection pool with a
**2-second timeout**. If the query succeeds within the timeout, `db.status`
is `"connected"` and `latency_ms` reports the round-trip time. If it fails
or times out, `db.status` is `"disconnected"` with the error message.

The health check does NOT:
- Create a new connection (uses the pool)
- Run expensive queries (just `SELECT 1`)
- Cache results (always live check)
- Leak internal details in the error (no hostnames, ports, or credentials)

### Docker Compose healthcheck

```yaml
backend:
  healthcheck:
    test: ["CMD-SHELL", "curl -sf http://localhost:3000/api/health || exit 1"]
    interval: 10s
    timeout: 5s
    retries: 3
    start_period: 30s
```

The `start_period` gives the backend 30 seconds to establish the initial DB
connection before healthcheck failures count.

The `db` service declares its own healthcheck so `depends_on: condition:
service_healthy` on the backend has a real signal to wait on:

```yaml
db:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U app -d ecommerce"]
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 10s
```

The `start_period` covers the initdb window where a temporary server runs
migrations.

## Shutdown Behavior

On SIGTERM (sent by `docker compose down`):

1. `core.clj` shutdown hook calls `(.stop server)` --- Jetty stops accepting new
   connections and drains in-flight requests (StopTimeout: 10s)
2. Then `(.close datasource)` --- HikariCP closes all pooled connections
3. Then `(flush)` --- ensures buffered logs are written
4. Process exits 0

`docker-compose.yml` sets `stop_grace_period: 15s` on the backend service
(> Jetty's 10s StopTimeout) to avoid SIGKILL during drain.

For in-progress CSV imports: on startup, a recovery step marks any
`csv_import_jobs` in non-terminal state (`Pending`/`Processing`) as `Failed`
with error reason 'interrupted by server restart'. Re-uploading is the recovery
path.

## Related Documents

- [Tech Stack](tech-stack.md) --- runtime and library versions
- [API Contract](api-contract.md) --- endpoint definitions
- [Data Model](data-model.md) --- database schema
