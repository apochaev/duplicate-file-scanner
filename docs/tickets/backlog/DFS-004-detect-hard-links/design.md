# DFS-004: Detect hard links within candidate groups — design

## Summary

A new stage `HardLinkDetector.detect(List<DuplicateSet>) -> List<DuplicateSet>` in `application`, run after `FullHashConfirmer`, backed by a new secondary port `FileIdentity.isSameFile(Path, Path)` in `infra.port` and its adapter `NioFileIdentity` (`Files.isSameFile`) in `infra.adapter`. It builds sets through DFS-017's 4-argument `DuplicateSet` constructor and `PhysicalCopy`. `domain.model`, `module-info.java` and `FullHashConfirmer` do not change.

Acceptance criteria:
- **AC-1** Each file in path order joins the first copy whose first link `isSameFile` confirms, so all links of one file end up in one `PhysicalCopy`.
- **AC-2** A set that ends up with fewer than two copies is dropped. The `DuplicateSet` constructor would reject it anyway.
- **AC-3** A file that matches no existing copy starts a new single-link `PhysicalCopy`.
- **AC-4** Calls are `identity.isSameFile(file, firstLink)`, made only within one set, files ascending, copies in creation order. The first file of a set is never checked.
- **AC-5** Sets keep their input order and a dropped set is removed. Files are sorted ascending before clustering, which puts copies in first-path order and links in ascending order.
- **AC-6, AC-7** Retired (delivered by DFS-017).
- **AC-8** If `isSameFile` throws `IOException`, the candidate `file` is dropped from its set without trying further copies. The set is dropped if fewer than two copies remain.
- **AC-9** `NioFileIdentity` delegates to `Files.isSameFile`. It is tested on real `@TempDir` files, with a hard link made by `Files.createLink` and `Assumptions` when the link cannot be created.

Placement: stage in `application` (not exported), port in `infra.port`, adapter in `infra.adapter`. Nothing new is exported.

Checklist items not applicable: 1 (consumes a finished list), 5 (DFS-001 collapses repeats, `DuplicateSet` rejects a repeated path), 6 (no tunable), 7 (holds one set at a time), 12 (no content read).

For the gate: when a copy's *first link* itself is unreadable, the design drops the file checked against it, not the link (see Open design notes).

## Public API

- `FileIdentity` (interface) in `infra.port`: `boolean isSameFile(Path a, Path b) throws IOException`. Equal paths are the same file. Throws NPE on null and `IOException` if either file cannot be accessed. Must be thread-safe.
- `NioFileIdentity` in `infra.adapter`: `implements FileIdentity`, no-arg, stateless. Returns `Files.isSameFile(a, b)`.
- `HardLinkDetector` in `application`: `HardLinkDetector(FileIdentity identity)` (NPE on null) and `List<DuplicateSet> detect(List<DuplicateSet> sets)` (NPE on a null list or a null element). Returns an immutable list. Each output set keeps its input `size` and `digest`. The stage ignores the incoming `copies()` and re-derives them from `files()`.

Algorithm per set:
1. Sort `files()` by `Path.compareTo`.
2. For each file after the first, call `identity.isSameFile(file, copy.firstLink.path())` for each copy formed so far, in order, and add the file to the first copy that matches. If none matches, start a new copy. On `IOException`, drop the file at once without checking later copies.
3. If fewer than two copies remain, drop the set. Otherwise build `new DuplicateSet(size, digest, survivingFilesAscending, copies)`.

## Layer placement

- `application`: `HardLinkDetector`, tested in `HardLinkDetectorTest` against a hand-written fake `FileIdentity`. The fake is told which path pairs are the same file (symmetric) and which paths are unreadable (it throws `IOException` when either argument is one). It records every call as an ordered `(a, b)` list for AC-4.
- `domain.model` (exported, added to `module-info.java`): none. The stage uses DFS-017's `DuplicateSet` (4-argument canonical constructor) and `PhysicalCopy` unchanged.
- `infra.port` / `infra.adapter`: `FileIdentity` / `NioFileIdentity`, tested in `NioFileIdentityTest` on real files in a `@TempDir`.

## Checklist disposition

1. Accumulation and merging: not applicable. The stage consumes `FullHashConfirmer`'s finished `List<DuplicateSet>`, so there is nothing to merge.
2. Inter-stage currency invariants: carried by DFS-017's constructors: at least two copies, sizes match, `copies` partition `files`, immutable copies. The stage only builds sets that satisfy them and drops the rest before construction.
3. Deterministic output order: sets keep their input order and dropped sets are removed. Within a set, files ascend, copies are ordered by first link and links ascend. Sorting before clustering yields all three.
4. Immutable results: `List.copyOf` for the returned list. `DuplicateSet` and `PhysicalCopy` store immutable copies.
5. Repeated input: not applicable. DFS-001 already collapses repeated paths, and `DuplicateSet` rejects a path listed twice.
6. Configuration and its defaults: not applicable. There is no tunable.
7. Memory or performance, measured: not applicable. The stage holds one set at a time and reads attributes, not content. Calls per set are at most files × copies.
8. Groups of one are dropped: a set left with fewer than two physical copies is dropped. This covers all links of one file (AC-2) and drops after an IOException (AC-8).
9. Path-order processing: files are checked in ascending path order (AC-4). There is no size threshold because no content is read.
10. Failure mid-stage: the file whose check throws (the candidate) is dropped and the rest of the set continues (AC-8). There is no report until DFS-005's listener exists. See Open design notes for an unreadable first link.
11. New secondary ports: `FileIdentity` has one pairwise method (a key-based port was rejected in the ticket because `fileKey()` is null on Windows). The stage is tested with a fake, and only `NioFileIdentity` is tested on real files.
12. Large and near-duplicate fixtures: not applicable. No content is read.
13. Reused across concurrent scans: stateless apart from the final port reference. One instance may serve concurrent scans if the port is thread-safe, and `NioFileIdentity` is.
14. Input/output currency named: `List<DuplicateSet>` in and out, named in the ticket's Context.

## Configuration

- None. The stage has no tunable.

## Open design notes

- **The candidate is dropped when a check fails.** `isSameFile(file, firstLink)` cannot say which path failed. The stage drops `file`, the one being placed, and never re-checks an existing copy's first link. As a result, if a copy's first link becomes unreadable after the scan, every later file checked against it is dropped instead. This errs toward reporting fewer copies, never more, so it cannot break "never delete the last copy", and delete-time re-verification catches the unreadable keeper anyway. For AC-8's scenarios, the test-author should make the unreadable file a non-first path, so it is checked as a candidate and never becomes a first link.
- **The constructor is a throwing stub too.** The `FilterStage` skeleton puts `requireNonNull` in the stub constructor. Here the constructor throws `not implemented: DFS-004` (the Accumulator skeleton's L-013 advice), so a null-argument test fails red rather than passing against the stub. The implementer replaces it with `this.identity = Objects.requireNonNull(identity, "identity")`.
- **Wiring.** The stage is not wired into a pipeline or factory. That is scan orchestration (DFS-005).

## Stubs

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/HardLinkDetector.java` (constructor, `detect`), `.../infra/port/FileIdentity.java` (interface, no body), `.../infra/adapter/NioFileIdentity.java` (`isSameFile`).
- Each throws `new UnsupportedOperationException("not implemented: DFS-004")` per D2 / CLAUDE.md decision 5.
- Commit: to be filled in after the design commit.
