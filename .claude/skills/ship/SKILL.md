---
name: ship
description: Commit verified work to main, push (which deploys), watch the deploy, and confirm the deployed version. Use only when the user asks to ship, push, or deploy.
argument-hint: <slug>
---

# Ship

Pushing to `main` deploys to the scored service. Only do it when the user has asked in this
conversation.

1. `git status` and `git diff`. If the tree holds several features or topics, propose one commit
   per feature/topic (with its workflow files) and ask; otherwise commit this feature's files plus
   `workflow/` and `REVIEW.md` updates. Never stage `.fagdag/harness-token`. Use a conventional message (`feat: <feature>`),
   with the attribution line from the system reminder.
2. `git pull --rebase` (teammates push too), then `./mvnw -q package` again if anything came in,
   then `git push`.
3. Watch the deploy: `gh run watch $(gh run list --workflow deploy.yml -L1 --json databaseId -q '.[0].databaseId') --exit-status`.
   If it fails, show the failing step's log (`gh run view --log-failed`) and
   `.github/fagdag/logs.sh --tail 100`.
4. `workflow/scripts/harness.sh health` must report deployed = origin/main.
4b. **Smoke on deployed** (ask first: it writes test rows to our live database):
   `hurl --test --variable base_url=<TEAM_URL> examples/<slug>.hurl` for each shipped feature.
4c. `workflow/scripts/harness.sh feedback` on the next tick: **every** claimed feature must still be
   OK, not just the new one. If a deploy broke claimed checks and the fix isn't quick, propose a
   rollback (`git revert <sha> && git push`, after the user agrees), then `/triage-check`.
5. Tell the user it's ready for **a person to claim** on the harness (claims are permanent; agents
   can't claim), with the evidence: verify.sh result, the rule→test table, deployed SHA. Draft the
   `REVIEW.md` claim log row (feature, tick from `harness.sh feedback`, evidence) for them to
   confirm once they've claimed.
