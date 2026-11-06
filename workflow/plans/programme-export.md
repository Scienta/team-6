# Plan: programme-export

Spec: `specs/programme-export.md` (V1), our decisions: `workflow/specs/programme-export.md`, rules
in `workflow/DESIGN.md` D2000–D2086. Status: approved (tick 140, PO-1..4 answered).

## Current state

- Not implemented. Nobody else is on it: the only commits are the spec sync and the teammate's
  DESIGN section (`5aad35b`). Released tick 133, unclaimed.
- Builds on scheduling: `programme_entry (room, slot, talk_id)` and `Programme.SLOTS`/`ROOMS`
  (start/end constants, D2085). `CoSpeakerDao.findByTalks` shows the batch pattern (D2082).
- Scheduling is a colleague's feature and is being fixed by them. We only **read** its table and
  constants; no changes to scheduling code.

## Changes

| Layer | File | Change |
|---|---|---|
| DAO | new `ProgrammeExportDao` | **one** query: `programme_entry` ⨝ `talk` ⨝ main `speaker`, plus the co-speakers' names as `array_agg(name ORDER BY talk_co_speaker.id)`, `ORDER BY slot, room`. No N+1 (D2082). |
| model | new `ProgrammeRow` record | `slot, start, end, room, talkId, title, format, speakers` in the spec's field order (D2005). `start`/`end` from `Programme.SLOTS` (D2085). |
| service | new `ProgrammeExportService` | `rows()` `@Transactional(readOnly = true)` builds the list once (D2081, D2083). `csv(rows)` writes RFC 4180 with `\n`, minimal quoting, `"; "`-joined speakers (D2007–D2010). New class, not `ConferenceService`, as `WorkshopService`. |
| controller | new `ProgrammeExportController` | `GET /programme` with `produces = application/json` → `List<ProgrammeRow>`; `406` when the client won't take JSON (PO-3). `GET /programme.csv` → `ResponseEntity<String>` with the `Content-Type` set on the response, **no** `produces =` (D2080), so `Accept: application/json` still gets CSV. Header `Content-Disposition: attachment; filename=programme.csv` (PO-4). |
| schema | none | |

## Tests (written first)

| Rule / decision | JUnit `ProgrammeExportTest` | Hurl |
|---|---|---|
| R9 empty: `[]`, CSV = header + `\n` exactly | `emptyProgrammeIsHeaderOnly` | ✔ |
| R1–R4 rows, fields, field order, start/end | `oneRowPerScheduledTalk` | ✔ |
| R3 order S1→S5, R1→R3 (placed in scrambled order) | `readsLikeADay` | ✔ |
| R5 main speaker first, co-speakers in the order added; CSV `"; "` | `speakersMainFirstThenCoSpeakers` | ✔ |
| R4 "as they are now": add a co-speaker / change a speaker's name after scheduling → export shows it | `showsTalksAndSpeakersAsTheyAreNow` | - |
| R6–R8 byte-exact CSV: header, `\n` only, last line ends in `\n`, quoting (`,` `"` `\n` `\r\n` in a title), UTF-8 name (`Åse Ødegård`), no BOM | `csvIsByteExactRfc4180WithUnixLineEndings` | ✔ (header, content type) |
| R6 `text/csv` also for `Accept: application/json` | `csvIgnoresAcceptAndIsUtf8` | ✔ |
| R2 JSON and CSV have the same rows in the same order | `jsonAndCsvAgree` | - |
| PO-1 withdrawn talk still listed | `withdrawnTalkStaysListed` | - |
| PO-2/PO-4 `text/csv;charset=UTF-8`, `Content-Disposition` | `csvIgnoresAcceptAndIsUtf8` | ✔ |
| PO-3 `Accept: text/csv` on `/programme` → 406 | `jsonViewIs406WhenTheClientWontTakeJson` | ✔ |

Fixtures: talks are placed through the scheduling API (the real path). Where many are needed, use
SQL rows like `SchedulingTest`'s concurrency fixtures.

## Risks and open questions

- PO-1..4 answered at tick 140 (see the spec). PO-3 (`406`) and PO-4 have no harness-verified precedent: watch after the claim.
- The checks will likely compare the CSV byte for byte, so the byte-exact test is the main guard.
- Shares `programme_entry` with scheduling, which a colleague is changing right now. Pull before
  implementing; read-only use keeps the overlap small.

## Done when

- [x] every rule and decision has a test, and the tests failed before the change
- [x] `workflow/scripts/verify.sh` passes (JDK 27 standing in for 25)
- [x] `DESIGN.md` D2000–D2086 agree with the decisions (status line, answered D2050–D2056)
- [ ] deployed `/health` reports the new SHA; a person claims; claim log in `REVIEW.md`
