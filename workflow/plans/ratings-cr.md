# Plan: ratings-cr

Change request: `specs/ratings-cr.md` (V1), our decisions: `workflow/specs/ratings-cr.md`, rules
in `workflow/DESIGN.md` D1204, D1206 (changed in place), D1208. Status: approved (tick 171, PO-1..2 answered).

## Current state

- Ratings is a claimed base feature, all checks OK (tick 167). The change request is unclaimed.
  Nobody else is working on it.
- `RatingDao.statsByTalk` returns `{count, sum}` per talk in one query. `ConferenceService.enrich`
  calls `calculateAverage(total, count)`: `BigDecimal`, scale 1, `HALF_UP`. The ranking in
  `listTalks` compares `averageRating`.
- Old rounding is pinned in: `RatingsTest` (`4.5` across views, a parameterised average table),
  `FormatsTest#averageRatingRoundsHalvesUp` (`4.3`), `#averageRatingOfAWholeNumberIsWrittenWithOneDecimal`
  (`4.0`), `UpgradeContractTest` P11 (`4.3`), `AdminReportsTest` (`/admin/stats` `4.1`, which stays),
  `examples/ratings.hurl` (`4.5`), `examples/spring-boot-4.hurl` (`4.3`).

## Changes

| Layer | File | Change |
|---|---|---|
| DAO | `RatingDao.statsByTalk` | also `MAX(score)`, `MIN(score)` → `{count, sum, max, min}` (same single query) |
| service | `ConferenceService.calculateAverage` | `(count ≥ 5 ? (sum − max − min, count − 2) : (sum, count))`, `BigDecimal.divide(…, 2, HALF_EVEN)`, kept as a `BigDecimal` (scale 2, PO-2). `TalkDto.averageRating` becomes `BigDecimal`; the ranking uses `compareTo`, and `/admin/stats` uses `doubleValue()` (its result is unchanged). A `static` method, so it can be unit-tested with the spec's tie examples. |
| service | ranking | unchanged code; it already compares `averageRating`, which is now the new value (R4) |
| other | `/admin/stats`, CSV | no change (D1208) |

## Tests (written first)

| Rule / decision | JUnit | Hurl `examples/ratings-cr.hurl` |
|---|---|---|
| R2 banker's rounding on exact values: `3.125` → `3.12`, `3.375` → `3.38`, `4.25` stays `4.25`, `10/3` → `3.33` | `RatingsCrTest#averageIsRoundedToTwoDecimalsHalfToEven` (unit test of `calculateAverage`) | - |
| R2 over HTTP: 10 ratings, so 8 count after trimming: trimmed sum 25 → `3.125` → `3.12`; 27 → `3.375` → `3.38` (a tie needs 8 counted scores) | `RatingsCrTest#tiesGoToTheEvenNeighbourOverHttp` | ✔ |
| R1 4 ratings: all count (`1,2,5,5` → `3.25`); 5 ratings: trimmed (`1,2,5,5,5` → `4.0`) | `RatingsCrTest#trimmedFromFiveRatings` | ✔ |
| R1 exactly one of each even when tied (`1,1,5,5,5,5` → `(1+5+5+5)/4 = 4.0`) | `RatingsCrTest#leavesOutExactlyOneHighestAndOneLowest` | ✔ |
| R3 `ratingCount` counts all (6 above → `6`) | in the R1 tests | ✔ |
| R4 ranking flips because of trimming (outlier talk) | `RatingsCrTest#rankingUsesTheNewAverage` | ✔ |
| a replaced rating recalculates | `RatingsCrTest#replacingARatingRecalculates` | - |
| existing pins | update to the new values with `// ratings-cr` comments: `4.3` → `4.25`, `4.0` → `4.00`, `4.5` → `4.50` (check the raw JSON text; the tests' Jackson 2 tree drops trailing zeros) |
| PO-2 always two decimals in the raw JSON (`"averageRating":4.00`) | `RatingsCrTest#averageIsAlwaysWrittenWithTwoDecimals` | ✔ | ✔ (`ratings.hurl`, `spring-boot-4.hurl`) |

## Risks and open questions

- PO-1: the claim switches the checks → push, wait for `/health`, claim at once (see DESIGN D1210).
- PO-2: always two decimals (`4.00`).
- Bleed risk: ratings is claimed. A wrong switch-over turns it FAULTY. Watch ratings *and*
  ratings-cr after the deploy.
- `FormatsTest#averageRatingOfAWholeNumberIsWrittenWithOneDecimal` (a teammate's pin, `4.0`) may need
  to change depending on PO-2.

## Done when

- [x] every rule has a test, failing before the change; the old pins are updated
- [x] `workflow/scripts/verify.sh` passes (JDK 27 standing in for 25)
- [ ] deployed `/health` reports the new SHA; ratings **and** ratings-cr checks OK; claim log in `REVIEW.md`
