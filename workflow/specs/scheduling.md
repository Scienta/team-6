# Programme scheduling: our spec

Feature request: `specs/scheduling.md` (V1). The request gives the business rules and the API surface;
the decisions below (status codes, edge cases) are ours. Open questions for the product owner are
marked **PO?**.

## Rules (from the feature request)

| # | Rule |
|---|---|
| R1 | The venue is fixed: rooms `R1`, `R2`, `R3`; slots `S1` 09:00–10:00, `S2` 10:15–11:15, `S3` 11:30–12:30, `S4` 13:30–15:30, `S5` 15:45–16:45, the same in every room. Nobody can add or change them. |
| R2 | `start` and `end` are `HH:mm`, 24-hour, zero-padded (`09:00`, never `9:00`). |
| R3 | Put a talk in a room and slot (`PUT /programme/{room}/{slot}` `{talkId}`), look it up (`GET`), take it out (`DELETE`). A talk taken out can go somewhere else. |
| R4 | Only `ACCEPTED` talks go on the programme. |
| R5 | A talk must fit its slot: a `WORKSHOP` needs the long slot `S4`; every other format fits anywhere, `S4` included. |
| R6 | One talk per room and slot. |
| R7 | A talk is on the programme only once. To move it, take it out first. |
| R8 | Nobody in two rooms at once: the main speakers and co-speakers of all talks in one slot are all different people. |
| R9 | The rules are checked when a talk is put on the programme. If the talk changes afterwards (withdrawn, co-speaker added), the programme stays as it is. |
| R10 | Several committee members at once never leave the programme breaking R5–R8. |

## Our decisions

Check order (D1904): place `404` → body `400` → talk exists `400` → `409`s (repeat, R4, R5, R6, R7, R8).

| Case | Answer |
|---|---|
| `PUT`, allowed | `200`, the Entry `{room, slot, start, end, talkId}` |
| `PUT` the same talk into the room and slot it already has (double-click) | `409`, unchanged, also after the talk was withdrawn (**PO-?1**; precedent: the harness failed acceptance check-3/check-5 on `200` for a repeated decision) |
| `GET`, a talk is there | `200`, the Entry |
| `GET`, empty room and slot | `404` (**PO-?2**) |
| `DELETE`, a talk is there | `204`, no body (**PO-?3**) |
| `DELETE`, empty room and slot | `404`, like removing a non-co-speaker (co-speakers PO-1) |
| room or slot not in the enum (`R4`, `S6`, lower case `r1`), any method | `404` (**PO-?4**) |
| body `talkId` missing, null, not a whole number (`1.0`, `"1"`, `true`), or < 1 | `400` (as `speakerId` in acceptance) |
| no body / malformed JSON | `400` (Spring) |
| non-JSON Content-Type | `415` (Spring) |
| bad room/slot **and** an unreadable body (malformed JSON, non-JSON) | `400`/`415`: Spring reads the body before our place check runs. Accepted: only a request with two errors sees it (review finding C) |
| `talkId` a whole number but no such talk (incl. too big for an id) | `400` (**PO-?5**; precedent: `POST /talks` answers `400` for an unknown `speakerId`, and the harness accepts that) |
| talk not `ACCEPTED` (`SUBMITTED`, `WAITLISTED`, `REJECTED`, `WITHDRAWN`) (R4) | `409` |
| `WORKSHOP` in a slot other than `S4` (R5) | `409` |
| room and slot already hold another talk (R6) | `409` |
| talk already on the programme elsewhere (R7) | `409` |
| a speaker of the talk (main or co) already speaks in another room in that slot (R8) | `409` |
| order among the `409`s | not observable apart from the message; checked repeat, R4, R5, R6, R7, R8 |
| speakers compared for R8 | the talks' speakers **now** (main + current co-speakers), not as they were when scheduled (**PO-?6**) |
| talk changes after scheduling (R9) | entry stays, `GET` still shows it; nothing re-checked |
| Talk view (D1900) | unchanged: no `room`/`slot` field on Talk |
| trailing slash `/programme/R1/S1/` | same as without (D1652, already global) |

`start`/`end` are string constants in our code (D1702), never formatted from `LocalTime`.

## Product owner answers

None yet. Open:

| # | Question | Our default (harness precedent wins over a guess) |
|---|---|---|
| PO-?1 | Putting a talk into the room and slot it already has: `200` unchanged or `409`? | `409` (was `200`; changed at tick ~111 after acceptance PO-1 was overruled by the harness) |
| PO-?2 | `GET` of an empty room and slot: `404`, or `200` with `talkId: null`? | `404` |
| PO-?3 | `DELETE` answer: `204` empty, or `200` with the removed Entry? | `204` |
| PO-?4 | A room or slot that doesn't exist (`R4`, `S6`): `404` or `400`? | `404` |
| PO-?5 | `talkId` of a talk that doesn't exist: `404`, `400` or `409`? | `400` (was `404`; changed at tick ~111 to match `POST /talks`) |
| PO-?6 | R8 with a co-speaker added after scheduling: compare against the speakers now, or as scheduled? | now |

## Effects on existing endpoints

- None change. Talk responses gain no fields. Acceptance, co-speakers and withdrawal stay allowed for
  scheduled talks (R9); they don't touch the programme.

## Concurrency

R10. The programme is one table with the 15 fixed `(room, slot)` rows, created at start-up, `talk_id`
nullable. `PUT` locks the **three rows of the slot** (`SELECT … WHERE slot = ? FOR UPDATE`), then the
talk row (`FOR UPDATE`), then checks R4–R8 and updates. So:

- R6 and R8: two PUTs into the same slot (any room) run one after another; the second sees the first.
- R7: two PUTs of one talk into different slots both lock the talk row; the second sees the first.
- Lock order is always slot rows → talk row; acceptance and co-speakers lock only the talk row, so no cycle.
- Backstops: the write is conditional (`… AND talk_id IS NULL`; 0 rows → `409`), so a taken place is
  never overwritten (R6; the primary key can't help, the 15 rows always exist); `UNIQUE (talk_id)`
  (NULLs don't clash), a violation becomes `409` (R7).
- R8 has no constraint (it spans tables); the slot lock is the argument.
- Not covered by a lock: a co-speaker added to an already scheduled talk while another talk is put in
  that slot. That's the same as "put, then add", which R9 allows.

No advisory locks, no session `SET`.

## Evidence

- `examples/scheduling.hurl`: every rule and decision above.
- `src/test/java/no/scienta/cfp/SchedulingTest.java`: the rules over HTTP; the conditional write
  directly; concurrent PUTs: 12 talks into one place (one wins), one talk into 15 places (one wins),
  9 talks sharing a speaker into the 3 rooms of one slot (one wins), PUT racing DELETE (consistent).
- An independent review (tick ~110) checked the lock order and found no race or deadlock.
