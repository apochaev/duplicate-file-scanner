## design (architect, attempt 1)

- Wrote `design.md` and stubs: `application/HardLinkDetector.java` (constructor + `detect`, both throw), `infra/port/FileIdentity.java` (interface), `infra/adapter/NioFileIdentity.java` (`isSameFile` throws). All throw `not implemented: DFS-004`.
- No change to `domain.model`, `module-info.java` or `FullHashConfirmer`.
- `./gradlew :file-scanner:compileJava :file-scanner:compileTestJava` succeeds.
- Expected commit: `feat(file-scanner): design hard-link stage stubs [DFS-004]` (orchestrator commits).

## test (test-author, attempt 1)

- Wrote `HardLinkDetectorTest` (`application`, 7 tests) against a hand-written `FakeFileIdentity`, covering AC-1, AC-2, AC-3, AC-4, AC-5, AC-8 (two scenarios).
- Wrote `NioFileIdentityTest` (`infra.adapter`, 2 tests) against real files in `@TempDir`, reusing the package's existing `Fixtures.createHardLinkOrSkip` helper, covering AC-9 (both scenarios).
- Wrote `test-plan.md` mapping every live criterion (AC-1..AC-5, AC-8, AC-9) to its scenario and test; AC-6/AC-7 noted as retired, no tests.
- `./gradlew :file-scanner:test`: all 9 new tests fail with exactly `java.lang.UnsupportedOperationException: not implemented: DFS-004`; the other 92 pre-existing tests (1 pre-existing skip, unrelated) still pass.
- No null-argument tests added (not tagged to any live AC); flagged in `learnings.md`.
- Expected commit: `test(file-scanner): add hard-link detection tests [DFS-004]` (orchestrator commits).
