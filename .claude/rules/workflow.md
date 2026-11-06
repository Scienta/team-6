# Team workflow rules

- Our process is `workflow/PROCESS.md`. The skills in `.claude/skills/` run its steps:
  `/feature` (the whole loop), `/sync-specs`, `/plan-feature`, `/po-answers`, `/implement-feature`,
  `/verify-feature`, `/ship`, `/feature-status`, `/triage-check`; `/improve` for non-feature work
  (measure, plan, the user picks steps); `/wrap-up` for `REVIEW.md` before 17:00.
- Talk to the harness with `workflow/scripts/harness.sh` (rate-limited; reads the token itself).
  Never print, echo or commit `.fagdag/harness-token`.
- Agents never claim and never say a feature is done without evidence the user can check:
  `workflow/scripts/verify.sh` output, the rule→test table, and the deployed SHA from
  `harness.sh health`.
- Push to `main` only when the user asks: a push deploys the scored service.
- `specs/` holds the organisers' text, untouched. Our decisions go in `workflow/specs/<slug>.md`,
  plans in `workflow/plans/<slug>.md`, examples in `examples/<slug>.hurl`.
- Code style: Java 25 / Spring Boot 4.1 (since the spring-boot-4 upgrade), in the codebase's existing
  style (`JdbcTemplate`, `ResponseStatusException`, Controller → `ConferenceService` → DAO).
- Formats the specs fix (dates, `ratedAtDisplay`, trailing slashes, JSON leniency) are written
  explicitly in our code, never left to JVM, locale or Jackson defaults; `UpgradeContractTest` and
  `examples/spring-boot-4.hurl` pin them. See `workflow/specs/spring-boot-4.md`.
- `REVIEW.md` is a running log: add the claim log row when a person claims, and a section 4 entry
  when the harness catches something we missed.
