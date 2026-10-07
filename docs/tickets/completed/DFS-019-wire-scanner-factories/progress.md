# DFS-019 progress

## design (architect)

- Wrote `design.md` from the use-case archetype's `design.md.tmpl` (version 2). Every checklist item is either addressed or marked not applicable.
- Stubbed `ScannerFactories.scanUseCases(Executor)`, which throws `not implemented: DFS-019`. Added the data record `ScanUseCases` (not stubbed; its non-null checks are listed for the implementer).
- `./gradlew build` is green. With the two `allowEmptyShould(true)` flags removed temporarily, `ArchitectureTest` passes; the file was reverted. The real edit is assigned to the test-author in `design.md`.
- Files: `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/factory/ScannerFactories.java` (changed), `.../domain/factory/ScanUseCases.java` (new), this story folder's `design.md`, `progress.md` and `learnings.md`.
- Expected commit: `feat(file-scanner): design ScannerFactories wiring [DFS-019]` (made by the orchestrator).

## test (test-author)

- Wrote `ScannerFactoriesTest` (7 tests, one per scenario, AC-1 to AC-6) and `test-plan.md`. Edited `ArchitectureTest` per design decision 5 (flags removed from two rules; util rule keeps it with a comment); its 3 tests pass.
- All 7 new tests fail on the stub's `not implemented: DFS-019` (AC-5's `assertThrows` reports it as the cause of the wrong-type failure).
- Expected commit: `test(file-scanner): add ScannerFactories tests [DFS-019]` (made by the orchestrator).

## test, attempt 2 (test-author)

- Fixed the red-gate defect: `DFS-019.AC-5 A null executor is a caller bug` now uses `try { scanUseCases(null); fail(...); } catch (NullPointerException expected) {}` instead of `assertThrows`, so the stub's `UnsupportedOperationException` propagates uncaught. Removed the now-unused `assertThrows` import. No other test in the file used `assertThrows`.
- `StoryGate red DFS-019` passes: 7 story tests fail with the stub, 170 other tests pass (1 skipped). `StoryGate test-phase DFS-019` passes traceability (6 criteria, 7 scenarios).
- Expected commit: `test(file-scanner): add ScannerFactories tests [DFS-019]` (made by the orchestrator).

## Implement
Implemented ScannerFactories.scanUseCases wiring and ScanUseCases non-null checks. Expected commit: feat(file-scanner): wire scanner factories [DFS-019]
