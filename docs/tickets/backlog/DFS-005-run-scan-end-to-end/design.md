# DFS-005: Run a scan end to end — design

## Summary

A new `application.ScanPipeline` (ports only as fields) runs one scan synchronously: `scan(List<Path>, ScanConfig, ScanListener, BooleanSupplier) throws IOException` returns a `ScanResult`. Five new `domain.model` types (already exported, so `module-info.java` is unchanged): `ScanConfig`, `ScanResult`, `ScanListener`, `ScanEvent` (sealed), `ScanPhase`. There is one new port, `infra.port.LocationResolver`, with its adapter `infra.adapter.NioLocationResolver`. `PartialHashFilter`, `FullHashConfirmer` and `HardLinkDetector` each get one new stubbed constructor that takes a `Consumer<WalkEvent.Skipped> onDrop`. Their existing constructors and methods are unchanged.

- AC-1: `ScanPipeline.scan` runs walk into `SizeIndex`, then partial hash, full hash and hard links, one group at a time, and returns `ScanResult.sets`.
- AC-2: the walk listener copies each `WalkEvent.Skipped` into the per-scan skip collector.
- AC-3: the new `onDrop` constructors report `Skipped(path, UNREADABLE, "<stage> failed: <msg>")`. The pipeline's sink collects it.
- AC-4: `ScanEvent.PhaseStarted`, `FileWalked(filesSoFar)` and `GroupDone(phase, done, total)` are sent by the pipeline.
- AC-5: one sink both appends to the collector and sends `ScanEvent.Skipped`.
- AC-6: the core never prints. Everything goes through `ScanListener`.
- AC-7 / AC-8: `ScanConfig.defaults()` and the validating compact constructor. `minSize` goes to `new SizeIndex(minSize)`.
- AC-9 / AC-10: `LocationResolver.realPath`, then sort ascending, then drop locations that are equal or nested (`Path.startsWith`). `NioLocationResolver` uses `toRealPath()` plus a directory check.
- AC-11 / AC-12: an empty list throws `IllegalArgumentException`. All locations are resolved before any event is sent, and the first `IOException` is rethrown unchanged.
- AC-13: if a walk throws, the scan records `Skipped(root, UNREADABLE, "walk failed: <msg>")` and goes on to the next location.
- AC-14 / AC-15: the `BooleanSupplier` is checked at each walk event and before each group. The scan then throws `CancellationException`.
- AC-16: `sets` stays in stage order. `skipped` is kept in the order things happened.
- AC-17: an end-to-end test on the real NIO adapters, in the `infra.adapter` test package, so `Fixtures` stays package-private.
- AC-18: all state is local to the scan call. `ScanPipeline` has no mutable fields.
- Not applicable: checklist item 2 (no new currency between stages) and item 7 (no new whole-walk structure; `SizeIndex` was measured in DFS-001).

## Public API

- `ScanConfig` in `domain.model`: `record ScanConfig(long minSize, int sampleSize, long partialHashThreshold)`, `static ScanConfig defaults()`. The compact constructor throws `IllegalArgumentException` for `minSize < 1`, `sampleSize <= 0` or `partialHashThreshold <= 0`.
- `ScanResult` in `domain.model`: `record ScanResult(List<DuplicateSet> sets, List<WalkEvent.Skipped> skipped)`. Both lists are immutable copies with no null elements.
- `ScanPhase` in `domain.model`: `enum { WALK, PARTIAL_HASH, FULL_HASH, HARD_LINKS }`.
- `ScanListener` in `domain.model`: `@FunctionalInterface void onEvent(ScanEvent event)`.
- `ScanEvent` in `domain.model`: sealed, with the records `PhaseStarted(ScanPhase phase)`, `FileWalked(long filesSoFar)` (at least 1), `GroupDone(ScanPhase phase, int done, int total)` (phase not `WALK`, `1 <= done <= total`) and `Skipped(WalkEvent.Skipped skipped)`. All components are non-null.
- `LocationResolver` in `infra.port`: `Path realPath(Path location) throws IOException`. It throws `NoSuchFileException` for a missing location and `NotDirectoryException` for one that is not a directory.
- `NioLocationResolver` in `infra.adapter`: `realPath` = `location.toRealPath()`. If the result is not a directory, it throws `new NotDirectoryException(location.toString())`.
- `ScanPipeline` in `application`: `ScanPipeline(LocationResolver resolver, FileTreeWalker walker, ContentHasher hasher, FileIdentity identity)` and `ScanResult scan(List<Path> locations, ScanConfig config, ScanListener listener, BooleanSupplier stopSignal) throws IOException`. It throws NPE for any null argument.
- New constructors (the existing ones are untouched):
  - `PartialHashFilter(ContentHasher, int sampleSize, long skipThreshold, Consumer<WalkEvent.Skipped> onDrop)`
  - `FullHashConfirmer(ContentHasher, Consumer<WalkEvent.Skipped> onDrop)`
  - `HardLinkDetector(FileIdentity, Consumer<WalkEvent.Skipped> onDrop)`

  A drop is reported as `new WalkEvent.Skipped(path, UNREADABLE, prefix + msg)`. The prefix is `"partial hash failed: "`, `"full hash failed: "` or `"hard-link check failed: "`. `msg` is the exception's message, or its class's simple name when the message is null (a `Skipped` detail must be non-null). For `HardLinkDetector` the dropped path is the file being checked.

`scan` does its work in this order:
1. Null checks.
2. Empty `locations`: throw IAE.
3. Resolve every location in the order given, and rethrow the first `IOException`.
4. Sort ascending, and keep a location only if it does not `startsWith` an already kept one.
5. Create the per-scan state: `SizeIndex(config.minSize())`, the skip list, the sink, and the three stages built with the sink. `PartialHashFilter` gets `(sampleSize, partialHashThreshold)`.
6. Send `PhaseStarted(WALK)`, then walk each kept location. At each walk event, check the stop signal first; if it reads true, set a flag and return `false` without handling the event. A `FileFound` goes to `SizeIndex.add` and sends `FileWalked(++count)`, including files below `minSize`, since the event counts regular files the walker yields. A `Skipped` goes to the sink. After each walk, if the flag is set, throw `CancellationException` without walking later locations. If a walk throws `IOException`, send `Skipped(root, UNREADABLE, "walk failed: " + msg)` to the sink.
7. For each of `PARTIAL_HASH`, `FULL_HASH` and `HARD_LINKS`:
   1. Send `PhaseStarted`, even when there are 0 groups.
   2. For each input group in order: check the stop signal (throw `CancellationException` if set), call the stage with `List.of(group)`, append the output, and send `GroupDone(phase, i, total)`.
   3. The phase's output is the next phase's input. `total` is the size of that input.
8. Return `new ScanResult(sets, skipped)`.

## Layer placement

- `application`: `ScanPipeline`, plus the three new stage constructors.
- `domain.model` (already exported; `module-info.java` is unchanged): `ScanConfig`, `ScanResult`, `ScanListener`, `ScanEvent`, `ScanPhase`.
- `infra.port` / `infra.adapter`: `LocationResolver` / `NioLocationResolver`.

## Checklist disposition

1. Accumulation and merging: the walk accumulates into one per-scan `SizeIndex` (DFS-001, not thread-safe), used by the scanning thread only. Nothing is merged here; DFS-006 may give each worker its own index and `merge` them.
2. Inter-stage currency invariants: not applicable. No new currency between stages; `CandidateGroup` and `DuplicateSet` keep their DFS-001/DFS-003/DFS-004 invariants. The new records validate their own components (see Public API).
3. Deterministic output order: `sets` is in `SizeIndex.groups()` order (largest size first). Every stage preserves input order, so feeding groups one at a time keeps it; paths ascend inside each set. `skipped` is in event order: walk skips location by location in path order, then drops phase by phase in group order. No sorting is added.
4. Immutable results: `ScanResult` copies both lists with `List.copyOf`, `DuplicateSet` is already immutable, and the event records are immutable.
5. Repeated input: equal or nested locations are dropped after resolution (AC-9), so no file is walked twice. `SizeIndex` still collapses equal paths as a backstop. Hard links reached from two locations are one physical copy (DFS-004).
6. Configuration and its defaults: `ScanConfig`, whose defaults are `(1, 65536, 131072)`. The compact constructor validates it, and it reaches `SizeIndex(minSize)` and `PartialHashFilter(..., sampleSize, partialHashThreshold, ...)` through their constructors. A test pins the defaults to the stages' `DEFAULT_*` constants (AC-7).
7. Memory or performance, measured: not applicable. The only data held across the whole walk is `SizeIndex`, which was measured in DFS-001. The hashing phases hold one group at a time, and the skip list is proportional to the skips.
8. Groups of one are dropped: done by the stages. The pipeline adds nothing, and `DuplicateSet` rejects fewer than two copies.
9. Path-order processing: kept within each stage. Locations are walked in ascending path order. The partial-hash threshold is `ScanConfig.partialHashThreshold` (128 KB default, CLAUDE.md decision 3).
10. Failure mid-stage: the file is dropped from its group (unchanged behaviour) and is now reported. Through the new `onDrop` constructors it becomes `UNREADABLE` in `ScanResult.skipped` and a `ScanEvent.Skipped` (AC-3, AC-5). A failed location walk is reported the same way (AC-13).
11. New secondary ports: `LocationResolver.realPath`. `ScanPipeline` is tested against fakes of all four ports; only `NioLocationResolver` is tested on real `@TempDir` files, with a link created at test time and `Assumptions` when that is not possible (AC-10).
12. Large and near-duplicate fixtures: the AC-17 end-to-end test generates, in code, a 200 KB identical pair and a 200 KB pair differing only in the middle byte. Nothing is committed to `fixtures/`.
13. Reused across concurrent scans: `ScanPipeline`'s fields are the four ports only. The `SizeIndex`, the stages (built per scan from the config and sink), the skip list and the counters are all locals of `scan` (AC-18).
14. Input/output currency named: yes. The ticket names the input (`List<Path>` plus `ScanConfig`), the output (`ScanResult`) and each stage's currency (decisions 2 to 5).

## Configuration

- `ScanConfig.minSize` (default 1) is enforced in `SizeIndex.add`. `sampleSize` (default 65536) and `partialHashThreshold` (default 131072) are enforced in `PartialHashFilter`. The `ScanConfig` constructor rejects invalid values before the walk starts.

## Open design notes

- The drop sink is a JDK `Consumer<WalkEvent.Skipped>` rather than a new interface. The stage builds the `Skipped` itself, so it owns its detail prefix.
- The pipeline is named `ScanPipeline`, with a `scan(...)` method; the ticket leaves the name open.
- The stop signal is checked before the event is handled, so the event that sees `true` is not added (AC-14: the listener returns `false` for the 3rd event).
- `FileWalked` counts every regular file the walker yields, including files below `minSize` (AC-4 says "per regular file").
- The ticket gives no detail text for a failed location walk. I use `"walk failed: <msg>"`.
- Red gate: every new record's compact constructor throws, so each story test fails with the stub's exception, including tests that only build an expected `ScanEvent`. No pre-existing test uses a new type or a new constructor, so DFS-001..DFS-004 stay green (verified: `:file-scanner:test` passes with the stubs).
- Test files the test-author needs:
  - `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/Fixtures.java` is package-private, with `FILES`, `copyInto`, `createSymlinkOrSkip`, `createHardLinkOrSkip`, `createJunction` and `isWindows`. Put `NioLocationResolverTest` and the AC-17 end-to-end test (`ScanPipelineEndToEndTest`) in that package, so `Fixtures` stays unchanged.
  - `application/PartialHashFilterRealFileTest.java` is the precedent for a real-adapter test.
  - Leave `PartialHashFilterTest`, `FullHashConfirmerTest` and `HardLinkDetectorTest` untouched (DoD). Test the new `onDrop` constructors in a new class, for example `application/StageDropReportingTest`.
  - AC-6 captures `System.out`/`System.err`, so restore them in `finally`.

## Stubs

- Files stubbed: `domain/model/ScanConfig.java`, `ScanResult.java`, `ScanEvent.java` (4 records), `ScanPhase.java`, `ScanListener.java`, `infra/port/LocationResolver.java`, `infra/adapter/NioLocationResolver.java`, `application/ScanPipeline.java`, and the new constructors in `PartialHashFilter.java`, `FullHashConfirmer.java` and `HardLinkDetector.java`.
- Each throws `new UnsupportedOperationException("not implemented: DFS-005")` per D2 / CLAUDE.md decision 5.
- Commit: made by the orchestrator after the design gate.
