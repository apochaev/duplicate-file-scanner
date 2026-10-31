# DFS-030: Export a complete scan for analysis — design

## Summary

Placement: new record `FolderTotal` in `domain.model` (already exported, so `module-info.java` is unchanged). `ScanResult` gains `roots` and `folders` as its first two components. The per-folder counter is `ScanPipeline.FolderCounter`, a package-private nested class in `application`. The `Clock`, the `scan`/`folder` lines and the reshaped `copies` go in `ScanCommand`, and the text header goes in `TextReport.header`. No new port and no new setting.

- **AC-1** roots: `ScanPipeline` passes its `keepTopLevel` output into `ScanResult` (that wiring is already in place). Tests are red on the stubbed `ScanResult` constructor and `FolderCounter.totals()`.
- **AC-2** per-folder totals, sorted, with no entry for a folder holding only subfolders: stubs `FolderCounter.add` / `FolderCounter.totals()`.
- **AC-3** a file counts only when the walk keeps it: `add` is called only after the exclude filter, and `add` itself ignores files below `minSize`. Stub: `FolderCounter.add`.
- **AC-4** a file that later fails stays counted, because counting happens during the walk, before any hashing. Stub: `FolderCounter.add`/`totals`.
- **AC-5** hard links count once per path, since every `add` call counts. Stub: `FolderCounter.add`.
- **AC-6** validation and immutability: stubs `FolderTotal` and `ScanResult` compact constructors.
- **AC-7** `scan` header line: stub `ScanCommand.scanLine`. Times come from a `Clock` added to the constructor, read once before `start` and once after completion (already wired).
- **AC-8** `folder` lines: stub `ScanCommand.folderLine`.
- **AC-9** `copies` as `{path, modified}` objects: stub `ScanCommand.copies(DuplicateSet)`.
- **AC-10** line order: `ScanCommand.write` already calls the stubs in the order scan, folder, set, skipped, summary. Tests are red on `scanLine`, and the summary code is unchanged.
- **AC-11** text header: stub `TextReport.header`, which `write` prints first.

Checklist items not applicable: 5 (repeated input, already handled by `keepTopLevel`), 8, 9, 11, 12.

Expected breakage (existing tests that run a real scan, so the stub turns into FAILED or exit code 1): `ScanCommandTest`, `ScanRunnerTest`, `CliApplicationTest`, `ScannerConfigurationTest`, `ScannerFactoriesTest`, `ScanProgressSnapshotTest`, `ScanServiceRealThreadTest`, `ScanPipelineEndToEndTest` (declared as a precaution).

## Public API

- `FolderTotal` in `domain.model`: `record FolderTotal(Path folder, long files, long bytes)`. Rejects a null folder (NPE), `files < 1` and `bytes < 0` (IAE).
- `ScanResult` in `domain.model`: `record ScanResult(List<Path> roots, List<FolderTotal> folders, List<DuplicateSet> sets, List<WalkEvent.Skipped> skipped, long excluded)`. Rejects any null list and a null element in `roots`/`folders` (NPE), and `excluded < 0` (IAE). Takes `List.copyOf` of all four lists in the order given. It does not sort: ordering is the producer's job.
- `ScanPipeline.FolderCounter` in `application` (package-private, nested): `FolderCounter(long minSize)`, `void add(FileEntry file)`, `List<FolderTotal> totals()`. `add` counts `file` under `file.path().getParent()` if `size >= minSize`. `totals()` is immutable and ascending by `Path.compareTo`.
- `ScanCommand` in `cli`: `public ScanCommand(StartScan, GetScanStatus, GetScanResult, Duration pollInterval, Clock clock)` and the package-private `(…, Duration, Clock, ReportFile.Opener)`. Adds `static final int SCHEMA_VERSION = 2` and private `ObjectNode scanLine(ScanResult, ScanConfig, Instant startedAt, Instant finishedAt)`, `ObjectNode folderLine(FolderTotal)`, `ArrayNode copies(DuplicateSet)`.
- `TextReport` in `cli`: `static void write(ScanResult, ScanConfig, Instant startedAt, Instant finishedAt, PrintStream)` and `static List<String> header(List<Path> roots, ScanConfig, Instant startedAt, Instant finishedAt)`, which returns the two header lines.
- `ScanRunner` in `cli`: passes `Clock.systemUTC()`.

## Layer placement

- `application`: `ScanPipeline.FolderCounter`. It is nested in `ScanPipeline.java` because `touches` names no other `application` main file. Package-private so the heap benchmark in the same package can measure it directly.
- `domain.model` (exported): `FolderTotal`, plus the reshaped `ScanResult`. The package is already exported, so `module-info.java` is unchanged.
- `infra.port` / `infra.adapter`: none.

## Checklist disposition

1. Accumulation and merging: one `FolderCounter` per `scan()` call, fed by the walk callback on the scan's thread. Not thread-safe and has no `merge` (ticket item 1). The output is sorted, so it does not depend on arrival order. Parallel walking (DFS-006) would revisit this.
2. Inter-stage currency invariants: `FolderTotal` and `ScanResult` constructors as in Public API (AC-6).
3. Deterministic output order: `roots` ascending (the `keepTopLevel` order) and `folders` ascending by folder path, independent of walk order. `sets` and `skipped` are unchanged.
4. Immutable results: `ScanResult` copies all lists, and `totals()` returns an immutable list.
5. Repeated input: not applicable. `keepTopLevel` drops equal and nested roots before walking, so each file is walked once (ticket decision 9). Hard links are different paths, counted on purpose (AC-5).
6. Configuration and its defaults: no new setting. `FolderCounter` takes `config.minSize()` and enforces it itself, matching `SizeIndex`, which receives the same kept entries.
7. Memory or performance, measured: the counter holds one entry per folder across the whole walk. The test-author commits a `@Disabled` heap benchmark (`FolderCounterHeapBenchmark`, modeled on `SizeIndexHeapBenchmark`) at 100,000 folders against `ScanPipeline.FolderCounter`. It is run once by hand, and the figure goes in the ticket (decision 10).
8. Groups of one are dropped: not applicable. The ticket adds no group stage.
9. Path-order processing: not applicable. No file content is read.
10. Failure mid-stage: counting happens during the walk, so a file dropped later as `UNREADABLE` by a hash stage stays in its folder's total and also appears in `skipped` (AC-4). Walker `Skipped` events never reach `add` (AC-3).
11. New secondary ports: not applicable. None added.
12. Large and near-duplicate fixtures: not applicable. No content-dependent behavior.
13. Reused across concurrent scans: `FolderCounter` is created inside `scan()` and never escapes it. `ScanPipeline` stays stateless.
14. Input/output currency named: yes. The ticket names `FileEntry` in and `FolderTotal`/`ScanResult` out (decision 2, item 2).

## Configuration

- No new tunable. `FolderCounter` applies the existing `ScanConfig.minSize()` (default 1, so empty files are not counted). The default excludes are applied before `add` by the existing `DefaultExcludeFilter`.

## Expected breakage

- `ScanCommandTest`: its `scan(...)` helper runs real scans through `ScannerFactories`, so the scan ends FAILED on the stub and the command exits 1 instead of 0.
- `ScanRunnerTest`: runs a real scan through `ScannerFactories`, so the exit code is 1, not 0.
- `CliApplicationTest`: the real Spring app scans a temp folder, the scan fails on the stub, and the exit code is 1.
- `ScannerConfigurationTest`: a real scan through the Spring beans ends FAILED, but the test asserts COMPLETED.
- `ScannerFactoriesTest`: real scans through the factories end FAILED, but the test asserts COMPLETED.
- `ScanProgressSnapshotTest`: a real scan ends FAILED, so the final snapshot and HashProgress events are missing.
- `ScanServiceRealThreadTest`: a real `ScanPipeline` on real threads ends FAILED, but the test asserts COMPLETED.
- `ScanPipelineEndToEndTest`: calls `ScanPipeline.scan` directly with `NioFileTreeWalker`. It should fail with the stub as root cause, and is declared in case the walker wraps the listener's exception.

## Open design notes

- **Test compilation is broken until the tests commit** (the reshaping case in `archetype.md`). The test-author must update these existing callers: `ScanResultTest`, `ScanServiceTest` (`new ScanResult` with 3 args), `ScanCommandTest` (`new ScanResult`, the 4-arg and opener `ScanCommand` constructors, and its `copies(...)` helper, which reads strings), and `TextReportTest` (`TextReport.write(scan, out)`, `new ScanResult`). They must also update `CliApplicationTest`'s expected line types (`["set","summary"]` becomes `scan`, `folder`…, `set`, `summary`). That test is not named in the ticket's caller list, but it sits inside the `cli` test `touches`.
- **Test-only callers outside `touches`** (`ScannerFactoriesTest`, `ScanProgressSnapshotTest`, `ScanPipelineEndToEndTest`) compile unchanged and need no edit. They only read `sets()`/`skipped()`.
- **Clock reads:** exactly two per completed scan: `startedAt` just before `StartScan.start`, and `finishedAt` after `await` returns COMPLETED. Failed and cancelled scans read it once. A test can use a `Clock` that returns queued instants.
- **Text header format** (from AC-11's examples): times are UTC as `yyyy-MM-dd HH:mm:ssZ`. The finish drops its date when it falls on the start's UTC date. The minimum size uses `TextReport.size`.
- **Wiring done in the design commit**, so each criterion is reachable only through a stub: `FolderCounter.add` sits beside `sizeIndex.add` in the walk callback, and `totals()` and `kept` are passed into `ScanResult`. In `ScanCommand.write` the stub calls are already in final line order.
- **Implementer also owns, outside the stubs:** `tools/analysis/overlaps.sql` (version 2 shape, `folder` totals, roll-up stopping at the `scan` roots), `CLAUDE.md` (decision 1, and the JSONL description: line order, version rule, pointer to DFS-030) and `README.md` (text report example and JSONL description).

## Stubs

- Files stubbed: `FolderTotal.java` (compact constructor), `ScanResult.java` (compact constructor), `ScanPipeline.java` (`FolderCounter.add`, `FolderCounter.totals`), `ScanCommand.java` (`scanLine`, `folderLine`, `copies`), `TextReport.java` (`header`). Signature-only changes: `ScanRunner.java` and the `ScanCommand` constructors.
- Each throws `new UnsupportedOperationException("not implemented: DFS-030")` per D2.
- Commit: filled in after the design commit.
