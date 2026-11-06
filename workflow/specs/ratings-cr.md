# Ratings change request: our spec

Change request: `specs/ratings-cr.md` (V1, released at tick 167). It changes how
[Ratings](../../specs/ratings.md) calculates `averageRating` (DESIGN.md D1204, D1206). Everything
else about ratings stays. Open questions are marked **PO?**.

## Rules (from the change request)

| # | Rule | D-rule |
|---|---|---|
| R1 | With **5 or more** ratings, the average leaves out **exactly one** highest and **one** lowest score, even when several reviewers share the top or bottom score. Fewer than 5: every score counts, as before. | D1204 (changed), D1208 |
| R2 | The average is rounded to **2 decimals**, ties at the third decimal to the **even** neighbour (banker's rounding): `3.125` → `3.12`, `3.375` → `3.38`. | D1204 (changed) |
| R3 | `ratingCount` still counts every reviewer, including the two left out. | D1203 |
| R4 | `GET /talks?sort=rating` ranks by the new rounded average (then `ratingCount`, then oldest, unrated last, as before). | D1206 (changed) |
| R5 | Nothing else about ratings changes (scores, reviewers, replacing a rating, `ratedAtDisplay`, concurrency). | D1200–D1203, D1205, D1207, D1250 |

## Our decisions

| Case | Answer |
|---|---|
| how | `RatingDao.statsByTalk` also returns `MAX(score)` and `MIN(score)`. At ≥5 ratings: `(sum − max − min) / (count − 2)`. Exact integer division with `BigDecimal`, scale 2, `RoundingMode.HALF_EVEN`, so a tie is decided on the exact value (`25/8 = 3.125` → `3.12`). No floating point before rounding. |
| exactly 5 ratings | trimmed (3 count); 4 ratings → all 4 count |
| all ratings equal (e.g. 5 × `4`) | trimmed to 3 × `4` → `4.00` |
| a rating replaced (same reviewer) | recalculated from the current scores (as today) |
| JSON form of the number (PO-2) | always two decimals: `4.00`, `4.50`, `4.25` (a `BigDecimal` with scale 2). The CSV report prints `4,00`. |
| `/admin/stats` `averageRating` (no spec, D1951) | unchanged. It averages the talks' averages with its own rounding; the change request is about a talk's average. |
| `/admin/talks.csv` rating column (no spec) | follows the new value (`4,25`), as it prints the talk's average |
| switch-over (PO-1) | the claim switches the checks: push, wait for `/health`, then claim at once |

## Product owner answers

| # | Question | Answer | Asked (tick) | Test |
|---|---|---|---|---|
| PO-1 | Do the ratings checks switch at release or at claim? | at the claim | 171 | process: `/ship`, then claim in the same tick |
| PO-2 | "Two decimals" in JSON: `4.0` or `4.00`? | always two: `4.00` | 171 | `RatingsCrTest#averageIsAlwaysWrittenWithTwoDecimals`, hurl "# PO-2" |

## Evidence

- `src/test/java/no/scienta/cfp/RatingsCrTest.java`: R1–R4 with the spec's examples and the edge
  cases above. Existing pins of the old rounding (`RatingsTest`, `FormatsTest`, `UpgradeContractTest`,
  `ratings.hurl`, `spring-boot-4.hurl`) are updated to the new rule, each with a comment pointing to
  this change request.
- `examples/ratings-cr.hurl`.
