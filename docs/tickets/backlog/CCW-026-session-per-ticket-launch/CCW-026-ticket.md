---
id: CCW-026
title: Start each ready ticket in its own session from the coordinator
status: in-review
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: []
touches:
  - .claude/skills/tickets/SKILL.md
  - .claude/skills/tickets/TEMPLATE.md
  - .claude/skills/story/SKILL.md
  - CLAUDE.md
  - docs/design/story-orchestration.md
  - docs/tickets/lanes.md
  - tools/story-gate
  - docs/tickets/backlog/DFS-060-compact-results-screen/DFS-060-ticket.md
blocked_reason:
created: 2026-11-07
updated: 2026-11-08
completed:
---

# CCW-026: Start each ready ticket in its own session from the coordinator

## Story

As the user working in the coordinator session (dfs-product)
I want each ticket I make ready to start in a new Claude Code session of its own that runs `/story run <ID>` until the ticket is ready for my review
So that no long-lived lane session carries one ticket's context into the next, and I do not have to switch to a lane session to start work

## Context

Filed 2026-11-07 by dfs-harness at the user's request, after CCW-024's measurement run was handed to dfs-backend: "what do you think if "story run DFS-054 in dfs-backend" is actually starting new session to fully implement the ticket this should help with context management? ... i want to work in product session and once a ticket is ready kick it off in a new session (frontned backend probably dont matter at this point) so i executes until ready for review".

- **Where context grows today.** Every pipeline phase (architect, test-author, implementer, verifier, retro) already runs in a subagent with its own fresh context. What grows is the session orchestrating `/story run`: a lane session such as dfs-backend keeps every ticket's gate summaries, phase reports and `plan.json` handling, ticket after ticket, and resends all of it every turn. CCW-022's Context measured this: long-lived main sessions dominate the cost (dfs-backend about 260k context per call), pipeline subagents under 10%. A session per ticket starts each orchestration empty.
- **What exists that this changes.**
  - CCW-021 decision 3 and the `tickets` skill's "The coordinator": the coordinator "never starts, launches or closes", because `start`, `/story run` and `done` create, merge or remove a lane owner's branches and worktrees.
  - CCW-018 "Sessions and lanes": a session's lanes come from its name in `docs/tickets/lanes.md` (via `ListAgents`). A session named after a ticket (for example `DFS-054`) owns no lane, so `/tickets` would ask which lanes it owns.
  - CCW-017 "Autonomous launch": three launch points, all inside the lane-owning session (after `refine`'s ready commit, after `done`, on an unblock message).
  - CCW-018 messages 2 and 3 (unblock, harness change landed) and CCW-021's ready hand-off assume long-lived lane sessions to message.
- **Related tickets.** CCW-025 (draft) drops the coordinator's ready hand-off; a launch from the coordinator would take its place. CCW-022 (ready) adds clear-at-ticket-boundary rules for lane sessions; a session per ticket makes the lane-session part of it moot, and the two should be reconciled before either is implemented.
- **Two ways to start a session, from `claude --help` (Claude Code 2.1.319, 2026-11-07).** `claude [options] [prompt]` starts an interactive session with a first prompt; `-n, --name <name>` names it; `--permission-mode` accepts `acceptEdits`, `auto`, `bypassPermissions`, `manual`, `dontAsk`, `plan`; `-p/--print` runs without a terminal, where `--permission-prompts none` denies anything that would prompt.
  1. Interactive, in a new terminal window, from the coordinator's shell, for example `start powershell -NoExit -Command "claude -n DFS-054 '/story run DFS-054'"`: visible; gates and permission prompts are answered in that window.
  2. Headless, `claude -p "/story run DFS-054" ...` in the background: nothing to watch, nothing can be approved midway, so only a `gates: [done]` ticket runs to the end; a stop ends the process and is resumed with `--resume`.
  Not verified: that a skill invoked as `/story run <ID>` works as the first prompt in either form.
- **Interim (2026-11-07):** run `/clear` in a lane session before each `/story run`; it gives most of the context saving with no harness change.

## Decisions

1. **Interactive sessions only, started from a printed line** (2026-11-08, confirmed in refine; user idea of 2026-11-07). Every ready commit, story stop and `done` prints a ready `claude -n <ID> "<command>"` line for the next step (`/story run <ID>`, `/story approve <ID>`, `/tickets done <ID>`, ...) in place of CCW-022's `safe to /clear; resume with <command>`; the user pastes it into a new terminal. No session spawns another, and headless `-p` is not used (unverified, cannot answer gates). Question 2 follows: nothing launches, so CCW-021 decision 3 ("never launches") stands. A coordinator that opens the window itself is a later ticket, not this one.

2. **The printed line carries `--permission-mode auto`** (2026-11-08, chosen by the user over no flag, `acceptEdits` and an extended allowlist). The line is `claude -n <ID> --permission-mode auto "<command>"`. `.claude/settings.json` is not extended, so the ticket needs no allowlist list. Cost accepted: a classifier, not a readable rule, decides what runs unprompted, in sessions that move and merge files; the safety invariants and the delete-time proof stay in the core, not in the permission layer.

3. **Sessions no longer own lanes; `lane` stays a label** (2026-11-08, confirmed in refine; dfs-harness's proposal). `lanes.md` shrinks to the `coordinator` line (the catch-up hook keys on it). Removed: the one-owner-per-lane rule, the session-lane lookup (`ListAgents` name against `lanes.md`) in `list`, `new`, `refine`, `start`, `done` and `cancel`, `validate`'s lane-map rule, and messages 1 to 3 with the coordinator's ready hand-off. `lane` stays on tickets and groups `list`; "Dispatchable now" covers every lane, grouped by non-overlapping `touches`. Launch points 2 and 3 become printed lines (decision 1). If the criteria exceed 15 or the decisions 8, propose splitting at the seam printed lines/permissions versus lane removal.

4. **The ticket's own session runs the whole ticket, `done` included** (2026-11-08, confirmed in refine). Gate approvals, the product-owner review, `/story learn` and `/tickets done <ID>` all happen in the session named after the ticket, which owns its branch and worktree. The finish-stop line prints `/tickets done <ID>`; the coordinator still never runs `done`, `start` or `/story`.

5. **`done` and `refine` print the launch lines; `autorun` is retired** (2026-11-08, confirmed in refine). At its end `done` prints one `claude -n <ID> --permission-mode auto "/story run <ID>"` line (or `/tickets start <ID> --worktree` for an `archetype: none` ticket) for every ticket the close made dispatchable (`ready`, every dependency `done`, no branch), whatever its `autorun` value; `refine`'s ready commit prints the line for the ticket just made ready, or names the dependency it waits on. Removed: the `autorun` field, the autonomous-launch section with its launch check and approval-commit rule (CCW-017), step 4a's launching and messaging, and `validate`'s autorun rule; the `autorun` line is deleted from the open ticket that still carries it (DFS-060, mechanical count 2026-11-08), and left in completed tickets, which are history and which `validate` does not check. A later ticket may let the coordinator spawn the window.

6. **No cap; print the non-overlapping sets** (2026-11-08, confirmed in refine). `done`'s and `list`'s lines are grouped by "Dispatchable now" sets, and two lines whose `touches` overlap carry the note "do not run together". The harness cannot see running sessions, so a cap would be text only. Open question 8 (`-p` as a first prompt) is moot: headless is not used (decision 1).

7. **CCW-025 cancelled as superseded; CCW-022 stays done** (2026-11-08, confirmed in refine; cancel commit `docs: cancel CCW-025 [CCW-025]`). CCW-026 replaces CCW-022's "safe to /clear; resume with" lines with the launch lines and keeps its `/compact` template, the `plan.json` commit before every stop and the keep-it-small rules. `depends_on` stays empty.

8. **`catch-up --lanes` lists every started ticket in any lane** (2026-11-08, confirmed in refine). `list` in any non-coordinator session runs it and shows, for each started ticket, its worktree, `plan.json` state and phase, and its next command as a launch line (decision 1). The SessionStart hook stays coordinator-only. The lane filter and its `lanes.md` lookup are removed from `CatchUp`.

## Acceptance criteria

The full ID of each criterion is `CCW-026.AC-<n>`.

- **AC-1** `refine`'s ready commit ends with the launch line for the ticket just made ready when every `depends_on` is `done`: `claude -n <ID> --permission-mode auto "/story run <ID>"` for a pipeline ticket, `"/tickets start <ID> --worktree"` for `archetype: none`; with a dependency open it names that dependency and prints no line.
- **AC-2** Every story stop (gate, blocked, finished) ends with the launch line for its next command in place of `safe to /clear`: `/story approve <ID>` at a gate, `/story run <ID>` after `blocked`, `/story learn <ID>` or `/tickets done <ID>` at the finish. Uncommitted files of the session's own are still listed beside it, and `plan.json` is still committed first (CCW-022 decision 1).
- **AC-3** `done`'s end prints one launch line per ticket the close made dispatchable (`ready`, every dependency `done`, no branch `tickets/<its ID>-<slug>`), whatever its former `autorun` value, grouped by non-overlapping `touches`; two lines whose `touches` overlap carry the note "do not run together". With none unblocked it prints none.
- **AC-4** `done` neither starts a story nor sends a message, and `new --for` no longer exists as a request form that messages an owner; the harness-change, unblock and ready messages are not in either skill.
- **AC-5** `/tickets list`, `new`, `refine`, `start`, `done` and `cancel` run in a session named after a ticket (a name absent from `lanes.md`) without asking which lanes it owns.
- **AC-6** `list` groups the table by `lane` and prints "Dispatchable now" and "Started elsewhere" over all lanes, each dispatchable ticket with its launch line; there is no "Other lanes" heading.
- **AC-7** `docs/tickets/lanes.md` holds only the `coordinator` line; `catch-up --hook` still prints for the session mapped to `coordinator` and nothing for any other name; `validate` reports a line in `lanes.md` that is not `<name>: coordinator`, and a second `coordinator` line.
- **AC-8** `catch-up --session <name> --lanes` lists every started ticket of every lane with its worktree, `plan.json` state, phase and next command as a launch line, for any name that is not the coordinator; the coordinator and a missing name get nothing from it.
- **AC-9** `autorun` is gone: the field is absent from the template, the skills and `CLAUDE.md`; `validate` does not check it; DFS-060 no longer carries it.
- **AC-10** Every launch line this ticket prints carries `--permission-mode auto`, and the session name is the ticket ID; `.claude/settings.json` is unchanged.
- **AC-11** `CLAUDE.md` "Tickets" and `docs/design/story-orchestration.md` describe sessions per ticket, the printed launch lines and lane as a label, with no mention of session lane ownership or `autorun`.
- **AC-12** `tools/story-gate/story-gate selftest` passes with the lane-filter cases replaced by the all-lanes cases.

## Scenarios

Per scenario, `[run]` = `selftest` or a grep/script run recorded in `verification.md`; `[read]` = checked by reading the edited skill text.

```gherkin
Feature: Start each ready ticket in its own session

  @AC-1
  Scenario: refine prints the line for a ready ticket with no open dependency  [read]
    Given CCW-0XX is a pipeline ticket whose depends_on are all done
    When the user confirms ready and the commit "docs: refine CCW-0XX [CCW-0XX]" is made
    Then the reply ends with: claude -n CCW-0XX --permission-mode auto "/story run CCW-0XX"
    And for an archetype none ticket the line ends with "/tickets start CCW-0XX --worktree"

  @AC-1
  Scenario: refine names the open dependency instead of a line  [read]
    Given a ticket depends on CCW-099, which is in-progress
    When its ready commit is made
    Then the reply names CCW-099 and prints no claude line

  @AC-2
  Scenario: a gate stop prints the approve line  [read]
    Given DFS-054's session reaches the design gate
    When plan.json is committed and the stop is printed
    Then the last line is: claude -n DFS-054 --permission-mode auto "/story approve DFS-054"
    And the text "safe to /clear" is absent from the story skill [run: grep]

  @AC-2
  Scenario: uncommitted files are listed beside the line  [read]
    Given the session has an uncommitted file docs/x.md
    When a stop is printed
    Then the line is followed by "(uncommitted, on disk: docs/x.md)"

  @AC-3
  Scenario: done prints lines for the unblocked, flagging overlaps  [read]
    Given closing CCW-026 makes A (touches .claude/skills/story) and B (touches .claude/skills/story/SKILL.md) dispatchable, and C (touches tools/story-gate) dispatchable
    When done finishes
    Then it prints three claude lines
    And A and B carry "do not run together" and C does not

  @AC-3
  Scenario: done prints nothing when nothing is unblocked  [read]
    Given no ready ticket depends on the closed one
    When done finishes
    Then no claude line is printed

  @AC-4
  Scenario: no message or launch text remains  [run: grep]
    When .claude/skills is searched for "SendMessage", "message 1", "message 2", "message 3", "Receiving an unblock" and "Autonomous launch"
    Then there are no hits

  @AC-5
  Scenario: a ticket-named session is not asked for lanes  [read]
    Given a session named CCW-099 and a lanes.md without that name
    When /tickets list runs
    Then the skill text contains no step that looks up the session's lanes or asks for them

  @AC-6
  Scenario: list covers all lanes with lines  [read]
    Given ready tickets in backend, frontend and harness
    When /tickets list runs in any session
    Then the table is grouped by lane, Dispatchable now covers all three, each row has its launch line, and no "Other lanes" heading exists

  @AC-7
  Scenario: lanes.md holds only the coordinator line  [run: selftest and grep]
    Given lanes.md after the edit
    Then its fenced block is exactly "dfs-product: coordinator"
    And catch-up --hook prints for session dfs-product and 0 bytes for session CCW-099 (selftest cases)
    And validate's text reports a line "dfs-backend: backend" [read]

  @AC-8
  Scenario: the all-lanes view  [run: selftest]
    Given worktrees for a backend ticket and a harness ticket, one at a gate and one blocked
    When catch-up --session CCW-099 --lanes runs
    Then both appear with worktree, state, phase and a claude launch line
    And with --session dfs-product, and with no name, the output is empty

  @AC-9
  Scenario: autorun is gone  [run: grep]
    When .claude, CLAUDE.md, docs/design and docs/tickets/backlog are searched for "autorun"
    Then the only hits are in this ticket's own text; docs/tickets/completed is not searched

  @AC-10
  Scenario: every line has the same shape  [run: grep]
    When the skills' and CatchUp's launch-line templates are searched for "claude -n"
    Then each contains "--permission-mode auto" and a ticket ID after -n
    And git diff on .claude/settings.json is empty

  @AC-11
  Scenario: documentation is consistent  [run: grep]
    When CLAUDE.md and docs/design/story-orchestration.md are searched for "owns", "owner", "lane session", "launch point" and "safe to /clear"
    Then the remaining hits, if any, are listed in verification.md and none states the old model

  @AC-12
  Scenario: selftest  [run]
    When tools/story-gate/story-gate selftest runs
    Then it exits 0
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md`: remove lane ownership, "Autonomous launch", the messages, the coordinator's ready hand-off, `new --for`, the lane steps in `list`/`start`/`done`/`cancel`, and `validate`'s lane-map and autorun rules; add the launch lines and the overlap note; replace CCW-022's clear line
- `.claude/skills/tickets/TEMPLATE.md`: drop the `autorun` comment line
- `.claude/skills/story/SKILL.md`: "Every stop" prints the launch line; no autonomous start
- `CLAUDE.md` ("Tickets")
- `docs/design/story-orchestration.md`
- `docs/tickets/lanes.md`: shrink to the coordinator line
- `tools/story-gate`: `CatchUp.java` (`--lanes` over all lanes, launch lines) and `CatchUpSelfTest.java`
- `docs/tickets/backlog/DFS-060-compact-results-screen/DFS-060-ticket.md`: delete its `autorun` line only

**Out of scope:**
- A coordinator that opens the window itself, and headless `-p` sessions.
- Cloud or remote sessions: the build needs the local Windows toolchain (JDK 25, the Node download under `server/.gradle/`).
- Changing the pipeline phases or their agents; `.claude/settings.json`.
- Removing `autorun` from completed tickets.

## Definition of done

- [x] Every scenario above is checked (mechanically or by reading, as recorded per scenario); exceptions in verification.md
- [x] `tools/story-gate/story-gate selftest` passes (126 cases)
- [x] No files changed outside the paths listed under Touches (git status: only touched paths plus this ticket's verification.md)
- [x] The old-form search over `.claude/`, `CLAUDE.md` and `docs/design/` (lane ownership, `autorun`, launch point, owner, message 1 to 3, `safe to /clear`, ready hand-off, `ListAgents`) is repeated before review, with its hits recorded in `verification.md` (CCW-018.L-1)
- [ ] A live run: the coordinator makes a ticket ready, the printed line is pasted into a new terminal, and that session takes the ticket to the product-owner review without any lane question

## Open questions

None.

## Evidence

**Live trials so far** (2026-11-07): CCW-022 and CCW-028 each run in a session named after the ticket (`claude -n CCW-022`, `claude -n CCW-028 "/tickets start CCW-028 --worktree"`), answering `harness` to the lane question and declining the `lanes.md` line. Record what tripped them here (question 8: whether the first-prompt form ran the skill).

- **CCW-028, 2026-11-07 17:38:** `claude -n CCW-028 "/tickets start CCW-028 --worktree"` ran the tickets skill as the first prompt, interactively, with no typing: it created the branch and worktree and set `in-progress` in 17 s. Question 8 is answered for the interactive form; `-p` is still unchecked. The lane lookup found `CCW-028` missing from `lanes.md` and the session went ahead without asking, citing the user's explicit start and the ticket's `harness` lane, then offered the `lanes.md` line. The skill says to ask; the session's choice was the sensible one, which supports dropping session ownership of lanes.
- **CCW-028's message 3, 2026-11-07 17:51:** on merging (`38f44c2`) the CCW-028 session sent message 3 to every running session in `lanes.md`, as dfs-harness's own prompt told it to. In dfs-harness it bought nothing (that session wrote the ticket and already knew the change) and cost a full turn at its context size: the message plus two confirming checks and a reply, about three requests over the whole context. dfs-backend and dfs-frontend paid the same unless they were about to run `/story`. Earlier the same day, dfs-frontend's request message for CCW-027 (message 1) woke dfs-harness the same way. Evidence for dropping messages 1 to 3, message 3 above all: CCW-028's AC-4 run showed pipeline subagents read their agent definitions at spawn, from the session's own checkout (DFS-059's test-author, implementer and verifier, in a dfs-frontend session started before the merge, ran on the new text; CCW-028 `verification.md`, "AC-4 run"). So a merged agent change reaches running sessions with no message and no restart. Not verified for skills and `CLAUDE.md`, which the orchestrating session itself holds.
