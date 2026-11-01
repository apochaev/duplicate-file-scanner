---
id: DFS-042
title: Name the CLI start script dfs and document installing outside the repo
status: draft
type: build
scope: [cli, docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: low
lane: backend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-042: Name the CLI start script dfs and document installing outside the repo

## Story

As the user running DFS from any folder
I want the CLI's start script to be called `dfs` (next to the server's `dfs-server`), and the steps to install both outside the repo written down
So that the command does not clash with other tools named `cli` on my `PATH`, and updating the installed copies is a known routine

## Context

Requested by the frontend lane (dfs-frontend) on 2026-11-01, from the user's question how to run the binaries outside the project. No ticket depends on it; it is a convenience.

Today `./gradlew :cli:installBootDist` produces `cli/build/install/cli-boot/bin/cli` and `cli.bat` (the `application` plugin's default name, the subproject's), while the server's scripts are `bin/dfs-server` and `dfs-server.bat` (`applicationName = "dfs-server"`, DFS-035 decision 4). The user installs both by copying `cli/build/install/cli-boot` and `server/build/install/dfs-server-boot` to a folder outside the repo (for example `C:\Users\<user>\Apps\dfs\cli` and `...\server`, with `robocopy /MIR`), adding both `bin` folders to the user `PATH` and setting the user `JAVA_HOME` to a Java 25 (the scripts use `JAVA_HOME` before `PATH`; a Java 17 on `PATH` fails with `UnsupportedClassVersionError`, see DFS-039).

## Acceptance criteria

The full ID of each criterion is `DFS-042.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: CLI start script name and installing outside the repo

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
- An installer or bundled runtime (`jpackage`, `ui.md` U11 "Later").
- The old-Java message (DFS-039).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

Suggested by the requester; the owner decides:
- `applicationName = "dfs"` in `cli/build.gradle.kts`, so `installBootDist` writes `bin/dfs` and `bin/dfs.bat` (and the install folder becomes `dfs-boot`); a test like the server's `LaunchScriptTest` that runs the installed script.
- A short "Installing outside the repo" note in `CLAUDE.md` Commands: the two `installBootDist` tasks, the copy, `PATH` and `JAVA_HOME`, and updating (stop a running server first, its jar is locked).
- Does anything refer to the old script name (docs, tests)?
- Archetype: likely `none` (a build setting and docs).
