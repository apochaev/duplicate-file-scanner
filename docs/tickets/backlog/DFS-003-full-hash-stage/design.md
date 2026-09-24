# DFS-003: Confirm duplicates with full SHA-256 hash — design

## Summary

Stage 3 of the detection pipeline: `FullHashConfirmer` (`application`) turns DFS-002's `List<CandidateGroup>` into `List<DuplicateSet>`, the new exported result record in `domain.model`. `NioContentHasher.fullHash` gets its real SHA-256 body. `ContentHasher`'s signature and docs are unchanged; `module-info.java` is unchanged (`domain.model` is already exported).

- **AC-1** identical files form one set with group size and shared digest: `FullHashConfirmer.confirm` groups members by `ContentHasher.fullHash` digest into `DuplicateSet(size, digest, files)`.
- **AC-2** content differing anywhere, including the middle byte, never shares a set: grouping is on the full-content digest only.
- **AC-3** a digest shared by fewer than two members yields no set: `confirm` drops it (checklist 8).
- **AC-4** full hashes requested in ascending path order: `confirm` sorts each group's files by `Path.compareTo` before hashing.
- **AC-5** output order: each group replaced in place by its sets, ordered by smallest path, files ascending (as `PartialHashFilter`).
- **AC-6** `DuplicateSet`'s compact constructor rejects null/blank digest, null files/entry, fewer than two files, size mismatch; stores `List.copyOf`.
- **AC-7** an `IOException` from `fullHash` drops only that member; not reported until DFS-005.
- **AC-8** `NioContentHasher.fullHash` streams the whole file through JDK `MessageDigest` SHA-256, lowercase hex.
- **AC-9** the test-author removes `NioContentHasherTest.fullHashIsStubbedForDfs003` (display name `DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003`) and adds AC-8 tests in that file.
- **AC-10** no size check: the set carries the scan-time `CandidateGroup.size()`, whatever the file size is now.

Placement: stage in `application`, `DuplicateSet` in `domain.model`, no new port; `NioContentHasher` in `infra.adapter`.
Not applicable: checklist 1 (finished list, nothing to merge), 5 (DFS-001 collapsed repeated paths), 6 (no tunable), 7 (one group at a time), 11 (no new port).
Deviation: stage named `FullHashConfirmer.confirm` rather than the skeleton's `filter`, since it changes type (see Open design notes).

## Public API

- `DuplicateSet` in `domain.model`: `record DuplicateSet(long size, String digest, List<FileEntry> files)` — compact constructor enforces AC-6's invariants; no convenience methods.
- `FullHashConfirmer` in `application`: `public FullHashConfirmer(ContentHasher hasher)`; `public List<DuplicateSet> confirm(List<CandidateGroup> groups)` — immutable result, throws `NullPointerException` for a null list or null element.
- `NioContentHasher` in `infra.adapter`: `public String fullHash(Path file) throws IOException` — signature unchanged, body implemented by this ticket.

## Layer placement

- `application`: `FullHashConfirmer` (not exported). `CandidateGroup` stays internal.
- `domain.model` (exported; already listed in `module-info.java`, no change): `DuplicateSet`.
- `infra.port` / `infra.adapter`: no new port; existing `ContentHasher.fullHash` (unchanged), implemented in `NioContentHasher`.

## Checklist disposition

1. Accumulation and merging: not applicable: consumes a finished `List<CandidateGroup>`, nothing to merge.
2. Inter-stage currency invariants: `DuplicateSet`'s constructor enforces non-blank digest, non-null files without null entry, at least two files, every entry's size equal to `size`, and stores `List.copyOf(files)`; the `CandidateGroup` input already carries the same invariants.
3. Deterministic output order: input group order kept; each group replaced in place by its sets ordered by smallest path; files in each set ascending by `Path.compareTo`. Sorting is the stage's job, not the record's (as for `CandidateGroup`).
4. Immutable results: `DuplicateSet` is a record with an immutable `files` copy; `confirm` returns `List.copyOf`.
5. Repeated input: not applicable: DFS-001's `SizeIndex` already collapsed repeated paths before stage 1's output.
6. Configuration and its defaults: not applicable: no tunable, a full hash reads the whole file.
7. Memory or performance, measured: not applicable: one group's digests held at a time; no measurement needed.
8. Groups of one are dropped: a digest shared by fewer than two members produces no set, including after an unreadable member is dropped.
9. Path-order processing: members hashed in ascending path order within each group. No skip threshold here: every group is fully hashed; the 128 KB skip lives in `PartialHashFilter`, so small groups arrive here unchanged.
10. Failure mid-stage: unreadable (`IOException`) member dropped from its group, not the whole group; not reported until DFS-005's listener. Size change: deliberately not checked — the file is hashed as it is now, and the set carries the scan-time size (AC-10); execution-time re-verification against the `FileEntry` baseline is the safety net (ticket Context).
11. New secondary ports: not applicable: reuses `ContentHasher.fullHash` (declared by DFS-002). The stage is tested against a hand-written fake `ContentHasher`; only `NioContentHasher` is tested on real temp files.
12. Large and near-duplicate fixtures: 300 KB files differing only in the middle byte, generated in code (in `@TempDir` for the adapter tests; digests come from the fake for stage tests). Nothing committed to `fixtures/`.
13. Reused across concurrent scans: stateless apart from the final `hasher` reference; one instance may serve concurrent scans given a thread-safe hasher (`NioContentHasher` uses a fresh `MessageDigest` per call).
14. Input/output currency named: named by the ticket: `List<CandidateGroup>` in, `List<DuplicateSet>` out.

## Configuration

- Tunable(s) this stage reads, their default, and where they are enforced: none.

## Open design notes

- Naming: `FullHashConfirmer.confirm` instead of the skeleton's `<ClassName>.filter`, because the stage changes currency (`CandidateGroup` in, `DuplicateSet` out) rather than filtering one type.
- `DuplicateSet` does not sort its files, mirroring `CandidateGroup`; the stage supplies ascending path order. A test constructing a `DuplicateSet` directly should not expect it to reorder.
- `NioContentHasher.fullHash` must stream in fixed-size chunks, never read the whole file into memory.
- Files the test-author reads (beyond this doc and the ticket): `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasherTest.java` — remove the method `fullHashIsStubbedForDfs003` (AC-9), update the class Javadoc that describes `fullHash` as a DFS-003 stand-in, and add the AC-8 tests there. For the stage tests, follow the fake-hasher style of `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/PartialHashFilterTest.java` (new `FullHashConfirmerTest.java` beside it). `DuplicateSet` tests go in a new `file-scanner/src/test/java/us/pochaev/dfs/scanner/domain/model/DuplicateSetTest.java` (the directory does not exist yet).

## Stubs

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/DuplicateSet.java` (new; compact constructor), `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/FullHashConfirmer.java` (new; `confirm`), `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasher.java` (`fullHash` re-stubbed from DFS-002's permanent message).
- Each throws `new UnsupportedOperationException("not implemented: DFS-003")` per D2 / CLAUDE.md decision 5. `FullHashConfirmer`'s constructor is real (null check only), as in the `FilterStage` skeleton.
- Commit: to be filled in after the design commit.
