---
id: DFS-038
title: Generate the OpenAPI spec and the Angular API client
status: draft
type: build
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-037]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-038: Generate the OpenAPI spec and the Angular API client

## Story

As the developer of the DFS UI
I want the Angular API client and models generated from an OpenAPI spec generated from the Spring controllers
So that the frontend and server types cannot drift

## Context

`docs/design/ui.md` item 4, second half (split 2026-11-01; DFS-037 is the first). D2: JSON REST under `/api`, an OpenAPI spec generated from the Spring controllers, and the Angular client and models generated from it. The only endpoint so far is `GET /api/session` (`SessionController`, `{"authenticated":true}`); the DFS-037 page switches to the generated client for it.

## Acceptance criteria

The full ID of each criterion is `DFS-038.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Generated OpenAPI spec and API client

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined>

**Out of scope:**
- New endpoints.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Spec generation: springdoc at build time (a test or Gradle task writing the spec) or at runtime; is the spec committed?
- Client generator: openapi-generator (typescript-angular) or another; generated at build time or committed?
- Is the spec endpoint served at runtime, and if so behind the cookie?
- How a drift is caught: the build fails when the spec changes and the client is stale?
- Archetype: `none` or a pipeline archetype?
