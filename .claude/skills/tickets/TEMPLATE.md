---
id: DFS-000
title:
status: draft
type: feature
scope: []
priority: medium
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

## Scenarios

```gherkin
Feature: <name>

  Background:
    Given <shared precondition>

  Scenario: <happy path>
    Given <concrete state>
    When <action>
    Then <observable outcome>

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
