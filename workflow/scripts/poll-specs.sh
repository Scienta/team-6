#!/usr/bin/env bash
# Read-only poll for released or changed specs. Unlike `harness.sh sync` this never writes to
# specs/, so it can run unattended while people edit the working tree.
#
# Usage: workflow/scripts/poll-specs.sh
# Prints one line per difference and exits 10 if anything is NEW or CHANGED, 0 if all the same.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
h="$root/workflow/scripts/harness.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Never report "nothing new" because we failed to reach the harness: an empty or failed listing is
# an error (exit 1), not an all-clear.
if ! "$h" list > "$tmp/slugs" || [ ! -s "$tmp/slugs" ]; then
  echo "ERROR: could not list specs from the harness" >&2
  exit 1
fi

found=0
while read -r s; do
  [ -n "$s" ] || continue
  sleep 1
  set +e
  "$h" spec "$s" > "$tmp/$s.md" 2>/dev/null
  rc=$?
  set -e
  case "$rc" in
    0) ;;
    4) continue ;;  # listed but not released yet (404)
    *) echo "ERROR: fetching $s failed (exit $rc)" >&2; exit 1 ;;
  esac
  dest="$root/specs/$s.md"
  if [ ! -f "$dest" ]; then
    echo "NEW      $s  $(grep -m1 '^Kind:' "$tmp/$s.md" || true)"
    found=1
  elif ! cmp -s "$tmp/$s.md" "$dest"; then
    echo "CHANGED  $s  local=$(grep -m1 '^Version:' "$dest" || echo '?')  harness=$(grep -m1 '^Version:' "$tmp/$s.md" || echo '?')"
    found=1
  fi
done < "$tmp/slugs"

[ "$found" = 0 ] && echo "same: $(wc -l < "$tmp/slugs") specs on the harness, nothing new"
exit $(( found ? 10 : 0 ))
