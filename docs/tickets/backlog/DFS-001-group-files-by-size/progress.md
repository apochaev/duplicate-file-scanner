# DFS-001 progress

## design (architect)

- Wrote `design.md` (archetype `pipeline-stage`) and stubbed `SizeIndex` and `CandidateGroup` in `us.pochaev.dfs.scanner.application`. Every constructor and method throws `UnsupportedOperationException("not implemented: DFS-001")`. `module-info.java` is unchanged because nothing is exported.
- Files touched: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/SizeIndex.java`, `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/CandidateGroup.java`, `docs/tickets/backlog/DFS-001-group-files-by-size/design.md`, this file, `learnings.md`.
- Verified: `./gradlew :file-scanner:compileJava` succeeds.
- Expected commit: `feat(file-scanner): add SizeIndex and CandidateGroup stubs [DFS-001]`.

## test (test-author)

- Wrote `SizeIndexTest` (20 tests, AC-1 through AC-6, plus 2 for AC-7) and `CandidateGroupTest` (5 tests, all AC-7) in `us.pochaev.dfs.scanner.application`, one test file per production type, following existing plain-JUnit-5 style (no AssertJ). All logic is pure in-memory: `FileEntry` values built directly with `Path.of(...)` and fixed `Instant`s, no fakes, no fixtures, no real filesystem.
- Filled in `test-plan.md` mapping all 22 criterion/scenario rows to their `@DisplayName`s.
- Verified: `./gradlew :file-scanner:test --tests "us.pochaev.dfs.scanner.application.*"` runs 22 tests, all 22 fail, and every JUnit XML report's failure `message` is exactly `java.lang.UnsupportedOperationException: not implemented: DFS-001` (checked via `grep -o 'message="[^"]*"'` across all report files, one distinct value).
- Files touched: `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/SizeIndexTest.java`, `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/CandidateGroupTest.java`, `docs/tickets/backlog/DFS-001-group-files-by-size/test-plan.md`, this file, `learnings.md`.
- Expected commit: `test(file-scanner): add SizeIndex and CandidateGroup tests [DFS-001]`.
