# CCW-021 verification

## Product owner review

*Completed 2026-11-07: the last three live runs (AC-6, AC-13, AC-14) are taken from normal use after the merge.*

- **What you can do now:** run the coordinator (dfs-product) without other sessions reporting to it. At every start or `/clear`, and in its `/tickets list`, it sees every worktree with its pipeline state (gates as `awaiting-approval at the gate after <phase>`), uncommitted ticket files and pending advice. It writes and refines tickets in every lane and never starts or closes them.
- **Built:** `story-gate catch-up` (`tools/story-gate/CatchUp.java`), run by a SessionStart hook in `.claude/settings.json` and by the coordinator's `/tickets list`; the coordinator role in `.claude/skills/tickets/SKILL.md`, `docs/tickets/lanes.md` (`dfs-product: coordinator`) and `CLAUDE.md`.
- **Merged:** `c818603` (implementation, `c89a9d9`) and `f70f45b` (label fix, `27caec0`); message 3 sent after each.
- **Session name route:** the hook input's `session_title` (Claude Code 2.1.316 documents it for SessionStart, present when `-n`/`/rename` set a title), falling back to `DFS_SESSION`.
- **Deviations:** see "Decisions taken during implementation".
- **Criteria:** every scenario passes (table below): 11 run, 4 read and 3 taken from live use.
- **Known limits:** CCW-025 (draft) proposes dropping the ready hand-off verified here under AC-6, and CCW-026 (draft) would let the coordinator start ticket sessions, reversing part of AC-5.

## Decisions taken during implementation

1. **Session name from `session_title`.** The SessionStart hook input carries `session_title` when a custom title is set (hooks reference, code.claude.com/docs/en/hooks, read 2026-11-02); `DFS_SESSION` stays as the fallback (decision 10). So no relaunch is needed while the session keeps its name.
2. **Catch-up self-tests in code, not case folders.** `CatchUpSelfTest.java`, run by `selftest` like `JsonSelfTest`, counted as one case. A `CCW-021.AC-n-...` case folder would make `SelfTest`'s coverage rule demand a case for every CCW-021 scenario, most of them live runs across sessions. The count change moved three nested-selftest expectations by one (`AC-12-passes` 2 to 3, `CCW-009.AC-7-resolves-by-prefix` and `CCW-009.AC-8-clean-run` 3 to 4).
3. **No "message 4".** The coordinator's message after a `ready` commit is named "the ready hand-off", not numbered, since AC-8 requires that no file mentions a message 4 (the retired report message).
4. **Labels on uncommitted files** (`27caec0`): `main checkout: <path>` or `<ID> worktree: <path>`. In the first real run, dfs-product read `CCW-021: <path>` as "uncommitted on main" and suggested cleaning up an in-progress ticket's worktree.
5. **Advice is read from `main`'s tree**, the canonical exchange per `advisory/README.md`; the hook works from any tree.

## Scenarios

| Scenario | AC | Mode | Result |
|---|---|---|---|
| The lane map accepts one coordinator | 1 | run | pass (below) |
| The lane map refuses a misused coordinator | 1 | run | pass (below) |
| A cleared coordinator gets the full picture | 9 | run | pass (below) |
| Pending advice is listed at session start | 11 | run | pass (below) |
| A superseded revision is flagged | 11 | run | pass (below) |
| Temporary files are ignored | 11 | run | pass (below) |
| Only the coordinator gets the catch-up | 12 | run | pass (below) |
| No name means no output | 12 | run | pass (below) |
| The coordinator's list shows mid-session state | 13 | run | pass (below, live use) |
| A lane session's list is unchanged | 13 | read | pass (below) |
| A pipeline stop shows in the catch-up | 14 | run | pass (below, live use) |
| A blocked phase shows in the catch-up | 14 | read | pass (below) |
| The coordinator writes tickets but never starts, launches or closes | 5 | read | pass (below) |
| An autorun ticket made ready by the coordinator is handed over as an unblock | 6 | run | pass (below, live use) |
| A ticket made ready with an open dependency | 6 | read | pass (below) |
| The coordinator's list covers every lane | 7 | run | pass (below) |
| No old mention of the message rule remains | 8 | run | pass (below) |
| story-gate's self-tests cover the catch-up | 15 | run | pass (below) |

### AC-1: lane map (run, 2026-11-02, main at `f70f45b`)

`validate`'s "Lane map" rule as merged, applied literally by a script (scratchpad `lanemap.py`, never committed) to the real `lanes.md` and two scratch copies:

```
lanes.md: nothing to report
lanes-two.md: line "dfs-other: coordinator" is a second coordinator (first: "dfs-product: coordinator")
lanes-mixed.md: line "dfs-backend: backend, coordinator" mixes coordinator with lanes; line "dfs-product: coordinator" is a second coordinator (first: "dfs-backend: backend, coordinator")
```

### AC-9, AC-11, AC-12: real `/clear` in dfs-product (run, 2026-11-02, main at `e1f9a7a`)

State set up beforehand: an uncommitted edit to CCW-021's ticket file in its worktree; scratch files in `main`'s checkout, never committed and deleted after the run: `advisory/proposals/ADV-903-r1.md`, `ADV-903-r2.md` (`Supersedes: ADV-903-r1`), `ADV-904-r1.md.tmp`, `ADV-905-r1.md`, and `advisory/responses/ADV-906-r1.json` (truncated JSON). Scratch IDs 9xx keep clear of real advice. The real ADV-001 and ADV-002 have `evaluated` responses. The `received` case is covered by the self-test.

`DFS_SESSION` was not set in dfs-product (`! echo $DFS_SESSION` there printed nothing, 2026-11-02), so the hook found the coordinator by the hook input's `session_title` alone: the route of decision 1, confirmed on Claude Code 2.1.316.

The hook's output as run by hand just before (the `/clear` session reported the same content, paraphrased by dfs-product):

```
Catch-up for dfs-product (coordinator), main at e1f9a7a (CCW-021; run /tickets list for the backlog):
worktrees:
- CCW-021 tickets/CCW-021-coordinator-session ../duplicate-file-scanner-CCW-021-coordinator-session: no plan.json
- DFS-051 tickets/DFS-051-execute-plan-into-quarantine ../duplicate-file-scanner-DFS-051-execute-plan-into-quarantine: plan done
uncommitted ticket and plan.json files:
- CCW-021: docs/tickets/backlog/CCW-021-coordinator-session/CCW-021-ticket.md (M)
pending advice (advisory/README.md):
- invalid response advisory/responses/ADV-906-r1.json: unexpected end of input at line 1, column 12
- ADV-903 r1 superseded by r2 advisory/proposals/ADV-903-r1.md (record a superseded response)
- ADV-903 r2 "Scratch new revision" advisory/proposals/ADV-903-r2.md
- ADV-905 r1 "Scratch pending" advisory/proposals/ADV-905-r1.md
```

No ticket content, no `.tmp` file, ADV-001 and ADV-002 not listed. Since `27caec0` the uncommitted line reads `- CCW-021 worktree: ...`.

### AC-12 and AC-7: real `/clear` in dfs-backend and dfs-product (run, 2026-11-02, main at `75d861d`)

- **dfs-backend**, after `/clear`, asked whether a SessionStart hook added anything to its context: "no hook output".
- **dfs-product**, after `/clear`: the hook printed, word for word:

```
Catch-up for dfs-product (coordinator), main at 75d861d (CCW-021; run /tickets list for the backlog):
worktrees:
- CCW-021 tickets/CCW-021-coordinator-session ../duplicate-file-scanner-CCW-021-coordinator-session: no plan.json
- DFS-051 tickets/DFS-051-execute-plan-into-quarantine ../duplicate-file-scanner-DFS-051-execute-plan-into-quarantine: plan done
uncommitted ticket and plan.json files:
- CCW-021 worktree: docs/tickets/backlog/CCW-021-coordinator-session/CCW-021-ticket.md (M)
```

- **AC-7:** its `/tickets list` then showed "This session is dfs-product, which lanes.md maps to coordinator", one table per lane (backend: DFS-006, DFS-007, DFS-021, DFS-051, DFS-052; frontend: DFS-039, DFS-045, DFS-049, DFS-050; harness: CCW-021, CCW-022, CCW-023), each headed with its owner, and "Dispatchable now" and "Started elsewhere" for each lane (backend: none / DFS-051 with its worktree; frontend: none / none; harness: none, CCW-022 waits on CCW-021 / CCW-021 with its worktree). Under the table it ran `story-gate catch-up --session dfs-product`, which printed the same picture (AC-13's coordinator half; its mid-session half is still pending). It changed nothing, and its proposed next steps left `done` for DFS-051 and CCW-021 to the lane owners and offered only a refinement (DFS-049), as the role intends.

### AC-12: no name, and a lane session (run)

With `{"source":"clear"}` on stdin and `DFS_SESSION` unset: no output, exit 0. With `session_title` `dfs-backend` against `main`: 0 bytes, exit 0.

### Run time and size

At a real start against `main` (`c818603`): 3.65 s wall clock (launcher's `java -version` plus a source-launch compile of story-gate), 6 lines. Scratch clone: 3.2 s, 3 lines.

### AC-8 and the CCW-018.L-1 search (run, 2026-11-02, after the rename)

`grep -rniE "three moments|exactly three|no others|only when a request is filed|message 4|messages? .{0,40}three|three .{0,40}messages?|four moments"` over `.claude`, `CLAUDE.md`, `docs/design`:

- `.claude/skills/tickets/SKILL.md:120`: "sent only at these three moments and the coordinator's ready hand-off below, and no others": includes the coordinator's message.
- `CLAUDE.md:128`: "...a harness change lands (CCW-018), or the coordinator makes a ticket in another lane ready": includes it.

No "message 4" anywhere. Every other "three" in the tickets skill (lines 22, 97, 158, 209) is about ID digits, launch points, refine's three bullets and the three lanes. Lane-ownership wording elsewhere: `.claude/skills/story/SKILL.md:12` ("a ticket in this session's own lanes") still holds, since the coordinator owns none. The first search, before the rename, found "message 4" in the new text (lines 139, 157), which led to decision 3.

### AC-15: self-test (run)

`tools/story-gate/story-gate selftest`: `PASS selftest: 126 cases match their expected verdicts; ...` on `c89a9d9` and again on `27caec0`. The catch-up cases: session_title, DFS_SESSION fallback, lane-map roles, no name (no output, exit 0), worktree list parsing, awaiting-approval with its gate, blocked with its phase, running and invalid plan, advice (received pending, evaluated hidden, superseded, `.tmp` and other names ignored, invalid response), a Supersedes line not naming the old revision, and a real temporary repository with a ticket worktree (worktree line, uncommitted ticket and `plan.json`, no ticket content, a lane session silent).

### AC-6, AC-13, AC-14: live use after the merge (checked 2026-11-07 from session transcripts)

The three runs left pending on 2026-11-02 happened in normal use; checked by dfs-harness from the transcripts under `~/.claude/projects/C--Users-apoch-Projects-us-pochaev-dfs-duplicate-file-scanner/`.

- **AC-6, the ready hand-off as an unblock.** dfs-product made DFS-050 (frontend, `autorun`, dependencies done) ready in `6ad83e0` (2026-11-05 13:36 local) and sent dfs-frontend `DFS-050 ready in 6ad83e0: unblocks DFS-050 (frontend, autorun); run the launch check and start the lowest` (13:36:18). dfs-frontend launched it: its architect committed `18ccda9` (`feat(server): design DFS-050 overlaps tab with stubs`) at 13:48. The same form went out for DFS-055 (`9b26afb`), DFS-052 (`a9ef6a7`) and DFS-057 (`1dd4934`).
- **AC-13 and AC-14, the coordinator's mid-session list shows pipeline stops.** In dfs-product (transcript `37875e01-6741-452c-bee1-91a3ca91b00b.jsonl`, 2026-11-05 16:42 local), `/tickets list` ran `story-gate catch-up --session dfs-product`, which printed:

```
Catch-up for dfs-product (coordinator), main at 9b26afb (CCW-021; run /tickets list for the backlog):
worktrees:
- CCW-021 tickets/CCW-021-coordinator-session ../duplicate-file-scanner-CCW-021-coordinator-session: no plan.json
- DFS-050 tickets/DFS-050-overlaps-tab ../duplicate-file-scanner-DFS-050-overlaps-tab: plan awaiting-approval at the gate after design
- DFS-055 tickets/DFS-055-serialize-plan-executions ../duplicate-file-scanner-DFS-055-serialize-plan-executions: plan awaiting-approval at the gate after done
uncommitted ticket and plan.json files:
- main checkout: docs/tickets/backlog/CCW-021-coordinator-session/CCW-021-ticket.md (M)
- CCW-021 worktree: docs/tickets/backlog/CCW-021-coordinator-session/CCW-021-ticket.md (M)
```

Two real gates (DFS-050 after design, DFS-055 after done), each with its gate. The `main checkout` line was a stray 2-line edit of the ticket's `touches`, made outside any Claude session and restored on 2026-11-07.

### `[read]` scenarios (read 2026-11-02, `.claude/skills/tickets/SKILL.md` and `tools/story-gate` at `f70f45b`)

Checked by reading only, not run:

- **AC-5, the coordinator writes tickets but never starts, launches or closes.** "The coordinator" (lines 141 to 145): "Writes tickets in every lane" (`new` and `refine` with the ready commit), "only after telling that session in the same turn" (another session's draft), "Commits its own edits at once", "Never starts, launches or closes". `start` (line 165) refuses `start`, `done` and `cancel` in the coordinator, `done` repeats it (line 177), and "Autonomous launch" (line 98) says the coordinator never launches. The `story` skill is started only by the user or by the tickets skill's autonomous launch, which the coordinator never does; the `story` skill does not mention the coordinator (0 hits), as decision 12 intends.
- **AC-6, a ticket made ready with an open dependency.** Line 141: "a ticket with a dependency still open needs only message 1's form, since the owner's `done` on the dependency launches it as before". The ready hand-off in "Messages" gives the same two forms, and refine's coordinator bullet (line 159) sends it in place of the launch bullets.
- **AC-13, a lane session's list is unchanged.** Line 163: "In any other session `list` does not run the script"; the paragraph before it, for every session, is unchanged except for the CCW-021 sentence.
- **AC-14, a blocked phase shows in the catch-up.** `CatchUp.planState` (line 183): `"blocked" -> "plan blocked in phase " + <first phase not done>`; self-test "AC-14 blocked names the blocked phase" expects `plan blocked in phase tests`.

### Touches

`git diff --name-only 5a7cb79 27caec0`: `.claude/settings.json`, `.claude/skills/tickets/SKILL.md`, `CLAUDE.md`, the ticket file, `docs/tickets/lanes.md`, `tools/story-gate/*` (CatchUp, CatchUpSelfTest, SelfTest, StoryGate, three `expected.txt`). All inside `touches`.
