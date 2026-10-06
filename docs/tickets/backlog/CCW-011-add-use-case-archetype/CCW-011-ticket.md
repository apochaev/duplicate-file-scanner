---
id: CCW-011
title: Add a use-case archetype
status: draft
type: docs
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: []
touches: [.claude/archetypes/use-case]
blocked_reason:
created: 2026-10-05
updated: 2026-10-05
completed:
---

# CCW-011: Add a use-case archetype

## Story

As the story pipeline's agents (architect, test-author, implementer, verifier)
I want an archetype written for use cases and the services that implement them
So that tickets like DFS-018 get a checklist, templates and guides that ask the right questions, instead of stretching `pipeline-stage`

## Context

DFS-005.L-8 (`docs/tickets/completed/DFS-005-run-scan-end-to-end/learnings.md`): `pipeline-stage` fit DFS-005's composing service poorly. None of its skeletons fit, checklist items 2 and 7 had nothing to say, and `design.md.tmpl`'s "this stage" wording read oddly. DFS-018 (track scans asynchronously) is the first ticket to carry `archetype: use-case` and depends on this one; more use cases follow (DFS-019, deletion preview and execution, undo/purge, location overlaps).

The phase agents read these files from an archetype directory (their real definitions in `.claude/agents/`): `archetype.md` (architect: edge-case checklist, "where files go", `models`), `design.md.tmpl` (architect), `test-plan.md.tmpl` and `test-guide.md` (test-author), and `impl-guide.md` (implementer). `pipeline-stage` also has a `skeletons/` folder; the plan from DFS-018's refinement is to ship no Java skeletons until a second use case shows what they should look like. `/story run`'s phase table is fixed across archetypes (`.claude/skills/story/SKILL.md`), so the five agents and their checks need no change.

Seed for the edge-case checklist, drawn from DFS-018's refinement decisions (`docs/tickets/backlog/DFS-018-track-scans-asynchronously/DFS-018-ticket.md`, decisions 1 to 12):

- Unknown or stale IDs: what each command and query returns for an ID it never issued (DFS-018: `Failure(UNKNOWN_SCAN)`).
- Results as values: which outcomes are `Result.Failure` values and which stay exceptions (null arguments) (decision 4).
- Status model: every state including the transient ones (queued, stopping), the legal transitions, which states are terminal, and what each command does in each state (decision 3).
- Races: which transitions can collide and which outcome wins, and where that is decided atomically (decisions 3, 8).
- Validation before side effects: what is checked on the caller's thread before anything is queued or recorded (decision 6).
- State placement: per-request state lives behind a port, the service holds only its ports and the executor (decision 8, CLAUDE.md's "no state in singleton fields").
- Caller-supplied callbacks: what happens when a listener or callback throws (decision 7).
- Failures off the caller's thread: where an asynchronous failure is recorded so it is not lost; `Error` handling (decision 7).
- Retention: how long state is kept, and whether removal is in scope (decision 9).
- Testability: a seam for any composed collaborator that is hard to drive (decision 12), a manual executor for queued states, latches instead of `Thread.sleep` (decision 10).

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-011.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: <name>

  @AC-1
  Scenario: <to be written during refinement>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `.claude/archetypes/use-case/`: `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `test-guide.md`, `impl-guide.md` (new)

**Out of scope:**
- Java skeletons for use cases (until a second use-case ticket shows their shape)
- Changes to `pipeline-stage` or to the phase agents
- Implementing DFS-018

## Definition of done

- [ ] Every scenario above is checked (mechanically where possible, otherwise by reading) and passes
- [ ] `/tickets validate` reports no archetype finding for DFS-018
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Archetype for this ticket itself (likely `none`: an instructions-only ticket implemented by hand, as CCW-002 was).
- Acceptance criteria and scenarios; for each, whether it is run mechanically or only checked by reading.
- Which `pipeline-stage` files to adapt and which to write fresh (`test-plan.md.tmpl` and `impl-guide.md` may carry over with small edits).
- The `models` defaults for the use-case phases (the same as `pipeline-stage`'s, or a larger test-author budget given DFS-005.L-1).
- Whether `/tickets validate` or any `story-gate` check reads anything archetype-specific beyond the directory's existence.
