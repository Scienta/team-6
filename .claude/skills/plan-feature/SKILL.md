---
name: plan-feature
description: Turn a fagdag feature request (specs/<slug>.md) into our spec decisions (workflow/specs/<slug>.md), D-rules in the design document (workflow/DESIGN.md) and an implementation plan (workflow/plans/<slug>.md). Use when asked to analyze a spec or plan a feature or change request.
argument-hint: <slug>
---

# Plan a feature

Input: a slug. If `specs/<slug>.md` is missing or may be stale, run `/sync-specs` first.

1. **Read** `specs/<slug>.md` (and `specs/<slug>.diff` for a change request), the specs it links to,
   `workflow/DESIGN.md` (the whole service: rules from other features, non-functional requirements
   D1700+, cross-cutting rules D1900+), and `workflow/PROCESS.md`.
2. **Current state.** Read what exists: the existing feature code follows Controller (`web`) →
   service (`service`, e.g. `ConferenceService`) → DAO (`dao`, `JdbcTemplate`) → `schema.sql`, with types in `model`
   (DESIGN.md D1730), with `enrich(TalkDto)` adding
   cross-feature fields. Check git log, `workflow/specs/<slug>.md`, existing tests and hurl files,
   and `workflow/scripts/harness.sh feedback <slug>`. Use `jbcontext search` only if you don't
   know where something lives.
3. **Our spec.** Write or update `workflow/specs/<slug>.md` from `workflow/templates/feature-spec.md`:
   number the rules R1..Rn, decide every status code and edge case (unknown ids, repeats,
   concurrency, effects on existing endpoints), and mark business-rule doubts as **PO?** for a
   person to ask the product owner.
4. **Design** (step 2c). Fold the feature into `workflow/DESIGN.md`: its rules and decisions as
   D-rules in the next free block of 100 (each citing its `Rn` or `PO-n`, our decisions marked
   **[ours]**), new fields in the Talk view table, its concurrency and cross-cutting effects, and a
   source map row. For a change request, change the affected D-rules in place and note it in the row.
   Check the new rules against every other area: a contradiction becomes a **PO?** question or a
   decision, never a silent overwrite.
5. **Plan.** Write `workflow/plans/<slug>.md` from `workflow/templates/plan.md`: file-by-file
   changes, a test per rule/decision (JUnit in `src/test/java/no/scienta/cfp/`, Hurl in
   `examples/<slug>.hurl`), risks to existing features, and done-when.
6. **Stop.** List the PO? questions numbered, with our default answer, so the user can reply
   "1: …, 2: …". Record their answers with `/po-answers`. Show the user the rule table, the decisions, the new D-rules and any
   contradictions found, the PO? questions and the plan summary,
   and ask for approval before `/implement-feature`. Set the plan's status to `approved` only when
   they say so.
