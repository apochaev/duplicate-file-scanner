---
id: DFS-061
title: Skip known operating-system folders in scans
status: draft
type: feature
scope: [file-scanner, cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: medium
lane: backend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-08
updated: 2026-11-08
completed:
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

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-061.AC-<n>`.

- **AC-1** A directory whose name is one of the fixed names of decision 1 is not entered, at any depth below a scan root: no file under it appears in a duplicate set; Windows names match in any letter case, the others only as spelled; a directory merely containing a name (`my.Trash`, `lost+found2`) is scanned.
- **AC-2** Each skipped system folder is reported once as a `skipped` entry with reason `SYSTEM_FOLDER` and the folder's path, in the JSONL `skipped` line and the text report, and counted in the summary's `skipped`; no file under it is listed, counted as found, or hashed.
- **AC-3** With `--no-default-excludes` (a `ScanConfig` whose `defaultExcludes` is false), files under the folders of decision 1 are scanned like any other and no `SYSTEM_FOLDER` entry is reported; `ScanConfig.defaults()` has the skip on.

## Scenarios

```gherkin
Feature: Known system folders are skipped

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
- <to be refined>

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Interaction with a start location that is itself inside such a folder.
- Whether the file names deferred by DFS-027 (`Thumbs.db`, `desktop.ini`, ...) join this ticket.
- Archetype and gates.
