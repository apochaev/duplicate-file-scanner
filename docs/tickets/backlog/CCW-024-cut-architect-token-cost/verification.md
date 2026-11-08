# CCW-024 verification

## Product owner review

### What you can do now

The architect no longer repeats work it was refused: it reads a file with Read before changing it, instead of printing it with `cat` and having its writes refused (DFS-049 lost 19 writes this way). It finds Expected breakage by running the existing tests once, with `--continue`, instead of searching for and reading test files (about 45k of context in DFS-055). Each Java archetype names the exact test task to run.

### Acceptance criteria

- **CCW-024.AC-1:** met. `architect.md` "What to read" now requires a Read of every existing file before Write or Edit, including files an earlier attempt left in the worktree, and forbids deleting them to get around the refusal.
- **CCW-024.AC-2:** met. Step 4 runs the archetype's named task once after the stubs compile, with `--continue`, filters the output to failing classes and checks each report for `not implemented: <TICKET-ID>`.
- **CCW-024.AC-3:** met. The "Read nothing else" exception no longer covers breakage; a test may be read only when the design depends on how it is built, named in the `## Summary` with a reason ("No tests read." otherwise).
- **CCW-024.AC-4:** met. `use-case` and `pipeline-stage` name `./gradlew test --continue`, `wiring` `./gradlew :scanner-spring:test :cli:test :server:test --continue`, `endpoint` `./gradlew :server:test --continue`.
- **CCW-024.AC-5:** met. DFS-054's design phase (archetype `endpoint`, 2026-11-07), the first Java-archetype design phase after the merge, shows no `File has not been read yet`, one run of `./gradlew :server:test --continue`, and no grep or Read into a test source tree; its Summary says "No tests read." Details under "AC-5 run".

### Try it

Nothing runs by itself: these are instruction edits. Read `.claude/agents/architect.md` ("What to read", steps 4 and 6, "The `## Summary` section") and the new "Expected breakage run" bullet at the end of "Where files go" in each Java `archetype.md`. The real test is AC-5: after the merge, the next Java design phase's transcript should show no `File has not been read yet`, one test-task run, and no searching of the test trees.

### Decided without you

- The filter example in step 4 is `2>&1 | grep -E " FAILED$|tests completed"`, and the report path given is `<project>/build/test-results/test/TEST-<package>.<Class>.xml`.
- Step 6's clause "since tests don't exist yet" now reads "since the story tests don't exist yet (the existing tests run once, in step 4)", so it no longer contradicts step 4.
- The new archetype rule sits as the last bullet of each "Where files go" section, not in the edge-case checklist, since it is an instruction for the architect, not a question for refinement.

### Known limits

- AC-5 rests on one run (DFS-054). Its output fell from about 71k to 40k, but tool calls barely moved (66 against 63 and 79): the architect still reads many source files (28 Reads, among them core model types outside `touches` that its Summary names as read-only). Not a criterion here.
- The other pipeline agents (test-author, implementer, verifier) may hit the same read-before-edit refusal; not measured, out of scope.
- `story-gate selftest` failed two cases that already failed on `main` (`CCW-020.AC-1-archetype-files`, `CCW-020.AC-12-docs`, stale archetype versions); out of scope, and fixed later on 2026-11-06 outside this ticket, in `a4a1676` (the cases no longer pin a version number); `selftest` now passes all 126 cases.

### Evidence

- Commit `db54a27` on `tickets/CCW-024-cut-architect-token-cost`: `architect.md` and the four Java `archetype.md` files, versions bumped use-case 9→10, pipeline-stage 16→17, wiring 3→4, endpoint 3→4.
- `tools/story-gate/story-gate selftest --root <worktree>` on 2026-11-06: `FAIL selftest: 2 problems`, the same two as on `main` before the change; `CCW-015.AC-7-instructions`, which pins four phrases of step 4 and the Summary, passes. The two failures were fixed later on 2026-11-06 outside this ticket, in `a4a1676` (the cases no longer pin a version number); `selftest` now passes all 126 cases.
- Search for other mentions (CCW-018.L-1), `grep -rn "Expected breakage\|grep -rl\|search the test\|their callers" .claude CLAUDE.md docs/design`, excluding the `design.md.tmpl` files: `architect.md` (changed), the four Java `archetype.md` files (changed), `component/archetype.md` checklist item 3 (already says run `:server:npmTest`; consistent), `pipeline-stage/test-guide.md:37` (test-author guidance on output literals; unrelated), the tickets skill's refine rule (about refinement; unrelated). The five `design.md.tmpl` files describe the section's format, not how it is found; unchanged.

### How it went

Implemented by hand (archetype `none`) in one sitting after refinement; one wording fix (step 6) found while reviewing the diff before commit. No pipeline phases, retries or escalations.

AC-5 checked on 2026-11-07 from DFS-054's architect transcript; every criterion is met.

## Summary

- CCW-024.AC-1: met (read with Read before Write or Edit).
- CCW-024.AC-2: met (one test-task run with `--continue`, filtered).
- CCW-024.AC-3: met (tests read only for the design, named in the Summary).
- CCW-024.AC-4: met (each Java archetype names its task).
- CCW-024.AC-5: met (DFS-054: no refusal, one test-task run, no test reads).

## AC-5 run

Ticket DFS-054 (archetype `endpoint`), run by dfs-backend on 2026-11-07, 10:24 to 10:35 local, after the session was resumed to load the new `architect.md`; stubs committed in `b6c1ec2`. Transcript: `~/.claude/projects/C--Users-apoch-Projects-us-pochaev-dfs-duplicate-file-scanner/03efb8dd-5251-49bb-991a-3bfb372605b6/subagents/agent-ad0833ab0f4bff783.jsonl` (meta: `"agentType":"architect"`, "Architect design for DFS-054", model opus, effort high). The first Java design phase after the merge: DFS-052's architect started 11 minutes later in the same session.

- **No `File has not been read yet`:** 0 such tool results (20 Writes, 1 Edit, all accepted).
- **One run of the named test task:** the only test run is `./gradlew :server:test --continue 2>&1 | grep -E " FAILED$|tests completed|BUILD|npmTest|ngBuild|error"`, the endpoint archetype's task with its filter. The other Gradle calls are `:server:compileJava` (step 6) and `:server:updateOpenApi` (the archetype's spec step).
- **No grep or Read into a test tree:** every Read, Grep and Glob path is under `src/main`, `.claude/archetypes/endpoint/`, the story folder or `server/frontend/openapi.json`; none under `src/test` or a `*.spec.ts`. design.md's Summary: "Expected breakage: None. Existing tests read: No tests read."

| Run | Tool calls | Model requests | Output tokens |
|---|---|---|---|
| DFS-049 (before) | 79 | 31 | 70.9k |
| DFS-055 (before) | 63 | 30 | 72.4k |
| DFS-054 (after) | 66 | 29 | 40.3k |

Context, not a pass condition (decision 4): DFS-054 is a different ticket, so the figures compare runs, not the fix alone. Counted with a script over the transcript: tool_use blocks, distinct assistant message ids, and the sum of `output_tokens` per id.

## Decision 5 measurement

DFS-057 (archetype `component`, dfs-frontend, 2026-11-07, `bf0f7892-5e76-4556-af00-1220b2d226a8/subagents/agent-aec5754afbc36d067.jsonl`): 37 tool calls, 20 requests, 33.3k output, 0 refused writes. Requests whose only Write or Edit calls are stub files (outside `docs/tickets/`) produced 8.4k of the 33.3k, 25%. The same count for DFS-049 gives 32.3k of 70.9k, 46%, with 19 refused writes. With the double write gone the remaining stub share is about 8k per frontend ticket, below decision 5's ~13k estimate, so no ticket for thinner `component` stubs is filed.
