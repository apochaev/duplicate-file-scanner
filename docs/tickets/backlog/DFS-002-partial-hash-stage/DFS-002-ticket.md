---
id: DFS-002
title: Filter candidates by head-and-tail partial hash
status: draft
type: feature
scope: [file-scanner]
priority: high
depends_on: [DFS-001]
touches: [us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-002: Filter candidates by head-and-tail partial hash

## Story

As a user scanning large folders
I want files of equal size compared first by a cheap head-and-tail sample
So that full-content hashing is only spent on files that still look identical

## Context

Stage 2 of the detection pipeline (CLAUDE.md design decision 3): a partial hash over the head and tail (about 64 KB each), dropping groups of one afterwards. The stage is skipped for small files (under about 128 KB). Files within a group are hashed in path order for spinning-disk locality. Introduces the `ContentHasher` secondary port (sample and full) and its NIO adapter. Input is the candidate groups from DFS-001. Test `application` against a fake hasher; test the adapter on real temp files. Large fixtures are generated in code, not committed, and the near-duplicate case (same size, differing only in the middle byte, over the 128 KB threshold) must be covered.

## Scenarios

```gherkin
Feature: Partial-hash stage

  Background:
    Given a candidate group produced by the size-grouping stage

  Scenario: Files that differ in their first bytes are separated
    Given two files of 200 KB that differ in the first byte
    When the partial-hash stage runs
    Then no candidate group remains

  Scenario: Files that differ only in the middle survive the sample
    Given two files of 300 KB identical in the first 64 KB and the last 64 KB but differing in the middle byte
    When the partial-hash stage runs
    Then both files remain in one candidate group

  Scenario: Identical files survive
    Given two identical files of 300 KB
    When the partial-hash stage runs
    Then both files remain in one candidate group

  Scenario: Small files skip the stage
    Given two files of 100 KB
    When the partial-hash stage runs
    Then the hasher is not asked for a sample
    And both files remain in one candidate group

  Scenario: Groups of one are dropped after the stage
    Given three files of 300 KB, two identical and one differing in the first byte
    When the partial-hash stage runs
    Then one candidate group remains containing the two identical files

  Scenario: Files are hashed in path order
    Given a candidate group whose paths arrive out of order
    When the partial-hash stage runs
    Then the hasher is asked for samples in ascending path order
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: the partial-hash stage
- `us.pochaev.dfs.scanner.infra.port`: `ContentHasher`
- `us.pochaev.dfs.scanner.infra.adapter`: NIO implementation of the sample hash

**Out of scope:**
- Full SHA-256 (DFS-003) and hard-link detection (DFS-004)
- A persistent hash cache (deferred, would sit behind a port)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] The near-duplicate large-file case is generated in code, not committed

## Open questions

- Scenarios are a first draft derived from CLAUDE.md only; review and complete them.
- The 64 KB sample and 128 KB threshold are assumed defaults, not confirmed with the user. Should they be configurable via the scan config record?
- `ContentHasher` signature: one port with sample and full methods (added here) or only the sample method now and the full one in DFS-003? What does the sample digest cover (head and tail only, or also the size)?
- Failure handling: what happens to a file that becomes unreadable mid-stage? Dropped and reported as skipped? The progress listener does not exist until DFS-005, so how is it reported meanwhile?
