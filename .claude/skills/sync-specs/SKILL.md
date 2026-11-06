---
name: sync-specs
description: Check the fagdag harness for released or changed feature specs and download them into specs/. Use when asked to check for new specs, download specs, or what's released.
---

# Sync specs

0. **Start:** `git pull --rebase` (if the tree is dirty, say so and don't pull), then
   `git log --oneline -10` to see what teammates have pushed since we last looked.
1. Run `workflow/scripts/harness.sh sync`. It lists the slugs on `$H/specs`, downloads each spec to
   `specs/<slug>.md`, and prints `NEW`, `CHANGED` (diff in `specs/<slug>.diff`) or `same`.
2. Run `workflow/scripts/harness.sh feedback` for the claimed/unclaimed state of every feature.
3. For every NEW or CHANGED slug, check what already exists: `workflow/specs/<slug>.md`,
   `workflow/plans/<slug>.md`, `examples/<slug>.hurl`, and `git log --oneline -- <related files>`.
   Teammates may already have worked on it.
4. Report a table: slug, kind (base / drip / change request), version, claimed, our spec/plan/tests
   present, suggested next step (`/plan-feature <slug>` usually).

Don't commit `specs/` changes on your own; mention them. Never print the token.
