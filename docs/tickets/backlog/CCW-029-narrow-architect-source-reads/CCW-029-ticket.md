---
id: CCW-029
title: Narrow what the architect reads to design a ticket
status: draft
type: chore
scope: [docs]
archetype:
priority: low
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# CCW-029: Narrow what the architect reads to design a ticket

## Story

As the user paying for every design phase
I want the architect to read only the source files its design needs, named before it starts
So that the most expensive phase (Opus, effort high) stops paying for exploratory reads that every later request carries again

## Context

Filed 2026-11-07 by dfs-harness after closing CCW-024, at the user's request to log tickets that further reduce cost.

- **Measured.** The architect is the costliest subagent: 34% of subagent cost since 2026-10-25 (18 runs, all Opus), ahead of test-author 27%, verifier 22%, implementer 15% (input-equivalent tokens: cache read 0.1, cache write 1.25, output 5; same model weights, so Opus's higher price makes the architect's share larger still). Every file it reads stays in context for each later request of the run.
- **After CCW-024 (DFS-054, endpoint):** 66 tool calls, 28 of them Reads, 2.49M cache-read tokens over 29 requests. Its Reads included 9 `file-scanner` files outside `touches` (`DeletionPlan`, `Operation`, `Conflict`, `Source`, `PlanRequest`, `KeepAndQuarantine`, `KeepCopy`, `LeftBehind`, and `PreviewDeletionPlanService`, read twice), found by two Greps for the core types, which its Summary names as read-only. CCW-024 cut output (71k to 40k) but not tool calls (79 and 63 before).
- `architect.md` "What to read" allows a source file only when the ticket's `touches` and the archetype's "where files go" name it; a type the design maps from (an API record built from a core record) falls outside both, so the architect searches for it.
- Ideas for refinement (none decided): refinement lists the existing types the design builds on in the ticket (a "Design inputs" line); the architect reads signatures only (`grep` the record header) rather than whole files; a per-archetype note of the usual inputs (for `endpoint`: the core use case and its records).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-029.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: The architect reads only named inputs

  @AC-1
  Scenario: <an endpoint ticket names its core inputs and the architect reads only those>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <likely `.claude/agents/architect.md`, `.claude/skills/tickets/SKILL.md` (refine) or `TEMPLATE.md`, and archetype files>

**Out of scope:**
- The model or effort of the architect (a separate decision per ticket via `models`).

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] The next Java design phase after the merge is measured (Reads, cache-read tokens) beside DFS-054's

## Open questions

1. Who names the inputs: refinement (in the ticket), the archetype, or the architect itself from `touches`?
2. Is reading signatures only safe when the design depends on a record's invariants (a compact constructor's checks)?
3. Worth doing before CCW-022 and CCW-026? Main sessions cost about three times all subagents together (86.8M against 26.7M input-equivalent tokens since 2026-10-25), so this is a smaller lever.
