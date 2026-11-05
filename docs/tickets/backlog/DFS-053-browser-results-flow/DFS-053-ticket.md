---
id: DFS-053
title: Exercise the scan and results flow in a browser
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: frontend
depends_on: [DFS-050]
touches: []
blocked_reason:
created: 2026-11-02
updated: 2026-11-02
completed:
---

# DFS-053: Exercise the scan and results flow in a browser

## Story

As a user browsing scan results
I want the scan, folder tree, Sets and Overlaps flow checked in a real browser
So that integration failures are caught before a release

## Context

Filed as required by DFS-049 decision 8 after its ready confirmation. The first Playwright flow follows DFS-050; framework setup and browser downloads remain outside DFS-049. See docs/design/ui.md D6. This draft is deferred; it does not block duplicate groups or Copy folder path.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-053.AC-<n>`.

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

Select the Playwright setup, Gradle integration, portable fixture strategy (including hard links), server lifecycle, clipboard verification, and CI/browser policy during refinement.
