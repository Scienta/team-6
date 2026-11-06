# Plan: <feature>

Spec: `specs/<slug>.md` (V<n>), our decisions: `workflow/specs/<slug>.md`. Status: draft | approved | done.

## Current state

What already exists (code, tables, tests), and what the harness says (`harness.sh feedback <slug>`).

## Changes

Layer by layer, following the existing pattern (Controller → `ConferenceService` → DAO → `schema.sql`):

| Layer | File | Change |
|---|---|---|
| schema | `src/main/resources/schema.sql` | new statements at the bottom, `IF NOT EXISTS` |
| DAO | ... | ... |
| service | `ConferenceService.java` | rules, status codes, `@Transactional`, row lock |
| controller | ... | route |
| response | `TalkDto` / `enrich` | new fields on every view |

## Tests (written first)

| Rule / decision | JUnit test | Hurl entry |
|---|---|---|
| R1 | `...Test#...` | `examples/<slug>.hurl` "# R1 ..." |
| concurrency | `...Test#concurrent...` | - |

## Risks and open questions

- **PO?** ...
- Regressions in existing features: which existing tests and hurl files cover them.

## Done when

- [ ] every rule and decision has a test, and the tests failed before the change
- [ ] `workflow/scripts/verify.sh` passes
- [ ] deployed `/health` reports the new SHA
- [ ] a person has claimed it; the claim log in `REVIEW.md` has the evidence
