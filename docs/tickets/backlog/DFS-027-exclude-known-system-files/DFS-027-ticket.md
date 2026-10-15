---
id: DFS-027
title: Exclude known system files by default
status: in-review
type: feature
scope: [file-scanner, cli]
archetype: pipeline-stage
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-024]
touches:
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/ScanConfig.java
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/ScanResult.java
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/application/ScanPipeline.java
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/application/
  - file-scanner/src/test/java/us/pochaev/dfs/scanner/application/ScanConfigTest.java
  - file-scanner/src/test/java/us/pochaev/dfs/scanner/application/ScanPipelineTest.java
  - file-scanner/src/test/java/us/pochaev/dfs/scanner/application/ScanServiceTest.java
  - file-scanner/src/test/java/us/pochaev/dfs/scanner/application/
  - file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/ScanPipelineEndToEndTest.java
  - cli/src/main/java/us/pochaev/dfs/cli/ScanCommand.java
  - cli/src/main/java/us/pochaev/dfs/cli/TextReport.java
  - cli/src/test/java/us/pochaev/dfs/cli/ScanCommandTest.java
  - cli/src/test/java/us/pochaev/dfs/cli/TextReportTest.java
  - CLAUDE.md
blocked_reason:
created: 2026-10-13
updated: 2026-10-15
completed:
---

# DFS-027: Exclude known system files by default

## Story

As a user scanning media copied between Macs and Windows
I want operating-system metadata files such as `.DS_Store` and `._*` left out of the scan by default
So that the report shows only the duplicates worth reclaiming, and those files are never offered for deletion

## Context

On 2026-10-13 a scan of `C:/Media D:/Media E:/Media F:/Media` (7.3 TB, 700,488 files) reported 11,590 duplicate sets and 46,567 files, 438.1 GB reclaimable. 29,565 of those 46,567 paths were macOS metadata (`._*` AppleDouble files and `.DS_Store`), about 4 KB each, so about 115 MB of the 438 GB. The two largest sets were exactly these files (7,993 copies of one `._*` file in a `.photoslibrary`, and 4,920 copies of `._.DS_Store`), and they caused an 18-minute stall in the hard-link phase (see DFS-026).

Where the 7,993 copies of that one `._*` file live (from the same report, 2026-10-14): 7,790 under `F:\Media\Catalogs\ApplePhotos` and 203 under `E:\Media\iPhone\2018-03 Photoslibrary` (sha256 `51dfecee9153`), so the set spans two libraries, not one. A smaller scan on 2026-10-14 (`E:/Media/iPhone/2018-03 Photoslibrary E:/Media/iPhone/iPhoneOxana F:/Media/iPhone/oxana E:/Media/Video/Import E:/Media/iPhone/GMetrix`: 573 sets, 1,633 files, 3.2 GB reclaimable) shows the same pattern at small scale: the photoslibrary's 203 copies are set #97, and many further sets are 4 KB `._*` files, including `._.DS_Store` (#303, 4 copies) and `._VIRB04xx.MP4` sidecars next to camera videos (#207, 11 copies).

A `._<name>` file holds the Mac metadata of `<name>` (resource fork, extended attributes, Finder tags). Pairing it off on its own as a duplicate is meaningless, and deleting it apart from its main file loses that metadata.

Today `ScanConfig(minSize, sampleSize, partialHashThreshold)` in `domain.model` holds the scan settings, and the size filter is applied in `application` (`SizeIndex`, built in `ScanPipeline`), not in the walker (CLAUDE.md: "filtering by size belongs in `application`"). The walker (`NioFileTreeWalker`) yields every regular file. `cli` parses its options by hand in `ScanCommand` (`--min-size`, `--format`, `--progress`).

Starting design, to be confirmed in refinement:
- A built-in list of file-name globs, on by default: `.DS_Store`, `._*`, `Thumbs.db`, `desktop.ini`, `.localized`, `ehthumbs.db`.
- `ScanConfig` gains the exclude patterns; the filter runs in `application`, next to the size filter.
- `cli`: `--exclude=<glob>` (repeatable) adds patterns; `--no-default-excludes` drops the built-in list.
- Excluded files are counted, not listed: the summary (JSONL `summary` line and the text `Summary:`) gains an excluded count, with no `skipped` entry per file.

**Decisions (refinement, 2026-10-14):**

1. **The built-in list is `._*` and `.DS_Store` only, matched case-sensitively; other names are deferred.** These two are what the real scans showed (29,565 of 46,567 duplicate paths on 2026-10-13), and both are written by macOS with exactly this spelling. `Thumbs.db`, `ehthumbs.db`, `desktop.ini`, `.localized`, `Icon\r` and folder-level patterns (`.Spotlight-V100`, `.Trashes`, `.fseventsd`, `.AppleDouble`) are left for a later ticket, once a scan shows them. Rejected for now: the six-name list matched case-insensitively (broader than the evidence) and adding folder patterns (they need the file-name-vs-path question settled first).
2. **One option, `--no-default-excludes`; no `--exclude=<glob>` yet.** The built-in list must be possible to switch off (a real file whose name starts with `._` stays scannable), but adding patterns is a feature of its own and comes with the deferred names. Given more than once, it is a usage error (exit 2), like the other options. Rejected: both options (glob syntax, its errors and its interplay to specify now) and neither (a real `._name` file could never be scanned).
3. **An excluded count in both summaries, always present.** The JSONL `summary` line gains `"excluded": <n>` (DFS-020's schema, an additive field), and the text report's last line gains `, <n> excluded` after `skipped` (`Summary: 573 sets, 1,633 files, 3.2 GB reclaimable, 0 skipped, 29,565 excluded`). It is 0 when nothing matched and always 0 with `--no-default-excludes`. Excluded files are counted, never listed one by one (there can be tens of thousands). The count has to reach `cli` through the scan result (`ScanResult` in `domain.model`, today `sets` and `skipped`). Existing DFS-020 and DFS-023 tests that assert the summary change with it. Rejected: the text report only (a JSONL consumer could not tell files were left out) and no count (files would vanish silently, unlike `skipped`).
4. **Excluded files count in the `Walking:` line, like files under `--min-size` today.** `ScanPipeline` emits `ScanEvent.FileWalked` for every `FileFound` before any filtering, and `SizeIndex` drops small files afterwards; the exclude filter sits next to the size filter, after that event, so "found" keeps meaning every regular file on disk and DFS-024's progress is unchanged. Rejected: filtering before `FileWalked` (inconsistent with `--min-size`, and changes DFS-024's counts) and a separate excluded figure in the walk line (new progress field for a number the summary already gives).
5. **Archetype `pipeline-stage`.** The core is a filter in the detection pipeline with a configuration default, and it changes two `domain.model` types (`ScanConfig`, `ScanResult`), which the design gate should see before tests are written. DFS-026, also a pipeline change with `cli` wiring, ran the same way. Rejected: `none` (no design gate on the domain-model changes, no independent test author or verifier).
6. **`ScanConfig` gains a flag, `boolean defaultExcludes`, `true` in `ScanConfig.defaults()`.** The two names live with the filter in `application`; `--no-default-excludes` builds the config with the flag `false`. The flag maps one-to-one onto the single option and decides no pattern syntax (`._*` is a prefix match, not a plain name), keeping decision 2's deferral. When `--exclude=<glob>` arrives, `ScanConfig` gains a pattern list beside the flag. Every `new ScanConfig(...)` call changes (in `ScanCommand` and tests); whether a convenience constructor is added is the architect's call. The test pinning `defaults()` to the `application` constants gains the flag. Rejected: the names themselves as `List<String> excludes` in `ScanConfig` (forces the pattern format into `domain.model` now).
7. **The name check runs before the size check, so every name match counts as excluded, whatever its size.** A 0-byte `.DS_Store` is counted in `excluded`, not dropped silently by `--min-size`. This follows from decision 4's placement (before `SizeIndex.add`); the count means "files the built-in list left out", checkable with a plain name search, and does not change with `--min-size`. With `--no-default-excludes`, a 0-byte `.DS_Store` is dropped by the size filter as today and counted nowhere. Rejected: size first (the filter would need `minSize` or move after `SizeIndex`, reopening decision 4).

**Edge-case checklist (`pipeline-stage`), as read in refinement** (the architect still records each in `design.md`):

- 1 Accumulation: not applicable beyond a count; the filter decides per file as the walk yields it and keeps only the excluded count, one per scan.
- 2 Inter-stage currency invariants, 4 Immutable results: not applicable; the filter passes `FileEntry` values through unchanged. `ScanResult`'s new count must not be negative.
- 3 Deterministic order: the filter keeps the walk's order; it drops entries and never reorders.
- 5 Repeated input: not applicable; `ScanPipeline.keepTopLevel` drops start locations equal to or inside another before walking, so a file reaches the filter once.
- 6 Configuration and defaults: decision 6.
- 7 Memory: not applicable; the filter holds one counter, no per-file state.
- 8 Groups of one, 9 Path order, 11 New ports, 12 Large fixtures: not applicable; the filter reads names only, before grouping, and reads no content.
- 10 Failure mid-stage: not applicable; a name check does no I/O and cannot fail.
- 13 Concurrent scans: the counter belongs to one scan run, never a shared field.
- 14 Currency named: the filter consumes the walk's `FileEntry` (from `WalkEvent.FileFound`) and passes kept entries on to `SizeIndex.add`; the architect chooses the filter's own type. Its place is fixed by decision 4: after `ScanEvent.FileWalked`, before `SizeIndex.add`.

## Acceptance criteria

The full ID of each criterion is `DFS-027.AC-<n>`.

- **AC-1** By default a regular file whose name is exactly `.DS_Store`, or starts with `._`, is in no duplicate set; the match is case-sensitive, so `.ds_store` and `.DS_STORE` are scanned as usual (decision 1).
- **AC-2** With `--no-default-excludes`, files named `.DS_Store` or starting with `._` are scanned like any other file; the option given twice is a usage error, exit 2, with no scan started (decision 2).
- **AC-3** The JSONL `summary` line has an `excluded` field and the text `Summary:` line ends `, <n> excluded`, both giving the number of files left out by the built-in list; it is 0 when none matched and with `--no-default-excludes`, and no excluded file appears in a `set` or `skipped` line (decision 3).
- **AC-4** Excluded files are counted in the `Walking:` progress line's files and bytes found, exactly as if they were not excluded (decision 4).
- **AC-5** `ScanConfig.defaults()` has the default excludes on, and a scan run with a `ScanConfig` whose flag is off scans `.DS_Store` and `._*` files like any other (decision 6).
- **AC-6** A file excluded by name is counted in `excluded` even when it is below `--min-size` (for example 0 bytes); with `--no-default-excludes` the same file is dropped by the size filter and counted nowhere (decision 7).

## Scenarios

```gherkin
Feature: Default excludes

  Background:
    Given a folder "media" holding
      | path           | content      |
      | a/photo.jpg    | 6 KB of "P"  |
      | b/photo.jpg    | 6 KB of "P"  |
      | a/._photo.jpg  | 4 KB of "M"  |
      | b/._photo.jpg  | 4 KB of "M"  |
      | a/.DS_Store    | 6 KB of "D"  |
      | b/.DS_Store    | 6 KB of "D"  |

  @AC-1 @AC-3
  Scenario: macOS metadata files are left out by default and counted
    When the user scans "media" with default options
    Then stdout has exactly one "set" line, holding "a/photo.jpg" and "b/photo.jpg"
    And no "set" or "skipped" line names a path ending "._photo.jpg" or ".DS_Store"
    And the "summary" line has "excluded": 4 and "skipped": 0

  @AC-3
  Scenario: The text report ends with the excluded count
    When the user scans "media" with --format=text
    Then the last line is "Summary: 1 sets, 2 files, 6.0 KB reclaimable, 0 skipped, 4 excluded"

  @AC-1
  Scenario: The match is case-sensitive
    Given "a/.DS_Store" is renamed to "a/.ds_store" and "b/.DS_Store" to "b/.DS_STORE"
    When the user scans "media" with default options
    Then a "set" line holds "a/.ds_store" and "b/.DS_STORE"
    And the "summary" line has "excluded": 2

  @AC-1
  Scenario: Only a name that starts with "._" or is exactly ".DS_Store" matches
    Given "a/x._photo.jpg" and "b/x._photo.jpg" each hold 5 KB of "X"
    And "a/.DS_Store.bak" and "b/.DS_Store.bak" each hold 5 KB of "Y"
    When the user scans "media" with default options
    Then one "set" line holds both "x._photo.jpg" paths and another holds both ".DS_Store.bak" paths
    And the "summary" line has "excluded": 4

  @AC-2 @AC-3
  Scenario: --no-default-excludes scans metadata files like any other
    When the user scans "media" with --no-default-excludes
    Then stdout has three "set" lines: the two "photo.jpg", the two "._photo.jpg", the two ".DS_Store"
    And the "summary" line has "excluded": 0

  @AC-3
  Scenario: Nothing matched gives an excluded count of 0
    Given a folder "plain" holding only "a/photo.jpg" and "b/photo.jpg", 6 KB of "P" each
    When the user scans "plain" with default options
    Then the "summary" line has "excluded": 0
    And with --format=text the last line ends ", 0 skipped, 0 excluded"

  @AC-2
  Scenario: --no-default-excludes given twice is a usage error
    When the user runs the scanner with "media --no-default-excludes --no-default-excludes"
    Then the exit code is 2
    And stderr says "--no-default-excludes given more than once" and shows the usage line, which names --no-default-excludes
    And nothing is written to stdout and no scan is started

  @AC-4
  Scenario: Excluded files still count as found in the walk progress
    When the user scans "media" with --progress=log
    Then the finished "Walking:" line reports "6 files, 32.0 KB found"

  @AC-5
  Scenario: The default configuration has the excludes on
    Then ScanConfig.defaults() has default excludes on, minSize 1, sampleSize 65536 and partialHashThreshold 131072

  @AC-5
  Scenario: A configuration with the flag off scans metadata files
    Given a ScanConfig equal to the defaults except default excludes off
    When the scan pipeline runs over "media" with it
    Then the result holds three duplicate sets and an excluded count of 0

  @AC-6
  Scenario: An empty metadata file counts as excluded, not as too small
    Given "a/.DS_Store" and "b/.DS_Store" are emptied to 0 bytes
    And "c/._empty" is an empty file
    When the user scans "media" with default options
    Then the "summary" line has "excluded": 5

  @AC-6
  Scenario: With the excludes off, an empty metadata file is dropped by size and counted nowhere
    Given "a/.DS_Store" and "b/.DS_Store" are emptied to 0 bytes
    When the user scans "media" with --no-default-excludes
    Then no "set" line names a ".DS_Store" path
    And the "summary" line has "excluded": 0
```

"Default options" means JSONL output (no `--format`) unless the scenario says otherwise.

## Scope

**Touches:**
- `domain.model`: `ScanConfig` gains `defaultExcludes` (decision 6); `ScanResult` gains the excluded count (decision 3).
- `application`: one new filter class holding the two names; `ScanPipeline.walkLocations` applies it after `ScanEvent.FileWalked` and before `SizeIndex.add`, and carries the count into `ScanResult` (decisions 4, 7).
- `us.pochaev.dfs.cli`: `ScanCommand` parses `--no-default-excludes`, adds it to the usage line and writes `"excluded"` in the JSONL `summary`; `TextReport` appends `, <n> excluded`.
- Tests: `ScanConfigTest`, `ScanPipelineTest`, `ScanServiceTest`, `ScanPipelineEndToEndTest` (the existing `new ScanConfig(...)`/`new ScanResult(...)` calls and summary assertions), the new filter's test, `ScanCommandTest`, `TextReportTest`.
- `CLAUDE.md`: the Commands block (`--no-default-excludes`) and Current state (the JSONL `summary` gains `excluded`; default excludes).

**Out of scope:**
- Deleting a `._<name>` sidecar together with its main file (belongs to the deletion tickets, DFS-021/DFS-022).
- Excluding by path or by size range beyond the existing `--min-size`.
- `--exclude=<glob>` and any user-defined pattern (decision 2).
- Other system names (`Thumbs.db`, `ehthumbs.db`, `desktop.ini`, `.localized`, `Icon\r`) and folder-level patterns: deferred to a later ticket (decision 1).

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] A real scan of `'E:/Media/iPhone/2018-03 Photoslibrary' E:/Media/iPhone/iPhoneOxana F:/Media/iPhone/oxana E:/Media/Video/Import E:/Media/iPhone/GMetrix --format=text` (2026-10-14 baseline: 573 sets, 1,633 files, 3.2 GB reclaimable) lists no set whose paths are `._*` or `.DS_Store`, reports a non-zero `excluded` count and fewer than 573 sets, with reclaimable space still 3.2 GB
- [x] The same scan with `--no-default-excludes` reproduces the baseline (573 sets, 1,633 files) with `0 excluded`
  - Run 2026-10-15 from the story worktree. Default: `Summary: 394 sets, 932 files, 3.2 GB reclaimable, 0 skipped, 990 excluded`, and no listed path is `._*` or `.DS_Store`. With `--no-default-excludes`: `Summary: 573 sets, 1,633 files, 3.2 GB reclaimable, 0 skipped, 0 excluded`.
- [x] `CLAUDE.md` names `--no-default-excludes` and the `excluded` summary field

## Open questions

None.
