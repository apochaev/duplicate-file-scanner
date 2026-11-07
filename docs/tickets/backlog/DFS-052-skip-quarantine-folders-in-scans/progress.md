# DFS-052 progress

## design (architect, 2026-11-07)

- Wrote `design.md` (archetype `use-case`), every checklist item addressed or marked not applicable.
- Added `domain.model.QuarantineFolder` (constant `NAME = ".dfs-quarantine"`), `SkipReason.QUARANTINE`, `ErrorCode.LOCATION_IN_QUARANTINE`; updated `StartScan` Javadoc.
- Stubs (throw `not implemented: DFS-052`): `ScanService.firstInQuarantine(List<Path>)`, called in `begin` before the resolve loop; `NioFileTreeWalker.Visitor.isQuarantine(Path)`, called in `preVisitDirectory` for every non-root plain directory. `NioQuarantineDeleter.QUARANTINE_FOLDER` now `= QuarantineFolder.NAME`.
- `:file-scanner:compileJava` green. `./gradlew test --continue`: 15 classes fail, all directly on the stub except `LaunchScriptTest` (child process, exit code), declared under Expected breakage.
- Deviation flagged: AC-5's "exit 2" conflicts with the existing refused-start exit code 1 and "no CLI source change"; design keeps 1 (design.md open note 6).
- Expected commit: `feat(file-scanner): design quarantine folder skipping [DFS-052]` (stubs + design.md + progress/learnings), made by the orchestrator.
