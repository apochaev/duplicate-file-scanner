# DFS-003 progress

## design (architect, 2026-09-23)

- Wrote `design.md` (Summary, Public API, placement, all 14 checklist items dispositioned).
- New stubs: `domain/model/DuplicateSet.java` (compact constructor), `application/FullHashConfirmer.java` (`confirm`); re-stubbed `infra/adapter/NioContentHasher.fullHash` from DFS-002's permanent message to `not implemented: DFS-003`. All under `file-scanner/src/main/java/us/pochaev/dfs/scanner/`.
- `module-info.java` and `ContentHasher` unchanged.
- `./gradlew :file-scanner:compileJava` and `:file-scanner:compileTestJava` pass.
- Expected commit: `feat(file-scanner): design full-hash stage stubs [DFS-003]` (made by the orchestrator).

## tests (test-author, 2026-09-24)

- New `file-scanner/src/test/java/us/pochaev/dfs/scanner/domain/model/DuplicateSetTest.java` (AC-6): 7 tests on the compact constructor's invariants and the defensive copy, `try/fail/catch` style matching `CandidateGroupTest`.
- New `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/FullHashConfirmerTest.java` (AC-1 through AC-5, AC-7, AC-10): 10 tests against a hand-written `FakeContentHasher` fake (style of `PartialHashFilterTest`'s `RecordingContentHasher`); no real bytes needed since digests come from the fake per design.md's checklist item 12.
- Edited `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasherTest.java` (AC-8, AC-9): removed `fullHashIsStubbedForDfs003` (DFS-002's stand-in test) and its Javadoc paragraph, rewrote the class Javadoc to cover both `sampleHash` and `fullHash`, and added two AC-8 tests using real `@TempDir` bytes (a known 3-byte input against its known SHA-256, and a 300 KB pair plus a middle-byte-differing third file for discrimination). The two pre-existing DFS-002 `sampleHash` tests are untouched and still pass.
- Wrote `test-plan.md`, mapping every criterion to its scenario(s) and test(s).
- Ran `./gradlew :file-scanner:test --tests "...DuplicateSetTest" --tests "...FullHashConfirmerTest" --tests "...NioContentHasherTest"`: 21 tests ran, 19 failed (all 17 new tests plus the 2 new AC-8 tests), all with `UnsupportedOperationException("not implemented: DFS-003")` and nothing else (confirmed via `grep` over the JUnit XML). The 2 remaining tests are DFS-002's pre-existing `sampleHash` tests, which pass as expected (not this story's tests).
- Did not touch any `src/main` file. Expected commit: `test(file-scanner): add full-hash stage tests [DFS-003]` (made by the orchestrator after the red gate).

## tests, attempt 2 (test-author, 2026-09-24)

- Fixed defect 1 (traceability, AC-9): added `NioContentHasherTest.fullHashReturnsADigestInsteadOfThrowing` (`DFS-003.AC-9 fullHash returns a digest instead of throwing`): `fullHash` on a real `@TempDir` file must return a 64-char lowercase hex digest; the stub's exception is not caught. Updated the AC-9 row of `test-plan.md`. No other test changed.
- Re-ran the three story test classes: 22 tests, 20 failed, all with `UnsupportedOperationException: not implemented: DFS-003` (JUnit XML); the 2 passing are DFS-002's `sampleHash` tests. Expected commit unchanged: `test(file-scanner): add full-hash stage tests [DFS-003]`.

## implement (implementer, 2026-09-24)

- `domain/model/DuplicateSet.java`: compact constructor now enforces AC-6's invariants (non-null/non-blank digest, non-null files with no null element via `List.copyOf`, at least two files, every entry's size equal to `size`); no sorting, as design.md specifies.
- `application/FullHashConfirmer.java`: `confirm` null-checks `groups` and each element, then for each `CandidateGroup` sorts members by ascending path, calls `hasher.fullHash` per member (dropping one on `IOException` without failing the rest, per AC-7), groups by digest in a `LinkedHashMap` (preserving ascending-smallest-path order, matching `PartialHashFilter`'s pattern), and emits one `DuplicateSet` per digest with two or more members, dropping singletons (AC-3). Output list is `List.copyOf`.
- `infra/adapter/NioContentHasher.java`: `fullHash` streams the file through a fresh SHA-256 `MessageDigest` via a `FileChannel` and a 64 KB `ByteBuffer`, never loading the whole file into memory; returns lowercase hex via `HexFormat`.
- `./gradlew build` is green (file-scanner and cli, including ArchUnit and `ModuleExportsTest`). No `not implemented: DFS-003` markers remain under `src/main`. No test file was touched.
- Expected commit: `feat(file-scanner): implement full-hash stage [DFS-003]` (made by the orchestrator).
