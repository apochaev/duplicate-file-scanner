# CCW-024 verification

## Product owner review

### What you can do now

The architect no longer repeats work it was refused: it reads a file with Read before changing it, instead of printing it with `cat` and having its writes refused (DFS-049 lost 19 writes this way). It finds Expected breakage by running the existing tests once, with `--continue`, instead of searching for and reading test files (about 45k of context in DFS-055). Each Java archetype names the exact test task to run.

### Acceptance criteria

- **CCW-024.AC-1:** met. `architect.md` "What to read" now requires a Read of every existing file before Write or Edit, including files an earlier attempt left in the worktree, and forbids deleting them to get around the refusal.
- **CCW-024.AC-2:** met. Step 4 runs the archetype's named task once after the stubs compile, with `--continue`, filters the output to failing classes and checks each report for `not implemented: <TICKET-ID>`.
- **CCW-024.AC-3:** met. The "Read nothing else" exception no longer covers breakage; a test may be read only when the design depends on how it is built, named in the `## Summary` with a reason ("No tests read." otherwise).
- **CCW-024.AC-4:** met. `use-case` and `pipeline-stage` name `./gradlew test --continue`, `wiring` `./gradlew :scanner-spring:test :cli:test :server:test --continue`, `endpoint` `./gradlew :server:test --continue`.
- **CCW-024.AC-5:** not yet checked. It needs the first Java-archetype design phase run after this merges (decision 4), likely DFS-054 in the backend lane.

### Try it

Nothing runs by itself: these are instruction edits. Read `.claude/agents/architect.md` ("What to read", steps 4 and 6, "The `## Summary` section") and the new "Expected breakage run" bullet at the end of "Where files go" in each Java `archetype.md`. The real test is AC-5: after the merge, the next Java design phase's transcript should show no `File has not been read yet`, one test-task run, and no searching of the test trees.

### Decided without you

- The filter example in step 4 is `2>&1 | grep -E " FAILED$|tests completed"`, and the report path given is `<project>/build/test-results/test/TEST-<package>.<Class>.xml`.
- Step 6's clause "since tests don't exist yet" now reads "since the story tests don't exist yet (the existing tests run once, in step 4)", so it no longer contradicts step 4.
- The new archetype rule sits as the last bullet of each "Where files go" section, not in the edge-case checklist, since it is an instruction for the architect, not a question for refinement.

### Known limits

- AC-5 and the decision 5 measurement (the stub-writing share in the next `component` design phase) are open until those runs happen.
- The other pipeline agents (test-author, implementer, verifier) may hit the same read-before-edit refusal; not measured, out of scope.
- `story-gate selftest` still fails the two cases that already failed on `main` (`CCW-020.AC-1-archetype-files`, `CCW-020.AC-12-docs`, stale archetype versions); out of scope.

### Evidence

- Commit `db54a27` on `tickets/CCW-024-cut-architect-token-cost`: `architect.md` and the four Java `archetype.md` files, versions bumped use-case 9→10, pipeline-stage 16→17, wiring 3→4, endpoint 3→4.
- `tools/story-gate/story-gate selftest --root <worktree>` on 2026-11-06: `FAIL selftest: 2 problems`, the same two as on `main` before the change; `CCW-015.AC-7-instructions`, which pins four phrases of step 4 and the Summary, passes.
- Search for other mentions (CCW-018.L-1), `grep -rn "Expected breakage\|grep -rl\|search the test\|their callers" .claude CLAUDE.md docs/design`, excluding the `design.md.tmpl` files: `architect.md` (changed), the four Java `archetype.md` files (changed), `component/archetype.md` checklist item 3 (already says run `:server:npmTest`; consistent), `pipeline-stage/test-guide.md:37` (test-author guidance on output literals; unrelated), the tickets skill's refine rule (about refinement; unrelated). The five `design.md.tmpl` files describe the section's format, not how it is found; unchanged.

### How it went

Implemented by hand (archetype `none`) in one sitting after refinement; one wording fix (step 6) found while reviewing the diff before commit. No pipeline phases, retries or escalations.

Ready for review of AC-1 to AC-4; the ticket stays in review until the AC-5 run.

## Summary

- CCW-024.AC-1: met (read with Read before Write or Edit).
- CCW-024.AC-2: met (one test-task run with `--continue`, filtered).
- CCW-024.AC-3: met (tests read only for the design, named in the Summary).
- CCW-024.AC-4: met (each Java archetype names its task).
- CCW-024.AC-5: open, waiting for the first Java design phase after the merge.

## AC-5 run

To be filled after the run: ticket, transcript path, the three signals, and tool calls, model requests and output tokens beside DFS-049 (79 calls, 31 requests, 70.9k) and DFS-055 (63 calls, 30 requests, 72.4k).

## Decision 5 measurement

To be filled when AC-5 is verified: the latest `component` design phase after the merge, if any, and its stub-writing share of output, or "none ran".
