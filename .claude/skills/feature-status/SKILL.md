---
name: feature-status
description: Show the harness's view of our team - tick, score, deploy health, and every feature's checks. Use when asked about status, score, feedback, or whether checks pass.
argument-hint: "[slug]"
---

# Feature status

1. `workflow/scripts/harness.sh feedback [slug]`: tick, score, down state, and per feature whether
   it's claimed plus its check statuses. Non-OK checks are printed in full.
2. `workflow/scripts/harness.sh health`: is the deployed version origin/main?
3. Summarise in a few lines: what's down or FAULTY (suggest `/triage-check <feature/check>`),
   which released features are unclaimed (suggest `/plan-feature`), and if score is bleeding.
