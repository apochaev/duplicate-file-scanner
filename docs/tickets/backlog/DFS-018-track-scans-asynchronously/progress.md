# DFS-018 progress

## design (architect)

- Wrote `design.md` (use-case archetype, all 14 checklist items addressed, none marked not applicable).
- New stubs: `domain.model` `Result`, `ErrorCode`, `ScanSnapshot`; `domain.usecases.command.StartScan`; `domain.usecases.query.GetScanResult`; `application` `ScanService`, `ScanRunner` (package-private); `infra.port` `ScanRecord`, `ScanRepository`; `infra.adapter.InMemoryScanRepository`.
- Changed in place: `ScanStatus` (six values), `CancelScan` and `GetScanStatus` (return `Result<ScanSnapshot>`), `ScanPipeline` (`implements ScanRunner`, stubbed `run`).
- Every behavioural method and both `ScanService` constructors throw `UnsupportedOperationException("not implemented: DFS-018")`. `module-info.java` unchanged.
- `./gradlew :file-scanner:compileJava :file-scanner:compileTestJava` green.
- Expected commit: `feat(file-scanner): design scan lifecycle use cases and stubs [DFS-018]` (made by the orchestrator).
