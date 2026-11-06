# <Feature>: our spec

Feature request: `specs/<slug>.md` (V<n>). The request gives the business rules and the API surface;
the decisions below (status codes, edge cases) are ours. Open questions for the product owner are
marked **PO?**.

## Rules (from the feature request)

| # | Rule |
|---|---|
| R1 | ... |

## Our decisions

Follow the existing conventions unless the spec says otherwise: 400 for invalid input, 404 for an
unknown resource in the path, 409 for a business-rule conflict, 200 for idempotent repeats.

| Case | Answer |
|---|---|
| happy path | `200`/`201`, the full resource |
| unknown id in path | `404` |
| non-numeric id in path | `400` |
| repeated request (double-click) | ... |
| rule violation | `409` |

## Effects on existing endpoints

Which existing responses gain or change fields, and which must stay unchanged.

## Concurrency

Which rule must hold under concurrent requests, which row is locked (`SELECT … FOR UPDATE`), and
which constraint backs it up. No advisory locks; no session `SET`.

## Evidence

- `examples/<slug>.hurl`: every rule and decision above.
- `src/test/java/no/scienta/cfp/<Feature>Test.java`: ...
