---
id: CCW-019
title: Add the endpoint archetype
status: ready
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: harness
depends_on: [DFS-040]
touches:
  - .claude/archetypes/endpoint
  - server/src/test/java/us/pochaev/dfs/server
  - docs/tickets/backlog/CCW-019-endpoint-archetype/dry-run.md
  - docs/design/ui.md
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

## Decisions

1. **The endpoint archetype only; the Angular component archetype comes after DFS-041** (2026-11-01). The endpoint material is rich (DFS-036, DFS-038, DFS-040); the only Angular code so far is a toolbar and a status line (DFS-037, DFS-038), too thin for a component archetype. DFS-041 (the scan screen) is built by hand and becomes that archetype's material, in a later `CCW` ticket.
2. **Proven by a recorded dry run against DFS-040; first live use on the next endpoint ticket** (2026-11-01), as CCW-013 proved `wiring`. `dry-run.md` walks every checklist item, template and guide against DFS-040's ticket and code, and names which item would have raised each DFS-040 decision and each surprise met while building DFS-038 and DFS-040 (the `//api` root URL, the token page in the spec, the generator's `--services` option, the regeneration gap, the Promise timing in the Vitest helper, the timing-sensitive tests). An item that would have caught nothing is cut; a surprise no item catches gets one. The next endpoint ticket then runs through the pipeline, and its retro feeds back.
3. **Endpoint story tests surface the stub exception; the red gate is unchanged** (2026-11-01). `tools/story-gate/RedCheck.java` requires every story test to fail with `UnsupportedOperationException("not implemented: <ID>")` in its JUnit report, but a stubbed controller method called over HTTP shows the client only a `500`. The archetype ships a test-support skeleton: a test-only handler records the server-side exception behind a `500`, and the request helpers rethrow it, so the story test fails with the stub's own exception while still going through the real server, its security and its status codes. The architect regenerates `server/frontend/openapi.json` from the stubbed controller in the design phase (its annotations already define the spec), so `OpenApiSpecTest` passes from the start; spec assertions go in `OpenApiSpecTest`, not in story tests (a story test reading the committed spec would pass against stubs). Not a `RedCheck` change (it would rely on error bodies leaking exception text); not controller-level story tests (security and status codes are what endpoint criteria are about). The other gates need no change: `GreenCheck` runs `cleanTest build --continue`, which already includes the frontend build, `:server:npmTest` and `OpenApiSpecTest`; `Junit.java` reads only `build/test-results/test`, which is fine for endpoint tickets (their story tests are JUnit).
4. **`archetype: none`, written by hand** (2026-11-01), like every earlier archetype ticket (CCW-013). The archetype files are instructions, checked by reading; two parts are run mechanically: the stub-surfacing helper (decision 5) and the dry run's trace (decision 2).
5. **The stub-surfacing helper lives once in `server`'s test tree** (2026-11-01, settled while writing the criteria; refines decision 3). Every endpoint ticket needs the same code, so it is not a skeleton to copy: a test-only `StubSurfacingConfiguration` (a handler that records the exception behind any `500`) that `TestServer` always loads, and `TestServer`'s request helpers rethrow the recorded exception when a response is `500`. Any server test that hits a `500` then fails with the server's own exception instead of a status mismatch, a better failure for every test, not only red runs. The archetype's test guide names it; its skeletons hold only what each ticket copies (a controller with stubbed methods and its API records).

## Acceptance criteria

The full ID of each criterion is `CCW-019.AC-<n>`.

- **AC-1** `.claude/archetypes/endpoint/` holds `archetype.md` (frontmatter `name: endpoint`, `version: 1`, `phases: [design, test, implement, verify]`, `gates: [design, done]`, and `models` like the other archetypes), `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md` and `skeletons/` with at least a stubbed controller and an API record.
- **AC-2** `archetype.md` says which tickets it applies to (adding or changing HTTP endpoints under `/api` in `server`) and which not (core use cases: `use-case`; Spring wiring: `wiring`; Angular screens), and its "Where files go" covers: a package per feature under `us.pochaev.dfs.server`, the API's own records (never core types in the spec), `ApiError` and `ApiException`, `@Tag` and `@Operation(operationId)`, stubbed controller methods throwing `not implemented: <ID>` with records left unstubbed, and the architect regenerating `server/frontend/openapi.json` with `:server:updateOpenApi` in the design phase.
- **AC-3** `archetype.md`'s edge-case checklist covers at least: security (cookie, `Host`, `Origin` on every state-changing method, no CORS, the path-inside-roots rule for any endpoint that reads a path inside a scan), status codes and error codes (including unknown IDs and the core's `Result.Failure` codes), the spec and the generated client (operation IDs, schema names, server URL, what the generator produces), streams (throttling, reconnect, the terminal event, never blocking a scanning thread), state kept in `server`, timing-sensitive tests and their margins, and the dev proxy; every item names the ticket it comes from.
- **AC-4** With the stub-surfacing helper, a server test that calls a controller method throwing `UnsupportedOperationException("not implemented: CCW-019")` over HTTP fails with that exception, type and message, in its JUnit XML report, so `RedCheck` counts it as a stub failure; every existing server test still passes.
- **AC-5** `design.md.tmpl` has sections for the endpoint table (method, path, operation ID, success status, error codes), the security matrix, the API records, the spec regeneration step and `## Expected breakage`; `test-plan.md.tmpl` maps each criterion to tests and names which are timing-sensitive; `test-guide.md` names `TestServer`, the stub-surfacing helper, the raw-socket `Host` check and bean-override test configurations such as `HoldingExecutorConfiguration`.
- **AC-6** `dry-run.md` traces each of DFS-040's seven decisions and each surprise met building DFS-038 and DFS-040 (the `//api` root URL, the token page in the spec, the generator's `--services` option, the generated-client regeneration gap, the Promise timing in the Vitest helper, the timing-sensitive tests) to the checklist item or guide section that would have raised it, with none left untraced; and lists every checklist item with at least one event it traces to, none unused.
- **AC-7** `docs/design/ui.md` D7 says the endpoint archetype exists (CCW-019) and that the Angular component archetype follows DFS-041.

## Scenarios

How each scenario is checked: AC-1 mechanically (a listing and the frontmatter read by a script); AC-4 mechanically (a throwaway stub controller and test run once in `server`, its JUnit XML inspected, then removed; plus the full `./gradlew build`); AC-6 partly mechanically (a script counts the dry run's rows and untraced or unused entries) and partly by reading; AC-2, AC-3, AC-5 and AC-7 by reading. `verification.md` records which was which. This is not a walkthrough of a pipeline run: the first live use is the next endpoint ticket (decision 2).

```gherkin
Feature: Endpoint archetype

  @AC-1
  Scenario: The archetype's files exist
    When the files under .claude/archetypes/endpoint are listed
    Then they include archetype.md, design.md.tmpl, test-plan.md.tmpl, impl-guide.md, test-guide.md and skeletons/
    And archetype.md's frontmatter has name endpoint, version 1, phases [design, test, implement, verify] and gates [design, done]

  @AC-2
  Scenario: The archetype says where it applies and where files go
    When archetype.md is read
    Then it applies to endpoints under /api in server and refers use cases to use-case, wiring to wiring and screens elsewhere
    And "Where files go" names the feature package, the API records, ApiError and ApiException, @Tag and @Operation, the stubs and the spec regeneration in the design phase

  @AC-3
  Scenario: The checklist asks what DFS-036, DFS-038 and DFS-040 met
    When archetype.md's edge-case checklist is read
    Then it has items on security, status and error codes, the spec and the generated client, streams, state in server, timing-sensitive tests and the dev proxy
    And every item names the ticket it comes from

  @AC-4
  Scenario: A stubbed endpoint fails its test with the stub exception
    Given a throwaway controller method that throws UnsupportedOperationException("not implemented: CCW-019")
    And a throwaway server test that calls it over HTTP through TestServer
    When the server tests run
    Then that test's JUnit XML failure has type java.lang.UnsupportedOperationException and a message starting "not implemented: CCW-019"
    And every other server test passes
    When the throwaway files are removed
    Then ./gradlew build is green

  @AC-5
  Scenario: The templates and guides carry the endpoint specifics
    When design.md.tmpl, test-plan.md.tmpl and test-guide.md are read
    Then the design template has the endpoint table, the security matrix, the API records, the spec step and Expected breakage
    And the test plan maps criteria to tests and marks timing-sensitive ones
    And the test guide names TestServer, the stub-surfacing helper, the raw-socket Host check and bean-override test configurations

  @AC-6
  Scenario: The dry run traces every decision and surprise
    When dry-run.md is checked by a script
    Then every DFS-040 decision (1 to 7) and every listed surprise has a row naming a checklist item or guide section
    And every checklist item appears in at least one row

  @AC-7
  Scenario: The design doc records the archetype
    When docs/design/ui.md D7 is read
    Then it names the endpoint archetype (CCW-019) and says the component archetype follows DFS-041
```

## Scope

**Touches:**
- `.claude/archetypes/endpoint`: the new archetype (`archetype.md`, templates, guides, skeletons).
- `server/src/test/java/us/pochaev/dfs/server`: the stub-surfacing configuration and `TestServer`'s rethrow (decision 5).
- `docs/tickets/backlog/CCW-019-endpoint-archetype/dry-run.md`: the dry run (decision 2).
- `docs/design/ui.md`: D7.

**Out of scope:**
- The Angular component archetype (decision 1; after DFS-041).
- Changes to `tools/story-gate` (decision 3).
- A live pipeline run (decision 2; the next endpoint ticket).

## Definition of done

- [ ] Each scenario checked as the note above the scenarios says, recorded in `verification.md` as mechanical or by reading
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests), with the throwaway AC-4 files removed
- [ ] No files changed outside the paths listed under Touches
- [ ] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
