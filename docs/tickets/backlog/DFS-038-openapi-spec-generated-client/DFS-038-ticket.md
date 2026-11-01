---
id: DFS-038
title: Generate the OpenAPI spec and the Angular API client
status: in-review
type: build
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-037]
touches:
  - server/build.gradle.kts
  - server/src/main/java/us/pochaev/dfs/server
  - server/src/main/resources/application.properties
  - server/src/test/java/us/pochaev/dfs/server
  - server/frontend
  - CLAUDE.md
  - docs/design/ui.md
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

## Decisions

1. **The spec is committed and checked by a test** (2026-11-01). springdoc-openapi 3.2.0 (the Spring Boot 4 line) builds the spec from the controllers. A Gradle task `:server:updateOpenApi` starts the app and writes `server/frontend/openapi.json`, which is committed; a JUnit test fails when the live spec differs from the committed file, naming that command. The frontend build generates the client from the committed file, so it needs no Java run. Not generated on every build: the server jar contains the frontend (DFS-037), so a frontend build that needed a running server would loop. Not API-first: D2 generates the spec from the controllers.
2. **Client generator: `ng-openapi-gen` 1.1.0, output not committed** (2026-11-01). Pinned exactly in `server/frontend/package.json` and run by an npm script before `ng build` and `ng test` (so through the Node Gradle downloads, and inside the existing `ngBuild` and `npmTest` tasks), it reads `server/frontend/openapi.json` and writes Angular `HttpClient` services and TypeScript model interfaces to `server/frontend/src/app/api/`, which is git-ignored. The committed spec is what gets reviewed; the code is rebuilt from it. Not openapi-generator's `typescript-angular`: much larger output and older Angular idioms.
3. **No spec at runtime** (2026-11-01). `springdoc.api-docs.enabled=false` in `application.properties`; only `:server:updateOpenApi` and the drift test start the app with it on (random port) and fetch the spec once. The `springdoc-openapi-starter-webmvc-api` starter, no Swagger UI. Under today's rules `/v3/api-docs` would be public, and nothing in the product needs it; the committed `openapi.json` is the readable spec.
4. **`archetype: none`, tests written first** (2026-11-01), as in DFS-036 and DFS-037. The Java and Vitest tests are written from the scenarios and run red before the implementation; `verification.md` records the red run and the commit it preceded. With DFS-037 this gives D7's archetypes the whole chain once, by hand: controller, spec, generated client, component.
5. **Implementation shape** (2026-11-01, settled while writing the criteria).
   - `SessionController` returns a record `SessionStatus(boolean authenticated)` instead of a `Map`, so the spec has a named schema; the JSON stays `{"authenticated":true}`. The controller carries `@Tag(name = "session")` and the method `@Operation(operationId = "getSession")`, so the generated service is `SessionService` with a `getSession` method.
   - An `OpenApiConfiguration` bean sets the spec's title (`DFS server API`), version and a fixed server URL, so the spec does not change with the random port.
   - One JUnit test, `OpenApiSpecTest`, starts the server with `--springdoc.api-docs.enabled=true`, fetches `/v3/api-docs` and compares it as JSON (not as text) with `server/frontend/openapi.json`, whose path the `test` task passes as a system property. `:server:updateOpenApi` is a `Test` task that runs only that test with a second system property telling it to write the file (pretty-printed) instead of comparing.
   - npm's `prebuild`, `pretest` and `prestart` scripts run `ng-openapi-gen`, so `ngBuild`, `npmTest` and `npm_run_start` all generate first. `server/build.gradle.kts` excludes `src/app/api/**` from the frontend task inputs, so the generated output does not make every build out of date.
   - The page uses `SessionService.getSession()` and shows "Connected" only when the answer says `authenticated: true`, so a renamed field breaks the frontend compile (AC-6).

## Acceptance criteria

The full ID of each criterion is `DFS-038.AC-<n>`.

- **AC-1** With springdoc switched on, the spec describes `GET /api/session` with tag `session`, operation ID `getSession` and a `200` response whose schema `SessionStatus` has a required boolean `authenticated`; its server URL does not contain the port; and the committed `server/frontend/openapi.json` is equal to it as JSON.
- **AC-2** When the committed spec differs from the live one, `./gradlew build` fails in `OpenApiSpecTest` with a message naming `./gradlew :server:updateOpenApi`; running that task rewrites the file, and the build is then green.
- **AC-3** A normal run (springdoc not switched on) serves no spec: `GET /v3/api-docs` answers `404`, with or without the session cookie, and `GET /api/session` still answers `{"authenticated":true}` with the cookie.
- **AC-4** The frontend build generates the client from the committed spec into `server/frontend/src/app/api/` before `ng build`, `ng test` and `ng serve`; that folder is git-ignored; and a second `./gradlew :server:processResources` with nothing changed still reports the npm tasks `UP-TO-DATE` (DFS-037.AC-5).
- **AC-5** The page reads `/api/session` only through the generated `SessionService`, with no hand-written `HttpClient` call: "Connected" for `{"authenticated":true}`, "Open the link printed in the console" for a `403`, and not "Connected" for `{"authenticated":false}`.
- **AC-6** Renaming the field in `SessionStatus` and running `:server:updateOpenApi` makes `./gradlew build` fail in the frontend compile at the page's use of `authenticated`; reverting both makes it green again.
- **AC-7** `CLAUDE.md` names the spec file, the drift test and `:server:updateOpenApi` (in the `server` entry and Commands), and `docs/design/ui.md` D2 names springdoc, `ng-openapi-gen` and the committed spec.

## Scenarios

AC-1, AC-2's failure message and AC-3 are JUnit tests in `server/src/test/java/us/pochaev/dfs/server` against a real server on a random port. AC-5 is Vitest tests in `server/frontend/` with `HttpTestingController`, plus a search showing no `HttpClient` call outside the generated folder. The rest are run once and recorded in `verification.md`: AC-2's update round trip, AC-4 (generated files, `git status`, the second run's task outcomes), AC-6 (the rename, then reverted), and AC-7 (read).

```gherkin
Feature: Generated OpenAPI spec and API client

  @AC-1
  Scenario: The committed spec matches the controllers
    Given the server runs with --springdoc.api-docs.enabled=true on a random port
    When the test requests GET /v3/api-docs
    Then the spec has path /api/session with a get operation, tag "session" and operationId "getSession"
    And its 200 response refers to schema SessionStatus with required boolean property "authenticated"
    And no server URL in the spec contains the port
    And the spec is JSON-equal to server/frontend/openapi.json

  @AC-2
  Scenario: A stale spec fails the build and names the fix
    Given server/frontend/openapi.json lacks the "authenticated" property
    When ./gradlew build runs
    Then OpenApiSpecTest fails with a message containing "./gradlew :server:updateOpenApi"
    When ./gradlew :server:updateOpenApi runs
    Then server/frontend/openapi.json has the property again
    And ./gradlew build is green

  @AC-3
  Scenario: No spec on a normal run
    Given the server runs with its default settings
    And cookie C from exchanging the printed token
    When a client requests GET /v3/api-docs without a cookie
    Then the response is 404
    When a client requests GET /v3/api-docs with C
    Then the response is 404
    When a client requests GET /api/session with C
    Then the response is 200 with body {"authenticated":true}

  @AC-4
  Scenario: The client is generated, not committed
    Given server/frontend/src/app/api does not exist
    When ./gradlew :server:bootJar runs
    Then server/frontend/src/app/api holds a SessionService with a getSession method and a SessionStatus model
    And git status lists nothing under server/frontend/src/app/api
    When ./gradlew :server:processResources runs again with nothing changed
    Then the npmInstall and ngBuild tasks are UP-TO-DATE

  @AC-5
  Scenario: Connected through the generated client
    Given the page with the generated SessionService
    When GET /api/session answers 200 with {"authenticated":true}
    Then the page shows "Connected"

  @AC-5
  Scenario: Not connected
    When GET /api/session answers 403
    Then the page shows "Open the link printed in the console"
    And it does not show "Connected"

  @AC-5
  Scenario: An answer that says not authenticated
    When GET /api/session answers 200 with {"authenticated":false}
    Then the page does not show "Connected"
    And it shows "Open the link printed in the console"

  @AC-5
  Scenario: No hand-written HTTP calls
    Then no file under server/frontend/src outside src/app/api injects HttpClient or calls "/api/" by URL

  @AC-6
  Scenario: A server-side rename breaks the frontend build
    Given SessionStatus's field is renamed from authenticated to signedIn
    When ./gradlew :server:updateOpenApi runs and then ./gradlew build
    Then the build fails in the frontend compile with an error naming "authenticated" in the page
    When both changes are reverted
    Then ./gradlew build is green

  @AC-7
  Scenario: Docs name the spec and its commands
    Then CLAUDE.md's server entry names server/frontend/openapi.json, the drift test and the update command
    And CLAUDE.md's Commands list ./gradlew :server:updateOpenApi
    And docs/design/ui.md D2 names springdoc, ng-openapi-gen and the committed spec
```

## Scope

**Touches:**
- `server/build.gradle.kts`: the springdoc starter, the spec path passed to `test`, the `updateOpenApi` task, `src/app/api/**` excluded from the frontend task inputs.
- `server/src/main/java/us/pochaev/dfs/server`: `SessionController` returns `SessionStatus` with `@Tag` and `@Operation`; the new `SessionStatus` record and `OpenApiConfiguration`.
- `server/src/main/resources/application.properties`: `springdoc.api-docs.enabled=false`.
- `server/src/test/java/us/pochaev/dfs/server`: `OpenApiSpecTest` (AC-1, AC-2, AC-3).
- `server/frontend`: `openapi.json` (generated, committed), `package.json` and `package-lock.json` (`ng-openapi-gen`, the pre-scripts), `.gitignore` (`src/app/api/`), the page and its Vitest tests.
- `CLAUDE.md`: the `server` entry and Commands.
- `docs/design/ui.md`: D2.

**Out of scope:**
- New endpoints.
- Swagger UI or any spec served at runtime (decision 3).
- SSE progress in the spec (D2's Server-Sent Events arrive with the scan screen).

## Definition of done

- [x] Every scenario above is an automated test and passes, except those run once and recorded in `verification.md` (see the note above the scenarios)
- [x] The tests ran red before the implementation; the failing run and the commit it preceded are recorded in `verification.md` (decision 4)
- [x] Each Java test's `@DisplayName` and each Vitest test's name starts with its criterion ID (`DFS-038.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests and the Vitest tests)
- [x] Started by hand once on Windows: the page shows "Connected", and `/v3/api-docs` answers `404`
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
