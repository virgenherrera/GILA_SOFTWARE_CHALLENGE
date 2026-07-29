#!/usr/bin/env sh
# Structural enforcement of AXIOM-ECHO: runs the Echo System (Stages 1-4)
# before allowing a commit. Exits non-zero on the first failing stage.
set -e

echo "Echo System: Stage 1 (BUILD)"
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm backend clojure -T:build uber
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm frontend pnpm exec ng build --configuration=production

echo "Echo System: Stage 2a (STATIC ANALYSIS)"
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm backend clojure -M:lint
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm backend clojure -M:fmt --check
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm frontend pnpm exec ng lint
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm frontend pnpm exec prettier --check .

echo "Echo System: Stage 2b (DYNAMIC TESTS)"
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm backend clojure -M:test
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm frontend pnpm exec vitest run

echo "Echo System: Stage 4 (AUDIT)"
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm backend clojure -M:outdated
DOCKER_CONFIG=/tmp/docker-config docker compose run --rm frontend pnpm audit --audit-level=moderate

echo "Echo System: all gates green --- commit allowed"
