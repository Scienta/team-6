# Plan: faster build and tests

Status: steps 1, 2, 4 done (step 3, parallel forks, not approved). Measured on a laptop at tick ~40 (`target/surefire-reports`).

## Where the time goes (~75 s of tests; compile is ~2 s; the deploy skips tests)

| Cost | Seconds | Why |
|---|---|---|
| `HealthTest#probesThatTimeOut…` | 15 | 3 probes × the 5 s DB timeout against a paused container |
| `HealthTest` own container + Spring context | ~16 | a second Postgres container (it pauses it, so it can't share) |
| `HealthTest#…WithinFiveSeconds…` | 5 | one 5 s timeout |
| First test class (whichever runs first) | ~18 | starts the shared Testcontainers Postgres |
| Every other test method | ~0.3–1 each, ~20 total | real HTTP + DB work; fine |

Tests run one class at a time in one JVM.

## Steps (biggest win first)

1. **Reuse the shared container** (−15 s): `TestDatabase` gets `.withReuse(true)`.
   `~/.testcontainers.properties` already has `testcontainers.reuse.enable=true` on this laptop.
   The database names become `cfp_<uuid>` instead of `cfp_<counter>`, so a reused container doesn't
   hit existing names. Without the property (other laptops), behaviour is as today.
2. **Make the health timeout a property** (−16 s): `health.db-timeout-seconds` (default **5**,
   the deploy contract) replaces the two hard-coded `5`s in `HealthController`. `HealthTest` sets
   it to 1 and checks `took < timeout + 1 s`. A small test asserts that the default is 5, so the
   contract stays tested.
3. **Run test classes in 2 forks** (wall time ≈ the slowest fork, ~20 s instead of ~40 s):
   surefire `forkCount=2`, `reuseForks=true`. It has to be separate JVMs, not JUnit in-JVM
   parallelism, because `HealthTest` counts `health-check` threads JVM-wide and the executor is
   static. Every class already has its own database, and step 1 makes the names unique across forks.
4. **`verify.sh --quick`** for the implement loop: `-Dtest='!HealthTest'` and `./mvnw -o`
   (offline). `/implement-feature` uses `--quick`; `/verify-feature` and `/ship` run the full gate.

## Expected

Full `./mvnw package`: ~75 s → ~25 s. The quick loop: ~12 s. No change to what's deployed.

## Risks

- Reused containers keep old test databases. Harmless, but `docker rm -f` on the container clears them.
- With forks, test logs interleave. Surefire keeps a report per class, so failures stay readable.
- Step 2 touches production code (the health check). The default and behaviour stay the same, and
  `HealthTest` plus the deploy's own `/health` check cover it.

## Result (steps 1, 2, 4)

| | Before | After |
|---|---|---|
| Test classes, summed | ~75 s | ~45 s |
| `HealthTest` | 37 s | 23 s (5 s of tests; the rest is its own container starting) |
| First class (container start) | ~18 s overhead | ~4 s (container reused) |
| `verify.sh`: build, tests, service, all Hurl | not measured | 53 s |
| `verify.sh --quick` | - | 31 s |

Next possible win: reuse `HealthTest`'s own container too (~15 s more). It needs a separate reuse
label, so it never shares the common container, and an unpause at start-up in case a killed run
left it paused.
