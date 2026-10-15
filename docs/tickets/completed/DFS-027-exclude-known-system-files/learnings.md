## design (architect)

- The architect definition says the red gate accepts a failure "on its own only when the failure, or its root cause, is `not implemented: <ID>`", but does not say how it treats a *new* story test that fails red by assertion without reaching a stub (for example a missing-validation test where nothing throws). Because the architect may not read `tools/story-gate`, I designed defensively so every new behavior reaches a stub (stubbed the whole `ScanResult` compact constructor and `ScanConfig.defaults()`, DFS-005 style), which turns most existing scan tests red on the stub too. One sentence in `architect.md` or `archetype.md` stating the rule for new tests would let a later design stub more narrowly.
- A test class that calls a stubbed method from a `static` field initializer (`ScanServiceTest.CONFIG = ScanConfig.defaults()`) fails every test after the first with `NoClassDefFoundError`, whose root cause is no longer the stub's `UnsupportedOperationException`. The archetype's Expected breakage guidance names CLI and end-to-end tests but not this case; worth a line in `archetype.md` (or a test convention to avoid static fixtures that call domain factories).

## tests (test-author)

- `ScanServiceTest`'s static `CONFIG = ScanConfig.defaults()` made a stubbed `defaults()` kill the whole class with `NoClassDefFoundError`; turning it into a method fixed it. The architect's "expected breakage" note could say to do that in the tests phase instead of leaving it to the red gate.

- DFS-027 implement: two frozen tests in ScanCommandTest are defective. (1) `textReportNoDuplicates` (DFS-023.AC-6/7, ~line 371) still expects the summary without ", 0 excluded"; contradicts DFS-027.AC-3 and the test at line ~359. (2) `matchIsCaseSensitive` (~line 575) renames .DS_Store to .ds_store with Files.move; on Windows (case-insensitive NTFS) the rename is a no-op, so the test fails; CLAUDE.md says case tests should skip on Windows via Assumptions.

## verify (verifier)

- The frozen `matchIsCaseSensitive` test originally renamed with `Files.move`, which is a silent no-op for a case-only rename on NTFS; the fix (delete and recreate under the new name) is better than skipping via `Assumptions`, since NTFS preserves the case given at creation. Worth a line in the test guide: for case-variant names, create the file under the target name instead of renaming, and only skip when two case variants must coexist in one folder.
- A test-author updating existing summary assertions for an additive field should grep every literal summary string (here `0 skipped\n` in `textReportNoDuplicates` was missed), not only the ones listed in design.md's call-site list.

## Entries

### DFS-027.L-1: A static field initializer that calls a stub hides the stub behind NoClassDefFoundError

- Status: proposed
- Impact: local (one archetype's guide)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`
- Change: add: when an existing test class calls a stubbed method from a `static` field initializer, the test-author turns that field into a method in the tests phase, so each test fails on the stub's own exception rather than `NoClassDefFoundError` after the first.
- Evidence: DFS-027 `ScanServiceTest.CONFIG = ScanConfig.defaults()` with `defaults()` stubbed (design commit cf20387); the architect declared it as expected breakage, the test-author converted it to `config()` in d96c5e1; both phases flagged it in learnings.md.
- Recurrences: 1 (DFS-027)
- Recommendation: apply; two phases hit it, and the fix is one line in the guide.
- Decision:

### DFS-027.L-2: The architect is not told that every story test must fail on a stub

- Status: proposed
- Impact: shared (an agent prompt: `.claude/agents/architect.md`)
- Target: `.claude/agents/architect.md`
- Change: in step 3, add: the red gate requires every new story test to fail with `not implemented: <TICKET-ID>` (a story test that fails on a plain assertion fails the gate), so every new behavior a criterion names must be reachable only through a stubbed method; stub the narrowest method that gives that, and say in `design.md` which stub each criterion reaches.
- Evidence: DFS-027 design notes in learnings.md ("designed defensively so every new behavior reaches a stub… turns most existing scan tests red"); `tools/story-gate/RedCheck.java` line 11; red run on d96c5e1: 114 other tests failed on the ticket's stub. Same rule exists for the test-author as DFS-026.L-3 in test-guide.md.
- Recurrences: 1 (DFS-027)
- Recommendation: apply; it states a rule the gate already enforces, where the agent that must design for it can read it.
- Decision:

### DFS-027.L-3: A rename to a case variant does nothing on NTFS; create the file under the new name

- Status: proposed
- Impact: shared (CLAUDE.md, Code and test conventions)
- Target: `CLAUDE.md`
- Change: in the fixtures bullet, after "Expect the case-sensitivity test to skip on Windows and macOS", add: a test that needs one file under a case-variant name creates it under that name (delete and write) rather than renaming it with `Files.move`, which is a silent no-op on Windows; skip only when two case variants must exist in one folder.
- Evidence: DFS-027 defect 1 (plan.json): `ScanCommandTest.matchIsCaseSensitive` renamed `.DS_Store` to `.ds_store` with `Files.move`, excluded stayed 4 instead of 2, implement attempt 1 failed-gate; fixed in 901c596 by delete-and-write, no skip needed; the verifier flagged it in learnings.md.
- Recurrences: 1 (DFS-027)
- Recommendation: apply; it cost a failed gate and a rework, and the current CLAUDE.md wording points toward skipping instead.
- Decision:

### DFS-027.L-4: A new field in a summary or report misses assertions that spell the old line out

- Status: proposed
- Impact: local (one archetype's guide)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`
- Change: add: when a ticket adds a field to an existing output line (a JSONL summary, a text report line), the test-author searches the test trees for every literal of that line (for example `grep -rn "skipped" */src/test`) and updates each, not only the call sites `design.md` lists; the compiler finds changed signatures but not expected text.
- Evidence: DFS-027 defect 1 (plan.json): `ScanCommandTest.textReportNoDuplicates` still expected `Summary: 0 sets, 0 files, 0 B reclaimable, 0 skipped`, failing green at the implement phase; fixed in 901c596; the verifier flagged it in learnings.md.
- Recurrences: 1 (DFS-027)
- Recommendation: apply; one missed literal cost a failed gate and a rework round.
- Decision:
