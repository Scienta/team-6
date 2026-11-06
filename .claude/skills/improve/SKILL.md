---
name: improve
description: Run the improvement track for work that isn't a feature request (build or test speed, logging, tooling, refactoring) - measure, plan, let the user pick steps, implement, measure again. Use when asked to speed something up, clean up, or improve the build or tooling.
argument-hint: <topic>
---

# Improvement track

1. **Measure first.** Collect real numbers (surefire timings in `target/surefire-reports/*.xml`,
   `time workflow/scripts/verify.sh`, log counts, failure rates). Don't guess where the time or
   the problem is.
2. **Plan** in `workflow/plans/<topic>.md`: a table of where the cost is, then numbered steps with
   the biggest win first, the expected gain for each, and risks (especially any production code
   touched). Status `draft`.
3. **Stop:** the user picks which steps to do. Record the choice in the plan's status line.
4. **Implement** only the chosen steps. If a step changes production behaviour or configuration,
   add a test that pins the production value (like `HealthContractTest`).
5. **Measure again** the same way, and add a before/after table to the plan. Report honestly if
   the gain is smaller than estimated, and why. List the next possible win without doing it.
6. Run the full `workflow/scripts/verify.sh`, then hand over to `/ship` when the user says so.
