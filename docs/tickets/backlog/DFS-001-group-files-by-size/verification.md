# DFS-001: Group scanned files by size — verification

## Summary

- **DFS-001.AC-1** met: grouping by size across folders and singleton dropping are implemented and tested with value equality.
- **DFS-001.AC-2** met: default minimum of 1 and a configurable minimum are enforced in `add`; both have tests, including the 99/100 boundary.
- **DFS-001.AC-3** met in code, but one of its three clauses is not really tested. Merge-equals-single and the minimum-mismatch rejection are tested well. The "leaves the other index unchanged" test is vacuous: it passes for any implementation (see Test quality).
- **DFS-001.AC-4** met: equal paths collapse to the later mtime whatever the insertion order; three tests cover it.
- **DFS-001.AC-5** met: largest group first, paths ascending, order-independent; tested.
- **DFS-001.AC-6** met in code (`List.copyOf` at both levels). The test checks snapshot semantics only, not that mutation attempts are rejected.
- **DFS-001.AC-7** met: `CandidateGroup` invariants, a negative minimum and a null entry are rejected and tested. `merge(null)` is implemented but untested, which matches the ticket's wording.
- Build: `./gradlew build` green (re-run by the verifier). 22 application tests pass (17 `SizeIndexTest`, 5 `CandidateGroupTest`).
- Scope: only `application` main and test sources plus the story folder changed.
- Invariants: no violations. The equal-path collapse protects "never delete the last copy" against nested start locations.
- DoD: the heap figure is recorded, but the verifier could not reproduce it because the measuring class was not committed. That item is left unticked for the human to accept.
- **Ready for the human gate.** Nothing blocks. The human should decide on AC-3's vacuous "unchanged" test: accept it, or tighten the scenario and send the story back to the test phase.

## Per-criterion verdicts

### AC-1: unique sizes dropped, equal sizes grouped across folders: met
- `SizeIndex.groups()` groups by `FileEntry.size()` (the map key) whatever the directory, and drops groups with fewer than two files after collapsing.
- Tests: `filesWithUniqueSizeAreDropped`, `filesOfEqualSizeFormOneGroup` (compares the whole `CandidateGroup` by value, so it checks size, members and order), and `filesAreGroupedAcrossFolders`.

### AC-2: empty files excluded by default, configurable minimum: met
- `DEFAULT_MIN_SIZE = 1`. `add` returns early when `size < minSize`, so entries below the minimum are never stored (memory benefit as designed).
- Tests: `emptyFilesAreExcludedByDefault` and `minimumSizeExcludesSmallerFiles` (the 99/100 boundary, with the inclusive comparison checked).

### AC-3: merge equals single index, other unchanged, mismatched minimum rejected: met in code, one clause weakly tested
- `merge` checks for null, rejects a different minimum before touching any state, treats `merge(this)` as a no-op (the design's open note), and `addAll`s `other`'s entries into this index's own lists. No list is ever shared and `other` is never written to. Correct by inspection.
- `mergingPartialIndexesEqualsOneIndex` is meaningful: each index alone gives no groups, and after the merge the result equals the single index's.
- `mergingIndexesWithDifferentMinimumSizesIsRejected` is meaningful.
- `mergingAnEqualIndexChangesNothing` is meaningful. It depends on AC-4's collapse, which is intended.
- **`mergingLeavesOtherIndexUnchanged` is vacuous.** `index2` holds one file, so `index2.groups()` is empty before and after, whatever `merge` does. A merge that clears `other`, or one that shares `other`'s list via `putIfAbsent(size, otherList)`, would still pass. The sharing bug would not show here anyway, because `index1` already holds size 10, so `computeIfAbsent` finds its own list. The test follows the Gherkin literally (the test author flagged this in `learnings.md`). The weakness is in the scenario. A stronger version would give `index2` two files of one size that `index1` does not yet hold, and then add a third file of that size to `index1` after the merge.

### AC-4: repeated path counts once, later mtime kept: met
- `groups()` copies each size bucket, sorts it by path (a stable sort), and collapses adjacent equal paths, keeping the entry with the strictly later `lastModified`. Entries with equal mtime and equal path and size are equal records, so which one survives makes no difference. The result does not depend on insertion order.
- Tests: `samePathAddedTwiceCountsOnce`, `repeatedPathDoesNotInflateGroup`, and `equalPathsKeepLaterModificationTime`. The last one compares both insertion orders for equality, which also fails if the collapse did not happen, because the stable sort would leave the two orders different.

### AC-5: deterministic order: met
- Groups are sorted by `size` descending (sizes are distinct, so this is a total order). Files are sorted by `Path` ascending.
- Tests: `outputDoesNotDependOnInsertionOrder` (compares reversed insertion and checks that paths are sorted) and `groupsAreReturnedLargestFirst` (inserted 10, 50, 30; expects 50, 30, 10).

### AC-6: returned groups immutable: met in code, test covers snapshot only
- The outer list is `List.copyOf`, each group's `files` is `List.copyOf` in the `CandidateGroup` constructor, and `groups()` builds fresh lists on every call, so results never alias internal state.
- `returnedGroupsAreImmutable` matches the scenario (later `add`s do not change the earlier result). Its first assertion, `assertEquals(before, List.of(beforeGroup))`, is trivially true. The size and path assertions carry the check. No test tries `groups().add(...)` or `group.files().add(...)` and expects `UnsupportedOperationException`. Non-blocking, since the scenario only asks for snapshot semantics.

### AC-7: invalid input rejected: met
- `CandidateGroup` rejects a null list (NPE), a null element (NPE via `List.copyOf`), fewer than two files (IAE), and a size mismatch (IAE). `SizeIndex(long)` rejects a negative minimum (IAE), and `add(null)` throws NPE.
- Tests: five in `CandidateGroupTest` and two in `SizeIndexTest`. They use try/fail/catch, as the test guide requires, so a wrong exception type fails the test.
- `merge(null)` throws NPE per the design, but no test covers it. The ticket's scenario names only "a null entry", so this is acceptable. Noted as an optional addition.

## Test quality

- The tests assert on observable output (the `groups()` contents, compared by record value) and never on internals. No fakes are needed: this is pure in-memory logic, as the design says.
- Weak or vacuous: AC-3's "leaves the other index unchanged" (vacuous, see above) and AC-6's first assertion (trivially true).
- `progress.md` says `SizeIndexTest` has 20 tests. It has 17, for 22 in total with `CandidateGroupTest`, which is what `test-plan.md` lists. This is a bookkeeping slip only.

## Design conformance

- The public API matches `design.md` exactly: `DEFAULT_MIN_SIZE`, both constructors, `add`, `merge` and `groups`, the `CandidateGroup` record, and the documented exception types.
- Checklist disposition:
  - 1: the Javadoc says the class is not thread-safe and that `merge` combines partial indexes. `merge` copies. Singletons are dropped only in `groups()`.
  - 2, 3, 4: implemented as committed.
  - 5: implemented. A shrunk group is dropped by the `>= 2` check after collapsing.
  - 6: implemented. 0 is allowed.
  - 7: a figure is recorded (see the DoD note below).
  - 8: implemented. Singletons are dropped once, after collapsing, which covers both the "before" and "after" cases in the design.
  - 9 to 12: correctly not applicable.
  - 13: the only static is the constant.
  - 14: yes.
- `module-info.java` is unchanged and nothing new is exported, as designed.

## Safety invariants

- **Never delete the last copy:** the relevant part for this stage is the equal-path collapse, which prevents a file being reported as its own duplicate when start locations are nested. It is implemented and tested. Collapsing relies on `Path.equals`, so non-normalized or mixed relative/absolute paths for the same file would not collapse. Per the ticket, DFS-005 must normalize the start locations. Carry this forward.
- **Re-verify at execution time:** `CandidateGroup` carries the whole `FileEntry` (size and mtime baseline), as decided.
- **Preview is a pure query / no disk access:** the stage does no I/O.
- **Hard links:** out of scope here (later stage). Nothing in this diff treats them as duplicates beyond size candidacy, which is correct for stage 1.
- **Symlinks and junctions:** not applicable (no walking).
- **Empty files excluded:** yes, by default.

## Definition-of-done notes

- Ticked by the verifier:
  - every scenario is an automated test and passes;
  - the build is green (re-run);
  - no files changed outside Touches (the diff covers only `application` main and test sources and the story folder);
  - the empty-file default and minimum-size tests exist;
  - the `SizeIndex` Javadoc states that it is not thread-safe and describes `merge`.
- Not ticked: the heap measurement. The figure (~213 bytes/entry for 1M entries, `groups()` ~188 ms) is recorded in the ticket, but the measuring class was deliberately not committed, so the verifier could not reproduce it. The figure is plausible. The ticket calls it "consistent with the 250-400 byte/entry estimate", but it is actually below that range. The human should accept or reject it at the gate.
