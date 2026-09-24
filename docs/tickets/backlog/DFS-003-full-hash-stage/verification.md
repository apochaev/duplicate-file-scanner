# DFS-003: Confirm duplicates with full SHA-256 hash — verification

## Summary

Verified against branch `tickets/DFS-003-full-hash-stage` (design a52b558, tests 519bbd3, implement ff1d6d4; forked from dad8ca0). I re-ran `./gradlew build` and it passed: `FullHashConfirmerTest` 10/10, `DuplicateSetTest` 7/7, `NioContentHasherTest` 5/5, `ArchitectureTest` 3/3 and `ModuleExportsTest` 3/3, with no failures.

- DFS-003.AC-1 met: equal digests give one set carrying the group's size and the shared digest (3 stage tests).
- DFS-003.AC-2 met: the stage groups by the full digest only. Real-byte middle-byte discrimination is proven on the adapter.
- DFS-003.AC-3 met: a digest held by only one member produces no set. Tested, including the case after an unreadable member is dropped.
- DFS-003.AC-4 met: members are sorted by `Path.compareTo` before hashing. The fake's call log proves the order from input that arrives out of order.
- DFS-003.AC-5 met: a `LinkedHashMap` over the sorted members gives smallest-path order. The test's input order would fail a naive implementation.
- DFS-003.AC-6 met, with minor test gaps: every invariant is enforced and `List.copyOf` is used. The tests check only the exception type, not that the message names the rule, and do not assert that `files()` rejects changes.
- DFS-003.AC-7 met: an `IOException` drops only that member. Both scenarios are tested.
- DFS-003.AC-8 met: `fullHash` streams 64 KB chunks through SHA-256 and returns lowercase hex. Tested with the known "abc" digest and a real 300 KB middle-byte discrimination case.
- DFS-003.AC-9 met: the DFS-002 stand-in test is gone (checked with `git grep`). A new test proves `fullHash` returns a 64-character hex digest.
- DFS-003.AC-10 met: no size check. The set carries `CandidateGroup.size()`, and the test asserts 307200.

All 7 Definition-of-done items are verified and ticked. All 14 checklist dispositions in design.md match the code.

Verdict: **ready for the human gate.** Nothing needs to go back to an earlier phase. The notes below are optional follow-ups, not blockers.

## Per-criterion detail

- **AC-1**: `FullHashConfirmer.splitByFullHash` builds `DuplicateSet(group.size(), digest, members)`. The tests `Identical files form a duplicate set` (asserts size, digest and files), `Small files go straight to the full hash` and `Only the identical files form a set` cover it. The small-file scenario is honest: the stage has no threshold, so a 1 KB group is hashed like any other.
- **AC-2**: The stage keys on the digest only. Stage tests use distinct fake digests, as design.md checklist 12 says. The real "differs only in the middle byte" proof is `NioContentHasherTest` `The real adapter tells files apart by a single byte anywhere` (300 KB, byte at `size/2` flipped). `A group splits into two sets` covers interleaved pairs.
- **AC-3**: `members.size() >= 2` guards set creation. It is covered by the middle-byte test (empty result), the three-file test, and the AC-7 two-file test.
- **AC-4**: The test gives `[z, a]` and asserts the hasher saw `[a, z]`. This discriminates: an implementation without the sort would fail.
- **AC-5**: The first group's input `[x/b, x/d, x/a, x/c]` would put the b/d set first if hashing followed input order. The test asserts a/c, then b/d, then the second group's set. This discriminates too.
- **AC-6**: The constructor order is: null digest (NPE), blank digest (IAE), null files (NPE), `List.copyOf` (NPE on a null element), fewer than 2 (IAE), size mismatch (IAE). The tests cover every rejection plus the defensive copy. Gaps, none blocking: (a) the scenario says "an exception naming the broken rule", but no test checks the message, and the NPE for a null element comes from `List.copyOf` with no message; (b) "immutable" is true by construction (`List.copyOf`) but not asserted, for example by `set.files().add(...)` throwing.
- **AC-7**: `catch (IOException e) { continue; }` drops the member. Tested with 3 members (2 remain, one set) and 2 members (no set).
- **AC-8**: `FileChannel` plus a 64 KB `ByteBuffer`, a fresh `MessageDigest` per call, and `HexFormat.of()` (lowercase). It streams and never reads the whole file into memory, as the design note requires. The known-vector test uses the standard SHA-256("abc") value.
- **AC-9**: `fullHashIsStubbedForDfs003` has been removed, and the class Javadoc was rewritten. `git grep` finds no reference to the stand-in test. The gate's green result shows the stand-in test raised no regression.
- **AC-10**: The stage never reads a file's current size, and `DuplicateSet.size` comes from the group. The test asserts `300 * 1024` with a fake returning equal "after-append" digests. This is adequate: with a fake, "hashing as it is now" cannot be shown more directly, and the adapter never checks size either (its Javadoc says so).

## Test quality

- The tests assert on outputs (sets, files, digests, order of hasher calls), not on internals. The fake records calls and throws if `sampleHash` is reached, so the stage cannot secretly use the wrong port method.
- AC-4 and AC-5 are built so that a plausible wrong implementation fails.
- The adapter tests use real bytes and cover both the matching and the non-matching case (the lesson from DFS-002 about constant-returning implementations).
- Cosmetic: the class Javadocs of `FullHashConfirmerTest` and `DuplicateSetTest` still say the code "is currently stubbed" and that every test "fails red today". That was true only in the tests phase and is now stale. The try/fail/catch style in `DuplicateSetTest` was chosen for the red gate. It is a little noisier than `assertThrows` but correct.

## Design conformance

- The Public API matches design.md: `DuplicateSet(long, String, List<FileEntry>)` in `domain.model`, `FullHashConfirmer(ContentHasher)` with `confirm(List<CandidateGroup>)` in `application`, and `NioContentHasher.fullHash` with its signature unchanged. `module-info.java` and `ContentHasher` are unchanged (neither file is in the diff).
- The checklist dispositions hold. 2: the constructor invariants are in place. 3: order as specified, with no sorting in the record. 4: `List.copyOf` in both the record and `confirm`. 8: singletons dropped. 9: path order, no threshold. 10: IOException drops the member, and size is deliberately not checked. 11: no new port, and a fake is used in stage tests. 12: generated in code, nothing added to `fixtures/`. 13: stateless apart from a final `hasher` field; `NioContentHasher` uses a fresh digest per call. 14: currencies as named. 1, 5, 6 and 7 are marked not applicable, correctly.
- The noted deviation (`confirm` rather than `filter`) is recorded in design.md.
- Scope: everything changed is under `file-scanner/src/{main,test}/java/us/pochaev/dfs/scanner/{application,domain/model,infra/adapter}` or the story folder. No files are outside `touches`.

## Safety invariants (CLAUDE.md)

- Never delete the last copy, re-verify at execution time, preview is a pure query: none apply, because this stage deletes nothing. `DuplicateSet` keeps `FileEntry` (size, mtime) as the baseline for later re-verification, which matches the ticket.
- Hard links are not duplicates: not handled here. The ticket defers this to DFS-004, and the `DuplicateSet` Javadoc records that two paths to one physical file can appear in one set until then. Nothing may delete based on this stage's output alone. This is acceptable for this ticket and noted for DFS-004/DFS-005.
- Symlinks, junctions and cloud placeholders: not applicable. The stage does not walk, and `FileChannel.open` on a path given by the walker only follows a link if the walker yielded one, which it does not.
- Empty files excluded: not applicable here (DFS-001's job). The stage would handle a 0-byte group correctly anyway.

## Friction / follow-ups

- The stale "currently stubbed / fails red today" Javadoc in two test classes is a pattern the pipeline will repeat. The test-guide could tell test-authors to describe red-phase behavior in `progress.md` rather than in test Javadoc.
- Optional hardening of the AC-6 tests (message content, unmodifiability), not needed for this ticket.
- The architect's two learnings in `learnings.md` (the currency-changing stage name, and the re-stub/retire steps for a deferred port method) are worth carrying into the `pipeline-stage` archetype.
