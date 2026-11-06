# What I have written at the prompts

My instructions to Claude in the `fagsem-2026` session, grouped by theme and in order within each group, as typed
(typos included). Slash commands are listed as typed; messages from Claude's own sub-agents are left out.

## Setup and specs

- `/rename fagsem-2026`, `/color` (twice)
- "I have curl -H "Authorization: Bearer <team token>" https://<harness>/specs/<slug>/spec.md where team token is in .fagdag/harness-token Harness-url is harness-url. Download all specs from https://fagsem-h2026-harness.scienta.cloud/specs into a new specs directory"
- `/skills`
- "The new spec is co-speakers. Analyze how the other specs are implemented and create a plan for implementation. Note tests and test coverage. Use skills and mcps if applicable"

## Building the workflow

- "Read .claude/README.md and make a workflow that will support the operations I have given so far, i.e. check for spec, download new spec, create a plan for implementation, execute plan (and other things, if I forgot)"
- "Use workflow to implement the missing skills. Start with a plan"
- "Update the spec / info with elements decided, add a test that documents this. Update workflow accordingly
  1: 200
  2: Withdrawn or reject does not count
  3: 403
  4: 404
  Continue"
- "Update workflow plan with missing steps if any."

## Test speed

- `/list-agents`
- "Analyze the tests, they take too long. Make a short plan of how to speed up build, if possible"
- "Go-ahead for 1, 2, 4"
- "The tests now run slowly again. Analyze if they can be optimized for speed. Target win, do not do comparison test. Terse"
- "Go ahead"
- "Examine tests again for speed. Choose 1 aspect to improve. Quick win. Terse"
- "Check the mentioned test again for quick fix"
- "Analyze tests for quick win on test execution speed. Focus on one test only. Terse."

## Running the feature loop

- "run the workflow loop to fetch and analyze new spec if any"
- "Take into considerations and code that comes from a collegue, so that we do not overlap in our implementation.
  NOTE: Wait with springboot-4 , code will arrive soon. Are there any other specs missing code? Be upgrade friendly if anything is implemented"
- "we have got new spec, perform workflow on it"
- "Is new spec available?"
- "Did you check https://fagsem-h2026-harness.scienta.cloud/specs ? Terse"
- "check for specs"
- "chekc specs"
- "Plan feature"
- `/plan-feature` (twice: once for workshop-registration, once for submission-cap-cr)
- "Go ahead, implement it"

## Product owner answers I relayed

- Workshop registration: "1: 400 2: 409 3: 200 4: Must look like email"
- Programme export: "1: Do what the spec says 2: Keep charset 3: 406 4: attachment; filename=programme.csv"
- Ratings change request: "1: Claim is a switch over 2: 2 decimals, i.e. 4.00"
- Co-speakers main speaker: "1: Use 200 Show plan again"

## Harness errors and fixes

- "We have an error. A speaker cannot be co-speaker for his own talk. Examine the claims that have been made to see the exact error. Check and make a terse plan before progressing."
- "Read the specs/co-speakers.md and review the plan, keep it terse"
- "Run 1,2,3,4. I want to ship myself"
- "I need to go back to previous error: "Adding the main speaker as co-speaker changes nothing " . This is still wrong. Maybe 400 is the correct code. Change it to 400. I will push, and then we need to see if it corrects the check"
- "I have pushed the code, it is just built, not sure when the external page updates"
- "1: Do it 2: Show me the plan again"
- "Read the claims page, and follow up workshop registration errors"
- "Do not need to fix scheduling, an collegue does this.
  we have got new spec, perform workflow on it"

## Pushing and checking

- "I have pushed" (several times)
- "The code has been pushed. Check the remote status"
- "git pull", "Refresh folders"
- "I have pushed. No need to check, we are finished."

## Code quality and structure

- "Analyze java code and improve code following best practices for modern code" (interrupted)
- "Improve code structure. Put Daos in a dao package, model in model package, etc. Make everything sensible"
- "Create a list of my instructions given to claude in a markdown file. Use bullet points. "What have I written at prompts?""
