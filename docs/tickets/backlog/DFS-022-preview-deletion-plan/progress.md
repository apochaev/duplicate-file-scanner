## design (architect, attempt 1) — 2026-11-01

- Wrote `design.md` (Summary, API, algorithm, plan-ID canonical encoding, checklist disposition; Expected breakage: none).
- Stubbed `application/PreviewDeletionPlanService.preview` (throws `not implemented: DFS-022`); added the `PreviewDeletionPlan` port, the plan records in `domain.model` (no constructor checks yet; listed in design.md), four `ErrorCode` values, `ScannerFactories.previewDeletionPlan()` and the `ScannerAutoConfiguration` bean (both real one-liners so existing Spring contexts keep starting).
- `./gradlew :file-scanner:compileJava :scanner-spring:compileJava` passes. `module-info.java` unchanged.
- Expected commit: `feat(file-scanner): design deletion plan preview with stubs [DFS-022]`.

## Test phase
Tests written: PreviewDeletionPlanServiceTest (26), PreviewDeletionPlanFactoryTest (1), 2 added to ScannerAutoConfigurationTest; all 29 fail with the stub message only. Expected commit: test(file-scanner): add DFS-022 preview tests [DFS-022]

## Implementation (attempt 1)
Implemented PreviewDeletionPlanService.preview per design.md and added record constructor checks. ./gradlew build green. Commit: orchestrator's.

## tests: user feedback at the done gate (2026-11-01)

Add golden-value tests that pin the plan-ID encoding documented in design.md, so a future change to the encoding fails a test (DFS-033 and execution recompute the ID): at least the empty plan's exact ID (SHA-256 of `19:dfs-deletion-plan/11:01:0`, `2595215d...bd9740`, compute the full value from the documented encoding) and one one-operation plan's exact ID built by hand from the encoding. Tag them with the AC-1 ID (`DFS-022.AC-1 ...`). Do not change existing tests or src/main; the implementation already exists, so these tests must pass against it (verified with the green check, not red).
