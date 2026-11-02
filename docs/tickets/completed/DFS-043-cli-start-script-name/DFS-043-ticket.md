---
id: DFS-043
title: Name the CLI start script dfs and document installing outside the repo
status: done
type: build
scope: [cli, docs]
archetype: none
priority: low
lane: backend
depends_on: []
touches:
  - cli/build.gradle.kts
  - cli/src/test/java/us/pochaev/dfs/cli/LaunchScriptTest.java
  - README.md
  - CLAUDE.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed: 2026-11-01
---

# DFS-043: Name the CLI start script dfs and document installing outside the repo

## Story

As the user running DFS from any folder
I want the CLI's start script to be called `dfs` (next to the server's `dfs-server`), and the steps to install both outside the repo written down
So that the command does not clash with other tools named `cli` on my `PATH`, and updating the installed copies is a known routine

## Context

Requested by the frontend lane (dfs-frontend) on 2026-11-01, from the user's question how to run the binaries outside the project. No ticket depends on it; it is a convenience.

Today `./gradlew :cli:installBootDist` produces `cli/build/install/cli-boot/bin/cli` and `cli.bat` (the `application` plugin's default name, the subproject's), while the server's scripts are `bin/dfs-server` and `dfs-server.bat` (`applicationName = "dfs-server"`, DFS-035 decision 4). The user installs both by copying `cli/build/install/cli-boot` and `server/build/install/dfs-server-boot` to a folder outside the repo (for example `C:\Users\<user>\Apps\dfs\cli` and `...\server`, with `robocopy /MIR`), adding both `bin` folders to the user `PATH` and setting the user `JAVA_HOME` to a Java 25 (the scripts use `JAVA_HOME` before `PATH`; a Java 17 on `PATH` fails with `UnsupportedClassVersionError`, see DFS-039).

### Decisions

1. **Archetype `none`, implemented by hand.** One build setting, one launch test modelled on the server's `LaunchScriptTest`, and a docs section; the pipeline's phases would cost more than the change. The implementing session ticks the done-items with evidence and writes `verification.md` opening with the product owner review (tickets skill, "Implementing a ticket"). No gates, no autorun.
2. **Keep the repo-root `dfs.bat` beside the installed `bin/dfs`, and document the difference.** `dfs.bat` is the in-repo development launcher: it rebuilds the boot jar from source, switches the console to UTF-8 (`chcp 65001`) and runs it, as `README.md` "Usage" describes. `bin/dfs` (and `dfs.bat` in that `bin`) is the installed copy, for use from any folder without the source tree. The README says which to use when; neither launcher changes behavior, and the README's existing `dfs.bat` usage stays valid. Rejected: retiring `dfs.bat` (loses the rebuild-and-run loop while developing) and making it call the installed script (couples development to a manual install).
3. **A CLI `LaunchScriptTest` on the server's pattern.** `cli/build.gradle.kts`'s `tasks.test` also depends on `installBootDist` and passes `-Ddfs.installDir=<cli/build/install/dfs-boot>` (as `server/build.gradle.kts` does for `dfs-server-boot`). The test checks that `bin/dfs` and `bin/dfs.bat` exist and `bin/cli` does not, then runs the platform's script (`dfs.bat` on Windows, `dfs` elsewhere) on a `@TempDir` holding two identical files the test writes itself (`a/same.txt` and `b/same.txt`, same bytes), with a bounded timeout, and asserts exit code 0, exactly one JSONL `set` line naming both paths and exactly one `summary` line on stdout. The test writes its own files because `Fixtures.copyInto` lives in `file-scanner`'s test sources, which `cli` cannot reach. Only a temp folder, never a real drive. Rejected: checking the files only (a script that exists but fails to start would pass) and a manual run alone (a removed `applicationName` would regress unseen).
4. **Docs: a README section plus one CLAUDE.md line.** `README.md` gets an "Installing outside the repo" section after "Usage": run `./gradlew :cli:installBootDist :server:installBootDist`; copy `cli/build/install/dfs-boot` and `server/build/install/dfs-server-boot` to a folder outside the repo (for example `robocopy <src> C:\Users\<user>\Apps\dfs\cli /MIR`, and `...\server`); add both `bin` folders to the user `PATH`; set the user `JAVA_HOME` to a Java 25 (the scripts use it before `PATH`); to update, rebuild and copy again, stopping a running server first since its jar is locked. "Usage" gets one sentence on which launcher to use when (decision 2). `CLAUDE.md` Commands gets one line beside `:server:installBootDist`: `./gradlew :cli:installBootDist  # cli/build/install/dfs-boot/bin/dfs(.bat)`. Rejected: `CLAUDE.md` only (the user would read the agents' file for install steps) and a separate `docs/install.md` (one more file for about 15 lines).
5. **Nothing refers to the old name.** A search of the repo for the `cli` start script (`bin/cli`, `cli.bat`, `cli-boot`) found no doc or test using it, so the rename breaks nothing else.

## Acceptance criteria

The full ID of each criterion is `DFS-043.AC-<n>`.

- **AC-1** `./gradlew :cli:installBootDist` writes `cli/build/install/dfs-boot/bin/dfs` and `bin/dfs.bat`, and no `bin/cli` or `bin/cli.bat`.
- **AC-2** The installed start script, run with `JAVA_HOME` set to a Java 25 on a folder holding two identical files, exits with code 0 and writes to stdout exactly one JSONL `set` line naming both files and exactly one `summary` line.
- **AC-3** `README.md` has an "Installing outside the repo" section naming both `installBootDist` tasks, copying both install folders out of the repo, adding both `bin` folders to `PATH`, setting `JAVA_HOME` to a Java 25, and updating (stop a running server first).
- **AC-4** `README.md` "Usage" says to use `dfs.bat` inside the repo (rebuilds from source) and the installed `dfs` everywhere else, and `CLAUDE.md` Commands lists `./gradlew :cli:installBootDist` with the `bin/dfs` path.

## Scenarios

```gherkin
Feature: CLI start script name and installing outside the repo

  # AC-1 and AC-2: automated, in cli's LaunchScriptTest. AC-3 and AC-4: checked by reading.

  @AC-1
  Scenario: The CLI installs as dfs
    When I run ./gradlew :cli:installBootDist
    Then cli/build/install/dfs-boot/bin holds dfs and dfs.bat
    And it holds neither cli nor cli.bat

  @AC-2
  Scenario: The installed script scans a folder
    Given a temp folder with a/same.txt and b/same.txt holding the same bytes "dfs-043"
    And JAVA_HOME set to the test's Java 25
    When I run the installed dfs.bat on Windows, or dfs elsewhere, with that folder and --progress=off
    Then it exits with code 0 within 60 seconds
    And stdout holds exactly one "set" line naming a/same.txt and b/same.txt
    And stdout holds exactly one "summary" line

  @AC-3
  Scenario: README explains installing outside the repo
    When I read README.md "Installing outside the repo"
    Then it names ./gradlew :cli:installBootDist and :server:installBootDist
    And it copies cli/build/install/dfs-boot and server/build/install/dfs-server-boot out of the repo
    And it adds both bin folders to the user PATH and sets JAVA_HOME to a Java 25
    And it says to stop a running server before copying an update, since its jar is locked

  @AC-4
  Scenario: The two launchers and the build command are documented
    When I read README.md "Usage" and CLAUDE.md "Commands"
    Then Usage says dfs.bat rebuilds and runs inside the repo, and the installed dfs runs from any folder
    And Commands lists ./gradlew :cli:installBootDist with cli/build/install/dfs-boot/bin/dfs(.bat)
```

## Scope

**Touches:**
- `cli/build.gradle.kts`: `application { applicationName = "dfs" }`; `tasks.test` depends on `installBootDist` and passes `-Ddfs.installDir`.
- `cli/src/test/java/us/pochaev/dfs/cli/LaunchScriptTest.java`: new, AC-1 and AC-2.
- `README.md`: "Installing outside the repo", and the launcher sentence in "Usage".
- `CLAUDE.md`: one Commands line.

**Out of scope:**
- An installer or bundled runtime (`jpackage`, `ui.md` U11 "Later").
- The old-Java message (DFS-039).
- Changing the repo-root `dfs.bat` (decision 2).
- Renaming the server's scripts or the boot jar names.

## Definition of done

- [x] The AC-1 and AC-2 scenarios are automated tests in `LaunchScriptTest` and pass; AC-3 and AC-4 are checked by reading (2 tests, 0 failures; README and CLAUDE.md read, see verification.md)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests) (2026-11-01, worktree: 556 JUnit tests, 3 skipped, Vitest 21, 0 failures)
- [x] No files changed outside the paths listed under Touches (`git status`: the four Touches paths plus this ticket's folder)
- [x] Manually: `./gradlew :cli:installBootDist`, then `cli/build/install/dfs-boot/bin/dfs.bat <fixture copy in a temp folder> --format=text` prints a report (never a real drive) (2 sets, 4 files, 45 B reclaimable on a scratchpad fixture copy)
- [x] `verification.md` opens with the product owner review, one verdict per criterion

## Open questions

None.
