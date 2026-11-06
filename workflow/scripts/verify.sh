#!/usr/bin/env bash
# The gate a change passes before it is pushed, and again before a person claims a feature.
#
# Usage: workflow/scripts/verify.sh [--deployed]
#   1. ./mvnw package               (unit + Testcontainers tests; Docker must run)
#   2. local service on Compose Postgres, then every examples/*.hurl against it
#   --deployed: also run the hurl examples against TEAM_URL (writes test data to our live database)
#
# Env: JAVA_RUN  the java binary for the local service (default: JDK 11, else 17, else java)
#
# Exits non-zero on the first failing step, so "verify.sh passed" is evidence you can quote.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
port=18080
log="$(mktemp -t cfp-verify).log"

step() { printf '\n==> %s\n' "$*"; }

step "build and test (./mvnw package)"
./mvnw -q package 2>&1 | grep -E 'Tests run:|FAIL|ERROR|BUILD' | grep -v 'Failures: 0, Errors: 0' || true
[ -f target/cfp.jar ] || { echo "build failed, no target/cfp.jar"; exit 1; }
for f in target/surefire-reports/*.txt; do grep -h 'Tests run' "$f"; done
if grep -lE 'Failures: [1-9]|Errors: [1-9]' target/surefire-reports/*.txt >/dev/null 2>&1; then
  echo "test failures"; exit 1
fi

step "local service on Compose Postgres (port $port)"
docker compose up -d >/dev/null
for _ in $(seq 1 30); do
  docker compose exec -T postgres pg_isready -U cfp -d cfp >/dev/null 2>&1 && break
  sleep 1
done
# Run on a JDK close to production (Dockerfile: Temurin 11). JDK 20+ formats dates differently
# (e.g. a narrow no-break space before AM/PM), which breaks ratings.hurl and isn't what we deploy.
java_bin="${JAVA_RUN:-}"
if [ -z "$java_bin" ] && [ -x /usr/libexec/java_home ]; then
  for v in 11 17; do
    # java_home falls back to the default JDK without failing, so check what it returned.
    h="$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)"
    if [ -n "$h" ] && "$h/bin/java" -version 2>&1 | head -1 | grep -q "\"$v\."; then java_bin="$h/bin/java"; break; fi
  done
fi
java_bin="${java_bin:-java}"
"$java_bin" -version 2>&1 | head -1
"$java_bin" -jar target/cfp.jar --server.port=$port > "$log" 2>&1 &
pid=$!
trap 'kill $pid 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do
  curl -fsS "http://localhost:$port/health" >/dev/null 2>&1 && break
  kill -0 $pid 2>/dev/null || { echo "service died, see $log"; tail -30 "$log"; exit 1; }
  sleep 1
done
curl -fsS "http://localhost:$port/health"; echo

step "hurl examples against localhost:$port"
hurl --test --jobs 1 --variable "base_url=http://localhost:$port" examples/*.hurl

if [ "${1:-}" = "--deployed" ]; then
  url="$(gh variable get TEAM_URL 2>/dev/null || echo "https://fagsem-h2026-$(cat .fagdag/team).scienta.cloud")"
  step "hurl examples against $url"
  hurl --test --jobs 1 --variable "base_url=$url" examples/*.hurl
fi

step "PASSED (service log: $log)"
