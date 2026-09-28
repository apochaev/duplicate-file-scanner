# DFS-005 progress

## design (architect, attempt 1)

- Wrote `design.md` and compilable stubs, each throwing `not implemented: DFS-005`.
- New: `domain/model/{ScanConfig,ScanResult,ScanEvent,ScanPhase,ScanListener}.java`, `infra/port/LocationResolver.java`, `infra/adapter/NioLocationResolver.java`, `application/ScanPipeline.java`.
- Changed: `application/{PartialHashFilter,FullHashConfirmer,HardLinkDetector}.java`. Only a new stubbed `onDrop` constructor was added to each; the existing constructors and methods are untouched.
- `module-info.java` is unchanged. `./gradlew :file-scanner:compileJava :file-scanner:compileTestJava :file-scanner:test` is green, so the existing tests still pass.
- Expected commit (made by the orchestrator): `feat(file-scanner): design scan pipeline stubs [DFS-005]`.

## tests (test-author, attempt 1)

- Wrote 29 new tests across 5 files, covering AC-1..AC-18: `application/ScanConfigTest.java` (AC-7, AC-8), `application/ScanPipelineTest.java` (AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-9, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, AC-18, against hand-written fakes of all four ports), `application/StageDropReportingTest.java` (AC-3, the new `onDrop` constructors of `PartialHashFilter`/`FullHashConfirmer`/`HardLinkDetector`), `infra/adapter/NioLocationResolverTest.java` (AC-10, real files/link, package-private `Fixtures`), `infra/adapter/ScanPipelineEndToEndTest.java` (AC-17, real NIO adapters).
- Filled in `test-plan.md` with the criterion → scenario → test mapping and fakes/fixtures used.
- Verified `./gradlew :file-scanner:test`: 130 tests total, all 29 new tests fail with exactly `UnsupportedOperationException("not implemented: DFS-005")`, the 101 pre-existing tests still pass, and the one pre-existing skip (`NioFileTreeWalkerTest`'s case-insensitivity assumption) is unrelated to this ticket.
- No files touched outside `touches`; `Fixtures.java`, `plan.json` and every pre-existing test file are unchanged.
- Expected commit (made by the orchestrator): `test(file-scanner): write scan pipeline tests against stubs [DFS-005]`.

## implement (implementer, attempt 1)

- Implemented every stub per design.md: `domain/model/{ScanConfig,ScanEvent,ScanResult}.java` (validation, `defaults()`, immutable copies), `infra/adapter/NioLocationResolver.java` (`toRealPath()` plus a directory check), `application/ScanPipeline.java` (resolves and dedupes locations, walks into a per-scan `SizeIndex`, then feeds `PartialHashFilter`/`FullHashConfirmer`/`HardLinkDetector` one group at a time, sending `ScanEvent`s and collecting `ScanResult.skipped`), and the new `onDrop` constructors on `PartialHashFilter`, `FullHashConfirmer` and `HardLinkDetector` (each existing constructor now delegates to the new one with a no-op sink, per ticket decision 2). `ScanPhase`/`ScanListener` needed no changes (already real).
- `./gradlew build` is green except for 2 pre-existing test failures I judge to be test-authoring defects rather than implementation bugs (see `learnings.md` for the detailed diagnosis, each backed by the actual-vs-expected diff observed while running against my design.md-faithful implementation): `ScanPipelineTest`'s AC-14 test (`stoppingDuringWalkThrowsCancellationAndSkipsNextLocation`) and `ScanPipelineEndToEndTest`'s AC-17 test (`realPipelineOnRealFiles`). All other 128 tests pass, including every DFS-001..DFS-004/DFS-017 test, `ArchitectureTest` and `ModuleExportsTest`. No `not implemented:` marker remains under `src/main`.
- No test file was edited. No file outside `touches` was changed.
- Expected commit (made by the orchestrator): `feat(file-scanner): implement the end-to-end scan pipeline [DFS-005]`.
