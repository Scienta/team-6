# Co-speakers: our spec

Feature request: `GET $H/specs/co-speakers/spec.md` (V1). The request gives the business rules and the
API surface. The decisions below (status codes, edge cases) are ours. Open questions for the product
owner are marked **PO?**.

## Rules (from the feature request)

| # | Rule |
|---|---|
| R1 | A talk keeps its one main speaker (`speakerId`). Co-speakers are other registered speakers, added afterwards and removable. |
| R2 | At most **two** co-speakers per talk. |
| R3 | The main speaker can't be a co-speaker of their own talk. |
| R4 | Every talk shows `coSpeakerIds` in the order the co-speakers were added; `[]` when none. |
| R5 | Adding someone who is already a co-speaker changes nothing. |
| R6 | `GET /speakers/{id}/talks` (and `GET /talks?speakerId=`) still shows only the talks where they're the **main** speaker. |
| R7 | Co-speaking doesn't count towards the 3-talk limit; a speaker at the limit can still co-speak. |

## Our decisions

| Case | Answer |
|---|---|
| `PUT /talks/{id}/co-speakers/{speakerId}`, OK | `200`, the full Talk with `coSpeakerIds` |
| PUT, already a co-speaker (R5) | `200`, talk unchanged, order unchanged. This holds even when the talk already has 2. |
| PUT, talk doesn't exist | `404` |
| PUT, speaker doesn't exist | `404` |
| PUT, speaker is the main speaker (R3) | `409` |
| PUT, talk already has 2 other co-speakers (R2) | `409` |
| `DELETE /talks/{id}/co-speakers/{speakerId}`, OK | `200`, the full Talk |
| DELETE, speaker isn't a co-speaker | `200`, talk unchanged (idempotent, so double-clicks are safe). **PO?** (could be 404) |
| DELETE, talk doesn't exist | `404` |
| Non-numeric id in the path | `400` (Spring's type mismatch) |
| Removed, then added again | Goes to the end of the order. |
| `coSpeakerIds` everywhere a Talk is shown | POST /talks, GET /talks/{id}, GET /talks (all variants), GET /speakers/{id}/talks |

## Concurrency

Like the 3-talk limit, R2 must hold when several PUTs for the same talk arrive at once. The PUT locks
the talk row (`SELECT … FOR UPDATE`) and then counts and inserts in the same transaction. A unique
`(talk_id, speaker_id)` with `ON CONFLICT DO NOTHING` covers R5 under double-clicks.

## Evidence

- `examples/co-speakers.hurl`: every rule and decision above, against a running service.
- `src/test/java/no/scienta/cfp/CoSpeakersTest.java`: 10 concurrent PUTs on one talk give exactly 2 co-speakers.
