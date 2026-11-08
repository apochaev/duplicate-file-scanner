# CCW-028 verification

## Product owner review

### What you can do now

The implementer, test-author and verifier read a file with Read before changing it, instead of printing it with `cat` or `grep` and having the write refused. Before this, 54 writes were refused across their runs, 53 of them on a file the agent had never opened with Read; each refusal cost another round trip carrying the whole context.

### Acceptance criteria

- **CCW-028.AC-1:** met. `implementer.md` requires a Read of every existing file it will change (the architect's stubs, the sources in `touches`, `progress.md` and `learnings.md`) before Write or Edit, even after a shell print; after a refusal it reads and retries once.
- **CCW-028.AC-2:** met. `test-author.md` gives the same rule, naming existing test classes (`ScanCommandTest.java`), Vitest specs (`scan-row.spec.ts`), `test-plan.md` and `progress.md`.
- **CCW-028.AC-3:** met. `verifier.md` gives the same rule, naming `verification.md` and the ticket file.
- **CCW-028.AC-4:** not yet measured. It needs the first pipeline run after the merge.

### Try it

Nothing runs by itself: these are instruction edits. Run `git diff main...HEAD -- .claude/agents` and look for one added paragraph in each of the three files. The real test is AC-4: after the merge, the next `/story run`'s implementer, test-author and verifier transcripts should show no `File has not been read yet`.

### Decided without you

- The test-author paragraph also names `test-plan.md` and `progress.md`, and each paragraph cites the 54-refusal figure.
- The verifier has no "Read nothing else" line, so its paragraph follows the "What to read" list.

### Known limits

- AC-4 and its done-item (and so the "every scenario is checked" item) stay open until a run is measured; the ticket is not closed before then.
- The 54 refusals are from transcripts since 2026-10-25; the after-count rests on one run.

### Evidence

- `tools/story-gate/story-gate selftest`: PASS, 126 cases; the phrases pinned by `CCW-020.AC-4-agent-prompts` are intact.
- Only the three agent files and this ticket's folder changed. No count, name or list changed, so no search for other mentions was needed.

### How it went

Implemented by hand (archetype `none`); the implementer paragraph was amended once before commit to add `progress.md` and `learnings.md`. No pipeline phases, retries or escalations.

Ready for the user's approval of AC-1 to AC-3; AC-4 waits for the next pipeline run.

## Summary

- CCW-028.AC-1: met (implementer reads stubs, `touches` sources, progress and learnings first).
- CCW-028.AC-2: met (test-author reads existing test classes and specs first).
- CCW-028.AC-3: met (verifier reads `verification.md` and the ticket first).
- CCW-028.AC-4: not yet measured (needs the next pipeline run).
