# DFS-005: Run a scan end to end — verification

## Summary

- DFS-005.AC-1 met: walk, then SizeIndex, then partial, full and hard-link phases; the fake-backed test asserts the exact set and copies, and that skipped is empty.
- DFS-005.AC-2 met: walker `Skipped` goes through the sink into `skipped`; asserted by equality.
- DFS-005.AC-3 met: all three `onDrop` constructors report UNREADABLE with a stage prefix; tested per stage, at pipeline level, and for the null-message fallback.
- DFS-005.AC-4 met: the full event list is asserted by exact equality.
- DFS-005.AC-5 met: one sink appends and notifies; the test checks the skip falls between FULL_HASH and HARD_LINKS.
- DFS-005.AC-6 met: out and err are captured and restored in `finally`; both asserted empty.
- DFS-005.AC-7 met: defaults pinned to the stage constants; empty files and minSize 4096 are both tested.
- DFS-005.AC-8 met: each of the three invalid values is tested.
- DFS-005.AC-9 met: sorting plus the `startsWith` dedupe; both scenarios assert the exact walked roots.
- DFS-005.AC-10 met: a real junction or symlink, a missing path and a regular file are tested on `@TempDir`.
- DFS-005.AC-11 met.
- DFS-005.AC-12 met: everything is resolved before any event; the test asserts the walker and the listener were untouched.
- DFS-005.AC-13 met: a failed walk is recorded as `UNREADABLE` and the next location is still walked.
- DFS-005.AC-14 met: the fixed test's expected `[true, true, false]` matches the scenario; it also checks D:/b is never walked and the CancellationException.
- DFS-005.AC-15 met: hashed paths equal b1/b2 only and the identity port is never called. The test relies on the exact number of stop checks (7), so it is fragile.
- DFS-005.AC-16 met: set order, skipped order and the order of listener `Skipped` events are all asserted.
- DFS-005.AC-17 met: the fixed near pair (base 1) differs from the same pair at every byte and from each other only at offset 102400, which is outside the 64 KB head and tail samples, so the full-hash stage is really exercised. Two minor gaps are noted below.
- DFS-005.AC-18 met: two threads with a barrier; each result's sets and skips are asserted exactly.
- Ready for the human gate. No phase needs re-running; the non-blocking notes are under "Concerns".

## Per-criterion detail

The implementation (`ScanPipeline`, `ScanConfig`, `ScanEvent`, `ScanResult`, `NioLocationResolver`, the three `onDrop` constructors) follows design.md's `scan` algorithm step by step:

- Null checks, then IAE on an empty list.
- Every location is resolved before any event (AC-12).
- Locations are sorted ascending, then nested ones are dropped.
- Per-scan state is local to the call (AC-18).
- The stop signal is checked before each walk event is handled. On `true` the listener returns `false` and a flag is set, and the scan throws after that root's walk (AC-14).
- The stop signal is checked again before each group in each later phase (AC-15).
- `PhaseStarted` is sent even when a phase has 0 groups.
- `GroupDone(i+1, total)` is sent with `total` equal to the size of the phase's input (AC-4).

The existing constructors now delegate with a no-op sink, as decision 2 allows once the new constructors are real. The detail fallback `PartialHashFilter.detail` uses the class simple name when the message is null, as design.md says.

### The two fixed tests (commit 23db805)

- **AC-14.** `stopAfterChecks(2)` gives check results false, false, true at the three walk events. The pipeline returns `true`, `true`, `false`. That is exactly the scenario's "returns false for the 3rd walk event", so the old `[false, false, false]` expectation contradicted the scenario.
  - The fake walker stops on `false`, and the test asserts `walkedRoots == [C:/a]`, so it proves "D:/b is never walked".
  - The `fail(...)` after `scan` proves the exception.
  - Not asserted, and not required: that f3 never reached `SizeIndex`. The pipeline does skip it, since the check comes before handling.
  - The test is meaningful, not vacuous.
- **AC-17.** `same*` uses `b = i`, `near1` uses `b = i + 1`, and `near2` is `near1` with byte 102400 incremented.
  - `near*` differs from `same*` at every byte, so the old collision is gone.
  - 200 KB is above the 128 KB threshold, so the partial hash runs. Its head `[0, 65536)` and tail `[139264, 204800)` do not cover offset 102400, so `near1` and `near2` pass the partial stage together and are separated only by the full SHA-256.
  - This genuinely proves "does not group a same-size pair differing only in its middle byte" end to end.
  - The hard link is checked with exact path sets: `{same1, link}` as one copy, two copies in total.
  - The fixtures include `empty/zero-a.dat` and `empty/zero-b.dat`, so the "no empty file in any set" assertion is not vacuous.
  - The gate reported only the pre-existing skip in `NioFileTreeWalkerTest`, so this test ran (it was not skipped) on this machine.

## Test quality

- The tests assert on outputs and observable port interactions: exact sets, copies, skipped lists, full event sequences, walked roots, hash calls and identity calls. None of them restates the implementation.
- The fakes fail loudly on unconfigured paths (`fail("no digest configured…")`), so an unexpected stage call cannot pass silently.
- AC-15's identity assertion is meaningful: `HardLinkDetector` calls `isSameFile` for the second file of any pair, so a run that reached HARD_LINKS would record calls.
- The tests expecting an exception use try/catch with `fail`, per the L-013 red-gate convention. Since the stubs are gone this is only stylistic.

## Invariants (CLAUDE.md)

- **Never delete the last copy, re-verify at execution time, preview is a pure query:** not applicable. No deletion code is in this diff.
- **Hard links are not duplicates:** `HardLinkDetector` is unchanged except for drop reporting. AC-17 proves on real files that a hard link counts as one physical copy.
- **No symlink or junction following:** the walker is unchanged. `NioLocationResolver` resolves only a link given as a start location, which matches CLAUDE.md's "A link given as the root itself is resolved". Nothing inside the tree is followed.
- **Empty files excluded:** `ScanConfig` rejects `minSize < 1`. AC-7 checks this with fakes and AC-17 with real files.
- **Spring-friendliness:** `domain.model` has no Spring types, the core prints nothing (AC-6), progress goes through the listener, and the service holds no state in fields.
- **Layering:** `domain.model` types depend on nothing outside it. `application` uses `infra.port` only. `module-info.java` is unchanged, and the ArchUnit and ModuleExports tests are green per the gate.

## Design conformance

- The diff matches design.md's Public API, Layer placement and all 14 Checklist disposition lines.
- The tests landed where design.md placed them: AC-17 and AC-10 in the `infra.adapter` test package, with `Fixtures` unchanged.
- No pre-existing test file changed. Only 5 new test files were added.
- No file outside `touches` or the story folder changed.

## Concerns (non-blocking)

1. **AC-17 portability.** The test compares result paths, which are rooted at `toRealPath()` of the `@TempDir`, against paths built from the unresolved `@TempDir`. On macOS (`/var` is a symlink to `/private/var`) or a Windows temp path with 8.3 short names, these differ and the test would fail. It passes here. Suggested fix in a later ticket: build the expected paths from `root.toRealPath()`.
2. **AC-17 coverage gaps.**
   - Ticket decision 9 says the expected sets are "derived from the fixtures' real contents (`Fixtures.FILES`)", but the test asserts nothing about the committed fixtures' duplicate sets beyond the empty-file check.
   - Decision 9 also says only the link-specific assertion is skipped when the hard link cannot be created, but `createHardLinkOrSkip` skips the whole test.
   - Neither affects the AC text itself.
3. **AC-15 fragility.** The test hard-codes 7 stop-signal checks, which depends on exactly where the pipeline checks. Its key assertions (hashed paths and no identity calls) are sound. A future change to the check points would break it noisily, not silently.
4. **Minor style.** The stages use the fully qualified `us.pochaev.dfs.scanner.domain.model.SkipReason` instead of an import. `ScanPipeline.runGroupPhase` uses a fully qualified `java.util.function.Function`. This is cosmetic.
