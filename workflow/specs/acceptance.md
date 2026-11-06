# Acceptance state machine: our spec

Feature request: `specs/acceptance.md` (V1). The request gives the business rules and the API
surface; the decisions below (status codes, edge cases) are ours. Open questions for the product
owner are marked **PO?**.

## Rules (from the feature request)

| # | Rule |
|---|---|
| R1 | The committee decides on a talk: `ACCEPTED`, `REJECTED` or `WAITLISTED` (`PUT /talks/{id}/status`). |
| R2 | A `WAITLISTED` talk can still be accepted or rejected. `ACCEPTED` and `REJECTED` are final. |
| R3 | Accepting needs ratings from at least **two different reviewers**. Rejecting and waitlisting need none. |
| R4 | The talk's own speaker can withdraw it (`POST /talks/{id}/withdraw`), unless it is `REJECTED` or `WITHDRAWN`. |
| R5 | Nobody else can withdraw it. |
| R6 | `WITHDRAWN` is out for good: no decision or withdrawal afterwards. |
| R7 | The current status shows wherever a talk is shown. |
| R8 | A decision or withdrawal that isn't allowed changes nothing. |
| R9 | Concurrent decisions/withdrawals on one talk never leave it in a state the rules don't allow. |

## Transitions

| From \ To | ACCEPTED | REJECTED | WAITLISTED | WITHDRAWN (speaker) |
|---|---|---|---|---|
| SUBMITTED | ✔ if ≥2 reviewers | ✔ | ✔ | ✔ |
| WAITLISTED | ✔ if ≥2 reviewers | ✔ | = 200 unchanged (PO-1) | ✔ |
| ACCEPTED | = 200 unchanged (PO-1) | ✘ 409 | ✘ 409 | ✔ |
| REJECTED | ✘ 409 | = 200 unchanged (PO-1) | ✘ 409 | ✘ 409 |
| WITHDRAWN | ✘ 409 | ✘ 409 | ✘ 409 | ✘ 409 |

Only the listed transitions are allowed. Repeating the decision a talk already has (`=` in the
table) is a `200` with the talk unchanged (PO-1): a committee double-click is harmless.
Withdrawing an already withdrawn talk stays a `409`, as the feature request says so.

## Our decisions

| Case | Answer |
|---|---|
| `PUT /talks/{id}/status`, allowed | `200`, the full Talk with the new `status` |
| decision not allowed from the current status | `409`, unchanged |
| the same decision the talk already has (PO-1) | `200`, unchanged |
| `ACCEPTED` with fewer than 2 distinct reviewers | `409`, unchanged |
| body `status` missing, not a string, or not one of `ACCEPTED`/`REJECTED`/`WAITLISTED` (incl. `SUBMITTED`, `WITHDRAWN`, lower case) | `400` |
| no body / malformed JSON | `400` (Spring) |
| talk doesn't exist | `404` (after the body is validated, as in ratings) |
| `POST /talks/{id}/withdraw`, own talk, allowed | `200`, the full Talk, `WITHDRAWN` |
| withdraw a `REJECTED` or `WITHDRAWN` talk (by its speaker) | `409`, unchanged |
| withdraw by another speaker, or an unregistered speaker id (PO-3) | `403`, unchanged |
| body `speakerId` missing, null, not a whole number (incl. `2.0`, `true`, `"2"`, as for rating scores), or < 1 | `400` |
| body `speakerId` a whole number too big for an id | `403` (nobody's id, PO-3) |
| body sent with a Content-Type other than JSON | `415` (Spring) |
| withdraw, talk doesn't exist | `404` |
| non-numeric id in path | `400` (Spring) |
| order of checks | `400` body → `404` talk → `403` ownership → `409` state/ratings |

## Product owner answers

| # | Question | Answer | Asked (tick) | Test |
|---|---|---|---|---|
| PO-1 | Repeating the decision a talk already has (accept an accepted talk, waitlist a waitlisted one): 409 or 200? | `200`, talk unchanged | 38 | `AcceptanceTest#repeatingTheSameDecisionIsHarmless`, `examples/acceptance.hurl` "# PO-1" |
| PO-2 | Do withdrawn and rejected talks count towards a speaker's 3-talk limit? | No: only talks that aren't `WITHDRAWN` or `REJECTED` count | 38 | `AcceptanceTest#withdrawnAndRejectedTalksDoNotCountTowardsTheLimit`, `examples/acceptance.hurl` "# PO-2" |
| PO-3 | Unregistered speaker id withdrawing: 403 or 404? | `403` | 38 | `AcceptanceTest#onlyTheSpeakerCanWithdraw`, `examples/acceptance.hurl` "# PO-3" |

## Effects on existing endpoints

- `status` on every Talk view now shows the real value (it's already read from `talk.status`).
- **Talk submissions, 3-talk limit (PO-2):** the limit counts only the speaker's talks that aren't
  `WITHDRAWN` or `REJECTED`. A speaker with 3 talks, one of them withdrawn, can submit a new one.
  The limit query in `TalkDao.insertIfBelow` changes. Its concurrency guarantee (speaker row
  lock) is unchanged.
- Unchanged: withdrawn and rejected talks still appear in `GET /talks` and the speaker's list, can
  still be rated, and can still get co-speakers.
- `GET /statistics` (`byStatus`) and the CSV report pick up the new statuses without change.

## Concurrency

R9: the transition runs in one `@Transactional` method that locks the talk row
(`SELECT status, speaker_id FROM talk WHERE id = ? FOR UPDATE`), checks the transition and the
reviewer count, then updates. Concurrent requests on one talk run one after another and each sees
the previous one's result. So at most one of several competing decisions wins, and a withdrawal
racing an accept can't be overwritten. Ratings are never deleted, so the reviewer count can't drop
after the check. No advisory locks.

## Evidence

- `examples/acceptance.hurl`: every rule and decision above.
- `src/test/java/no/scienta/cfp/AcceptanceTest.java`: the rules over HTTP, plus 3 rounds of 9
  concurrent mixed decisions on one talk: only `200`/`409`, ACCEPTED and REJECTED never both win, and a final decision is never overwritten; and withdraw racing accept/reject.
- Concurrency tests sample races, they don't prove their absence: the row lock is the argument (see Concurrency), and an independent review checked it.
