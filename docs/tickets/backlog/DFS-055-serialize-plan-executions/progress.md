# DFS-055 progress

## design (architect, attempt 1, 2026-11-05)

- Wrote `design.md` (Summary first) and the stubs; `./gradlew :file-scanner:compileJava :scanner-spring:compileJava` passes.
- New: `infra/port/ExecutionLock.java`, `infra/adapter/FileChannelExecutionLock.java` (`acquire` stub).
- Changed: `ErrorCode` (+`EXECUTION_ALREADY_RUNNING`), `JobStore` (+abstract `createUnless`), `ManifestWriter` (+default `manifestPath` stub), `InMemoryJobStore` (`createUnless` stub), `JobRunner` (+`Outcomes.ended` default no-op; `claim`/`abandon`/`dispatch` stubs), `PlanExecutionProgress` (+`lease`), `PlanExecutionService` (ctor takes `ExecutionLock`; `begin` delegates to `launch` stub; `ExecutionOutcomes.ended` stub), `ScannerFactories.planExecutionUseCases` (wires `FileChannelExecutionLock`).
- No `scanner-spring` main change (deviation, design.md Open design notes 1). Expected breakage: none.
- Expected commit: `feat(file-scanner): design plan execution lock and single run slot [DFS-055]`.

## Tests phase (attempt 1)

- Wrote the DFS-055 tests and `test-plan.md`; everything compiles. Against the stubs every new test fails with `not implemented: DFS-055`; the existing DFS-051 execution tests now fail the same way, because they start runs through the stubbed `launch`.
- New: `ExecutionLockProbe` (child-JVM `hold`/`try` probe with helper methods) and `FileChannelExecutionLockTest` in `infra.adapter` (test tree).
- Changed: `PlanExecutionServiceTest` (lock as 5th constructor argument, `CountingStore.createUnless`, `CountingLock` fake, `*.jsonl` lookup, an `@AfterEach` that cancels every created run, the DFS-055 tests), `PlanVerifyServiceTest` (`RecordingStore.createUnless`), `InMemoryJobStoreTest` (`createUnless`), `ScannerAutoConfigurationTest` (AC-3 bean wiring). `PlanExecutionRealThreadTest` needed no edit.
- Expected commit: `test(file-scanner): add plan execution serialization tests [DFS-055]`.
