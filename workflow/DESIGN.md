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
- *A change request arrives* — either a spec whose `Version:` goes up, or a separate change-request
  spec such as `specs/ratings-cr.md` (`Kind: change request`, no `.diff`): find the affected D-rules, change
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
| Talks | D1100–D1199 | `specs/talks.md` (Version 1) + change request `specs/submission-cap-cr.md` (Version 1, released tick 180) | `workflow/specs/submission-cap-cr.md` (rules + PO-1..5) | base, claimed; change request unclaimed, not implemented, D1105/D1106 changed in place |
| Ratings | D1200–D1299 | `specs/ratings.md` (Version 1) + change request `specs/ratings-cr.md` (Version 1, released tick 167) | `workflow/specs/ratings-cr.md` | base, claimed; change request implemented (not yet shipped), unclaimed; D1204/D1206 changed in place |
| Co-speakers | D1300–D1399 | `specs/co-speakers.md` (Version 1) | `workflow/specs/co-speakers.md` | released tick 0, claimed tick 70; D1306 replaced and D1350 extended by submission-cap-cr |
| Acceptance | D1400–D1499 | `specs/acceptance.md` (Version 1) | `workflow/specs/acceptance.md` | released tick 20, claimed tick 107 |
| Workshop registration | D1500–D1599 | `specs/workshop-registration.md` (Version 1) | `workflow/specs/workshop-registration.md` | released tick 67, claimed tick 123 |
| Platform / Spring Boot 4 | D1600–D1699 | `specs/spring-boot-4.md` (Version 1) | `workflow/specs/spring-boot-4.md` | released tick 40, claimed tick 98 |
| Scheduling | D1800–D1899 | `specs/scheduling.md` (Version 1) | `workflow/specs/scheduling.md` | released tick 100, claimed tick 136 |
| Non-functional requirements | D1700–D1799 | `AGENTS.md` (deploy contract, platform), `specs/spring-boot-4.md` | ours | — |
| Cross-cutting | D1900–D1999 | several | — | — |
| Unspecified endpoints | D1950–D1969 | none — in the code only | — | — |
| Programme export | D2000–D2099 | `specs/programme-export.md` (Version 1) | `workflow/specs/programme-export.md` | released tick 133, being implemented, unclaimed |

## Domain model

```
Speaker ──1──< Talk >──*── Rating        (reviewer is just a number, not an entity)
   │             │
   └──*──────────┤ co-speaker (max 2, ordered)
                 │
                 ├── status: SUBMITTED → ACCEPTED | REJECTED | WAITLISTED | WITHDRAWN
                 │
                 ├──< Registration       (only when format=WORKSHOP and status=ACCEPTED, max 20)
                 │
                 └──0..1 Programme entry (room R1-R3 × slot S1-S5, 15 fixed places; only ACCEPTED)
                              │
                              └── read by Programme export (GET /programme, /programme.csv):
                                  entry + talk + speaker names, joined as they are now
```

Dependency order, strict: **speakers → talks → ratings → acceptance → workshop-registration**, and
**acceptance → scheduling → programme-export** (export also reads co-speakers and speaker names).
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
| `averageRating`, `ratingCount` | ratings (+ ratings-cr) | `null` / `0` when nobody has rated; average trimmed at ≥5 ratings, 2 decimals half-even (D1204) |
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
| D1105 | **[submission-cap-cr R1, R4]** A speaker may have at most **3** talks, counting talks where they are the main speaker **and** talks they co-speak on. Must hold under a rush of submissions and co-speaker invitations together (see D1354, D1901). *Was (talks V1): only their own talks.* |
| D1106 | **[acceptance PO-2, submission-cap-cr R2, R5, PO-4]** The limit counts only talks that are not `WITHDRAWN` or `REJECTED`, for the main speaker and the co-speakers alike; `SUBMITTED`, `WAITLISTED` and `ACCEPTED` count. Removing a co-speaker frees their place at once. Speakers already over the limit keep everything but get nothing new until below it. |
| D1107 | A speaker can list their own talks, oldest first. |
| D1150 | `submittedAt`: UTC, `yyyy-MM-dd'T'HH:mm:ss.SSS+00:00` — always milliseconds, offset always `+00:00`, never `Z`. Legacy tools parse it as a plain string. |
| D1151 | `submittedOn`: the UTC date of `submittedAt`, `yyyy-MM-dd`. |

## D1200–D1299 Ratings

Source: `specs/ratings.md` (Version 1), **changed by `specs/ratings-cr.md`** (change request, released tick 167; decisions in `workflow/specs/ratings-cr.md`). Base feature, claimed. D1204 and D1206 are changed in place; the old rule is kept in the row for reference.

| # | Rule |
|---|---|
| D1200 | A reviewer is just the positive integer in the URL — reviewers are not registered anywhere. |
| D1201 | A score is a whole number 1–5. Nothing else is a score. |
| D1202 | At most one rating per reviewer per talk; rating again replaces the score. `PUT /talks/{id}/ratings/{reviewerId}` → `200`. |
| D1203 | Every talk shows `ratingCount` and `averageRating` **wherever a talk is shown** (see D1900). |
| D1204 | **[ratings-cr R1, R2]** `averageRating`: with ≥5 ratings, leave out exactly one highest and one lowest score; then round to **2 decimals, half to even** (`3.125` → `3.12`, `3.375` → `3.38`; `4,4,4,5` → `4.25`). An unrated talk has no average. *Was (ratings V1): one decimal, halves up, `4.25` → `4.3`.* |
| D1205 | `GET /talks` lists all talks oldest first; `?speakerId=` filters to one speaker. |
| D1206 | `?sort=rating` ranks by the rounded average (D1204, so the trimmed 2-decimal one since ratings-cr R4) desc, then by `ratingCount` desc, then oldest first. Unrated talks last. |
| D1207 | Concurrent ratings on one talk: no rating lost, nobody counted twice, double-clicks safe (see D1901). |
| D1208 | **[ratings-cr R3, ours]** `ratingCount` counts every reviewer, also the two left out of the average. Calculation: `(sum − max − min) / (count − 2)` at ≥5 ratings, else `sum / count`; exact `BigDecimal` from the integer sums, scale 2, `HALF_EVEN`, so ties are decided on the exact value (`25/8 = 3.125` → `3.12`), never on a `double`. Exactly 5 ratings are trimmed; all-equal scores trim to the same value (5 × `4` → `4.00`). `/admin/stats` keeps its own average (D1951); `/admin/talks.csv` prints the new value (D1952). |
| D1209 | **[PO-2, tick 171]** JSON form: **always two decimals**, `4.00`, `4.50`, `4.25` (`averageRating` is a `BigDecimal` with scale 2). `/admin/talks.csv` prints `4,00`. |
| D1210 | **[PO-1, tick 171]** Switch-over: **the claim switches** the harness's ratings checks to the new rule. Until then they check the old one. So: push, wait until `/health` shows the new SHA, then a person claims ratings-cr straight away. Expect at most a tick of FAULTY ratings checks in between. |
| D1250 | `ratedAtDisplay`: time of the latest rating, UTC, US short format `M/d/yy, h:mm AM` — no leading zeros on month/day/hour, 2-digit year, 12-hour clock, **exactly one ordinary space** before AM/PM. E.g. `11/4/26, 1:05 PM`, `11/4/26, 12:00 AM`. The clash with Spring Boot 4's JDK locale data (D1601) is resolved by D1650: the format is written explicitly. |

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
| D1306 | **[submission-cap-cr R3, replaces co-speakers R7]** Co-speaking counts towards the limit (D1105). A speaker at the limit can't be added as co-speaker to a talk still in the running, but can be added to a `REJECTED` or `WITHDRAWN` talk. *Was (co-speakers V1): co-speaking never counts; a speaker at the limit can still co-speak.* A speaker already over the limit can also be added to a `REJECTED` or `WITHDRAWN` talk (PO-5). |
| D1307 | Removed then re-added goes to the **end** of the order. **[ours]** |
| D1350 | **[ours]** PUT ok → `200` + full Talk. Already a co-speaker → `200` unchanged. Talk or speaker unknown → `404`. Speaker is the main speaker → `400`, talk unchanged, also when it already has 2 (PO-2: `409` was FAULTY ticks 71–89, `200` still FAULTY at tick 90, `400` passes `co-speakers/check-2`). Already 2 others → `409`. **[submission-cap-cr]** Co-speaker at the limit, talk in the running → `409` (PO-1); already a co-speaker → still `200` unchanged even at or over the limit (PO-2); main speaker → still `400`, it wins over the limit (PO-3). |
| D1351 | **[ours]** DELETE ok → `200` + full Talk. Not a co-speaker, incl. unknown speaker → `404` unchanged (PO-1). Talk unknown → `404`. |
| D1352 | **[ours]** Non-numeric path id → `400` (Spring type mismatch). |
| D1353 | Concurrency: PUT locks the talk row `FOR UPDATE`, then counts and inserts in one transaction. Unique `(talk_id, speaker_id)` with `ON CONFLICT DO NOTHING` covers D1304 under double-clicks. |
| D1354 | **[submission-cap-cr R4, ours — not implemented]** The co-speaker PUT must also lock the **co-speaker's speaker row** `FOR UPDATE` before counting, always talk row → speaker row. Today a submission (speaker-row lock, D1105) and an invitation (talk-row lock, D1353) for the same speaker don't block each other, so both can take the last place. No cycle: submissions lock only the speaker row; decisions and withdrawals lock only the talk row and can only free places. No constraint can span the count; the lock is the argument. |

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
| D1552 | **[ours/PO-1]** Unknown talk id → `404` (POST and GET). A talk that isn't a `WORKSHOP`: `POST` → `409` (PO-1 `400` was FAULTY on `workshop-registration/check-2`, ticks 124–126), `GET` → `200 []` (our `400` was FAULTY on check-2 at tick 133). |
| D1553 | **[PO-3]** Email already registered for this workshop (any letter case) → `409`, nothing changes, also when full or withdrawn (as D1003's duplicate email). PO-3 `200` was FAULTY on `workshop-registration/check-3`, ticks 124–126. |
| D1554 | **[PO-2]** Workshop not `ACCEPTED` → `409`. Full (20) → `409` (precedent: limits D1301, the 3-talk limit). |
| D1555 | **[ours]** Check order: `400` body/email → `404` unknown → `409` not a workshop → `409` already registered → `409` not accepted → `409` full. |
| D1556 | **[ours]** Concurrency: one `@Transactional` that locks the talk row (`TalkDao.lockForUpdate`, the same lock as D14xx decisions and withdrawals), then duplicate, status and count, then insert. The unique index is a backstop mapped to `409`. Tested: 30 concurrent → 20 × `201` + 10 × `409`; 5 concurrent same-email → 1 × `201` + 4 × `409`. |

D1552 (POST `409`), D1553 and D1554 are harness-verified only once check-2 and check-3 are OK after the tick-126 fix. Attendees are **not** speakers: no shared table, although D1503 uses the same rule as D1003.

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
| D1700 | Java **25** (Temurin, LTS) and Spring Boot **4.1.2**, for build and runtime. `pom.xml` `java.version`, both `Dockerfile` stages and the `setup-java` step in `.github/workflows/deploy.yml` use the same major version; change them together (CI on Java 11 failed with "release version 25 not supported" after the upgrade). Changing the version is a platform change under D1601. **[ours]** |
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

## D1800–D1899 Scheduling

Source: `specs/scheduling.md` (Version 1), decisions in `workflow/specs/scheduling.md` (R1–R10).
Released tick 100. Open PO questions PO-?1..6 carry our defaults.

| # | Rule |
|---|---|
| D1800 | Fixed venue: rooms `R1`–`R3`; slots `S1` 09:00–10:00, `S2` 10:15–11:15, `S3` 11:30–12:30, `S4` 13:30–15:30, `S5` 15:45–16:45, the same in every room. |
| D1801 | `start`/`end` are `HH:mm`, 24-hour, zero-padded — string constants in `Programme.java` (D1702). |
| D1802 | `PUT /programme/{room}/{slot}` `{talkId}` puts a talk in; `GET` shows it; `DELETE` takes it out; a talk taken out can go elsewhere. Entry = `{room, slot, start, end, talkId}`. |
| D1803 | Only `ACCEPTED` talks. |
| D1804 | A `WORKSHOP` only in `S4`; every other format fits anywhere. |
| D1805 | One talk per room and slot; a talk on the programme only once (take it out to move it). |
| D1806 | Main speakers and co-speakers of all talks in one slot are all different people (compared as they are now, PO-?6). |
| D1807 | Rules are checked at PUT only; a talk changing later (withdrawn, co-speaker added) leaves the programme alone. |
| D1808 | Concurrent PUTs never break D1803–D1806 (see D1901). |
| D1850 | **[ours]** PUT ok → `200` Entry. Same talk into the place it already has → `200` unchanged (PO-?1; `409` was FAULTY on `scheduling/check-1`, tick 140 — unlike D1450's repeat). Rule violations D1803–D1806 → `409`. |
| D1851 | **[ours]** GET/DELETE of an empty place → `404` (PO-?2); DELETE ok → `204`, no body (PO-?3). |
| D1852 | **[ours]** Room or slot outside the venue (incl. lower case) → `404`, any method (PO-?4). |
| D1853 | **[ours]** `talkId` missing, null, not a whole number, or < 1 → `400`; no such talk (incl. too big for an id) → `400` (PO-?5, as an unknown `speakerId` in `POST /talks`). Malformed JSON → `400`, non-JSON → `415`. |
| D1854 | **[ours]** Check order: place `404` → body `400` → unknown talk `400` → `409`s. Exception: an unreadable body (malformed, non-JSON) is `400`/`415` before the place check. |
| D1855 | Concurrency: table `programme_entry` with the 15 places pre-inserted. PUT locks the slot's 3 rows `FOR UPDATE`, then the talk row; always in that order. Backstops: conditional write `talk_id IS NULL` (0 rows → `409`), `UNIQUE (talk_id)` (violation → `409`). Talk responses are unchanged (no programme fields). |

## D1900–D1999 Cross-cutting

| # | Rule |
|---|---|
| D1900 | **Every response returning a Talk returns the complete Talk view** — all fields in the table above, from every feature. "Wherever a talk is shown" appears in ratings, co-speakers and acceptance; it means all eight endpoints listed there. |
| D1901 | **Five specs independently demand correctness under concurrent writes** (six with submission-cap-cr R4, D1354): D1105 (submission rush), D1207 (reviewers double-clicking), D1408 (committee racing), D1502 (registration rush), D1808 (committee scheduling at once). With a 5-connection pool and **no advisory locks**, the pattern is `SELECT … FOR UPDATE` on the contended row inside `@Transactional`, plus a database constraint as backstop. A constraint is the only unconditional proof; locks are only as good as the code around them. |
| D1902 | **Case-insensitive email, specified twice:** D1003 (speakers) and D1503 (attendees). Same comparison rule, separate tables, and both must return the original casing (D1004, D1504). |
| D1903 | **Exact string formats are contractual** — D1150, D1151, D1250, D1801, and the programme CSV (D2006–D2010). Legacy clients parse them as plain strings. Never let a locale or JDK default decide them. |
| D1904 | Error-code conventions, as settled in acceptance and co-speakers **[ours]**: `400` malformed input, `403` wrong actor, `404` unknown entity, `409` rule violation / illegal state, `415` wrong Content-Type. Validate body → entity exists → authorisation → state. |
| D1905 | A feature request gives business rules and API surface only. Status codes, edge cases and error handling are ours to decide and write down (AGENTS.md). |

## D1950–D1969 Endpoints with no spec

These exist in the code and are covered by **no** feature request. They are not free to change: a
harness check may exercise them, and the base features' checks pass today.

| # | Endpoint | Note |
|---|---|---|
| D1950 | `GET /health` | **Deploy contract**, not a spec: `200 {"status":"ok","version":"<GIT_SHA>"}`, or `503 {"status":"db-unavailable",...}` when the database doesn't answer within 5 s. |
| D1951 | `GET /admin/stats` | Statistics incl. `byStatus` — picks up new acceptance statuses automatically. Its `averageRating` averages the talks' averages with its own rounding (cut off to one decimal). Its formula is not changed by ratings-cr, but its **inputs** are: the talks' trimmed two-decimal averages, so its value can move (two talks at `4,4,4,5`: was `4.3`, now `4.2`). No spec, so no check. |
| D1952 | `GET /admin/talks.csv` | CSV report for the committee's spreadsheet: `;`-separated, comma decimals, Norwegian dates, `text/csv; charset=UTF-8`. Its rating column prints the talk's average, so it follows ratings-cr (`4,25`, D1208). Its helper `Utils.csv` is **not RFC 4180** (quotes only on `;` or `"`, and *replaces* `"` with `'`): never reuse it for the programme CSV (D2008). |
| D1953 | `GET /speakers/search?q=` | Speaker search. |
| D1954 | `PUT /speakers/{id}` | Speaker update — `specs/speakers.md` describes no update endpoint. **Inconsistent with the speaker rules:** it accepts a 1000-character bio (D1002 says 500) and a whitespace-only name (only `""` is rejected; D1001 wants a real name), and uses the weaker `Utils.isValidEmail`. Since D2003 shows names "as they are right now", this endpoint is the one way a bad name reaches the programme. |
| D1955 | `GET /actuator/info` | Version reporting; becomes contractual under D1602. |

## D2000–D2099 Programme export

Source: `specs/programme-export.md` (Version 1, `V: 1`), decisions in
`workflow/specs/programme-export.md` (PO-1..4 answered tick 140). Released tick 133, being
implemented, unclaimed. Rules D2000–D2012 are from the feature request; D2050–D2056 are now decided
(answers in bold).

| # | Rule |
|---|---|
| D2000 | Two **read-only** views of the programme (D1800–D1855): `GET /programme` (JSON array of `ProgrammeRow`) and `GET /programme.csv` (CSV). Both `200`. |
| D2001 | **One row per scheduled talk, nothing else**: one row per filled place (D1802), no rows for the empty ones. Both views have the same rows **in the same order**. |
| D2002 | Order: earliest start first, then by room `R1`, `R2`, `R3` within a slot. Slot order S1→S5 *is* start order (`Programme.SLOTS`), and rooms follow `Programme.ROOMS`. |
| D2003 | Each row: `slot`, `start`, `end` (as D1801), `room`, `talkId`, `title`, `format`, `speakers`. Talk and speaker data **as they are right now**, read when the request runs, not when the talk was scheduled. |
| D2004 | `speakers` are **names**: the main speaker first, then the co-speakers in the order they were added (D1303). JSON: an array of strings. |
| D2005 | JSON `ProgrammeRow` field order as the spec's schema: `slot, start, end, room, talkId, title, format, speakers`. This differs from the scheduling `Entry` (`room, slot, …`), so build it explicitly; don't reuse `Programme.entry`. |
| D2006 | CSV: UTF-8 (**no BOM**: it must *start* with the header), served as `text/csv` **even when the client asks for JSON**. |
| D2007 | CSV header, exactly: `slot,start,end,room,talkId,title,format,speakers`. |
| D2008 | CSV fields per RFC 4180: quoted **only** when they contain a comma, a `"` or a line break (`\n` or `\r`); a `"` inside is doubled (`"Kotlin, ""really"" fast"`); everything else written as it is (no trimming, no escaping). |
| D2009 | CSV line endings are `\n` (**not** RFC 4180's `\r\n`: the spec overrides the RFC here). Every line, the last included, ends in `\n`; no blank line at the end. A line break *inside* a quoted field is kept as it is. |
| D2010 | CSV `speakers` field: the names joined by `"; "` (semicolon, space), e.g. `Grace Hopper; Ada Lovelace`. |
| D2011 | Empty programme: JSON `[]`; CSV the header line alone (`slot,…,speakers\n`). |
| D2012 | No change to any other endpoint, and no new field on the Talk view (D1900). |

**Open questions** (for `/plan-feature` and probably the PO; our proposed default in brackets):

| # | Question |
|---|---|
| D2050 | **Withdrawn talks on the programme.** D1807 keeps a talk on the programme when it is withdrawn later, and D2001 says "one row per scheduled talk, nothing else". So the printed programme and the room signs show a withdrawn talk. **Decided (PO-1): include it, as the spec says.** |
| D2051 | **Two rooms at once, after the fact.** D1806 checks "nobody speaks in two rooms at once" only at PUT. A co-speaker added later (D1807 allows it) can make the export show one person in two rooms in the same slot. **Decided: show it as it is now; no re-check.** |
| D2052 | **`text/csv` vs `text/csv;charset=UTF-8`.** `spring.servlet.encoding.force=true` (D1653) adds `;charset=UTF-8`. The spec says "served as `text/csv`" and "UTF-8". **Decided (PO-2): keep `;charset=UTF-8`.** |
| D2053 | **Status codes for a bad `Accept`.** The CSV must ignore `Accept` (D2006). Should `GET /programme` with `Accept: text/csv` be a `406` or JSON anyway? **Decided (PO-3): `406` when the client won't take JSON (`produces = application/json`); no `Accept`, `*/*` or JSON → JSON.** |
| D2054 | **A `"; "` inside a speaker name** makes the CSV `speakers` field ambiguous. Names are free text (D1000). **Decided: write as is.** |
| D2055 | **Formula-looking fields** (`=`, `+`, `-`, `@` first) are a CSV-injection risk in spreadsheets, but D2008 says fields are written as they are. **Decided: write as is.** |
| D2056 | `Content-Disposition` (download filename) is not specified. **Decided (PO-4): `Content-Disposition: attachment; filename=programme.csv`.** |

**Design notes** (constraints `/plan-feature` must respect):

| # | Note |
|---|---|
| D2080 | **Content negotiation trap.** `@GetMapping(value = "/programme.csv", produces = "text/csv")` makes Spring answer `406` to `Accept: application/json`, breaking D2006. Set the `Content-Type` header on the `ResponseEntity` instead (as `/admin/talks.csv` does): a preset content type skips negotiation. |
| D2081 | **One ordering, two writers.** Build the row list once (`ConferenceService`, D1730), then write it as JSON or CSV, so D2001's "same order" can't drift. |
| D2082 | **No N+1.** Read entries, talks, main speakers and co-speakers in a fixed number of queries (as the talk lists do), not one speaker lookup per row like `/admin/talks.csv` (D1720: pool of 5). |
| D2083 | Read-only, so no locks; a read under concurrent scheduling sees a consistent snapshot if it runs in one transaction (`@Transactional(readOnly = true)`), which keeps JSON and CSV built from the same state. |
| D2084 | Trailing slashes (`/programme/`, `/programme.csv/`) work through D1652. `/programme` doesn't clash with `/programme/{room}/{slot}` (different segment counts). |
| D2085 | `start`/`end` come from `Programme.SLOTS` (D1801), the same constants as scheduling, so the two features can't disagree. |
| D2086 | Titles can hold commas, quotes and line breaks (only a blank title is rejected, D1101), so D2008 quoting is exercised by real data, not just edge cases. Test with a title containing `,`, `"`, `\n` and `\r\n`, and a non-ASCII name (UTF-8). |

## Evidence

| Area | JUnit | Hurl |
|---|---|---|
| Speakers | `SpeakersTest`, `SpeakerFormTest` | `examples/speakers*.hurl` |
| Talks | `TalksTest` (incl. concurrent 3-talk limit) | `examples/talks.hurl` |
| Ratings | `RatingsTest` (incl. many reviewers at once, double-click), `ConferenceServiceTest` | `examples/ratings.hurl` |
| Ratings change request | `RatingsCrTest` (spec's ties 25/8 → 3.12, 27/8 → 3.38, trim at 5, raw `4.00`); old-rounding pins updated in `RatingsTest`, `FormatsTest`, `UpgradeContractTest`, `AdminReportsTest` | `examples/ratings-cr.hurl`; `spring-boot-4.hurl` updated |
| Submission cap change request | planned: tests per rule and PO-1..5, a concurrent test mixing submissions and co-speaker PUTs for one speaker; `CoSpeakersTest`'s old "limit doesn't apply" test updated | planned: `examples/submission-cap-cr.hurl`; `co-speakers.hurl` updated |
| Formats | `FormatsTest`, `UtilsTest` (byte-exact `ratedAtDisplay`) | — |
| Admin reports | `AdminReportsTest` | — |
| Co-speakers | `CoSpeakersTest` (10 concurrent PUTs → exactly 2) | `examples/co-speakers.hurl` |
| Acceptance | `AcceptanceTest` (3×9 concurrent decisions; withdraw racing accept) | `examples/acceptance.hurl` |
| Health | `HealthTest`, `HealthContractTest` | — |
| Scheduling | `SchedulingTest` (one place × 12 talks, one talk × 15 places, 9-way speaker clash, PUT racing DELETE) | `examples/scheduling.hurl` |
| Workshop registration | `WorkshopRegistrationTest` | `examples/workshop-registration.hurl` |
| Programme export | `ProgrammeExportTest` (byte-exact CSV, JSON = CSV rows and order, PO-1..4) | `examples/programme-export.hurl` |
| Spring Boot 4 | `UpgradeContractTest` (P1–P4, P11, P12, P15–P20) | `examples/spring-boot-4.hurl` |

Gate: `workflow/scripts/verify.sh` — `./mvnw package`, then every `examples/*.hurl` against a local
service on Compose Postgres. D1901's concurrency rules for implemented features all have a test.
