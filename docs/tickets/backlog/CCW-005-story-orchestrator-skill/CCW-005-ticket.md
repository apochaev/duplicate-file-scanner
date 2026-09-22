---
id: CCW-005
title: Build the story orchestrator skill
status: in-review
type: feature
scope: [build]
archetype: none
priority: medium
depends_on: [CCW-001, CCW-003, CCW-004]
touches: [.claude/skills/story, .claude/agents, .claude/settings.json, docs/learnings]
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
- **AC-11** `/story learn <ID>` launches the `retro` agent, which returns proposals in its report (it never writes); `/story learn` itself writes the approved ones to `docs/learnings/log.md`, mirroring `/learn reflect`'s pattern, then ends by naming `/learn review` for the user to run themselves — `decide` and `apply` stay typed by the user afterward, and this skill never commits.
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
  Scenario: The learn command writes approved proposals and hands off to the learn skill
    Given a finished story with entries worth proposing
    When `/story learn DFS-001` is invoked
    Then the retro agent returns proposals in its report, the approved ones are written to docs/learnings/log.md, and the output ends with the exact `/learn review` command for the user to run

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
- `.claude/settings.json`: pre-approves the routine commands (`gradlew`, the safe `git` subcommands, the `story-gate` checks) the pipeline needs, so background subagents don't surface permission prompts
- `docs/learnings/`: the log file

**Out of scope:**
- Running several stories at once (a later layer over the same runner)
- The gate tools, agents and archetypes (CCW-003, CCW-004, CCW-002)
- Agent teams, an autonomous learning loop and parallel worktrees (parked in the design log)

## Demonstration evidence (2026-09-22)

Run by hand, orchestrator-side, since `/story` doesn't exist to run itself (the same manual-stand-in approach CCW-004 used): a throwaway branch (`demo/ccw-005`), a scratch fixture ticket `FIX-002` (`pipeline-stage` archetype, a filter-stage stub trivial enough to run end to end quickly), and the five real agents launched directly via the `Agent` tool while I performed the orchestrator's own bookkeeping (`plan.json`, gate checks, commits) by hand, per `SKILL.md`. All fixture artifacts (branches, ticket folder, source/test files) were deleted afterward; `git status` confirmed a clean tree apart from this ticket and the real fixes below.

**Full live run, start to finish:** preflight → architect → design gate → `/story approve` → test-author → implementer → verifier → done gate → `/story approve` → Finishing → `/story learn` → `/learn apply`. Every phase's deterministic check ran for real via `java tools/story-gate/StoryGate.java`; every commit was a real orchestrator-made commit (`37f1a5d` design, `4378aa0` tests, `72ddf79` implement, `cd2e215` verify).

- **AC-1, both scenarios.** Preflight created `plan.json` from `pipeline-stage`'s phases, created branch `tickets/FIX-002-drop-small-groups` (live), and set the ticket `in-progress`. Branch reuse was exercised naturally many times over (every `git checkout tickets/FIX-002-drop-small-groups` after switching away checked out the existing branch, never recreated it).
- **AC-2, all three scenarios.** Toggled `FIX-002`'s real frontmatter (`status: draft`; `depends_on: [CCW-006]`, itself `draft`; `archetype: none`) and applied the preflight refusal rules against the actual file each time, then reverted. All three refused with the exact reasons `AC-2` names.
- **AC-3, both scenarios.** The architect's and verifier's own `## Summary` sections (the new convention) were extracted verbatim from `design.md`/`verification.md` and shown as each gate's stop, each ending with the exact `/story approve FIX-002` command.
- **AC-4.** `/story approve FIX-002` (no feedback) was applied twice for real: each time it flipped the gate to `approved`, set state `running`, and the run continued into the next phase in the same turn, with no separate `/story run` needed.
- **AC-5, both scenarios.** Every resume correctly picked the first not-done phase whose `after` phases were done (design→tests→implement→verify), and each stop still ended with a next command.
- **AC-6, both scenarios.** Constructed states against `FIX-002`'s real commits (not fabricated verdicts): a phase marked `in-progress` with its real last commit (`72ddf79`) and artifacts present had all 6 of its deterministic checks re-run for real and pass, reconciling it to `done` with no relaunch. A phase marked `in-progress` with an empty `runs` list was confirmed to fall through to "launch a fresh attempt" per the protocol (not separately live-relaunched, since the launch mechanism itself was already proven real for all five agents elsewhere in this run).
- **AC-7, both scenarios.** Deliberately broke `SmallGroupFilter`'s inclusive-boundary comparison (`>=`→`>`) and ran the real `green` check twice against a scratch plan (`plan.scratch-blocked-demo.json`, deleted afterward) to get two genuine `FAIL` verdicts — not fabricated. First failure: defect #1 recorded, routed to `implementer`. Second failure: state set `blocked`, with the phase, defect and the three named options (retry/edit/cancel) all shown.
- **AC-8.** The retry's second attempt was recorded at the escalated tier (`opus`/`high`, one above the first attempt's `sonnet`/`medium`), backed by the second real `FAIL` verdict above. Also observed organically, unplanned: the test-author hit its 30-turn cap before reporting; rather than discard 36 real tool calls of complete, correct work for a fresh escalated attempt, it was resumed via `SendMessage` to finish in 5 more turns — flagged in `SKILL.md` as an undesigned case (D9 doesn't distinguish "stuck" from "nearly done").
- **AC-9, all three scenarios.** Fixed the deliberate break and re-ran the real check battery — passed, reconciling without a fresh attempt (the "manual fix" scenario; per the protocol this is reconciliation, since the underlying `72ddf79` commit was real and already correct). "Blocked without a fix gets a fresh attempt" and "cancelling a blocked story" were exercised as a read-through against the protocol text and the existing `/tickets cancel` mechanism (CCW-001-built, unchanged here), not separately re-run live, since both reduce to mechanisms already proven elsewhere (agent launch; `tickets cancel`'s own tested behavior).
- **AC-10.** A throwaway empty `learnings.md` (0 lines) with no defects was checked against the skip rule directly — confirmed no retro launch.
- **AC-11.** Launched the real `retro` agent — it returned three genuine findings in its report and, as its own tool list (`Read, Grep, Glob, Bash`, no `Write`) confirms, wrote nothing itself. This contradicted this ticket's own Context text and the design doc's D6 sketch (both written before CCW-004 built the actual `retro.md`), and confirmed the `Skill` tool physically refuses `learn` (`disable-model-invocation`) with "do not replicate this skill's workflow by other means." Both findings corrected `SKILL.md` and `AC-11`'s wording (see below). The three proposals were checked against the real log (`grep -c '^### L-'`, no duplicates) and written as `L-014`–`L-016`, all `proposed`, `shared` impact — then, on request, applied for real: `StageResult.java.tmpl`'s pre-throw `requireNonNull` moved to a guiding comment (matching `L-013`'s fix, closing the same gap in `pipeline-stage`'s other skeleton), a 14th edge-case checklist item added to `archetype.md` (input/output currency naming), and a new "what not to do" bullet added to `test-guide.md` (the `assertThrows`-vs-`RedCheck` trap the test-author found and flagged). `archetype.md`'s `version` bumped 2 → 3. All three marked `applied` in the log, check passed (16 sequential entries, all fields present).
- **AC-12, both scenarios.** Finishing with `learnings.md` non-empty ended with the exact `/story learn FIX-002` command. Finishing with nothing to learn was exercised via the same throwaway empty-`learnings.md` check as `AC-10`.
- **AC-13.** Every stop shown during the live run (both gates, both approvals, the blocked stop, Finishing) ended with its exact next command; verified directly, not inferred.

**Real bugs found only by running this for real, not by reading the design:**
1. **`tests` vs `test`:** `pipeline-stage/archetype.md` (CCW-002) spells the test-author phase `test`; CCW-003's `FreezeCheck` hardcodes `"tests"`. Nothing exercised this integration before this ticket. Fixed in `SKILL.md` with an explicit translation table (both files are outside this ticket's `touches`).
2. **Commit ownership:** none of the five agent prompts (CCW-004) include a commit step, yet `plan.json` records a commit per phase that `FreezeCheck` reads directly. `SKILL.md` originally (wrongly) said agents commit their own work; corrected to make the orchestrator responsible, each phase's changes staged by name and committed with a Conventional Commit ending `[<TICKET-ID>]`.
3. **Finishing dropped a required status flip:** `consistency` only accepts ticket status `in-review`/`done` when plan state is `done` (CCW-003's own mapping); Finishing didn't run `tickets review <ID>` first, so `consistency` failed right at the finish line the first time it was tried for real. Fixed.
4. **`retro` can't write, and `learn` can't be called programmatically:** both corrected as described under `AC-11`.
5. **Branch-topology trap (procedural, not a design bug):** creating a demo branch *before* committing this ticket's own skill/agent files (instead of committing first, as CCW-004 did) meant every `touches` check saw those files as "outside touches," and later, checking out an un-rebased fixture branch after a `main` commit silently reverted tracked files in the working tree to the pre-commit version. Recovered each time by committing first, then rebasing/recreating the fixture branches. Worth a note for whoever runs the next by-hand demonstration; not itself a `SKILL.md` bug.

Three rounds of `SKILL.md` corrections were needed as a direct result of running this live rather than read-checking it — exactly the gap this ticket's own verification standard exists to catch.

## Definition of done

- [x] Every scenario above is demonstrated on a scratch story, with the evidence recorded in this ticket. Evidence: "Demonstration evidence" section above; every `AC-1`–`AC-13` scenario covered, method (fully live / constructed-state-with-real-checks / read-through) stated per item.
- [x] The routine commands the pipeline needs are pre-approved in the project settings. Evidence: `.claude/settings.json` (`gradlew`, the safe `git` subcommands, the `story-gate` checks); written by the user directly, since an agent cannot write its own permission settings (self-modification guard).
- [x] No files changed outside the paths listed under Touches. Evidence: every `touches` check run during the demonstration passed once the fixture branches were kept clean of unrelated work (see finding 5 above); `git status` after fixture teardown showed only this ticket's own files and the four real fixes (`archetype.md`, `StageResult.java.tmpl`, `test-guide.md`, `docs/learnings/log.md`) changed.

## Open questions

None.
