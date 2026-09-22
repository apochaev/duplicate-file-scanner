---
id: CCW-004
title: Define the five story agents
status: draft
type: feature
scope: [build]
priority: medium
depends_on: [CCW-002, CCW-003]
touches: [.claude/agents]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed:
---

# CCW-004: Define the five story agents

*(Formerly DFS-011; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the story orchestrator
I want the architect, implementer, test author, verifier and retro defined as subagents with tight tools, models and instructions
So that each phase runs with the least context and authority it needs

## Context

Five subagent files in `.claude/agents/`, launched by the `/story` skill (D1). Each prompt names exactly the files it reads, and each agent appends progress notes to `progress.md` and friction to `learnings.md`. The orchestrator, not the agents, writes `plan.json` (D5); agents report their outcome in their final message.

| Agent | Job | Model, effort, starting turn cap (D9) |
|---|---|---|
| `architect` | Domain design, ports, package placement, and compilable stubs that throw the not-implemented exception. Writes `design.md`. | opus, high, 25 |
| `test-author` | Writes tests from the acceptance criteria, their scenarios and the stubs, at least one per criterion, with `@DisplayName` starting with the criterion ID (for example `DFS-001.AC-3 Files of equal size form one candidate group`). Writes `test-plan.md`. Stops when the tests compile and fail for the right reason. | sonnet, medium, 30 |
| `implementer` | Makes the red tests pass. Never edits test files. | sonnet, medium, 40 |
| `verifier` | Runs after the deterministic gate passes. Reviews the diff against the scenarios, the `CLAUDE.md` invariants and `design.md`; judges test quality; records a verdict per acceptance criterion; ticks the done-items it verified; writes `verification.md`. No source edits. | opus, medium, 20 |
| `retro` | Runs only when `learnings.md` is non-empty or the story had defects. Reads the plan, `progress.md` and the learnings; proposes diffs; never applies them. | sonnet, low, 15 |

The turn caps are guesses to be replaced by pilot measurements. Escalation on retry (one tier up) is applied by the orchestrator through the per-invocation model, not in these files.

## Scenarios

```gherkin
Feature: Story agents

  Scenario: Every agent is defined with the agreed settings
    Given the directory `.claude/agents/`
    When the five agent files are read
    Then each has a name, a description, tools, a model, an effort and a turn cap matching the table

  Scenario: The architect produces a design and compilable stubs
    Given a ready ticket with an archetype
    When the architect runs
    Then `design.md` exists, the stubs compile, and each stub method throws `UnsupportedOperationException` with the message `not implemented: <TICKET-ID>`

  Scenario: The test author covers every scenario
    Given `design.md` and the stubs
    When the test author runs
    Then every acceptance criterion has at least one test whose display name starts with its ID, and `test-plan.md` maps criteria, scenarios and tests

  Scenario: The implementer leaves the tests alone
    Given red tests
    When the implementer runs
    Then the tests pass and no test file has changed

  Scenario: The verifier reviews only after the gate passes
    Given a passing deterministic gate
    When the verifier runs
    Then it writes `verification.md` with a verdict per acceptance criterion, ticks the done-items it verified, and changes no source file

  Scenario: The retro step is skipped when there is nothing to learn
    Given a story with an empty `learnings.md` and no defects
    When the orchestrator reaches the retro phase
    Then retro is not launched

  Scenario: Agents keep their reads minimal
    Given any agent prompt
    When it is read
    Then it names exactly the files that agent reads and nothing speculative
```

## Scope

**Touches:**
- `.claude/agents/`: the five agent files

**Out of scope:**
- The orchestrator skill that launches them (CCW-005)
- The gate tools (CCW-003) and the archetype content (CCW-002)

## Definition of done

- [ ] Every scenario above is demonstrated on a scratch story, with the evidence recorded in this ticket
- [ ] Each agent file's frontmatter uses only fields the current docs list
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Scenarios are a first draft derived from the design log only; review and complete them.
- Tool allowlists per agent, and whether path guards are needed (the verifier needs Edit for the ticket and Write for its report but must not touch source; a `PreToolUse` hook scoped to the verifier, or the touches check alone?).
- How does an agent learn the story folder path (passed in the launch prompt by the orchestrator)?
- CCW-007 (the `learn` skill) creates `.claude/agents/reflect.md` (Sonnet, low, 15 turns), which reads a ticket, its history and the learnings log. It is the seed of `retro`. Does `retro` reuse it, extend it with the plan and `progress.md` inputs, or replace it? `touches` overlaps at `.claude/agents`, so sequence the two tickets.
- Is `omitClaudeMd` worth using for `retro` to save tokens?
- Is there a way to evaluate agent prompts beyond the pilot run?
