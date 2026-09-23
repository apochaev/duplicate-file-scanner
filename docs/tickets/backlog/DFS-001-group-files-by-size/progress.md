# DFS-001 progress

## design (architect)

- Wrote `design.md` (archetype `pipeline-stage`) and stubbed `SizeIndex` and `CandidateGroup` in `us.pochaev.dfs.scanner.application`. Every constructor and method throws `UnsupportedOperationException("not implemented: DFS-001")`. `module-info.java` is unchanged because nothing is exported.
- Files touched: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/SizeIndex.java`, `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/CandidateGroup.java`, `docs/tickets/backlog/DFS-001-group-files-by-size/design.md`, this file, `learnings.md`.
- Verified: `./gradlew :file-scanner:compileJava` succeeds.
- Expected commit: `feat(file-scanner): add SizeIndex and CandidateGroup stubs [DFS-001]`.
