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

## implement (implementer)

- Filled in `CandidateGroup`'s compact constructor (null/size/count checks, `List.copyOf` defensive copy) and `SizeIndex` (`Map<Long, List<FileEntry>>` backing store, `add`/`merge` with the documented preconditions, `groups()` sorting by path then collapsing equal paths to the later `lastModified`, dropping singletons, sorting groups by size descending, returning `List.copyOf`).
- `merge(other)` short-circuits when `other == this` (Open design note in `design.md`), so it never mutates a collection it is iterating.
- Measured heap for 1,000,000 `FileEntry` values (half forming two-file groups, half dropped as unique sizes) with a throwaway class run against the compiled classes (not committed): ~213 bytes/entry retained, `groups()` over the full index ~188 ms. Recorded in `DFS-001-ticket.md`'s Definition of done.
- Files touched: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/SizeIndex.java`, `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/CandidateGroup.java`, `docs/tickets/backlog/DFS-001-group-files-by-size/DFS-001-ticket.md` (heap measurement recorded per DoD), this file.
- Verified: `./gradlew build` is green (includes ArchUnit layering tests and `ModuleExportsTest`); no `not implemented:` marker remains under `file-scanner/src/main`; no test file was modified (`git diff --stat -- file-scanner/src/test` is empty).
- Expected commit: `feat(file-scanner): implement SizeIndex and CandidateGroup [DFS-001]`.
