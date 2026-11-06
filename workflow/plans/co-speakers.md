# Plan: co-speakers

Spec: `specs/co-speakers.md` (V1), our decisions: `workflow/specs/co-speakers.md`. Status: approved (tick 38, with PO-1).

## Current state

Implemented and deployed in `296bc7b`. `examples/co-speakers.hurl` covers every rule and passes in
`verify.sh` (27 requests). `CoSpeakersTest` covers only R2 and R5 under concurrency, so the other
rules have no test in `./mvnw package`. Harness: released at tick 0, unclaimed.

## Changes

- `ConferenceService.removeCoSpeaker`: 404 when the speaker isn't a co-speaker of the talk (PO-1). `CoSpeakerDao.remove` returns the number of rows deleted.
- `examples/co-speakers.hurl`: the second DELETE expects 404 (# PO-1).
- Tests for the rules only covered by Hurl so far.

## Tests

| Rule / decision | JUnit `CoSpeakersTest` |
|---|---|
| R4 new talk has `[]`; order kept; re-added goes last | `coSpeakersShowInTheOrderAdded` |
| R3, PO-2 main speaker → 400, unchanged (also with 2 co-speakers) | `addingTheMainSpeakerIs400` |
| R2 third → 409, unchanged | `atMostTwoCoSpeakers` |
| 404 unknown talk / speaker (PUT, DELETE) | `unknownTalkOrSpeakerIs404` |
| PO-1 DELETE of a non-co-speaker (or unknown speaker) → 404 unchanged | `removingANonCoSpeakerIs404` |
| R6 speaker's talk list and `?speakerId=` show main-speaker talks only | `ownTalkListShowsOnlyMainSpeakerTalks` |
| R7 a speaker at the 3-talk limit can still co-speak; co-speaking doesn't use up the limit | `coSpeakingDoesNotCountTowardsTheLimit` |

## Risks and open questions

- PO-1 makes DELETE non-idempotent: a double-clicked remove gets 200 then 404. Accepted by the PO.

## Done when

- [x] the tests above pass (the rule tests should pass at once; PO-1 fails until the code changes)
- [x] `workflow/scripts/verify.sh` passes
- [ ] a person has claimed it; the claim log in `REVIEW.md` has the evidence
