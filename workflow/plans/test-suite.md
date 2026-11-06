# Plan: test suite gaps and cleanup

Status: steps 1–6 done (the user chose all of them); step 3's rounding left as **PO?**, today's behaviour pinned. Measured on a laptop on 2026-11-06 (`target/surefire-reports`, JDK 18).

## Where we stand

40 tests, 1 skipped, all green. Test classes take ~18 s summed (speed was handled in `test-speed.md`).

| Area | Java tests | Hurl (`examples/`) | Gap |
|---|---|---|---|
| Speakers: register, unique email, search | `SpeakersTest`, `ConferenceServiceTest` | `speakers.hurl` | `GET`/`PUT /speakers/{id}` have no Java test |
| Talks: submit, limit of 3, lookup | `TalksTest`, `ConferenceServiceTest` | `talks.hurl` | `submittedAt`/`submittedOn` exact format checked **only** by Hurl; `GET /talks` sort/filter has no Java test |
| Ratings | **none** | `ratings.hurl` (happy path) | Half-up average, ranking ties, `ratedAtDisplay` format, re-rating, concurrency: no Java test |
| Acceptance, co-speakers | `AcceptanceTest` (13), `CoSpeakersTest` (9) | yes | well covered, including races |
| Health / deploy contract | `HealthTest`, `HealthContractTest` | - | fine |
| `/admin/stats`, `/admin/talks.csv` | none | none | entirely untested (CSV uses `norwegianDate`, `csv`, `capitalize`) |

Findings in the existing tests:

- **Spring Boot 4 is released** (`specs/spring-boot-4.md`: "nothing else changes … formats included"). The upgrade
  means a newer Java and Jackson, which is exactly what breaks the legacy formats: JDK 20+ puts a narrow no-break space
  before `AM`/`PM` in `ratedAtDisplay` (`Utils.usShort`), and Jackson defaults decide `submittedAt`'s `+00:00` vs `Z`.
  Today only Hurl checks these, and `verify.sh` deliberately runs the service on JDK 11, so the local gate would
  **hide** a JDK 21 format break. Nothing in `./mvnw test` would catch it.
- `UtilsTest.roundsRatingToOneDecimal` pins **cut-off** rounding (4.25 → 4.2), while `specs/ratings.md` says half up
  (4.25 → 4.3). The talk average correctly uses half up (`ConferenceService.calculateAverage`); `Utils.roundRating`
  is only used by `/admin/stats`. The test reads like a spec rule it contradicts.
- `UtilsTest.nowIsCurrentTime` is `@Disabled("flaky on Jenkins")`: it compares two second-precision timestamps, so it
  fails when the second ticks over between them. We have no Jenkins.
- `Utils.MAX_TALKS = 5` is unused and contradicts the real limit of 3 (`checkSubmissionLimit`).
- `SpeakersTest` and `TalksTest` carry their own copy of the HTTP helper; the newer tests use `Api`.

## Steps (biggest win first)

1. **Pin the legacy formats in JUnit** (safety net for the Boot 4 upgrade). A `FormatsTest`: `Utils.usShort` for a
   fixed instant, at midnight, and that the character before `AM`/`PM` is an ordinary space (U+0020); `submittedAt`
   matches `yyyy-MM-dd'T'HH:mm:ss.SSS+00:00` exactly and `submittedOn` is its UTC date, through the real API;
   average `4,4,4,5` shows `4.3`. Gain: a format break shows up in `verify.sh --quick` in seconds, not as a
   FAULTY harness check. No production code.
2. **`RatingsTest`, rule by rule from `specs/ratings.md`**: re-rating keeps one rating per reviewer, score bounds,
   unknown talk/reviewer, ranking by rounded average, then count, then oldest, unrated last, concurrent ratings by one
   reviewer end as one row. Gain: the only base feature without Java tests. No production code.
3. **Fix the misleading `UtilsTest`s**: rewrite `nowIsCurrentTime` to parse `Utils.now()` and assert it's within
   2 s of the clock (not flaky), and drop `@Disabled`. Rename/comment the `roundRating` test as "admin stats only,
   cut off" or, if the product owner says stats should match the spec, change it (**PO?**, touches production).
4. **Run the Java tests on the JDK we deploy.** Surefire uses whatever `java` is on the PATH (JDK 18 here, Temurin
   11 in the Dockerfile). Make `verify.sh` pick the build JDK the same way it picks the run JDK, and keep the two in
   step with the `Dockerfile` when Boot 4 moves Java. Gain: tests see the same date formatting as production. Risk:
   needs the JDK installed; it falls back as today.
5. **Smoke tests for untested endpoints**: `GET`/`PUT /speakers/{id}`, `GET /talks` sort and filter,
   `/admin/stats`, `/admin/talks.csv` (header, `;` escaping, comma decimal, Norwegian date). One test each, as
   characterisation, so the Boot 4 upgrade can't silently change them.
6. **Tidy-up**: move `SpeakersTest`/`TalksTest` onto `Api` (~60 lines less), delete `Utils.MAX_TALKS` (unused
   production constant). Low value; do it only if touching those files anyway.

## Expected

About 15–25 new tests, roughly +2 s of test time (they share the container and run in existing-style classes).
Steps 1, 2 and 5 are the ones that de-risk Spring Boot 4.

## Risks

- Step 1's `usShort` test **fails on JDK 20+ by design**. That's the point: it must go green with the upgrade's fix,
  not be loosened.
- Step 3's rounding change and step 6's constant removal touch production code; everything else is tests only.
- Step 4 changes which JDK runs the tests locally; a machine without JDK 11 behaves as today.

## Result

| | Before | After |
|---|---|---|
| Tests | 40 (1 skipped) | 83 (0 skipped) |
| Test classes, summed | ~18 s | ~21 s (`RatingsTest`'s methods ~2 s; the rest is Spring start-up per class) |
| `verify.sh` build + tests (wall) | - | ~27 s |
| Hurl examples on the new jar | - | 5/5 files, 79 requests pass |
| Base features with Java tests per rule | speakers, talks (partly) | speakers, talks, ratings |
| Legacy formats pinned in `./mvnw test` | none | `ratedAtDisplay` (incl. the U+0020 before AM/PM), `submittedAt`/`submittedOn`, half-up average, CSV date/quoting |

Checked that the new tests bite: switching the talk average to the cut-off rounding fails
`FormatsTest.averageRatingRoundsHalvesUp` and `RatingsTest.rankingUsesTheRoundedAverageThenTheCountThenAge`.

What changed per step:

1. `FormatsTest` (12 tests).
2. `RatingsTest` (13 tests).
3. `UtilsTest.nowIsCurrentTimeInUtc` replaces the disabled test (brackets the clock, no race);
   `adminStatsRatingIsCutOffToOneDecimal` says it's the admin stats only. **PO?** should `/admin/stats` round half up
   like the spec? Not changed.
4. `verify.sh` reads the JDK version from the `Dockerfile` and builds, tests and runs on it when installed; it warns
   when it isn't. **This laptop only has JDK 18, so it warns**: install Temurin 11 (and the Boot 4 target JDK later)
   for this step to take effect.
   On `spring-boot-4-tests` this is superseded by the upgrade's own `verify.sh` (JDK from `pom.xml`, fails without
   it), which does the same, stricter.
5. `SpeakersTest` (+`GET`/`PUT /speakers/{id}`), `TalksTest` (+formats, lengths, capitals, lookup, own list order,
   4th talk), `AdminReportsTest` (stats and the exact CSV).
6. `SpeakersTest` and `TalksTest` use `Api`; `Utils.MAX_TALKS` removed (unused).

Found along the way, not fixed (production code, needs a decision):

- **SQL injection in `/speakers/search`** (`SpeakerDao.search` concatenates `q` into the SQL): `q=O'Brien` answers
  500, `q=%' OR '1'='1` returns every speaker. Fix: a bind parameter (`ILIKE ?` with `'%' + q + '%'`), plus a test.
- `verify.sh`'s Compose step fails while another worktree's Postgres holds port 5432 (`team-6-postgres-1`).

Next possible win: run `./mvnw test` on JDK 21+ once (e.g. in the Boot 4 work); `FormatsTest.ratedAtDisplayHasAnOrdinarySpaceBeforeAmPm`
is expected to fail there until `Utils.usShort` pins the format.
