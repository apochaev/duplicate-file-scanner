# DFS-004: Detect hard links within candidate groups — verification

## Summary

Verify phase, attempt 1, on implement commit c59dc35 (diff reviewed: 3749457..c59dc35).

- **DFS-004.AC-1** met: files join the first copy whose first link `isSameFile` confirms; test asserts `[a,b]` grouped as one `PhysicalCopy`.
- **DFS-004.AC-2** met: a set left with fewer than two copies returns null and is omitted; test asserts empty output for two links of one file.
- **DFS-004.AC-3** met: an unmatched file starts its own single-link copy; two tests assert whole `PhysicalCopy` lists.
- **DFS-004.AC-4** met: calls are `(candidate, firstLink)`, per set, ascending; test asserts the exact ordered call list, which also excludes cross-set calls.
- **DFS-004.AC-5** met: input order kept, dropped set removed, files sorted before clustering; test compares whole records for S1 (out-of-order input) and S3.
- **DFS-004.AC-6, AC-7** retired (DFS-017); nothing to verify here.
- **DFS-004.AC-8** met: `IOException` drops the candidate without further checks, set dropped under two copies; both scenarios tested with a non-first unreadable path.
- **DFS-004.AC-9** met: `NioFileIdentity` delegates to `Files.isSameFile`; both real-file tests ran on this Windows machine (0 skipped), the link test aborts via `Assumptions` if `createLink` fails.
- Invariant "hard links are not duplicates": implemented; `Files.isSameFile` only inside one set. No other safety invariant applies to this stage.
- Design conformance: matches design.md's API, algorithm and every checklist disposition. No `domain.model`, `module-info`, `FullHashConfirmer` or earlier tests changed.
- Minor notes (not blocking): no null-argument tests (documented gap, no AC tags one); an unreadable *first link* drops later candidates instead (accepted design note, untested).
- All six Definition-of-done items verified and ticked.

**Verdict: ready for the human gate.** No phase needs to be repeated.

## Per-criterion detail

| Criterion | Verdict | Implementation evidence | Test evidence |
|---|---|---|---|
| AC-1 | met | `HardLinkDetector.regroup`: inner loop over `copies`, `matchedCopy.add(file)` on the first `isSameFile` true | `hardLinkedPairCountsAsOneCopy`: `copies() == [PhysicalCopy[a,b], PhysicalCopy[c]]`, `files() == [a,b,c]` |
| AC-2 | met | `if (copies.size() < 2) return null`; `detect` skips null | `twoHardLinksToOneFileAreNotDuplicates`: `assertEquals(List.of(), result)` |
| AC-3 | met | no match -> `copies.add(newCopy)` with one link | `independentIdenticalFilesRemainSeparateCopies` (three single-link copies) and the AC-1 test's `[c]` copy |
| AC-4 | met | first file of a set enters with an empty `copies` list, so it is never checked; each later file is checked against `copy.get(0)` in copy order; the loop is per set | `identityIsOnlyCheckedInsideASetAgainstEachCopysFirstLink`: exact list `[(x/b,x/a), (x/c,x/a), (y/e,y/d)]`. Exact equality also proves no cross-set call and that `x/c` is not checked against `x/b` |
| AC-5 | met | `files.sort(comparing(path))` before clustering; output built in input order; `List.copyOf` | `outputOrderFollowsTheInputTheFirstPathAndThePathOrder`: S1 given as `[xc,xa,xd,xb]`, expected `[xa,xc],[xb],[xd]`; S2 (all links) removed; whole-record `assertEquals` |
| AC-8 | met | `catch (IOException) { dropped = true; break; }` then `continue`; surviving files re-sorted; set dropped under two copies | `fileWhoseIdentityCannotBeCheckedIsDroppedNotItsSet` (`files() == [a,c]`, two copies) and `droppingAnUnreadableFileLeavesFewerThanTwoCopies` (empty) |
| AC-9 | met | `NioFileIdentity.isSameFile` = `requireNonNull` x2 + `Files.isSameFile(a, b)` | `NioFileIdentityTest`: hard link via `Fixtures.createHardLinkOrSkip` (`Assumptions.abort` on `UnsupportedOperationException`/`FileSystemException`) -> true; two independent identical 10 KB files -> false. Test report: 2 tests, 0 skipped |

## Test quality

- The stage tests assert on output values (whole `DuplicateSet`/`PhysicalCopy` records or their `files()`/`copies()`) and, for AC-4, on the recorded call sequence. They do not mirror the implementation's internals. The fake is small and plainly correct: symmetric pairs, equal paths count as the same file, unreadable paths throw from any call that names them.
- AC-5's input really is out of path order and includes a link pair that is not adjacent (`x/a` with `x/c`, `x/b` between them). That exercises "copies ordered by first path" in a way a trivial input would not.
- AC-8's tests make the unreadable file a non-first path on purpose, following design.md. The opposite case, an unreadable first link, is a documented design note and has no test. That is acceptable because no AC or scenario covers it.
- AC-9 does exercise the real Windows behavior (`fileKey()` is null there, and `Files.isSameFile` still recognises the link). The JUnit XML report shows `skipped="0"` for `NioFileIdentityTest`.
- Gap, already flagged in `learnings.md`: design.md documents `NullPointerException` on a null `identity`, a null `sets` list and a null element, but no test covers it. The implementation does call `requireNonNull` in all three places. The gap is not blocking because no live AC tags null rejection.

## Design conformance

- Public API matches design.md: `FileIdentity.isSameFile(Path, Path) throws IOException` in `infra.port`; `NioFileIdentity` is no-arg and stateless in `infra.adapter`; `HardLinkDetector(FileIdentity)` and `detect(List<DuplicateSet>)` in `application`. The incoming `copies()` are ignored and re-derived, and `size`/`digest` are kept.
- Checklist dispositions: item 3 (sort before clustering), item 4 (`List.copyOf` output; `PhysicalCopy(List.copyOf(copy))`), item 8 (drop under two copies), item 9 (ascending order), item 10 (candidate dropped, rest continues), item 11 (one pairwise port, faked in stage tests, real files only for the adapter) and item 13 (only a final port field) are all reflected in the code. Items 1, 5, 6, 7 and 12 are not applicable, as design.md states, and nothing in the diff contradicts that.
- The stage is not wired into a factory. That matches design.md ("scan orchestration (DFS-005)").
- Layering: `application` depends only on `domain.model` and `infra.port`. The adapter depends only on `infra.port`. ArchUnit and `ModuleExportsTest` pass. Nothing new is exported.

## Safety invariants (CLAUDE.md)

- **Hard links are not duplicates**: implemented by this ticket. `Files.isSameFile` is used, not `fileKey()`, and only within one set's members, never across the disk.
- **Never delete the last copy / re-verify at execution time / preview is a pure query**: not applicable, since the stage deletes nothing. The stage's error handling only ever under-reports copies (a dropped file, never an extra copy), so it cannot make a later plan unsafe.
- **No symlink/junction following, empty files excluded**: not applicable. The stage walks no tree and reads no content. `Files.isSameFile` follows links when it resolves a path, but its inputs are regular files that the walker already yielded.

## Scope and freeze

- `git diff --stat 3749457..c59dc35`: code changes are limited to `application/HardLinkDetector.java`, `infra/port/FileIdentity.java`, `infra/adapter/NioFileIdentity.java` and their two test classes, plus the story folder. The ticket diff is only the `status`/`updated` frontmatter.
- `git diff --stat fdeb2c0..c59dc35`: the implement phase changed no test file.
- `./gradlew build` re-run in this phase: BUILD SUCCESSFUL, all tasks up to date against c59dc35's tree.

## Definition of done (ticked in the ticket)

1. Every scenario is an automated test and passes: 9 scenarios map to 9 tests (7 + 2), and all pass with 0 skipped.
2. `./gradlew build` green: confirmed.
3. No files changed outside Touches: confirmed (see Scope).
4. The adapter test creates the hard link at test time and skips via `Assumptions`: confirmed (`Fixtures.createHardLinkOrSkip`).
5. `domain.model`, `FullHashConfirmer` and DFS-001 to DFS-003's tests are unchanged: confirmed, none of them appears in the diff.
6. Every checklist item is addressed in design.md: all 14 items are dispositioned there.
