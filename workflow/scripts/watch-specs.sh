#!/usr/bin/env bash
# Background watcher: polls the harness for released or changed specs and logs every check.
# Read-only - never writes to specs/. Exits once something is found, so the log keeps the hit
# at the bottom instead of repeating it.
#
# Usage: workflow/scripts/watch-specs.sh [interval-seconds]   (default 60)
# Log:   workflow/.poll-specs.log  (gitignored)  -  tail -f it.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
interval="${1:-60}"
log="$root/workflow/.poll-specs.log"

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }

echo "[$(ts)] watch-specs started, every ${interval}s, baseline $(ls "$root"/specs/*.md | wc -l) specs" >> "$log"

while true; do
  set +e
  out="$("$root/workflow/scripts/poll-specs.sh" 2>&1)"
  code=$?
  set -e

  case "$code" in
    0)  echo "[$(ts)] ok   $out" >> "$log" ;;
    10) {
          echo "[$(ts)] ==== SPEC CHANGE DETECTED ===="
          echo "$out" | sed "s/^/[$(ts)] /"
          echo "[$(ts)] ==== watcher stopping; run /sync-specs to pull it into specs/ ===="
        } >> "$log"
        exit 10 ;;
    *)  echo "[$(ts)] ERR  (exit $code) $out" >> "$log" ;;
  esac

  sleep "$interval"
done
