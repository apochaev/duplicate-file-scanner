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
