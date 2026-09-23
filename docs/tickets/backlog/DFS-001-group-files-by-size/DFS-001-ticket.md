---
id: DFS-001
title: Group scanned files by size
status: in-progress
type: feature
scope: [file-scanner]
archetype: pipeline-stage
priority: high
depends_on: []
touches: [us.pochaev.dfs.scanner.application]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed:
---

# DFS-001: Group scanned files by size

## Story

As a user scanning a location for duplicates
I want files grouped by size, dropping any file whose size is unique
So that only files that could possibly be identical are examined by the more expensive stages

## Context

Stage 1 of the detection pipeline (CLAUDE.md design decision 3): group by size, drop groups of one. Input is the `FileEntry` values the walker emits as `WalkEvent.FileFound`. The walker yields empty files on purpose; size filtering belongs in `application` (see "`FileTreeWalker` is built" in CLAUDE.md). Empty (0-byte) files are excluded by default and a minimum-size setting generalizes this. Test against fakes or in-memory adapters, not the real disk.

**Decided (2026-09-21): a mergeable accumulator, `SizeIndex`, in `application`.** It offers `add(FileEntry)`, `merge(SizeIndex)` and `groups()`. `groups` drops singletons and returns immutable groups, largest first, with paths sorted ascending within each group. The stage must be usable in parallel later (large trees, and DFS-006 walking directories concurrently), and singletons can only be dropped after partial results are merged: a pair whose files landed in different partitions would otherwise be lost. So:
- `SizeIndex` is deliberately not thread-safe. The orchestration (DFS-005) decides threading: one index used sequentially now, one index per worker merged at the end later.
- Output never depends on insertion order, so parallel arrival order cannot change results, and later stages can hash in path order.
- Returned groups are immutable values, so the later stages can process groups on different threads.
- `merge(other)` copies `other`'s entries and leaves `other` unchanged, so two indexes never share mutable state.
- No parallelism is built in this ticket.

**Decided (2026-09-21): `CandidateGroup(long size, List<FileEntry> files)`, a record internal to `application`.** It is the currency between pipeline stages 1 to 3, not public API (the exported result is `DuplicateSet`, DFS-003). It carries `FileEntry` values, not bare paths, because the scan's size and modification time are the baseline for the "unchanged since the scan" re-verification at execution time. The constructor rejects fewer than two files, files whose size differs from `size`, and takes an immutable defensive copy. Sorting is `SizeIndex`'s job.

**Decided (2026-09-21): the minimum size is a constructor argument, `SizeIndex(long minSize)`, with a no-arg constructor defaulting to 1 (empty files excluded).** `add` ignores entries below the minimum, so the rule is enforced inside the stage and a high minimum saves memory on large trees. `merge` rejects an index built with a different minimum. DFS-005's scan config record has its own default of 1 and passes it explicitly.

**Decided (2026-09-21): `groups` returns groups largest first (descending size).** Each group has a distinct size, so this is a total order with no tie-breaker. It is a deterministic canonical order, useful to consumers: starting the longest jobs first shortens a parallel batch, and a cancelled scan leaves its biggest savings in the partial results. A consumer that needs a different order re-sorts.

**Decided (2026-09-21): a plain in-memory `Map<Long, List<FileEntry>>` for the first version, with the memory measured.** A file with a unique size cannot be dropped before the walk ends, so the index holds every entry at or above the minimum until `groups` runs (rough, unmeasured estimate: 250 to 400 bytes per entry, so 2.5 to 4 GB at 10 million files). The API does not commit to a storage layout, so internals can change later without touching callers. If measurement shows a need, each of these is its own ticket: a compact representation (primitive arrays, shared directory prefixes), or a two-pass walk that keeps entries only for colliding sizes.

**Decided (2026-09-21): equal paths are collapsed inside each group when `groups()` runs.** Scanning two locations where one is nested in the other feeds the index the same `FileEntry` twice, which would otherwise form a group of "two copies" that is really one file: a file reported as a duplicate of itself, so deleting one of them would delete the only copy. Because each group is sorted by path, equal paths are adjacent, so this is a linear pass with no extra memory. Two entries with the same path and size but different modification times collapse to the one with the later modification time, which does not depend on insertion order. A group that shrinks below two files is dropped. Not handled here: the same path in two different groups because its size changed between visits (rare; DFS-004's identity check and the delete-time byte compare cover it). DFS-005 should also normalize start locations so nested ones are dropped before walking.

## Acceptance criteria

The full ID of each criterion is `DFS-001.AC-<n>`. Derived from the scenarios and confirmed by the author on 2026-09-21 (CCW-001).

- **AC-1** Files whose size is unique are dropped, and files of equal size form one candidate group, across folders.
- **AC-2** Empty files are excluded by default, and files below a configurable minimum size are excluded.
- **AC-3** Merging partial indexes gives the same groups as a single index, leaves the merged-in index unchanged, and is rejected when the minimum sizes differ.
- **AC-4** The same path added more than once counts once, keeping the later modification time.
- **AC-5** Output is deterministic: largest group first, paths ascending, independent of insertion order.
- **AC-6** Returned groups are immutable.
- **AC-7** Invalid input is rejected: a candidate group's invariants, a negative minimum size and a null entry.

## Scenarios

```gherkin
Feature: Size grouping

  Background:
    Given a size index with default settings (empty files excluded)

  @AC-1
  Scenario: Files with a unique size are dropped
    Given "a.txt" of 10 bytes, "b.txt" of 20 bytes and "c.txt" of 30 bytes are added
    When groups are requested
    Then no candidate groups are produced

  @AC-1
  Scenario: Files of equal size form one candidate group
    Given "a.txt" and "b.txt" of 10 bytes each and "c.txt" of 20 bytes are added
    When groups are requested
    Then one candidate group of size 10 is produced containing "a.txt" and "b.txt"

  @AC-1
  Scenario: Files are grouped across folders
    Given "folder1/a.txt" and "folder2/b.txt" of 10 bytes each are added
    When groups are requested
    Then one candidate group is produced containing both paths

  @AC-2
  Scenario: Empty files are excluded by default
    Given two files of 0 bytes are added
    When groups are requested
    Then no candidate groups are produced

  @AC-2
  Scenario: A minimum size excludes smaller files
    Given an index created with a minimum size of 100 bytes
    And two files of 99 bytes and two files of 100 bytes are added
    When groups are requested
    Then one candidate group is produced containing only the two 100-byte files

  @AC-3
  Scenario: Merging indexes with different minimum sizes is rejected
    Given index 1 created with a minimum size of 1 byte and index 2 created with a minimum size of 100 bytes
    When index 2 is merged into index 1
    Then the merge is rejected

  @AC-3
  Scenario: Merging partial indexes equals one index
    Given index 1 holds only "folder1/a.txt" of 10 bytes and index 2 holds only "folder2/b.txt" of 10 bytes
    And neither index alone produces a candidate group
    When index 2 is merged into index 1
    Then the groups equal those of a single index that received both files

  @AC-3
  Scenario: Merging leaves the other index unchanged
    Given index 2 holds "b.txt" of 10 bytes and has been merged into index 1
    When more files are added to index 1
    Then the groups of index 2 are unchanged

  @AC-4
  Scenario: The same path added twice counts once
    Given "a.txt" of 10 bytes is added twice
    When groups are requested
    Then no candidate groups are produced

  @AC-4
  Scenario: A repeated path does not inflate a group
    Given "a.txt" of 10 bytes is added twice and "b.txt" of 10 bytes is added once
    When groups are requested
    Then one candidate group is produced containing "a.txt" and "b.txt" once each

  @AC-3
  Scenario: Merging an index with an equal one changes nothing
    Given an index holding "a.txt" and "b.txt" of 10 bytes each
    When another index holding the same two files is merged into it
    Then its groups equal those before the merge

  @AC-4
  Scenario: Equal paths keep the later modification time
    Given "a.txt" of 10 bytes modified at 10:00, "a.txt" of 10 bytes modified at 11:00, and "b.txt" of 10 bytes are added
    When groups are requested
    Then the group holds "a.txt" with the 11:00 modification time
    And the result is the same whichever order the entries were added in

  @AC-5
  Scenario: Output does not depend on insertion order
    Given the same set of files added in two different orders
    When groups are requested from each index
    Then both produce the same groups
    And the paths within each group are sorted ascending

  @AC-5
  Scenario: Groups are returned largest first
    Given two files each of 10 bytes, 50 bytes and 30 bytes are added
    When groups are requested
    Then the groups are returned in the order 50, 30, 10 bytes

  @AC-6
  Scenario: Returned groups are immutable
    Given groups were requested from an index
    When more files are added to the index afterwards
    Then the previously returned groups are unchanged

  @AC-7
  Scenario: A candidate group enforces its invariants
    Given a single file, or two files of different sizes, or files whose size differs from the group's size
    When a candidate group is constructed from them
    Then construction is rejected

  @AC-7
  Scenario: Invalid arguments are rejected
    Given a negative minimum size, or a null entry
    When the index is created or the entry is added
    Then it is rejected
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application` (main and test sources): `SizeIndex` and `CandidateGroup`

**Out of scope:**
- Hashing of any kind, hard-link detection
- Threading of the index (DFS-005), parallel walking (DFS-006), and any CLI work
- Scan orchestration (`StartScan`, executor, progress)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] The empty-file default and the minimum-size setting are each covered by a test
- [ ] Heap for 1 million synthetic entries is measured and the figure recorded in this ticket before it is closed. Measured (implementer, 2026-09-22): 1,000,000 `FileEntry` values added to one `SizeIndex` (half forming 500,000 two-file groups, half unique sizes dropped), `-Xmx8g`, `System.gc()` before/after: retained heap grew by ~213,725,152 bytes, about 213 bytes/entry, consistent with the ticket's 250-400 byte/entry estimate. `groups()` over the full index took ~188 ms. Measured with a throwaway `HeapMeasure` class run directly against `file-scanner`'s compiled classes (not committed, not part of the build).
- [ ] The Javadoc of `SizeIndex` states that it is not thread-safe and that `merge` is how partial indexes are combined

## Open questions

None.
