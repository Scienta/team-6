#!/usr/bin/env bash
# The gate a change passes before it is pushed, and again before a person claims a feature.
#
# Usage: workflow/scripts/verify.sh [--quick] [--deployed]
#   1. ./mvnw package               (unit + Testcontainers tests; Docker must run)
#   2. local service on Compose Postgres, then every examples/*.hurl against it
#   --quick: skip HealthTest (~25 s of DB timeouts) and run Maven offline; for the implement loop only.
#            /verify-feature and /ship run the full gate.
#   --deployed: also run the hurl examples against TEAM_URL (writes test data to our live database)
#
# Env: JAVA_HOME_OVERRIDE  the JDK for the build and the local service (default: the JDK matching
#      pom.xml's java.version, found via /usr/libexec/java_home or SDKMAN). It must match what the
#      Dockerfile deploys: other JDKs format dates differently (see workflow/specs/spring-boot-4.md).
#
# Exits non-zero on the first failing step, so "verify.sh passed" is evidence you can quote.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
port=18080
log="$(mktemp -t cfp-verify).log"
quick=false deployed=false
for arg in "$@"; do
  case "$arg" in
    --quick) quick=true ;;
    --deployed) deployed=true ;;
    *) echo "usage: $0 [--quick] [--deployed]" >&2; exit 2 ;;
  esac
done

step() { printf '\n==> %s\n' "$*"; }

# The JDK that production runs: pom.xml's java.version (the Dockerfile uses the same major version).
want_java="$(sed -n 's:.*<java.version>\([0-9]*\)</java.version>.*:\1:p' pom.xml | head -1)"
[ -n "$want_java" ] || { echo "could not read java.version from pom.xml"; exit 1; }
jdk="${JAVA_HOME_OVERRIDE:-}"
if [ -z "$jdk" ] && [ -x /usr/libexec/java_home ]; then
  # java_home falls back to the default JDK without failing, so check what it returned.
  h="$(/usr/libexec/java_home -v "$want_java" 2>/dev/null || true)"
  if [ -n "$h" ] && "$h/bin/java" -version 2>&1 | head -1 | grep -q "\"$want_java\."; then jdk="$h"; fi
fi
if [ -z "$jdk" ]; then
  for h in "$HOME"/.sdkman/candidates/java/"$want_java".*; do [ -x "$h/bin/java" ] && jdk="$h"; done
fi
[ -n "$jdk" ] || { echo "no JDK $want_java found (pom.xml java.version); set JAVA_HOME_OVERRIDE"; exit 1; }
export JAVA_HOME="$jdk"
java_bin="$jdk/bin/java"

mvn_args=(-q clean package)
if $quick; then mvn_args=(-o -q clean package "-Dtest=!HealthTest" -Dsurefire.failIfNoSpecifiedTests=false); fi
step "build and test (./mvnw ${mvn_args[*]}, JDK $want_java)"
"$java_bin" -version 2>&1 | head -1
# clean, so a failed build can't leave last run's jar and test reports behind to be mistaken for a pass.
set +e
./mvnw "${mvn_args[@]}" 2>&1 | grep -E 'Tests run:|FAIL|ERROR|BUILD' | grep -v 'Failures: 0, Errors: 0'
mvn_status=${PIPESTATUS[0]}
set -e
[ "$mvn_status" -eq 0 ] && [ -f target/cfp.jar ] || { echo "build failed (mvnw exit $mvn_status)"; exit 1; }
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
# The same JDK as the build, which matches production.
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

if $deployed; then
  url="$(gh variable get TEAM_URL 2>/dev/null || echo "https://fagsem-h2026-$(cat .fagdag/team).scienta.cloud")"
  step "hurl examples against $url"
  hurl --test --jobs 1 --variable "base_url=$url" examples/*.hurl
fi

if $quick; then step "PASSED (quick: HealthTest skipped; run the full gate before shipping) (service log: $log)"; else step "PASSED (service log: $log)"; fi
