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

found=0
for s in $("$h" list); do
  sleep 1
  if ! "$h" spec "$s" > "$tmp/$s.md" 2>/dev/null; then
    continue  # listed but not released yet (404)
  fi
  dest="$root/specs/$s.md"
  if [ ! -f "$dest" ]; then
    echo "NEW      $s  $(grep -m1 '^Kind:' "$tmp/$s.md" || true)"
    found=1
  elif ! cmp -s "$tmp/$s.md" "$dest"; then
    echo "CHANGED  $s  local=$(grep -m1 '^Version:' "$dest" || echo '?')  harness=$(grep -m1 '^Version:' "$tmp/$s.md" || echo '?')"
    found=1
  fi
done

[ "$found" = 0 ] && echo "same: $(ls "$root"/specs/*.md | wc -l) specs, nothing new"
exit $(( found ? 10 : 0 ))
