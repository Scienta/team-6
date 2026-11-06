---
name: plan-feature
description: Turn a fagdag feature request (specs/<slug>.md) into our spec decisions (workflow/specs/<slug>.md) and an implementation plan (workflow/plans/<slug>.md). Use when asked to analyze a spec or plan a feature or change request.
argument-hint: <slug>
---

# Plan a feature

Input: a slug. If `specs/<slug>.md` is missing or may be stale, run `/sync-specs` first.

1. **Read** `specs/<slug>.md` (and `specs/<slug>.diff` for a change request), the specs it links to,
   and `workflow/PROCESS.md`.
2. **Current state.** Read what exists: the existing feature code follows Controller →
   `ConferenceService` → DAO (`JdbcTemplate`) → `schema.sql`, with `enrich(TalkDto)` adding
   cross-feature fields. Check git log, `workflow/specs/<slug>.md`, existing tests and hurl files,
   and `workflow/scripts/harness.sh feedback <slug>`. Use `jbcontext search` only if you don't
   know where something lives.
3. **Our spec.** Write or update `workflow/specs/<slug>.md` from `workflow/templates/feature-spec.md`:
   number the rules R1..Rn, decide every status code and edge case (unknown ids, repeats,
   concurrency, effects on existing endpoints), and mark business-rule doubts as **PO?** for a
   person to ask the product owner.
4. **Plan.** Write `workflow/plans/<slug>.md` from `workflow/templates/plan.md`: file-by-file
   changes, a test per rule/decision (JUnit in `src/test/java/no/scienta/cfp/`, Hurl in
   `examples/<slug>.hurl`), risks to existing features, and done-when.
5. **Stop.** List the PO? questions numbered, with our default answer, so the user can reply
   "1: …, 2: …". Record their answers with `/po-answers`. Show the user the rule table, the decisions, the PO? questions and the plan summary,
   and ask for approval before `/implement-feature`. Set the plan's status to `approved` only when
   they say so.
