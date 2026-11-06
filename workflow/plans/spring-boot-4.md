# Plan: Upgrade to Spring Boot 4.x

Spec: `specs/spring-boot-4.md` (V1), our decisions: `workflow/specs/spring-boot-4.md`. Status: **approved** (tick ~42).

## Current state

- Boot 2.7.18, Java 11 (`maven.compiler.release` 11, Temurin 11 images), Jackson 2, Tomcat 9,
  Testcontainers 1.21.4 (`org.testcontainers:postgresql`), Maven 3.9.11 wrapper (fine for Boot 4).
- `javax.*` in `RequestLoggingFilter`, `SpeakerController`, `SpeakerForm`.
- Dates: `Utils.usShort` uses `DateFormat.SHORT` with `Locale.US` (JDK-dependent). `TalkDto.submittedAt`
  is a `java.util.Date` and `submittedOn` a `java.sql.Date`, both formatted by Jackson defaults.
- Trailing slashes work only because Spring 5 matches them by default.
- The harness is already probing `GET /actuator/info` (14 calls, answering `2.7.18`), and
  spring-boot-4 is unclaimed.
- No one else on the team has a branch or plan for this (checked at tick 40).

## Phase A: pin today's behaviour (on 2.7, safe to ship to `main` on its own)

Tests first. They must all be **green on 2.7**: they describe today's contract.

| Pin | JUnit test | Hurl entry (`examples/spring-boot-4.hurl`) |
|---|---|---|
| P1, P2 | `UpgradeContractTest#submittedAtAndOnFormatsOnEveryTalkView` | `# P1 P2` regex on `submittedAt` and `submittedOn` |
| P3 | `UtilsTest#usShortIsByteExact` (fixed instants 00:00, 12:00, 13:05 UTC; asserts `U+0020` before AM/PM) | `# P3` regex with a literal space |
| P4 | - | `# P4` `GET /speakers/{id}/` returns 200, `POST /speakers/` returns 201 |
| P5 | - | `# P5` seven 400 cases |
| P6 | - | `# P6` mixed-case email returns 409 |
| P7 | - | `# P7` six 400 cases |
| P8 | existing `TalksTest` (concurrent) | `# P8` 4th talk returns 409 |
| P9 | - | `# P9` score `0`, `-1`, `6`, `2.5`, `null`, `"3"`, reviewer `0` and `-1` return 400; unknown talk returns 404 |
| P10 | - | `# P10` `sort=RATING`, `sort=id`, `sort=score` return 400 |
| P11 | `UpgradeContractTest#averageRoundsHalfUpAndIsAnExplicitNullWhenUnrated` | `# P11` 4, 4, 4, 5 gives `4.3`; unrated `averageRating == null` |
| P12 | `UpgradeContractTest#rankingBreaksTiesOnRatingCountThenAge` (added after review; `ratings.hurl` only covered unrated-last) | - |
| P13 | - | `# P13` unknown ids return 404, `abc` returns 400 |
| P14 | existing `HealthTest` | - |
| P15 | `UpgradeContractTest#actuatorInfoReportsTheRunningVersions` | `# P15` `versions.springBoot` exists |
| P16 | - | `# P16` body with `// comment` and an unknown field returns 201 |

Exit criteria for Phase A: `verify.sh` green on 2.7. Optionally ship it to `main` (new tests only,
no production code change) so the pins guard every later change.

## Phase B: upgrade (branch `spring-boot-4`)

| Layer | File | Change |
|---|---|---|
| build | `pom.xml` | `spring-boot.version` 4.1.2, `java.version` and `release` 25; `spring-boot-starter-web` becomes `spring-boot-starter-webmvc`; drop the explicit Testcontainers version (the BOM manages 2.0.5) and use `org.testcontainers:testcontainers-postgresql`; check whether `spring-boot-starter-test` still brings everything the tests use (otherwise add `spring-boot-starter-webmvc-test`) |
| image | `Dockerfile` | `maven:3.9-eclipse-temurin-25` and `eclipse-temurin:25-jre`; keep `GIT_SHA`, `PGSSLMODE`, port 8080 |
| imports | `RequestLoggingFilter`, `SpeakerController`, `SpeakerForm` | `javax.servlet` becomes `jakarta.servlet`, `javax.validation` becomes `jakarta.validation` |
| P3 | `Utils.usShort` | Explicit `DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US)` in UTC, so the space is a literal in the pattern |
| P1, P2 | `TalkDto` | Explicit formats on `submittedAt` (pattern `yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'`, UTC) and `submittedOn` (`yyyy-MM-dd`); keep the Java types the report code uses, or add formatted getters |
| P4 | new `TrailingSlashConfig` | Register Spring's `UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest()` so every path accepts a trailing slash without a redirect |
| P16, output | `application.properties` | Jackson 3 property names for allowing comments; keep unknown properties ignored, nulls included, and no alphabetical property sorting |
| tests | `TestDatabase`, `HealthTest` | `org.testcontainers.postgresql.PostgreSQLContainer` (Testcontainers 2 package) |
| gate | `workflow/scripts/verify.sh` | Pick the JDK from `pom.xml`'s `java.version` (25 after the upgrade) for both the build and the local run; today it prefers 11 or 17 for the run and uses the default `java` for the build |
| docs | `AGENTS.md` and `README.md` say Java 11 / Boot 2.7 | `AGENTS.md` is the organisers' file; leave it, but note the new versions in `.claude/rules/workflow.md` and the code style line there |

Order: build and imports first (until it compiles), then the tests (red where a trap hits), then fix P3,
P1/P2, P4 and P16 one at a time until green.

## Risks and open questions

- **PO?** Does "formats included" cover JSON field order and error bodies? We assume not (only the
  documented fields), but we keep the field order anyway.
- **All claimed features at risk.** Mitigations: the Phase A pins, the full gate on JDK 25, the image
  built locally with `docker build` and run against Postgres before the push, and `verify.sh --deployed`
  right after the deploy. The deploy pipeline only rolls back on `/health`; a functional regression needs
  a `git revert` by hand.
- Jackson 3 may change number handling for `Map<String, Object>` bodies (P9 `2.5` and `"3"`), and
  validation messages may change; the pins catch both.
- `spring.sql.init` and `server.error.*` may have moved or changed in Boot 4; the existing tests cover the
  schema start-up.
- Co-speakers code (André) is touched by the import and DTO changes; `CoSpeakersTest` and
  `co-speakers.hurl` must stay green.
- `ReportController` (`/admin/*`) isn't in any spec; we keep it compiling but don't pin its output.

## Deviations (written while implementing)

- **Red before green, as planned.** On 4.1.2 / Java 25, before any fix: P3 (`1:05<U+202F>PM`), P4
  (`POST /speakers/` 404), P1 (`…922Z`) and P16 (comment rejected with 400) failed, exactly the traps the
  spec predicted.
- **Not foreseen: `-parameters`.** Before the trap fixes, 45 requests across `AcceptanceTest`,
  `CoSpeakersTest` and `TalksTest` answered 500 ("Name for argument of type [java.lang.Long] not
  specified"). Added `<parameters>true</parameters>` to the compiler plugin.
- **Not foreseen: `submittedOn` a day early.** The P2 pin caught it once `submittedAt` and `submittedOn`
  were formatted in UTC: `java.sql.Date.valueOf` is local midnight. `submittedOn` is now a `String`
  (`Utils.utcDay`), and `Utils.toSqlDate` is gone.
- `ConferenceServiceTest`: `ResponseStatusException.getStatus()` became `getStatusCode()` (Spring 6).
- The tests keep parsing with Jackson 2 (`com.fasterxml.jackson.core:jackson-databind`, test scope,
  managed by Boot's `jackson-2-bom`), so they stay a client independent of the app's Jackson 3.
- `workflow/scripts/verify.sh` also runs `clean` and checks the Maven exit code, so a failed build can't
  pass on last run's jar and reports.
- Acceptance (Erlend, pushed during planning) is covered too: `AcceptanceTest` and `acceptance.hurl`.
- **Independent review (step 5) found what the pins missed.** The agent ran about 140 identical requests
  against the old jar (2.7 / JDK 18) and the new one (4.1 / JDK 25) and compared them. Status codes and
  success bodies matched, but:
  - F1: three Boot properties were renamed in 4.0 and the old names are ignored silently, so `message`
    and `errors` vanished from error bodies and the charset from `Content-Type`. Renamed.
  - F2: Jackson 3 rejects content after the JSON (201 became 400). `use-jackson2-defaults=true`.
  - P12 tie-breaks were claimed as pinned but weren't; P1 couldn't catch a wrong instant. Both are tested now.
  - F4: the branch was behind `main` (test speed-up, bio fix, `verify.sh --quick`). Fast-forwarded
    and re-applied; `TestDatabase` and `SpeakerFormTest` moved to Testcontainers 2 and jakarta, and
    `verify.sh` keeps both `--quick` and the JDK, `clean` and exit-code changes.
  - New pins P17-P20 failed with the old property block (charset, trailing content, `message`) and pass
    with the fix.

## Done when

- [x] every P1-P16 has a test, green on 2.7 before the upgrade (JUnit on JDK 18; `spring-boot-4.hurl` 49/49 against the 2.7 image)
- [ ] the same tests are green on 4.1.2 / Java 25 (`workflow/scripts/verify.sh`)
- [x] the image built with the new `Dockerfile` passes every `examples/*.hurl` locally (6 files, 128 requests; `/actuator/info` reports 4.1.2, Java 25.0.5)
- [ ] an independent review agent checked the diff against both specs (rule→test table, no gaps)
- [ ] deployed `/health` reports the new SHA, `/actuator/info` reports `4.1.2` and Java 25, and `verify.sh --deployed` is green
- [ ] the base features' harness checks stay OK on the next ticks
- [ ] a person has claimed it; the claim log in `REVIEW.md` has the evidence
