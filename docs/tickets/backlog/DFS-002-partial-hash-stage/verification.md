# DFS-002 verification (re-verification after the test-phase retry)

## Summary

- **DFS-002.AC-1** met. The stage splits groups on different digests (fake test). New real-file tests show `NioContentHasher.sampleHash` gives different digests for a head difference and for a tail difference. A constant or head-only `sampleHash` now fails the suite.
- **DFS-002.AC-2** met. A real 300 KB pair that differs only at 150 KB stays in one group.
- **DFS-002.AC-3** met. Identical digests keep both members, in order.
- **DFS-002.AC-4** met. The hasher gets no calls and the same group instance passes through.
- **DFS-002.AC-5** met. A 2+1 split keeps only the pair.
- **DFS-002.AC-6** met. Input z,a is sampled as a,z. The test checks the recorded call order.
- **DFS-002.AC-7** met. Both scenarios are tested: the unreadable member is dropped, and the rest of the group survives or the group is dropped.
- **DFS-002.AC-8** met. The message `"full hash is implemented by DFS-003"` was checked by reading the code. The test checks the exception type, as agreed earlier.
- **DFS-002.AC-9** met. The fake test now also requires exactly 2 hasher calls for 10 KB files under the 8 KB custom threshold. A threshold that fell back to the 128 KB default now fails it. The real-byte test still covers the outcome.
- Both gaps from the previous verification are closed. Production code has not changed since `56f83cc`. The retry added tests and story-folder docs only.
- No CLAUDE.md safety invariant applies to this stage, and none is violated. The implementation still matches `design.md`, including all 14 checklist dispositions.
- `./gradlew build --rerun-tasks` is green: file-scanner and cli, ArchUnit and `ModuleExportsTest` included, 0 failures.
- **Verdict: ready for the human gate.**

## What changed since the previous verification

Commit `421f402` touches only these files:
- `NioContentHasherTest.java`
- `PartialHashFilterTest.java`
- `test-plan.md`, `progress.md` and `learnings.md` in the story folder

`git diff 56f83cc HEAD -- file-scanner/src/main` is empty.

## Per-criterion detail

### AC-1: differences in the head or tail separate files
- Stage side: `PartialHashFilterTest.filesThatDifferInSampledBytesAreSeparated` uses a fake that returns different digests and asserts that no group remains. This is unchanged and sound.
- Adapter side (new): `sampleHashDiffersWhenHeadDiffers` and `sampleHashDiffersWhenTailDiffers`. Each uses a 1000-byte file and a 200-byte sample, so the head (bytes 0 to 199) and the tail (bytes 800 to 999) do not overlap. The files differ at byte 0 or at byte 999, and each test asserts `assertNotEquals` on the real digests.
  - A constant `sampleHash` fails both tests.
  - A head-only `sampleHash` fails the tail test.
  - A tail-only `sampleHash` fails the head test.
- The files are generated in `@TempDir`, and nothing is committed.
- The two halves compose soundly: the stage splits on digest inequality, and the adapter produces that inequality from real bytes. No single test runs the scenario's exact 200 KB setup end to end through both, but the scenario's claim is fully covered.

### AC-2 to AC-8
There are no changes since the previous verification, and the verdicts there stand. See the Summary.

### AC-9: configurable sample size and threshold
- `configuredSampleSizeIsPassedToHasher` now asserts `hasher.calls().size() == 2` before its `allMatch` check. If the custom 8 KB threshold were ignored, the 10 KB group would be passed through unhashed, zero calls would be recorded, and the test would fail. The earlier vacuous `allMatch` pass is gone.
- Together with the real-byte `customSampleSizeAndThresholdChangeBehavior`, both constructor arguments are now proven to take effect.
- The default values (65,536 and 131,072) were checked by reading the code in the previous verification. No production code has changed since.

## Test quality

- The new tests assert on observable output: real digests and the recorded hasher interactions. They do not restate the implementation.
- Still untested, and not required by any AC (as noted before):
  - output order when one group splits into several subgroups, or when several input groups are given;
  - that the returned list is immutable;
  - input validation;
  - the exact 128 KB "at" boundary for AC-4.

## Design and invariant conformance

Unchanged from the previous verification:
- Layering is respected.
- `module-info.java` is untouched.
- No write or delete happens in this stage.
- Links are excluded upstream by the walker.
- Hard links are DFS-004's concern.
- Empty files fall under the threshold.
- The zero-padding-on-shrink robustness note still stands. It is not a defect: DFS-003 and the delete-time byte comparison cover it.

## Friction

- There is no scripted path for reopening a phase at the human gate after a verification says "go back". The orchestrator reset the phases by hand, as recorded in `learnings.md`.
- `StoryGate test-phase` cannot pass on a reopened tests phase. `RedCheck` runs against the current `src/main`, which already holds the real implementation, so every test passes "against stubs". The test-author correctly did not work around this by editing `src/main`. The gate needs a mode that runs against the stub commit, or an orchestrator step for this case.
- These carry over from the previous verification:
  - the cross-ticket edit to `SizeIndexHeapBenchmark` (`66ca663`, `[DFS-001]`);
  - no archetype convention for a method stubbed until a later ticket (AC-8);
  - the test guide does not require discrimination tests for a hashing adapter. This retry was needed only because of that gap.

All of these are candidates for `/story learn`.
