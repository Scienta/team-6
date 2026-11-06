---
name: triage-check
description: Debug a FAULTY harness check - find the failing request from the transcript and logs, reproduce it in a test, and fix it. Use when a check is FAULTY, the service is down, or the harness reports a failure.
argument-hint: <feature/check-n>
---

# Triage a harness check

1. `workflow/scripts/harness.sh feedback <feature>` and
   `workflow/scripts/harness.sh timeline <feature/check-n>`. Note the failing request (method and
   templated path), the transcript (what we answered), and when it started failing. Did it start
   with one of our deploys? Compare with `git log --since`.
2. `.github/fagdag/logs.sh --tail 500 --since <time of the faulty run>`. Find the request log lines
   (`http : METHOD /path -> status ... req=... res=...`) and any stack trace around them.
3. Work out what the check expected from `specs/<feature>.md` and `workflow/specs/<feature>.md`. If
   our decision differs from what the check seems to want, flag it: a person may ask the PO.
4. **Reproduce** with a failing JUnit test (and a hurl entry), then fix through
   `/implement-feature`. Update `workflow/specs/<feature>.md` if a decision changed.
5. After `/verify-feature` and `/ship`, confirm with `harness.sh feedback <feature>` on the next
   tick. Add an entry to section 4 of `REVIEW.md` ("What the harness caught that we didn't"): the
   check, the cause, and why our review missed it.
