# CFP service — design

The single, combined description of what this service does. Rules are numbered from **D1000**.

## What this document is

`specs/` holds **inputs**: the organisers' feature requests and change requests, copied verbatim,
never edited by us. Each one describes a single feature in isolation, at the moment it was
released.

This file is the **check document**: one coherent description of the whole service, assembled from
every spec plus the decisions we made (status codes, edge cases, error handling) that no feature
request supplies. Where a feature request and this document disagree, the feature request wins and
this document is wrong and must be fixed.

**How to use it**

- *Implementing or changing something:* read this document for the whole picture, then the relevant
  `specs/<slug>.md` for the authoritative wording of the rules you are touching.
- *A new feature request arrives:* `/plan-feature` writes `workflow/specs/<slug>.md`, then its rules
  are folded in here with new D-numbers.
- *A change request arrives* (a spec whose `Version:` goes up): find the affected D-rules, change
  them, and note the change in the rule's row. `specs/<slug>.diff` shows what moved.

**Rule numbering.** D1000 and up, grouped by area, never reused and never renumbered — a D-number
is a stable handle for tests, commits and review comments. Gaps between groups leave room to insert.
Rules taken from a feature request cite it; rules that are our decision are marked **[ours]**.

**Staleness warning.** This document is derived. Seven specs exist today and more drip in during the
game. All seven are `Version: 1` today (the separate `V:` line is not a version; it looks like the
feature's weight). If `workflow/scripts/poll-specs.sh` reports a CHANGED
spec, assume this document is stale until someone has reconciled it.

## Source map

| Area | Rules | Feature request | Our decisions | Status |
|---|---|---|---|---|
| Speakers | D1000–D1099 | `specs/speakers.md` (Version 1) | — | base, claimed |
| Talks | D1100–D1199 | `specs/talks.md` (Version 1) | — | base, claimed |
| Ratings | D1200–D1299 | `specs/ratings.md` (Version 1) | — | base, claimed |
| Co-speakers | D1300–D1399 | `specs/co-speakers.md` (Version 1) | `workflow/specs/co-speakers.md` | released tick 0, claimed tick 70 |
| Acceptance | D1400–D1499 | `specs/acceptance.md` (Version 1) | `workflow/specs/acceptance.md` | released tick 20, unclaimed |
| Workshop registration | D1500–D1599 | `specs/workshop-registration.md` (Version 1) | `workflow/specs/workshop-registration.md` | released tick 67, implemented (not yet shipped), unclaimed |
| Platform / Spring Boot 4 | D1600–D1699 | `specs/spring-boot-4.md` (Version 1) | `workflow/specs/spring-boot-4.md` | released tick 40, claimed tick 98 |
| Non-functional requirements | D1700–D1799 | `AGENTS.md` (deploy contract, platform), `specs/spring-boot-4.md` | ours | — |
| Cross-cutting | D1900–D1999 | several | — | — |
| Unspecified endpoints | D1950–D1969 | none — in the code only | — | — |

## Domain model

```
Speaker ──1──< Talk >──*── Rating        (reviewer is just a number, not an entity)
   │             │
   └──*──────────┤ co-speaker (max 2, ordered)
                 │
                 ├── status: SUBMITTED → ACCEPTED | REJECTED | WAITLISTED | WITHDRAWN
                 │
                 └──< Registration       (only when format=WORKSHOP and status=ACCEPTED, max 20)
```

Dependency order, strict: **speakers → talks → ratings → acceptance → workshop-registration**.
Workshop registration needs `format=WORKSHOP` from talks *and* `status=ACCEPTED` from acceptance,
and accepting needs two distinct reviewers from ratings. Co-speakers hangs off talks only.
Spring Boot 4 is orthogonal — it must change nothing above.

### The Talk view

`Talk` accretes fields from four features. **Every response that returns a Talk returns all of
them** (D1900). Getting this wrong is the most likely way to break a passing base check.

| Field | From | Notes |
|---|---|---|
| `id`, `title`, `format`, `level`, `speakerId` | talks | |
| `durationMinutes` | talks | derived from `format`, never supplied by the client |
| `status` | talks + acceptance | `SUBMITTED` at creation; acceptance widens the enum |
| `submittedAt`, `submittedOn` | talks | exact string formats, see D1150–D1151 |
| `averageRating`, `ratingCount` | ratings | `null` / `0` when nobody has rated |
| `coSpeakerIds` | co-speakers | `[]` when none, in insertion order |

Talk is returned by: `POST /talks`, `GET /talks/{id}`, `GET /talks` (all variants),
`GET /speakers/{id}/talks`, `PUT /talks/{id}/co-speakers/{speakerId}`, `DELETE` of the same,
`PUT /talks/{id}/status`, `POST /talks/{id}/withdraw`.

---

## D1000–D1099 Speakers

Source: `specs/speakers.md` (Version 1). Base feature, claimed, all checks OK.

| # | Rule |
|---|---|
| D1000 | A speaker registers with a name and an email; bio optional. `POST /speakers` → `201`. |
| D1001 | Name and email are required and must be real (non-blank). |
| D1002 | A bio is at most **500 characters** — counted in characters, not UTF-16 units (fixed in `156f85a`). |
| D1003 | One email is one speaker, compared case-insensitively: `ada@example.com` = `ADA@Example.com`. |
| D1004 | A profile reads back exactly as registered, including the email's original letter case. |
| D1005 | Registrations survive restart and redeploy (the database persists). |
| D1006 | Trailing-slash URLs (`/speakers/`, `/speakers/{id}/`) behave identically to the versions without. Legacy clients depend on this. |

## D1100–D1199 Talks

Source: `specs/talks.md` (Version 1). Base feature, claimed, all checks OK.

| # | Rule |
|---|---|
| D1100 | A speaker submits a talk with title, format and level. `POST /talks` → `201`. |
| D1101 | Every talk belongs to an already-registered speaker and has a real title. |
| D1102 | `durationMinutes` is decided by `format` alone — the client never picks it: `LIGHTNING_10`→10, `LIGHTNING_20`→20, `PRESENTATION`→45, `WORKSHOP`→120. |
| D1103 | Formats and levels are exactly these capitalised strings; nothing else is valid. Levels: `BEGINNER`, `INTERMEDIATE`, `ADVANCED`. |
| D1104 | A new talk is `SUBMITTED`. |
| D1105 | A speaker may have at most **3** talks. Must hold under a concurrent submission rush (see D1901). |
| D1106 | **[amended by acceptance PO-2]** The 3-talk limit counts only talks that are not `WITHDRAWN` or `REJECTED`. |
| D1107 | A speaker can list their own talks, oldest first. |
| D1150 | `submittedAt`: UTC, `yyyy-MM-dd'T'HH:mm:ss.SSS+00:00` — always milliseconds, offset always `+00:00`, never `Z`. Legacy tools parse it as a plain string. |
| D1151 | `submittedOn`: the UTC date of `submittedAt`, `yyyy-MM-dd`. |

## D1200–D1299 Ratings

Source: `specs/ratings.md` (Version 1). Base feature, claimed, all checks OK.

| # | Rule |
|---|---|
| D1200 | A reviewer is just the positive integer in the URL — reviewers are not registered anywhere. |
| D1201 | A score is a whole number 1–5. Nothing else is a score. |
| D1202 | At most one rating per reviewer per talk; rating again replaces the score. `PUT /talks/{id}/ratings/{reviewerId}` → `200`. |
| D1203 | Every talk shows `ratingCount` and `averageRating` **wherever a talk is shown** (see D1900). |
| D1204 | `averageRating` is rounded to one decimal, halves up: `4,4,4,5` → 4.25 → `4.3`. An unrated talk has no average. |
| D1205 | `GET /talks` lists all talks oldest first; `?speakerId=` filters to one speaker. |
| D1206 | `?sort=rating` ranks by rounded average desc, then by `ratingCount` desc, then oldest first. Unrated talks last. |
| D1207 | Concurrent ratings on one talk: no rating lost, nobody counted twice, double-clicks safe (see D1901). |
| D1250 | `ratedAtDisplay`: time of the latest rating, UTC, US short format `M/d/yy, h:mm AM` — no leading zeros on month/day/hour, 2-digit year, 12-hour clock, **exactly one ordinary space** before AM/PM. E.g. `11/4/26, 1:05 PM`, `11/4/26, 12:00 AM`. See D1601 — this collides with Spring Boot 4. |

## D1300–D1399 Co-speakers

Source: `specs/co-speakers.md` (Version 1), decisions in `workflow/specs/co-speakers.md`.
Released tick 0, **claimed tick 70**.

| # | Rule |
|---|---|
| D1300 | A talk keeps its one main speaker (`speakerId`). Co-speakers are other registered speakers, added afterwards and removable. |
| D1301 | At most **two** co-speakers per talk. |
| D1302 | The main speaker is never a co-speaker of their own talk — never appears in `coSpeakerIds`. |
| D1303 | `coSpeakerIds` in insertion order; `[]` when none. |
| D1304 | Adding an existing co-speaker changes nothing — including when the talk already has 2. |
| D1305 | `GET /speakers/{id}/talks` and `?speakerId=` show only talks where they are the **main** speaker. |
| D1306 | Co-speaking never counts towards the 3-talk limit; a speaker at the limit can still co-speak. |
| D1307 | Removed then re-added goes to the **end** of the order. **[ours]** |
| D1350 | **[ours]** PUT ok → `200` + full Talk. Already a co-speaker → `200` unchanged. Talk or speaker unknown → `404`. Speaker is the main speaker → `400`, talk unchanged, also when it already has 2 (PO-2: `409` was FAULTY ticks 71–89, `200` still FAULTY at tick 90, `400` passes `co-speakers/check-2`). Already 2 others → `409`. |
| D1351 | **[ours]** DELETE ok → `200` + full Talk. Not a co-speaker, incl. unknown speaker → `404` unchanged (PO-1). Talk unknown → `404`. |
| D1352 | **[ours]** Non-numeric path id → `400` (Spring type mismatch). |
| D1353 | Concurrency: PUT locks the talk row `FOR UPDATE`, then counts and inserts in one transaction. Unique `(talk_id, speaker_id)` with `ON CONFLICT DO NOTHING` covers D1304 under double-clicks. |

## D1400–D1499 Acceptance

Source: `specs/acceptance.md` (Version 1), decisions in `workflow/specs/acceptance.md`.
Released tick 20, **unclaimed**, implemented.

| # | Rule |
|---|---|
| D1400 | The committee decides: `ACCEPTED`, `REJECTED` or `WAITLISTED` via `PUT /talks/{id}/status`. |
| D1401 | `WAITLISTED` can still become accepted or rejected. `ACCEPTED` and `REJECTED` are **final**. |
| D1402 | Accepting requires ratings from at least **two different reviewers** — a reviewer who rated twice is one reviewer. Rejecting and waitlisting need none. |
| D1403 | The talk's own speaker may withdraw it, unless it is `REJECTED` or `WITHDRAWN`. |
| D1404 | Nobody else may withdraw it. |
| D1405 | `WITHDRAWN` is out for good — no decision or withdrawal afterwards. |
| D1406 | The current status shows wherever a talk is shown (see D1900). |
| D1407 | A disallowed decision or withdrawal changes nothing. |
| D1408 | Concurrent decisions never leave the talk in a state the rules disallow (see D1901). |

**Transitions** (a repeat of the current status is not a transition: `409`, unchanged. PO-1 said `200`; the harness overruled it, tick 111):

| From \ To | ACCEPTED | REJECTED | WAITLISTED | WITHDRAWN (speaker) |
|---|---|---|---|---|
| SUBMITTED | ✔ if ≥2 reviewers | ✔ | ✔ | ✔ |
| WAITLISTED | ✔ if ≥2 reviewers | ✔ | ✘ 409 | ✔ |
| ACCEPTED | ✘ 409 | ✘ 409 | ✘ 409 | ✔ |
| REJECTED | ✘ 409 | ✘ 409 | ✘ 409 | ✘ 409 |
| WITHDRAWN | ✘ 409 | ✘ 409 | ✘ 409 | ✘ 409 |

| # | Rule **[ours]** |
|---|---|
| D1450 | Allowed decision → `200` + full Talk. Disallowed from current status → `409` unchanged. Repeat of current → `409` unchanged (PO-1 overruled by the harness, tick 111). `ACCEPTED` with <2 distinct reviewers → `409`. |
| D1451 | Bad `status` in body (missing, non-string, or not one of the three — including `SUBMITTED`, `WITHDRAWN`, lower case) → `400`. Malformed/absent JSON → `400`. Non-JSON Content-Type → `415`. |
| D1452 | Withdraw own talk, allowed → `200` + Talk `WITHDRAWN`. Withdraw a `REJECTED`/`WITHDRAWN` talk → `409`. |
| D1453 | Withdraw by another speaker, or an unregistered/too-large speaker id → `403` (PO-3). Bad `speakerId` (missing, null, non-integer incl. `2.0`/`true`/`"2"`, or <1) → `400`. |
| D1454 | Unknown talk → `404`. Non-numeric path id → `400`. |
| D1455 | **Check order:** `400` body → `404` talk → `403` ownership → `409` state/ratings. |
| D1456 | Concurrency: one `@Transactional` method locking the talk row `FOR UPDATE`, then checking transition and reviewer count. Ratings are never deleted, so the count cannot drop after the check. No advisory locks. |
| D1457 | Withdrawn and rejected talks still appear in `GET /talks` and speaker lists, can still be rated, and can still get co-speakers. |

**PO answers on record:** PO-1 repeat decision → `200` unchanged, overruled by the harness (check-3/check-5, ticks 108-110): `409` from tick 111. PO-2 withdrawn/rejected talks
don't count towards the 3-talk limit (amends D1106). PO-3 unregistered withdrawer → `403`.

## D1500–D1599 Workshop registration

Source: `specs/workshop-registration.md` (Version 1), decisions in
`workflow/specs/workshop-registration.md` (PO-1..4 answered tick 101). Released tick 67, implemented,
**unclaimed**. Code: `WorkshopController`, `WorkshopService`, `RegistrationDao`, `Registration` (record).

| # | Rule |
|---|---|
| D1500 | Attendees register for a workshop with name and email, both required. `POST /workshops/{id}/registrations` → `201`. |
| D1501 | `{id}` is the **talk id**. Only talks with `format=WORKSHOP` **and** `status=ACCEPTED` take registrations. |
| D1502 | Capacity is exactly **20** per workshop, "and never more". Registration opens to everyone at once — expect a rush (see D1901). |
| D1503 | One registration per email per workshop, compared **case-insensitively** (as D1003). |
| D1504 | The response echoes the email **as the attendee typed it**: stored as typed; uniqueness on `lower(email)` (unique index `(talk_id, lower(email))`). |
| D1505 | The same person may register for several different workshops. |
| D1506 | If a workshop is withdrawn later, everyone already registered **stays** on its list. |
| D1507 | `GET /workshops/{id}/registrations` → `200`, the list in registration order. |
| D1508 | `Registration` = `{ id, workshopId, name, email }`. |
| D1550 | **[ours]** `POST` new → `201` + Registration. `name`/`email` missing, null, not a string or blank → `400`. Body not a JSON object / no body → `400`. |
| D1551 | **[PO-4]** The email must look like an email: one `@`, no whitespace (Unicode too) or control characters, a domain of ≥2 non-empty dot-separated labels → else `400`. Not trimmed: `" ada@example.com "` → `400`. |
| D1552 | **[ours/PO-1]** Unknown talk id → `404` (POST and GET). A talk that isn't a `WORKSHOP` → `400` (POST and GET; precedent D1350). |
| D1553 | **[PO-3]** Email already registered for this workshop (any letter case) → `200` with the **existing** registration (email as first typed), nothing changes — also when the workshop is full or withdrawn. |
| D1554 | **[PO-2]** Workshop not `ACCEPTED` → `409`. Full (20) → `409` (precedent: limits D1301, the 3-talk limit). |
| D1555 | **[ours]** Check order: `400` body/email → `404` unknown → `400` not a workshop → `200` already registered → `409` not accepted → `409` full. |
| D1556 | **[ours]** Concurrency: one `@Transactional` that locks the talk row (`TalkDao.lockForUpdate`, the same lock as D14xx decisions and withdrawals), then duplicate, status and count, then insert. The unique index is a backstop mapped to `409`. Tested: 30 concurrent → 20 × `201` + 10 × `409`; 5 concurrent same-email → 1 × `201` + 4 × `200`, same id. |

Riskiest after claiming (no harness-verified precedent): D1553 (`200`) and D1554 not accepted
(`409`). Attendees are **not** speakers: no shared table, although D1503 uses the same rule as D1003.

## D1600–D1699 Platform / Spring Boot 4

Source: `specs/spring-boot-4.md` (Version 1), decisions in `workflow/specs/spring-boot-4.md` (which pins
today's behaviour as P1–P20). Released tick 40, **claimed tick 98**: Spring Boot 4.1.2 on
Java 25 (Temurin, LTS).

| # | Rule |
|---|---|
| D1600 | The service runs on Spring Boot 4.x on a Java version supported for it (≥17; the spec's example shows Java 25). |
| D1601 | **Nothing else changes for users** — every rule in this document keeps working, *formats included*. |
| D1602 | `GET /actuator/info` reports the real running versions (`versions.springBoot`, `springFramework`, `java`). Hard-coding or faking them breaks the rules of conduct. |

| D1650 | **[ours]** Every format a spec fixes (D1150, D1151, D1250, trailing slashes, JSON leniency) is written explicitly in our code, never left to JVM, locale or Jackson defaults. |
| D1651 | **[ours]** `spring.jackson.use-jackson2-defaults=true`: content after the JSON value is ignored, fields keep declaration order, dates keep `+00:00`. |
| D1652 | **[ours]** Trailing slashes work on every path and method (incl. `POST /speakers/`) by wrapping the request, not redirecting — Spring 6+ dropped trailing-slash matching. |
| D1653 | **[ours]** Error bodies keep their status codes and the legacy `message` and `errors` attributes; JSON responses stay `application/json;charset=UTF-8`. |

> **D1601 was a real collision, resolved by D1650.** D1250 requires one *ordinary* space before
> AM/PM; JDK 20+ locale data emits U+202F there. Other traps the upgrade hit: Boot properties
> renamed in 4.0 and *silently ignored* under the old names, Jackson 3 defaults, `javax` → `jakarta`,
> `-parameters` needed for `@PathVariable` names (every `/talks/{id}/…` route answered 500), and
> `submittedOn` a day early east of UTC. Full list in `workflow/specs/spring-boot-4.md`. Any future
> platform change must keep `UpgradeContractTest` and `examples/spring-boot-4.hurl` green.

## D1700–D1799 Non-functional requirements

How the service must be built and run, as opposed to what it does. Sources: the organisers'
deploy contract and platform notes in `AGENTS.md` (restated here, which is authoritative when they
differ), `specs/spring-boot-4.md`, and our own decisions. `.claude/rules/workflow.md` points here
instead of repeating these.

**Runtime**

| # | Requirement |
|---|---|
| D1700 | Java **25** (Temurin, LTS) and Spring Boot **4.1.2**, for build and runtime. `pom.xml` `java.version` and both `Dockerfile` stages use the same major version. Changing either is a platform change under D1601. **[ours]** |
| D1701 | Local runs and the gate use the production JDK: `verify.sh` picks it from `pom.xml` (override `JAVA_HOME_OVERRIDE`). Different JDKs format dates differently (D1250, D1650), so a green gate on another JDK proves nothing. **[ours]** |
| D1702 | Every format a spec fixes is produced explicitly in our code, never by JVM, locale or Jackson defaults (D1650, D1903). |
| D1703 | Reported versions are real, never hard-coded (D1602). |

**Deploy contract** (`AGENTS.md`; a push to `main` deploys)

| # | Requirement |
|---|---|
| D1710 | `Dockerfile` at the repo root, built for `linux/amd64`, takes `--build-arg GIT_SHA` and bakes it in. |
| D1711 | Listens on port **8080**. |
| D1712 | `GET /health` per D1950: `200` with the SHA only if the database answers within 5 s, else `503 db-unavailable`. |
| D1713 | Database from `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`; `PGSSLMODE=require` in the image, off for local Compose. Data persists across deploys; the filesystem does not. No other external dependencies. |
| D1714 | One instance, may scale to zero (expect cold starts). Beyond **80** concurrent requests the platform answers `503` itself. |

**Database**

| # | Requirement |
|---|---|
| D1720 | Connection pool capped at **5** (`spring.datasource.hikari.maximum-pool-size`). Hold transactions briefly; a slow transaction starves every other request. |
| D1721 | **No advisory locks** (not guaranteed on this database). Use row locks (`SELECT … FOR UPDATE`) or conditional writes. |
| D1722 | Session settings use `SET LOCAL` inside a transaction; a plain `SET` leaks to the next user of the pooled connection. |
| D1723 | Schema changes go at the bottom of `schema.sql` with `IF NOT EXISTS`: it runs on every start against a database that keeps its data. **[ours]** |
| D1724 | A rule that must hold under concurrency (D1901) locks the contended row inside `@Transactional` **and** has a database constraint as backstop. **[ours]** |

**Code structure** **[ours]**

| # | Requirement |
|---|---|
| D1730 | Controller → `ConferenceService` → DAO (`JdbcTemplate`) → `schema.sql`. Errors are `ResponseStatusException` with the D1904 status codes. |
| D1731 | Every Talk-returning path ends in `getTalk`/`enrich(TalkDto)`, which adds the cross-feature fields (D1900). A new Talk field is added in `enrich`, not per endpoint. |

**Observability and verification** **[ours]**

| # | Requirement |
|---|---|
| D1740 | `RequestLoggingFilter` logs every request: method, path and query, status, duration, request and response body. It is how we see what the harness sends (`.github/fagdag/logs.sh`); the base app logged nothing. |
| D1741 | Every D-rule has a JUnit test and a Hurl entry; concurrency rules have a concurrent test (`Api.concurrently`), which samples races rather than proving their absence. |
| D1742 | Nothing ships without `workflow/scripts/verify.sh` green (`./mvnw package`, then every `examples/*.hurl` against a local service on Compose Postgres). |
| D1743 | No secrets in the repo or in Actions; `.fagdag/harness-token` is never printed or committed. |

## D1900–D1999 Cross-cutting

| # | Rule |
|---|---|
| D1900 | **Every response returning a Talk returns the complete Talk view** — all fields in the table above, from every feature. "Wherever a talk is shown" appears in ratings, co-speakers and acceptance; it means all eight endpoints listed there. |
| D1901 | **Four specs independently demand correctness under concurrent writes:** D1105 (submission rush), D1207 (reviewers double-clicking), D1408 (committee racing), D1502 (registration rush). With a 5-connection pool and **no advisory locks**, the pattern is `SELECT … FOR UPDATE` on the contended row inside `@Transactional`, plus a database constraint as backstop. A constraint is the only unconditional proof; locks are only as good as the code around them. |
| D1902 | **Case-insensitive email, specified twice:** D1003 (speakers) and D1503 (attendees). Same comparison rule, separate tables, and both must return the original casing (D1004, D1504). |
| D1903 | **Exact string formats are contractual** — D1150, D1151, D1250. Legacy clients parse them as plain strings. Never let a locale or JDK default decide them. |
| D1904 | Error-code conventions, as settled in acceptance and co-speakers **[ours]**: `400` malformed input, `403` wrong actor, `404` unknown entity, `409` rule violation / illegal state, `415` wrong Content-Type. Validate body → entity exists → authorisation → state. |
| D1905 | A feature request gives business rules and API surface only. Status codes, edge cases and error handling are ours to decide and write down (AGENTS.md). |

## D1950–D1969 Endpoints with no spec

These exist in the code and are covered by **no** feature request. They are not free to change: a
harness check may exercise them, and the base features' checks pass today.

| # | Endpoint | Note |
|---|---|---|
| D1950 | `GET /health` | **Deploy contract**, not a spec: `200 {"status":"ok","version":"<GIT_SHA>"}`, or `503 {"status":"db-unavailable",...}` when the database doesn't answer within 5 s. |
| D1951 | `GET /admin/stats` | Statistics incl. `byStatus` — picks up new acceptance statuses automatically. |
| D1952 | `GET /admin/talks.csv` | CSV report. |
| D1953 | `GET /speakers/search?q=` | Speaker search. |
| D1954 | `PUT /speakers/{id}` | Speaker update — note `specs/speakers.md` describes no update endpoint. |
| D1955 | `GET /actuator/info` | Version reporting; becomes contractual under D1602. |

## Evidence

| Area | JUnit | Hurl |
|---|---|---|
| Speakers | `SpeakersTest`, `SpeakerFormTest` | `examples/speakers*.hurl` |
| Talks | `TalksTest` (incl. concurrent 3-talk limit) | `examples/talks.hurl` |
| Ratings | `RatingsTest` (incl. many reviewers at once, double-click), `ConferenceServiceTest` | `examples/ratings.hurl` |
| Formats | `FormatsTest`, `UtilsTest` (byte-exact `ratedAtDisplay`) | — |
| Admin reports | `AdminReportsTest` | — |
| Co-speakers | `CoSpeakersTest` (10 concurrent PUTs → exactly 2) | `examples/co-speakers.hurl` |
| Acceptance | `AcceptanceTest` (3×9 concurrent decisions; withdraw racing accept) | `examples/acceptance.hurl` |
| Health | `HealthTest`, `HealthContractTest` | — |
| Workshop registration | none yet | none yet |
| Spring Boot 4 | `UpgradeContractTest` (P1–P4, P11, P12, P15–P20) | `examples/spring-boot-4.hurl` |

Gate: `workflow/scripts/verify.sh` — `./mvnw package`, then every `examples/*.hurl` against a local
service on Compose Postgres. All four concurrency rules in D1901 now have a test.
