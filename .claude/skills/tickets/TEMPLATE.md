---
id: DFS-000
title:
status: draft
type: feature
scope: []
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: medium
lane:                  # backend | frontend | harness (every CCW ticket); see docs/tickets/lanes.md
depends_on: []
touches: []
blocked_reason:
created: YYYY-MM-DD
updated: YYYY-MM-DD
completed:
---

# DFS-000: <title>

## Story

As a <role>
I want <capability>
So that <benefit>

## Context

<!-- Why this exists. Reference CLAUDE.md design decisions by number, name existing types and ports. An agent must not need anything beyond this file and CLAUDE.md. -->

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-000.AC-<n>`.

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

  @AC-1
  Scenario: <edge case>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `<package or path>`: <what changes there>

**Out of scope:**
- <what this ticket deliberately does not do>

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

<!-- Must read "None." before status can be `ready`. -->
