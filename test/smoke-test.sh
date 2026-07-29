#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAX_RETRIES=30
RETRY_INTERVAL=2

echo "=== Smoke Test ==="
echo "Target: $BASE_URL"

wait_for_service() {
  local url="$1"
  local description="$2"
  local attempt=0

  while [ $attempt -lt $MAX_RETRIES ]; do
    if curl -sf "$url" > /dev/null 2>&1; then
      echo "[PASS] $description"
      return 0
    fi
    attempt=$((attempt + 1))
    sleep $RETRY_INTERVAL
  done

  echo "[FAIL] $description — not reachable after $((MAX_RETRIES * RETRY_INTERVAL))s"
  return 1
}

check_status() {
  local url="$1"
  local expected="$2"
  local description="$3"

  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' "$url")

  if [ "$status" = "$expected" ]; then
    echo "[PASS] $description (HTTP $status)"
  else
    echo "[FAIL] $description — expected HTTP $expected, got HTTP $status"
    return 1
  fi
}

failures=0

echo ""
echo "--- Waiting for services ---"
wait_for_service "$BASE_URL/api/health" "Backend health endpoint reachable via nginx" || failures=$((failures + 1))

echo ""
echo "--- Health check ---"
check_status "$BASE_URL/api/health" "200" "GET /api/health returns 200" || failures=$((failures + 1))

health_body=$(curl -sf "$BASE_URL/api/health" 2>/dev/null || echo '{}')
if echo "$health_body" | grep -q '"healthy"'; then
  echo "[PASS] Health response contains \"healthy\""
else
  echo "[FAIL] Health response missing \"healthy\": $health_body"
  failures=$((failures + 1))
fi

echo ""
echo "--- Frontend ---"
check_status "$BASE_URL" "200" "GET / returns 200 (Angular SPA)" || failures=$((failures + 1))

frontend_body=$(curl -sf "$BASE_URL" 2>/dev/null || echo '')
if echo "$frontend_body" | grep -q '<app-root'; then
  echo "[PASS] Frontend contains <app-root> element"
else
  echo "[FAIL] Frontend missing <app-root> element"
  failures=$((failures + 1))
fi

echo ""
echo "--- API endpoints ---"
check_status "$BASE_URL/api/products" "200" "GET /api/products returns 200" || failures=$((failures + 1))
check_status "$BASE_URL/api/products/categories" "200" "GET /api/products/categories returns 200" || failures=$((failures + 1))

echo ""
echo "--- SPA fallback ---"
check_status "$BASE_URL/nonexistent-route" "200" "SPA fallback returns 200 for unknown route" || failures=$((failures + 1))

echo ""
if [ $failures -eq 0 ]; then
  echo "=== ALL SMOKE TESTS PASSED ==="
  exit 0
else
  echo "=== $failures SMOKE TEST(S) FAILED ==="
  exit 1
fi
