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

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| spring-boot-4 | claimed | 98 | Spec and plan: [workflow/specs/spring-boot-4.md](workflow/specs/spring-boot-4.md), [workflow/plans/spring-boot-4.md](workflow/plans/spring-boot-4.md). 20 pinned behaviours (P1-P20: `UpgradeContractTest`, `UtilsTest#usShortIsByteExact`, `examples/spring-boot-4.hurl`), green on Boot 2.7 before the upgrade and on 4.1.2 / Java 25 after. `verify.sh` green on JDK 25 (96 tests, 7 Hurl files, 137 requests). The production image built locally passed every Hurl file. An independent review agent compared about 140 identical requests against the old and new jars. Deployed `4e7a533`: `/actuator/info` reports `4.1.2` / `25.0.5`, Hurl green against the live URL. Harness after the claim: spring-boot-4 2/2 OK, base features all OK from tick 100 |

## 4. What the harness caught that we didn't

<!-- Failures the harness found after you claimed (or after a deploy), and why your own review missed them. -->

**Co-speakers check-2: the main speaker added as their own co-speaker (FAULTY ticks 71-100).** The
feature request only says "the main speaker can't also be a co-speaker" and lists no error codes. We
invented `409`, by analogy with "at most two co-speakers", and the harness rejected it from the tick
after the claim. We then asked the product owner, who said `200, nothing changes`. We shipped that,
and the harness rejected it too (tick 90, on the new code). `400` passed at tick 101.

- *Why our review missed it:* the independent review checked the code against **our** decisions,
  and our decisions were the bug. All our tests passed because they encoded the same guess.
- *Why the second try missed:* the PO answer was a business opinion, not the checks' contract, and
  we shipped it without a way to verify it before the next tick.
- *What we changed:* `/verify-feature` now lists every status code that the feature request doesn't
  give and that we invented, and compares each with how the base features answer the same *kind*
  of request. A bad request gets `400` (as `POST /talks` with a speaker that isn't allowed) and a
  limit or duplicate gets `409`. Each invented code is flagged as **PO?** before claiming. The
  workshop-registration spec starts from this rule. Logs (`logs.sh`) plus `harness.sh timeline`
  showed which response failed, and when a deploy actually went live.

**(Caught by our own gate, not the harness:) `ratedAtDisplay` and the JDK.** The first run of
`verify.sh` failed `ratings.hurl` because the local service ran on JDK 27, which puts a narrow
no-break space (U+202F) before AM/PM. Production then ran Temurin 11, so the deployed service was
fine. We made the gate run the production JDK, and the finding became the main risk in the
spring-boot-4 upgrade: Java 25 has the same locale data. The upgrade now writes the format
explicitly instead of relying on locale data, pinned byte-exact by `UtilsTest` and
`UpgradeContractTest` ([DESIGN.md](workflow/DESIGN.md) D1250, D1650, D1701).

**Harness, ratings check-1, tick 99 (after the spring-boot-4 deploy).** One `POST /speakers` got no
answer at all (status none, empty body), and the check was OK again at tick 100. Our deploy finished at
13:03 and the ticks after it (93-98) were OK. A docs-only commit pushed right after ours triggered a
second redeploy at 13:06, and tick 99 falls in that container swap. The logs show no errors or 500s.
Our review couldn't catch it: no local gate tests the moment a container is replaced. **Lesson:** two
pushes to `main` minutes apart cost us a check run; batch docs commits or push them between ticks.

**Caught by our own layers before the harness did: the Spring Boot 4 upgrade** (details under
"Deviations" in [workflow/plans/spring-boot-4.md](workflow/plans/spring-boot-4.md)). Each layer caught
something the one before it missed:

1. **Pins written first, on the old stack.** On Boot 4.1 / Java 25 they failed exactly where the spec
   predicted:
   - `ratedAtDisplay` got a narrow no-break space (U+202F) before PM
   - `POST /speakers/` returned 404
   - `submittedAt` ended in `Z` instead of `+00:00`
   - JSON with comments got a 400
2. **The gate found two traps the spec hadn't foreseen.**
   - Every `/talks/{id}/…` route answered 500. Spring 6.1+ needs the `-parameters` flag, which
     `spring-boot-starter-parent` normally sets, and we don't use the parent POM.
   - `submittedOn` came out **a day early** on any JVM east of UTC (`java.sql.Date` is local midnight).
     Production runs in UTC, which hid it.
3. **The independent review agent found what no test of ours covered.**
   - Three properties renamed in Boot 4.0 are **silently ignored** under their old names. Error bodies
     lost `message` and `errors`, and responses lost `charset=UTF-8`. The legacy mobile client depends
     on those.
   - Jackson 3 now rejects text after the JSON (201 became 400).
   - The branch was behind `main` and would have undone teammates' work.

Why our first pass missed the review's findings: we pinned what the specs say and what the harness
sends, but not the legacy behaviour that only lived in `application.properties`.

## 5. Reusable artifacts

<!-- What's worth taking to client work: agents, skills, prompts, spec templates, gates. Give the paths. -->

- **Pin first, then upgrade.** For a framework or runtime upgrade:
  1. List the implicit behaviour the specs, the real traffic (our request log) and the config promise.
  2. Pin it with tests that pass on the *old* stack.
  3. Upgrade, and fix until the same tests pass on the new stack.

  Example: [workflow/specs/spring-boot-4.md](workflow/specs/spring-boot-4.md) (the "Upgrade traps"
  and "Behaviour we pin" tables) and
  [src/test/java/no/scienta/cfp/UpgradeContractTest.java](src/test/java/no/scienta/cfp/UpgradeContractTest.java).
- **A review agent that compares old and new behaviour.** Instead of reading the diff, it runs the
  same requests against the old and the new build and compares status, headers and bodies. That's how
  it found the silently ignored properties.
- **A gate that can't pass on the wrong runtime:** [workflow/scripts/verify.sh](workflow/scripts/verify.sh)
  takes the JDK from `pom.xml` for both the build and the run, cleans first, and checks Maven's exit code.

## 6. One thing we'd do differently

<!-- One thing, and why. -->
