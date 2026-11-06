---
name: verify-feature
description: Verify a feature against its spec before shipping or claiming - run the full gate (tests, local service, hurl) and review the code rule by rule. Use when asked to verify, test, check coverage, or whether a feature is ready to claim.
argument-hint: <slug>
---

# Verify a feature

1. **Gate.** Run `workflow/scripts/verify.sh` (Docker must run). It runs `./mvnw package`, starts
   the service on Compose Postgres, and runs every `examples/*.hurl`. Quote its pass/fail lines.
   Run `--deployed` only if the user asks: it writes test data to our live database.
2. **Rule-by-rule review**, independent of the author. Spawn one review agent (general-purpose) and
   give it `specs/<slug>.md`, `workflow/specs/<slug>.md`, `workflow/DESIGN.md` and the diff (`git diff origin/main`), not
   your conclusion. Ask for a table of rule, where implemented, which test proves it, and gaps:
   untested rules, status codes not matching our decisions, missing fields on any Talk view,
   races, regressions in other features, and anywhere `DESIGN.md` disagrees with the feature's
   spec or the code (including its non-functional requirements, D1700+).
3. Check the effects on other features: existing responses still have every field the base specs
   list, and `ConferenceServiceTest`, `TalksTest` and the base hurl files still pass (step 1
   covers that).
3b. **Invented status codes** (lesson from co-speakers check-2, see `REVIEW.md` §4): list every
   status code in `workflow/specs/<slug>.md` that the feature request doesn't give. For each, say
   what the base features answer to the same *kind* of request (bad or not-allowed input → `400`,
   as `POST /talks` with an unknown speaker; limit or duplicate → `409`, as the 3-talk limit and a
   duplicate email; unknown id → `404`). Flag any mismatch and any code without a precedent as
   **PO?**, and tell the user before claiming. Our tests can't catch this: they encode our own guess.
4. **Fix review findings** (step 5b): fix each finding with a test, or, if the behaviour is
   acceptable, record it as a row in `workflow/specs/<slug>.md` (e.g. "415 for a non-JSON body")
   and the matching D-rule in `workflow/DESIGN.md`. Fix doc drift between the spec, `DESIGN.md`,
   the plan and the tests. Re-run the full gate.
5. Report: gate result, rule→test table, what the review found and what was done about each
   finding. If clean, say it's ready for `/ship`.
