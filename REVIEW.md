# REVIEW

> **Showcase brief (17:00, 4 minutes, hard stop).** You screen-share this repo from the shared
> presenter laptop, in a random order drawn at 17:00. No slides, and no harness screens.
>
> 1. **Our workflow in one minute:** section 1, pointing at the files in `workflow/` and `.claude/`.
> 2. **One story:** a bug our review caught before the harness did, or one the harness caught that
>    we missed, and why. Use the claim log.
> 3. **One artifact we'd reuse**, opened on screen.
>
> Keep this file as a running log from t = 0, so 17:00–17:05 is for tidying, not writing. To be
> eligible to win, every heading below needs some text. The comments under each heading are prompts
> and don't count; replace them with your own words.

## 1. Workflow

<!-- The steps you actually ran, from spec to claim. Link to the files in workflow/ and .claude/. -->

One loop per feature, described in [workflow/PROCESS.md](workflow/PROCESS.md): sync → spec → plan
→ implement → verify → ship → claim (a person) → watch → triage. Each step is a skill in
[.claude/skills/](.claude/skills/), and `/feature <slug>` runs the whole loop, stopping at the human
checkpoints (plan approval, push, claim). The fixed operations are scripts:
[workflow/scripts/harness.sh](workflow/scripts/harness.sh) (list/sync specs, feedback, timeline,
deployed health; rate-limited, never prints the token) and
[workflow/scripts/verify.sh](workflow/scripts/verify.sh) (the gate: `./mvnw package`, then every
Hurl example against a local service). Templates for our spec decisions and plans are in
[workflow/templates/](workflow/templates/). Agent guardrails are in
[.claude/rules/workflow.md](.claude/rules/workflow.md).

## 2. How we reviewed agent output

<!-- Who or what reviewed the agents' work (people, review agents, tests, Hurl examples, gates in
the deploy workflow), and against what (the spec, its examples, your own checks). -->

- **Tests first, against our spec.** Every rule (R1..Rn) and every product owner answer (PO-n) in
  `workflow/specs/<slug>.md` has a JUnit test named after it and a Hurl entry. The new tests had to
  fail before the change (acceptance: 13 red → green; co-speakers PO-1: red → green).
- **Product owner answers are code.** Tick 38: acceptance PO-1 (the same decision again is a 200),
  PO-2 (withdrawn and rejected talks don't count towards the 3-talk limit, which changes the talks
  feature), PO-3 (an unregistered speaker withdrawing gets 403), and co-speakers PO-1 (DELETE of a
  non-co-speaker is a 404). They're recorded in each spec's "Product owner answers" table and tested
  in `AcceptanceTest` / `CoSpeakersTest` (`// PO-n`) and `examples/*.hurl` (`# PO-n`).
- **Gate:** `workflow/scripts/verify.sh` (all tests plus every Hurl file against a local service on
  a production-like JDK) before every push.
- **Independent review agent** per feature (`/verify-feature`), given the specs and the diff but
  not our conclusions. It reports rule → implementation → test, plus races and regressions.

## 3. Claim log

<!-- A running entry per feature or change request, written when you claim or decide to skip it:
claimed or skipped, at which tick, and the evidence you held before claiming. For example:

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed | 3 | examples.hurl green against our URL, plus our own edge-case tests for R3 and R7 |
-->

## 4. What the harness caught that we didn't

<!-- Failures the harness found after you claimed (or after a deploy), and why your own review missed them. -->

**Co-speakers, adding the main speaker (harness `co-speakers/check-2`, FAULTY ticks 71–75).** We
answered `409` when a talk's main speaker was added as its co-speaker; the check wanted the request
to change nothing. The feature request gives no status codes, so `409` was our own guess, and every
test we had checked that guess. Our review compared code with our decisions, not decisions with
the business intent. The PO confirmed `200`, talk unchanged (co-speakers PO-2), and the fix shipped
with a test.

**(Caught by our own gate, not the harness:) `ratedAtDisplay` and the JDK.** The first run of
`verify.sh` failed `ratings.hurl` because the local service ran on JDK 27, which puts a narrow
no-break space (U+202F) before AM/PM. Production then ran Temurin 11, so the deployed service was
fine. We made the gate run the production JDK, and the finding became the main risk in the
spring-boot-4 upgrade: Java 25 has the same locale data. The upgrade now writes the format
explicitly instead of relying on locale data, pinned byte-exact by `UtilsTest` and
`UpgradeContractTest` ([DESIGN.md](workflow/DESIGN.md) D1250, D1650, D1701).

## 5. Reusable artifacts

<!-- What's worth taking to client work: agents, skills, prompts, spec templates, gates. Give the paths. -->

## 6. One thing we'd do differently

<!-- One thing, and why. -->
