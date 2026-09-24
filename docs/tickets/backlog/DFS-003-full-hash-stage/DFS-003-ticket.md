---
id: DFS-003
title: Confirm duplicates with full SHA-256 hash
status: in-progress
type: feature
scope: [file-scanner]
archetype: pipeline-stage
priority: high
depends_on: [DFS-002]
touches: [us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-23
completed:
---

# DFS-003: Confirm duplicates with full SHA-256 hash

## Story

As a user reviewing duplicates
I want files that still match after the sample stage confirmed by a full SHA-256 hash
So that every reported duplicate set has identical content, not merely similar samples

## Context

Stage 3 of the detection pipeline (CLAUDE.md design decision 3): full SHA-256 for files still matching, dropping groups of one afterwards, hashing in path order within a group. The output of this stage is the `DuplicateSet` view from design decision 1 (identical content, N paths). Files under the sample threshold skip the partial stage and arrive here directly. The hash is a filter, not the delete-time proof: design decision 4 requires a byte-for-byte compare against the keeper before quarantining, which is a separate ticket.

**What `DuplicateSet` is ultimately for.** Its purpose is to be presented to the user so they can make an informed decision about *which directory* to remove duplicate files from. For every set of identical files that means: where each copy lives (its path, hence its folder), how much space removing the extra copies frees (size × (copies − 1)), and which copy is older or newer (modification time). The directory-level view itself — "folder2 has 340 of its 400 files duplicated in folder1" — is `LocationOverlap(A, B)` (design decision 1), a separate projection built *from* a list of `DuplicateSet`s by a later ticket; it is not this stage's job, and `DuplicateSet` must not be shaped around one presentation.

This is a `pipeline-stage` archetype ticket (`.claude/archetypes/pipeline-stage/archetype.md`). Input is DFS-002's output, `List<CandidateGroup>` (`application`, internal): groups at or under the 128 KB skip threshold already pass through `PartialHashFilter` unchanged, so "small files go straight to the full hash" needs no separate entry path. Output is `List<DuplicateSet>` (checklist item 14). Checklist items resolved without a separate decision: nothing to merge, the stage consumes a finished list (item 1); `DuplicateSet`'s constructor carries the invariants (item 2, see below); output order is the input's, each group replaced in place by the sets it splits into, ordered by their smallest path, members ascending (item 3, as DFS-002); immutable results (item 4); nothing to collapse, DFS-001 already collapsed repeated paths (item 5); no tunable, a full hash reads the whole file (item 6); no measurement, one group held at a time (item 7); groups of one dropped (item 8); path order (item 9); the near-duplicate pair is already a scenario (item 12); stateless, only a port reference (item 13).

**Decided (2026-09-23): `DuplicateSet(long size, String digest, List<FileEntry> files)` in `domain.model`, introduced by this ticket.** `size` in bytes; `digest` the lowercase hexadecimal SHA-256 of the full content, the same `String` type `ContentHasher` already returns; `files` at least two `FileEntry` values in ascending path order, stored as an immutable defensive copy. The constructor rejects a null or blank digest, null files or a null entry, fewer than two files, and an entry whose size differs from `size` — the same invariants as `CandidateGroup`. Keeping `FileEntry` (path, size, modification time) rather than bare paths gives the user-facing view everything it shows, and is the "unchanged since the scan" baseline for re-verification at execution time. Keeping the digest gives a set an identity across scans and for a future hash cache. A typed `Sha256Digest` was rejected: the user never sees the difference. No convenience methods (reclaimable bytes and the like) for now; the view computes them from `size` and the file count. `domain.model` is already exported, so `module-info.java` does not change and `ModuleExportsTest` stays green on its own.

**Decided (2026-09-23): full SHA-256 for every group; the hash-versus-compare question belongs to DFS-007.** DFS-007 ("benchmark hash vs compare", depends on DFS-003) already owns the question of comparing pairs byte-for-byte instead of hashing, and whether `DuplicateSet` then keeps a digest. DFS-003 takes the straightforward path CLAUDE.md decision 3 describes; DFS-007 revisits it on evidence.

**Decided (2026-09-23): DFS-003's test-author deletes DFS-002's `fullHash` stand-in test in the tests phase.** `NioContentHasherTest.fullHashIsStubbedForDfs003` (display name `DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003`) guards a transitional state this ticket ends by design. Left in place it blocks the pipeline twice (found reading the real test and `tools/story-gate/RedCheck.java` at refinement, per CCW-005.L-5): once the architect re-stubs `fullHash` as `not implemented: DFS-003`, the test re-throws that exception and `RedCheck` reports it as a `regression`, since it is not a DFS-003 story test; once `fullHash` works, it hits its own `fail("expected fullHash to throw ...")` and `green` fails. So the test-author removes that one method and writes DFS-003's AC-8 adapter tests in the same file. The architect's `design.md` must name `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/NioContentHasherTest.java` among the files the test-author reads, since the test-author reads only files the design names. The file is in `us.pochaev.dfs.scanner.infra.adapter`, already in `touches`; `freeze` (scoped by CCW-009) judges only DFS-003's own tests-phase artifacts. DFS-002's closed ticket keeps its AC-8 as written (an accurate record of a transitional state); retiring it there would edit another ticket and is left out of scope.

**Decided (2026-09-23): a file whose size changed since the scan is hashed as it is now; the stage does not check size.** (Checklist item 10, size half; DFS-002 did not check it either, and DFS-002.L-5 stays parked.) A file that changed almost certainly gets a different digest, splits off, and its former twin's group of one is dropped, so no false duplicate results. The one residual case, every copy changed identically, yields files that genuinely are identical now; the set then carries the scan-time size from the `FileEntry` values. The designed safety net is CLAUDE.md's execution-time re-verification (keeper and candidate still exist, unchanged in size and modification time against the `FileEntry` baseline, byte-identical per decision 4). Rejected: passing an expected size into `fullHash` and dropping on mismatch — it changes the port DFS-002 settled for a case only re-verification can close fully anyway, since a same-size edit would pass it. The design doc states this disposition explicitly rather than skipping the item.

**Settled by DFS-002, adopted here (2026-09-23):** SHA-256 via the JDK `MessageDigest` (DFS-002 decided one algorithm for both the sample and the full hash; `NioContentHasher` already uses it), and a file that becomes unreadable before this stage hashes it is dropped from its group, not the whole group failed, with no report until DFS-005's listener exists (DFS-002's own decision; this ticket's open questions asked to "decide once").

Safety invariants: none of CLAUDE.md's apply directly to this stage, as in DFS-002. It neither walks the tree (symlinks, junctions, cloud placeholders and empty-file exclusion belong to the walker and DFS-001) nor deletes anything (never delete the last copy, re-verify at execution time and preview-is-a-pure-query are downstream). Hard links are DFS-004's job: until it lands, two paths to one physical file can appear in one `DuplicateSet`, which is why nothing may delete on this stage's output alone.

## Acceptance criteria

The full ID of each criterion is `DFS-003.AC-<n>`.

- **AC-1** Files in a candidate group whose full content is identical form one `DuplicateSet` containing every one of them, carrying the group's size and their shared digest.
- **AC-2** Files that differ anywhere in their content, including only in the middle byte past the partial-hash sample, never end up in the same `DuplicateSet`.
- **AC-3** A group left with fewer than two files sharing a digest produces no `DuplicateSet`.
- **AC-4** Files within a group are fully hashed in ascending path order.
- **AC-5** Output order is deterministic: each input group is replaced, in its own position, by the sets it splits into, ordered by their smallest path; the files in each set are in ascending path order.
- **AC-6** `DuplicateSet`'s constructor rejects a null or blank digest, null files or a null entry, fewer than two files, and an entry whose size differs from the set's size; it stores an immutable defensive copy of the files.
- **AC-7** A file that becomes unreadable before this stage hashes it is dropped from its group rather than failing the whole group.
- **AC-8** `NioContentHasher.fullHash` returns the lowercase hexadecimal SHA-256 of the file's whole content: a known input gives its known digest, equal content gives equal digests, and a one-byte difference anywhere gives different digests.
- **AC-9** No test asserts that `fullHash` throws: DFS-002's stand-in test `DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003` is removed in DFS-003's tests phase, so the red gate reports no regression from it and the green gate does not fail on it.
- **AC-10** The stage hashes each file as it is when hashed and does not compare its size with the scan's: files whose content all changed identically still form one `DuplicateSet`, which carries the scan-time size from their `FileEntry` values.

## Scenarios

```gherkin
Feature: Full-hash stage

  @AC-1
  Scenario: Identical files form a duplicate set
    Given two identical files of 300 KB in "folder1" and "folder2"
    When the full-hash stage runs
    Then one duplicate set is produced containing both paths, with size 307200 and their shared digest

  @AC-2 @AC-3
  Scenario: Files differing only in the middle are separated
    Given two files of 300 KB that passed the partial stage and differ only in the middle byte
    When the full-hash stage runs
    Then no duplicate set is produced

  @AC-1 @AC-3
  Scenario: Only the identical files form a set
    Given three files of 300 KB, two identical and one differing only in the middle byte
    When the full-hash stage runs
    Then one duplicate set is produced containing the two identical files

  @AC-1
  Scenario: Small files go straight to the full hash
    Given two identical files of 1 KB, passed through the partial stage unsampled
    When the full-hash stage runs
    Then one duplicate set is produced containing both paths

  @AC-2
  Scenario: A group splits into two sets
    Given four files of 300 KB in one candidate group: "a" and "c" identical, "b" and "d" identical, the two pairs differing only in the middle byte
    When the full-hash stage runs
    Then two duplicate sets are produced, one containing "a" and "c", the other containing "b" and "d"

  @AC-4
  Scenario: Files are hashed in path order
    Given a candidate group whose paths arrive out of order
    When the full-hash stage runs
    Then the hasher is asked for full hashes in ascending path order

  @AC-5
  Scenario: Output order follows the input and the smallest path
    Given two candidate groups, the first splitting into a set of "x/b" and "x/d" and a set of "x/a" and "x/c", the second forming one set
    When the full-hash stage runs
    Then the sets are, in order: "x/a" and "x/c", then "x/b" and "x/d", then the second group's set
    And each set lists its files in ascending path order

  @AC-6
  Scenario: A duplicate set rejects inputs that break its definition
    Given a duplicate set is constructed with a blank digest, or with one file, or with a file whose size differs from the set's size, or with a null entry
    Then construction fails with an exception naming the broken rule
    And a set constructed from a mutable list is unaffected when that list is changed afterwards

  @AC-7
  Scenario: An unreadable file is dropped, not its whole group
    Given three identical files of 300 KB, one of which becomes unreadable before the stage hashes it
    When the full-hash stage runs
    Then one duplicate set is produced containing the two readable files

  @AC-7
  Scenario: An unreadable file leaves its group with fewer than two members
    Given two identical files of 300 KB, one of which becomes unreadable before the stage hashes it
    When the full-hash stage runs
    Then no duplicate set is produced

  @AC-8
  Scenario: The real adapter computes the SHA-256 of the whole file
    Given a file containing exactly the three bytes "abc"
    When the NIO adapter's full hash is computed
    Then the digest is "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

  @AC-8
  Scenario: The real adapter tells files apart by a single byte anywhere
    Given two identical files of 300 KB, and a third that differs from them only in the middle byte
    When the NIO adapter's full hash is computed for each
    Then the two identical files have equal digests
    And the third file's digest differs from theirs

  @AC-9
  Scenario: DFS-002's fullHash stand-in test no longer blocks the gates
    Given the test tree after DFS-003's tests phase, with fullHash stubbed as "not implemented: DFS-003"
    When the red gate runs
    Then no test named "DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003" exists
    And the red gate reports no regression
    And after the implement phase the green gate passes with fullHash returning a digest

  @AC-10
  Scenario: Copies that all changed identically since the scan still form a set with the scan-time size
    Given a candidate group of two files recorded by the scan at 300 KB
    And both have since had the same 1 KB appended, so the hasher returns equal digests for them
    When the full-hash stage runs
    Then one duplicate set is produced containing both files, with size 307200
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: the full-hash stage, and its tests
- `us.pochaev.dfs.scanner.domain.model`: `DuplicateSet`, and its tests
- `us.pochaev.dfs.scanner.infra.port`: `ContentHasher.fullHash`'s documentation, if the design refines it (its signature is unchanged)
- `us.pochaev.dfs.scanner.infra.adapter`: `NioContentHasher.fullHash`, SHA-256 via the JDK `MessageDigest`; `NioContentHasherTest` (removing DFS-002's stand-in test, adding DFS-003's AC-8 tests)

**Out of scope:**
- Byte-for-byte compare at delete time (`ContentComparator`) and execution-time re-verification
- Hashing versus comparing pairs, and whether `DuplicateSet` keeps its digest in the long run (DFS-007)
- The directory-level view `LocationOverlap(A, B)` and its roll-up to ancestor folders (design decision 1; no ticket yet)
- Hard-link detection (DFS-004) and scan orchestration, including reporting dropped files (DFS-005)
- Checking a file's size against the scan's before hashing it (decided against, see Context)
- Retiring DFS-002's AC-8 in DFS-002's own closed ticket
- A persistent hash cache (deferred, would sit behind a port)

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `DuplicateSet` is in an exported domain package (`domain.model`) and `ModuleExportsTest` still passes, with `module-info.java` unchanged
- [x] The near-duplicate large-file cases (300 KB, differing only in the middle byte) are generated in code, not committed
- [x] `design.md` names `NioContentHasherTest.java` among the files the test-author reads, and DFS-002's `fullHash` stand-in test is gone after the tests phase (AC-9)
- [x] Every item of the `pipeline-stage` archetype's edge-case checklist is addressed in the design doc or marked not applicable with a one-line reason, including item 10's size half (not checked, per Context)

## Open questions

None.
