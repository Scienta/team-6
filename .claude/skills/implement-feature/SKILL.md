---
name: implement-feature
description: Execute an approved plan in workflow/plans/<slug>.md, tests first. Use when asked to implement a feature, execute a plan, or fix something the plan or a triage found.
argument-hint: <slug>
---

# Implement a feature

1. Read `workflow/plans/<slug>.md` and `workflow/specs/<slug>.md`. If the plan isn't `approved`,
   ask before going on.
2. **Tests first.** Add the JUnit tests from the plan's test table (Spring Boot `RANDOM_PORT`,
   `TestDatabase.fresh(registry)`, plain `HttpClient`, like `CoSpeakersTest`), named after the rule
   (`mainSpeakerCannotCoSpeak` for R3). Add or extend `examples/<slug>.hurl` with a `# Rn ...`
   comment per entry and `{{newUuid}}` in emails so it can run repeatedly. Run
   `./mvnw -q test -Dtest=<Feature>Test` and confirm the new tests fail for the right reason.
3. **Code**, layer by layer as the plan says. Keep to the codebase's style: Java 25, Spring Boot
   4.1, `JdbcTemplate`, `ResponseStatusException`. New schema goes at the bottom of `schema.sql`
   with `IF NOT EXISTS`. Rules that must hold under concurrency lock a row with
   `SELECT … FOR UPDATE` inside `@Transactional` and have a constraint as a backstop.
4. Re-run the feature's tests until green, then `workflow/scripts/verify.sh --quick` (all tests except
   `HealthTest`, plus every Hurl file). The full gate is `/verify-feature`'s job.
5. Tick off the plan's checklist, note deviations in the plan, and hand over to `/verify-feature`.
   Don't commit or push: that's `/ship`.
