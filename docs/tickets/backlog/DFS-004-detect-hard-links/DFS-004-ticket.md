---
id: DFS-004
title: Detect hard links within candidate groups
status: draft
type: feature
scope: [file-scanner]
priority: high
depends_on: [DFS-003]
touches: [us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-004: Detect hard links within candidate groups

## Story

As a user deleting duplicates
I want paths that are the same physical file recognised as one copy
So that a file is never reported as a duplicate of itself and deleting one hard link is never mistaken for removing a redundant copy

## Context

Safety invariant "Hard links are not duplicates" (CLAUDE.md). `BasicFileAttributes.fileKey()` returns null on Windows (verified), so it cannot identify them; use `Files.isSameFile` within a candidate group, never pairwise over the whole disk. `application` is tested against fakes, so the same-file check needs a secondary port. Hard links cannot be committed as fixtures: create them at test time with `Files.createLink` and skip the test (`Assumptions`) when the platform or privileges do not allow it.

## Scenarios

```gherkin
Feature: Hard-link detection

  Scenario: Two hard links to one file are not duplicates
    Given "folder1/a.bin" and "folder2/b.bin" are hard links to the same file of 10 KB
    When duplicates are detected
    Then no duplicate set is produced

  Scenario: A hard-linked pair counts as one copy
    Given "a.bin" and "b.bin" are hard links to one file and "c.bin" is an independent file with identical content
    When duplicates are detected
    Then one duplicate set is produced with two physical copies

  Scenario: Independent identical files remain separate copies
    Given three independent identical files
    When duplicates are detected
    Then one duplicate set is produced with three physical copies

  Scenario: Identity is only checked inside a candidate group
    Given files of different sizes
    When duplicates are detected
    Then the same-file check is never invoked between them
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: hard-link collapsing step
- `us.pochaev.dfs.scanner.domain.model`: how aliases are represented in `DuplicateSet`
- `us.pochaev.dfs.scanner.infra.port`: same-file check port
- `us.pochaev.dfs.scanner.infra.adapter`: implementation using `Files.isSameFile`

**Out of scope:**
- Symlink and junction handling (already done by the walker)
- Deletion planning

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] The adapter test creates the hard link at test time and skips via `Assumptions` when it cannot

## Open questions

- Scenarios are a first draft derived from CLAUDE.md only; review and complete them.
- Placement: after the full hash (CLAUDE.md order) or before hashing, which avoids hashing the same physical file twice? If before, the dependency on DFS-003 changes.
- Model: are aliases collapsed into one copy with the alias paths recorded, or dropped? Which path represents the copy?
- Port name and signature, and the cost of clustering within a group (equivalence classes via `isSameFile` is quadratic in the worst case): acceptable?
