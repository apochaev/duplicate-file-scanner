---
id: DFS-052
title: Skip quarantine folders when scanning
status: draft
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 35}}
gates: [done]
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: backend
depends_on: [DFS-051, DFS-055]
touches:
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.domain.usecases.command
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.infra.adapter
  - cli/src/test/java/us/pochaev/dfs/cli
  - server/src/test/java/us/pochaev/dfs/server/ScanApiTest.java
  - CLAUDE.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-07
completed:
---

# DFS-052: Skip quarantine folders when scanning

## Story

As the user who has executed a deletion plan
I want later scans to leave the app's quarantine folders out
So that quarantined files are never reported as duplicates of the files they were verified against, and a plan can never quarantine a file that is already in quarantine

## Context

DFS-051 moves quarantined files to `<volume root or anchor>/.dfs-quarantine/<run ID>/<path below it>` (DFS-051 decision 5 and its design's `ExecutionConfig.quarantineAnchors`). A later scan whose location contains a volume root or an anchor walks `.dfs-quarantine` like any folder, so every quarantined file reappears as a copy of its keeper. Raised by DFS-051's architect at the design gate (2026-11-01) as a follow-up outside DFS-051's scope, and confirmed by DFS-051's product-owner review (its `verification.md`, "Known limits", design note 7, 2026-11-05): a later scan of a volume root would offer the quarantined files as duplicates, inviting the user to "delete" copies already in quarantine. The anchors are configured as `dfs.execution.quarantine-anchors` (`ExecutionProperties` in `scanner-spring`).

The walker (`NioFileTreeWalker`, `FileTreeWalker.walk`) already leaves things out with `WalkEvent.Skipped(path, SkipReason, detail)`; default name excludes (`.DS_Store`, `._*`) are filtered in `application` (DFS-027) and counted in the summary's `excluded`.

## Decisions

1. **The walker prunes the folder and reports it as skipped** (2026-11-07). `NioFileTreeWalker` never enters a directory named `.dfs-quarantine` (DFS-051's `NioQuarantineDeleter.QUARANTINE_FOLDER`) and reports it once as `WalkEvent.Skipped(path, SkipReason.QUARANTINE, detail)`, a new `SkipReason`; it appears as one `skipped` line per quarantine folder in the JSONL and text reports. Always on, not tied to `--no-default-excludes`: skipping the quarantine is a safety rule, not OS-metadata cleanup. A quarantine of thousands of files costs one directory check, and the report says where a quarantine was left out. The new `reason` value is additive (DFS-030: no `schemaVersion` bump); tests asserting `skipped` lines are searched in the test trees (DFS-030.L-2). Not a per-file filter in `application` like the default excludes (walks the whole quarantine, `--no-default-excludes` would bring quarantined files back, and `excluded` would mix OS metadata with safety) and not a silent prune (files under a scanned folder left out with no trace).
2. **Any directory named exactly `.dfs-quarantine`, anywhere, matched case-sensitively** (2026-11-07). The walker decides by the name alone, like the default excludes' case-sensitive names. It needs no execution config (the anchors live in `ExecutionConfig`, which scans never see, and a CLI scan may have none configured), and it also catches a quarantine made under other anchor settings, by an earlier run or on a disk from another machine. Known limit: a user's own folder with that exact name is skipped too; the report shows it. Not only directly under a volume root or a configured anchor (new config wiring into scans, and it misses quarantines made under anchors since changed) and not matched ignoring case (the app never creates another casing).
3. **A scan location in a quarantine is refused** (2026-11-07). `StartScan` refuses a location whose absolute path has an element named exactly `.dfs-quarantine` (the location itself or any ancestor; a check on the path's names, no disk access, case-sensitive as decision 2) with a new `ErrorCode` `LOCATION_IN_QUARANTINE`, the message naming the location; nothing is started. The CLI exits 2 with the message, and the server's `POST /api/scans` answers `400` `LOCATION_IN_QUARANTINE` through its existing mapping of core codes. Keeps the story's second promise: a plan from such a scan could otherwise move quarantined files into a quarantine nested in the quarantine. Quarantined files are handled by undo and purge (DFS-051's follow-ups), not by scans. Known limit: the scanner cannot be used to look inside a quarantine. Not scanned as asked (breaks the promise, and DFS-054's plans could target quarantined files) and not scanned with a warning (a warning stops no plan).
4. **Archetype `use-case`, gates `[done]`** (2026-11-07). It changes the `StartScan` use case (`ScanService`) and a secondary adapter (`NioFileTreeWalker`), with two new `domain.model` values; no detection stage changes, so not `pipeline-stage`. The folder name moves from `NioQuarantineDeleter.QUARANTINE_FOLDER` to one constant in `domain.model`, since `application` may not depend on `infra.adapter`; the deleter uses it. Straight to the product-owner review: decisions 1 to 3 fix the enum value, the match rule and the error, nothing here plans or moves files, and the review catches a misplaced check. Runs after DFS-055 (both touch `infra.adapter`; DFS-055 is done). Not `[design, done]` (a stop with nothing left to decide).

## Acceptance criteria

The full ID of each criterion is `DFS-052.AC-<n>`.

- **AC-1** `NioFileTreeWalker` never enters a plain directory named exactly `.dfs-quarantine` below the walked root, at any depth: none of its files or subfolders are yielded or walked, and it is reported once as `WalkEvent.Skipped(<its path>, SkipReason.QUARANTINE, <detail>)`.
- **AC-2** Only that exact name on a plain directory is pruned: directories named `.DFS-Quarantine` or `.dfs-quarantine-old` and a regular file named `.dfs-quarantine` are walked or yielded as before, and a symbolic link or junction named `.dfs-quarantine` is still reported as `SYMLINK` or `SPECIAL_FILE` and never followed.
- **AC-3** A scan leaves quarantined files out: for a location holding `A/photo.jpg` and `.dfs-quarantine/<run ID>/A/photo.jpg` with identical content, the `ScanResult` has no duplicate set for them, no folder total counts the quarantined file, `skipped` holds the quarantine folder with reason `QUARANTINE`, and `excluded` is unchanged; the same with `defaultExcludes` off.
- **AC-4** `StartScan` refuses a location whose absolute path has an element named exactly `.dfs-quarantine` (the location itself or an ancestor) with `Failure(LOCATION_IN_QUARANTINE)`, the message naming the location, checked before the location is looked up on disk; one such location among several refuses the whole start, and no scan is created. A location with `.DFS-Quarantine` in its path is not refused for that.
- **AC-5** The CLI writes the pruned folder as a `skipped` line with `"reason": "QUARANTINE"` in JSON Lines and lists it with `QUARANTINE` under "Skipped" in the text report, counted in the summary's skipped count; a location in a quarantine exits 2 with the message on stderr and nothing on stdout.
- **AC-6** The server's `POST /api/scans` with a location in a quarantine answers `400` with code `LOCATION_IN_QUARANTINE` and starts no scan.

## Scenarios

```gherkin
Feature: Skip quarantine folders when scanning

  Background:
    Given a @TempDir "<tmp>" holding "A/photo.jpg" (2 MB)
    And "<tmp>/.dfs-quarantine/run-1/A/photo.jpg", a byte-identical copy, created at test time

  @AC-1
  Scenario: The walker prunes a quarantine folder and reports it
    When the walker walks "<tmp>"
    Then it yields "<tmp>/A/photo.jpg" only
    And it reports one Skipped("<tmp>/.dfs-quarantine", QUARANTINE, <detail>)
    And no path under "<tmp>/.dfs-quarantine" is visited

  @AC-1
  Scenario: A deeper quarantine folder is pruned too
    Given "<tmp>/A/B/.dfs-quarantine/run-2/x.bin" exists
    When the walker walks "<tmp>"
    Then "<tmp>/A/B/.dfs-quarantine" is reported as Skipped with QUARANTINE
    And "x.bin" is not yielded

  @AC-2
  Scenario Outline: Only the exact folder name is pruned
    Given <entry> exists under "<tmp>" holding or being "y.bin"
    When the walker walks "<tmp>"
    Then <outcome>

    Examples:
      | entry                                           | outcome                                              |
      | a directory ".DFS-Quarantine"                   | its "y.bin" is yielded                               |
      | a directory ".dfs-quarantine-old"               | its "y.bin" is yielded                               |
      | a regular file named ".dfs-quarantine"          | the file is yielded                                  |

  @AC-2
  Scenario: A link named like a quarantine is still a link
    Given "<tmp>/B/.dfs-quarantine" is a symbolic link (or on Windows a junction) to "<tmp>/A", created at test time and skipped where the platform refuses
    When the walker walks "<tmp>"
    Then it is reported as Skipped with SYMLINK (or SPECIAL_FILE for the junction), not QUARANTINE
    And nothing is yielded through it

  @AC-3
  Scenario: A quarantined copy is not a duplicate
    When a scan of "<tmp>" completes
    Then the ScanResult has no duplicate set
    And no folder total includes "<tmp>/.dfs-quarantine" or the files under it
    And skipped holds "<tmp>/.dfs-quarantine" with reason QUARANTINE
    And excluded is 0

  @AC-3
  Scenario: Turning off default excludes does not bring quarantines back
    When a scan of "<tmp>" with defaultExcludes off completes
    Then the ScanResult has no duplicate set and skipped holds the quarantine folder

  @AC-4
  Scenario Outline: A location in a quarantine is refused
    When StartScan is called with <locations>
    Then it returns Failure LOCATION_IN_QUARANTINE with a message naming <named>
    And no scan is created

    Examples:
      | locations                                              | named                                  |
      | "<tmp>/.dfs-quarantine"                                 | "<tmp>/.dfs-quarantine"                |
      | "<tmp>/.dfs-quarantine/run-1/A"                         | "<tmp>/.dfs-quarantine/run-1/A"        |
      | "<tmp>/A" and "<tmp>/.dfs-quarantine/run-1"             | "<tmp>/.dfs-quarantine/run-1"          |
      | "<tmp>/.dfs-quarantine/missing" (does not exist)        | "<tmp>/.dfs-quarantine/missing"        |

  @AC-4
  Scenario: Another casing is not a quarantine
    Given "<tmp>/.DFS-Quarantine/C" exists
    When StartScan is called with it
    Then the scan starts

  @AC-5
  Scenario: The CLI reports the pruned folder
    When the CLI scans "<tmp>" as JSON Lines, and again with --format=text
    Then the JSON Lines hold a skipped line with path "<tmp>/.dfs-quarantine" and reason "QUARANTINE" and no set line
    And the text report lists "<tmp>/.dfs-quarantine  QUARANTINE" under "Skipped (1):"
    And both summaries count 1 skipped

  @AC-5
  Scenario: The CLI refuses a location in a quarantine
    When the CLI is run with "<tmp>/.dfs-quarantine/run-1"
    Then it exits 2 with a stderr message naming that location and LOCATION_IN_QUARANTINE
    And stdout is empty

  @AC-6
  Scenario: The server refuses a location in a quarantine
    When I POST to "/api/scans" the locations ["<tmp>/.dfs-quarantine/run-1"]
    Then the answer is 400 with code "LOCATION_IN_QUARANTINE"
    And GET "/api/scans" lists no new scan
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: `SkipReason.QUARANTINE`, `ErrorCode.LOCATION_IN_QUARANTINE`, and the folder name `.dfs-quarantine` as one constant reachable from `application` and `infra.adapter` (decision 4).
- `us.pochaev.dfs.scanner.domain.usecases.command`: `StartScan`'s documented failures.
- `us.pochaev.dfs.scanner.application`: the location check in `ScanService`.
- `us.pochaev.dfs.scanner.infra.adapter`: the prune in `NioFileTreeWalker`; `NioQuarantineDeleter` uses the shared constant.
- `cli/src/test/java/us/pochaev/dfs/cli`: the AC-5 tests (no CLI source change: `ScanCommand` and `TextReport` print `reason().name()`).
- `server/src/test/java/us/pochaev/dfs/server/ScanApiTest.java`: the AC-6 test (no server source change: `ScanController` maps core codes to `400`).
- `CLAUDE.md`: the walker's skip reasons ("Provisional port sketch") and the safety invariant on quarantine folders.

**Out of scope:**
- Undo and purge of a quarantine run (DFS-051's follow-ups).
- Hiding `.dfs-quarantine` in the server's folder picker (`ListDirectory`).
- Matching by the configured quarantine anchors (decision 2) or ignoring case.
- A switch to scan quarantines (decision 3's known limit).
- Changes to the JSONL `schemaVersion` (the new reason is additive, DFS-030).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] Each test's `@DisplayName` starts with its criterion ID (`DFS-052.AC-n`)
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] Every use-case-archetype checklist item is addressed or marked not applicable with a reason
- [ ] The test trees were searched for tests asserting `skipped` lines or the list of skip reasons, and any that broke were updated (DFS-030.L-2)
- [ ] `CLAUDE.md` lists `QUARANTINE` among the walker's skip reasons and states that scans leave quarantine folders out and refuse locations inside one (the verifier reads it)
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
