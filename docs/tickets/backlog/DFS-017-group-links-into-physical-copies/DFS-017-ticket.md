---
id: DFS-017
title: Group duplicate-set links into physical copies
status: ready
type: feature
scope: [file-scanner]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: [DFS-003]
touches: [us.pochaev.dfs.scanner.domain.model]
blocked_reason:
created: 2026-09-25
updated: 2026-09-25
completed:
---

# DFS-017: Group duplicate-set links into physical copies

## Story

As a user deciding which directory to delete duplicates from
I want each duplicate set to say which of its paths are the same physical file
So that I can see that deleting a hard link frees no space, and one link is never mistaken for a redundant copy

## Context

Split off from DFS-004 (hard-link detection) during its refinement, 2026-09-25. DFS-004 decided that `DuplicateSet` (DFS-003, `domain.model`, exported) groups its paths into physical copies, and that DFS-003's existing callers and tests must keep working unchanged. That model change cannot go through the story pipeline: `RedCheck` needs every story test to fail against stubs while every other test passes. Stubbing the reshaped `DuplicateSet` (or `PhysicalCopy`, which its 3-argument constructor now builds) would fail `FullHashConfirmer`'s and DFS-003's tests as regressions; implementing it for real would let this ticket's own tests pass against stubs. DFS-003 did not hit this because `DuplicateSet` was new then. So this ticket is `archetype: none`, implemented by hand with its tests, and DFS-004 depends on it, keeping only the parts that are new and stubbable (the stage, `FileIdentity`, `NioFileIdentity`).

The shape, decided in DFS-004's refinement (see its Context for the full reasoning and the rejected alternatives):
- New `PhysicalCopy(List<FileEntry> links)` in `domain.model`: one physical file and every path that reaches it; at least one link, no null, stored as an immutable copy.
- `DuplicateSet(long size, String digest, List<FileEntry> files, List<PhysicalCopy> copies)` becomes the canonical record. `files` is every link in ascending path order, as in DFS-003; `copies` groups them, ordered by their first path. It needs at least two physical copies, every link's size equal to `size`, and `copies` must exactly partition `files`. Both lists are stored as immutable copies. The record validates but does not sort.
- The existing 3-argument `DuplicateSet(long size, String digest, List<FileEntry> files)` stays as a second constructor that makes each file its own single-link copy. It differs from the canonical constructor by arity, so there is no erasure clash (`List<PhysicalCopy>` and `List<FileEntry>` both erase to `List`), and DFS-003's `new DuplicateSet(10, "digest", null)` stays unambiguous and still throws `NullPointerException`. Verified at refinement by reading `DuplicateSetTest` and `FullHashConfirmer` (CCW-005.L-5).
- `domain.model` is already exported, so `module-info.java` does not change.

Safety invariants: none apply directly. This is a data type; it walks no tree and deletes nothing. It is the representation the "hard links are not duplicates" invariant (DFS-004) needs.

## Acceptance criteria

The full ID of each criterion is `DFS-017.AC-<n>`.

- **AC-1** `PhysicalCopy`'s constructor rejects null or empty links and a null entry; `DuplicateSet`'s canonical constructor rejects a null or blank digest, null files or copies or a null element in either, fewer than two physical copies, a link whose size differs from the set's size, and `copies` that do not exactly partition `files` (a path in two copies, or a path missing from or added to `files`); both store immutable defensive copies.
- **AC-2** The 3-argument `DuplicateSet(size, digest, List<FileEntry> files)` makes each file its own single-link copy, with `files()` returning the files as given, so `FullHashConfirmer` and DFS-003's tests compile and run unchanged.
- **AC-3** A set built through the canonical constructor with hard-linked paths grouped into one copy keeps them together: `copies()` has one `PhysicalCopy` per physical file, and `files()` still lists every path.

## Scenarios

```gherkin
Feature: Physical copies in a duplicate set

  @AC-1
  Scenario: Physical copies and duplicate sets reject inputs that break their definition
    Given a physical copy constructed with no links, or with a null entry
    And a duplicate set constructed with one physical copy, or with a blank digest, or with a link whose size differs from the set's, or with the same path in two copies, or with copies naming a path that is not in its files
    Then each construction fails with an exception
    And a copy or set constructed from a mutable list is unaffected when that list is changed afterwards

  @AC-2
  Scenario: The full-hash stage's construction still works unchanged
    Given a duplicate set constructed from the files "a", "b" and "c" through the 3-argument constructor
    Then it has three physical copies of one link each
    And files() returns "a", "b" and "c" in that order
    And constructing it with a null files list through the 3-argument form fails with a NullPointerException

  @AC-3
  Scenario: Hard-linked paths grouped into one copy
    Given files "a.bin", "b.bin" and "c.bin" of 10 KB, and copies ["a.bin", "b.bin"] and ["c.bin"]
    When a duplicate set is constructed through the canonical constructor
    Then copies() holds two physical copies, the first with links "a.bin" and "b.bin", the second with "c.bin"
    And files() returns "a.bin", "b.bin" and "c.bin"
```

Each scenario is run mechanically, as a JUnit test whose `@DisplayName` starts with its criterion's full ID.

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: new `PhysicalCopy`; `DuplicateSet` gains its `copies` component and keeps the 3-argument constructor; tests for both, in new test classes in the `domain.model` test package (a `PhysicalCopyTest`, and a separate class for `DuplicateSet`'s DFS-017 rules), leaving DFS-003's `DuplicateSetTest` unedited

**Out of scope:**
- Detecting hard links (DFS-004: the stage, `FileIdentity`, `NioFileIdentity`)
- Any change to `FullHashConfirmer`, `CandidateGroup`, `PartialHashFilter`, or DFS-001 to DFS-003's tests
- Which link of a copy a later deletion keeps (deletion planning)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] DFS-003's `DuplicateSetTest` and `FullHashConfirmerTest` pass without being edited, and `FullHashConfirmer` is unchanged (AC-2)
- [ ] `module-info.java` is unchanged and `ModuleExportsTest` still passes

## Open questions

None.
