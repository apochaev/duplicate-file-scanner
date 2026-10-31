---
name: story
description: Advance a ready ticket through the story pipeline (architect, test-author, implementer, verifier, retro), launching each phase's agent, stopping at human gates, and resuming from plan.json. Run by the user, or started by Claude only through the tickets skill's autonomous launch for an autorun ticket.
argument-hint: run|status|approve|learn <ID> [feedback]
disable-model-invocation: false
---

# Story

The orchestrator for the story pipeline (`docs/design/story-orchestration.md`, D1–D12). It runs in the main session, launches the phase agents (`.claude/agents/architect.md`, `test-author.md`, `implementer.md`, `verifier.md`, `retro.md`) as subagents, and stops at every human gate and at `blocked`. Every invocation either advances to the next stop and shows it, or refuses and says why — never a bare status (D12).

Usage: `/story <mode> <ID> [args]`. Modes: `run`, `status`, `approve`, `learn`. This skill is `disable-model-invocation: false` so that the `tickets` skill can start it, but it is not Claude's to launch on its own judgement — a run spends real tokens, makes real commits, and can flip a ticket's status. Claude may start `run <ID>` only through the `tickets` skill's "Autonomous launch" (CCW-017): right after `refine`'s ready commit, or after `done` closes the ticket's last dependency, for a ticket with `autorun: true` that passed the launch check. Every other `run`, and every `approve`, `status` and `learn`, happens only when the user types it. `approve` is never invoked by Claude: the gates are the user's.

## Story folder and `plan.json`

The story folder is the ticket's own folder, `docs/tickets/{backlog,completed}/<TICKET-ID>-<slug>/`. `plan.json` lives there, and this skill is its only writer (D5) — every phase agent writes its own artifacts and appends to `progress.md`/`learnings.md`, and reports its outcome in its final message, but never touches `plan.json` itself, so no agent can grade its own work.

Shape (template: `plan.json.tmpl` in this skill's folder):

```json
{
   "ticket": "DFS-001",
   "archetype": {"name": "pipeline-stage", "version": 2},
   "branch": "tickets/DFS-001-group-scanned-files-by-size",
   "worktree": "C:/Users/me/Projects/duplicate-file-scanner-DFS-001-group-scanned-files-by-size",
   "state": "running",
   "phases": [
      {"id": "design", "agent": "architect", "after": [], "status": "pending",
         "runs": [], "artifacts": [], "commit": null}
   ],
   "gates": [{"after": "design", "status": "pending"}, {"after": "done", "status": "pending"}],
   "defects": []
}
```

`state` is one of `running`, `awaiting-approval`, `blocked`, `done`. A phase `status` is `pending`, `in-progress` or `done`. A `runs` entry is `{"attempt": 1, "model": "opus", "effort": "high", "turns": 21, "wallClockSeconds": 940, "outcome": "passed | failed-gate | maxTurns"}` — turns and wall-clock time only, no token count (its source is unconfirmed; CCW-006's pilot is where that gets settled, not this skill).

## Worktree (CCW-012)

Every story is worked in its own git worktree, never in the repo checkout: branch `tickets/<TICKET-ID>-<slug>` (the slug is the ticket folder's), worktree `../<repo-dir>-<TICKET-ID>-<slug>` beside the repo checkout, the same names the `tickets` skill uses (CCW-008). The repo checkout stays on `main` throughout, so several stories can be in flight and `/tickets done` merges into it directly.

- **Finding it.** Every mode finds the story's worktree with `git worktree list --porcelain`, by the line `branch refs/heads/tickets/<TICKET-ID>-<slug>`; the `worktree` line above it is the path. Never guess the path. `plan.json` lives inside the worktree, so it cannot be how the worktree is found; its `worktree` field (written by preflight) is a cross-check instead: if it disagrees with what `git worktree list` reports (the worktree was moved or recreated), refuse, naming both paths, and change nothing.
- **Addressing it, never `cd`.** The orchestrating session stays in the repo checkout. Every git command against the story runs as `git -C <worktree> ...`; every check runs as `java tools/story-gate/StoryGate.java <check> <TICKET-ID> --root <worktree>`; `plan.json` and every other story file are read and written by absolute path under `<worktree>`. Never `cd` into the worktree: whether a `cd` persists between shell calls is the harness's behaviour, not this skill's (CCW-001.L-5).
- **The story folder** is `<worktree>/docs/tickets/{backlog,completed}/<TICKET-ID>-<slug>/`, and the ticket file is inside it. Read the ticket from the worktree once one exists, since its copy on `main` is not updated until `/tickets done` merges.
- **The working-tree block.** Every agent launch (resume step 6, and the retro in `learn`) carries this block right after the story-folder and ticket lines, with the three values filled in:

  ```
  Working tree: this story runs in a git worktree. The repository root for this story is
  <worktree>
  (branch tickets/<TICKET-ID>-<slug>). Read and write every file under that root, and run every git and Gradle command from that root (for example `cd "<worktree>" && ./gradlew build`). Never read or edit files under <repo checkout> (the repo checkout, on main); it is a different tree. CLAUDE.md, the archetype and tools/story-gate are the copies under the worktree root.
  ```

  The agent definitions are not changed for this: agents already take their paths from the launch prompt and never derive them (resume step 6). The `cd` inside an agent's own command is the agent's business; the rule against `cd` binds this orchestrating session.

## Phase ids, agents and gates (fixed convention, not per-archetype)

An archetype's `archetype.md` names phases only by id (`phases: [design, test, implement, verify]`) and configures `gates:`/`models:`; it does not name which agent runs each phase or which deterministic check follows it. Those are fixed across every archetype, because they are the same five-agent pipeline shape (D1) every time:

| Phase id in `plan.json` | Archetype's own id (`phases:`) | Agent | Deterministic check(s) run on return | Purpose |
|---|---|---|---|---|
| `design` | `design` | `architect` | `touches` | confirms the architect stayed in scope; the design itself is judged only at the human gate |
| `tests` | `test` | `test-author` | `test-phase` (red, then traceability off the same reports — CCW-003's composite) | the red gate: every story test fails only with the stub's exception |
| `implement` | `implement` | `implementer` | `green`, `disabled`, `freeze`, `touches`, `stubs`, `traceability`, `consistency` | the full deterministic battery (D7) — build and tests green for real, no leftover stubs, tests unchanged since the test phase, every criterion still traced |
| `verify` | `verify` | `verifier` | `consistency` | confirms the ticket status and `plan.json` still agree after the verifier ticks done-items |

**`tests` vs `test` (found running this ticket's own demonstration, not designed in advance):** `pipeline-stage`'s `archetype.md` spells the test-author phase's id `test` (singular) in its `phases:` list, but CCW-003's `FreezeCheck`/`Story.testsCommit` hardcodes the literal id `"tests"` (plural) when reading a phase's commit out of `plan.json` — a mismatch between two already-built, already-closed tickets that nothing exercised until this orchestrator actually wired them together. Since `archetype.md` and `tools/story-gate` are both outside this ticket's `touches`, the fix here is narrow: `plan.json` always uses `tests`, regardless of what the archetype's own `phases:` list calls it. Flag this for `/story learn` — the durable fix is probably renaming `archetype.md`'s `phases:` entry to `tests` (removing the need for this translation table), but that is a call for the log, not a unilateral edit here.

`retro` is not a `phases:` entry — it never blocks or advances `run`. It exists only behind `/story learn` (see below).

Building `plan.json`'s `phases` list from an archetype: each phase's `after` is the id of the phase immediately before it in `phases:` (empty for the first), using the `plan.json` ids from the left column above, not the archetype's own spelling. An archetype that wants a different shape (parallel implementer/test-author, D2's later layer) would need an extended `archetype.md` schema — not built here.

A gate in `archetype.md`'s `gates:` list names either an actual phase id (pause right after that phase is marked `done`) or the reserved name `done`, meaning after the *last* id in `phases:` (D3's "before done" gate) — not a phase of its own.

## Preflight (`run`, when no `plan.json` exists yet)

1. Look the branch up: `git worktree list --porcelain` for `branch refs/heads/tickets/<TICKET-ID>-<slug>`, and `git branch --list tickets/<TICKET-ID>-<slug>`. Read the ticket from the worktree if one holds the branch, otherwise from the repo checkout. If that worktree's story folder already has a `plan.json`, this is a resume: skip preflight and go to the resume protocol.
2. Refuse, naming the reason, and do nothing else, unless:
   - `status` is `ready` (a fresh start), or `in-progress` while a worktree holds the branch and has no `plan.json` (the user ran `/tickets start <ID> --worktree` first; also a fresh start, not a refusal);
   - `archetype` is not `none` — refuse with "implemented by hand, not through the pipeline" for a `none` ticket;
   - every `depends_on` entry has `status: done` — refuse naming the first one that isn't.
3. Set up the worktree, always off `main`, never off the current branch tip (a story must not inherit another ticket's unmerged commits). By what step 1 found:
   - **(a) no branch:** `git worktree add -b tickets/<TICKET-ID>-<slug> ../<repo-dir>-<TICKET-ID>-<slug> main`.
   - **(b) a worktree holds the branch, ticket `ready`:** reuse it.
   - **(c) a worktree holds the branch, ticket `in-progress`, no `plan.json`:** reuse it.
   - **(d) the branch exists, no worktree:** `git worktree add ../<repo-dir>-<TICKET-ID>-<slug> tickets/<TICKET-ID>-<slug>` (no `-b`).
   - **(e) the branch is checked out in place in a checkout that is not a story worktree** (the old in-place `/story run`; `git worktree list` shows it on the repo checkout or another tree not named `../<repo-dir>-<TICKET-ID>-<slug>`): refuse, naming that checkout's path, and tell the user to switch it back to `main` (`git -C <that checkout> checkout main`) and rerun. Never move someone's checkout automatically.

   If `git worktree add` refuses, stop and report git's message.
4. Create `plan.json` in the worktree's story folder from the resolved archetype's `phases:` and `models:` and the gates (per the table above) — the gates are the ticket's own `gates:` when it has one, otherwise the archetype's `gates:` (CCW-017; the `tickets` skill's `refine` decides them per ticket), with `worktree` set to the worktree's path as `git worktree list --porcelain` prints it, state `running`, every phase `pending`, no defects.
5. Run the `tickets` skill's `start <ID>`. A worktree now holds the branch, so `start` makes its `in-progress` edit inside the worktree, never in the repo checkout (its first branch, CCW-008). In state (c) the ticket is already `in-progress`; `start` accepts that and changes nothing. `start` refuses only if its own preconditions somehow no longer hold, which this preflight has already checked.
6. Continue into the resume protocol below, in the same invocation.

## Resume protocol (every `run`, including right after preflight)

Ported from D5, with the escalation, blocked-clearing and gate-summary rules from this ticket's refinement folded in:

1. Find the worktree (see "Worktree") and read `plan.json` from its story folder. If no worktree holds the branch, or `plan.json`'s `worktree` disagrees with `git worktree list --porcelain`, refuse, naming the reason, and change nothing.
2. If state is `awaiting-approval`: only `approve` (below) can change that from outside this loop. `run` invoked while still `awaiting-approval` does not advance — it re-shows the same gate's summary and the same `/story approve <ID>` command, so a plain `run` is always safe to retry without accidentally skipping the gate.
3. If state is `blocked`: treat the blocked phase as `in-progress` and go straight to step 5's reconciliation, except the attempt counter is reset to 1 first (a human-initiated retry is not the automatic escalation D9 describes) and state is set back to `running` once the phase is no longer blocked. (A phase sent back by human feedback at a gate, rather than by two failed attempts, follows the same reset-and-reconcile shape — see `approve <ID> [feedback]` below.)
4. Otherwise (state `running`): pick the first phase that is not `done` and whose `after` phases are all `done`. If none remains, every phase is `done` — go to "Finishing", below.
5. If the picked phase is `in-progress` (an interrupted run): reconcile. If its last recorded `runs` attempt has artifacts and a commit, and the phase's deterministic check(s) (table above) pass against that commit, mark it `done`, record the commit, and loop back to step 4 in the same invocation. Otherwise fall through to step 6 to launch a fresh attempt, handing the agent the notes already in `progress.md`.
6. Launch the phase's agent as a subagent (`Agent` tool, `subagent_type` the phase's agent name), giving it the story folder's path and the ticket's path (both under the worktree) explicitly at the top of the prompt, followed by the working-tree block (see "Worktree") — agents never derive or search for these themselves. Resolve model/effort/maxTurns in order: the ticket's `models:` override, then the archetype's `models:`, then the agent's own frontmatter (D9); on the *second* attempt of this phase, one tier above whatever that resolved to (`sonnet`→`opus` for model; one step up for effort; unchanged `maxTurns` unless the archetype says otherwise). If the agent stops at its turn cap without delivering a final report but the work on disk looks genuinely complete (not stuck or looping), prefer resuming that same agent (`SendMessage` to its agent id, asking it to wrap up in as few turns as possible) over discarding its work for a fresh, escalated attempt; record the combined turns/wall-clock of both invocations in one `runs` entry with a `note` explaining the resume, since this is not a clean single invocation. This case is not fully designed — flag it for `/story learn` (found running this ticket's own demonstration, CCW-005).
7. When the agent returns, run the phase's deterministic check(s) with `java tools/story-gate/StoryGate.java <check> <TICKET-ID> --root <worktree>` (or the `story-gate` launcher with the same `--root`). Record the attempt in the phase's `runs` (model, effort, turns, wall-clock seconds, outcome). When the checks pass, also give that `runs` entry a `note` holding their output lines (each verdict's summary line and its `- ` detail lines) joined with `; `, so a tests run keeps red's counts and traceability's line in `plan.json` (CCW-016).
8. On every check passing: commit the phase's changes *yourself* — none of the five agent prompts include a commit step, and something must produce the commit `plan.json` records (`FreezeCheck`, CCW-003, reads it straight out of the plan). Stage exactly what the phase's own `touches`/story-folder/test-tree scope covers, in the worktree (`git -C <worktree> add <path>...`, by name, never `-A`, per `CLAUDE.md`), confirm with `git -C <worktree> status` that nothing else is staged, commit with `git -C <worktree> commit` and a Conventional Commits message ending `[<TICKET-ID>]` (type by phase: `feat`/`refactor` for `design` and `implement`, `test` for `tests`, `docs` for `verify`'s ticket-file ticks), then mark the phase `done`, record the commit (`git -C <worktree> rev-parse HEAD`), and set the phase's `artifacts` to every file that commit added or changed, from `git -C <worktree> show --name-only --format= <commit>`, not from the agent's report: `freeze` and `disabled` judge only the files listed there, so a test file the phase edited but did not report would go unguarded (DFS-027.L-6). Loop back to step 4 in the same invocation. This is a deliberate, narrow exception to "commits are made only when the user asks": the human gates (D3) are what the user is actually approving, and between them the pipeline is meant to run unattended (D3's own words) — a per-phase pause to ask "commit this?" would defeat that. Flagged as friction for `/story learn`'s first real run to confirm or challenge.
9. On a check failing: append a defect to `plan.json`'s `defects` — `{"id": <next>, "phase": "<id>", "route": "<agent>", "summary": "<the verdict's compact reason>", "status": "open"}`. Routing: a `test-phase` failure routes to `test-author`; any other failure on the `implement` phase routes to `implementer`; a `design`-phase `touches` failure routes to `architect`; a `verify`-phase failure routes to `verifier`. Exception: when an `implement`-phase `green` failure is traced to a defect in a test rather than in the implementation (the implementer reports it, and the orchestrator confirms it by reading the failing test; the implementer's claim alone is not enough), route that defect to `test-author` instead. Record the implement attempt as `failed-gate`, then do not retry `implement`. Reopen `tests` the way `approve <ID> [feedback]` sends `tests` back: attempt counter reset to 1, verified with `green` rather than `test-phase`, and `implement` set to `in-progress` so step 5 reconciles it against its existing commit without relaunching the implementer. Then loop back to step 4. If this phase's `runs` now holds two failed attempts, set state `blocked` and stop — the escalation (see "Blocked", below) is the output. Otherwise mark the phase `pending` again and loop back to step 4 in the same invocation (a same-phase retry happens without returning control to the user; only `blocked` and a human gate stop the run).
10. Immediately after a phase is marked `done` (step 8), check whether a gate is configured right after it (its own id, or `done` if it was the last phase in `phases:`). If so: leave that gate's plan entry `pending` (only `approve`, below, changes it) and set overall state `awaiting-approval`; stop and show the stop (see "Human gate", below) instead of looping to step 4.

## Human gate

Shown once, whenever step 10 above stops the run:

- At the `design` gate: the architect's `## Summary` section from `design.md`, verbatim.
- At the `done` gate (the product-owner review, CCW-017): the verifier's `## Product owner review` section from `verification.md`, verbatim; when `verification.md` has no such section (written before CCW-017), its `## Summary` instead. Nothing else in the file is read for this purpose.
- Ends with the exact command: `/story approve <TICKET-ID>` (approve and continue), and a one-line reminder that free-text feedback instead of approval is read from the conversation, not a flag.
- Then sends the stop's notification (see "Notifications").

## Notifications (CCW-017)

At every stop that needs the user — a human gate, `blocked`, and a refused autonomous launch (the `tickets` skill's launch check) — send exactly one notification with the `PushNotification` tool, `status: "proactive"`. It is a deferred tool: load it first with `ToolSearch` (`select:PushNotification`) if its schema is not yet loaded. The message is one line under 200 characters, no markdown, starting with the ticket ID and what the user should act on and ending with the exact next command, for example:

- `CCW-018 ready for your review: 7/7 criteria met — /story approve CCW-018`
- `DFS-031 design ready for review — /story approve DFS-031`
- `DFS-031 blocked in implement: green failed twice — /story status DFS-031`
- `DFS-032 not launched: ticket changed after approval — re-confirm with /tickets refine DFS-032`

The tool skips itself when the user is at the terminal and may report "not sent"; that is expected, never an error, and changes nothing about the stop. Send none for routine progress (a phase finishing, a retry).

## Blocked

Shown once, whenever step 9 above sets state `blocked`:

- Names the phase, the open defect's summary, and the concrete options (D12): retry — fix whatever the defect names, then plain `/story run <TICKET-ID>` (step 3 above reconciles or re-attempts fresh, attempt counter reset); edit the ticket or `design.md` by hand and resume the same way; or cancel via `/tickets cancel <TICKET-ID>` — a cancelled ticket is never `ready`/`in-progress`, so a later `run` refuses it on its own, with no separate `/story cancel`.
- Then sends the stop's notification (see "Notifications").

## Finishing

When step 4 finds every phase `done`: run the `tickets` skill's `review <ID>` first, against the ticket file in the worktree (sets the ticket `in-review`; commit that edit with `git -C <worktree>` like a phase's). This is required, not optional — CCW-003's `consistency` check accepts only ticket status `in-review` or `done` when plan state is `done`, so skipping this step leaves `consistency` failing right at the finish line (found running this ticket's own demonstration). Then set state `done`. Say so, then:

- If `learnings.md` is non-empty, or `plan.json` has any defect (open or closed): end with the exact command `/story learn <TICKET-ID>`.
- Otherwise: state plainly that there is nothing to learn.

Setting the ticket all the way to `done` is not this skill's job — that stays a separate, human `/tickets done <ID>` once the user is satisfied with the result; `done` merges the branch into the repo checkout's `main` and offers to remove the worktree. D3's "before done" gate is about the pipeline's own finish line, not the ticket lifecycle's terminal state.

## Modes

### `run <ID>`

Preflight (if no `plan.json` yet) then the resume protocol, both above. This is the only mode that launches agents or advances state. Invoke it from the repo checkout; it creates or finds the story's worktree itself (see "Worktree").

### `status <ID>`

Read-only, never launches anything or writes `plan.json`. Finds the worktree (see "Worktree") and reads the plan from it (refuses, naming the reason, if there is no worktree or no plan yet, or if the plan's `worktree` disagrees with `git worktree list --porcelain`) and prints: the state, which phase is next (or, if `awaiting-approval`/`blocked`, which gate/phase that is), and the same next-command line that mode's stop would show — so `status` and a genuine stop always agree.

### `approve <ID> [feedback]`

Finds the worktree and reads the plan exactly as resume step 1 does (same refusals). Requires state `awaiting-approval`; otherwise refuse and say what state it is actually in.

- No feedback: mark the pending gate's plan entry `approved`, set state `running`, and continue the resume protocol immediately, in the same invocation — the user does not need a separate `/story run` to keep going.
- With feedback: do not approve. Append the feedback to the named phase's entry in `progress.md` as a note for its next attempt. Then send that phase, and everything after it, back for targeted rework:
  - The named phase, and any phase after it up to and including the phase the gate sits after, resets to `pending` with its attempt counter back at 1 — this is a human-initiated redirect, not D9's automatic-retry escalation, so it never inherits an escalated model/effort tier from a prior attempt.
  - A later phase whose own artifacts need no change once the rework lands (its existing commit will still satisfy its checks — for example `implement` when only tests are being added to) resets to `in-progress` instead of `pending`, so step 5 reconciles it against its existing commit rather than relaunching its agent.
  - Leave the gate `pending`, set state `running`, and continue the resume protocol immediately in the same invocation — do not wait for a separate `/story run`.
  - If the phase sent back is `tests`, and a later `implement` commit already exists on the branch: `test-phase` (`RedCheck`) cannot be used to verify the new attempt, because `RedCheck` requires every story test to fail against the stub, which cannot hold once `src/main` already carries the real implementation — it would report every story test as "passed against stubs," the inverse of what it is meant to catch. Verify the reopened tests phase with a plain green run instead (`java tools/story-gate/StoryGate.java green <TICKET-ID> --root <worktree>`, confirmed against the JUnit XML reports for the changed/added tests), since what actually needs proving here is "the new tests pass for real, against real behavior" — which `green` already checks. Do not force a red run by reverting `src/main`; that is out of every phase agent's boundaries. Record which check was actually used in the phase's `runs` entry note.
  - Tell the user the gate is still open, the feedback is recorded, and that the run is continuing with the phases now reset.

### `learn <ID>`

Requires the ticket's story folder to have a `plan.json` (any state). The story folder is in the worktree while one holds the branch (found and cross-checked as in resume step 1); after `/tickets done` has merged and removed the worktree, it is the repo checkout's `docs/tickets/completed/<TICKET-ID>-<slug>/`. If `learnings.md` is empty and `plan.json` has no defects: say there is nothing to learn and stop — this is the retro skip (mirrors D6's "runs only when there is something to learn"). Otherwise:

1. Launch the `retro` agent (`.claude/agents/retro.md`) with only the story folder's path, followed by the working-tree block when the story folder is in a worktree — no conversation history, files only, per D6/CCW-007.
2. It returns proposals in its final report (`READ:`/`FINDINGS:`) — it never writes; its tools have no `Write` (found running this ticket's own demonstration, contradicting an earlier assumption in this ticket's own Context and in D6's design-doc sketch, both written before CCW-004 built the actual agent).
3. For each proposal, check it against every ticket's existing entries the same way `/learn add` does (show up to three candidates with their full ID and Change/Evidence lines, ask "same friction?"), then draft the exact new entry (or the recurrence line) and show it; on the user's approval, write it directly to *this story's own* `docs/tickets/{backlog,completed}/<TICKET-ID>-<slug>/learnings.md`, identified `<TICKET-ID>.L-<n>` with `<n>` counted only within that file (CCW-010: entries live per-ticket, never in one shared, sequentially-numbered file) — all eight fields, `Status: proposed` — mirroring `/learn reflect`'s already-solved version of this exact problem (an agent proposes, the calling skill writes only what is approved).
4. End by naming `/learn review` as the next command for the user to run themselves. `learn` is `disable-model-invocation: true` and cannot be invoked here through the `Skill` tool ("reserved for explicit user invocation" — found running this ticket's own demonstration); `decide` and `apply` are typed by the user afterward exactly as `/learn` already documents. `learn` stays each ticket's `learnings.md` only writer from here on.

## What this skill never does

- Never edits a phase agent's artifacts (`design.md`, test files, source, `verification.md`) — only `plan.json`, and (via `tickets start`/`review`) the ticket's `status`/`updated` fields.
- Never runs more than one phase's agent per `run` invocation before returning control at a stop — a `pending`→`done` chain across several phases in one call is expected (step 8 loops), but the moment a human gate or `blocked` is reached, it stops.
- Commits each phase's own changes itself once that phase's deterministic check passes (see the resume protocol, step 8) — the one deliberate exception to "commits only when asked," bounded to exactly what that phase's gate just confirmed is in scope.
- Never checks a story branch out in the repo checkout, never `cd`s the orchestrating session into a worktree, and never runs a story's git command or check without `git -C <worktree>` or `--root <worktree>` (see "Worktree").
- Never invokes the `learn` skill directly (the `Skill` tool refuses it — `disable-model-invocation`); `learn <ID>` writes proposals itself and ends by naming `/learn review` for the user to type.
