# Our process: from feature request to claim

One loop per feature (or change request), plus a shorter track for improvements that aren't
features. Each step has a skill in `.claude/skills/` that an agent runs, and a human checkpoint
where noted. `/feature <slug>` runs the whole feature loop and stops at every checkpoint.

```
 start ─> sync ─> spec ─> plan ─> implement ─> verify ─> ship ─> CLAIM (human) ─> watch ─> log
   │       │       │ ▲     │ ▲         ▲         │  │      │                        │
   │       │       │ └PO?  │ └approve  └─ fix ───┘  │      └ smoke on deployed       ├ FAULTY → triage → implement
   │       │       └ /po-answers                    └ review findings                └ regression → rollback
   └ git pull, teammates' work                                               (poll: /loop /feature-status)

 improvement track:  analyse ─> plan ─> approve ─> implement ─> measure ─> verify ─> ship      (/improve)
 end of day:         /wrap-up  (REVIEW.md sections 1-6 before 17:00)
```

## Feature loop

| # | Step | Skill | Output | Gate |
|---|---|---|---|---|
| 0 | **Start**: `git pull --rebase`; what have teammates done (git log, `workflow/specs`, `workflow/plans`)? | `/sync-specs` | up-to-date tree | no duplicate work |
| 1 | **Sync**: what's released or changed? | `/sync-specs` | `specs/<slug>.md` (organisers' copy), `specs/<slug>.diff` when changed | - |
| 2 | **Spec**: rules + our decisions (status codes, edge cases, concurrency) | `/plan-feature` | `workflow/specs/<slug>.md` from [templates/feature-spec.md](templates/feature-spec.md) | open **PO?** questions go to a person, numbered with our default |
| 2b | **PO answers**: a person asks the product owner; the answers are recorded | `/po-answers <slug> <answers>` | "Product owner answers" table in `workflow/specs/<slug>.md`, decision tables updated, plan's test table gains a `PO-n` row | every answer has a test tagged `// PO-n` |
| 3 | **Plan**: files to touch, tests to write, in order | `/plan-feature` | `workflow/plans/<slug>.md` from [templates/plan.md](templates/plan.md) | **human approves the plan** |
| 4 | **Implement**: tests first (JUnit + Hurl), then code | `/implement-feature` | code, `src/test/.../<Feature>Test.java`, `examples/<slug>.hurl` | tests red before, green after; `verify.sh --quick` |
| 5 | **Verify**: the full gate + an independent review | `/verify-feature` | `workflow/scripts/verify.sh` output; rule → code → test table from a review agent | every rule has a test; script passes |
| 5b | **Fix review findings**: each finding is fixed (with a test) or recorded as a decision in our spec | `/verify-feature` → `/implement-feature` | stronger tests, spec rows for accepted behaviour | full gate passes again |
| 6 | **Ship**: group the commits, push, deploy, smoke-test the deployed service | `/ship` | one commit per feature/topic on `main`, deploy run green, `/health` = new SHA, the feature's Hurl file green against `TEAM_URL` (if the user agrees) | **human says push** |
| 7 | **Claim** | (a person, on the harness) | claim button | evidence from steps 5-6 shown to the person |
| 8 | **Watch**: the new feature's checks **and every other feature's**: a change (e.g. a PO answer) can affect claimed features | `/feature-status`, polled with `/loop 10m /feature-status` | `harness.sh feedback` | all checks OK; no score bleeding |
| 9a | **Triage** a FAULTY check | `/triage-check` | failing request from transcript + logs, a reproducing test, fix | back to step 4 |
| 9b | **Roll back** when a deploy breaks claimed features and the fix isn't quick | `/ship` (rollback) | `git revert <sha>` + push, after the user agrees | checks OK again |
| 10 | **Log**: REVIEW.md claim log / "harness caught" | (every skill appends) | `REVIEW.md` | - |

## Improvement track

For work that isn't a feature request (build speed, logging, tooling, refactoring): `/improve <topic>`.

| Step | Output | Gate |
|---|---|---|
| Analyse: measure first (timings, logs, failure counts) | numbers in `workflow/plans/<topic>.md` | - |
| Plan: numbered steps, biggest win first, risks | `workflow/plans/<topic>.md` | **human picks the steps** |
| Implement + measure again | before/after table in the plan | the same tests still pass |
| Verify, ship | as steps 5-6 above | **human says push** |

## End of day

`/wrap-up` before 17:00: every `REVIEW.md` heading has text. That means the claim log, what the
harness caught, reusable artifacts and one thing we'd do differently, plus links to `workflow/` and
`.claude/` in section 1.

## Tools

- [`scripts/harness.sh`](scripts/harness.sh): `list`, `sync`, `spec`, `feedback`, `timeline`, `health`.
  Rate-limited and never prints the token.
- [`scripts/verify.sh`](scripts/verify.sh): `./mvnw package`, then the local service on Compose
  Postgres and every `examples/*.hurl` against it. `--deployed` also runs them against `TEAM_URL`.
  `--quick` skips `HealthTest` and runs Maven offline, for the implement loop. Verify and ship run the full gate.
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
- Plans carry a status (`draft` → `approved` → `done`) and a "Done when" checklist that is ticked
  as the steps pass.
- Local runs must match production: the service runs on JDK 11 (or 17) in `verify.sh`, because
  JDK 20+ formats dates differently.
