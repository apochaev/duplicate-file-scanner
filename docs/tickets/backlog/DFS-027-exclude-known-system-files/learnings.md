## design (architect)

- The architect definition says the red gate accepts a failure "on its own only when the failure, or its root cause, is `not implemented: <ID>`", but does not say how it treats a *new* story test that fails red by assertion without reaching a stub (for example a missing-validation test where nothing throws). Because the architect may not read `tools/story-gate`, I designed defensively so every new behavior reaches a stub (stubbed the whole `ScanResult` compact constructor and `ScanConfig.defaults()`, DFS-005 style), which turns most existing scan tests red on the stub too. One sentence in `architect.md` or `archetype.md` stating the rule for new tests would let a later design stub more narrowly.
- A test class that calls a stubbed method from a `static` field initializer (`ScanServiceTest.CONFIG = ScanConfig.defaults()`) fails every test after the first with `NoClassDefFoundError`, whose root cause is no longer the stub's `UnsupportedOperationException`. The archetype's Expected breakage guidance names CLI and end-to-end tests but not this case; worth a line in `archetype.md` (or a test convention to avoid static fixtures that call domain factories).

## tests (test-author)

- `ScanServiceTest`'s static `CONFIG = ScanConfig.defaults()` made a stubbed `defaults()` kill the whole class with `NoClassDefFoundError`; turning it into a method fixed it. The architect's "expected breakage" note could say to do that in the tests phase instead of leaving it to the red gate.

- DFS-027 implement: two frozen tests in ScanCommandTest are defective. (1) `textReportNoDuplicates` (DFS-023.AC-6/7, ~line 371) still expects the summary without ", 0 excluded"; contradicts DFS-027.AC-3 and the test at line ~359. (2) `matchIsCaseSensitive` (~line 575) renames .DS_Store to .ds_store with Files.move; on Windows (case-insensitive NTFS) the rename is a no-op, so the test fails; CLAUDE.md says case tests should skip on Windows via Assumptions.

## verify (verifier)

- The frozen `matchIsCaseSensitive` test originally renamed with `Files.move`, which is a silent no-op for a case-only rename on NTFS; the fix (delete and recreate under the new name) is better than skipping via `Assumptions`, since NTFS preserves the case given at creation. Worth a line in the test guide: for case-variant names, create the file under the target name instead of renaming, and only skip when two case variants must coexist in one folder.
- A test-author updating existing summary assertions for an additive field should grep every literal summary string (here `0 skipped\n` in `textReportNoDuplicates` was missed), not only the ones listed in design.md's call-site list.
