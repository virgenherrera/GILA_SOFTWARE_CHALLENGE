> [INDEX](../INDEX.md) / [Architecture](./) / Health Check Strategy

# Health Check Strategy

## Problem: Zombie Service Anti-Pattern

A zombie service is a process that responds to health probes ("I'm alive") while
being unable to serve real requests because a critical dependency (the database)
is unreachable. The orchestrator sees a healthy container; every actual request
fails. The user experiences a broken application with no automatic recovery.

## Decision: 503 + Stay Alive + Retry with Backoff

The backend process does NOT crash when the database becomes unreachable. Instead:

1. The `/api/health` endpoint returns **503 Service Unavailable** with a body
   that identifies the failing dependency
2. The process stays alive and retries the database connection with exponential
   backoff
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
| **Startup**: DB not ready yet | Retry connection with backoff (1s, 2s, 4s, 8s, max 30s). `depends_on: service_healthy` should prevent this, but defense-in-depth. |
| **Runtime**: DB goes down | `/api/health` returns 503. Process stays alive. Background retry attempts reconnection. When DB returns, health flips back to 200. |
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

## Related Documents

- [Tech Stack](tech-stack.md) --- runtime and library versions
- [API Contract](api-contract.md) --- endpoint definitions
- [Data Model](data-model.md) --- database schema
