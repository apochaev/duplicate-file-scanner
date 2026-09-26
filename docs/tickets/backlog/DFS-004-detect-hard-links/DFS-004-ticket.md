---
id: DFS-004
title: Detect hard links within candidate groups
status: in-review
type: feature
scope: [file-scanner]
archetype: pipeline-stage
priority: high
depends_on: [DFS-003, DFS-017]
touches: [us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-26
completed:
---

# DFS-004: Detect hard links within candidate groups

## Story

As a user deleting duplicates
I want paths that are the same physical file recognised as one copy
So that a file is never reported as a duplicate of itself and deleting one hard link is never mistaken for removing a redundant copy

## Context

Safety invariant "Hard links are not duplicates" (CLAUDE.md). `BasicFileAttributes.fileKey()` returns null on Windows (verified), so it cannot identify them; use `Files.isSameFile` within a candidate group, never pairwise over the whole disk. `application` is tested against fakes, so the same-file check needs a secondary port. Hard links cannot be committed as fixtures: create them at test time with `Files.createLink` and skip the test (`Assumptions`) when the platform or privileges do not allow it (`Files.createLink` works on NTFS without administrator rights, so on Windows it is expected to run, not skip).

This is a `pipeline-stage` archetype ticket (`.claude/archetypes/pipeline-stage/archetype.md` names hard-link detection among "later stages of the same shape"). No tunable (checklist item 6); nothing to merge, it consumes a finished list (item 1).

**Decided (2026-09-25): the stage runs after the full hash, as a transform `List<DuplicateSet>` → `List<DuplicateSet>`.** Paths that turn out to be one physical file are recognised inside each confirmed set, so `Files.isSameFile` runs only among a set's members (usually two or three), never across the disk. `CandidateGroup`, `PartialHashFilter` and `FullHashConfirmer` are untouched and `depends_on` stays `[DFS-003]`. Accepted cost: a hard-linked file's content is hashed once per link by DFS-003 (the repeat read is usually served by the OS file cache, and hard links are rare in ordinary user folders). Rejected: collapsing aliases before hashing, on `CandidateGroup`, which saves those reads but would change three finished stages and their tests; DFS-007's benchmark can measure the repeat-read cost if it ever matters.

**Decided (2026-09-25): `DuplicateSet` groups links into physical copies.** Why it matters for the user's decision (which directory to delete from, DFS-003's Context): deleting one link of a hard-linked file frees no space, and must never be mistaken for removing a redundant copy. Shape:
- New `PhysicalCopy(List<FileEntry> links)` in `domain.model` (already exported, so `module-info.java` does not change): one physical file and every path that reaches it, at least one link, no null, links in ascending path order, stored as an immutable copy.
- `DuplicateSet(long size, String digest, List<FileEntry> files, List<PhysicalCopy> copies)` becomes the canonical record: `files` is every link in ascending path order, as in DFS-003; `copies` groups them into physical copies, ordered by their first path. It needs at least **two physical copies** (a set that turns out to be all links of one file is no duplicate and is dropped), every link's size equal to `size`, and `copies` must exactly partition `files` (every path in exactly one copy, no path outside `files`). Both lists are stored as immutable copies. Like DFS-003's version, the record validates but does not sort; the stage supplies the order.
- Kept for compatibility, verified against the real code: the existing 3-argument form `DuplicateSet(long size, String digest, List<FileEntry> files)` stays as a second constructor that makes each file its own single-link copy. `FullHashConfirmer` calls exactly this and stays unchanged; DFS-003's `DuplicateSetTest` (7 tests, including `new DuplicateSet(10, "digest", null)` expecting `NullPointerException`) and `FullHashConfirmerTest` use only the 3-argument form and `files()`, and keep compiling and passing unchanged. DFS-003's tests check exception types, not messages, so the reworded "two physical copies" message does not break them.
- **Amended the same day, found at refinement by reading `DuplicateSetTest` (CCW-005.L-5):** the first version of this decision made `DuplicateSet(long, String, List<PhysicalCopy>)` canonical beside a `DuplicateSet(long, String, List<FileEntry>)` constructor. That does not compile: both erase to `DuplicateSet(long, String, List)` ("name clash: same erasure"), and DFS-003's `new DuplicateSet(10, "digest", null)` would be ambiguous even with distinct types. The fourth component keeps the two constructors apart by arity. Also rejected: switching `FullHashConfirmer` and DFS-003's tests to a static factory, which would edit a finished stage and another ticket's tests.
- **Delivered by DFS-017, not this ticket (decided 2026-09-25).** The model change cannot pass this ticket's red gate: stubbing the reshaped `DuplicateSet` (or `PhysicalCopy`, which the 3-argument constructor now builds) fails `FullHashConfirmer`'s and DFS-003's tests as `RedCheck` regressions, while implementing it for real lets its own tests pass against stubs. DFS-003 did not hit this because `DuplicateSet` was new then. So `PhysicalCopy` and the `DuplicateSet` reshape moved to DFS-017 (`archetype: none`, built by hand with its tests), and this ticket depends on it. Its criteria AC-6 and AC-7 are retired here and live on as DFS-017.AC-1 and DFS-017.AC-2. This ticket uses the model (constructs sets through the canonical constructor) but no longer changes `domain.model`.
- Rejected: dropping the extra links and keeping one representative (the user would never learn a linked path exists, and deleting it would free nothing); a separate alias map beside a flat `files` list (splits the "two physical copies" rule across two fields).

**Decided (2026-09-25): a `FileIdentity` port, `boolean isSameFile(Path a, Path b) throws IOException`, in `infra.port`; adapter `NioFileIdentity` in `infra.adapter` calls `Files.isSameFile`.** Clustering inside one set: take its files in ascending path order and put each into the first existing copy whose *first link* it is the same file as, or start a new copy. Calls per set are at most files × copies (quadratic only for a set of many distinct copies; real sets hold two or three files, and the check reads attributes, not content). Stage tests use a fake told which pairs are the same file, as DFS-002/DFS-003's hash fakes are; only the adapter is tested on real files, with hard links created at test time. Rejected: a key-based `identityOf(Path)` port (linear, but `fileKey()` is null on Windows, so a plain-NIO adapter cannot produce the key); calling `Files.isSameFile` from `application` directly (breaks CLAUDE.md's layering and the fake-based tests).

**Decided (2026-09-25): a file whose same-file check throws `IOException` is dropped from its set** (checklist item 10), as DFS-002 and DFS-003 drop an unreadable file; if that leaves fewer than two physical copies, the set is dropped. No report until DFS-005's listener exists. Rejected: counting it as its own copy (if it is really a link of another copy, the set overstates copies, which breaks this ticket's own invariant); failing the whole set (one bad path would hide real duplicates, as DFS-002 already rejected).

Remaining checklist items, resolved without a separate decision: input and output currency are both `List<DuplicateSet>` (item 14); `PhysicalCopy`'s and `DuplicateSet`'s constructors carry the invariants (item 2, built by DFS-017); output keeps the input's set order, a dropped set simply removed, copies ordered by their first path, links ascending (item 3); immutable results (item 4); nothing to collapse, DFS-001 already collapsed repeated paths (item 5); no measurement, one set held at a time (item 7); a set left with one physical copy is dropped (item 8); files are checked in ascending path order (item 9); no content is read, so no large or near-duplicate fixtures (item 12); stateless apart from the port reference (item 13).

Safety invariants: this ticket implements "hard links are not duplicates" (CLAUDE.md). The others do not apply to this stage: it walks no tree (symlinks, junctions, cloud placeholders and empty-file exclusion belong to the walker and DFS-001) and deletes nothing (never delete the last copy, execution-time re-verification and preview-is-a-pure-query are downstream). Which link of a copy a later deletion keeps is deletion planning's decision, not this stage's.

## Acceptance criteria

The full ID of each criterion is `DFS-004.AC-<n>`.

- **AC-1** Paths in a `DuplicateSet` that are the same physical file are grouped into one `PhysicalCopy` holding all of them.
- **AC-2** A set whose files are all links of one physical file produces no `DuplicateSet`: a file is never reported as a duplicate of itself.
- **AC-3** Independent files with identical content stay separate physical copies, one `PhysicalCopy` each.
- **AC-4** The same-file check is invoked only between files of the same set, in ascending path order, and each file only against the first link of each copy formed so far.
- **AC-5** Output order is deterministic: sets keep their input order (a dropped set is simply removed), copies are ordered by their first path, and each copy's links are in ascending path order.
- **AC-6 (retired)** `PhysicalCopy`'s and `DuplicateSet`'s constructors enforce the physical-copy invariants. Retired 2026-09-25: moved to DFS-017 (as DFS-017.AC-1), since the model change cannot pass this ticket's red gate.
- **AC-7 (retired)** The 3-argument `DuplicateSet` constructor keeps `FullHashConfirmer` and DFS-003's tests working unchanged. Retired 2026-09-25: moved to DFS-017 (as DFS-017.AC-2), for the same reason.
- **AC-8** A file whose same-file check throws `IOException` is dropped from its set; if fewer than two physical copies remain, the set is dropped.
- **AC-9** `NioFileIdentity.isSameFile` returns true for two hard links to one file and false for two independent files with identical content, on real files; the hard-link test skips via `Assumptions` when a link cannot be created.

## Scenarios

```gherkin
Feature: Hard-link detection

  @AC-2
  Scenario: Two hard links to one file are not duplicates
    Given a duplicate set of "folder1/a.bin" and "folder2/b.bin", which are hard links to the same file of 10 KB
    When the hard-link stage runs
    Then no duplicate set is produced

  @AC-1 @AC-3
  Scenario: A hard-linked pair counts as one copy
    Given a duplicate set of "a.bin", "b.bin" and "c.bin", where "a.bin" and "b.bin" are hard links to one file and "c.bin" is an independent file with identical content
    When the hard-link stage runs
    Then one duplicate set is produced with two physical copies: "a.bin" with "b.bin", and "c.bin"
    And files() lists "a.bin", "b.bin" and "c.bin"

  @AC-3
  Scenario: Independent identical files remain separate copies
    Given a duplicate set of three independent files with identical content
    When the hard-link stage runs
    Then one duplicate set is produced with three physical copies of one link each

  @AC-4
  Scenario: Identity is only checked inside a set, against each copy's first link
    Given two duplicate sets, the first of "x/a", "x/b" and "x/c" where only "x/a" and "x/b" are the same file, the second of "y/d" and "y/e"
    When the hard-link stage runs
    Then the same-file check is asked, in order: "x/b" against "x/a", "x/c" against "x/a", "y/e" against "y/d"
    And never between a file of the first set and a file of the second

  @AC-5
  Scenario: Output order follows the input, the first path and the path order
    Given three duplicate sets in the order S1, S2, S3, where S2 is all links of one file and S1's files arrive out of path order
    When the hard-link stage runs
    Then the output is S1 then S3
    And S1's copies are ordered by their first path, with each copy's links in ascending path order

  @AC-8
  Scenario: A file whose identity cannot be checked is dropped, not its set
    Given a duplicate set of three independent identical files, one of which becomes unreadable before its same-file check
    When the hard-link stage runs
    Then one duplicate set is produced with the two readable files as two physical copies

  @AC-8
  Scenario: Dropping an unreadable file leaves fewer than two copies
    Given a duplicate set of two files, one of which becomes unreadable before its same-file check
    When the hard-link stage runs
    Then no duplicate set is produced

  @AC-9
  Scenario: The real adapter recognises a hard link
    Given a file "a.bin" of 10 KB and a hard link "b.bin" to it, created at test time
    When the NIO adapter checks whether "a.bin" and "b.bin" are the same file
    Then it answers yes

  @AC-9
  Scenario: The real adapter tells independent identical files apart
    Given two independent files "a.bin" and "c.bin" with identical content of 10 KB
    When the NIO adapter checks whether they are the same file
    Then it answers no
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: the hard-link stage, and its tests
- `us.pochaev.dfs.scanner.infra.port`: `FileIdentity`
- `us.pochaev.dfs.scanner.infra.adapter`: `NioFileIdentity` using `Files.isSameFile`, and its tests

**Out of scope:**
- `PhysicalCopy` and the `DuplicateSet` reshape (DFS-017); this ticket only uses them
- Collapsing hard links before hashing (decided against, see Context)
- Symlink and junction handling (already done by the walker)
- Deletion planning, including which link of a copy is kept, and execution-time re-verification
- The folder view `LocationOverlap` (no ticket yet) and scan orchestration, including reporting dropped files (DFS-005)
- Any change to `FullHashConfirmer`, `CandidateGroup`, `PartialHashFilter` or DFS-001 to DFS-003's tests

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] The adapter test creates the hard link at test time and skips via `Assumptions` when it cannot
- [x] `domain.model`, `FullHashConfirmer` and DFS-001 to DFS-003's tests are unchanged
- [x] Every item of the `pipeline-stage` archetype's edge-case checklist is addressed in the design doc or marked not applicable with a one-line reason

## Open questions

None.
