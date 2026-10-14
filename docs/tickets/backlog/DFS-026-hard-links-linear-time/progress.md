## design (architect, attempt 1) — 2026-10-13

- Wrote `design.md` (Summary, API, placement, checklist 1-14, configuration, notes for implementer and test-author).
- Reshaped `FileIdentity` to the single method `Object key(Path) throws IOException`; stubbed `NioFileIdentity.key`, new package-private `WindowsFileId.key` (with private `record Key`), and `HardLinkDetector.regroup`; all throw `UnsupportedOperationException("not implemented: DFS-026")`.
- `./gradlew :file-scanner:compileJava :cli:compileJava` passes. `compileTestJava` does not until the test-author rewrites the four `FileIdentity` fakes (expected; see design.md).
- Build files and CLAUDE.md left to the implementer (AC-7, decision 5).
- Expected commit: `feat(file-scanner): design key-based hard-link detection [DFS-026]` (orchestrator commits).
