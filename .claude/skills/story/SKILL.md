---
name: story
description: Advance a ready ticket through the story pipeline (architect, test-author, implementer, verifier, retro), launching each phase's agent, stopping at human gates, and resuming from plan.json. Run only by the user.
argument-hint: run|status|approve|learn <ID> [feedback]
disable-model-invocation: true
---

# Story

The orchestrator for the story pipeline (`docs/design/story-orchestration.md`, D1–D12). It runs in the main session, launches the phase agents (`.claude/agents/architect.md`, `test-author.md`, `implementer.md`, `verifier.md`, `retro.md`) as subagents, and stops at every human gate and at `blocked`. Every invocation either advances to the next stop and shows it, or refuses and says why — never a bare status (D12).

Usage: `/story <mode> <ID> [args]`. Modes: `run`, `status`, `approve`, `learn`. This skill is `disable-model-invocation: true`: run it only when the user types the command, never launch it on your own judgement — a run spends real tokens, makes real commits, and can flip a ticket's status.

## Story folder and `plan.json`

The story folder is the ticket's own folder, `docs/tickets/{backlog,completed}/<TICKET-ID>-<slug>/`. `plan.json` lives there, and this skill is its only writer (D5) — every phase agent writes its own artifacts and appends to `progress.md`/`learnings.md`, and reports its outcome in its final message, but never touches `plan.json` itself, so no agent can grade its own work.

Shape (template: `plan.json.tmpl` in this skill's folder):

```json
{
   "ticket": "DFS-001",
   "archetype": {"name": "pipeline-stage", "version": 2},
   "branch": "tickets/DFS-001-group-scanned-files-by-size",
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

1. Read the ticket. Refuse, naming the reason, and do nothing else, unless:
   - `status` is `ready` (a fresh start) or `in-progress` with a `plan.json` already present (a resume falls through to the resume protocol instead, skipping preflight);
   - `archetype` is not `none` — refuse with "implemented by hand, not through the pipeline" for a `none` ticket;
   - every `depends_on` entry has `status: done` — refuse naming the first one that isn't.
2. Resolve the branch: `tickets/<TICKET-ID>-<slug>` (the slug is the ticket's own folder name). Create it off the current branch tip if it does not exist; check it out if it does.
3. Create `plan.json` from the resolved archetype's `phases:`, `gates:` and `models:` (per the table above), state `running`, every phase `pending`, no defects.
4. Run the `tickets` skill's `start <ID>` (sets the ticket `in-progress`; refuses only if start's own preconditions somehow no longer hold, which preflight has already checked).
5. Continue into the resume protocol below, in the same invocation.

## Resume protocol (every `run`, including right after preflight)

Ported from D5, with the escalation, blocked-clearing and gate-summary rules from this ticket's refinement folded in:

1. Read `plan.json`.
2. If state is `awaiting-approval`: only `approve` (below) can change that from outside this loop. `run` invoked while still `awaiting-approval` does not advance — it re-shows the same gate's summary and the same `/story approve <ID>` command, so a plain `run` is always safe to retry without accidentally skipping the gate.
3. If state is `blocked`: treat the blocked phase as `in-progress` and go straight to step 5's reconciliation, except the attempt counter is reset to 1 first (a human-initiated retry is not the automatic escalation D9 describes) and state is set back to `running` once the phase is no longer blocked.
4. Otherwise (state `running`): pick the first phase that is not `done` and whose `after` phases are all `done`. If none remains, every phase is `done` — go to "Finishing", below.
5. If the picked phase is `in-progress` (an interrupted run): reconcile. If its last recorded `runs` attempt has artifacts and a commit, and the phase's deterministic check(s) (table above) pass against that commit, mark it `done`, record the commit, and loop back to step 4 in the same invocation. Otherwise fall through to step 6 to launch a fresh attempt, handing the agent the notes already in `progress.md`.
6. Launch the phase's agent as a subagent (`Agent` tool, `subagent_type` the phase's agent name), giving it the story folder's path and the ticket's path explicitly at the top of the prompt — agents never derive or search for these themselves. Resolve model/effort/maxTurns in order: the ticket's `models:` override, then the archetype's `models:`, then the agent's own frontmatter (D9); on the *second* attempt of this phase, one tier above whatever that resolved to (`sonnet`→`opus` for model; one step up for effort; unchanged `maxTurns` unless the archetype says otherwise). If the agent stops at its turn cap without delivering a final report but the work on disk looks genuinely complete (not stuck or looping), prefer resuming that same agent (`SendMessage` to its agent id, asking it to wrap up in as few turns as possible) over discarding its work for a fresh, escalated attempt; record the combined turns/wall-clock of both invocations in one `runs` entry with a `note` explaining the resume, since this is not a clean single invocation. This case is not fully designed — flag it for `/story learn` (found running this ticket's own demonstration, CCW-005).
7. When the agent returns, run the phase's deterministic check(s) with `java tools/story-gate/StoryGate.java <check> <TICKET-ID>` (or the `story-gate` launcher). Record the attempt in the phase's `runs` (model, effort, turns, wall-clock seconds, outcome).
8. On every check passing: commit the phase's changes *yourself* — none of the five agent prompts include a commit step, and something must produce the commit `plan.json` records (`FreezeCheck`, CCW-003, reads it straight out of the plan). Stage exactly what the phase's own `touches`/story-folder/test-tree scope covers (by name, never `git add -A`, per `CLAUDE.md`), commit with a Conventional Commits message ending `[<TICKET-ID>]` (type by phase: `feat`/`refactor` for `design` and `implement`, `test` for `tests`, `docs` for `verify`'s ticket-file ticks), then mark the phase `done` and record the commit (`git rev-parse HEAD`). Loop back to step 4 in the same invocation. This is a deliberate, narrow exception to "commits are made only when the user asks": the human gates (D3) are what the user is actually approving, and between them the pipeline is meant to run unattended (D3's own words) — a per-phase pause to ask "commit this?" would defeat that. Flagged as friction for `/story learn`'s first real run to confirm or challenge.
9. On a check failing: append a defect to `plan.json`'s `defects` — `{"id": <next>, "phase": "<id>", "route": "<agent>", "summary": "<the verdict's compact reason>", "status": "open"}`. Routing: a `test-phase` failure routes to `test-author`; any other failure on the `implement` phase routes to `implementer`; a `design`-phase `touches` failure routes to `architect`; a `verify`-phase failure routes to `verifier`. If this phase's `runs` now holds two failed attempts, set state `blocked` and stop — the escalation (see "Blocked", below) is the output. Otherwise mark the phase `pending` again and loop back to step 4 in the same invocation (a same-phase retry happens without returning control to the user; only `blocked` and a human gate stop the run).
10. Immediately after a phase is marked `done` (step 8), check whether a gate is configured right after it (its own id, or `done` if it was the last phase in `phases:`). If so: leave that gate's plan entry `pending` (only `approve`, below, changes it) and set overall state `awaiting-approval`; stop and show the stop (see "Human gate", below) instead of looping to step 4.

## Human gate

Shown once, whenever step 10 above stops the run:

- The authoring agent's own `## Summary` section, verbatim, from `design.md` (the `design` gate) or `verification.md` (the `done` gate) — nothing else in that file is read for this purpose.
- Ends with the exact command: `/story approve <TICKET-ID>` (approve and continue), and a one-line reminder that free-text feedback instead of approval is read from the conversation, not a flag.

## Blocked

Shown once, whenever step 9 above sets state `blocked`:

- Names the phase, the open defect's summary, and the concrete options (D12): retry — fix whatever the defect names, then plain `/story run <TICKET-ID>` (step 3 above reconciles or re-attempts fresh, attempt counter reset); edit the ticket or `design.md` by hand and resume the same way; or cancel via `/tickets cancel <TICKET-ID>` — a cancelled ticket is never `ready`/`in-progress`, so a later `run` refuses it on its own, with no separate `/story cancel`.

## Finishing

When step 4 finds every phase `done`: run the `tickets` skill's `review <ID>` first (sets the ticket `in-review`). This is required, not optional — CCW-003's `consistency` check accepts only ticket status `in-review` or `done` when plan state is `done`, so skipping this step leaves `consistency` failing right at the finish line (found running this ticket's own demonstration). Then set state `done`. Say so, then:

- If `learnings.md` is non-empty, or `plan.json` has any defect (open or closed): end with the exact command `/story learn <TICKET-ID>`.
- Otherwise: state plainly that there is nothing to learn.

Setting the ticket all the way to `done` is not this skill's job — that stays a separate, human `/tickets done <ID>` once the user is satisfied with the result. D3's "before done" gate is about the pipeline's own finish line, not the ticket lifecycle's terminal state.

## Modes

### `run <ID>`

Preflight (if no `plan.json` yet) then the resume protocol, both above. This is the only mode that launches agents or advances state.

### `status <ID>`

Read-only, never launches anything or writes `plan.json`. Reads the plan (refuses, naming the reason, if there is none yet) and prints: the state, which phase is next (or, if `awaiting-approval`/`blocked`, which gate/phase that is), and the same next-command line that mode's stop would show — so `status` and a genuine stop always agree.

### `approve <ID> [feedback]`

Requires state `awaiting-approval`; otherwise refuse and say what state it is actually in.

- No feedback: mark the pending gate's plan entry `approved`, set state `running`, and continue the resume protocol immediately, in the same invocation — the user does not need a separate `/story run` to keep going.
- With feedback: do not approve. Append the feedback to the phase's entry in `progress.md` as a note for its next attempt, leave the gate `pending` and state `awaiting-approval`. Tell the user the gate is still open, the note is recorded for the next attempt, and how to actually re-run that phase: editing `plan.json`'s phase status back to `pending` is the orchestrator's job, not the user's, so this case is expected to be rare in the pilot — record it in `learnings.md` if it comes up, since D5's resume protocol has no built-in "reject a passed phase" path yet.

### `learn <ID>`

Requires the ticket's story folder to have a `plan.json` (any state). If `learnings.md` is empty and `plan.json` has no defects: say there is nothing to learn and stop — this is the retro skip (mirrors D6's "runs only when there is something to learn"). Otherwise:

1. Launch the `retro` agent (`.claude/agents/retro.md`) with only the story folder's path — no conversation history, files only, per D6/CCW-007.
2. It returns proposals in its final report (`READ:`/`FINDINGS:`) — it never writes; its tools have no `Write` (found running this ticket's own demonstration, contradicting an earlier assumption in this ticket's own Context and in D6's design-doc sketch, both written before CCW-004 built the actual agent).
3. For each proposal, check it against the log's existing entries the same way `/learn add` does (show up to three candidates with their Change/Evidence lines, ask "same friction?"), then draft the exact new entry (or the recurrence line) and show it; on the user's approval, write it directly to `docs/learnings/log.md` in the log's format (next `L-<n>`, all eight fields, `Status: proposed`) — mirroring `/learn reflect`'s already-solved version of this exact problem (an agent proposes, the calling skill writes only what is approved).
4. End by naming `/learn review` as the next command for the user to run themselves. `learn` is `disable-model-invocation: true` and cannot be invoked here through the `Skill` tool ("reserved for explicit user invocation" — found running this ticket's own demonstration); `decide` and `apply` are typed by the user afterward exactly as `/learn` already documents. `learn` stays the log's only writer from here on.

## What this skill never does

- Never edits a phase agent's artifacts (`design.md`, test files, source, `verification.md`) — only `plan.json`, and (via `tickets start`/`review`) the ticket's `status`/`updated` fields.
- Never runs more than one phase's agent per `run` invocation before returning control at a stop — a `pending`→`done` chain across several phases in one call is expected (step 8 loops), but the moment a human gate or `blocked` is reached, it stops.
- Commits each phase's own changes itself once that phase's deterministic check passes (see the resume protocol, step 8) — the one deliberate exception to "commits only when asked," bounded to exactly what that phase's gate just confirmed is in scope.
- Never invokes the `learn` skill directly (the `Skill` tool refuses it — `disable-model-invocation`); `learn <ID>` writes proposals itself and ends by naming `/learn review` for the user to type.
