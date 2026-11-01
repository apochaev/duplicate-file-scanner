---
id: CCW-019
title: Add the endpoint archetype
status: draft
type: feature
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: harness
depends_on: [DFS-040]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# CCW-019: Add the endpoint archetype

## Story

As the developer running the story pipeline on server tickets
I want an archetype for a ticket that adds or changes HTTP endpoints in `server`
So that the architect, test-author, implementer and verifier follow the patterns DFS-036, DFS-038 and DFS-040 established by hand, instead of improvising for controllers, security, the spec chain and SSE

## Context

`docs/design/ui.md` D7: harness work goes under `CCW`, including archetypes for API endpoints and Angular components and the Node/npm steps in the pipeline gates. DFS-036 decision 6 and DFS-037 decision 5 put the endpoint archetype after the first data endpoint; DFS-040 (closed 2026-11-01) is that endpoint, built by hand like DFS-036 to DFS-038 because no archetype fit.

The material, all on `main`:
- **DFS-036:** security: the token link, the `DFS_SESSION` cookie, the `Host`/`Origin` filter, `/api/**` needing the cookie; tests against a real server on a random port (`TestServer`), forged `Host` over a raw socket.
- **DFS-038:** the spec chain: springdoc builds the spec from the controllers (only `/api/**`, not served at runtime), committed as `server/frontend/openapi.json`, checked by `OpenApiSpecTest`, rewritten by `:server:updateOpenApi`; `ng-openapi-gen` generates the Angular client; `@Tag`/`@Operation(operationId)` name the generated services.
- **DFS-040:** a data endpoint family: the API's own records (never core types in the spec), `ApiError` with codes and `ApiException` mapped by a `@RestControllerAdvice`, the core's `Result.Failure` codes passed through, `404 UNKNOWN_*` for unknown IDs, `201` with `Location`, a registry in `server` folding core events, an `SseEmitter` stream with throttling, test configurations overriding a scanner bean (`HoldingExecutorConfiguration`), and timing-sensitive tests.

The existing archetypes (`pipeline-stage`, `use-case`, `wiring`, under `.claude/archetypes/`) cover the scanner core and Spring wiring; each has `archetype.md` (where files go, an edge-case checklist, phases, gates, models), design and test-plan templates, implementation and test guides, and skeletons.

## Acceptance criteria

The full ID of each criterion is `CCW-019.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Endpoint archetype

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
- <to be refined>

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Scope: the endpoint archetype alone, or also the Angular component archetype (DFS-041 is next and is a screen)?
- How the archetype is proven: a read-through against DFS-040's code, or a live pipeline run on a real endpoint ticket (which one)?
- Do the pipeline's deterministic gates need changes for server tickets (the spec update, npm steps, the timing-sensitive tests)? Read the gate commands' real definitions.
- Stub convention for a controller: what the architect stubs (controller methods throwing, records unstubbed) and how the test-author's red run fails (500 from a stub vs 404 for a missing mapping).
- Archetype of this ticket itself: `none` (instructions, by hand) as for earlier archetype tickets?
