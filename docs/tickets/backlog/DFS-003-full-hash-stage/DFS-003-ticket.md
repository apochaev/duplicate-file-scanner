---
id: DFS-003
title: Confirm duplicates with full SHA-256 hash
status: draft
type: feature
scope: [file-scanner]
priority: high
depends_on: [DFS-002]
touches: [us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-003: Confirm duplicates with full SHA-256 hash

## Story

As a user reviewing duplicates
I want files that still match after the sample stage confirmed by a full SHA-256 hash
So that every reported duplicate set has identical content, not merely similar samples

## Context

Stage 3 of the detection pipeline (CLAUDE.md design decision 3): full SHA-256 for files still matching, dropping groups of one afterwards, hashing in path order within a group. The output of this stage is the `DuplicateSet` view from design decision 1 (identical content, N paths). Files under the sample threshold skip the partial stage and arrive here directly. The hash is a filter, not the delete-time proof: design decision 4 requires a byte-for-byte compare against the keeper before quarantining, which is a separate ticket.

## Scenarios

```gherkin
Feature: Full-hash stage

  Scenario: Identical files form a duplicate set
    Given two identical files of 300 KB in "folder1" and "folder2"
    When the full-hash stage runs
    Then one duplicate set is produced containing both paths

  Scenario: Files differing only in the middle are separated
    Given two files of 300 KB that passed the partial stage and differ in the middle byte
    When the full-hash stage runs
    Then no duplicate set is produced

  Scenario: Only the identical files form a set
    Given three files of 300 KB, two identical and one differing in the middle byte
    When the full-hash stage runs
    Then one duplicate set is produced containing the two identical files

  Scenario: Small files go straight to the full hash
    Given two identical files of 1 KB
    When the full-hash stage runs
    Then one duplicate set is produced containing both paths

  Scenario: Files are hashed in path order
    Given a candidate group whose paths arrive out of order
    When the full-hash stage runs
    Then the hasher is asked for full hashes in ascending path order
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: the full-hash stage
- `us.pochaev.dfs.scanner.domain.model`: `DuplicateSet`
- `us.pochaev.dfs.scanner.infra.port`: full-hash method on `ContentHasher`
- `us.pochaev.dfs.scanner.infra.adapter`: SHA-256 implementation via the JDK `MessageDigest`

**Out of scope:**
- Byte-for-byte compare at delete time (`ContentComparator`)
- Hard-link detection (DFS-004) and scan orchestration (DFS-005)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `DuplicateSet` is in an exported domain package and `ModuleExportsTest` still passes

## Open questions

- Scenarios are a first draft derived from CLAUDE.md only; review and complete them.
- `DuplicateSet` shape: fields (size, digest, paths?) and the digest value type. Is it introduced here or in DFS-004/DFS-005?
- SHA-256 via the JDK `MessageDigest` is an assumed default, not confirmed with the user.
- Failure handling for unreadable files must match DFS-002; decide once.
