## design (architect, attempt 1) — 2026-11-01

- Wrote `design.md` (Summary, API, algorithm, plan-ID canonical encoding, checklist disposition; Expected breakage: none).
- Stubbed `application/PreviewDeletionPlanService.preview` (throws `not implemented: DFS-022`); added the `PreviewDeletionPlan` port, the plan records in `domain.model` (no constructor checks yet; listed in design.md), four `ErrorCode` values, `ScannerFactories.previewDeletionPlan()` and the `ScannerAutoConfiguration` bean (both real one-liners so existing Spring contexts keep starting).
- `./gradlew :file-scanner:compileJava :scanner-spring:compileJava` passes. `module-info.java` unchanged.
- Expected commit: `feat(file-scanner): design deletion plan preview with stubs [DFS-022]`.
