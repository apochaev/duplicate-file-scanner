---
id: DFS-030
title: Export a complete scan for analysis
status: ready
type: feature
scope: [file-scanner, cli]
archetype: pipeline-stage
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: [DFS-019, DFS-020]
touches:
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/FolderTotal.java
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/ScanResult.java
  - file-scanner/src/main/java/us/pochaev/dfs/scanner/application/ScanPipeline.java
  - file-scanner/src/test/java/us/pochaev/dfs/scanner/application/
  - cli/src/main/java/us/pochaev/dfs/cli/ScanCommand.java
  - cli/src/main/java/us/pochaev/dfs/cli/ScanRunner.java
  - cli/src/main/java/us/pochaev/dfs/cli/TextReport.java
  - cli/src/test/java/us/pochaev/dfs/cli/
  - tools/analysis/overlaps.sql
  - CLAUDE.md
  - README.md
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
3. **One ticket, `archetype: pipeline-stage`, as DFS-027 did.** DFS-027 (exclude known system files) had the same shape, a pipeline change plus CLI output, and ran as one `pipeline-stage` ticket with `scope: [file-scanner, cli]`. Counting during the walk is pipeline work (the checklist's accumulation, ordering and measured-memory items apply). The `wiring` archetype's "use case + CLI = two tickets" rule targets new use-case ports, and this ticket adds none. The archetype's checklist says nothing about output formats, so the JSONL schema is settled in full here in refinement.
4. **A `scan` header line comes first, holding the roots, the settings, the times and the format version.** Example: `{"type":"scan","schemaVersion":2,"roots":["F:\\Media"],"minSize":1,"defaultExcludes":true,"sampleSize":65536,"partialHashThreshold":131072,"startedAt":"2026-10-31T09:12:03Z","finishedAt":"2026-10-31T11:47:55Z"}`.
   - **`roots`** come from `ScanResult` (decision 2), as resolved paths.
   - **The settings** are all four `ScanConfig` components (`minSize`, `sampleSize`, `partialHashThreshold`, `defaultExcludes`), so a consumer knows exactly what a counted file meant.
   - **The times** are taken by `ScanCommand` from an injected `java.time.Clock`, just before the scan starts and once it finishes. They are written as ISO-8601 UTC. The core stays clock-free, and tests pin exact times with a fixed clock.
   - **It is always the first line**, so a reader can check `schemaVersion` before parsing anything else.
   - **It is written only with `--format=jsonl`.**
   - **The `ScanCommand` constructor gains the `Clock`**, so `ScanRunner` (which builds it) and `ScanCommandTest` change with it.
5. **`folder` lines hold direct counts only, come right after the header, and are sorted by path.** Example: `{"type":"folder","path":"F:\\Media\\Photos\\2019","files":400,"bytes":10485760000}`.
   - **Direct counts only:** the counted files sitting in that folder itself, not its subfolders. A consumer sums up the tree. A stored subtree total would be an analysis result (decision 1) and would invite double counting.
   - **One line per folder that directly holds at least one counted file**, matching decision 2. Folders that hold only subfolders can be derived from the paths.
   - **Line order:** `scan`, then the `folder` lines, then `set`, then `skipped`, then `summary`. A streaming reader knows every total before the first set.
   - **Sorted by path** in `java.nio.file.Path` order, so the output is deterministic.
6. **Each path in `copies` becomes an object holding its own modification time.** Example: `"copies":[[{"path":"F:\\a\\x.jpg","modified":"2019-07-14T10:22:31.4512345Z"}],[{"path":"F:\\b\\x.jpg","modified":"2019-07-14T10:22:31.4512345Z"},{"path":"F:\\b\\link.jpg","modified":"2019-07-14T10:22:31.4512345Z"}]]`.
   - **One array per physical copy** as before, grouping hard-linked paths. Each path's time comes from its own `FileEntry.lastModified`, so hard links read at different moments are still exact.
   - **Times** are ISO-8601 UTC at full precision (`Instant.toString()`).
   - **Size stays on the `set` line**, since it is the same for every path.
   - **This breaks readers of the old string shape.** The only one is `tools/analysis/overlaps.sql`, which this ticket updates to the new shape, and to use the `folder` lines and the `scan` roots. Decision 7 bumps the format version.
7. **An integer `schemaVersion` in the header, bumped only for breaking changes. Readers ignore what they do not know.**
   - **This ticket's format is version 2.** A file with no `scan` line is version 1 (DFS-020's format), which is how earlier reports are recognised.
   - **The version goes up** only when something existing changes meaning or shape: a field renamed or retyped, or a line type removed.
   - **New fields and new line types do not bump it.** Readers must ignore fields and line types they do not recognise.
   - **`CLAUDE.md` records the rule** beside its JSONL description, and its pointer "the schema and rules are in DFS-020" moves to DFS-030.
8. **The text report gains a short header.** Example: `Scan of F:\Media` followed by `  2026-10-31 09:12:03Z to 11:47:55Z, minimum size 1 B, default excludes on`. With several roots, the first line lists them all, separated by commas. It uses the same roots, settings and times as the JSONL header (decision 4). Per-folder totals and modification times stay out of the text report: they are for tools, not people. `TextReport` and `TextReportTest` change with it.
9. **The roots recorded are the ones actually walked, and a file is counted when the walk keeps it.**
   - **Roots** are `ScanPipeline.keepTopLevel`'s output: resolved real paths, sorted, with any location equal to or nested inside another dropped. Example: `F:\Media F:\Media\Photos` records `["F:\\Media"]`. Because nested locations are dropped before walking, every file is walked once, so overlapping locations cannot double-count a folder (checklist item 5, settled by the existing code).
   - **A file counts in its folder's total** once the walk keeps it: not left out by the default excludes, and at least `minSize`. That is the point where it enters the `SizeIndex`.
   - **A file that fails later** (unreadable when sampled or hashed) stays in its folder's total and also appears in `skipped`. This errs on the safe side: an unreadable file is never shown as having a copy, so its folder looks partly duplicated and never falsely fully covered. A consumer can subtract skipped paths.
10. **Memory is measured with a committed `@Disabled` heap benchmark at 100,000 folders**, following DFS-001's `SizeIndexHeapBenchmark`. It is run once by hand, and the figure (bytes per folder, total) is recorded in this ticket before it closes (checklist item 7). 100,000 folders is about ten times the 2026-10-31 drive. The export's size on that drive is not a gate, since the pipeline cannot reach `F:`; it is noted after the user re-runs the scan.

Remaining `pipeline-stage` checklist items, settled without a separate question:

- **Item 1, accumulation:** the folder counter lives inside one `scan()` call and is fed by the walk's existing callback, which runs on the scan's thread. It needs no `merge`. Parallel walking (DFS-006) would revisit it.
- **Item 2, invariants:** a new record `FolderTotal(Path folder, long files, long bytes)` in `domain.model` (already exported; no new package) rejects a null folder, `files` below 1 and `bytes` below 0. `ScanResult` rejects null `roots` or `folders`, or a null element in either, and takes immutable copies.
- **Items 3 and 4, order and immutability:** `ScanResult.roots()` is sorted ascending (the walked order), `ScanResult.folders()` ascending by folder path, and both lists are immutable.
- **Hard links in totals:** totals count paths as the walk saw them, so two hard links in one folder count as 2 files and twice the bytes. The totals are raw walk data; a consumer that wants physical space uses the `copies` grouping of the `set` lines.
- **Item 6, configuration:** no new setting.
- **Items 8, 9, 11 and 12:** not applicable. The ticket adds no group stage, reads no file content, adds no port and needs no large fixtures.
- **Item 13, concurrent scans:** no state outside the `scan()` call.
- **Item 14, currency:** `ScanResult` (decision 2), which the CLI already consumes.

## Acceptance criteria

The full ID of each criterion is `DFS-030.AC-<n>`.

- **AC-1** `ScanResult.roots()` holds the locations the scan actually walked: resolved real paths, sorted ascending, with any location equal to or nested inside another left out.
- **AC-2** `ScanResult.folders()` holds one `FolderTotal` per folder that directly holds at least one counted file, with the number and summed size of those files, sorted ascending by folder path; a folder holding only subfolders has no entry.
- **AC-3** A file is counted only when the walk keeps it: a file left out by the default excludes, a file smaller than `minSize` (empty files included) and a path the walk skips are not counted.
- **AC-4** A counted file that later fails to be sampled or hashed stays in its folder's total and also appears in `ScanResult.skipped()`.
- **AC-5** Two hard links to one file in the same folder count as two files and twice the size in that folder's total.
- **AC-6** `FolderTotal` rejects a null folder, `files` below 1 and `bytes` below 0; `ScanResult` rejects null `roots` or `folders` or a null element in either, and returns immutable lists.
- **AC-7** With `--format=jsonl`, the first output line is a `scan` line holding `schemaVersion` 2, `roots`, `minSize`, `sampleSize`, `partialHashThreshold`, `defaultExcludes`, and `startedAt` and `finishedAt`, read from `ScanCommand`'s `Clock` before the scan starts and after it finishes, as ISO-8601 UTC.
- **AC-8** With `--format=jsonl`, one `folder` line per `FolderTotal` follows the `scan` line, in `ScanResult.folders()` order, each holding `path`, `files` and `bytes`.
- **AC-9** In each `set` line, `copies` is an array per physical copy of objects holding `path` and `modified`, the path's `lastModified` as ISO-8601 UTC at full precision.
- **AC-10** With `--format=jsonl`, the lines come in the order `scan`, `folder`, `set`, `skipped`, `summary`, and the `summary` line is unchanged.
- **AC-11** With `--format=text`, the report starts with `Scan of <roots, comma-separated>` and then a line giving the start and finish times, the minimum size and whether default excludes are on, before the first duplicate set.

## Scenarios

```gherkin
Feature: Complete scan export

  Background:
    Given a ScanConfig with minSize 1 and default excludes on

  @AC-1
  Scenario: Nested and repeated locations are recorded once, as walked
    Given folders "root/a" and "root/a/b" exist
    When a scan is run over "root/a/b", "root/a" and "root/a"
    Then ScanResult.roots() is exactly the real path of "root/a"

  @AC-1
  Scenario: Several separate locations are recorded sorted
    Given folders "root/z" and "root/m" exist
    When a scan is run over "root/z" and "root/m"
    Then ScanResult.roots() is the real paths of "root/m" then "root/z"

  @AC-2
  Scenario: Each folder's direct files are totalled
    Given "root/p/a.jpg" of 100 bytes, "root/p/b.jpg" of 250 bytes and "root/p/q/c.jpg" of 40 bytes
    When a scan is run over "root"
    Then ScanResult.folders() is "root/p" with 2 files and 350 bytes, then "root/p/q" with 1 file and 40 bytes
    And "root" has no entry, because it holds only a subfolder

  @AC-2
  Scenario: Folders without duplicates are totalled too
    Given "root/u/only.txt" of 10 bytes with no copy anywhere
    When a scan is run over "root"
    Then ScanResult.folders() contains "root/u" with 1 file and 10 bytes
    And ScanResult.sets() is empty

  @AC-3
  Scenario: Excluded, too small and empty files are not counted
    Given a ScanConfig with minSize 5 and default excludes on
    And in "root/f": ".DS_Store" of 6 bytes, "._x.jpg" of 6 bytes, "tiny.txt" of 3 bytes, "empty.txt" of 0 bytes and "kept.txt" of 8 bytes
    When a scan is run over "root"
    Then the entry for "root/f" has 1 file and 8 bytes

  @AC-3
  Scenario: With default excludes off, excluded names are counted
    Given a ScanConfig with minSize 1 and default excludes off
    And in "root/f": ".DS_Store" of 6 bytes and "kept.txt" of 8 bytes
    When a scan is run over "root"
    Then the entry for "root/f" has 2 files and 14 bytes

  @AC-3
  Scenario: A path the walk skips is not counted
    Given a fake walker that yields "root/g/ok.txt" of 5 bytes and a Skipped event for "root/g/locked.txt"
    When a scan is run over "root"
    Then the entry for "root/g" has 1 file and 5 bytes
    And ScanResult.skipped() contains "root/g/locked.txt"

  @AC-4
  Scenario: A file that fails hashing stays counted
    Given "root/h/a.bin" and "root/h/b.bin" of 20 bytes each
    And a fake hasher that fails reading "root/h/b.bin"
    When a scan is run over "root"
    Then the entry for "root/h" has 2 files and 40 bytes
    And ScanResult.skipped() contains "root/h/b.bin"

  @AC-5
  Scenario: Hard links count once per path
    Given "root/l/one.dat" of 30 bytes and "root/l/two.dat", a hard link to it
    When a scan is run over "root"
    Then the entry for "root/l" has 2 files and 60 bytes

  @AC-6
  Scenario: FolderTotal rejects invalid values
    Then FolderTotal(null, 1, 0) throws NullPointerException
    And FolderTotal("root/x", 0, 0) throws IllegalArgumentException
    And FolderTotal("root/x", 1, -1) throws IllegalArgumentException

  @AC-6
  Scenario: ScanResult rejects nulls and returns immutable lists
    Then a ScanResult with null roots, null folders, a null root or a null FolderTotal throws NullPointerException
    And adding to roots() or folders() of a valid ScanResult throws UnsupportedOperationException
    And changing the list passed in for folders after construction leaves folders() unchanged

  @AC-7
  Scenario: The JSONL output starts with the scan header
    Given ScanCommand's Clock reads "2026-10-31T09:12:03Z" before the scan and "2026-10-31T11:47:55Z" after it
    And a scan result whose roots are "D:\Photos" and "E:\Backup"
    And the config minSize 1, sampleSize 65536, partialHashThreshold 131072, default excludes on
    When the CLI writes the result with --format=jsonl
    Then the first line is {"type":"scan","schemaVersion":2,"roots":["D:\\Photos","E:\\Backup"],"minSize":1,"sampleSize":65536,"partialHashThreshold":131072,"defaultExcludes":true,"startedAt":"2026-10-31T09:12:03Z","finishedAt":"2026-10-31T11:47:55Z"}

  @AC-8
  Scenario: Folder lines follow the header
    Given a scan result whose folders are "D:\Photos\2019" with 400 files and 10485760000 bytes, then "D:\Photos\2020" with 3 files and 900 bytes
    When the CLI writes the result with --format=jsonl
    Then lines 2 and 3 are {"type":"folder","path":"D:\\Photos\\2019","files":400,"bytes":10485760000} and {"type":"folder","path":"D:\\Photos\\2020","files":3,"bytes":900}

  @AC-9
  Scenario: Each path in a set carries its modification time
    Given a duplicate set of 27 bytes with copies ["D:\a\x.jpg"] modified "2019-07-14T10:22:31.4512345Z" and ["D:\b\x.jpg", "D:\b\link.jpg"] both modified "2020-01-02T03:04:05Z"
    When the CLI writes the result with --format=jsonl
    Then the set line's copies are [[{"path":"D:\\a\\x.jpg","modified":"2019-07-14T10:22:31.4512345Z"}],[{"path":"D:\\b\\x.jpg","modified":"2020-01-02T03:04:05Z"},{"path":"D:\\b\\link.jpg","modified":"2020-01-02T03:04:05Z"}]]

  @AC-10
  Scenario: Line types come in a fixed order
    Given a scan result with 2 folders, 1 duplicate set and 1 skipped path
    When the CLI writes the result with --format=jsonl
    Then the line types are scan, folder, folder, set, skipped, summary
    And the summary line holds sets, files, skipped, reclaimableBytes and excluded with the same values as before this ticket

  @AC-10
  Scenario: An empty result still has the header and summary
    Given a scan result with no folders, no sets and no skipped paths
    When the CLI writes the result with --format=jsonl
    Then the line types are scan, summary

  @AC-11
  Scenario: The text report starts with what was scanned
    Given ScanCommand's Clock reads "2026-10-31T09:12:03Z" before the scan and "2026-10-31T11:47:55Z" after it
    And a scan result whose roots are "D:\Photos" and "E:\Backup", minSize 1, default excludes on
    When the CLI writes the result with --format=text
    Then the first line is "Scan of D:\Photos, E:\Backup"
    And the second line is "  2026-10-31 09:12:03Z to 11:47:55Z, minimum size 1 B, default excludes on"
    And the duplicate sets follow

  @AC-11
  Scenario: A scan that crosses midnight shows both dates
    Given ScanCommand's Clock reads "2026-10-31T23:50:00Z" before the scan and "2026-11-01T00:20:00Z" after it
    And a scan result whose root is "D:\Photos", minSize 1048576, default excludes off
    When the CLI writes the result with --format=text
    Then the second line is "  2026-10-31 23:50:00Z to 2026-11-01 00:20:00Z, minimum size 1.0 MB, default excludes off"
```

## Scope

**Touches:**
- `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/FolderTotal.java`: new record (checklist item 2).
- `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/ScanResult.java`: gains `roots` and `folders`.
- `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/ScanPipeline.java`: counts folders during the walk, and passes the roots and totals into `ScanResult`.
- `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/`: new tests, the heap benchmark, and the existing `new ScanResult(...)` callers (`ScanServiceTest`, `ScanResultTest`, `ScanPipelineTest`).
- `cli/src/main/java/us/pochaev/dfs/cli/ScanCommand.java`: the `Clock`, the `scan` and `folder` lines, the reshaped `copies`.
- `cli/src/main/java/us/pochaev/dfs/cli/ScanRunner.java`: passes a `Clock` to `ScanCommand`.
- `cli/src/main/java/us/pochaev/dfs/cli/TextReport.java`: the header.
- `cli/src/test/java/us/pochaev/dfs/cli/`: `ScanCommandTest`, `TextReportTest`, `ScanRunnerTest`.
- `tools/analysis/overlaps.sql`: reads format version 2, uses the `folder` totals and stops roll-up at the `scan` roots (decision 6).
- `CLAUDE.md`: decision 1 (overlaps are computed by an analysis tool from the export) and the JSONL description (line order, the version rule, the pointer to DFS-030) (decisions 1, 7).
- `README.md`: the text report example and the JSONL description.

**Out of scope:**
- Computing folder overlaps, in the scanner or the CLI (decision 1; see `docs/design/folder-overlap-analysis.md`)
- The analysis tool itself, beyond updating `overlaps.sql`
- Reading version 1 files in `overlaps.sql`
- Deletion plans and deleting anything (DFS-022, DFS-021)
- Changing `DuplicateSet`, or how duplicates are detected. The pipeline only gains per-folder counting (decision 2).
- Parallel walking (DFS-006)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] The heap benchmark for 100,000 folders is committed `@Disabled`, was run once by hand, and its figure (bytes per folder, total) is recorded in this ticket (decision 10)
- [ ] `tools/analysis/overlaps.sql` runs without error under DuckDB against a version 2 export of the test fixtures, and its `folders` view shows totals. Checked by hand after the pipeline, not by Gradle; the result is noted in this ticket
- [ ] `CLAUDE.md` decision 1 and its JSONL description match decisions 1 and 7; checked by reading
- [ ] `README.md`'s text report example shows the new header; checked by reading

## Open questions

None.
