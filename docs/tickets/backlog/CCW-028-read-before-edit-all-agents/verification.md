# CCW-028 verification

## Product owner review

### What you can do now

The implementer, test-author and verifier read a file with Read before changing it, instead of printing it with `cat` or `grep` and having the write refused. Before this, 54 writes were refused across their runs, 53 of them on a file the agent had never opened with Read; each refusal cost another round trip carrying the whole context.

### Acceptance criteria

- **CCW-028.AC-1:** met. `implementer.md` requires a Read of every existing file it will change (the architect's stubs, the sources in `touches`, `progress.md` and `learnings.md`) before Write or Edit, even after a shell print; after a refusal it reads and retries once.
- **CCW-028.AC-2:** met. `test-author.md` gives the same rule, naming existing test classes (`ScanCommandTest.java`), Vitest specs (`scan-row.spec.ts`), `test-plan.md` and `progress.md`.
- **CCW-028.AC-3:** met. `verifier.md` gives the same rule, naming `verification.md` and the ticket file.
- **CCW-028.AC-4:** met. DFS-059 (dfs-frontend, 2026-11-07), the first pipeline run after the merge: 0 refused writes in its test-author, implementer and verifier transcripts, each of which ran on the new paragraph. Details under "AC-4 run".

### Try it

Nothing runs by itself: these are instruction edits. Run `git diff main...HEAD -- .claude/agents` and look for one added paragraph in each of the three files. The real test is AC-4: after the merge, the next `/story run`'s implementer, test-author and verifier transcripts should show no `File has not been read yet`.

### Decided without you

- The test-author paragraph also names `test-plan.md` and `progress.md`, and each paragraph cites the 54-refusal figure.
- The verifier has no "Read nothing else" line, so its paragraph follows the "What to read" list.

### Known limits

- The 54 refusals come from every earlier transcript; the after-count rests on one run (DFS-059) with few edits (test-author 1 Edit, verifier 3 Edits).
- DFS-059's implementer made no Write or Edit call at all: it changed `ResultIndex.java`, `CLAUDE.md` and `docs/design/ui.md` through Python scripts run with Bash, and both it and the test-author appended to `progress.md` with `printf`. That route cannot be refused, so it says nothing for or against the rule. Whether agents should edit through Bash is a separate question (the verifier is told never to; the implementer and test-author are not).

### Evidence

- `tools/story-gate/story-gate selftest`: PASS, 126 cases; the phrases pinned by `CCW-020.AC-4-agent-prompts` are intact.
- Only the three agent files and this ticket's folder changed. No count, name or list changed, so no search for other mentions was needed.

### How it went

Implemented by hand (archetype `none`); the implementer paragraph was amended once before commit to add `progress.md` and `learnings.md`. No pipeline phases, retries or escalations.

AC-4 checked on 2026-11-07 from DFS-059's transcripts; every criterion is met.

## Summary

- CCW-028.AC-1: met (implementer reads stubs, `touches` sources, progress and learnings first).
- CCW-028.AC-2: met (test-author reads existing test classes and specs first).
- CCW-028.AC-3: met (verifier reads `verification.md` and the ticket first).
- CCW-028.AC-4: met (DFS-059: 0 refused writes in the three phases, all on the new paragraph).

## AC-4 run

DFS-059 (archetype `endpoint`), run by dfs-frontend (session `52309bff-bc3c-4ce5-ac5b-7de2bf874e6f`) right after the merge `38f44c2` (17:51:52 local), closed in `903db8e`. Transcripts under `~/.claude/projects/C--Users-apoch-Projects-us-pochaev-dfs-duplicate-file-scanner/52309bff-bc3c-4ce5-ac5b-7de2bf874e6f/subagents/`.

| Phase | Transcript | Started (local) | Write/Edit calls | Refused |
|---|---|---|---|---|
| test-author | `agent-a22509297cc3a4ff2` | 17:58 | 2 (Edit `ResultApiTest.java` after a Read of it; Write `test-plan.md`) | 0 |
| implementer | `agent-ab0c994bbb4ce0267` | 18:02 | 0 (edits through Python under Bash) | 0 |
| verifier | `agent-afa41e432d66c80a1` | 18:07 | 5 (Write `verification.md` twice; Edit it, the ticket and `learnings.md`, each after a Read or its own Write) | 0 |

Before: 54 refused writes across these three agents (53 on a file never opened with Read).

**The run used the new text.** dfs-frontend's session started at 17:46, five minutes before the merge, and the DFS-059 worktree branched from `b8f9aef`, before it, so its own `.claude/agents/` files are the old ones. Yet each of the three transcripts' `prompt_snapshot` line holds the new paragraph ("54 refused writes"). So agent definitions are read when the subagent is spawned, from the session's own checkout (here the main checkout), not when the session starts and not from the ticket's worktree: a merged agent change reaches running sessions without a resume or `/clear`.
