---
name: wrap-up
description: Prepare REVIEW.md for the 17:00 showcase - every heading filled, claim log complete, links to workflow/ and .claude/. Use when asked to wrap up, prepare the showcase, or before 17:00.
---

# Wrap-up

1. `workflow/scripts/harness.sh feedback`: final tick, score, every feature's claim state and checks.
2. `git log --oneline` and `workflow/plans/*.md` / `workflow/specs/*.md`: what was done and decided.
3. Check every `REVIEW.md` heading has the team's own text (the HTML comments don't count):
   - §1 Workflow: the steps actually run, linking `workflow/PROCESS.md`, `workflow/scripts/`,
     `workflow/templates/` and `.claude/skills/`, `.claude/rules/`.
   - §2 Review: tests first, PO answers as tests, the gate, the independent review agent.
   - §3 Claim log: one row per feature (claimed or skipped, tick, evidence). Ask the user for
     ticks you can't see in `claimedAtTick`.
   - §4 What the harness caught: from `/triage-check` entries; say so if nothing was caught.
   - §5 Reusable artifacts: paths.
   - §6 One thing we'd do differently: ask the user; don't invent it.
4. Show the user the gaps and a draft for each, and write only what they confirm. Commit through
   `/ship` when they say so.
