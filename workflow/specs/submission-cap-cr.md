# Submission cap change request: our spec

Change request: `specs/submission-cap-cr.md` (Version 1, released at tick 180). It changes the 3-talk
limit of [Talk submissions](../../specs/talks.md) and replaces co-speakers R7 ("co-speaking doesn't
count"). In `DESIGN.md` it changes D1105, D1106, D1306 and D1350 in place. Open questions are
marked **PO?**.

Status: rules and PO answers recorded (tick 188); plan in `workflow/plans/submission-cap-cr.md` (tick 190).

## Rules (from the change request)

| # | Rule | D-rule |
|---|---|---|
| R1 | A speaker's 3 talks include the talks they **co-speak** on, not only their own. | D1105 (changed) |
| R2 | `REJECTED` and `WITHDRAWN` talks don't count, for the main speaker or the co-speakers. Taking someone off a talk as co-speaker frees their place. A freed place can be used straight away. | D1106 (changed) |
| R3 | A speaker at the limit can't submit a new talk, and can't be added as co-speaker to a talk still in the running. Being added to a rejected or withdrawn talk is fine. | D1306 (replaced), D1350 |
| R4 | Submissions and co-speaker invitations for the same speaker arrive together; the limit must still hold. | D1354, D1901 |
| R5 | Nothing that exists is removed or changed. Speakers over the new limit keep everything, but get nothing new until they're below it. | D1106 |
| R6 | A speaker's talk list still shows only the talks where they are the main speaker. | D1305 (unchanged) |

## Our decisions

| Case | Answer |
|---|---|
| what counts towards a speaker's limit | talks where they are the main speaker **plus** talks where they are a co-speaker, in each case only talks not `REJECTED` or `WITHDRAWN`. `SUBMITTED`, `WAITLISTED` and `ACCEPTED` count (PO-4). |
| `POST /talks`, main speaker at the limit (counting co-speaking) | `409`, as today's 4th-talk answer |
| `PUT /talks/{id}/co-speakers/{speakerId}`, co-speaker at the limit, talk still in the running | `409`, nothing changes (PO-1) |
| same, but the talk is `REJECTED` or `WITHDRAWN` | `200`: it doesn't count (R3) |
| same, but they are already a co-speaker of this talk | `200`, nothing changes, also at or over the limit (PO-2) |
| same, but they are the talk's main speaker | `400`, as co-speakers PO-2; the main-speaker check wins over the limit (PO-3) |
| check order on the co-speaker `PUT` | `404` talk/speaker → `400` main speaker → `200` already a co-speaker → `409` talk has 2 co-speakers → `409` co-speaker at the limit. **Decided [ours] in `/plan-feature`:** "2 co-speakers" first, since it needs no second lock; the two `409`s differ only in the message. |
| speaker over the new limit, added to a rejected or withdrawn talk | `200`, allowed: those talks don't count (R3, PO-5) |
| speaker over the limit, `DELETE` as co-speaker | allowed as today; it frees a place (R2) |

## Product owner answers

| # | Question | Answer | Asked (tick) | Test |
|---|---|---|---|---|
| PO-1 | Adding a co-speaker who is at the cap: what answer? | `409`, nothing changes | 188 | `SubmissionCapTest#atTheLimitCannotBeAddedAsCoSpeaker`, hurl |
| PO-2 | Re-adding an existing co-speaker who is at or over the cap: still a no-op? | `200`, nothing changes | 188 | `SubmissionCapTest#reAddingAnExistingCoSpeakerIsANoOpAtTheLimit`, hurl |
| PO-3 | Adding the talk's main speaker as co-speaker when they're at the cap: `400` or `409`? | `400`: the main-speaker check wins | 188 | `SubmissionCapTest#mainSpeakerCheckWinsOverTheLimit`, hurl |
| PO-4 | Do `WAITLISTED` talks count towards the cap? | yes | 188 | `SubmissionCapTest#waitlistedAndAcceptedCount` |
| PO-5 | Can a speaker already over the cap be added as co-speaker to a `REJECTED` or `WITHDRAWN` talk? | yes, allowed (our default, accepted) | 192 | `SubmissionCapTest#overTheLimitKeepsEverythingButGetsNothingNew` |


## Effects on existing endpoints

- `POST /talks`: the limit query also counts the speaker's co-speaker places on talks in the running.
- `PUT /talks/{id}/co-speakers/{speakerId}`: a new `409` for a co-speaker at the limit.
- **Reverses co-speakers R7 / D1306.** `CoSpeakersTest` and `examples/co-speakers.hurl` pin the old
  rule ("a speaker at the limit can still co-speak") and must change. Co-speakers `check-3` has shown
  `null` (not run) since tick 180, which suggests the harness paused the old rule's check.
- Unchanged: speaker talk lists (R6), `DELETE` of a co-speaker, acceptance, scheduling, the Talk view.

## Concurrency

R4 is the main risk. Today the two writers take **different locks**: `POST /talks` locks the main
speaker's row (`TalkDao.insertIfBelow`), and the co-speaker `PUT` locks the talk row (D1353). A
submission and an invitation for the same speaker don't block each other, so both can take the last
place.

Required: the co-speaker `PUT` also locks the **co-speaker's speaker row** `FOR UPDATE` before
counting, always talk row first, then speaker row. Submissions lock only the speaker row and insert a
new talk; acceptance and withdrawal lock only the talk row and can only *free* places (`REJECTED` and
`WITHDRAWN` are terminal). So there is no lock cycle. A count spanning two tables has no constraint
backstop; the speaker-row lock is the argument (as D1806).

## Evidence

- `src/test/java/no/scienta/cfp/SubmissionCapTest.java`: R1–R6 and PO-1..5 (11 tests, 7 red before the change).
  Two rush tests for one speaker's last place: 5 submissions + 5 co-speaker PUTs, and 8 co-speaker PUTs on
  8 different talks. **Mutation check (tick ~195):** with `talkDao.lockSpeaker` removed from `addCoSpeaker`,
  both fail with 5 winners instead of 1; with it, 4 runs × 3 rounds pass. So the tests do prove the lock.
- `CoSpeakersTest#coSpeakingCountsTowardsTheLimit` (reversed from co-speakers R7).
- `examples/submission-cap-cr.hurl` (20 requests); `examples/co-speakers.hurl` R7 section reversed.
