# Programme export: our spec

Feature request: `specs/programme-export.md` (V1, released at tick 133). The rules and risks are in
[DESIGN.md](../DESIGN.md) **D2000–D2086**, written by a teammate; this file adds our decisions and
the product owner's answers. Open questions are marked **PO?**.

## Rules (from the feature request, = D2000–D2012)

| # | Rule | D-rule |
|---|---|---|
| R1 | `GET /programme` (JSON) and `GET /programme.csv` (CSV), read-only, `200` | D2000 |
| R2 | One row per scheduled talk, nothing else; same rows, same order in both | D2001 |
| R3 | Order: earliest start first (S1→S5), then room R1, R2, R3 | D2002 |
| R4 | Row: `slot, start, end, room, talkId, title, format, speakers`, talk and speaker data **as they are now** | D2003, D2005 |
| R5 | `speakers`: names, main speaker first, then co-speakers in the order added; CSV joins them with `"; "` | D2004, D2010 |
| R6 | CSV: UTF-8 (no BOM), `text/csv` even when the client asks for JSON, exact header line | D2006, D2007 |
| R7 | CSV per RFC 4180: quote only when a field has `,` `"` or a line break, double inner `"` | D2008 |
| R8 | CSV lines end in `\n` (not `\r\n`), every line incl. the last, no blank line at the end | D2009 |
| R9 | Empty programme: `[]`; CSV = the header line alone | D2011 |

## Our decisions

Both endpoints are read-only: `200`, except `406` for `GET /programme` when the client won't take JSON (PO-3).

| Case | Answer | D-rule |
|---|---|---|
| a withdrawn talk still on the programme (PO-1) | included: "one row per scheduled talk", and scheduling keeps it placed | D2050 |
| a co-speaker added after scheduling makes one person speak in two rooms | shown as it is now; the export doesn't re-check scheduling rules | D2051 |
| CSV `Content-Type` (PO-2) | `text/csv;charset=UTF-8` | D2052 |
| `GET /programme` with an `Accept` that excludes JSON (e.g. only `text/csv`) (PO-3) | `406`. No `Accept`, `*/*` or `application/json` → JSON. | D2053 |
| `GET /programme.csv` with `Accept: application/json` | CSV, `200` (the spec says so; no `produces =`, see D2080) | D2006, D2080 |
| `"; "` inside a speaker name; a field that starts with `=` `+` `-` `@` | written as it is (the spec has no escaping) | D2054, D2055 |
| `Content-Disposition` on the CSV (PO-4) | `attachment; filename=programme.csv` | D2056 |
| trailing slash | works (`TrailingSlashConfig`) | D2084 |

## Product owner answers

| # | Question | Answer | Asked (tick) | Test |
|---|---|---|---|---|
| PO-1 | A withdrawn talk still on the programme: include or leave out? | do what the spec says: include | 140 | `ProgrammeExportTest#withdrawnTalkStaysListed` |
| PO-2 | CSV `Content-Type`: `text/csv;charset=UTF-8` or exactly `text/csv`? | keep the charset | 140 | `ProgrammeExportTest#csvIgnoresAcceptAndIsUtf8`, hurl "# PO-2" |
| PO-3 | `GET /programme` with `Accept: text/csv`: JSON anyway or `406`? | `406` | 140 | `ProgrammeExportTest#jsonViewIs406WhenTheClientWontTakeJson`, hurl "# PO-3" |
| PO-4 | `Content-Disposition` on the CSV? | `attachment; filename=programme.csv` | 140 | `ProgrammeExportTest#csvIgnoresAcceptAndIsUtf8`, hurl "# PO-4" |

Precedent check (`/verify-feature` 3b): no harness-verified precedent for PO-3 (`406`) or PO-4.
Watch both after the claim. PO-1 and PO-2 follow the spec's text.

## Evidence

- `src/test/java/no/scienta/cfp/ProgrammeExportTest.java`: every rule. The CSV is compared
  **byte for byte** against an expected string built from the spec (header, quoting, `\n`, UTF-8
  name), and JSON and CSV are checked to have the same rows in the same order.
- `examples/programme-export.hurl`.
