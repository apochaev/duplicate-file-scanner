---
id: DFS-002
title: Filter candidates by head-and-tail partial hash
status: in-progress
type: feature
scope: [file-scanner]
archetype: pipeline-stage
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

This is a `pipeline-stage` archetype ticket (`.claude/archetypes/pipeline-stage/archetype.md`). Several checklist items resolve directly from DFS-001 or the archetype's own text, without a separate decision: no accumulation to merge (item 1 — the archetype names DFS-002 directly as a pure filter over a finished list); `CandidateGroup` is reused as the inter-stage currency, not a new type (item 2 — DFS-001's ticket calls it "the currency between pipeline stages 1 to 3"); output order is preserved from the input, both the group list and each group's ascending-path member order (item 3); immutability is inherited from `CandidateGroup`'s own constructor (item 4); there is nothing to collapse, since DFS-001 already collapsed repeated paths before this stage sees them (item 5); no memory measurement is needed, since the stage holds one candidate group at a time while sampling (item 7); groups of one are already an existing scenario (item 8); the near-duplicate fixture is already noted above (item 12); and there is no shared mutable state, since the stage is a stateless filter holding only a port reference (item 13).

**Decided (2026-09-21): one `ContentHasher` port interface, declaring both `sampleHash` and `fullHash` now.** Matches CLAUDE.md's provisional port sketch ("`ContentHasher` (sample and full)"). DFS-002's NIO adapter implements `sampleHash` for real; `fullHash` throws `UnsupportedOperationException` with a message pointing at DFS-003, which fills it in. DFS-003's own ticket already lists `infra.adapter` under its `touches`, so this creates no boundary conflict.

**Decided (2026-09-21): the sample hash is SHA-256 over the concatenated head and tail bytes only, size excluded.** One hashing algorithm for both the sample and full stages, so `ContentHasher` needs only one dependency and a test fake can return arbitrary fixed digests for either method. Size is not folded into the sample digest: every member of a candidate group already shares one size by construction (DFS-001), so hashing it in would be redundant, not additionally safe. A faster non-cryptographic checksum (CRC32) was considered — a false match there only costs one extra full-hash pass, since DFS-003's real hash is what actually enforces correctness — but the stage is disk-I/O-bound on a 64 KB read (CLAUDE.md's own locality concern), so the CPU saving looked marginal against the cost of a second hashing primitive.

**Decided (2026-09-21): the sample size (64 KB) and the skip threshold (128 KB) are constructor arguments with those values as defaults.** Matches DFS-001's `SizeIndex(long minSize)` precedent exactly (a no-arg constructor defaulting sensibly, enforced inside the stage) and the archetype checklist's own stated default for a tunable (item 6): a constructor argument for now, a field of DFS-005's scan config record later.

**Decided (2026-09-21): a file that becomes unreadable between the previous stage and this one is dropped from its group; the group is not failed.** (Checklist item 10.) The group's remaining members are still compared; if that leaves fewer than two, the existing groups-of-one rule drops it. One file's read problem must never hide real duplicates among its still-healthy groupmates — that would cut against the project's purpose. Reporting this properly is deferred: the progress listener does not exist until DFS-005, so for now the dropped file is simply excluded from the stage's output, with no separate signal. This is a punt, not a permanent decision; DFS-005 is expected to give it a real reporting path.

None of CLAUDE.md's safety invariants apply directly to this stage: it neither walks the filesystem tree (symlinks, junctions, empty-file exclusion are DFS-001's and the walker's concern) nor deletes anything (the deletion invariants are downstream of the whole pipeline).

## Acceptance criteria

The full ID of each criterion is `DFS-002.AC-<n>`.

- **AC-1** Files that differ within the sampled head or tail bytes are separated out of their candidate group.
- **AC-2** Files identical in the sampled bytes but differing only in the untested middle survive the stage (correctly deferred to the full-hash stage, not falsely confirmed here).
- **AC-3** Files identical throughout survive the stage.
- **AC-4** Files at or under the skip threshold bypass the sample entirely (the hasher is never invoked for them) and pass straight through.
- **AC-5** A group left with fewer than two surviving members after this stage is dropped.
- **AC-6** Members within a group are sampled in ascending path order.
- **AC-7** A file that becomes unreadable during this stage is dropped from its group rather than failing the whole group.
- **AC-8** `ContentHasher.fullHash` throws `UnsupportedOperationException` naming DFS-003, while `sampleHash` is fully implemented by the NIO adapter.
- **AC-9** The sample size and the skip threshold are each configurable via a constructor argument, defaulting to 64 KB and 128 KB respectively.

## Scenarios

```gherkin
Feature: Partial-hash stage

  Background:
    Given a candidate group produced by the size-grouping stage

  @AC-1
  Scenario: Files that differ in their first bytes are separated
    Given two files of 200 KB that differ in the first byte
    When the partial-hash stage runs
    Then no candidate group remains

  @AC-2
  Scenario: Files that differ only in the middle survive the sample
    Given two files of 300 KB identical in the first 64 KB and the last 64 KB but differing in the middle byte
    When the partial-hash stage runs
    Then both files remain in one candidate group

  @AC-3
  Scenario: Identical files survive
    Given two identical files of 300 KB
    When the partial-hash stage runs
    Then both files remain in one candidate group

  @AC-4
  Scenario: Small files skip the stage
    Given two files of 100 KB
    When the partial-hash stage runs
    Then the hasher is not asked for a sample
    And both files remain in one candidate group

  @AC-5
  Scenario: Groups of one are dropped after the stage
    Given three files of 300 KB, two identical and one differing in the first byte
    When the partial-hash stage runs
    Then one candidate group remains containing the two identical files

  @AC-6
  Scenario: Files are hashed in path order
    Given a candidate group whose paths arrive out of order
    When the partial-hash stage runs
    Then the hasher is asked for samples in ascending path order

  @AC-7
  Scenario: An unreadable file is dropped, not its whole group
    Given three files of 300 KB that would otherwise all remain candidates, and one of them becomes unreadable before the stage samples it
    When the partial-hash stage runs
    Then the unreadable file is absent from the stage's output
    And the remaining two files still form one candidate group

  @AC-7
  Scenario: An unreadable file leaves its group with fewer than two members
    Given two files of 300 KB, one of which becomes unreadable before the stage samples it
    When the partial-hash stage runs
    Then no candidate group remains

  @AC-8
  Scenario: The full-hash method is stubbed for DFS-003
    Given the `ContentHasher` NIO adapter built by this ticket
    When `fullHash` is called
    Then it throws `UnsupportedOperationException` naming DFS-003

  @AC-9
  Scenario: A custom sample size and threshold change stage behavior
    Given a stage constructed with a sample size of 4 KB and a skip threshold of 8 KB
    And two files of 10 KB identical in the first and last 4 KB but differing in the middle
    When the partial-hash stage runs
    Then both files remain in one candidate group, sampled using the 4 KB size rather than the 64 KB default
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
- [ ] `ContentHasher.fullHash` throws `UnsupportedOperationException` naming DFS-003 as the ticket that implements it
- [ ] Every item of the `pipeline-stage` archetype's edge-case checklist is addressed in the design doc or marked not applicable with a one-line reason

## Open questions

None.
