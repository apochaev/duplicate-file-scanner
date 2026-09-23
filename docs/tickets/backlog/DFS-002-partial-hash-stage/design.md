# DFS-002: Filter candidates by head-and-tail partial hash — design

## Summary

Stage 2 is `PartialHashFilter` (in `application`, not exported): a stateless filter from `List<CandidateGroup>` to `List<CandidateGroup>`, backed by a new `ContentHasher` port (`infra.port`) and its SHA-256 adapter `NioContentHasher` (`infra.adapter`). Nothing new goes in `domain.model`, and `module-info.java` does not change.

- **AC-1** (a head or tail difference separates files): `filter` groups members by `ContentHasher.sampleHash` digest; digests that differ end up in different groups.
- **AC-2** (a middle-only difference survives): `sampleHash` reads only the first and last `sampleSize` bytes, so a middle difference yields equal digests.
- **AC-3** (identical files survive): equal digests keep the files in one `CandidateGroup`.
- **AC-4** (small files bypass the sample): a group with `size <= skipThreshold` is returned as the same instance and the hasher is never called for it.
- **AC-5** (groups of one are dropped): a split-off subgroup with fewer than two members is dropped (and `CandidateGroup` could not hold one anyway).
- **AC-6** (path order): members are sorted by `Path.compareTo` ascending before `sampleHash` is called.
- **AC-7** (an unreadable file is dropped, not its group): an `IOException` from `sampleHash` drops only that member, and the rest of the group is still compared. There is no report until DFS-005.
- **AC-8** (`fullHash` is stubbed for DFS-003): `NioContentHasher.fullHash` ends up throwing `UnsupportedOperationException("full hash is implemented by DFS-003")`. That message deliberately does not use the `not implemented:` prefix.
- **AC-9** (configurable sample size and threshold): `PartialHashFilter(hasher, int sampleSize, long skipThreshold)`, with `PartialHashFilter(hasher)` defaulting to 64 KB and 128 KB. The sample size is passed to the hasher on every call.

Checklist items not applicable: 1 (no accumulation), 5 (DFS-001 already collapses repeated paths), 7 (no measurement: one group held at a time). The size-change half of item 10 is deferred (see Open design notes).

## Public API

- `ContentHasher` in `infra.port`: `String sampleHash(Path file, int sampleSize) throws IOException`; `String fullHash(Path file) throws IOException`. Both return a lowercase hex digest, and two files match exactly when their digests are `equals`. `sampleHash` hashes the head then the tail (`sampleSize` bytes each, overlapping for short files), with the size excluded. It rejects `sampleSize <= 0` with `IllegalArgumentException`.
- `NioContentHasher` in `infra.adapter`: `implements ContentHasher`, no-arg. Stateless: it creates a new `MessageDigest` (SHA-256) per call and does positional `FileChannel` reads using the channel's size at read time.
- `PartialHashFilter` in `application`: constants `DEFAULT_SAMPLE_SIZE = 64 * 1024`, `DEFAULT_SKIP_THRESHOLD = 128L * 1024`; constructors `PartialHashFilter(ContentHasher)` and `PartialHashFilter(ContentHasher, int sampleSize, long skipThreshold)`; one method, `List<CandidateGroup> filter(List<CandidateGroup> groups)`.

## Layer placement

- `application`: `PartialHashFilter`, which reuses the existing `CandidateGroup` as its input and output.
- `domain.model` (exported, added to `module-info.java`): none.
- `infra.port` / `infra.adapter`: the `ContentHasher` port and the `NioContentHasher` adapter.

## Checklist disposition

1. Accumulation and merging: not applicable: the stage takes the finished `List<CandidateGroup>` that DFS-001 produces. It has no `add` or `merge`.
2. Inter-stage currency invariants: reuses `CandidateGroup`, whose constructor already rejects nulls, fewer than two files and a size mismatch, and makes a defensive copy. No new currency type.
3. Deterministic output order: each input group is replaced in its own position by the groups it splits into, ordered by their smallest path. Members are in ascending path order. A bypassed group is passed through unchanged.
4. Immutable results: `CandidateGroup` is immutable and `filter` returns an immutable list.
5. Repeated input: not applicable: DFS-001's `SizeIndex.groups()` already collapses equal paths before this stage runs.
6. Configuration and its defaults: sample size 64 KB and skip threshold 128 KB, validated in the 3-argument constructor (see Configuration).
7. Memory or performance, measured: not applicable: the stage holds only one group's digests at a time and each read is at most `2 * sampleSize`. There is nothing whole-walk to measure.
8. Groups of one are dropped: every subgroup with fewer than two members is dropped (AC-5). This includes groups shrunk by an unreadable file (AC-7).
9. Path-order processing: members are sampled in ascending `Path.compareTo` order. The skip threshold is 128 KB (CLAUDE.md decision 3): at or under it, a 2 × 64 KB sample would read about as much as the full hash.
10. Failure mid-stage: an unreadable file (an `IOException` from `sampleHash`) is dropped from its group, and the group itself is not failed (AC-7). There is no report until DFS-005's listener exists. Size change is not checked here: the adapter hashes the file's current bytes, so a changed file most likely splits off, and DFS-003 plus the delete-time byte comparison (decision 4) remain the safety net.
11. New secondary ports: `ContentHasher` declares both methods now (ticket decision), matching the "sample and full" port sketch. `PartialHashFilter` is tested against a recording fake. Only `NioContentHasher` is tested on real temp files.
12. Large and near-duplicate fixtures: a 300 KB pair that differs only in the middle byte (AC-2), plus a 10 KB pair with 4 KB samples (AC-9). Both are generated in `@TempDir` in code and never committed.
13. Reused across concurrent scans: no mutable fields: only `final` hasher, sample size and threshold. `NioContentHasher` is stateless. One instance of each may serve concurrent scans.
14. Input/output currency named: the ticket names `CandidateGroup` for both input and output (Context, item 2).

## Configuration

- `sampleSize` (bytes read from each end): default `DEFAULT_SAMPLE_SIZE` = 65,536. Must be `> 0`, otherwise `IllegalArgumentException`.
- `skipThreshold` (a group with `size <= skipThreshold` bypasses the sample): default `DEFAULT_SKIP_THRESHOLD` = 131,072. Must be `>= 0`, otherwise `IllegalArgumentException`.
- `hasher`: must be non-null, otherwise `NullPointerException`.
- All three are checked inside `PartialHashFilter`'s 3-argument constructor, and the 1-argument constructor delegates to it. They will move to the DFS-005 scan config record later.

## Open design notes

- **Digest type.** The digest is a hex `String` rather than `byte[]`, because a `String` gives value equality and immutability for free and a fake can return any string. DFS-003 may promote it to a typed value if `DuplicateSet` exposes it.
- **Sample size passed on every call.** The sample size goes to the hasher with each call instead of being fixed in the adapter, so the stage owns the tunable (AC-9) and a fake can assert the size it received.
- **`fullHash` message.** The final `fullHash` message avoids the `not implemented:` prefix, so the story gate does not read it as a leftover stub. Until then the method carries the standard DFS-002 stub, so the AC-8 test starts red.
- **Size change.** Detecting a size change (checklist item 10) is not in scope. A future check would need the expected size passed into `sampleHash`.

## Stubs

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/PartialHashFilter.java`, `.../infra/port/ContentHasher.java` (interface, no bodies), `.../infra/adapter/NioContentHasher.java`.
- Each throws `new UnsupportedOperationException("not implemented: DFS-002")` per D2 / CLAUDE.md decision 5: the 3-argument `PartialHashFilter` constructor, `PartialHashFilter.filter`, `NioContentHasher.sampleHash` and `NioContentHasher.fullHash`.
- Commit: the orchestrator's design commit, `feat(file-scanner): add partial-hash stage stubs [DFS-002]`.
