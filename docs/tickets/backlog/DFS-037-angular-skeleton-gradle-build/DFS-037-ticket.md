---
id: DFS-037
title: Add the Angular skeleton built and packaged by Gradle
status: draft
type: build
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-036]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-037: Add the Angular skeleton built and packaged by Gradle

## Story

As the user of the local DFS server
I want its page to be an Angular app with Angular Material, built and packaged by `./gradlew build`
So that the UI screens that follow have a frontend to grow in, with no Node install on my machine and one jar to run

## Context

`docs/design/ui.md` item 4 of the suggested ticket order, split in two (2026-11-01): this ticket builds the Angular app and packages it into `dfs-server.jar`; DFS-038 adds the OpenAPI spec and the generated API client (D2) once this one is in.

U9: the app lives in `server/frontend/`, is built by Gradle through a Node Gradle plugin that downloads Node (nothing installed globally), and is packaged into the server jar, so `./gradlew build` builds everything. Material and the CDK are the component set. During development the Angular dev server proxies `/api` to a running server for hot reload.

Today (DFS-035, DFS-036) the page is `server/src/main/resources/static/index.html`, served by `TokenExchangeController` for `GET /` without a token. It calls `GET /api/session` and shows "Connected" on `200` and "Open the link printed in the console" on `403` (DFS-036.AC-8). The Angular app replaces it and keeps that behavior. The security rules stay as they are: the page is public, `/api/**` needs the `DFS_SESSION` cookie, and the `Host`/`Origin` filter covers every request, including the app's static assets.

## Acceptance criteria

The full ID of each criterion is `DFS-037.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Angular skeleton built and packaged by Gradle

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
- The OpenAPI spec and the generated API client (DFS-038).
- Any screen beyond the connection status (the scan screen is `ui.md` item 5).
- D7's endpoint archetype (drafted after the first data endpoint).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Which Node Gradle plugin, and which Node and Angular versions (pinned)?
- How the build output reaches the jar (a copy into `static/`, or a resources source set), and how `GET /` serves it now that `index.html` is generated.
- Frontend unit tests: which runner, and run by `./gradlew build`?
- The dev proxy: `ng serve` with a proxy config to a `bootRun` server; how the token and cookie work through it.
- Archetype: `none` or a pipeline archetype?
- Does `./gradlew build` stay usable offline after the first run (Node and npm cache)?
