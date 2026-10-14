---
id: DFS-027
title: Exclude known system files by default
status: draft
type: feature
scope: [file-scanner, cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-024]
touches: []
blocked_reason:
created: 2026-10-13
updated: 2026-10-13
completed:
---

# DFS-027: Exclude known system files by default

## Story

As a user scanning media copied between Macs and Windows
I want operating-system metadata files such as `.DS_Store` and `._*` left out of the scan by default
So that the report shows only the duplicates worth reclaiming, and those files are never offered for deletion

## Context

On 2026-10-13 a scan of `C:/Media D:/Media E:/Media F:/Media` (7.3 TB, 700,488 files) reported 11,590 duplicate sets and 46,567 files, 438.1 GB reclaimable. 29,565 of those 46,567 paths were macOS metadata (`._*` AppleDouble files and `.DS_Store`), about 4 KB each, so about 115 MB of the 438 GB. The two largest sets were exactly these files (7,993 copies of one `._*` file in a `.photoslibrary`, and 4,920 copies of `._.DS_Store`), and they caused an 18-minute stall in the hard-link phase (see DFS-026).

A `._<name>` file holds the Mac metadata of `<name>` (resource fork, extended attributes, Finder tags). Pairing it off on its own as a duplicate is meaningless, and deleting it apart from its main file loses that metadata.

Today `ScanConfig(minSize, sampleSize, partialHashThreshold)` in `domain.model` holds the scan settings, and the size filter is applied in `application` (`SizeIndex`, built in `ScanPipeline`), not in the walker (CLAUDE.md: "filtering by size belongs in `application`"). The walker (`NioFileTreeWalker`) yields every regular file. `cli` parses its options by hand in `ScanCommand` (`--min-size`, `--format`, `--progress`).

Starting design, to be confirmed in refinement:
- A built-in list of file-name globs, on by default: `.DS_Store`, `._*`, `Thumbs.db`, `desktop.ini`, `.localized`, `ehthumbs.db`.
- `ScanConfig` gains the exclude patterns; the filter runs in `application`, next to the size filter.
- `cli`: `--exclude=<glob>` (repeatable) adds patterns; `--no-default-excludes` drops the built-in list.
- Excluded files are counted, not listed: the summary (JSONL `summary` line and the text `Summary:`) gains an excluded count, with no `skipped` entry per file.

## Acceptance criteria

The full ID of each criterion is `DFS-027.AC-<n>`.

- **AC-1** <to be written during refinement>

## Scenarios

```gherkin
Feature: Default excludes

  @AC-1
  Scenario: <macOS metadata files are not in any duplicate set by default>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `domain.model` (`ScanConfig`): <to confirm>
- `application` (`ScanPipeline`, the filter): <to confirm>
- `us.pochaev.dfs.cli` (`ScanCommand`, the summary in JSONL and `TextReport`): <to confirm>
- `CLAUDE.md`: Commands and Current state

**Out of scope:**
- Deleting a `._<name>` sidecar together with its main file (belongs to the deletion tickets, DFS-021/DFS-022).
- Excluding by path or by size range beyond the existing `--min-size`.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check, e.g. a real scan of a folder holding `.DS_Store` and `._*` files reports them as excluded and in no set>

## Open questions

1. The default list: which names, and whether to match case-insensitively (Windows file names are case-insensitive).
2. Glob syntax and what it matches: the file name only, or also a path (e.g. `**/.Spotlight-V100/**`); whether excluded folders (`.Spotlight-V100`, `.Trashes`, `.fseventsd`) are skipped by the walker or filtered in `application`.
3. Whether the excluded count goes into the JSONL schema (DFS-020) as a new field on `summary`, and its name.
4. Whether excluded files appear in the walk's progress counts (DFS-024's `Walking:` line).
5. Archetype: `none` or a template set.
