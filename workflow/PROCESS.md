# Our process: from feature request to claim

One loop per feature (or change request). Each step has a skill in `.claude/skills/` that an agent
runs, and a human checkpoint where noted. `/feature <slug>` runs the whole loop and stops at every
checkpoint.

```
 sync ──> spec ──> plan ──> implement ──> verify ──> ship ──> CLAIM (human) ──> watch ──> log
  │        │  ▲      │ ▲                    │  ▲                                  │
  │        │  └ PO?  │ └ human approves     │  └ fix (back to implement)          └ FAULTY → triage
  └ /sync-specs      /plan-feature           /verify-feature   /ship              /triage-check
```

| # | Step | Skill | Output | Gate |
|---|---|---|---|---|
| 1 | **Sync**: what's released or changed? | `/sync-specs` | `specs/<slug>.md` (organisers' copy), `specs/<slug>.diff` when changed | - |
| 2 | **Spec**: rules + our decisions (status codes, edge cases, concurrency) | `/plan-feature` | `workflow/specs/<slug>.md` from [templates/feature-spec.md](templates/feature-spec.md) | open **PO?** questions go to a person |
| 2b | **PO answers**: a person asks the product owner; the answers are recorded | `/po-answers <slug> <answers>` | "Product owner answers" table in `workflow/specs/<slug>.md`, decision tables updated, plan's test table gains a `PO-n` row | every answer has a test tagged `// PO-n` |
| 3 | **Plan**: files to touch, tests to write, in order | `/plan-feature` | `workflow/plans/<slug>.md` from [templates/plan.md](templates/plan.md) | **human approves the plan** |
| 4 | **Implement**: tests first (JUnit + Hurl), then code | `/implement-feature` | code, `src/test/.../<Feature>Test.java`, `examples/<slug>.hurl` | tests red before, green after |
| 5 | **Verify**: the full gate | `/verify-feature` | `workflow/scripts/verify.sh` output; spec-vs-code review | every rule has a test; script passes |
| 6 | **Ship**: commit, push, deploy | `/ship` | commit on `main`, deploy run green, `/health` = new SHA | **human says push** |
| 7 | **Claim** | (a person, on the harness) | claim button | evidence from steps 5-6 shown to the person |
| 8 | **Watch**: harness checks for the feature | `/feature-status` | `harness.sh feedback <slug>` | all checks OK |
| 9 | **Triage** a FAULTY check | `/triage-check` | failing request from transcript + logs, a reproducing test, fix | back to step 4 |
| 10 | **Log**: REVIEW.md claim log / "harness caught" | (every skill appends) | `REVIEW.md` | - |

## Tools

- [`scripts/harness.sh`](scripts/harness.sh): `list`, `sync`, `spec`, `feedback`, `timeline`, `health`.
  Rate-limited and never prints the token.
- [`scripts/verify.sh`](scripts/verify.sh): `./mvnw package`, then the local service on Compose
  Postgres and every `examples/*.hurl` against it. `--deployed` also runs them against `TEAM_URL`.
- `.github/fagdag/logs.sh --tail 200 --since <RFC3339>`: the deployed container's logs, including
  our request log lines (`http : METHOD /path -> status ... req=... res=...`).

## Conventions

- `specs/` is the organisers' text, untouched. `workflow/specs/` is ours: the decisions.
- Rule ids `R1..Rn` in our spec are reused in test names, Hurl comments and the plan, so a reviewer
  can trace every rule to a test.
- Product owner answers are numbered `PO-1..n` per feature. The answer, the tick it was asked and
  the test that documents it go in the spec's "Product owner answers" table. The test carries a
  `// PO-n` comment and the Hurl entry a `# PO-n` comment. An answer can change an existing
  feature (e.g. which talks count towards a limit): that feature's spec gets a pointer to the
  answer, and its tests are re-run.
- A change request (a spec whose `Version:` goes up) goes through the same loop. The plan starts
  from `specs/<slug>.diff`.
