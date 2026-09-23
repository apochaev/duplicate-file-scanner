# DFS-002 progress

## design (architect, 2026-09-22)

- Wrote `design.md`, covering all 14 checklist items and all 9 ACs.
- Stubbed:
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/PartialHashFilter.java`
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/port/ContentHasher.java`
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasher.java`
- `module-info.java` is unchanged, since there is no new exported type.
- `./gradlew :file-scanner:compileJava` passes.
- Expected commit: `feat(file-scanner): add partial-hash stage stubs [DFS-002]`, containing the three stubs and `design.md`, `progress.md` and `learnings.md`.

## test (test-author, 2026-09-22)

- Wrote `test-plan.md`, mapping all 9 ACs (11 scenarios, AC-7 has two, AC-9 has one scenario covered by two tests) to tests.
- Added:
  - `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/PartialHashFilterTest.java` — AC-1, AC-3, AC-4, AC-5, AC-6, AC-7 (both scenarios), and the cheap half of AC-9, all against a private nested `RecordingContentHasher` fake.
  - `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/PartialHashFilterRealFileTest.java` — AC-2 and the real-byte half of AC-9, against the real `NioContentHasher` and files generated in `@TempDir` (a 300 KB and a 10 KB same-size, differs-only-past-the-sample pair, neither committed).
  - `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasherTest.java` — AC-8.
- Ran `./gradlew :file-scanner:test --tests "*PartialHashFilterTest" --tests "*PartialHashFilterRealFileTest" --tests "*NioContentHasherTest"`: 11 tests, 11 failures. The 10 `PartialHashFilter`/adapter-construction tests fail with the exact stub `UnsupportedOperationException: not implemented: DFS-002`, uncaught. `NioContentHasherTest`'s AC-8 test fails with `AssertionFailedError` instead (expected and flagged — see `learnings.md`).
- No invalid-constructor-argument or null-input tests were added: none of DFS-002's nine ACs or eleven scenarios name an invalid-input case (unlike DFS-001's dedicated AC-7), so adding one would have been inventing a scenario. Flagged in the test-author's final report as a possible gap for refinement to consider, not silently added.
- Expected commit: `test(file-scanner): add partial-hash stage tests [DFS-002]`, containing the three new test files, `test-plan.md`, and this `progress.md`/`learnings.md` update.

## Orchestrator note for the retry (defect 1, 2026-09-22)

`java tools/story-gate/StoryGate.java test-phase DFS-002` fails: `RedCheck` requires every story test's failure to be exactly `UnsupportedOperationException` with a message starting `"not implemented:"` (`TestResult.failedWithStub()`), with no exception for a test that legitimately checks a stub's *eventual*, differently-worded final message. AC-8's test currently catches/asserts on the exact "names DFS-003" message, which — run against the still-generic stub — fails as `AssertionFailedError`, not the raw stub exception, so `RedCheck` rejects it.

Fix for this retry: rewrite the AC-8 test (`NioContentHasherTest`) to assert only that `fullHash` throws `UnsupportedOperationException` (the type, checked generically, e.g. via the `try { ...; fail(...); } catch (UnsupportedOperationException expected) {}` pattern with **no assertion on the caught message**) — do not assert the specific "DFS-003" wording in an automated test. That specific wording is already the design's contract (`design.md`'s AC-8 disposition, the stub's own comment) and the ticket's Definition of done ("`ContentHasher.fullHash` throws `UnsupportedOperationException` naming DFS-003"); it's the kind of thing the verifier reads and confirms by eye, not something `RedCheck`'s all-story-tests-must-propagate-the-literal-stub-exception rule can accommodate. This keeps AC-8 covered by a real test (type-checked, fails red now for the right reason, stays green after the implementer's real change) without inventing a mechanism the archetype doesn't have yet. Flag this gap itself in `learnings.md` for `/story learn` — a "permanently-stubbed-until-a-future-ticket" method is a real, undesigned case for this archetype's convention, found running DFS-002 for the first time.

## test retry (test-author, 2026-09-22)

- Rewrote the AC-8 test in `NioContentHasherTest`. It no longer asserts the message. A bare catch of `UnsupportedOperationException` would also catch the stub's own exception and pass against the stub, so the test rethrows the caught exception unchanged while its message still starts with `not implemented:`. Otherwise it swallows the exception, which means it passes once the DFS-003 stand-in is in. It fails with `fail(...)` if nothing is thrown.
- Updated the AC-8 note for the implementer in `test-plan.md`, and added a retry entry to `learnings.md`.
- Ran the three story test classes: 11 tests, 11 failures, all `java.lang.UnsupportedOperationException: not implemented: DFS-002` (checked in the JUnit XML reports). `java tools/story-gate/StoryGate.java test-phase DFS-002` exits 0.
- Expected commit: `test(file-scanner): add partial-hash stage tests [DFS-002]`, the same as before, now including this fix.

## implement (implementer, 2026-09-22)

- Implemented `PartialHashFilter(ContentHasher, int, long)` (argument validation: non-null hasher, positive sample size, non-negative threshold) and `PartialHashFilter.filter` (skip-threshold bypass returning the same instance, ascending-path sampling via `LinkedHashMap<String, List<FileEntry>>` keyed by sample digest so subgroup order follows first-occurrence/smallest-path, dropping an unreadable member on `IOException` without failing its group, dropping any resulting subgroup with fewer than two members).
- Implemented `NioContentHasher.sampleHash` (positional `FileChannel` reads of the head and tail, each capped at the channel's size at read time so short files overlap correctly, concatenated into one SHA-256 digest, returned as lowercase hex via `HexFormat`) and replaced `NioContentHasher.fullHash`'s stub message with `"full hash is implemented by DFS-003"` (no `not implemented:` prefix), per the ticket's explicit instruction.
- `./gradlew build` is green (file-scanner and cli, including ArchUnit and `ModuleExportsTest`). No `not implemented:` marker remains under `file-scanner/src/main`. No test file was changed.
- Files changed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/PartialHashFilter.java`, `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasher.java`. `ContentHasher.java` needed no change (its interface stubs were already just declarations).
- Expected commit: `feat(file-scanner): implement partial-hash stage [DFS-002]`.
