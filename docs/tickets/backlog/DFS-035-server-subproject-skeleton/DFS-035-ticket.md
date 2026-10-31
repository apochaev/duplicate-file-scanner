---
id: DFS-035
title: Add the server subproject skeleton
status: draft
type: build
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: []
touches: []
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

## Acceptance criteria

The full ID of each criterion is `DFS-035.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Server subproject skeleton

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
- Security (U10): the next server ticket.
- The Angular frontend and its Gradle build (U9): `ui.md` ticket 4.
- Any endpoint that reads the disk.
- A `jpackage` installer (U11, later).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Archetype: `none` (implemented by hand) or a pipeline archetype? No existing archetype covers a new Gradle subproject.
- Wait for DFS-031 (`scanner-spring`) or depend on `file-scanner` directly for now?
- Does the skeleton expose anything (e.g. a health or version endpoint) or only start and listen on `127.0.0.1` with a random port?
- Bind address and port in this ticket (U10 says `127.0.0.1`, random port) or in the security ticket?
- Launch scripts: how `dfs-server.cmd` / `dfs-server.sh` find the jar and Java 25, and whether a test checks them.
