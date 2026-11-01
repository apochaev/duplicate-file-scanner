# DFS-033 progress

## design (architect, attempt 1)

- Wrote `design.md` (Summary first, every use-case checklist item disposed; items 1, 3, 4, 6, 7, 8, 9, 11, 12 not applicable).
- Stubs (throw `not implemented: DFS-033`): `application/CheckPlanDriftService.check`, `infra/adapter/NioFileAttributesReader.read`.
- New, no bodies or checks: `domain/usecases/query/CheckPlanDrift`, `domain/model/{DriftReport,PathDrift (nested Role),DriftKind}`, `infra/port/FileAttributesReader`.
- Edited: `ErrorCode` (`PLAN_NOT_FROM_SCAN`), `ScannerFactories.checkPlanDrift()` (real), `ScannerAutoConfiguration.checkPlanDrift` bean (real).
- `./gradlew :file-scanner:compileJava :scanner-spring:compileJava` passes. Expected breakage: none. `module-info.java` unchanged.
- Expected commit: `feat(file-scanner): design plan drift check stubs [DFS-033]` (made by the orchestrator).

## Implement
Implemented CheckPlanDriftService.check, NioFileAttributesReader.read and the DriftReport/PathDrift constructor checks per design.md. ./gradlew build green. Expected commit: feat(file-scanner): check plan drift [DFS-033].
