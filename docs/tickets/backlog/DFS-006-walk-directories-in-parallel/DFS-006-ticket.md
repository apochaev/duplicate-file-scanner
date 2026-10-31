---
id: DFS-006
title: Walk directories in parallel
status: draft
type: feature
scope: [file-scanner]
priority: low
lane: backend
depends_on: [DFS-005]
touches: [us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-006: Walk directories in parallel

## Story

As a user scanning a very large tree on fast or networked storage
I want directories walked concurrently
So that the file walk stops being the bottleneck, without changing what is found

## Context

The walker (`NioFileTreeWalker` on `Files.walkFileTree`) is sequential. Parallel directory listing helps on SSD and NVMe, network shares, and huge trees of small files. It does nothing for, or hurts, spinning disks (seek thrash; design decision 3 optimizes for locality there), and for trees with large files hashing dominates. So it is off by default and driven by the `parallelism` setting in the scan config record (DFS-005), and it must be benchmarked before it is built.

Design notes from the discussion:
- **Queue:** workers take a directory task, list it, emit `FileFound` for regular files and submit each plain subdirectory as a new task. The never-follow-links rule means there are no cycles, so no visited-set is needed. Tasks run on the caller-supplied executor; concurrent I/O is capped at `parallelism`; a pending-task counter or `Phaser` detects completion (structured concurrency is preview and not allowed); a shared flag cancels.
- **Hybrid alternative:** `walkFileTree` with a `maxDepth`; directories at the depth limit become tasks, each walked fully by a worker. Keeps the JDK's cached directory-listing attributes and the existing classifier, but balances badly on one huge subtree.
- **Listener contract:** `WalkListener.onEvent` is currently called by one thread and the port documents a depth-first walk. A parallel walker breaks both. Either a lock around the listener (contends on every file) or per-worker sinks: a supplier creates one listener per worker and the caller merges afterwards, which fits one `SizeIndex` per worker (DFS-001).
- **Attribute cost:** the current walker chose `walkFileTree` to reuse cached directory-listing attributes. A hand-rolled queue over `DirectoryStream` may pay an extra attribute read per file. Benchmark, do not assume.
- **One copy of the safety logic:** symlink, junction and reparse-point detection is shared between the sequential and parallel walkers (a shared classifier).
- **Order:** walk order becomes undefined; nothing may depend on it (DFS-001 sorts its output).

## Scenarios

```gherkin
Feature: Parallel directory walk

  Scenario: A parallel walk finds the same files as a sequential walk
    Given a tree with nested folders and files
    When it is walked with parallelism 4 and again with parallelism 1
    Then both walks report the same set of files

  Scenario: Links are still not followed
    Given a tree containing a link to a folder (created at test time, skipped when not permitted)
    When it is walked with parallelism 4
    Then the link is reported as skipped and its target is not walked through the link

  Scenario: An unreadable folder does not stop the walk
    Given a tree in which one folder cannot be read
    When it is walked with parallelism 4
    Then that folder is reported as skipped and all other files are found

  Scenario: Cancellation stops all workers
    Given a walk in progress with parallelism 4
    When the listener asks to stop
    Then the walk returns without delivering further events once workers have wound down

  Scenario: Parallelism 1 keeps today's behaviour
    Given the same tree
    When it is walked with parallelism 1
    Then events arrive in the same depth-first order as with the sequential walker
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.infra.port`: listener contract, possibly a new walk signature
- `us.pochaev.dfs.scanner.infra.adapter`: the parallel walker and the shared classifier

**Out of scope:**
- Parallel hashing
- Per-device concurrency limits for spinning disks

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] A benchmark on a large tree shows a worthwhile gain, recorded in this ticket before it is closed

## Open questions

- Scenarios are a first draft derived from a discussion only; review and complete them.
- Is it worth building at all? Which storage should the benchmark cover?
- Full queue or the `maxDepth` hybrid?
- Listener contract: per-worker sinks or a serialized listener, and the resulting port signature change.
- Extra attribute read per file with `DirectoryStream`: how bad is it, measured?
- Default `parallelism`, and does the same setting also drive hashing?
- Do spinning disks need a concurrency cap or an automatic fallback to 1?
