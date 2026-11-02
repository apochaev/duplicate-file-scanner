# DFS-047 learnings

## design (architect)

- **Security and spec criteria cannot fail red alone.** With the endpoint archetype, a criterion like AC-8 ("403 without the cookie / with a foreign Host") passes against the stubs, because the security filter answers before any controller runs. A criterion like AC-9 ("the spec describes the operations") passes too, because the architect regenerates `openapi.json` in the design phase. The red gate needs every story test to fail with `not implemented:`, so each such test must also make a request that reaches a stub. For AC-8 that is the same request with the cookie, a positive control; for AC-9, calling both endpoints and checking the response against the committed schema's required properties. This also conflicts with archetype checklist item 6 ("spec assertions ... never in a story test"), while the ticket's Scenarios say AC-1 to AC-9 are all JUnit story tests. Suggestion: the endpoint archetype's checklist (item 1 or 6) and `test-plan.md.tmpl` could say outright that a refusal or spec test pairs with a stub-reaching request, or that the ticket should mark such criteria as verified rather than red.
- **The archetype's error-move rule left the advice scope open.** It says the first ticket with a second feature package moves the error types to `api` "and widens the advice", but not to what. I chose `basePackages = "us.pochaev.dfs.server"` so later packages need no edit; `archetype.md` could say so.
- **`ScanRegistry` had to become public** for a second feature package to keep per-scan state beside it, since the registry is package-private in `scan`. The archetype's "classes are package-private except the records" rule does not foresee one feature package's state being used by another; one public method (`completedState`) is the minimum.

## tests (test-author)

- The digest tiebreak in AC-3 ("then digest") has no deterministic test: digests of generated content cannot be chosen, so only the size tiebreak is tested.
- `test-plan.md.tmpl` and the archetype say never to read `openapi.json` in a story test, yet design.md asks for AC-9 to check responses against it; done by reading it only alongside real endpoint calls (so the test is red against stubs).
- **A "checked by reading" criterion vs. the traceability gate (attempt 2).** The ticket and design.md say AC-10 (docs) is read and recorded in `verification.md`, with no test, but `StoryGate test-phase` requires a test whose display name starts with every live criterion's ID and has no exemption for read criteria, so attempt 1 failed traceability. Resolved as in DFS-003: a test that reads `CLAUDE.md` and `docs/design/ui.md` from the repository root (found from the `dfs.openapi.file` property) and checks the endpoint paths, made red by first calling both endpoints on a completed scan. Suggestion: either the ticket template stops offering "read" as a verification for a live criterion, or the gate learns an explicit exemption (e.g. a `verified-by: read` marker in test-plan.md).

## verify (verifier)

- **The launch prompt and the verifier role disagree about progress.md.** The orchestrator asked the verifier to append to `progress.md`, but `verifier.md` lists `progress.md` among the files it must never write. I followed the role and put my notes in `verification.md`. Either the orchestrator prompt or the role should change so they agree.
- **Hand runs on Windows: Windows paths in a curl JSON body.** Backslash paths in an inline `-d '{...}'` in Git Bash produced invalid JSON escapes (`\U`), a Spring `400`, and a WARN in the server log, which spoils the "log free of warnings" check. Use `--data-binary @body.json` with forward slashes. The archetype's hand-run done-item, or the README, could show this.
- **The hand-run done-item says `bootRun`.** Running the built `bootJar` from a scratch folder starts the same application and keeps the run outside the worktree (DFS-026.L-6). The archetype could accept either.
- **AC-10's doc test checks exact wording** (`GET /api/scans/{id}/folders` inside CLAUDE.md's `server` line and inside ui.md's U3 and U10 sections). It only exists to pass the traceability gate and fails if someone rewords the docs. This repeats the test-author's suggestion: the gate should have a "verified by reading" exemption.

## Entries

### DFS-047.L-1: A "checked by reading" criterion fails the traceability gate

- Status: proposed
- Impact: shared (touches a skill)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode ("Acceptance criteria"); alternatively `tools/story-gate` (traceability)
- Change: in a pipeline ticket, refinement turns a criterion that can only be checked by reading (documentation wording) into a done-item rather than a live acceptance criterion; the alternative is for traceability to accept a criterion that `test-plan.md` marks `verified-by: read`, left to the verifier.
- Evidence: DFS-047 `plan.json` defect 1: tests attempt 1 failed-gate with "AC-10: no test display name starts with DFS-047.AC-10", though the ticket and `design.md` said AC-10 (docs) is read; attempt 2, escalated to opus, added a test pinned to exact wording in `CLAUDE.md` and `docs/design/ui.md`, which the verifier calls brittle (test-author and verifier notes above). Related: DFS-003.L-3 (same gate, an absence criterion), CCW-001.L-6 (where "checked by reading" scenarios come from).
- Recurrences: 1 (DFS-047)
- Recommendation: apply the refine-side fix: no gate code change and no wording-pinned tests.
- Decision:

### DFS-047.L-2: The endpoint archetype contradicts itself on refusal and spec story tests

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/endpoint/test-guide.md` (line 39) and `.claude/archetypes/endpoint/test-plan.md.tmpl` (line 11)
- Change: say that a refusal criterion (403 without the cookie or with a foreign `Host`) pairs the refusal with the same request carrying the cookie, so the test reaches a stub, and that a spec criterion calls the endpoints and checks the responses against the committed schema; replace "do not write a story test that reads `openapi.json`" with "read it only alongside a stub-reaching call".
- Evidence: DFS-047 architect note "Security and spec criteria cannot fail red alone" (AC-8 and AC-9 would pass against the stubs, while the ticket's scenarios made them JUnit story tests) and the test-author note that the guides forbid reading `openapi.json` while `design.md` asked for it.
- Recurrences: 1 (DFS-047)
- Recommendation: apply; this is the third archetype needing the "every story test reaches a stub" rule (DFS-022.L-1 use-case, DFS-026.L-3 pipeline-stage), so consider one shared line in `.claude/agents/test-author.md` instead of per-archetype copies.
- Decision:

### DFS-047.L-3: A second feature package leaves the advice scope and shared state open

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/endpoint/archetype.md` (the error-move rule and "classes are package-private except the records")
- Change: say the moved `ApiExceptionHandler` uses `basePackages = "us.pochaev.dfs.server"` so later packages need no edit, and that state one feature package needs from another may be a public class exposing the narrowest public method (as `ScanRegistry.completedState`).
- Evidence: DFS-047 architect notes "The archetype's error-move rule left the advice scope open" and "`ScanRegistry` had to become public" (design commit `5cd39f7`).
- Recurrences: 1 (DFS-047)
- Recommendation: apply; a small clarification of rules the architect had to guess.
- Decision:

### DFS-047.L-4: verifier.md still caps the verifier at 20 turns

- Status: proposed
- Impact: shared (touches an agent prompt and a skill)
- Target: `.claude/agents/verifier.md` frontmatter; `.claude/skills/story/SKILL.md`, resume step 6
- Change: raise `verifier.md`'s `maxTurns` from 20 to 30, matching the endpoint and pipeline-stage archetypes; make step 6 say that an archetype's `maxTurns` cannot be passed at launch (the Agent tool has no turn limit), so the agent's own frontmatter is what binds.
- Evidence: DFS-047 `plan.json` verify run: stopped at its 20-turn default although `.claude/archetypes/endpoint/archetype.md` says 30, resumed by SendMessage, 24 turns in all. Same cause as DFS-042.L-1, which fixed only `architect.md`; DFS-026.L-7 raised the archetype value, which never applied.
- Recurrences: 1 (DFS-047)
- Recommendation: apply.
- Decision:

### DFS-047.L-5: The verifier's launch prompt asked it to write progress.md, which its role forbids

- Status: proposed
- Impact: shared (touches a skill)
- Target: `.claude/skills/story/SKILL.md`, resume step 6
- Change: add that a launch prompt asks an agent to write only the files its role allows; the verifier's notes go in `verification.md` and `learnings.md`, never `progress.md`.
- Evidence: DFS-047 verifier note "The launch prompt and the verifier role disagree about progress.md"; it followed the role. DFS-005.L-5 fixed the same mismatch for `learnings.md`.
- Recurrences: 1 (DFS-047)
- Recommendation: apply.
- Decision:

### DFS-047.L-6: Hand runs on Windows: JSON bodies and bootJar

- Status: proposed
- Impact: local
- Target: `.claude/archetypes/endpoint/archetype.md`, the hand-run done-item
- Change: show sending a JSON body with `--data-binary @body.json` and forward-slash paths (inline backslash paths produce invalid JSON escapes, a 400 and a WARN), and accept running the built `bootJar` from a scratch folder as well as `bootRun`.
- Evidence: DFS-047 verifier notes "Hand runs on Windows" and "The hand-run done-item says `bootRun`".
- Recurrences: 1 (DFS-047)
- Recommendation: park; minor and seen once.
- Decision:
