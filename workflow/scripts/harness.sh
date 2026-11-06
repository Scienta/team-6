#!/usr/bin/env bash
# Talks to the fagdag harness for this team. Never prints the team token.
#
# Usage: workflow/scripts/harness.sh <command> [args]
#   list                    feature slugs published on $H/specs
#   sync                    download every published spec into specs/<slug>.md; prints NEW, CHANGED or same per slug
#   spec <slug>             print one spec (from the harness, not the local copy)
#   feedback [slug]         compact status: tick, score, per feature claimed + check statuses (FAULTY in full)
#   feedback-json           the raw feedback JSON
#   timeline <feature/check>  the history of one check (e.g. co-speakers/check-3)
#   health                  the deployed service's /health, and whether its version is origin/main
#
# Rate limit: bursts of 5, then 1 request/s. The script waits 1 s between requests and honours Retry-After on 429.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
H="$(cat "$root/.fagdag/harness-url")"
TEAM="$(cat "$root/.fagdag/team")"
token_file="$root/.fagdag/harness-token"
[ -s "$token_file" ] || { echo "No team token in .fagdag/harness-token: a person pastes it from the harness /feedback page." >&2; exit 1; }
TOKEN="$(tr -d '[:space:]' < "$token_file")"

# get <path> [outfile]: GET with auth, retrying on 429. Exit 4 on 404 (e.g. a spec not released yet).
get() {
  local path="$1" out="${2:-/dev/stdout}" tmp code retry
  tmp="$(mktemp)"
  for _ in 1 2 3 4 5; do
    code="$(curl -sS -o "$tmp" -D "$tmp.h" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$H$path")"
    if [ "$code" = 429 ]; then
      retry="$(grep -i '^retry-after:' "$tmp.h" | tr -dc '0-9')"
      sleep "${retry:-2}"
      continue
    fi
    break
  done
  rm -f "$tmp.h"
  case "$code" in
    200) cat "$tmp" > "$out"; rm -f "$tmp" ;;
    404) rm -f "$tmp"; return 4 ;;
    *) echo "GET $path -> $code" >&2; head -c 500 "$tmp" >&2; echo >&2; rm -f "$tmp"; return 1 ;;
  esac
}

slugs() {
  # Buffer the page first: piping curl straight into grep can kill it with exit 23 when the reader
  # closes early, which under `set -o pipefail` would look like "no specs" instead of an error.
  local page; page="$(mktemp)"
  get /specs > "$page"
  grep -oE 'href="/specs/[a-z0-9-]+/spec\.md"' "$page" | sed -E 's#href="/specs/([^/]+)/spec\.md"#\1#' | sort -u
  rm -f "$page"
}

cmd="${1:-}"; shift || true
case "$cmd" in
  list)
    slugs ;;
  sync)
    mkdir -p "$root/specs"
    for s in $(slugs); do
      sleep 1
      tmp="$(mktemp)"
      if get "/specs/$s/spec.md" "$tmp"; then
        dest="$root/specs/$s.md"
        if [ ! -f "$dest" ]; then status=NEW
        elif cmp -s "$tmp" "$dest"; then status=same
        else status=CHANGED; diff -u "$dest" "$tmp" > "$root/specs/$s.diff" || true
        fi
        mv "$tmp" "$dest"
        printf '%-8s %-20s %s\n' "$status" "$s" "$(grep -m1 '^Kind:' "$dest" || true)"
        if [ "$status" = CHANGED ]; then echo "         diff saved in specs/$s.diff"; fi
      else
        rm -f "$tmp"; printf '%-8s %s\n' "404" "$s (listed but not released yet)"
      fi
    done ;;
  spec)
    [ $# -eq 1 ] || { echo "usage: $0 spec <slug>" >&2; exit 2; }
    get "/specs/$1/spec.md" ;;
  feedback-json)
    get "/api/teams/$TEAM/feedback" ;;
  feedback)
    filter="${1:-}"
    get "/api/teams/$TEAM/feedback" | jq -r --arg f "$filter" '
      "tick \(.tick)  score \(.score.total) (earned \(.score.earned), bled \(.score.bled))  down=\(.down.current)\(if .down.reason then " (" + .down.reason + ")" else "" end)",
      (.features[] | select($f == "" or .slug == $f) |
        "\(.slug)  claimed=\(.claimed)\(if .claimedAtTick != null then " @tick \(.claimedAtTick)" else "" end)  " +
          ([.checks[]? | "\(.name)=\(.status)"] | join(" ")),
        (.checks[]? | select(.status != "OK") | "  -- \(.name) \(.status)\n" + (. | tostring)))' ;;
  timeline)
    [ $# -eq 1 ] || { echo "usage: $0 timeline <feature/check>" >&2; exit 2; }
    get "/api/teams/$TEAM/feedback/timeline?check=$1" | jq . ;;
  health)
    url="$(cd "$root" && gh variable get TEAM_URL 2>/dev/null || echo "https://fagsem-h2026-$TEAM.scienta.cloud")"
    body="$(curl -sS --max-time 30 "$url/health")"
    echo "$url/health -> $body"
    git -C "$root" fetch -q origin main 2>/dev/null || true
    want="$(git -C "$root" rev-parse origin/main)"
    if echo "$body" | grep -q "\"$want\""; then echo "deployed = origin/main ($want)"; else echo "deployed != origin/main ($want): deploy pending or failed"; exit 3; fi ;;
  *)
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
