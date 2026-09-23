---
id: CCW-009
title: Scope DisabledCheck and FreezeCheck to the running story
status: draft
type: fix
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [tools/story-gate/DisabledCheck.java, tools/story-gate/FreezeCheck.java]
blocked_reason:
created: 2026-09-23
updated: 2026-09-23
completed:
---

# CCW-009: Scope DisabledCheck and FreezeCheck to the running story

## Story

As the orchestrator running the story pipeline
I want `DisabledCheck` and `FreezeCheck` to judge only the ticket currently running
So that a file another, already-closed ticket committed to the test tree cannot fail this ticket's gate

## Context

Spun off from `docs/learnings/log.md` L-027 rather than applied directly — a real code change to `tools/story-gate` with genuine edge cases (how a ticket's own test scope is determined, what "the ticket's own files" means for a check that currently just walks the whole test tree), not a one-line edit. Follows the precedent L-008 set for the same reason.

`DisabledCheck` scans the entire test source tree for `@Disabled`/unexplained-skip and `FreezeCheck` diffs the entire test tree against the `tests`-phase commit recorded in `plan.json` (`Story.testsCommit`) — neither is scoped to the ticket currently being run. Running DFS-002, both checks failed on `SizeIndexHeapBenchmark.java`, a file DFS-001 had already committed and closed, unrelated to anything DFS-002 touched (`docs/tickets/completed/DFS-001-group-files-by-size/plan.json`, defect 2; `docs/tickets/backlog/DFS-002-partial-hash-stage/plan.json`, defect 2). The orchestrator worked around it by hand each time — fixed the file directly (not routed to DFS-002's implementer, since it wasn't that phase's defect) and moved `plan.json`'s recorded `tests`-phase commit forward — but a story with no orchestrator able to make that judgment call would simply be blocked by an unrelated ticket's file.

A natural source of "the ticket's own scope" already exists: `plan.json`'s `tests`-phase `artifacts` list names exactly the test files that phase's test-author wrote. Whether that list, or the ticket's own `touches`, or something else, is the right basis for scoping is a design decision for refinement, not decided here.

## Acceptance criteria

The full ID of each criterion is `CCW-009.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: <name>

  Background:
    Given <shared precondition>

  @AC-1
  Scenario: <happy path>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `tools/story-gate/DisabledCheck.java`: scope the `@Disabled`/unexplained-skip scan to the running ticket's own test files
- `tools/story-gate/FreezeCheck.java`: scope the freeze diff to the running ticket's own test files

**Out of scope:**
- Any change to `RedCheck`/`TestPhaseGate` (a separate, already-parked question — see L-026, folded into L-025's `SKILL.md` fix instead of a code change)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] A regression test reproduces the DFS-001/DFS-002 false positive directly: an unrelated, already-committed test file with `@Disabled` (or changed since some other ticket's `tests`-phase commit) does not fail `DisabledCheck`/`FreezeCheck` for a ticket that never touched it

## Open questions

<!-- Must read "None." before status can be `ready`. -->
What "the running ticket's own test files" resolves to — `plan.json`'s `tests`-phase `artifacts` list, the ticket's `touches`, or something else — is undecided; settle it during refinement.
