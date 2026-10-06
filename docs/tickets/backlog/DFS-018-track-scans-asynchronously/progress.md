# DFS-018 progress

## design (architect)

- Wrote `design.md` (use-case archetype, all 14 checklist items addressed, none marked not applicable).
- New stubs: `domain.model` `Result`, `ErrorCode`, `ScanSnapshot`; `domain.usecases.command.StartScan`; `domain.usecases.query.GetScanResult`; `application` `ScanService`, `ScanRunner` (package-private); `infra.port` `ScanRecord`, `ScanRepository`; `infra.adapter.InMemoryScanRepository`.
- Changed in place: `ScanStatus` (six values), `CancelScan` and `GetScanStatus` (return `Result<ScanSnapshot>`), `ScanPipeline` (`implements ScanRunner`, stubbed `run`).
- Every behavioural method and both `ScanService` constructors throw `UnsupportedOperationException("not implemented: DFS-018")`. `module-info.java` unchanged.
- `./gradlew :file-scanner:compileJava :file-scanner:compileTestJava` green.
- Expected commit: `feat(file-scanner): design scan lifecycle use cases and stubs [DFS-018]` (made by the orchestrator).

## Test phase
- Wrote `ScanServiceTest` (37 invocations, AC-1 to AC-16), `ScanServiceRealThreadTest` (AC-17), `InMemoryScanRepositoryTest` (AC-18), and `test-plan.md`. All 39 tests fail red with `UnsupportedOperationException: not implemented: DFS-018` only.
- Expected commit: `test(file-scanner): add scan lifecycle tests [DFS-018]` (made by the orchestrator).

## Test phase, attempt 2

- Fixed the red-gate defect: the two `@ParameterizedTest`s in `ScanServiceTest` (AC-9, AC-12) now set `name = "DFS-018.AC-n ... {0}"`, so each invocation's name carries the criterion ID instead of the default `[n] VALUE`. test-plan.md notes the invocation names.
- `java tools/story-gate/StoryGate.java test-phase DFS-018` passes (red: 39 story tests fail with stubs, 131 other tests pass, 1 skipped; traceability: 18 criteria, 20 scenarios). Tests still uncommitted; expected commit `test(file-scanner): add scan tracking tests [DFS-018]`.
