---
id: CCW-005
title: Build the story orchestrator skill
status: ready
type: feature
scope: [build]
archetype: none
priority: medium
depends_on: [CCW-001, CCW-003, CCW-004]
touches: [.claude/skills/story, .claude/agents, docs/learnings]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed:
---

# CCW-005: Build the story orchestrator skill

*(Formerly DFS-012; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the maintainer running a story
I want `/story run <ID>` to advance a story through the pipeline, stop at the human gates, and resume from `plan.json`
So that supervised multi-agent implementation is repeatable and cheap to restart

## Context

A skill in the main session that launches the phase agents as subagents (D1). Each run advances until the next human gate or the end, then returns; running it again resumes from the plan.

- **State (D5):** one `plan.json` per story, the orchestrator its only writer. Shape: ticket, archetype and version, branch, state (`running`, `awaiting-approval`, `blocked`, `done`), phases (id, agent, `after`, status, `runs` with model, effort, turns and outcome, artifacts, commit), gates, defects.
- **Resume protocol (D5):** read the plan; mark an approved gate; pick the first phase not done whose `after` phases are done; if it is `in-progress`, reconcile (artifacts and commit exist and the gate passes, then done; otherwise re-run with the notes from `progress.md`); launch; run the phase's deterministic gate on return; on failure record a defect and route it; after two failed attempts set `blocked` and escalate.
- **Gates (D3):** two by default, after design and before `done`, configurable per archetype; each shows a summary of about twenty lines that lists the acceptance criteria with their coverage status.
- **Gate tools (D7, D10):** calls `tools/story-gate` after each phase.
- **Models (D9):** resolved ticket over archetype over agent; a second attempt runs one tier up through the per-invocation model.
- **Learning (D6):** `/story learn <ID>` shows ranked proposals; the user applies, parks or rejects each; decisions go in `docs/learnings/log.md`; applied changes bump the archetype version. Every proposal cites evidence. `docs/learnings/log.md` already exists as a manual log (2026-09-21, entries `L-<n>`); `/story learn` is a thin wrapper (decided while refining CCW-007, 2026-09-21): it runs the `retro` agent, which appends `proposed` entries in that format, then hands over to `/learn review`, `decide` and `apply` (CCW-007), which stay the log's only writer. Details are for this ticket's refinement.
- **Pilot data (D8):** the run records in `plan.json` are the raw material for the metrics.

**Decisions made while refining this ticket:**
- **Archetype (decided 2026-09-22):** `none`. This ticket builds the orchestrator itself, so it cannot run through a pipeline that does not yet exist to build it — the same reasoning CCW-001 and CCW-003 (also harness-building tickets) used. Rejected: a new archetype for skill-authoring tickets (speculative, no second ticket to validate it against, against D4's "as stories need them").
- **Gate approval (decided 2026-09-22):** `/story approve <ID>` is the explicit trigger for a pending human gate. It marks the gate approved and resumes the run in the same invocation, continuing to the next stop. Declining a gate is conversational: the user just says what is wrong, and the next `/story run` (with no approval recorded) re-shows the same pending-gate summary rather than advancing, since the resume protocol cannot pick a later phase past a pending gate. Rejected: inferring approval from plain conversation (nothing for a deterministic check to point at, and breaks D12's rule that every stop ends with a literal next command).
- **Branch naming (decided 2026-09-22):** `tickets/<TICKET-ID>-<slug>`, not the design doc's `story/<ID>` sketch — a ticket can be a bug or chore, not only a "story" — with the slug taken from the ticket's own folder name. Preflight creates the branch if it does not exist, or checks it out if it does (resuming an in-progress story). Rejected: `EnterWorktree` isolation (contradicts D2's stated single-worktree default, and that tool is reserved for explicit instruction).
- **Token counts (decided 2026-09-22):** the `runs` record keeps `model`, `effort`, `turns` and wall-clock time only; no token count. D9 leaves its source unconfirmed, and pinning it down is the pilot's (CCW-006's) job. Rejected: sourcing token counts now (blocks this ticket on an open question that belongs to CCW-006).
- **Gate summary source (decided 2026-09-22):** the authoring agent writes its own fixed `## Summary` section (about 20 lines) as part of its normal output — the architect in `design.md`, the verifier in `verification.md` — and the orchestrator extracts and prints it verbatim, no extra model call. This ticket therefore also makes that small addition to `.claude/agents/architect.md` and `.claude/agents/verifier.md` (added to Touches). Rejected: the orchestrator, or a fresh summarizer call, generating the summary itself at gate time (an avoidable model call, against D9/D10's "gates are scripts, minimal reads").
- **Blocked handling (decided 2026-09-22):** plain `/story run <ID>` clears `blocked` the same way it resumes an approved gate: it reconciles the blocked phase first (marks it done without re-running if a manual fix already satisfies the phase's gate), otherwise launches a fresh attempt with the attempt counter reset to 1 and flips state back to `running`. Cancelling the whole story reuses the existing `/tickets cancel <ID>`; a cancelled ticket is never `ready`/`in-progress`, so `/story run` refuses it afterward on its own. Rejected: dedicated `/story retry`/`/story cancel` commands (contradicts the Scope section's own four-command list, and redundant with the existing resume path).
- **`/story learn`'s commit rule (confirmed 2026-09-22, no new design needed):** CCW-007 already decided `/story learn <ID>` is a thin wrapper — it runs the `retro` agent to append `proposed` entries, then hands off verbatim to `/learn review`, `decide` and `apply`, which already never commit. Nothing here reimplements that.
- **The gate name `done` (interpretation, decided 2026-09-22):** an archetype's `gates:` list (D4) names either an actual phase id (pause after that phase completes) or the reserved name `done`, meaning "after the last phase in `phases:`, before the pipeline finishes" (D3's "before done" gate) — not tied to a specific phase id. `pipeline-stage`'s `gates: [design, done]` reads as: pause after `design`, and pause again after `verify` (its last phase) before finishing.

## Acceptance criteria

The full ID of each criterion is `CCW-005.AC-<n>`.

- **AC-1** Starting a ready story (dependencies done, archetype not `none`) creates `plan.json` from the archetype's phases, creates the `tickets/<TICKET-ID>-<slug>` branch if it does not exist or checks it out if it does, and sets the ticket to `in-progress`.
- **AC-2** `/story run` refuses a ticket that is not `ready` (or `in-progress` when resuming), whose dependencies are not all `done`, or whose archetype is `none`, naming the reason.
- **AC-3** A human gate configured for the reached phase boundary stops the run: the plan is marked `awaiting-approval` and the run shows the authoring agent's own `## Summary` section verbatim (the architect's from `design.md` after the design phase, the verifier's from `verification.md` before the `done` gate).
- **AC-4** `/story approve <ID>` marks the pending gate approved and resumes the run in the same invocation.
- **AC-5** Resuming picks the first phase that is not done and whose `after` phases are all done.
- **AC-6** An interrupted (`in-progress`) phase is reconciled on resume: marked done without re-running when its artifacts and commit exist and its deterministic gate passes; otherwise re-run with the notes from `progress.md`.
- **AC-7** A failed deterministic gate records a defect and routes it to the right agent; after two failed attempts on the same phase the plan is set `blocked` and the escalation names the phase, the defect, and the concrete options (retry via `/story run`, edit and resume, or cancel via `/tickets cancel`).
- **AC-8** A retried phase runs one tier above its resolved model/effort, and the attempt is appended to the phase's `runs` list with model, effort, turns and outcome.
- **AC-9** A `blocked` plan is cleared by plain `/story run <ID>`: reconciled first, otherwise a fresh attempt with the attempt counter reset to 1 and state returned to `running`.
- **AC-10** The retro phase is skipped when `learnings.md` is empty and the story had no defects.
- **AC-11** `/story learn <ID>` launches the `retro` agent to append proposed entries to `docs/learnings/log.md`, then hands off to `/learn review`, `decide` and `apply` verbatim; it writes nothing itself and never commits.
- **AC-12** Reaching `state: done` says so and names `/story learn <ID>` as the next step when there is something to learn, or says plainly that there is nothing to learn when there is not.
- **AC-13** Every stop point (a human gate, a run that resumes and stops again, `blocked`, `done`, and each proposal `/story learn` shows) ends its output with the literal next command, never a bare status.

## Scenarios

```gherkin
Feature: Story orchestrator

  @AC-1
  Scenario: Starting a story creates its branch and plan
    Given a ready ticket whose dependencies are done and whose archetype is not none
    When `/story run DFS-001` is invoked for the first time
    Then the `tickets/DFS-001-<slug>` branch is created, `plan.json` is created from the archetype's phases, and the ticket is set to in-progress

  @AC-1
  Scenario: Resuming reuses the existing branch
    Given an in-progress story whose branch already exists
    When `/story run DFS-001` is invoked again
    Then the existing branch is checked out rather than recreated

  @AC-2
  Scenario: An unready story is refused
    Given a ticket that is not ready and not in-progress
    When `/story run` is invoked
    Then it refuses and says why

  @AC-2
  Scenario: A story with undone dependencies is refused
    Given a ready ticket whose depends_on list is not all done
    When `/story run` is invoked
    Then it refuses and names the dependency that is not done

  @AC-2
  Scenario: A story with archetype none is refused
    Given a ticket whose archetype is none
    When `/story run` is invoked
    Then it refuses and says the ticket is implemented by hand, not through the pipeline

  @AC-3 @AC-13
  Scenario: A run stops at the design gate
    Given the design phase's deterministic gate has passed and a gate is configured after design
    When the run reaches the gate
    Then the state is awaiting-approval, the architect's own Summary section from design.md is shown verbatim, and the output ends with the exact `/story approve` command to continue

  @AC-3 @AC-13
  Scenario: A run stops at the done gate
    Given the last phase's deterministic gate has passed and a done gate is configured
    When the run reaches the gate
    Then the state is awaiting-approval, the verifier's own Summary section from verification.md is shown verbatim, and the output ends with the exact `/story approve` command to continue

  @AC-4
  Scenario: Approving a gate resumes in the same command
    Given a plan awaiting-approval
    When `/story approve DFS-001` is invoked
    Then the gate is marked approved and the run continues without a separate `/story run` invocation

  @AC-5 @AC-13
  Scenario: Resuming after approval stops again at the next gate
    Given an approved gate followed by phases that reach another configured gate
    When `/story run DFS-001` is invoked again
    Then it resumes at the first phase that is not done and whose after phases are done, and its own stop still ends with the next command

  @AC-6
  Scenario: Reconciling an interrupted phase
    Given a phase marked in-progress whose artifacts and commit exist and whose gate passes
    When the run resumes
    Then the phase is marked done without being re-run

  @AC-6
  Scenario: An interrupted phase without evidence is re-run
    Given a phase marked in-progress with no artifacts
    When the run resumes
    Then the agent is re-run with the notes from progress.md

  @AC-7
  Scenario: A failed red gate routes back to the test author
    Given the red gate fails
    When the failure is recorded
    Then a defect routes to the test author

  @AC-7 @AC-13
  Scenario: Two failed attempts block the story with named options
    Given a phase that has failed its gate twice
    When the second failure is recorded
    Then the state is set to blocked, and the escalation names the phase, the defect, and the options to retry, edit and resume, or cancel

  @AC-8
  Scenario: A retry runs one tier up
    Given a phase whose first attempt failed its gate
    When it is retried
    Then the model is one tier above the resolved one, and the runs list records model, effort, turns and outcome

  @AC-9
  Scenario: A manual fix clears blocked without a fresh attempt
    Given a blocked phase whose artifacts and commit now satisfy its gate after a manual fix
    When `/story run DFS-001` is invoked
    Then the phase is reconciled done, and the state returns to running

  @AC-9
  Scenario: Blocked without a fix gets a fresh attempt
    Given a blocked phase with no new artifacts
    When `/story run DFS-001` is invoked
    Then the agent is re-run with its attempt counter reset to 1, and the state returns to running

  @AC-9
  Scenario: Cancelling a blocked story
    Given a blocked story the user decides to abandon
    When `/tickets cancel DFS-001` is invoked
    Then the ticket is cancelled and no further `/story run` is possible

  @AC-10
  Scenario: Retro is skipped when there is nothing to learn
    Given empty learnings and no defects
    When the pipeline reaches the retro phase
    Then it is skipped

  @AC-11
  Scenario: The learn command hands off to the learn skill
    Given a finished story with entries worth proposing
    When `/story learn DFS-001` is invoked
    Then the retro agent appends proposed entries to docs/learnings/log.md, and `/learn review` then lists them for the user to apply, park or reject

  @AC-12 @AC-13
  Scenario: The pipeline finishing names the next step
    Given the last phase's gate has passed and learnings.md is non-empty
    When the plan reaches state done
    Then the run says so and ends with the exact `/story learn DFS-001` command

  @AC-12
  Scenario: Finishing with nothing to learn says so plainly
    Given the last phase's gate has passed and learnings.md is empty with no defects
    When the plan reaches state done
    Then the run says so and states plainly that there is nothing to learn
```

## Scope

**Touches:**
- `.claude/skills/story/`: the skill (`run`, `status`, `approve`, `learn`) and the `plan.json` template
- `.claude/agents/`: a `## Summary` section convention added to `architect.md` and `verifier.md`
- `docs/learnings/`: the log file

**Out of scope:**
- Running several stories at once (a later layer over the same runner)
- The gate tools, agents and archetypes (CCW-003, CCW-004, CCW-002)
- Agent teams, an autonomous learning loop and parallel worktrees (parked in the design log)

## Definition of done

- [ ] Every scenario above is demonstrated on a scratch story, with the evidence recorded in this ticket
- [ ] The routine commands the pipeline needs are pre-approved in the project settings
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
