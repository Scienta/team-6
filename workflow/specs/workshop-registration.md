# Workshop registration: our spec

Feature request: `specs/workshop-registration.md` (V1, released at tick 67). The request gives the
business rules and the API surface; the decisions below are ours. Open questions for the product
owner are marked **PO?**. Lesson from co-speakers check-2: a status code we invent for a rule the
spec only describes in words is a **PO?**, not a decision.

## Rules (from the feature request)

| # | Rule |
|---|---|
| R1 | An attendee registers for a workshop with a name and an email; both are required. |
| R2 | Only talks with format `WORKSHOP` that are `ACCEPTED` take registrations. |
| R3 | If a workshop is withdrawn later, the people already registered stay on its list. |
| R4 | At most **20** registrations per workshop, never more, also under a rush of simultaneous registrations. |
| R5 | One registration per email per workshop; emails compare case-insensitively (`ada@example.com` = `ADA@Example.com`). |
| R6 | The same person can register for several workshops. |
| R7 | `GET /workshops/{id}/registrations` lists the registrations in the order they were made. |
| R8 | A registration shows `id`, `workshopId`, `name`, and `email` **as the attendee typed it**. |

## Our decisions

Codes follow what the harness already accepts elsewhere: `400` for a bad body (speakers, talks),
`404` for an unknown id, `409` for a limit or a duplicate (3-talk limit, 2 co-speakers, duplicate
speaker email).

| Case | Answer |
|---|---|
| `POST`, allowed | `201`, the Registration |
| `name` or `email` missing, null, not a string, or blank | `400` |
| `email` that doesn't look like an email (PO-4): one `@`; no whitespace (Unicode too) or control characters; a domain of two or more non-empty labels (no leading, trailing or double dot) | `400` |
| email with surrounding spaces (`" ada@example.com "`) | `400`: not trimmed, since we show the email as typed (review finding) |
| body that isn't a JSON object (`[]`, `"x"`, malformed) | `400` (Spring) |
| non-numeric id in the path | `400` (Spring type mismatch), as every other endpoint |
| very long `name`/`email` | accepted: the spec sets no limit (review finding, accepted) |
| no body / malformed JSON | `400` (Spring) |
| talk id doesn't exist | `404` |
| talk exists but isn't a `WORKSHOP` (PO-1, revised tick 126) | `POST` → `409`, as a workshop that isn't accepted |
| workshop not `ACCEPTED` (submitted, waitlisted, rejected, withdrawn) (PO-2) | `409` |
| workshop full (20) | `409` |
| email already registered for this workshop, any letter case (PO-3, revised tick 126) | `409`, nothing changes (as a duplicate speaker email), also when the workshop is full or withdrawn |
| order of checks | `400` body/email → `404` unknown talk → `409` not a workshop → `409` already registered → `409` not accepted → `409` full |
| email format | must look like an email (PO-4), see above |
| `GET`, a workshop (any status) | `200`, the list in registration order; `[]` when none; a withdrawn workshop keeps its list (R3) |
| `GET`, unknown id | `404` |
| `GET`, a talk that isn't a workshop | `200 []`: harness `check-2` (tick 133) rejected our `400`; it lists right after the refused `POST`, as for a workshop that isn't accepted |
| trailing slash (`/workshops/{id}/registrations/`) | works like no slash (`TrailingSlashConfig`, every path) |

## Effects on existing endpoints

None. Talks don't show registrations; acceptance and withdrawal don't touch them (R3).

## Concurrency

R4 and R5 under a rush: `POST` runs in one `@Transactional` method that locks the talk row
(`SELECT format, status FROM talk WHERE id = ? FOR UPDATE`, the same lock as acceptance decisions
and withdrawals), checks duplicate, status and count, then inserts. So registrations for one
workshop run one at a time, and a registration can't race a withdrawal. Backstop: unique index on
`(talk_id, lower(email))`; it can't fire while the lock serialises registrations, and would be a `409` if it did. Pool of 5: each transaction is
a few short statements, so a rush of 30+ queues briefly on the row lock.

## Product owner answers

| # | Question | Answer | Asked (tick) | Test |
|---|---|---|---|---|
| PO-1 | A talk that isn't a workshop: 404 or 400? | `400` at tick 101; **revised to `409`** at tick 126: harness `workshop-registration/check-2` accepted our `409` for a REJECTED workshop, then failed on our `400` for an ACCEPTED `LIGHTNING_10` talk | 101, 126 | `WorkshopRegistrationTest#onlyAcceptedWorkshopsTakeRegistrations`, hurl "# PO-1" |
| PO-2 | A workshop that isn't accepted: 409, 400 or 404? | `409` | 101 | `WorkshopRegistrationTest#onlyAcceptedWorkshopsTakeRegistrations`, hurl "# PO-2" |
| PO-3 | Same email again for the same workshop: 409 or 200 with the existing registration? | `200` at tick 101; **revised to `409`** at tick 126: harness `workshop-registration/check-3` failed on our `200` for the same email in upper case | 101, 126 | `WorkshopRegistrationTest#oneRegistrationPerEmailRegardlessOfCase`, `concurrentDuplicatesRegisterOnce`, hurl "# PO-3" |
| PO-4 | Email format: non-blank only, or must look like an email? | must look like an email | 101 | `WorkshopRegistrationTest#nameAndEmailAreRequired`, hurl "# PO-4" |

Note on precedent (`/verify-feature` 3b): PO-1 `400` matches co-speakers' main-speaker case (input
that isn't allowed here). PO-2 `409` has no harness-verified precedent yet (acceptance is
unclaimed). Watch it after the claim.

## Evidence

- `examples/workshop-registration.hurl`: every rule and decision above.
- `src/test/java/no/scienta/cfp/WorkshopRegistrationTest.java`: the rules over HTTP, plus 30
  concurrent registrations for one workshop give exactly 20 `201`s and 10 `409`s, and 5 concurrent
  registrations with one email in different letter cases give one `201` and four `409`s (PO-3).
