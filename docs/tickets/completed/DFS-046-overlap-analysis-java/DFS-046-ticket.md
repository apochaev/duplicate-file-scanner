---
id: DFS-046
title: Compute folder overlaps from a scan result in the server
status: done
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: []
touches:
  - server/src/main/java/us/pochaev/dfs/server/analysis
  - server/src/test/java/us/pochaev/dfs/server/analysis
  - server/src/test/java/us/pochaev/dfs/server/ArchitectureTest.java
  - docs/design/folder-overlap-analysis.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-02
completed: 2026-11-02
---

# DFS-046: Compute folder overlaps from a scan result in the server

## Story

As the developer of the DFS UI
I want a tested Java implementation of the folder overlap analysis that works on a completed `ScanResult`
So that the Overlaps API (DFS-048) and the Overlaps tab (DFS-050) can answer "can this folder go?" and "where are the copies?" without the CLI export and the SQL tool

## Context

Milestone M-1 ("see the results of a scan"), step 1 of 5 (`docs/design/ui.md`, "Milestones"). Track one is DFS-046 → DFS-048; track two is DFS-047 → DFS-049; they join at DFS-050.

- **CLAUDE.md design decision 1:** the scanner does not compute `LocationOverlap`; an analysis tool does. `ScanResult` records what that needs: `roots`, `folders` (one `FolderTotal(folder, files, bytes)` per folder that directly holds counted files) and `sets` (`DuplicateSet(size, digest, files, copies)`, where each `PhysicalCopy(links)` groups hard-linked paths, and every set has at least two physical copies).
- **`docs/design/ui.md` U5b:** the analysis runs in the `server` subproject, in Java, in its own package, implementing `docs/design/folder-overlap-analysis.md`. JUnit cases are taken from that document's examples so the Java, SQL (`tools/analysis/overlaps.sql`) and JavaScript (`tools/viewer/analysis.js`) implementations agree. The server later answers small paged queries; paging and HTTP are DFS-048, not this ticket.
- **`docs/design/ui.md` D5:** the overlap-analysis package depends on neither web nor controller classes.
- **`folder-overlap-analysis.md` decisions 1–5** are the specification. They are restated as criteria below so this file is complete on its own.
- **Hard links are not duplicates** (CLAUDE.md safety invariant): a path in another folder that is a link of the same `PhysicalCopy` is the same data and never counts as a copy.

## Decisions

1. **`archetype: none`, tests written first** (2026-11-01). No archetype fits: `use-case` is for core ports in `file-scanner`, `endpoint` for HTTP. The work is pure logic fully specified by its tests, as DFS-036 to DFS-040 were built. JUnit tests run red against stubs before the implementation; `verification.md` records the red run and the commit it preceded.
2. **Shape** (2026-11-01). A new package `us.pochaev.dfs.server.analysis`:
   - `OverlapAnalysis.of(ScanResult)` computes everything once and returns an immutable object; it is thread-safe to read.
   - `List<FolderSummary> summaries()`: every listed summary, in the fixed order (AC-9).
   - `Optional<FolderSummary> summary(Path folder)`: the summary of any folder from a root down, also one not listed by `summaries()` (zeros for its copy figures); empty for a folder outside every root.
   - `List<FolderPair> pairs()`: every listed pair between immediate folders, in the fixed order (AC-9).
   - `FolderPair pair(Path x, Path y)`: one pair for any two folders, each counted recursively; `x` and `y` may come in either order.
   - Records: `FolderSummary(folder, totalFiles, totalBytes, filesWithCopyElsewhere, bytesWithCopyElsewhere, internalDuplicateFiles, internalBytes)`; `PairSide(folder, filesWithCopy, totalFiles, bytesWithCopy, totalBytes)` with `boolean full()` (`totalFiles > 0 && filesWithCopy == totalFiles`: a side with no files is never full); `FolderPair(a, b)` with `a.folder() < b.folder()`.
   - Paths are compared with `Path.compareTo` and matched with `Path.startsWith` after `normalize()`, so the platform's rules apply (case-insensitive on Windows).
3. **Counting units** (2026-11-01). A file is a path (a `FileEntry`), as `FolderTotal.files` counts it. Totals come from `ScanResult.folders`, summed up the tree. A file "with a copy elsewhere" counts once however many copies it has.
4. **Refused queries are exceptions** (2026-11-01). `pair(x, y)` throws `IllegalArgumentException` when `x` equals `y`, one contains the other, or either lies outside every root; DFS-048 checks these first and maps them to HTTP errors, so reaching the exception is a caller bug.
5. **A scale budget is a criterion** (2026-11-01). `folder-overlap-analysis.md` leaves scale open (pairs grow quadratically with the folders a set spans). A synthetic scan the size of the user's 3.1 TB drive (150,000 files, 9,000 folders) plus one set spread over 500 folders must analyse in under 10 seconds; the measured time and pair count go into the design doc's "Open" section, which then records scale as measured.

## Acceptance criteria

The full ID of each criterion is `DFS-046.AC-<n>`.

- **AC-1** Each folder's summary counts how many of its files have a copy in any other folder, so coverage spread over several folders shows: a folder of 1,000 files, 600 with a copy in folder X and 400 with a copy in folder Y, shows 1,000 of 1,000 with a copy elsewhere, while no pair with it shows more than 600.
- **AC-2** A pair between two folders is listed once, with `a` before `b` by path, and carries both directions: each side has that folder's files with a copy in the other folder, its total files, and their bytes; `full()` is true exactly when that side has at least one file and every one of them has a copy.
- **AC-3** A file with copies in n folders counts in each of the n(n−1)/2 pairs those folders form, and once in each of those folders' summaries.
- **AC-4** A path in another folder that is a hard link of the same physical copy never counts: not as a copy elsewhere in a summary, not on a pair side, and a set whose copies differ only by hard links adds nothing.
- **AC-5** For a folder's summary, a copy elsewhere means a copy outside its subtree: two identical files in sibling folders A/x and A/y each have a copy elsewhere in the summaries of A/x and A/y, and none in the summary of A.
- **AC-6** For each set with k ≥ 2 physical copies inside a folder's subtree, the summary counts k − 1 internal duplicate files and (k − 1) × size internal bytes, whether or not the set also has copies outside the subtree; these figures are separate from the copy-elsewhere figures.
- **AC-7** `pairs()` lists only pairs between folders that directly hold the files; `pair(x, y)` answers any two folders, each counted recursively, in either argument order, and throws `IllegalArgumentException` when the folders are equal, one contains the other, or either lies outside every root.
- **AC-8** Summaries cover every folder from each root down, including folders that hold only subfolders, counting the whole subtree, and nothing above a root; `summary(folder)` answers any such folder and is empty outside the roots.
- **AC-9** `summaries()` lists a folder only when it has a file with a copy elsewhere or an internal duplicate, ordered by bytes with a copy elsewhere (largest first), then internal bytes (largest first), then path; `pairs()` lists a pair only when a side is non-zero, ordered by the larger side's bytes with a copy (largest first), then `a`, then `b`.
- **AC-10** A synthetic scan result of 150,000 files in 9,000 folders, with 50,000 sets of two or three copies plus one set spread over 500 folders, is analysed by `OverlapAnalysis.of` in under 10 seconds; the measured time and pair count are recorded in `folder-overlap-analysis.md`.
- **AC-11** The server's `ArchitectureTest` fails when a class in `us.pochaev.dfs.server.analysis` depends on Spring or on any other `us.pochaev.dfs.server` package; the package may use only the JDK and `us.pochaev.dfs.scanner.domain..`.
- **AC-12** `docs/design/folder-overlap-analysis.md` no longer reads "not scheduled" and names DFS-046, the package `us.pochaev.dfs.server.analysis` and its entry point `OverlapAnalysis.of`.

## Scenarios

AC-1 to AC-11 are JUnit tests in `server/src/test/java/us/pochaev/dfs/server` (the analysis tests in its `analysis` package), building `ScanResult` values in memory with `/`-separated absolute paths under one root `/r` (on Windows, under a drive root), without touching disk. AC-10 runs as an ordinary test with an `assertTimeout` of 10 seconds. AC-12 is read and recorded in `verification.md`.

```gherkin
Feature: Folder overlap analysis

  Background:
    Given a scan result with root /r
    And every file is 1 MB unless stated

  @AC-1
  Scenario: Coverage spread over two folders
    Given /r/Old/Pictures holds 1,000 files
    And 600 of them have one copy each in /r/Photos/2019
    And the other 400 have one copy each in /r/Backup/2019
    When the result is analysed
    Then the summary of /r/Old/Pictures shows 1,000 total files and 1,000 with a copy elsewhere
    And the pair of /r/Old/Pictures and /r/Photos/2019 shows 600 on the /r/Old/Pictures side
    And the pair of /r/Backup/2019 and /r/Old/Pictures shows 400 on the /r/Old/Pictures side

  @AC-2
  Scenario: One pair carries both directions
    Given /r/Photos/2019 holds 400 files
    And /r/Backup/2019 holds 340 files, each a copy of a different file in /r/Photos/2019
    When the result is analysed
    Then pairs() holds exactly one pair of these folders, with a = /r/Backup/2019 and b = /r/Photos/2019
    And side a shows 340 of 340 files with a copy, 340 MB, and full() is true
    And side b shows 340 of 400 files with a copy, 340 MB of 400 MB, and full() is false

  @AC-2
  Scenario: A side with no files is never full
    Given /r/Photos/2019 holds 3 files
    And /r/Nothing lies inside /r and the result holds no file under it
    When pair(/r/Nothing, /r/Photos/2019) is called
    Then side a is /r/Nothing with 0 of 0 files with a copy, and full() is false
    And side b shows 0 of 3 files with a copy

  @AC-3
  Scenario: A file with copies in three folders
    Given /r/A/x.jpg, /r/B/x.jpg and /r/C/x.jpg are one set of three physical copies
    When the result is analysed
    Then pairs() holds the pairs (A, B), (A, C) and (B, C), each with 1 file with a copy on each side
    And the summaries of /r/A, /r/B and /r/C each show 1 file with a copy elsewhere

  @AC-4
  Scenario: Hard links in other folders are not copies
    Given /r/A/x.jpg and /r/B/x.jpg are two links of one physical copy
    And /r/C/x.jpg is a second physical copy of the same content
    When the result is analysed
    Then pairs() holds (A, C) and (B, C) with 1 file with a copy on each side
    And pairs() holds no pair of /r/A and /r/B
    And the summary of /r/A shows 1 file with a copy elsewhere, because of /r/C

  @AC-4
  Scenario: A set of hard links alone adds nothing
    Given /r/A/y.jpg and /r/B/y.jpg are two links of one physical copy
    And the result holds no other copy of y.jpg
    When the result is analysed
    Then no summary and no pair counts y.jpg

  @AC-5
  Scenario: A copy elsewhere means outside the subtree
    Given /r/Backup/2019/a/x.jpg and /r/Backup/2019/b/x.jpg are two physical copies of one set
    When the result is analysed
    Then the summaries of /r/Backup/2019/a and /r/Backup/2019/b each show 1 file with a copy elsewhere
    And summary(/r/Backup/2019) shows 0 files with a copy elsewhere and 1 internal duplicate

  @AC-6
  Scenario: Internal duplicates with no copy elsewhere
    Given /r/Photos/2019/IMG_001.jpg and /r/Photos/2019/IMG_001 (1).jpg are identical, 25 MB each
    And no other folder holds that content
    When the result is analysed
    Then the summary of /r/Photos/2019 shows 0 files with a copy elsewhere, 1 internal duplicate and 25 MB internal bytes
    And pairs() holds no pair of /r/Photos/2019 with itself

  @AC-6
  Scenario: Internal duplicates counted alongside copies elsewhere
    Given /r/P/a.jpg and /r/P/b.jpg and /r/Q/c.jpg are three physical copies of one 10 MB set
    When the result is analysed
    Then the summary of /r/P shows 2 files with a copy elsewhere, 20 MB, and 1 internal duplicate, 10 MB

  @AC-7
  Scenario: Ancestor pairs come only from the query
    Given /r/Backup/2019/x.jpg is a copy of /r/Photos/2019/x.jpg
    And /r/Backup/2020/y.jpg is a copy of /r/Photos/2020/y.jpg
    When the result is analysed
    Then pairs() holds (Backup/2019, Photos/2019) and (Backup/2020, Photos/2020) and no pair of /r/Backup and /r/Photos
    And pair(/r/Photos, /r/Backup) answers a = /r/Backup with 2 of 2 files with a copy, b = /r/Photos with 2 of 2

  @AC-7
  Scenario Outline: Refused pair queries
    When pair(<x>, <y>) is called
    Then it throws IllegalArgumentException

    Examples:
      | x                | y                   |
      | /r/Backup        | /r/Backup           |
      | /r/Backup        | /r/Backup/2019      |
      | /r/Backup/2019   | /r/Backup           |
      | /r/Backup        | /elsewhere/folder   |

  @AC-8
  Scenario: Summaries roll up to the root and stop there
    Given /r/Media/Old/Pictures/a.jpg is a copy of /r/Photos/a.jpg
    And /r/Media and /r/Media/Old hold no files directly
    When the result is analysed
    Then summaries() holds /r/Media/Old/Pictures, /r/Media/Old, /r/Media and /r/Photos, each with 1 total file and 1 with a copy elsewhere
    And summaries() holds no folder above /r
    And summary(/r) shows 2 total files and 0 with a copy elsewhere
    And summary(/elsewhere) is empty

  @AC-9
  Scenario: Fixed order and only non-empty entries
    Given /r/A/one.bin (30 MB) has a copy in /r/B
    And /r/C/two.bin (50 MB) has a copy in /r/D
    And /r/E/p.bin and /r/E/q.bin are identical (40 MB), with no copy elsewhere
    And /r/F/u.bin is unique
    When the result is analysed
    Then summaries() lists /r/C, /r/D, /r/A, /r/B, /r, /r/E in that order, and no /r/F
    # /r has no copy outside itself but 120 MB of internal bytes (30 + 50 + 40), so it precedes /r/E (40 MB)
    And pairs() lists (C, D) before (A, B), and no pair with /r/E or /r/F

  @AC-10
  Scenario: The analysis is fast enough for a real drive
    Given a generated scan result of 150,000 files in 9,000 folders
    And 50,000 sets of two or three physical copies in different folders
    And one set of 500 physical copies in 500 different folders
    When OverlapAnalysis.of runs on it
    Then it returns within 10 seconds
    And pairs() holds at least 124,750 pairs

  @AC-11
  Scenario: The analysis package stays free of web code
    Given a class in us.pochaev.dfs.server.analysis that imports a Spring type or a class of us.pochaev.dfs.server.scan
    When the server's ArchitectureTest runs
    Then it fails, naming that class

  @AC-12
  Scenario: The design docs name the implementation
    Then folder-overlap-analysis.md names DFS-046, us.pochaev.dfs.server.analysis and OverlapAnalysis.of
    And it records the measured scale
```

## Scope

**Touches:**
- `server/src/main/java/us/pochaev/dfs/server/analysis`: the new package (`OverlapAnalysis`, `FolderSummary`, `PairSide`, `FolderPair`).
- `server/src/test/java/us/pochaev/dfs/server/analysis`: the analysis tests and a builder for in-memory scan results.
- `server/src/test/java/us/pochaev/dfs/server/ArchitectureTest.java`: the AC-11 rule, proven with a violating fixture class as `GuardViolationFixture` does.
- `docs/design/folder-overlap-analysis.md`: status line, the package and the measured scale.
- Not `docs/design/ui.md`: DFS-047 edits it in parallel; DFS-048 names the package in U5b.

**Out of scope:**
- HTTP endpoints, paging and caching the analysis per scan (DFS-048).
- Any screen (DFS-050).
- Changes to `tools/analysis/overlaps.sql` or `tools/viewer/analysis.js`, and a test that runs all three implementations on one export.
- Any change to `file-scanner` (everything needed is in `ScanResult`).
- A verdict combining pairs ("both folders can go"): never computed (`folder-overlap-analysis.md` decision 1); only the deletion plan check (DFS-022) can say what is safe.

## Definition of done

- [x] Every scenario above is an automated test and passes, except AC-12 (read), recorded in `verification.md` (22 of 22 pass: 17 in `OverlapAnalysisTest`, 1 scale, 4 in `ArchitectureTest`)
- [x] The tests ran red before the implementation; the failing run and the commit it preceded are recorded in `verification.md` (decision 1) (18 of 22 failed on the stub; tests committed as `c285230`)
- [x] Each test's `@DisplayName` starts with its criterion ID (`DFS-046.AC-n`) (the parameterized AC-7 cases' names too)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests) (2026-11-02)
- [x] The measured AC-10 time on the user's machine is recorded in `folder-overlap-analysis.md` (940 ms, 224,349 pairs)
- [x] No files changed outside the paths listed under Touches (listed in `verification.md`, "Touches")
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
