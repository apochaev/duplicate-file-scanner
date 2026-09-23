# DFS-002 verification

## Summary

- **DFS-002.AC-1** partially met. `PartialHashFilter` separates members whose sample digests differ, and this is tested with a fake. The real adapter's side has no test: nothing shows that `NioContentHasher` gives different digests for a head difference, and the tail half of the sample is never exercised. A `sampleHash` that returned a constant, or hashed only the head, would pass the whole suite.
- **DFS-002.AC-2** met. A real-byte 300 KB pair that differs at 150 KB stays in one group (`PartialHashFilterRealFileTest`).
- **DFS-002.AC-3** met. Identical digests keep both members, in order.
- **DFS-002.AC-4** met. The hasher records no calls and the same group instance is passed through.
- **DFS-002.AC-5** met. A 2+1 split keeps only the pair.
- **DFS-002.AC-6** met. Input z,a is sampled as a,z. This test asserts on the order of calls, not on the code.
- **DFS-002.AC-7** met. Both scenarios are tested: the `IOException` member is dropped, and the rest of the group survives or the group is dropped.
- **DFS-002.AC-8** met. The code throws `UnsupportedOperationException("full hash is implemented by DFS-003")`. I checked this wording by reading the code. The test checks only the exception type, as the orchestrator agreed.
- **DFS-002.AC-9** partially met. The code handles both arguments correctly. The tests prove the custom sample size reaches the hasher, but not the custom skip threshold. If the threshold were ignored (default 128 KB), both AC-9 tests would still pass: the fake test's `allMatch` is true on zero calls, and the real test's group would be passed through unhashed.
- No CLAUDE.md safety invariant applies to this stage, and none is violated. The stage only reads, deletes nothing and does not walk.
- The implementation matches `design.md`, including all 14 checklist dispositions. `./gradlew build` is green.
- **Verdict: go back to the test phase** for a small, targeted addition. The implementation looks correct and should need no change. The tests to add:
  1. Adapter tests on real temp files. A head difference and a tail difference each give different `sampleHash` digests. Identical bytes give equal digests.
  2. An AC-9 assertion that the hasher was actually called, for example exactly two calls, so that the threshold override is proven.

  The human may instead accept this as it is and track the gaps separately.

## Per-criterion detail

### AC-1: differences in the head or tail separate files
- Code: `splitBySample` groups members by digest in a `LinkedHashMap`, and each digest bucket becomes its own subgroup. Correct.
- Test: `PartialHashFilterTest.filesThatDifferInSampledBytesAreSeparated` uses a fake that returns two different digests, and asserts that no group remains. This is a fair test of the stage.
- Gap: the scenario is about bytes ("differ in the first byte"). The only real-byte tests (AC-2, AC-9) assert that digests are *equal*. So `NioContentHasher.sampleHash` is never shown to tell two files apart:
  - A constant digest would pass every test.
  - A head-only hash would pass every test. The tail read (`tailStart = size - tailLength`) is untested.
- Reading the code, the tail logic is correct. For a file shorter than `2 * sampleSize` the head and tail overlap, as the port's Javadoc documents.

### AC-2: a difference only in the middle survives
- Test: `PartialHashFilterRealFileTest.filesDifferingOnlyInTheMiddleSurvive` runs on real bytes. The files are 300 KB and the difference is at 150 KB, outside both 64 KB samples.
- Met, although a constant-digest hasher would also pass (see AC-1).

### AC-3: identical files survive
- Fake test. It asserts the exact member list `[a, b]`.
- Met.

### AC-4: small files skip the sample
- Test: 100 KB files, with the default 128 KB threshold. It asserts:
  - zero hasher calls;
  - `assertSame` on the passed-through group.
- Met.
- Minor: the "at or under" boundary (exactly 128 KB) is not tested. The code uses `<=`, which matches.

### AC-5: groups of one are dropped
- Test: a 3-file group with two equal digests and one different. It asserts that exactly `[a, b]` remains.
- Met.

### AC-6: path order
- Test: members are given out of order and the test asserts the recorded call order.
- Met. The code sorts by `Path.compareTo`.

### AC-7: an unreadable file
- Tested with a fake that throws `IOException`, in two tests:
  - 3 files with one unreadable: the other two survive.
  - 2 files with one unreadable: nothing remains.
- Met. On a real disk, a file deleted before sampling surfaces as `NoSuchFileException`, which is an `IOException`, so the same path handles it.

### AC-8: `fullHash` is stubbed for DFS-003
- Wording confirmed by reading the code: `"full hash is implemented by DFS-003"`, with no `not implemented:` prefix.
- The test rethrows if the message still has the `not implemented:` prefix, and passes otherwise.
  - This avoids a vacuous pass against the stub.
  - It couples the test to the stub-message convention. This is already recorded in `learnings.md`.
- Met.

### AC-9: configurable sample size and threshold
- Code: both constructors, the defaults of 65,536 and 131,072, and validation. Correct.
- Tests:
  - `configuredSampleSizeIsPassedToHasher` asserts `allMatch(sampleSize == 4 KB)`. This is vacuously true if no call is made.
  - `customSampleSizeAndThresholdChangeBehavior` is a real-byte test with 10 KB files. It catches an ignored *sample size* only when the custom threshold is honoured. If the threshold were ignored, the group would bypass the sample and still "remain in one group".
- Partially met as far as test evidence goes.

## Test quality

- The fake-based tests assert on output and on recorded interactions, not on implementation details. `RecordingContentHasher` is a clean fake, and its `fullHash` fails loudly if the stage calls it.
- Weaknesses:
  - The real adapter is only tested for equality (AC-1).
  - The threshold half of AC-9 is not proven.
- Not tested, and not required by any AC:
  - checklist item 3's output order when one input group splits into several subgroups, or when several input groups are given;
  - that the returned list is immutable;
  - input validation (null hasher, `sampleSize <= 0`, negative threshold, null groups). The test-author flagged this in `learnings.md` as a refinement gap.

## Design and invariant conformance

- Layering:
  - `PartialHashFilter` is in `application` and depends only on `domain.model` and `infra.port`.
  - `NioContentHasher` is in `infra.adapter` and depends on `infra.port`.
  - `module-info.java` is unchanged.
  - The ArchUnit tests are green.
- The public API, constants, validation, digest type (hex `String`) and output order all match `design.md`.
- Invariants:
  - "Preview is a pure query", "never delete the last copy" and "re-verify at execution time" do not apply: nothing is written or deleted.
  - Symlinks and junctions: `FileChannel.open` would follow a link, but the walker has already excluded links before this stage (DFS-001 and the walker).
  - Hard links: DFS-004's concern.
  - Empty files: bypassed here in any case, since 0 is below the threshold.
- Minor robustness note, not a defect for this ticket: `readAt` stops at EOF and leaves the rest of the buffer zeroed. So if a file shrinks between `channel.size()` and the read, the digest covers zero padding. The worst case is a false "still looks identical", and DFS-003 and the delete-time byte comparison (decision 4) catch that. This fits design.md's deferral of size changes under checklist item 10.

## Friction

- `SizeIndexHeapBenchmark.java` (DFS-001) was changed during DFS-002's implement phase, in commit `66ca663` tagged `[DFS-001]`. The change removes `@Test`/`@Disabled` because `DisabledCheck` has no carve-out for a manual benchmark. The file sits in the `application` test package, so the scope gate accepted it. Still, it is a cross-ticket edit made from inside this story, and it should be taken to `/story learn`.
- The AC-8 "permanently stubbed until a later ticket" case has no convention in the archetype, `RedCheck` or `StubsCheck`. See `learnings.md`.
- There is no test-phase prompt to cover the real adapter's ability to *discriminate*, as opposed to its equality. The pipeline-stage test-guide could require an "adapter distinguishes a head difference and a tail difference" test whenever a stage introduces a hashing adapter.
