# Plan: acceptance

Spec: `specs/acceptance.md` (V1), our decisions: `workflow/specs/acceptance.md`. Status: approved (tick 38, with PO answers PO-1..3).

## Current state

- `talk.status` exists (`TEXT NOT NULL DEFAULT 'SUBMITTED'`), every new talk is `SUBMITTED`, and
  nothing changes it. `TalkDao` maps it into `TalkDto.status`, so R7 already holds for every view
  once the column changes. No schema change is needed.
- `rating` has PK `(talk_id, reviewer_id)`, so one row per reviewer.
- Pattern to copy: `ConferenceService.addCoSpeaker` (lock the talk row, check, write, `getTalk`).
- Harness, tick 23: `acceptance` released at tick 20, unclaimed, no checks yet.

## Changes

| Layer | File | Change |
|---|---|---|
| schema | `schema.sql` | none |
| DAO | `TalkDao.java` | `lockForUpdate(id)`: `SELECT status, speaker_id … FOR UPDATE`, returns a small row or null. `updateStatus(id, status)`. |
| DAO | `TalkDao.insertIfBelow` | PO-2: the limit counts `status NOT IN ('WITHDRAWN', 'REJECTED')` |
| DAO | `RatingDao.java` | `reviewerCount(talkId)`: `COUNT(DISTINCT reviewer_id)` |
| service | `ConferenceService.java` | new section "acceptance": `decide(talkId, body)` and `withdraw(talkId, body)`, both `@Transactional`, with checks in the order 400 → 404 → 403 → 409. The same decision the talk already has → `200` unchanged (PO-1). The transition table is a `Map<String, Set<String>>` of allowed decisions per status, plus a `WITHDRAWABLE` set. |
| controller | `TalkController.java` | `PUT /talks/{id}/status` and `POST /talks/{id}/withdraw`, body as `Map<String, Object>` (like `rate`), so wrong types become our `400`s, not Jackson errors |
| response | `TalkDto` / `enrich` | none |

## Tests (written first)

| Rule / decision | JUnit `AcceptanceTest` | Hurl `examples/acceptance.hurl` |
|---|---|---|
| R1 waitlist, reject from SUBMITTED | `committeeCanRejectOrWaitlistASubmittedTalk` | ✔ |
| R2 waitlisted → accepted/rejected; final states refuse everything | `waitlistedCanStillBeDecided`, `acceptedAndRejectedAreFinal` | ✔ |
| R3 accept: 0 or 1 reviewer → 409; one reviewer rating twice is still 1; 2 → 200 | `acceptNeedsTwoDifferentReviewers` | ✔ |
| R4 speaker withdraws from SUBMITTED, WAITLISTED, ACCEPTED; REJECTED → 409 | `speakerCanWithdrawUnlessRejected` | ✔ |
| R5 another speaker → 403 | `onlyTheSpeakerCanWithdraw` | ✔ |
| R6 withdrawn: decide → 409, withdraw again → 409 | `withdrawnIsOutForGood` | ✔ |
| R7 status on GET /talks/{id}, GET /talks, GET /speakers/{id}/talks | `statusShowsEverywhere` | ✔ |
| R8 refused requests leave status unchanged | asserted after every 409/403 | ✔ |
| 400s: bad/missing status, SUBMITTED/WITHDRAWN as decision, bad speakerId; 404 unknown talk | `invalidRequests` | ✔ |
| PO-1 accept an accepted, reject a rejected, waitlist a waitlisted → 200 unchanged | `repeatingTheSameDecisionIsHarmless` | ✔ |
| PO-2 a speaker with 3 talks withdraws (or gets rejected) → can submit a 4th; still at most 3 counting | `withdrawnAndRejectedTalksDoNotCountTowardsTheLimit` | ✔ |
| PO-3 unregistered speaker id withdraws → 403 | in `onlyTheSpeakerCanWithdraw` | ✔ |
| R9 3×9 concurrent mixed decisions → every 200 asked for the final status, every other got 409 | `concurrentDecisionsHaveOneWinner` | - |
| R9 concurrent withdraw + accept → never both lost; final state consistent with the 200s | `withdrawRacingAcceptEndsConsistent` | - |

## Risks and open questions

- PO-2 changes the talks feature's 3-talk limit: `TalksTest` and `talks.hurl` must stay green (no talk there is withdrawn, so the count is the same).
- Regressions: talks, ratings and co-speakers are untouched apart from the new methods.
  `TalksTest`, `CoSpeakersTest` and all hurl files run in `verify.sh`.
- Risk: the harness may expect a different status code for a rule (e.g. 422 or 400 where we say
  409). Mitigation: watch `harness.sh feedback acceptance` after claiming, then `/triage-check`.

## Done when

- [x] every rule and decision has a test, and the tests failed before the change
- [x] `workflow/scripts/verify.sh` passes
- [ ] deployed `/health` reports the new SHA
- [ ] a person has claimed it; the claim log in `REVIEW.md` has the evidence
