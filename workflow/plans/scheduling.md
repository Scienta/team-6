# Plan: scheduling

Spec: `specs/scheduling.md` (V1), our decisions: `workflow/specs/scheduling.md`. Status: approved (tick 107, with defaults for PO-?1..6; 2-5 being asked).

## Current state

- Nothing exists. `schema.sql` has a commented-out 2020 attempt (`talk.room`, `talk.slot`); we
  don't revive it: a separate table gives the locks and constraints R10 needs.
- Dependencies are in place: `talk.status` with `ACCEPTED` (acceptance, on `main`, **unclaimed**),
  `talk_co_speaker` (co-speakers, claimed), `talk.format`.
- Harness (tick 107): `scheduling` released at tick 100, V 3, unclaimed, no checks reported yet.

## Changes

| Layer | File | Change |
|---|---|---|
| schema | `schema.sql` | `CREATE TABLE IF NOT EXISTS programme_entry (room TEXT NOT NULL, slot TEXT NOT NULL, talk_id INTEGER UNIQUE, PRIMARY KEY (room, slot))`, then `INSERT … VALUES` the 15 rows `ON CONFLICT DO NOTHING` |
| model | `Programme.java` (new) | the venue: `ROOMS = [R1,R2,R3]`, `SLOTS` as an ordered map slot → `{"09:00","10:00"}` …, `LONG_SLOT = "S4"`; string constants (D1702) |
| DAO | `ProgrammeDao.java` (new) | `lockSlot(slot)` → `Map<room, talkId>` (`FOR UPDATE`); `find(room, slot)` → talkId or null; `findSlotOf(talkId)`; `place(room, slot, talkId)`; `clear(room, slot)` → rows changed; `speakersOf(List<talkId>)`: main + co-speakers in one query |
| DAO | `TalkDao.lockForUpdate` | reuse; add `format` to its row (`SELECT status, speaker_id, format …`) |
| service | `ConferenceService.java` | section "scheduling": `schedule(room, slot, body)` `@Transactional`, order path 404 → body 400 → lock slot → talk 404 (lock) → 409s R4, R5, R6, R7, R8; `DuplicateKeyException` → 409. `entry(room, slot)`, `unschedule(room, slot)` `@Transactional` |
| controller | `ProgrammeController.java` (new) | `PUT/GET/DELETE /programme/{room}/{slot}`, named path variables, body `Map<String, Object>` (like `decide`); `DELETE` → `204` |
| response | Entry | `LinkedHashMap` in order `room, slot, start, end, talkId` |
| docs | `workflow/DESIGN.md` | D1800–D1899 scheduling (new group; update the source map, domain model, D1901 list, evidence table) |

## Tests (written first)

| Rule / decision | JUnit `SchedulingTest` | Hurl `examples/scheduling.hurl` |
|---|---|---|
| R1, R2 every slot's exact `start`/`end` strings, every room | `venueHasFixedRoomsAndSlots` | ✔ (S1 `09:00`, S4 `13:30`/`15:30`) |
| R3 put, get, delete, put elsewhere | `putGetTakeOutAndMove` | ✔ |
| R4 only accepted (submitted, waitlisted, rejected, withdrawn → 409) | `onlyAcceptedTalks` | ✔ |
| R5 workshop only in S4; presentation in S4 ok | `workshopNeedsTheLongSlot` | ✔ |
| R6 occupied → 409, entry unchanged | `oneTalkPerRoomAndSlot` | ✔ |
| R7 already elsewhere → 409 | `aTalkIsOnTheProgrammeOnce` | ✔ |
| R8 main/main, main/co, co/co clash in one slot → 409; same speaker in another slot ok | `nobodyInTwoRoomsAtOnce` | ✔ |
| R9 withdrawn / co-speaker added after scheduling: entry stays | `laterChangesLeaveTheProgramme` | ✔ |
| PO-?1 repeat put → 200, also after withdrawal; entry stays | `repeatingAPutIsHarmless` | ✔ |
| PO-?2/3 empty GET/DELETE → 404, DELETE → 204 | `emptyRoomAndSlot` | ✔ |
| PO-?4 → 404, PO-?5 unknown talk → 400, 400s | `invalidRequests` | ✔ |
| R10 20 talks into one room/slot → one 200 | `concurrentPutsIntoOneSlot` | - |
| R10 one talk into 15 places → one 200 | `concurrentPutsOfOneTalk` | - |
| R10 9 speaker-sharing talks into the 3 rooms of one slot → one 200 | `concurrentSpeakerClash` | - |
| R10 PUT racing DELETE in one place | `putRacingTakeOutEndsConsistent` | - |
| R6 backstop: conditional write | `placingIntoATakenPlaceIsRefusedByTheDatabase` | - |
| PO-?6 co-speaker added after scheduling blocks the slot | `speakersAreComparedAsTheyAreNow` | - |
| 415 non-JSON | `invalidRequests` | - |

Tests share one database with other test classes: each test makes its own talks, and clears the
programme rows it uses (or the test class empties `programme_entry` in `@BeforeEach`). Accepting a
talk needs 2 reviewer ratings first: a helper `acceptedTalk(format, speakerId)`.

The hurl file runs against the deployed, shared database too: it takes out whatever is in the
places it uses first (`DELETE`, any of `204`/`404`), so it can run again.

## Risks and open questions

- **PO?** 1–6 in `workflow/specs/scheduling.md`; defaults are coded and each gets a test.
- The harness will also schedule into the 15 places; the shared programme fills up across runs.
  Nothing we can do; the hurl file cleans up its own places.
- Acceptance is unclaimed. Scheduling depends on it; claiming scheduling while acceptance is
  unclaimed may still score, but a teammate should check why acceptance isn't claimed.
- Regressions: none expected (new table, new routes). `AcceptanceTest`, `CoSpeakersTest` and every
  hurl file run in the gate.
- Lock time: a PUT holds the slot's 3 rows for one short transaction; fine with the pool of 5.

## Deviations from the plan

- `TalkDao.lockForUpdate` now also returns `format`; `ConferenceServiceTest` passes a mock `ProgrammeDao`.
- PO-?1 changed to `409` and PO-?5 to `400` after verify; PO-?1 went back to `200` at tick 140 when
  `scheduling/check-1` was FAULTY on the repeat PUT. PO-?5 `400` passed the same check. Original reasoning: following harness precedent (acceptance
  check-3/check-5 refused `200` for a repeat; `POST /talks` gives `400` for an unknown `speakerId`).
- Review findings (tick ~110): R6 got a conditional-write backstop; the tests above were added; the
  400/415-before-404 order for unreadable bodies is recorded in the spec.
- Rebased onto `origin/main` (workshop registration, acceptance PO-1 → 409) before verifying.
- Locally, `verify.sh` ran with `COMPOSE_PROJECT_NAME=team-6` to reuse the main checkout's Postgres on 5432.

## Done when

- [x] every rule and decision has a test, and the tests failed before the change (14/14 red: no `programme_entry`; then green)
- [x] `workflow/scripts/verify.sh` passes (full gate after rebase, review fixes and PO-?1/5 switch: 124 tests, 11 hurl files)
- [ ] deployed `/health` reports the new SHA
- [ ] a person has claimed it; the claim log in `REVIEW.md` has the evidence
