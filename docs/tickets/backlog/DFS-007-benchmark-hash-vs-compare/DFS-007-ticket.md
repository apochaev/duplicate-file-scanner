---
id: DFS-007
title: Benchmark full hash vs byte compare
status: draft
type: spike
scope: [file-scanner]
priority: low
lane: backend
depends_on: [DFS-003]
touches: [file-scanner/src/test/java/us/pochaev/dfs/scanner/bench, docs/tickets/backlog/DFS-007-benchmark-hash-vs-compare]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-007: Benchmark full hash vs byte compare

## Story

As a maintainer choosing how duplicate candidates are confirmed
I want measured numbers for full SHA-256 hashing versus byte-for-byte comparison across storage types and group sizes
So that DFS-003's confirmation strategy, and whether `DuplicateSet` keeps a digest, is decided on evidence instead of guesses

## Context

DFS-003 confirms candidates with a full SHA-256. The alternative is a byte-for-byte compare (the `ContentComparator` port already planned for delete time, decision 4). There is no file-size threshold at which one stops paying off; it depends on group size, storage and reuse:

- **Identical pair:** both read the same bytes; compare skips digest CPU. On NVMe, SHA-256 (roughly 0.4 to 2 GB/s per core, an unmeasured estimate) can be the bottleneck.
- **Same size, differs mid-file:** compare stops at the first difference; hashing always reads everything.
- **N identical files:** compare against one reference reads about 2(N-1) times the file size versus N times for hashing (N=3: 4S vs 3S; N=10: 18S vs 10S). A lockstep N-way compare gets back to N times but is complex.
- **Spinning disk:** hashing reads each file sequentially; compare interleaves reads across files and seeks.
- **Reuse:** a digest can go in the deferred persistent cache and identify a `DuplicateSet` across runs; a compare result cannot.

Hypothesis to test: compare wins for pairs on fast storage; hashing wins for groups of three or more and on spinning disks. A likely outcome is a hybrid (compare for pairs, hash for larger groups), which complicates `DuplicateSet` because pair sets would have no digest.

## Scenarios

```gherkin
Feature: Confirmation strategy benchmark

  Scenario: The measurements cover the agreed matrix
    Given the benchmark harness
    When it runs on each available storage type
    Then it records elapsed time and bytes read for both hashing and comparing
    And covers group sizes 2, 3, 5 and 10, file sizes 1 MB, 100 MB and 2 GB
    And covers both identical groups and groups differing in the middle or near the end

  Scenario: Timings exclude page-cache effects
    Given a file set larger than free memory, or caches flushed between runs
    When the timings are recorded
    Then reported numbers are cold-cache

  Scenario: The outcome is recorded
    When the benchmark is complete
    Then this ticket contains a results table and a recommendation
    And the confirmation-strategy question in DFS-003 is answered

  Scenario: Production code is untouched
    When the spike is finished
    Then nothing under any src/main directory has changed
```

## Scope

**Touches:**
- `file-scanner/src/test/java/us/pochaev/dfs/scanner/bench`: throwaway benchmark harness; large files generated in code, never committed
- This ticket's folder: the results table and recommendation

**Out of scope:**
- Implementing the chosen strategy (DFS-003 or a follow-up ticket)
- A lockstep N-way compare, unless the results justify it

## Definition of done

- [ ] Every scenario above is satisfied
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] Results and recommendation are written into this ticket, and DFS-003's open question is updated

## Open questions

- Scenarios are a first draft derived from a discussion only; review and complete them.
- Harness: a plain `main` in the test source set, or a JMH dependency? Adding a dependency needs a decision.
- How to get cold-cache timings reliably on Windows (files larger than RAM, or another method)?
- Which storage is available to run on (NVMe, SATA SSD, spinning disk, network share)?
- What margin counts as worthwhile, for example at least 20% faster on the common case?
