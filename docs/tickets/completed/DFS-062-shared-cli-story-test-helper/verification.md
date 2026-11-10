## Product owner review

### What you can do now
CLI-level story tests can call `StubFailures.rethrow(out, err)` to turn a stub's "not implemented:" text in CLI output into `UnsupportedOperationException`, so they meet the red gate. No per-ticket copy is left behind: the private `sysScan` in `ScanCommandTest` is gone.

### Acceptance criteria
- DFS-062.AC-1: met. The first `not implemented:` line is rethrown, from stderr or stdout (two tests).
- DFS-062.AC-2: met. Output without the marker returns normally.

### Try it
Nothing user-visible. `./gradlew :cli:test --tests "*StubFailuresTest"` runs the 3 tests. `grep -c sysScan cli/src/test/java/us/pochaev/dfs/cli/ScanCommandTest.java` prints 0.

### Decided without you
- `StubFailures` is package-private (`final`, private constructor), since only `cli` tests use it.
- Stderr is searched before stdout, as `sysScan` did.
- The 10 DFS-061 tests now call `scan(...)` directly and no longer rethrow a stub failure. No local wrapper was added, because Decision 1 makes one optional.

### Known limits
- No live stub exists to exercise the helper end to end. Its tests use plain strings.
- The pointer in `.claude/archetypes/use-case/test-guide.md` is left to `/learn apply DFS-061.L-7` after close.

### Evidence
- Full `./gradlew build` exit 0. `StubFailuresTest` has 3 tests, all passed. `ScanCommandTest` passed.
- Changed files: `ScanCommandTest.java` (`sysScan` removed, 10 calls renamed), new `StubFailures.java` and `StubFailuresTest.java`, and the ticket status. Everything is inside the `touches` path.

### How it went
Hand-implemented in one pass, no retries.

Ready for the user's approval.
