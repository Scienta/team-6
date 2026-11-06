#!/usr/bin/env bash
# Concurrency checks for co-speakers against a running service (the Testcontainers test needs Docker; this doesn't).
#   1. 10 different speakers PUT on one talk at the same time: exactly 2 get 200, 8 get 409, the talk has 2.
#   2. One speaker PUT 5 times at once (double-click): all 200, the talk has that speaker once.
#   3. One co-speaker DELETEd 5 times at once: exactly one 200, four 404 (co-speakers PO-1), the talk has 0.
# Usage: workflow/scripts/co-speakers-race.sh [base-url]
set -euo pipefail
base="${1:-https://fagsem-h2026-team-6.scienta.cloud}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
fail=0

id() { grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2; }
speaker() { curl -fsS -H 'Content-Type: application/json' -d "{\"name\":\"Race $1\",\"email\":\"race-$1-$RANDOM$RANDOM-$(date +%s%N)@example.com\"}" "$base/speakers" | id; }
talk() { curl -fsS -H 'Content-Type: application/json' -d "{\"title\":\"Race\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":$1}" "$base/talks" | id; }
co() { curl -fsS "$base/talks/$1" | grep -oE '"coSpeakerIds":\[[0-9,]*\]'; }
check() { if [ "$2" = "$3" ]; then echo "  ok   $1: $2"; else echo "  FAIL $1: got $2, want $3"; fail=1; fi; }

echo "1. 10 different speakers at once"
main=$(speaker main); t=$(talk "$main")
others=(); for i in $(seq 1 10); do others+=("$(speaker "o$i")"); done
for o in "${others[@]}"; do curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$base/talks/$t/co-speakers/$o" >"$tmp/$o" & done; wait
check "200s" "$(cat "$tmp"/* | grep -c 200 || true)" 2
check "409s" "$(cat "$tmp"/* | grep -c 409 || true)" 8
check "co-speakers on the talk" "$(co "$t" | grep -oE '[0-9]+' | wc -l | tr -d ' ')" 2
rm -f "$tmp"/*

echo "2. the same speaker 5 times at once"
t=$(talk "$main"); o="${others[0]}"
for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$base/talks/$t/co-speakers/$o" >"$tmp/$i" & done; wait
check "200s" "$(cat "$tmp"/* | grep -c 200 || true)" 5
check "coSpeakerIds" "$(co "$t")" "\"coSpeakerIds\":[$o]"
rm -f "$tmp"/*

echo "3. the same DELETE 5 times at once"
for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{http_code}\n' -X DELETE "$base/talks/$t/co-speakers/$o" >"$tmp/$i" & done; wait
check "200s" "$(cat "$tmp"/* | grep -c 200 || true)" 1
check "404s" "$(cat "$tmp"/* | grep -c 404 || true)" 4
check "coSpeakerIds" "$(co "$t")" '"coSpeakerIds":[]'

[ "$fail" = 0 ] && echo "all race checks passed" || { echo "race checks FAILED"; exit 1; }
