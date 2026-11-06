# Upgrade to Spring Boot 4.x: our spec

Feature request: `specs/spring-boot-4.md` (V1, released at tick 40). The request gives the business
rules; the decisions below are ours. Open questions for the product owner are marked **PO?**.

This is a change with no new API. Its risk is that it breaks the three base features at once, and those
are claimed and earning. So most of this spec is a list of today's behaviour we pin with tests
**before** the upgrade.

## Rules (from the feature request)

| # | Rule |
|---|---|
| R1 | The service runs on Spring Boot 4 (latest 4.x), on a Java version supported for it. |
| R2 | Nothing changes for users: everything in the Speakers, Talk submissions and Ratings specs keeps working exactly as today, **formats included**. |
| R3 | `GET /actuator/info` reports the running versions (`versions.springBoot`, `springFramework`, `java`) truthfully. No hard-coded or faked values. |

Implied by the deploy contract (not in the request, but breaking it fails the deploy):

| # | Rule |
|---|---|
| R4 | `GET /health` keeps its contract: 200 `{"status":"ok","version":"<GIT_SHA>"}`, 503 `db-unavailable` within 5 s. |
| R5 | Co-speakers (deployed, about to be claimed) keeps working: `examples/co-speakers.hurl` and `CoSpeakersTest` stay green. |

## Our decisions

| Decision | Answer | Why |
|---|---|---|
| Spring Boot version | **4.1.2** (latest GA on Maven Central at tick 40; 4.2 is still RC) | "latest 4.x is fine", and an RC isn't a release |
| Java version | **25** (Temurin 25, LTS) for build and runtime | Supported by Boot 4, LTS, and the request's own example is `25.0.5` |
| Date formats | Written by **explicit patterns** in our code, never by JVM, locale data or Jackson defaults | Today's formats come from Java 11 and Jackson 2 defaults, which both change in this upgrade (see the traps below) |
| Trailing slashes | Keep today's behaviour: a trailing slash works on **every** path, for every method (including `POST /speakers/`), by wrapping the request rather than redirecting it | Spring 6+ dropped trailing-slash matching; a redirect would break POST. The harness sends `POST /speakers/` and `GET /speakers/{id}/` |
| JSON input leniency | Keep: unknown properties ignored, Java comments allowed (`spring.jackson.parser.allow-comments` today) | Legacy clients, and "nothing changes" |
| JSON output | Keep: nulls are written (`"bio": null`, `"averageRating": null`), field order as today | Jackson 3 changes some defaults (for example, alphabetical property order) |
| Error bodies | Status codes must not change, and the legacy `message` and `errors` attributes stay (P17). We don't pin the rest of the error body | **PO?** Does "formats included" also cover error bodies and JSON field order? We assume only the documented fields |
| `/actuator/info` | Unchanged code (`VersionInfoContributor` reads `SpringBootVersion`, `SpringVersion`, `java.version`), so it's truthful by construction | R3, and the rules of conduct |
| Rollout | On a branch. Merge to `main` only when every pinning test passes on both the old and the new stack | A bad deploy breaks all claimed features at once |

## Upgrade traps (why the pinning is needed)

| Trap | Today (Boot 2.7, Java 11, Jackson 2) | After (Boot 4.1, Java 25, Jackson 3) |
|---|---|---|
| `ratedAtDisplay` | `DateFormat.SHORT`, `Locale.US` gives `11/4/26, 1:05 PM` | JDK 20+ locale data (CLDR 42) puts a **narrow no-break space** (U+202F) before `PM`, which the spec forbids |
| Trailing slash | Matched by default | Not matched: 404 |
| `submittedAt` | Jackson 2 `StdDateFormat` writes `…SSS+00:00` | Jackson 3 date handling differs; must not become `Z` or lose milliseconds |
| `submittedOn` (`java.sql.Date`) | `yyyy-MM-dd` | Jackson 3 serialization not guaranteed the same |
| `javax.*` | `javax.servlet`, `javax.validation` | `jakarta.*` (`RequestLoggingFilter`, `SpeakerController`, `SpeakerForm`) |
| Jackson properties | `spring.jackson.parser.allow-comments` | Property names changed for Jackson 3 |
| **Renamed Boot properties** (found by the review agent) | `server.error.include-message`, `server.error.include-binding-errors`, `server.servlet.encoding.force` | Renamed to `spring.web.error.*` and `spring.servlet.encoding.force` in 4.0; the old names are **silently ignored**: `message` and `errors` vanished from error bodies, and the charset from `Content-Type` |
| **Jackson 3 defaults** (found by the review agent) | Trailing content ignored, declaration order, dates with `+00:00` | Trailing content gives 400, alphabetical order, `Z`. We set `spring.jackson.use-jackson2-defaults=true` |
| Testcontainers | `org.testcontainers:postgresql` 1.21 | `testcontainers-postgresql` 2.0, new package |
| **Path variable names** (found by the gate, not foreseen) | Spring 5 read `@PathVariable` names from debug info | Spring 6.1+ needs `-parameters`; `spring-boot-starter-parent` sets it, but we import the BOM, so every `/talks/{id}/…` route answered **500**. Fixed in the compiler plugin |
| **`submittedOn` day** (found by the P2 pin, not foreseen) | `java.sql.Date` at local midnight, written by Jackson 2 with `toString()` | Formatted in UTC, it was **a day early** on any JVM east of UTC. Production runs in UTC so it never showed there, but it was fragile. Now a `String` computed in UTC (`Utils.utcDay`) |

## Behaviour we pin before upgrading

Each line is a test that must pass on 2.7 today **and** on 4.1 afterwards. Sources: the base specs and
what the harness sends (our request log, ticks 21-40).

| # | Behaviour | Source |
|---|---|---|
| P1 | `submittedAt` matches `yyyy-MM-dd'T'HH:mm:ss.SSS+00:00`, never `Z` | Talks spec |
| P2 | `submittedOn` is the UTC date of `submittedAt`, `yyyy-MM-dd` | Talks spec |
| P3 | `ratedAtDisplay` is `M/d/yy, h:mm AM`, with an ordinary space; `12:00 AM` at midnight, `1:05 PM` | Ratings spec |
| P4 | `GET /speakers/{id}/` returns 200, and `POST /speakers/` returns 201 | Speakers spec, harness |
| P5 | `POST /speakers` returns 400 for a missing, null, empty or whitespace-only (`" \t "`) name or email, or a 501-character bio | Speakers spec, harness |
| P6 | `POST /speakers` returns 409 for the same email in a different case | Speakers spec, harness |
| P7 | `POST /talks` returns 400 for a missing or null title or speakerId, lowercase `intermediate`, `KEYNOTE`, `EXPERT`, or an unknown speaker | Talks spec, harness |
| P8 | A 4th talk returns 409, also under concurrent submissions | Talks spec, harness |
| P9 | A rating returns 400 for a score of `0`, `-1`, `6`, `2.5`, `null` or `"3"`, or a reviewer id of 0 or below; 404 for an unknown talk | Ratings spec, harness |
| P10 | `GET /talks?sort=` with `RATING`, `id` or `score` returns 400 | Ratings spec, harness |
| P11 | `averageRating` rounds half up (4, 4, 4, 5 gives `4.3`), and is `null` (present, not missing) when unrated | Ratings spec |
| P12 | Ranking order: rounded average, then count, then oldest; unrated last | Ratings spec |
| P13 | An unknown speaker or talk returns 404; a non-numeric id returns 400 | Harness |
| P14 | `/health` contract (R4) | Deploy contract |
| P15 | `/actuator/info` `versions.springBoot` equals `SpringBootVersion.getVersion()` and `java` equals `java.version` | R3 |
| P16 | JSON with a `// comment` and an unknown property is accepted | Legacy (application.properties) |
| P17 | Error bodies keep `message` (e.g. `email already registered`, `talk not found`), validation 400s keep `errors` | Legacy ("the mobile client depends on these"); added after review |
| P18 | JSON responses are `application/json;charset=UTF-8` | Legacy (`encoding.force`); added after review |
| P19 | Field order of Speaker, Talk and Rating as today | Our decision; added after review |
| P20 | Content after the JSON value is ignored, as on Jackson 2 | Added after review |

## Effects on existing endpoints

None intended. Every endpoint's status codes, fields and formats stay as they are.

## Concurrency

No new rules. The existing row lock (`SELECT … FOR UPDATE` on the speaker) for the 3-talk limit, and the
rating upsert, must survive the upgrade; the existing concurrency tests in `TalksTest` and
`CoSpeakersTest` cover them.

## Evidence

- `examples/spring-boot-4.hurl`: P4-P13 and P16 against a running service (old and new).
- `UpgradeContractTest`: P1, P2, P3, P4, P11, P12, P15, P16 and P17-P20.
- `src/test/java/no/scienta/cfp/UtilsTest.java`: P3 with fixed instants (midnight, noon, 1:05 PM), byte-exact.
- `src/test/java/no/scienta/cfp/UpgradeContractTest.java`: P1, P2, P11, P15.
- `workflow/scripts/verify.sh` green on JDK 25, plus `GET /actuator/info` on the deployed service showing `4.1.2`.
