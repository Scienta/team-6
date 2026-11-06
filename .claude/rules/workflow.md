# Team workflow rules

- Our process is `workflow/PROCESS.md`. The skills in `.claude/skills/` run its steps:
  `/feature` (the whole loop), `/sync-specs`, `/plan-feature`, `/implement-feature`,
  `/verify-feature`, `/ship`, `/feature-status`, `/triage-check`.
- Talk to the harness with `workflow/scripts/harness.sh` (rate-limited; reads the token itself).
  Never print, echo or commit `.fagdag/harness-token`.
- Agents never claim and never say a feature is done without evidence the user can check:
  `workflow/scripts/verify.sh` output, the rule→test table, and the deployed SHA from
  `harness.sh health`.
- Push to `main` only when the user asks: a push deploys the scored service.
- `specs/` holds the organisers' text, untouched. Our decisions go in `workflow/specs/<slug>.md`,
  plans in `workflow/plans/<slug>.md`, examples in `examples/<slug>.hurl`.
- Code style: Java 11 / Spring Boot 2.7 as in the codebase (`JdbcTemplate`, `ResponseStatusException`,
  Controller → `ConferenceService` → DAO). The `fluent-java` skill targets Java 21+ and doesn't
  apply here.
- `REVIEW.md` is a running log: add the claim log row when a person claims, and a section 4 entry
  when the harness catches something we missed.
