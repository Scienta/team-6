---
name: po-answers
description: Record the product owner's answers to open PO? questions in our feature spec and plan, and add a test per answer. Use when the user relays answers from the product owner or decides open questions.
argument-hint: <slug> <answers, e.g. "1: 200, 2: ...">
---

# Record product owner answers

1. Read `workflow/specs/<slug>.md` and `workflow/plans/<slug>.md`, and match each answer to its
   **PO?** question. Number them `PO-1..n`, continuing the spec's "Product owner answers" table.
   If an answer is ambiguous, ask before writing.
2. **Spec:** add a row per answer (question, answer, tick from `workflow/scripts/harness.sh feedback`,
   test). Update the decision/transition tables to the answer and remove the **PO?** marks.
3. **Other features:** if an answer changes another feature's behaviour (a limit, a listing, a
   status code), add a note pointing to `PO-n` in that feature's `workflow/specs/<other>.md` and
   list it in the plan's risks.
4. **Plan:** add a `PO-n` row per answer to the test table: a JUnit test named after the decision
   with a `// PO-n: <answer>` comment, and a Hurl entry with a `# PO-n` comment. Mark existing code
   that must change.
5. Add a line to section 2 of `REVIEW.md` ("How we reviewed agent output"): the PO answers for this
   feature and where they are tested.
6. Carry on with `/implement-feature` if the plan was approved; otherwise show the updated plan.
