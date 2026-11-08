---
id: CCW-026
title: Start each ready ticket in its own session from the coordinator
status: draft
type: feature
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
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

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-026.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: Start each ready ticket in its own session

  @AC-1
  Scenario: <happy path: coordinator makes a ticket ready and a new session runs it to review>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to decide in refinement; likely `.claude/skills/tickets/SKILL.md`, `.claude/skills/story/SKILL.md`, `docs/tickets/lanes.md`, `CLAUDE.md` ("Tickets"), and a launcher script under `tools/`>

**Out of scope:**
- Cloud or remote sessions: the build needs the local Windows toolchain (JDK 25, the Node download under `server/.gradle/`).
- Changing the pipeline phases or their agents.

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] A live run: the coordinator makes a ticket ready and a new session takes it to the product-owner review without the user typing in a lane session

## Open questions

1. **Launch form:** interactive window (visible, gates answerable), headless `-p` (only `gates: [done]` tickets), or both chosen per ticket by its `gates`?
2. **Who launches:** the coordinator right after its ready commit (reverses CCW-021 decision 3 for launching only), a separate command the user runs in the coordinator, or a small script the user runs from any shell?
3. **Permissions in the new session:** which `--permission-mode` (the pipeline runs Gradle, git and file edits), and whether the project's `.claude/settings.json` allowlist is enough.
4. **Lanes:** keep `lane` on tickets (for parallel-safety and grouping) but stop mapping it to long-lived sessions? How does a ticket-named session pass the `/tickets` lane lookup (for example a `DFS_SESSION`-style variable, or "a session named after a ticket owns that ticket's lane")?
5. **What stays with the user:** gate approvals, `/tickets done` (merge and cleanup) — in the ticket's session, or back in the coordinator? CCW-021 keeps `done` away from the coordinator.
6. **Messages and launch points:** what replaces CCW-018 messages 2 and 3 and CCW-017's launch points 2 and 3 when there is no long-lived lane session (for example the ticket session's `done` launching the next autorun ticket in a new session).
7. **Parallel runs:** may the coordinator start several sessions at once when their `touches` do not overlap ("Dispatchable now" sets), and is there a cap?
8. **Unverified CLI behaviour:** check that `/story run <ID>` as the first prompt invokes the skill, interactively and with `-p`, before deciding question 1.
9. **Reconcile with CCW-022 and CCW-025:** merge, supersede, or depend on.

**Proposed answer to 4 to 6** (dfs-harness, 2026-11-07, for refinement to confirm or reject): keep `lane` as a label on tickets (it groups `/tickets list` and says which toolchain a ticket needs) and keep the `coordinator` line in `lanes.md` (the catch-up hook keys on it); drop session ownership of lanes, so `lanes.md` shrinks to the coordinator line. That removes the lane question at every ticket session's start, the one-owner-per-lane rule, and messages 1 to 3, which assume long-lived receivers. Parallel safety already comes from `touches` ("Dispatchable now" groups non-overlapping sets), never from lanes. Launch points 2 and 3 become "start a new session for the unblocked ticket". What depends on the lane map today and would change with it: the tickets skill ("Sessions and lanes", `list`, `start`, `done` step 4a, `validate`'s lane-map rule), `tools/story-gate` catch-up, `CLAUDE.md` "Tickets". Not done by hand meanwhile: removing lines from `lanes.md` now would break the catch-up and add `/tickets` questions.

**Live trials so far** (2026-11-07): CCW-022 and CCW-028 each run in a session named after the ticket (`claude -n CCW-022`, `claude -n CCW-028 "/tickets start CCW-028 --worktree"`), answering `harness` to the lane question and declining the `lanes.md` line. Record what tripped them here (question 8: whether the first-prompt form ran the skill).
