# Plan: workshop-registration

Spec: `specs/workshop-registration.md` (V1), our decisions: `workflow/specs/workshop-registration.md`.
Status: approved (tick 101, PO-1..4 answered).

## Current state

- Nothing exists yet. Nobody else is working on it (git log, tick 90). Released at tick 67, unclaimed.
- Reusable: `talk.format` and `talk.status` (acceptance) and the talk row lock
  (`TalkDao.lockForUpdate`, which returns `status, speaker_id` today).
- Platform (tick 101): **Spring Boot 4.1.2 on Java 25** (colleague's upgrade), Jackson 3, `jakarta.*`.
  `TrailingSlashConfig` already makes a trailing slash work on every path. New code goes in **new
  classes**, not `ConferenceService` (the god class, `TODO CFP-140`). `@PathVariable("id")` named
  explicitly. The test helper `Api` uses JsonPath.
- Gate: `verify.sh` wants JDK 25. It's not installed on this laptop, so `JAVA_HOME_OVERRIDE` with
  JDK 27 stands in until it is.

## Changes

| Layer | File | Change |
|---|---|---|
| schema | `schema.sql` | `workshop_registration (id BIGSERIAL PK, talk_id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)` + `CREATE UNIQUE INDEX IF NOT EXISTS ux_registration_email ON workshop_registration (talk_id, lower(email))`, at the bottom |
| DAO | `TalkDao.lockForUpdate` | also select `format` (acceptance ignores it) |
| DAO | new `RegistrationDao` | `insert(talkId, name, email)` → id; `count(talkId)`; `findByEmail(talkId, email)` (`lower`) → `Optional<Registration>`; `findByWorkshop(talkId)` ordered by id |
| response | new `Registration` **record** | `record Registration(long id, long workshopId, String name, String email)` (Java 25; Jackson 3 serialises records) |
| service | new `WorkshopService` | `register(talkId, body)` `@Transactional`: validate (400; PO-4 email shape) → lock talk → 404 unknown → 400 not WORKSHOP (PO-1) → existing email → 200 with it (PO-3) → 409 not ACCEPTED (PO-2) → 409 at 20 → insert → 201. Returns `(Registration, created)` so the controller picks 201 or 200. `registrations(talkId)`. Constant `CAPACITY = 20`. |
| controller | new `WorkshopController` | `POST /workshops/{id}/registrations` (201 new / 200 existing, body as `Map<String, Object>`), `GET /workshops/{id}/registrations` |

## Tests (written first)

| Rule / decision | JUnit `WorkshopRegistrationTest` | Hurl |
|---|---|---|
| R1 register, 201 body; missing/blank/non-string name or email → 400; PO-4 malformed email → 400 | `attendeeRegistersWithNameAndEmail`, `nameAndEmailAreRequired` | ✔ |
| R2 PO-1 not a workshop → 400; PO-2 workshop submitted/waitlisted/rejected → 409; unknown → 404 | `onlyAcceptedWorkshopsTakeRegistrations` | ✔ |
| R3 withdrawn: list kept, new registration 409 | `withdrawnWorkshopKeepsItsList` | ✔ |
| R4 20th ok, 21st 409 | `atMostTwentyAttendees` | ✔ |
| R4 rush: 30 concurrent → 20 × 201, 10 × 409, list has 20 | `rushNeverExceedsTwenty` | - |
| R5 PO-3 same email any case → 200 with the existing registration (same id, email as first typed); also when full | `oneRegistrationPerEmailRegardlessOfCase` | ✔ |
| R5 rush: 5 concurrent, same email in different cases → one 201, four 200 with the same id | `concurrentDuplicatesRegisterOnce` | - |
| R6 same email on two workshops → both 201 | `samePersonCanRegisterForSeveralWorkshops` | ✔ |
| R7/R8 GET in registration order, fields; `[]` when none; unknown → 404 | `organisersSeeRegistrationsInOrder` | ✔ |

Test setup: a workshop is accepted only with 2 reviewers' ratings (acceptance R3), so the helper
submits a `WORKSHOP` talk, rates it twice and accepts it. The speaker limit (3 counting talks) means
one speaker per workshop in tests.

## Risks and open questions

- PO-1..4 answered at tick 101 (see the spec). Riskiest after claiming (no harness-verified precedent): PO-2 `409` and PO-3 `200`. Watch `harness.sh feedback workshop-registration`.
- The harness rejected two of our invented codes for co-speakers. Get the PO answers before
  claiming.
- Regressions: `TalkDao.lockForUpdate` gains a column. `AcceptanceTest` covers its callers.

## Done when

- [x] every rule and decision has a test, and the tests failed before the change
- [x] `workflow/scripts/verify.sh` passes (JDK 27 standing in for 25)
- [ ] deployed `/health` reports the new SHA
- [ ] a person has claimed it; the claim log in `REVIEW.md` has the evidence
