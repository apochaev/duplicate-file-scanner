# DFS-052 progress

## design (architect, 2026-11-07)

- Wrote `design.md` (archetype `use-case`), every checklist item addressed or marked not applicable.
- Added `domain.model.QuarantineFolder` (constant `NAME = ".dfs-quarantine"`), `SkipReason.QUARANTINE`, `ErrorCode.LOCATION_IN_QUARANTINE`; updated `StartScan` Javadoc.
- Stubs (throw `not implemented: DFS-052`): `ScanService.firstInQuarantine(List<Path>)`, called in `begin` before the resolve loop; `NioFileTreeWalker.Visitor.isQuarantine(Path)`, called in `preVisitDirectory` for every non-root plain directory. `NioQuarantineDeleter.QUARANTINE_FOLDER` now `= QuarantineFolder.NAME`.
- `:file-scanner:compileJava` green. `./gradlew test --continue`: 15 classes fail, all directly on the stub except `LaunchScriptTest` (child process, exit code), declared under Expected breakage.
- Deviation flagged: AC-5's "exit 2" conflicts with the existing refused-start exit code 1 and "no CLI source change"; design keeps 1 (design.md open note 6).
- Expected commit: `feat(file-scanner): design quarantine folder skipping [DFS-052]` (stubs + design.md + progress/learnings), made by the orchestrator.

## test (test-author, 2026-11-07)

- Wrote 5 test locations (see test-plan.md): `NioFileTreeWalkerQuarantineTest`, `ScanQuarantineEndToEndTest`, `ScanServiceQuarantineTest`, plus tests appended to `ScanCommandTest` and `ScanApiTest`. 18 invocations, all red on `not implemented: DFS-052`.
- AC-5 refusal asserts `ScanCommand.ERROR` (1) per the user's decision, not the ticket's 2.
- Expected commit: `test(file-scanner): add quarantine skipping tests [DFS-052]`, made by the orchestrator.

## Implement

Implemented `NioFileTreeWalker.Visitor.isQuarantine` and `ScanService.firstInQuarantine`; CLAUDE.md lists QUARANTINE and the new safety invariant. Build green. Expected commit: feat(file-scanner): skip quarantine folders when scanning [DFS-052]. CLI refusal exits 1, not 2 (design open note 6).
