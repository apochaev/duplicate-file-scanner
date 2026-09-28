# DFS-005: Run a scan end to end — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-005.AC-1` | Identical files in two folders form one duplicate set | `DFS-005.AC-1 Identical files in two folders form one duplicate set` |
| `DFS-005.AC-2` | A path the walker skips is reported in the result | `DFS-005.AC-2 A path the walker skips is reported in the result` |
| `DFS-005.AC-3` | A file a stage cannot read is reported in the result | `DFS-005.AC-3 A file whose full hash fails is reported as UNREADABLE with a detail naming the full-hash stage` |
| `DFS-005.AC-3` | (stage-level, design.md's onDrop constructors) | `DFS-005.AC-3 PartialHashFilter reports a dropped member with a detail naming the partial-hash stage` |
| `DFS-005.AC-3` | (stage-level) | `DFS-005.AC-3 FullHashConfirmer reports a dropped member with a detail naming the full-hash stage` |
| `DFS-005.AC-3` | (stage-level, null-message fallback) | `DFS-005.AC-3 FullHashConfirmer falls back to the exception's class name when its message is null` |
| `DFS-005.AC-3` | (stage-level) | `DFS-005.AC-3 HardLinkDetector reports a dropped file with a detail naming the hard-link stage` |
| `DFS-005.AC-4` | Progress is reported per phase and per group | `DFS-005.AC-4 Progress is reported per phase and per group` |
| `DFS-005.AC-5` | Skips reach the listener as they happen | `DFS-005.AC-5 A skip reaches the listener between the phase it happened in and the next phase` |
| `DFS-005.AC-6` | The core prints nothing | `DFS-005.AC-6 Running a scan writes nothing to System.out or System.err` |
| `DFS-005.AC-7` | The default config matches the stages' own defaults | `DFS-005.AC-7 The default config matches the stages' own defaults` |
| `DFS-005.AC-7` | Empty files are excluded by default | `DFS-005.AC-7 Empty files are excluded by default` |
| `DFS-005.AC-7` | A larger minimum size excludes small duplicates | `DFS-005.AC-7 A larger minimum size excludes small duplicates` |
| `DFS-005.AC-8` | Invalid config values are rejected (minSize 0) | `DFS-005.AC-8 A minSize below 1 is rejected` |
| `DFS-005.AC-8` | Invalid config values are rejected (sampleSize 0) | `DFS-005.AC-8 A sampleSize of zero or less is rejected` |
| `DFS-005.AC-8` | Invalid config values are rejected (partialHashThreshold 0) | `DFS-005.AC-8 A partialHashThreshold of zero or less is rejected` |
| `DFS-005.AC-9` | Equal and nested start locations are walked once | `DFS-005.AC-9 Equal and nested start locations are walked once` |
| `DFS-005.AC-9` | Locations are walked in path order whatever the order given | `DFS-005.AC-9 Locations are walked in path order whatever the order given` |
| `DFS-005.AC-10` | A location given through a link resolves to its target | `DFS-005.AC-10 A location given through a link resolves to its target` |
| `DFS-005.AC-10` | A missing path or a file is not a valid location (missing) | `DFS-005.AC-10 A missing path throws NoSuchFileException` |
| `DFS-005.AC-10` | A missing path or a file is not a valid location (file) | `DFS-005.AC-10 A regular file throws NotDirectoryException` |
| `DFS-005.AC-11` | An empty location list is rejected | `DFS-005.AC-11 An empty location list is rejected` |
| `DFS-005.AC-12` | A mistyped location fails the scan before anything runs | `DFS-005.AC-12 A mistyped location fails the scan before anything runs` |
| `DFS-005.AC-13` | A location that fails mid-scan is skipped and the rest still run | `DFS-005.AC-13 A location that fails mid-scan is skipped and the rest still run` |
| `DFS-005.AC-14` | Stopping during the walk | `DFS-005.AC-14 Stopping during the walk throws CancellationException and never walks the next location` |
| `DFS-005.AC-15` | Stopping between groups of the full hash | `DFS-005.AC-15 Stopping between groups of the full hash throws CancellationException and skips the hard-link stage` |
| `DFS-005.AC-16` | Result order follows the pipeline and the order of events | `DFS-005.AC-16 Result order follows the pipeline order and the order skips and drops happened` |
| `DFS-005.AC-17` | The real pipeline on real files | `DFS-005.AC-17 The real pipeline on real files finds the identical pair, excludes the near-duplicate and the empty files, and treats the hard link as one physical copy` |
| `DFS-005.AC-18` | Two concurrent scans on one instance do not share state | `DFS-005.AC-18 Two concurrent scans on one pipeline instance return only their own results` |

## Fakes and fixtures used

- `LocationResolver`: hand-written `FakeLocationResolver` (in `ScanPipelineTest`) mapping a location to a real path or a configured `IOException`.
- `FileTreeWalker`: hand-written `FakeFileTreeWalker` (in `ScanPipelineTest`) replaying a configured list of `WalkEvent`s per root, or throwing a configured `IOException`; records the roots walked and the listener's return value per event. `BlockingFakeFileTreeWalker` (AC-18) adds a `CyclicBarrier(2)` so both concurrent scans' walks start together.
- `ContentHasher`: hand-written `FakeContentHasher` (in `ScanPipelineTest` and `StageDropReportingTest`, one per file) returning a configured digest per path, or throwing a configured `IOException` for a path marked to fail sample/full hashing; records `fullHash` calls for AC-15.
- `FileIdentity`: hand-written `FakeFileIdentity` / `RecordingIdentity` — two paths are the same file only when equal (no hard-link scenario needs a configured pair in this ticket's scenarios), or throws for a path marked unreadable (`StageDropReportingTest`); records calls for AC-15's "hard-link stage received none" assertion.
- `ScanListener`: hand-written `RecordingListener`, appends every `ScanEvent` in call order.
- Fixtures: `Fixtures.copyInto` (committed tree) plus a 200 KB identical pair, a 200 KB near-duplicate pair (differing only in the middle byte) and a hard link, all generated/created at test time in `ScanPipelineEndToEndTest` (AC-17); a link/junction created at test time in `NioLocationResolverTest` (AC-10). Nothing platform-specific is committed.

## Notes for the implementer

- `ScanConfig`'s compact constructor and `ScanConfig.defaults()` are stubbed to throw unconditionally, so almost every `ScanPipelineTest` test (which needs a `ScanConfig` instance to call `scan`) fails red at that line today, not necessarily at the line that exercises the behavior under test. This is expected (design.md's "Red gate" note) and resolves itself once `ScanConfig` is implemented.
- AC-15's stop signal is a `BooleanSupplier` that returns `false` for its first 7 calls and `true` from the 8th: this count is derived from design.md's `scan()` algorithm (4 walk-event checks + 2 PARTIAL_HASH group checks + 1 FULL_HASH group check succeed, the 2nd FULL_HASH group check fails), assuming groups are processed in `SizeIndex.groups()` order (size 20 before size 10). If the implementation checks the stop signal at different points than design.md describes, this test's exact count may need adjusting — the important assertions are that `fullHash` was called only for the size-20 pair and `identity.isSameFile` was never called.
- AC-13 and AC-3's "walk failed" detail text is not asserted verbatim (only `SkipReason.UNREADABLE` and the path); design.md's own convention (`"walk failed: <msg>"`) is not enforced by a test since the ticket's AC-13 scenario does not require checking the detail wording, only AC-3 does (for the full-hash stage).
- `ScanPipelineEndToEndTest` and `NioLocationResolverTest` live in `us.pochaev.dfs.scanner.infra.adapter`'s test package (not `application`) specifically so they can use the package-private `Fixtures` helper, per design.md; `Fixtures` itself is unchanged.
