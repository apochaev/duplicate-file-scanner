---
id: DFS-035
title: Add the server subproject skeleton
status: in-review
type: build
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: []
touches:
  - settings.gradle.kts
  - server/build.gradle.kts
  - server/src/main/java/us/pochaev/dfs/server
  - server/src/main/resources/application.properties
  - server/src/main/resources/static/index.html
  - server/src/test/java/us/pochaev/dfs/server
  - CLAUDE.md
  - README.md
  - docs/design/ui.md
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# DFS-035: Add the server subproject skeleton

## Story

As the user of the duplicate-file-scanner
I want a local server I can start with one command, built by `./gradlew build` next to `cli` and `file-scanner`
So that the web UI (`docs/design/ui.md`) has a home that keeps the core's layering rules from day one

## Context

- Second item of `docs/design/ui.md`, "Suggested ticket order": the `server` subproject skeleton, its ArchUnit guard and launch scripts (U11, D5). `CLAUDE.md` names `server` as the planned third subproject, separate from `cli` and `file-scanner`.
- **Shape to follow:** `cli/build.gradle.kts` (Spring Boot 4.1.1 plugin, the Boot BOM, `implementation(project(":file-scanner"))`, `--enable-native-access=ALL-UNNAMED` for `bootRun` and the `Enable-Native-Access` manifest attribute on `bootJar`, `NativeAccessTest` reading the jar's manifest). Shared Java config stays in the root `subprojects {}` block.
- **U11 launching:** development with `./gradlew :server:bootRun`; first version `./gradlew :server:bootJar` producing `dfs-server.jar` with `Enable-Native-Access: ALL-UNNAMED`, plus `dfs-server.cmd` and `dfs-server.sh`. Requires Java 25.
- **D5 guard rail:** `server` gets an `ArchitectureTest` with the same rule as `cli` (nothing under `us.pochaev.dfs.scanner` outside `domain`); the `CLAUDE.md` caveat applies (JPMS is not enforced on the classpath, so the ArchUnit rule is the real guard).
- **U10 ordering:** security (token, cookie, `Host`/`Origin` checks) is the first server ticket *with an endpoint that reads the disk*. This skeleton has no such endpoint; whether it has any endpoint at all is an open question.
- **Scanner wiring:** DFS-031 (backend lane, draft) extracts the shared `scanner-spring` module (D1). Whether this skeleton waits for it is an open question.
- Frontend parts (Angular, the Node Gradle plugin, U9) are ticket 4 in `ui.md`, not this one.

## Decisions

1. **Depend on `file-scanner` directly, no scanner beans** (2026-10-31). `server/build.gradle.kts` has `implementation(project(":file-scanner"))` like `cli`, so the ArchUnit guard (D5) has real scanner classes on the classpath to check. No `@Configuration` for the scanner here; the scan-screen ticket (`ui.md` ticket 5) switches to `scanner-spring` and names DFS-031 in its `depends_on`. This ticket does not wait for DFS-031.
2. **`archetype: none`, implemented by hand** (2026-10-31). Build plumbing with little logic; no existing archetype fits (`wiring` assumes beans and factories). Built and tested in a worktree, ending with `verification.md` and the product-owner review (CCW-017). No `autorun`.
3. **A loopback web server with a static placeholder page** (2026-10-31). Binds to `127.0.0.1` on a random port (`server.address=127.0.0.1`, `server.port=0`) and logs `DFS server listening on http://127.0.0.1:<port>/`. `GET /` returns a static `index.html` ("DFS server is running"), replaced by the Angular build in `ui.md` ticket 4. No `/api` endpoint, so U10's "no endpoint ever exists unprotected" holds until the security ticket.
4. **Gradle-generated launch scripts** (2026-10-31). The `application` plugin with `applicationName = "dfs-server"` and Spring Boot's `installBootDist` produce `bin/dfs-server` and `bin/dfs-server.bat` next to `lib/dfs-server.jar`, handling `JAVA_HOME`, `JAVA_OPTS` and `--enable-native-access=ALL-UNNAMED` as `cli`'s do. `docs/design/ui.md` U11 is amended from `dfs-server.cmd` to `dfs-server.bat`. A test starts the script for the current platform and waits for the listening line.

## Acceptance criteria

The full ID of each criterion is `DFS-035.AC-<n>`.

- **AC-1** `settings.gradle.kts` includes `server`; `./gradlew build` compiles and tests it; `server/build.gradle.kts` depends on `project(":file-scanner")` and the Spring Boot web starter and repeats none of the root `subprojects {}` config.
- **AC-2** When started, the server listens on `127.0.0.1` only, on a port chosen at startup (never a fixed one): two starts in a row get two listening ports, and each bound address is the loopback address.
- **AC-3** At startup the server logs one line `DFS server listening on http://127.0.0.1:<port>/` with the port it actually bound.
- **AC-4** `GET /` returns 200 with the static page containing `DFS server is running`; `GET /api/anything` returns 404 (no API exists yet).
- **AC-5** `server`'s `ArchitectureTest` forbids depending on anything under `us.pochaev.dfs.scanner` outside `domain`, passes on the server's own classes, and reports a violation for a test-only class that references `us.pochaev.dfs.scanner.infra.adapter.NioFileTreeWalker`.
- **AC-6** `./gradlew :server:bootJar` produces `server/build/libs/dfs-server.jar` whose manifest has `Enable-Native-Access: ALL-UNNAMED`, and `:server:bootRun` passes `--enable-native-access=ALL-UNNAMED`.
- **AC-7** `./gradlew :server:installBootDist` produces `bin/dfs-server` and `bin/dfs-server.bat`; the script for the current platform starts the server, which logs the AC-3 line within 60 seconds, and the test then stops the server and every process the script started.
- **AC-8** `CLAUDE.md` lists `server` as a built subproject with its run commands (`:server:bootRun`, `:server:bootJar`, `:server:installBootDist`), `README.md` names it, and `docs/design/ui.md` U11 names `dfs-server.bat`.

## Scenarios

Each scenario is an automated test in `server/src/test/java/us/pochaev/dfs/server`, except AC-1 (the build itself) and AC-8 (checked by reading, noted in `verification.md`).

```gherkin
Feature: Server subproject skeleton

  @AC-1
  Scenario: The build includes the server
    When "./gradlew build" runs from the repo root
    Then it compiles and tests ":server" along with ":file-scanner" and ":cli"
    And server/build.gradle.kts declares no toolchain, release or JUnit platform settings of its own

  @AC-2
  Scenario: The server binds to loopback on a fresh port
    When the server application context starts twice in a row
    Then each web server's bound address is 127.0.0.1
    And the two listening ports differ and neither is 8080

  @AC-3
  Scenario: Startup names the URL
    When the server starts and binds port P
    Then the log holds exactly one line "DFS server listening on http://127.0.0.1:P/"

  @AC-4
  Scenario: The placeholder page answers and no API exists
    Given the server is running
    When a client requests GET /
    Then the response is 200 and its body contains "DFS server is running"
    When a client requests GET /api/anything
    Then the response is 404

  @AC-5
  Scenario: The guard passes on the server's own classes
    When the server's ArchitectureTest checks us.pochaev.dfs.server
    Then no rule is violated

  @AC-5
  Scenario: The guard catches an infra dependency
    Given a test-only class that references us.pochaev.dfs.scanner.infra.adapter.NioFileTreeWalker
    When the same rule checks that class
    Then it reports one violation naming NioFileTreeWalker

  @AC-6
  Scenario: The jar allows native access
    When ":server:bootJar" has run
    Then server/build/libs/dfs-server.jar exists
    And its manifest has "Enable-Native-Access: ALL-UNNAMED"

  @AC-7
  Scenario: The launch script starts the server
    Given ":server:installBootDist" has run
    When the test starts bin/dfs-server.bat on Windows or bin/dfs-server elsewhere
    Then within 60 seconds its output holds "DFS server listening on http://127.0.0.1:"
    And after the test stops it, no process it started is still running

  @AC-8
  Scenario: The docs name the server
    Then CLAUDE.md lists server with :server:bootRun, :server:bootJar and :server:installBootDist
    And README.md names the server subproject
    And docs/design/ui.md U11 names dfs-server.bat
```

## Scope

**Touches:**
- `settings.gradle.kts`: include `server`.
- `server/build.gradle.kts`: new; Spring Boot plugin, `application` plugin (`applicationName = "dfs-server"`), web starter, `file-scanner`, native access for `bootRun`, `bootJar` and the start scripts, `bootJar` named `dfs-server.jar`, tests depending on `bootJar` and `installBootDist`.
- `server/src/main/java/us/pochaev/dfs/server`: `Main` (the Spring Boot application) and the startup listener that logs the URL.
- `server/src/main/resources/application.properties`: `server.address=127.0.0.1`, `server.port=0`.
- `server/src/main/resources/static/index.html`: the placeholder page.
- `server/src/test/java/us/pochaev/dfs/server`: `ArchitectureTest` (with its test-only violating class), `ServerApplicationTest` (AC-2 to AC-4), `NativeAccessTest` (AC-6), `LaunchScriptTest` (AC-7).
- `CLAUDE.md`: `server` moves from planned to built, with its commands.
- `README.md`: one line naming the server subproject.
- `docs/design/ui.md`: U11 names `dfs-server.bat`.

**Out of scope:**
- Security (U10): the next server ticket.
- The Angular frontend and its Gradle build (U9): `ui.md` ticket 4.
- Any endpoint that reads the disk.
- A `jpackage` installer (U11, later).

## Definition of done

- [x] Every scenario above is an automated test and passes, except AC-1 (the build) and AC-8 (read), each noted in `verification.md` (7 tests, 0 skipped)
- [x] Each test's `@DisplayName` starts with its criterion ID (`DFS-035.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests) (2026-10-31, worktree)
- [x] `./gradlew :server:bootRun` started by hand once: the URL line appears and the page opens in a browser, noted in `verification.md` (port 61011)
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
