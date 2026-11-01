---
id: DFS-037
title: Add the Angular skeleton built and packaged by Gradle
status: in-review
type: build
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-036]
touches:
  - server/build.gradle.kts
  - server/frontend
  - server/src/main/resources/static/index.html
  - server/src/test/java/us/pochaev/dfs/server
  - CLAUDE.md
  - docs/design/ui.md
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

## Decisions

1. **Toolchain** (2026-11-01). Gradle plugin `com.github.node-gradle.node` 7.1.0 downloads Node 24.22.2 (LTS "Krypton") and its bundled npm; nothing is installed globally. Angular 22.2 (accepts Node `^22.22.3 || ^24.15.0 || >=26.0.0`) with Angular Material and the CDK, versions pinned exactly in `server/frontend/package.json`, `package-lock.json` committed, packages installed with `npm ci`. The first build needs the network; later builds use the cached Node and `node_modules`. Node 26 (LTS since October 2026) is a later version bump, not this ticket.
2. **Packaging** (2026-11-01). A Gradle task copies the `ng build` output (`server/frontend/dist/frontend/browser/`) into `server/build/generated/frontend/static/`, which is added as a resources source directory; `processResources` depends on it, so `bootRun`, `bootJar`, `installBootDist` and the tests all carry the app. The hand-written `server/src/main/resources/static/index.html` is deleted. `TokenExchangeController` is unchanged: it still serves `static/index.html`, now Angular's; the hashed JS and CSS files are served from `static/` like any public resource. Nothing generated lands in the source tree; `server/frontend/dist`, `node_modules` and the downloaded Node are git-ignored.
3. **Tests** (2026-11-01). Frontend: Vitest (the Angular CLI default since v21) on jsdom, run by a Gradle `npm test` task wired into `:server:check`, so `./gradlew build` runs it; a component test covers both states of the connection status with `HttpTestingController` (`200` → "Connected", `403` → "Open the link printed in the console"). Java: the two assertions that `GET /` contains "DFS server is running" (`SecurityTest.java:85`, `ServerApplicationTest.java:82`) change to the Angular shell (`<app-root>` and the page title), since the text is now rendered in the browser; a new test fetches the JS file `index.html` references (`200`) and the same file with a foreign `Host` (`403`). The browser check stays by hand (like DFS-036.AC-8). No Playwright end-to-end test yet: a browser download and a second stack, better added with real screens.
4. **Dev proxy** (2026-11-01). `./gradlew :server:bootRun --args=--server.port=8080` starts the server on a fixed port; `./gradlew :server:npm_run_start` runs `ng serve --host 127.0.0.1` (port 4200) through the downloaded Node; `server/frontend/proxy.conf.json` sends `/api` to `http://127.0.0.1:8080` with `changeOrigin: true`, so the `Host` filter sees `127.0.0.1:8080`. To log in, the user opens the server's token link once, then `http://127.0.0.1:4200`: cookies are not scoped by port and `SameSite` ignores ports, so the `DFS_SESSION` cookie reaches the dev server and the proxy forwards it. No `Origin` rewrite yet (no state-changing endpoint); the first ticket with a `POST` decides it. Checked by hand, documented in `CLAUDE.md` Commands.
5. **`archetype: none`, tests written first** (2026-11-01), as in DFS-036. No archetype knows Angular, npm or Node in Gradle. The Java and Vitest tests are written from the scenarios and run red before the implementation; `verification.md` records the red run and the commit it preceded. This ticket and DFS-038 are the material for D7's frontend archetype, drafted after the first data endpoint.
6. **Where things live** (2026-11-01, settled while writing the criteria). The Angular workspace is `server/frontend/` (project name `frontend`, standalone components, no SSR, no routing yet); the node-gradle plugin's `nodeProjectDir` points there, and the downloaded Node sits under `server/.gradle/` (already ignored by the root `.gitignore`). `server/frontend/.gitignore` ignores `node_modules/`, `dist/` and `.angular/`. The page keeps the heading "DFS server is running" in a Material toolbar and shows the connection status below it; the document title is `DFS`.

## Acceptance criteria

The full ID of each criterion is `DFS-037.AC-<n>`.

- **AC-1** `./gradlew :server:bootJar` on a machine with no Node on `PATH` downloads Node 24.22.2 through the node-gradle plugin, runs `npm ci` and `ng build` in `server/frontend/`, and the jar holds `BOOT-INF/classes/static/index.html` with `<app-root>` plus every JS and CSS file that `index.html` references; `git status` shows no generated file after the build.
- **AC-2** `GET /` without a token answers `200` with the Angular `index.html` (contains `<app-root>`, title `DFS`); each JS and CSS file it references answers `200` with a `text/javascript` or `text/css` content type without a cookie; the same file requested with `Host: evil.example:<port>` answers `403`.
- **AC-3** The page shows the heading "DFS server is running" and, below it, "Connected" when `GET /api/session` answers `200` and "Open the link printed in the console" when it answers `403`; covered by a Vitest component test with `HttpTestingController` and checked once by hand in a browser.
- **AC-4** `./gradlew build` runs the Vitest tests as part of `:server:check`, and a failing frontend test fails the build.
- **AC-5** A second `./gradlew :server:processResources` with no change under `server/frontend/` runs neither `npm ci` nor `ng build` (both tasks `UP-TO-DATE`).
- **AC-6** With `./gradlew :server:bootRun --args=--server.port=8080` and `./gradlew :server:npm_run_start` running, opening the server's token link and then `http://127.0.0.1:4200` shows "Connected", and an edit to the page's text appears there without a restart.
- **AC-7** The DFS-035 and DFS-036 tests that asserted "DFS server is running" in the served HTML assert the Angular shell instead (`<app-root>`, title `DFS`) and pass; `CLAUDE.md` describes the Angular app in the `server` entry and lists the dev-proxy commands; `docs/design/ui.md` U9 names the toolchain of decision 1.

## Scenarios

AC-1's jar contents, AC-2 and AC-7's Java assertions are JUnit tests in `server/src/test/java/us/pochaev/dfs/server` (the jar is read from the `dfs.bootJar` path, like `NativeAccessTest`; the server runs on a random port; the foreign `Host` goes over a plain socket, like DFS-036's tests). AC-3's component behavior is a Vitest test in `server/frontend/`. The rest are run once and recorded in `verification.md`: AC-1's "no Node on `PATH`" and `git status`, AC-3's browser check, AC-4 (a deliberately broken frontend test, then reverted), AC-5 (the second run's task outcomes), AC-6 (by hand), and AC-7's docs (read).

```gherkin
Feature: Angular skeleton built and packaged by Gradle

  @AC-1
  Scenario: The jar carries the built app
    Given no node executable on PATH
    When ./gradlew :server:bootJar runs
    Then Node 24.22.2 is downloaded under server/.gradle
    And dfs-server.jar holds BOOT-INF/classes/static/index.html containing "<app-root>"
    And every file named in a src= or href= attribute of that index.html is in the jar under BOOT-INF/classes/static/
    And git status lists no file under server/frontend/dist, server/frontend/node_modules or server/src

  @AC-2
  Scenario: The page and its assets are public
    Given the server runs on 127.0.0.1 port P with dfs.server.open-browser=false
    When a client without a cookie requests GET /
    Then the response is 200, contains "<app-root>" and the title "DFS"
    When it requests each JS and CSS file that page references
    Then each answers 200 with content type text/javascript or text/css

  @AC-2
  Scenario: Assets are behind the Host check
    Given the server runs on port P and main-<hash>.js is a file the page references
    When a client requests /main-<hash>.js with Host "evil.example:P"
    Then the response is 403

  @AC-3
  Scenario: Connected
    Given the connection status component
    When GET /api/session answers 200 with {"authenticated":true}
    Then the page shows "DFS server is running" and "Connected"

  @AC-3
  Scenario: Not connected
    Given the connection status component
    When GET /api/session answers 403
    Then the page shows "Open the link printed in the console"
    And it does not show "Connected"

  @AC-3
  Scenario: In a browser
    When the user opens the printed link
    Then the page shows "Connected" with the Material toolbar
    When the user opens http://127.0.0.1:P/ in a private window
    Then the page shows "Open the link printed in the console"

  @AC-4
  Scenario: Frontend tests are part of the build
    Given a frontend test changed to expect "Disconnected" where the page shows "Connected"
    When ./gradlew build runs
    Then the build fails naming the frontend test task
    When the change is reverted
    Then ./gradlew build is green

  @AC-5
  Scenario: An unchanged frontend is not rebuilt
    Given ./gradlew :server:processResources has run once
    And nothing under server/frontend changed since
    When ./gradlew :server:processResources runs again
    Then the npm ci and ng build tasks are UP-TO-DATE

  @AC-6
  Scenario: Hot reload through the dev proxy
    Given ./gradlew :server:bootRun --args=--server.port=8080 is running
    And ./gradlew :server:npm_run_start is running
    When the user opens the server's token link, then http://127.0.0.1:4200
    Then the page shows "Connected"
    When the heading text in the component's template is edited and saved
    Then the page at port 4200 shows the new text without a restart

  @AC-7
  Scenario: Earlier tests and docs follow the Angular page
    Then SecurityTest and ServerApplicationTest assert "<app-root>" and the title "DFS" for GET / and pass
    And CLAUDE.md's server entry describes the Angular app and its Commands list the dev-proxy commands
    And docs/design/ui.md U9 names node-gradle 7.1.0, Node 24.22.2 and Angular 22.2
```

## Scope

**Touches:**
- `server/build.gradle.kts`: the node-gradle plugin (Node 24.22.2 download, `nodeProjectDir`), the `npm ci`, `ng build` and `npm test` tasks with inputs and outputs, the copy into `build/generated/frontend/static/` as a resources directory, `processResources` and `check` dependencies.
- `server/frontend`: the new Angular workspace (`package.json`, `package-lock.json`, `angular.json`, `tsconfig*.json`, `proxy.conf.json`, `.gitignore`, `src/` with the app and connection-status component and its Vitest test).
- `server/src/main/resources/static/index.html`: deleted.
- `server/src/test/java/us/pochaev/dfs/server`: `SecurityTest` and `ServerApplicationTest` assertions changed (decision 3); a new test for the jar contents, the assets and their `Host` check.
- `CLAUDE.md`: the `server` entry and Commands.
- `docs/design/ui.md`: U9.

**Out of scope:**
- The OpenAPI spec and the generated API client (DFS-038).
- Any screen beyond the connection status (the scan screen is `ui.md` item 5).
- D7's endpoint archetype (drafted after the first data endpoint).

## Definition of done

- [x] Every scenario above is an automated test and passes, except those run once and recorded in `verification.md` (see the note above the scenarios)
- [x] The tests ran red before the implementation; the failing run and the commit it preceded are recorded in `verification.md` (decision 5)
- [x] Each Java test's `@DisplayName` and each Vitest test's name starts with its criterion ID (`DFS-037.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests and the Vitest tests)
- [ ] Started by hand once on Windows from `installBootDist`: the browser opens on the link and the Angular page shows "Connected"
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
