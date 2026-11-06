---
name: ship
description: Commit verified work to main, push (which deploys), watch the deploy, and confirm the deployed version. Use only when the user asks to ship, push, or deploy.
argument-hint: <slug>
---

# Ship

Pushing to `main` deploys to the scored service. Only do it when the user has asked in this
conversation.

1. `git status` and `git diff`: commit only this feature's files plus `workflow/` and `REVIEW.md`
   updates. Never stage `.fagdag/harness-token`. Use a conventional message (`feat: <feature>`),
   with the attribution line from the system reminder.
2. `git pull --rebase` (teammates push too), then `./mvnw -q package` again if anything came in,
   then `git push`.
3. Watch the deploy: `gh run watch $(gh run list --workflow deploy.yml -L1 --json databaseId -q '.[0].databaseId') --exit-status`.
   If it fails, show the failing step's log (`gh run view --log-failed`) and
   `.github/fagdag/logs.sh --tail 100`.
4. `workflow/scripts/harness.sh health` must report deployed = origin/main.
5. Tell the user it's ready for **a person to claim** on the harness (claims are permanent; agents
   can't claim), with the evidence: verify.sh result, the rule→test table, deployed SHA. Draft the
   `REVIEW.md` claim log row (feature, tick from `harness.sh feedback`, evidence) for them to
   confirm once they've claimed.
