---
id: CCW-031
title: Run the story pipeline's routine phases on cheaper models
status: draft
type: chore
scope: [docs]
archetype:
priority: high
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-08
updated: 2026-11-08
completed:
---

# CCW-031: Run the story pipeline's routine phases on cheaper models

## Story

As the user paying for every `/story run`
I want the architect on templated archetypes, the retro and reflect agents, and the session driving `/story` to run on cheaper models
So that the largest usage contributor shrinks without weakening design on core tickets or review on risky ones

## Context

Filed 2026-11-08 by dfs-harness from Claude Code's usage report for the last 24 h: "Give story subagents a cheaper model" 59%, "Start fresh when you switch tasks" 32%, "Trim CLAUDE.md" 19% (shares overlap).

- **Measured** (every `plan.json` `runs` entry modified since 2026-10-01, grouped by phase and model):

  | Phase | Model | Runs | Turns | Not passed |
  |---|---|---|---|---|
  | design | opus | 26 | 831 | 1 |
  | verify | opus | 24 | 430 | 0 |
  | tests | sonnet | 27 | 522 | 4 |
  | implement | sonnet | 25 | 459 | 5 |
  | tests / implement | opus (escalations) | 6 | 70 | 0 |

  The two Opus phases take about 56% of all turns, and the architect alone takes 36%, at about 32 turns a run against `maxTurns` of 25 to 35.
- **What exists.** `.claude/agents/architect.md`: `opus`/`high`. Every archetype's `models:` sets `architect: {model: opus, effort: high, maxTurns: 25}` (`use-case`: 35). `retro.md` and `reflect.md`: `sonnet`/`low`. The `story` skill resolves model and effort from the ticket's `models:`, then the archetype's, then the agent's frontmatter, and moves a retried phase one tier up (`sonnet`→`opus`).
- **Why the architect can step down on some archetypes:** `component`, `endpoint` and `wiring` have skeletons and a settled "where files go"; `use-case` and `pipeline-stage` carry real domain design (ports, invariants), so they keep Opus.
- **The driving session.** CCW-029 measured main sessions at about three times all subagents together (86.8M against 26.7M input-equivalent tokens since 2026-10-25). The `/story run` loop is mechanical (read `plan.json`, launch an agent, run the gate, record a run), so the session running it could be launched on Sonnet (`claude --model sonnet -n <lane>`).
- **Related, not repeated here:** CCW-030 decides the verifier's model; CCW-029 narrows what the architect reads (it leaves the architect's model out of scope, which this ticket takes).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-031.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: Routine pipeline phases run on cheaper models

  @AC-1
  Scenario: <a component ticket's design phase launches the architect on sonnet/high>
    Given <concrete state>
    When <action>
    Then <observable outcome>

  @AC-1
  Scenario: <a use-case ticket's design phase still launches the architect on opus/high>
    Given <concrete state>
    When <action>
    Then <observable outcome>

  @AC-1
  Scenario: <a failed sonnet design attempt is retried on opus>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <likely the `models:` of `.claude/archetypes/{component,endpoint,wiring}/archetype.md`, `.claude/agents/retro.md`, `.claude/agents/reflect.md`, and the `story` skill or `docs/tickets/lanes.md` for the driving session's model>

**Out of scope:**
- The verifier's model (CCW-030).
- What the architect reads (CCW-029).
- The test-author and implementer, already on Sonnet.

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] The next design phase on a stepped-down archetype is compared with a recent Opus run of the same archetype (turns, outcome, defects found later)

## Open questions

1. Architect: `sonnet/high` for `component`, `endpoint` and `wiring` only, or also `pipeline-stage`? Does `maxTurns` need raising for Sonnet?
2. Retro and reflect: `haiku/low`, or keep Sonnet? They only propose and never write, but a weak retro proposal costs a human review.
3. Driving session: a recommendation in the `story` skill or `lanes.md` (launch lane sessions with `--model sonnet`), or nothing in the repo (a habit only)? Does a Sonnet session still follow the `story` skill's gates and notifications reliably?
4. How many runs before deciding the step-down holds: one per archetype, or the next three design phases?
