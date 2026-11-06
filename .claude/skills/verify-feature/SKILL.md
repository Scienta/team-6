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
   give it `specs/<slug>.md`, `workflow/specs/<slug>.md` and the diff (`git diff origin/main`), not
   your conclusion. Ask for a table of rule, where implemented, which test proves it, and gaps:
   untested rules, status codes not matching our decisions, missing fields on any Talk view,
   races, and regressions in other features.
3. Check the effects on other features: existing responses still have every field the base specs
   list, and `ConferenceServiceTest`, `TalksTest` and the base hurl files still pass (step 1
   covers that).
4. **Fix review findings** (step 5b): fix each finding with a test, or, if the behaviour is
   acceptable, record it as a row in `workflow/specs/<slug>.md` (e.g. "415 for a non-JSON body").
   Fix doc drift between the spec, plan and tests. Re-run the full gate.
5. Report: gate result, rule→test table, what the review found and what was done about each
   finding. If clean, say it's ready for `/ship`.
