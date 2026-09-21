---
id: DFS-012
title: Build the story orchestrator skill
status: draft
type: feature
scope: [build]
priority: medium
depends_on: [DFS-008, DFS-010, DFS-011]
touches: [.claude/skills/story, docs/learnings]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-012: Build the story orchestrator skill

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
- **Learning (D6):** `/story learn <ID>` shows ranked proposals; the user applies, parks or rejects each; decisions go in `docs/learnings/log.md`; applied changes bump the archetype version. Every proposal cites evidence.
- **Pilot data (D8):** the run records in `plan.json` are the raw material for the metrics.

## Scenarios

```gherkin
Feature: Story orchestrator

  Scenario: Starting a story
    Given a ready ticket whose dependencies are done and which has an archetype
    When `/story run DFS-001` is invoked
    Then `plan.json` is created from the archetype's phases and the ticket is set to in-progress

  Scenario: A run stops at a human gate
    Given the design phase has passed its gate and a design gate is configured
    When the run reaches the gate
    Then the state is awaiting-approval, a summary of about twenty lines is shown, and the run returns

  Scenario: Resuming after approval
    Given an approved gate
    When `/story run DFS-001` is invoked again
    Then it resumes at the first phase that is not done and whose `after` phases are done

  Scenario: Reconciling an interrupted phase
    Given a phase marked in-progress whose artifacts and commit exist and whose gate passes
    When the run resumes
    Then the phase is marked done without being re-run

  Scenario: An interrupted phase without evidence is re-run
    Given a phase marked in-progress with no artifacts
    When the run resumes
    Then the agent is re-run with the notes from `progress.md`

  Scenario: A failed red gate routes back to the test author
    Given the red gate fails
    When the failure is recorded
    Then a defect routes to the test author, and after two failed attempts the state is blocked and the user is told

  Scenario: A retry runs one tier up
    Given a phase whose first attempt failed its gate
    When it is retried
    Then the model is one tier above the resolved one, and the runs list records model, effort, turns and outcome

  Scenario: Retro is skipped when there is nothing to learn
    Given empty learnings and no defects
    When the pipeline reaches the retro phase
    Then it is skipped

  Scenario: The learn command applies, parks or rejects
    Given ranked proposals with evidence
    When the user chooses apply, park or reject for each
    Then the choices are logged in `docs/learnings/log.md`, and an applied change bumps the archetype version

  Scenario: An unready story is refused
    Given a ticket that is not ready, or whose dependencies are not done
    When `/story run` is invoked
    Then it refuses and says why
```

## Scope

**Touches:**
- `.claude/skills/story/`: the skill (`run`, `status`, `approve`, `learn`) and the `plan.json` template
- `docs/learnings/`: the log file

**Out of scope:**
- Running several stories at once (a later layer over the same runner)
- The gate tools, agents and archetypes (DFS-010, DFS-011, DFS-009)
- Agent teams, an autonomous learning loop and parallel worktrees (parked in the design log)

## Definition of done

- [ ] Every scenario above is demonstrated on a scratch story, with the evidence recorded in this ticket
- [ ] The routine commands the pipeline needs are pre-approved in the project settings
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Scenarios are a first draft derived from the design log only; review and complete them.
- Branch or worktree naming and creation for a story (for example `story/DFS-001`).
- How are token counts obtained for the run records, or only turns and wall-clock time?
- How does the user approve a gate: a `/story approve` command, or conversationally?
- How is the twenty-line gate summary produced from `design.md` and `verification.md`?
- How are `blocked` states handled by hand?
- Applied learning changes are committed only when the user asks (`CLAUDE.md`); how does `learn` fit that rule?
