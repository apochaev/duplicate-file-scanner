# DFS-001: Group scanned files by size — design

## Summary

Two types in `application`, nothing exported, no `module-info.java` change, no new port.
`SizeIndex` is a mergeable accumulator that is not thread-safe and turns `FileEntry` into `List<CandidateGroup>`.
`CandidateGroup(long size, List<FileEntry> files)` is the record passed between stages 1 to 3.

- **AC-1** (unique sizes dropped, equal sizes grouped across folders): `SizeIndex.groups()` groups by `FileEntry.size()` whatever the directory, and drops every group with fewer than two files.
- **AC-2** (empty files excluded, configurable minimum): `SizeIndex()` defaults to `DEFAULT_MIN_SIZE = 1`, `SizeIndex(long minSize)` sets the minimum, and `add` ignores any entry below it.
- **AC-3** (merge gives the same result, leaves `other` unchanged, rejects a different minimum): `SizeIndex.merge(SizeIndex)` copies the entries, throws `IllegalArgumentException` when the minimums differ, and drops singletons only in `groups()`.
- **AC-4** (repeated path counts once, later mtime wins): `groups()` sorts each group by path, then merges adjacent equal paths into the entry with the latest `lastModified`.
- **AC-5** (deterministic order): groups are ordered by size, largest first, and paths within a group by `Path.compareTo`, ascending. Insertion order has no effect.
- **AC-6** (immutable results): `groups()` returns an unmodifiable list of `CandidateGroup`s, each with a `List.copyOf` file list. It is a snapshot that later `add` or `merge` calls do not change.
- **AC-7** (invalid input rejected): the `CandidateGroup` constructor rejects fewer than two files, a size mismatch, a null list and null elements. `SizeIndex(long)` rejects a negative minimum (IAE). `add` and `merge` reject null (NPE).

Not applicable: items 9 (path-order processing), 10 (failure mid-stage), 11 (new ports) and 12 (large and near-duplicate fixtures), because this stage reads no file content.
Deferred to the implementer: item 7, measuring the heap for 1 million entries and recording it in the ticket.
Open notes for the gate: `merge(this)` is a no-op, `CandidateGroup` does not reject duplicate paths, and path order follows `Path.compareTo`, which depends on the platform.

## Public API

- `SizeIndex` in `us.pochaev.dfs.scanner.application`: `public static final long DEFAULT_MIN_SIZE = 1`; `SizeIndex()`; `SizeIndex(long minSize)`; `void add(FileEntry entry)`; `void merge(SizeIndex other)`; `List<CandidateGroup> groups()`.
  `groups()` does not clear the index, so it can be called again after more `add` or `merge` calls. Exceptions: negative `minSize` gives `IllegalArgumentException`, a null `entry` or `other` gives `NullPointerException`, and a different minimum on `merge` gives `IllegalArgumentException`.
- `CandidateGroup` in `us.pochaev.dfs.scanner.application`: `record CandidateGroup(long size, List<FileEntry> files)`.
  The compact constructor rejects a null `files` (NPE), a null element (NPE, via `List.copyOf`), fewer than two files (IAE) and any file whose `size()` differs from `size` (IAE). It stores `List.copyOf(files)` in the order given.

## Layer placement

- `application`: `SizeIndex`, `CandidateGroup` (both `public`, since later application stages consume them. The package is not exported).
- `domain.model` (exported, added to `module-info.java`): none. The input `FileEntry` already exists there.
- `infra.port` / `infra.adapter`: none.

## Checklist disposition

1. Accumulation and merging: `add`/`merge`/`groups`. The Javadoc states that `SizeIndex` is not thread-safe and that `merge` combines partial indexes. `merge` copies `other`'s entries, never its internal collections, and leaves `other` unchanged. Singletons are dropped only in `groups()`, after all merges. Output never depends on arrival order.
2. Inter-stage currency invariants: `CandidateGroup` has at least two files, each file's size equals the group's size, it rejects a null list or element, and it takes an immutable defensive copy. It does not sort; `SizeIndex` does.
3. Deterministic output order: groups by `size` descending (sizes are distinct across groups, so this is a total order), and files by `path` ascending under `Path.compareTo`.
4. Immutable results: the outer list is unmodifiable and each group's `files` comes from `List.copyOf`. `FileEntry` is a record. `groups()` is a snapshot.
5. Repeated input: within a group, sorting by path makes equal paths adjacent. They are merged into the one with the greatest `lastModified`, whatever the insertion order, and a group that drops below two files is removed. The same path appearing in two different size groups is out of scope, as the ticket says (DFS-004 and the delete-time compare cover it).
6. Configuration and its defaults: `minSize` defaults to 1. It is enforced inside `add`, so entries below it are never stored. It reaches the stage as a constructor argument, and later from DFS-005's config record. A negative value is rejected, and 0 is allowed (includes empty files).
7. Memory or performance, measured: the plain `Map<Long, List<FileEntry>>` holds every entry at or above the minimum until `groups()`. The implement/verify phase measures heap for 1 million synthetic entries with a one-off measurement, not an asserted unit test, and records the figure in the ticket before it closes (DoD).
8. Groups of one are dropped: in `groups()`, both before and after equal paths are merged.
9. Path-order processing: not applicable: this stage reads no file content. The ascending path order in its output is what lets DFS-002 and DFS-003 hash in path order.
10. Failure mid-stage: not applicable: the stage does no I/O and only reads sizes the walker already captured. A file that changes after the walk is handled by DFS-002/003 and the delete-time byte compare.
11. New secondary ports: not applicable: no port. The stage is pure in-memory logic, tested directly with `FileEntry` values built in code (no disk).
12. Large and near-duplicate fixtures: not applicable: grouping depends only on `size`, not on content. That fixture belongs to DFS-002/003.
13. Reused across concurrent scans: all state is per instance and nothing is static except the constant. One `SizeIndex` belongs to one scan, or to one worker's partition of it.
14. Input/output currency named: the ticket names it. `FileEntry` (from `WalkEvent.FileFound`) goes in, and `CandidateGroup` comes out.

## Configuration

- `minSize` (bytes), default `SizeIndex.DEFAULT_MIN_SIZE = 1`, enforced in `SizeIndex.add`. `merge` requires equal minimums.

## Open design notes

- **`merge(this)`** is not covered by the ticket. It is specified as a no-op, which the collapse already implies. The implementer must special-case it or snapshot the entries first, to avoid mutating a collection while iterating over it.
- **Duplicate paths inside `CandidateGroup`** are not rejected by its constructor. The ticket fixes that constructor's invariants, and collapsing is `SizeIndex`'s job. Rejecting duplicates would add a defensive check against "a file reported as a duplicate of itself". Decide at the gate whether to add it; it would need a scenario.
- **Path order is platform-defined.** `Path.compareTo` and `Path.equals` are case-insensitive on Windows and case-sensitive on Unix. Tests should use paths whose order does not depend on case.
- **Stubs omit precondition checks** (no `Objects.requireNonNull` before the throw), per L-013. Otherwise the null-entry and null-`other` tests would pass against the stub instead of failing red. The implementer adds them.

## Stubs

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/SizeIndex.java`, `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/CandidateGroup.java`.
- Every constructor and method body, including `CandidateGroup`'s compact constructor, throws `new UnsupportedOperationException("not implemented: DFS-001")`. `SizeIndex()` delegates to `SizeIndex(long)`, which throws.
- Commit: filled in by the orchestrator after the design commit.
