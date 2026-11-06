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

## Implement (attempt 1)

Implemented all eight stubs per design.md: `PlanExecutionService.launch` and `ExecutionOutcomes.ended`, `JobRunner.claim/abandon/dispatch` (plus `ended` on the task finally path, QUEUED cancel and QUEUED markFailed), `InMemoryJobStore.createUnless` (create and createUnless synchronized), `FileChannelExecutionLock.acquire` (static registry with shares), `ManifestWriter.manifestPath`; `requireNonNull(lease)` in `PlanExecutionProgress`; one CLAUDE.md line under Safety invariants. No stub markers left.

Status: all tests pass except one: `PlanExecutionServiceTest` "DFS-055.AC-3 The lock is held from the start until the run is recorded FAILED" (test defect, see learnings.md). Expected commit: `feat(file-scanner): serialize plan executions with a lock and single run slot [DFS-055]` once the test is fixed.

## Test rework 1

Fixed `PlanExecutionServiceTest.lockIsHeldUntilTheRunIsRecordedTerminal` (FAILED case): the manifest writer now fails the `Moved` line (written after the compare) instead of `Moving` (written before it, so the run had already passed it). Lock assertions unchanged. `*PlanExecutionServiceTest` passes against the real implementation. Uncommitted.
