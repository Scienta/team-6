# Plan: submission-cap-cr

Change request: `specs/submission-cap-cr.md` (V1, tick 180). Our spec (a teammate, tick 188):
`workflow/specs/submission-cap-cr.md`, rules R1–R6, PO-1..4. DESIGN.md: D1105, D1106, D1306, D1350
changed in place, D1354 new. Status: approved (tick 190, PO-1..5 answered).

## Current state

- Talks and co-speakers are claimed, all checks OK. Co-speakers `check-3` is `null` (not run) since
  tick 180: the harness has paused the old "co-speaking doesn't count" check. The change request is
  unclaimed. Nobody is implementing it (git log, tick 190).
- `TalkDao.insertIfBelow` locks the main speaker's row, then inserts if
  `COUNT(own talks not REJECTED/WITHDRAWN) < 3`.
- `ConferenceService.addCoSpeaker` locks the talk row (`CoSpeakerDao.lockTalk`, which returns only the
  main speaker), then 404 → 400 main speaker → 200 already co-speaker → 409 two co-speakers → add.
  **It doesn't know the talk's status and never looks at the co-speaker's other talks.**
- Old rule pinned in: `CoSpeakersTest#coSpeakingDoesNotCountTowardsTheLimit`, `examples/co-speakers.hurl`
  (the R7 section: "co1 co-speaks, and can still submit 3 talks of their own; a speaker at the limit
  can still co-speak"). Check `examples/scheduling.hurl`, `SchedulingTest`, `ProgrammeExportTest`:
  they add co-speakers through the API, and a speaker could now hit the limit.

## Changes

| Layer | File | Change |
|---|---|---|
| DAO | `TalkDao` | new `COUNTING` SQL: talks not `REJECTED`/`WITHDRAWN` where the speaker is main speaker **or** in `talk_co_speaker` (R1, R2, PO-4). `insertIfBelow` uses it (still under the speaker-row lock). New `lockSpeaker(id)` and `countingTalks(speakerId)`. |
| service | `ConferenceService.addCoSpeaker` | lock with `talkDao.lockForUpdate(talkId)` (status + main speaker) instead of `CoSpeakerDao.lockTalk`. After "already a co-speaker → 200" and "two co-speakers → 409": if the talk is in the running (not `REJECTED`/`WITHDRAWN`), lock the **co-speaker's speaker row** (D1354, always talk → speaker) and refuse with `409` when `countingTalks ≥ 3` (PO-1). Rejected or withdrawn talk: no limit (R3). |
| service | `removeCoSpeaker`, acceptance, withdraw | no change: the count reads current rows, so a removed co-speaker or a rejected/withdrawn talk frees the place at once (R2) |
| schema | none | |

Check order on the co-speaker PUT (spec row "to confirm"; **decided [ours]**): `404` talk/speaker → `400`
main speaker (PO-3) → `200` already a co-speaker (PO-2) → `409` two co-speakers → `409` co-speaker at
the limit. The two 409s differ only in the message; "two co-speakers" is checked first because it
needs no second lock.

Locks, no cycle (spec "Concurrency"): submission = speaker row; co-speaker PUT = talk row, then
co-speaker's speaker row; acceptance, withdrawal, workshop registration = talk row only, and can only
*free* places.

## Tests (written first): `SubmissionCapTest`

| Rule / decision | Test | Hurl `examples/submission-cap-cr.hurl` |
|---|---|---|
| R1 a speaker co-speaking on 2 talks can submit only 1 of their own; then `POST /talks` → 409 | `coSpeakingCountsTowardsTheLimit` | ✔ |
| R3, PO-1 a speaker at the limit (own talks) can't be added as co-speaker to a talk in the running → 409, nothing changes | `atTheLimitCannotBeAddedAsCoSpeaker` | ✔ |
| R3 … but can be added to a `REJECTED` and to a `WITHDRAWN` talk → 200 | `rejectedOrWithdrawnTalksAreOutsideTheLimit` | ✔ |
| R2 rejecting/withdrawing a talk frees a place for its main speaker **and** its co-speakers; removing a co-speaker frees theirs, usable at once | `freedPlacesCanBeUsedAtOnce` | ✔ |
| PO-4 `WAITLISTED` and `ACCEPTED` talks count | `waitlistedAndAcceptedCount` | - |
| PO-2 re-adding an existing co-speaker at/over the limit → 200 unchanged | `reAddingAnExistingCoSpeakerIsANoOpAtTheLimit` | ✔ |
| PO-3 the main speaker at the limit added to their own talk → 400 | `mainSpeakerCheckWinsOverTheLimit` | ✔ |
| R5, PO-5 a speaker over the new limit keeps everything; nothing new (submission 409, co-speaker on a talk in the running 409) until below it; a rejected/withdrawn talk → 200 | `overTheLimitKeepsEverythingButGetsNothingNew` (set up with SQL rows, since the API can't create it any more) | - |
| R6 a speaker's own talk list still shows only main-speaker talks | `ownTalkListUnchanged` | ✔ |
| R4 rush: one speaker with 2 counting talks; 5 submissions + 5 co-speaker PUTs (5 talks by others) at once → exactly 1 success; the speaker ends at 3 counting talks | `submissionsAndInvitationsRacingForTheLastPlace` (3 rounds) | - |
| R4 the same with only co-speaker PUTs on different talks (different talk locks, so only the speaker lock serialises them) | `invitationsOnDifferentTalksRacingForTheLastPlace` | - |
| old pins | `CoSpeakersTest#coSpeakingDoesNotCountTowardsTheLimit` → reversed (`coSpeakingCountsTowardsTheLimit`, pointing here); `co-speakers.hurl` R7 section updated | ✔ |

## Risks and open questions

- PO-5 (a teammate, after the plan was drafted): a speaker over the limit **can** be added to a `REJECTED` or `WITHDRAWN` talk → `200`. Tested in `overTheLimitKeepsEverythingButGetsNothingNew`.

- The reversal of co-speakers R7 changes the claimed co-speakers feature. The harness has already
  paused its check-3, so the switch-over follows PO-1 of ratings-cr: **the claim switches**. Push,
  wait for `/health`, claim at once.
- Concurrency is the main risk (R4). The rush tests use different talks so only the new speaker-row
  lock can make them pass. Run them several times.
- Other tests that add co-speakers through the API (`SchedulingTest`, `ProgrammeExportTest`,
  `scheduling.hurl`) may now hit the limit for a shared speaker. Check them in the full gate; fix
  setup, not the rule.

## Done when

- [x] every rule and PO answer has a test, failing before the change; old pins reversed
- [x] rush tests pass repeatedly (4 runs × 3 rounds each); `workflow/scripts/verify.sh` passes (JDK 27 standing in for 25)
- [x] DESIGN.md D1354 no longer says "not implemented"; evidence row filled
- [ ] shipped, then claimed in the same tick; talks, co-speakers and submission-cap-cr checks OK
