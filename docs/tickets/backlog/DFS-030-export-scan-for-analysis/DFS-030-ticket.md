---
id: DFS-030
title: Export a complete scan for analysis
status: draft
type: feature
scope: [file-scanner, cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: [DFS-019, DFS-020]
touches: []
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# DFS-030: Export a complete scan for analysis

## Story

As a user who wants to analyse a scan with other tools
I want the JSONL output to record everything the scan saw: where it looked, with which settings, how many files each folder holds, and each duplicate's size and modification time
So that a separate analysis tool (or plain DuckDB or SQLite queries) can work out which folders overlap, fully or partly, without rescanning the disk

## Context

- **The JSONL output today** (DFS-020; `ScanCommand.write`): one `set` line per `DuplicateSet` (`size`, `digest`, and `copies`: an array of physical copies, each an array of hard-linked path strings), then one `skipped` line per skipped path (`path`, `reason`, `detail`), then one `summary` line (`sets`, `files`, `skipped`, `reclaimableBytes`, `excluded`).
- **Enough for pairs, not for "full vs partial":** from the `set` lines a tool can compute folder pairs, both directions, internal duplicates and roll-up to parent folders. It cannot tell "340 duplicated files" from "340 of 400 files", because nothing records how many files each folder holds in total. It also cannot tell where roll-up should stop (the scanned roots) or which settings shaped the result (`--min-size`, the default excludes).
- **What exists in the core:** `ScanResult(sets, skipped, excluded)` from `GetScanResult.result(ScanId)`. `DuplicateSet(size, digest, files, copies)` with `PhysicalCopy(links)`. `FileEntry(path, size, lastModified)`, so modification times are already known; the JSONL just does not write them.
- **Why now:** the 2026-10-13 scan of a 3.1 TB media drive produced 11,590 duplicate sets across 46,567 files, too many to review set by set. A folder-level view makes that actionable.
- **Hard links are not duplicates** (CLAUDE.md safety invariants): the export keeps hard-linked paths grouped as one physical copy, as it does today, so a consumer never mistakes them for copies.

Decisions settled in refinement:

1. **Folder-level analysis moves out of the scanner; this ticket makes the export complete enough for it.** First refined as "show which folders overlap" (a `use-case` ticket with a query port in the core), DFS-030 was re-scoped on 2026-10-31. A folder overlap view is analysis: it wants interactive querying, slicing and possibly graph storage, and it changes faster than the scanner should. The overlap design settled before the re-scope (per-folder summaries plus pairs carrying both directions, subtree roll-up, internal duplicates, ordering) is kept in `docs/design/folder-overlap-analysis.md` for that tool. Deletion safety stays in the core: DFS-022's "never delete the last copy" check works from `DuplicateSet`s directly. CLAUDE.md decision 1 is updated to say that `LocationOverlap` is computed by an analysis tool from the export, not by the scanner.
2. **The scan records per-folder totals and the scanned roots in `ScanResult`.** During the walk, `ScanPipeline` counts each folder's files and bytes, and `ScanResult` gains a component holding them: one entry per folder that directly holds at least one counted file, about 9k entries for the 8,816 folders the 2026-10-31 scan of the same drive walked. `ScanResult` also records the scanned roots as resolved paths.
   - **A counted file** is one that took part in the scan: a regular file of at least the minimum size that the default excludes did not leave out. Empty, excluded and skipped files are not counted, matching what the report already calls a file.
   - **Existing `new ScanResult(...)` callers** change with the record: `ScanPipeline`, the `file-scanner` tests `ScanServiceTest` and `ScanResultTest`, and the `cli` tests `ScanCommandTest` and `TextReportTest`. Those test files go in `touches`, as DFS-027 did when it added `excluded`.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-030.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: Complete scan export

  @AC-1
  Scenario: <to be written during refinement>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- To be decided during refinement. Likely `ScanPipeline` and `ScanResult` in `file-scanner`, `ScanCommand` in `cli`, the tests named in decision 2, and `CLAUDE.md` (decision 1).

**Out of scope:**
- Computing folder overlaps, in the scanner or the CLI (decision 1; see `docs/design/folder-overlap-analysis.md`)
- The analysis tool itself
- Deletion plans and deleting anything (DFS-022, DFS-021)
- Changing `DuplicateSet`, or how duplicates are detected. The pipeline only gains per-folder counting (decision 2).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

1. **Archetype and split:** one ticket touching both `file-scanner` (folder counting, roots in `ScanResult`) and `cli` (the JSONL lines), or a core ticket followed by a `wiring` ticket.
2. **The `scan` header line:** which fields (roots, minimum size, excludes on or off, start time, finish time, schema version), and where the start time comes from (a clock passed into the core, or the CLI).
3. **The `folder` lines:** fields (path, files, bytes), order, and where they sit among the other line types.
4. **Size and modification time per path:** reshape `copies` from arrays of path strings into objects (a breaking change for DFS-020's consumers), or add parallel fields. Time format (ISO-8601 UTC, epoch milliseconds).
5. **Schema version:** whether the change bumps a version recorded in the header, and how a consumer detects an old-format file without one.
6. **The text report:** unchanged, or gains the roots and settings in its header.
7. **Scale:** memory for per-folder totals on a large walk, and the export's size on the 2026-10-31 scan (153,028 files, 8,816 folders).
