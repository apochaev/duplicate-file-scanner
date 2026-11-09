---
id: DFS-061
title: Skip known operating-system folders in scans
status: done
type: feature
scope: [file-scanner, cli]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [done]
priority: medium
lane: backend
depends_on: []
touches:
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.infra.port
  - us.pochaev.dfs.scanner.infra.adapter
  - cli/src/test/java/us/pochaev/dfs/cli
  - CLAUDE.md
blocked_reason:
created: 2026-11-08
updated: 2026-11-09
completed: 2026-11-09
---

# DFS-061: Skip known operating-system folders in scans

## Story

As a user scanning a whole drive
I want the folders the operating system keeps for itself (Windows `$RECYCLE.BIN`, macOS `.Trashes`, Linux `.Trash-1000` and the like) left out of the scan
So that files in a recycle bin or a system folder are never reported as duplicates or offered for deletion

## Context

Reported by the product owner on 2026-11-08: a scan listed files under `$RECYCLE.BIN\<SID>\`, such as `$R5Y7A3U.ARQ` (a recycle-bin copy of a deleted file; the matching `$I...` file holds its original path). They need skipping, as do the known system folders of Windows, macOS and Linux.

- DFS-027 (done) left out two file names by default (`.DS_Store`, `._*`) and **deferred folder-level patterns** (`.Spotlight-V100`, `.Trashes`, `.fseventsd`, `.AppleDouble`) and other names (`Thumbs.db`, `desktop.ini`, ...) until a scan showed them (its decision 1).
- DFS-052 (done) made the walker skip a plain directory named exactly `.dfs-quarantine`, reporting it as skipped with `QUARANTINE`, always, also with `--no-default-excludes`. That is the precedent for a folder-level skip in the walker.
- `ScanConfig.defaultExcludes` and the `excluded` count in both summaries (DFS-027) already exist for names filtered in `application`.

## Decisions

1. **Fixed folder names, matched at any depth** (2026-11-08). A directory is skipped when its name is exactly one of: `$RECYCLE.BIN`, `System Volume Information`, `Recycled`, `RECYCLER` (Windows; compared case-insensitively, as NTFS does), `.Trashes`, `.Spotlight-V100`, `.fseventsd`, `.TemporaryItems`, `.DocumentRevisions-V100` (macOS), `.Trash`, `lost+found`, or `.Trash-` followed by digits (Linux; compared case-sensitively). Wherever it sits under a scan root, the whole subtree is left out. Rejected: root-only matching (misses a stray system folder when a subfolder is scanned, and needs the walker to know volume roots) and a split rule for the ambiguous names (two rules to specify and test). Accepted cost: a user folder that happens to be named `lost+found` or `.Trash` is skipped too, which decision 2's switch will have to allow for.

2. **One `skipped` entry per skipped folder, reason `SYSTEM_FOLDER`** (2026-11-08). The walker reports the folder as `WalkEvent.Skipped(path, SkipReason.SYSTEM_FOLDER, detail)` and does not enter it, as DFS-052 does for `QUARANTINE`; the `skipped` line of the JSONL, the text report's skipped section and the summary's `skipped` count then include it, and the files under it are neither listed nor counted. Rejected: a count only (the user could not see which folder was left out, which hides a wrong skip of a user folder named `lost+found`) and a different shape in each format (DFS-020 and DFS-023 keep the two in step).

3. **`--no-default-excludes` also switches the folder skip off** (2026-11-08). The folder skip belongs to `ScanConfig.defaultExcludes` (DFS-027 decision 6) with the two file names: with the flag, the folders are walked like any other, and no `SYSTEM_FOLDER` entry is reported. No new option and no new `ScanConfig` field. Rejected: a separate flag (a second option, usage case and field for a rare need) and no switch (a user folder named `lost+found` or `.Trash` could not be scanned). Unlike DFS-052's `.dfs-quarantine`, which is skipped always, since scanning it would break the deletion invariants.

4. **A scan root is always walked; only folders found below it are matched** (2026-11-08). `scan D:\$RECYCLE.BIN` or a path inside it is an explicit request, so the root itself and its parents are never tested against the names; a matching folder below the root is still skipped. No new error code, unlike `LOCATION_IN_QUARANTINE` (DFS-052), which exists because quarantined files would break the deletion invariants. Rejected: refusing such a location with a new error (a new code and exit path, lifted by `--no-default-excludes`, blocking the legitimate use of seeing what a recycle bin holds).

5. **Only folders in this ticket** (2026-11-08). `Thumbs.db`, `ehthumbs.db`, `desktop.ini` and `.localized` stay deferred, as DFS-027 left them: a name filter in `application` is a second mechanism, DFS-027's condition for them (seeing them in a real scan) is not met, and a real `desktop.ini` can carry a user's folder settings. They get their own ticket if a scan shows them.

6. **The walker port gets a per-call switch; the adapter stays stateless** (2026-11-08). Only the walker can prune a subtree (a `WalkListener` returning `false` stops the whole walk), and `defaultExcludes` is a per-scan setting while `NioFileTreeWalker` is one shared bean. So `FileTreeWalker.walk` gains a per-call parameter saying whether to skip the system folders, which `ScanPipeline` fills from `config.defaultExcludes()` at its one call (`ScanPipeline.walkLocations`). The architect chooses the signature; the two test fakes that implement the port (`ScanPipelineTest.FakeFileTreeWalker`, `ScanFolderTotalsTest.FakeWalker`) change with it. `QUARANTINE` stays unconditional (AC-5). The name check applies to plain directories only (AC-6), at the same place in `NioFileTreeWalker` as the quarantine check. The `skipped` detail text is "operating-system folder, not scanned". Rejected: a walker constructor flag (the shared adapter would carry one scan's setting) and filtering in `application` (it cannot prune, so every file under the folder would still be walked and counted as found).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-061.AC-<n>`.

- **AC-1** A directory whose name is one of the fixed names of decision 1 is not entered, at any depth below a scan root: no file under it appears in a duplicate set; Windows names match in any letter case, the others only as spelled; a directory merely containing a name (`my.Trash`, `lost+found2`) is scanned.
- **AC-2** Each skipped system folder is reported once as a `skipped` entry with reason `SYSTEM_FOLDER` and the folder's path, in the JSONL `skipped` line and the text report, and counted in the summary's `skipped`; no file under it is listed, counted as found, or hashed.
- **AC-3** With `--no-default-excludes` (a `ScanConfig` whose `defaultExcludes` is false), files under the folders of decision 1 are scanned like any other and no `SYSTEM_FOLDER` entry is reported; `ScanConfig.defaults()` has the skip on.
- **AC-4** A scan location whose own name, or a parent's name, is one of the fixed names is walked normally; only matching folders found below it are skipped.
- **AC-5** The quarantine folder `.dfs-quarantine` is still skipped with reason `QUARANTINE`, with the system-folder skip off as well as on, and a system folder is never reported as `QUARANTINE`.
- **AC-6** Only a plain directory is matched: a regular file with one of the names is scanned like any other, and a symbolic link with one of the names keeps its `SYMLINK` reason.

## Scenarios

```gherkin
Feature: Known system folders are skipped

  Background:
    Given a folder "drive" holding
      | path                                   | content     |
      | docs/photo.jpg                         | 6 KB of "P" |
      | $RECYCLE.BIN/S-1-5-21-1/$R5Y7A3U.ARQ  | 6 KB of "P" |
      | $RECYCLE.BIN/S-1-5-21-1/$I5Y7A3U.ARQ  | 1 KB of "I" |

  @AC-1 @AC-2
  Scenario: A recycle bin copy is not reported as a duplicate
    When the user scans "drive" with default options
    Then stdout has no "set" line
    And stdout has exactly one "skipped" line, with the path "drive/$RECYCLE.BIN", reason "SYSTEM_FOLDER" and detail "operating-system folder, not scanned"
    And the "summary" line has "skipped": 1

  @AC-2
  Scenario: The text report lists the skipped folder
    When the user scans "drive" with --format=text
    Then a line starts with "    " followed by the path "drive/$RECYCLE.BIN" and "  SYSTEM_FOLDER  "
    And the last line ends ", 1 skipped, 0 excluded"

  @AC-2
  Scenario: Files under a skipped folder are not counted as found
    When the user scans "drive" with --progress=log
    Then the finished "Walking:" line reports "1 files, 6.0 KB found"

  @AC-1
  Scenario Outline: Each known name is skipped
    Given "drive/<name>/x.bin" and "drive/docs/x.bin" each hold 6 KB of "X"
    When the user scans "drive" with default options
    Then the "skipped" lines name "drive/<name>" with reason "SYSTEM_FOLDER"
    And no "set" line names a path under "<name>"

    Examples:
      | name                      |
      | $RECYCLE.BIN              |
      | System Volume Information |
      | Recycled                  |
      | RECYCLER                  |
      | .Trashes                  |
      | .Spotlight-V100           |
      | .fseventsd                |
      | .TemporaryItems           |
      | .DocumentRevisions-V100   |
      | .Trash                    |
      | lost+found                |
      | .Trash-1000               |
      | .Trash-0                  |

  @AC-1
  Scenario: A known folder is skipped at any depth
    Given "drive/deep/er/.Trashes/x.bin" and "drive/docs/x.bin" each hold 6 KB of "X"
    When the user scans "drive" with default options
    Then the "skipped" lines name "drive/deep/er/.Trashes" and no "set" line is produced

  @AC-1
  Scenario: Windows names match in any letter case
    Given "drive/$recycle.bin/x.bin" and "drive/system volume information/x.bin" and "drive/docs/x.bin" each hold 6 KB of "X"
    When the user scans "drive" with default options
    Then the "skipped" lines name "drive/$recycle.bin" and "drive/system volume information"
    And no "set" line is produced

  @AC-1
  Scenario Outline: Names that only resemble a known folder are scanned
    Given "drive/<name>/x.bin" and "drive/docs/x.bin" each hold 6 KB of "X"
    When the user scans "drive" with default options
    Then one "set" line holds both "x.bin" paths
    And no "skipped" line names "drive/<name>"

    Examples:
      | name        |
      | my.Trash    |
      | lost+found2 |
      | .Trash-     |
      | .Trash-abc  |
      | Trash       |
      | .trashes    |
      | LOST+FOUND  |

  @AC-3
  Scenario: With --no-default-excludes the folders are scanned
    When the user scans "drive" with --no-default-excludes
    Then one "set" line holds "drive/docs/photo.jpg" and "drive/$RECYCLE.BIN/S-1-5-21-1/$R5Y7A3U.ARQ"
    And no "skipped" line has reason "SYSTEM_FOLDER"

  @AC-3
  Scenario: The core setting reaches the walker
    Given a ScanConfig equal to the defaults except default excludes off
    When the scan pipeline runs over "drive" with it
    Then the result holds one duplicate set and no skipped folder
    And ScanConfig.defaults() has default excludes on

  @AC-4
  Scenario: A location inside a system folder is walked
    Given "drive/$RECYCLE.BIN/S-1-5-21-1/copy/$R5Y7A3U.ARQ" holds 6 KB of "P"
    When the user scans "drive/$RECYCLE.BIN/S-1-5-21-1" with default options
    Then one "set" line holds both "$R5Y7A3U.ARQ" paths
    And no "skipped" line is produced

  @AC-4
  Scenario: A location named like a system folder is walked, but folders below it are still skipped
    Given "drive/$RECYCLE.BIN/System Volume Information/x.bin" and "drive/$RECYCLE.BIN/S-1-5-21-1/x.bin" each hold 6 KB of "X"
    When the user scans "drive/$RECYCLE.BIN" with default options
    Then the "skipped" lines name "drive/$RECYCLE.BIN/System Volume Information" only
    And the files under "S-1-5-21-1" are in the scan

  @AC-5
  Scenario: The quarantine folder is skipped with or without the switch
    Given "drive/.dfs-quarantine/run1/x.bin" and "drive/docs/x.bin" each hold 6 KB of "X"
    When the user scans "drive" with default options
    Then the "skipped" lines include "drive/.dfs-quarantine" with reason "QUARANTINE"
    When the user scans "drive" with --no-default-excludes
    Then the "skipped" lines still include "drive/.dfs-quarantine" with reason "QUARANTINE"
    And no "skipped" line names it with reason "SYSTEM_FOLDER"

  @AC-6
  Scenario: A regular file with a known name is scanned
    Given "drive/a/lost+found" and "drive/b/lost+found" are regular files holding 6 KB of "F"
    When the user scans "drive" with default options
    Then one "set" line holds both "lost+found" paths

  @AC-6
  Scenario: A symbolic link with a known name keeps its link reason
    Given "drive/.Trashes" is a symbolic link to "drive/docs"
    When the user scans "drive" with default options
    Then the "skipped" lines name "drive/.Trashes" with reason "SYMLINK"
    And the test is skipped where the platform or privileges do not allow a symbolic link
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: `SkipReason` gains `SYSTEM_FOLDER`.
- `us.pochaev.dfs.scanner.infra.port`: `FileTreeWalker.walk` gains the per-call switch (decision 6).
- `us.pochaev.dfs.scanner.infra.adapter`: `NioFileTreeWalker` matches the names on plain directories and reports them.
- `us.pochaev.dfs.scanner.application`: `ScanPipeline` passes `config.defaultExcludes()` to the walker.
- Tests in `file-scanner`: `NioFileTreeWalkerTest` and a new walker test beside it, the two fakes of the port (`ScanPipelineTest`, `ScanFolderTotalsTest`), `ScanPipelineTest` for the setting, `ScanQuarantineEndToEndTest` (AC-5).
- `cli/src/test/java/us/pochaev/dfs/cli`: `ScanCommandTest`, which already asserts the `QUARANTINE` skipped lines (DFS-052.AC-5), gains the `SYSTEM_FOLDER` ones. `ScanCommand` and `TextReport` print any `SkipReason` generically, so no `cli` main source changes.
- `CLAUDE.md`: the safety invariants entry for the quarantine skip, and Current state, name the system folders and `--no-default-excludes`.

**Out of scope:**
- Further file names (`Thumbs.db`, `ehthumbs.db`, `desktop.ini`, `.localized`) (decision 5).
- Deleting or quarantining anything in these folders.
- A user-defined skip list (`--exclude=<glob>`), still deferred by DFS-027 decision 2.

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `CLAUDE.md` names the skipped folders and `--no-default-excludes`'s extra effect (the verifier reads it)
- [ ] Started by hand once on Windows: a scan of a drive root such as `D:\` lists `D:\$RECYCLE.BIN` as a `SYSTEM_FOLDER` skipped line, and no `$R...` file appears in a set (closed without, at the user's say-so, 2026-11-09)

## Open questions

None.
