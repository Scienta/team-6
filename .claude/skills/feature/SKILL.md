---
name: feature
description: Run our whole loop for a feature or change request - sync, spec, plan, implement, verify, ship, claim handover - stopping at the human checkpoints. Use when asked to "do" or "take" a feature end to end, or what to work on next.
argument-hint: "[slug]"
---

# Feature loop

The process is in `workflow/PROCESS.md`. Run the steps in order and **stop at each checkpoint**
for the user's answer:

1. `/sync-specs`. If no slug was given, pick the released, unclaimed feature (or FAULTY check) that's
   worth the most, and say why.
2. `/plan-feature <slug>`. **Checkpoint:** the user approves the plan and answers or parks the PO?
   questions.
3. `/implement-feature <slug>`.
4. `/verify-feature <slug>`. If there are gaps, go back to 3 (at most twice, then ask).
5. **Checkpoint:** the user says ship. Then `/ship <slug>`.
6. **Checkpoint:** a person claims on the harness. Afterwards, check with `/feature-status <slug>`
   on the following ticks, and `/triage-check` anything FAULTY.
7. Make sure `REVIEW.md` has the claim log row and anything the harness caught.

Keep a short status line per step, so the user can see where the loop is.
