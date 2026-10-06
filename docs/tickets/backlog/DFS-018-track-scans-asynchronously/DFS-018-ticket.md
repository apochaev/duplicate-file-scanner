---
id: DFS-018
title: Track scans asynchronously
status: in-review
type: feature
scope: [file-scanner]
archetype: use-case
priority: medium
depends_on: [DFS-005, CCW-011]
touches: [us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.domain.usecases.command, us.pochaev.dfs.scanner.domain.usecases.query, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-27
updated: 2026-10-06
completed:
---

# DFS-018: Track scans asynchronously

## Story

As a caller of the scanner library (the CLI, later a server)
I want to start a scan and receive a scan ID immediately
So that I can track progress, cancel it, and read the duplicate sets once it finishes, without blocking

## Context

Split on 2026-09-27 from the original DFS-005. Runs DFS-005's synchronous scan pipeline on a caller-supplied executor and tracks each scan by `ScanId`. Provisional port sketch (CLAUDE.md): commands `StartScan` (returns a scan ID immediately) and `CancelScan`; queries `GetScanStatus` and `ListDuplicateSets`; secondary port `ScanRepository` with an in-memory adapter first. Spring-friendliness rules apply: the executor is supplied by the caller, and scan state lives behind `ScanRepository` so several scans run concurrently (no state in singleton fields). `ScanId`, `ScanStatus` (RUNNING, COMPLETED, CANCELLED, FAILED), `CancelScan` and `GetScanStatus` already exist as a first slice and are not final designs.

Decisions (refinement, 2026-10-05):

1. **Archetype `use-case`, built first by CCW-011.** DFS-018 is a lifecycle service (it starts the pipeline on an executor, keeps per-`ScanId` state behind a port, and answers commands and queries), not a detection stage, and DFS-005 showed that `pipeline-stage` fits such a ticket poorly (DFS-005.L-8: no skeleton fit, checklist items 2 and 7 did not apply). More use cases follow (DFS-019, deletion preview and execution, undo/purge, location overlaps). CCW-011 builds a small `use-case` archetype (`archetype.md` with a use-case checklist, a `design.md` template, a test guide; no Java skeletons yet), its checklist drawn from the questions settled in this refinement; DFS-018 depends on it.
2. **Progress both pushed and pulled.** `StartScan` takes an optional `ScanListener` (push: the CLI's live progress line), and `GetScanStatus` returns a snapshot record in `domain.model` instead of the bare `ScanStatus` enum (pull: a server polling `GET /scans/{id}`). The snapshot carries at least the status, the current `ScanPhase`, files walked so far, groups done and total for the current phase, and, for a FAILED scan, the failure message. The service keeps the snapshot current from the same pipeline events it forwards to the caller's listener. The contract states that the listener is called on the executor's thread, not the caller's. Replacing `GetScanStatus`'s return type is allowed: the first slice is not a final design.
3. **Six statuses: QUEUED, RUNNING, CANCELLING, COMPLETED, CANCELLED, FAILED.** `StartScan` records the scan as QUEUED before handing it to the executor; it becomes RUNNING when the executor starts the task. `cancel()` on a QUEUED scan makes it CANCELLED at once, and the task, when picked up, exits without walking. `cancel()` on a RUNNING scan makes it CANCELLING and raises the pipeline's stop signal; it becomes CANCELLED only once the pipeline has actually stopped (its `CancellationException`). COMPLETED, CANCELLED and FAILED are terminal, and `cancel()` on a terminal scan, or a second `cancel()` on a CANCELLING one, has no effect. A scan whose pipeline returns normally after `cancel()` was requested reports COMPLETED: its result is real, so the pipeline's own outcome wins. The status values replace the first-slice enum in place.
4. **Every use case returns a sealed `Result<T>`: `Success<T>(T value)` or `Failure<T>(ErrorCode code, String message)`, in `domain.model`.** This follows CLAUDE.md's "sealed interfaces for results and variants". `ErrorCode` is an enum, starting with `UNKNOWN_SCAN`, `NO_LOCATIONS`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`, `EXECUTOR_REJECTED` and `SCAN_NOT_COMPLETED` (decision 5); later use cases add codes. The signatures become: `StartScan` returns `Result<ScanId>`, `GetScanStatus` returns `Result<ScanSnapshot>`, `CancelScan` returns `Result<ScanSnapshot>` (the state after the request: CANCELLING, CANCELLED, or the terminal state it already had), and `ListDuplicateSets` returns a `Result` too (its value type is decision 5). Where the line falls: expected outcomes are values (an unknown `ScanId` for every operation; for `StartScan`, an empty location list, a location that does not exist or is not a directory, which the service translates from the pipeline's exceptions, and the executor rejecting the task). A `null` argument stays a `NullPointerException`, a caller bug and not an outcome. A scan that ran and failed is a successful query whose snapshot says FAILED: the result describes the call, not the scan. The first-slice `CancelScan` and `GetScanStatus` signatures change accordingly.
5. **`GetScanResult` (renamed from the sketch's `ListDuplicateSets`) returns `Result<ScanResult>`: the whole result, unpaged, only for a COMPLETED scan.** The value is DFS-005's `ScanResult(sets, skipped)` in the pipeline's own order (largest files first, DFS-005 decision 8), so the caller also gets the skip list. For any other status it returns `Failure(SCAN_NOT_COMPLETED, ...)`, a new `ErrorCode`, whose message names the current status. A CANCELLED or FAILED scan has no result (DFS-005 decision 7: a stopped pipeline returns no partial result). Paging, filtering and other views belong to the tickets that build them (location overlaps, the CLI review screens).
6. **`StartScan` validates on the caller's thread, before anything is queued.** It rejects an empty location list (`NO_LOCATIONS`), then resolves each location through the `LocationResolver` port (DFS-005), translating `NoSuchFileException` to `LOCATION_NOT_FOUND` and `NotDirectoryException` to `NOT_A_DIRECTORY`. A rejected start creates no scan record and no ID. Only then is the scan recorded QUEUED and submitted; the pipeline resolves the locations again inside `scan()`, unchanged (microseconds per location). A location that disappears between the check and the pipeline's own resolution fails that scan through the pipeline's existing rules (FAILED, with the message); `ScanPipeline`'s API is not changed.
7. **Failures while running end up in the snapshot.** A pipeline that throws an `IOException` or a `RuntimeException` (other than the `CancellationException` of decision 3) makes the scan FAILED, with the snapshot's failure message set to the exception's class name and message (for example `java.nio.file.NoSuchFileException: D:\Photos`). An `Error` is not caught, but a `finally` block first marks the scan FAILED, so no snapshot stays RUNNING or CANCELLING forever. A caller's `ScanListener` that throws is detached on its first exception: the scan continues, no further events go to that listener, and the snapshot records it in a `listenerFailure` field (the exception's class name and message; null while none). The core never prints (CLAUDE.md).
8. **All per-scan state is one immutable `ScanRecord` behind `ScanRepository`, changed only by an atomic update.** The record holds the snapshot and, once COMPLETED, the `ScanResult`. The port (in `infra.port`) has `create`, `find` and `update(ScanId, UnaryOperator<ScanRecord>)`, the update applied atomically; the in-memory adapter (in `infra.adapter`) backs it with a `ConcurrentHashMap` and `compute`. The cancel request is the status itself, not a separate flag: `cancel()` atomically moves RUNNING to CANCELLING (or QUEUED to CANCELLED), and the pipeline's stop signal reads `status == CANCELLING` from the repository. Every transition is one atomic update that checks the current status first, which settles the races: a completion and a cancel arriving together produce exactly one outcome (COMPLETED wins, decision 3), and a cancel during pickup from the queue cannot leave a scan RUNNING. The service implementing the use cases holds only its ports, its seams (the `ScanRunner` of decision 12) and the executor, never per-scan state (CLAUDE.md's "no state in singleton fields").
9. **Scans are kept until the process exits.** Nothing removes or evicts a scan in this ticket; the CLI runs one scan per process. Removal (a `ForgetScan` command) or eviction is deferred to the ticket that builds a long-running server, and is listed under Out of scope. Automatic eviction is deliberately not the default: a scan vanishing while a user reviews it would surface as `UNKNOWN_SCAN` right before a deletion step.
10. **Deterministic tests for the logic, one real-thread test, and no `Thread.sleep`.** `application` is tested against fakes: a hand-written `ManualExecutor` that queues tasks until the test calls `runNext()` (pins QUEUED), a fake walker that blocks on a latch at a chosen event (pins RUNNING and CANCELLING), and fakes of the other pipeline ports and of `ScanRepository`. The completion-versus-cancel race is tested by applying the two transitions in both orders, not by racing threads. One end-to-end test wires a real virtual-thread executor, the in-memory repository and the real NIO adapters over a `@TempDir`, runs two scans concurrently and waits for their terminal states with a bounded timeout. No test uses `Thread.sleep`; waiting is on a latch, a future or a bounded poll.
11. **`StartScan` has two overloads, with and without a listener,** instead of accepting a null listener (decision 4: null is a caller bug). If the executor rejects the task (`RejectedExecutionException`), the already-QUEUED record is moved to FAILED with the rejection message, and `StartScan` returns `Failure(EXECUTOR_REJECTED, ...)` instead of the ID. The record is created before submission so that a task the executor starts immediately always finds it.
12. **The service runs the pipeline through a package-private seam, `ScanRunner`.** A functional interface in `application`, `ScanResult run(List<Path> locations, ScanConfig config, ScanListener listener, BooleanSupplier stopSignal) throws IOException`, which DFS-005's `ScanPipeline` implements (its existing `scan` method, unchanged in behaviour). The service depends on `ScanRunner`; production passes the real `ScanPipeline`, and `application` tests pass a scripted fake that emits chosen events, blocks at a chosen point, returns a chosen result or throws a chosen exception. `ScanPipeline` is `final` and is not subclassed. AC-17 still proves the real pipeline end to end.

## Acceptance criteria

The full ID of each criterion is `DFS-018.AC-<n>`.

- **AC-1** `StartScan` with valid locations returns `Success(ScanId)` and the scan's status is QUEUED until the executor runs its task (decisions 3, 11).
- **AC-2** When the executor runs the task, the scan is RUNNING while the pipeline works and COMPLETED once it returns, and `GetScanResult` then returns `Success` holding the pipeline's `ScanResult` (decisions 3, 5).
- **AC-3** `StartScan` rejects an empty location list with `Failure(NO_LOCATIONS)`, a missing location with `Failure(LOCATION_NOT_FOUND)` and a location that is a file with `Failure(NOT_A_DIRECTORY)`, on the caller's thread, without creating a scan record or submitting a task (decision 6).
- **AC-4** If the executor rejects the task, `StartScan` returns `Failure(EXECUTOR_REJECTED)` and the scan's record is FAILED with the rejection message (decision 11).
- **AC-5** `GetScanStatus` returns `Success(snapshot)` whose status, current phase, files walked and groups done/total follow the pipeline's events; a QUEUED snapshot has no phase and zero counts (decision 2).
- **AC-6** A listener passed to `StartScan` receives every pipeline event in order, on the executor's thread; the overload without a listener runs the same scan (decisions 2, 11).
- **AC-7** `CancelScan` on a QUEUED scan returns `Success` with status CANCELLED, and the task, when the executor runs it, never runs the pipeline (decision 3).
- **AC-8** `CancelScan` on a RUNNING scan returns `Success` with status CANCELLING, the pipeline's stop signal reads true from then on, and the scan becomes CANCELLED once the pipeline stops; `GetScanResult` then returns `Failure(SCAN_NOT_COMPLETED)` (decisions 3, 8).
- **AC-9** `CancelScan` on a CANCELLING, COMPLETED, CANCELLED or FAILED scan changes nothing and returns `Success` with the unchanged snapshot (decision 3).
- **AC-10** A scan whose pipeline returns normally after a cancel was requested ends COMPLETED with its result, whichever of the two transitions is applied first (decisions 3, 8).
- **AC-11** `GetScanStatus`, `CancelScan` and `GetScanResult` return `Failure(UNKNOWN_SCAN)` for a `ScanId` that `StartScan` never returned (decision 4).
- **AC-12** `GetScanResult` returns `Failure(SCAN_NOT_COMPLETED)` whose message names the current status for a QUEUED, RUNNING, CANCELLING, CANCELLED or FAILED scan (decision 5).
- **AC-13** A pipeline that throws an `IOException` or a `RuntimeException` other than `CancellationException` makes the scan FAILED, with the snapshot's failure message `<exception class name>: <message>` (decision 7).
- **AC-14** A pipeline that throws an `Error` leaves the scan FAILED, and the `Error` still propagates out of the task (decision 7).
- **AC-15** A listener that throws is detached after its first exception: it receives no further events, the scan still completes, and the snapshot's `listenerFailure` holds the exception's class name and message (decision 7).
- **AC-16** Every use case throws `NullPointerException` for a null argument and submits nothing (decision 4).
- **AC-17** Two scans started on one service instance with a real virtual-thread executor, the in-memory repository and the real NIO adapters both end COMPLETED, and each result holds only files from its own location (decisions 8, 10).
- **AC-18** The in-memory `ScanRepository` applies concurrent `update` calls on one scan atomically: no update is lost (decision 8).

## Scenarios

```gherkin
Feature: Starting, tracking and cancelling a scan

  Background:
    Given a scan service built from a fake location resolver, a scripted ScanRunner that can emit chosen events, block at a chosen point, return a chosen result or throw a chosen exception, a ManualExecutor and an in-memory scan repository
    And locations "C:/a" and "C:/b" that resolve to directories

  @AC-1
  Scenario: Starting a scan returns an ID while the scan waits in the queue
    When StartScan is invoked with locations ["C:/a"]
    Then it returns Success holding a ScanId
    And GetScanStatus for that ID reports QUEUED
    And the ManualExecutor holds exactly one task

  @AC-2
  Scenario: A scan runs and completes with its result
    Given a started scan over ["C:/a"] whose scripted ScanRunner returns one duplicate set of "C:/a/x.jpg" and "C:/a/y.jpg"
    When the ScanRunner is set to block after its first event and the ManualExecutor runs the task
    Then GetScanStatus reports RUNNING
    When the ScanRunner is released
    Then GetScanStatus reports COMPLETED
    And GetScanResult returns Success holding the one duplicate set and an empty skip list

  @AC-3
  Scenario Outline: An invalid start is rejected before anything is queued
    When StartScan is invoked with <locations>
    Then it returns Failure with code <code>
    And the repository holds no scan record
    And the ManualExecutor holds no task

    Examples:
      | locations              | code               |
      | []                     | NO_LOCATIONS       |
      | ["C:/missing"]         | LOCATION_NOT_FOUND |
      | ["C:/a/file.txt"]      | NOT_A_DIRECTORY    |
      | ["C:/a", "C:/missing"] | LOCATION_NOT_FOUND |

  @AC-4
  Scenario: The executor rejects the task
    Given an executor that throws RejectedExecutionException("queue full") on submit
    When StartScan is invoked with ["C:/a"]
    Then it returns Failure with code EXECUTOR_REJECTED
    And the repository's only record is FAILED with a failure message containing "queue full"

  @AC-5
  Scenario: The snapshot follows the pipeline's progress
    Given a started scan over ["C:/a"]
    Then GetScanStatus returns a snapshot with status QUEUED, no phase, 0 files walked and 0 of 0 groups
    When the task runs and the ScanRunner has sent FileWalked(48210), PhaseStarted(FULL_HASH) and GroupDone(FULL_HASH, 37, 120) and is blocked
    Then GetScanStatus returns a snapshot with status RUNNING, phase FULL_HASH, 48210 files walked and 37 of 120 groups

  @AC-6
  Scenario: A listener receives every event on the executor's thread
    Given a recording listener
    When StartScan is invoked with ["C:/a"] and the listener, and the task runs to completion
    Then the listener received exactly the ScanRunner's events, in order
    And every event arrived on the thread that ran the task, not the test thread

  @AC-6
  Scenario: The overload without a listener runs the same scan
    When StartScan is invoked with ["C:/a"] and no listener, and the task runs to completion
    Then GetScanStatus reports COMPLETED

  @AC-7
  Scenario: Cancelling a queued scan cancels it at once and it never walks
    Given a started scan over ["C:/a"] that is still QUEUED
    When CancelScan is invoked
    Then it returns Success with status CANCELLED
    When the ManualExecutor runs the task
    Then the ScanRunner was never called
    And GetScanStatus still reports CANCELLED

  @AC-8
  Scenario: Cancelling a running scan goes through CANCELLING
    Given a started scan over ["C:/a"] whose ScanRunner is blocked after its third event
    When CancelScan is invoked
    Then it returns Success with status CANCELLING
    And the stop signal passed to the ScanRunner reads true
    When the ScanRunner is released and throws CancellationException
    Then GetScanStatus reports CANCELLED
    And GetScanResult returns Failure with code SCAN_NOT_COMPLETED

  @AC-9
  Scenario Outline: Cancelling a scan that is already stopping or finished changes nothing
    Given a scan whose status is <status>
    When CancelScan is invoked
    Then it returns Success with status <status>
    And the scan's snapshot is unchanged

    Examples:
      | status     |
      | CANCELLING |
      | COMPLETED  |
      | CANCELLED  |
      | FAILED     |

  @AC-10
  Scenario: Completion wins over a cancel request that lost the race
    Given a RUNNING scan
    When CancelScan moves it to CANCELLING
    And the ScanRunner then returns its result normally
    Then GetScanStatus reports COMPLETED
    And GetScanResult returns Success holding that result

  @AC-10
  Scenario: A cancel request after completion is a no-op
    Given a RUNNING scan
    When the ScanRunner returns its result normally
    And CancelScan is invoked afterwards
    Then CancelScan returns Success with status COMPLETED
    And GetScanResult returns Success holding that result

  @AC-11
  Scenario Outline: An unknown scan ID is a failure, not an exception
    Given a ScanId that StartScan never returned
    When <operation> is invoked with it
    Then it returns Failure with code UNKNOWN_SCAN

    Examples:
      | operation     |
      | GetScanStatus |
      | CancelScan    |
      | GetScanResult |

  @AC-12
  Scenario Outline: The result is only available once the scan completed
    Given a scan whose status is <status>
    When GetScanResult is invoked
    Then it returns Failure with code SCAN_NOT_COMPLETED and a message containing "<status>"

    Examples:
      | status     |
      | QUEUED     |
      | RUNNING    |
      | CANCELLING |
      | CANCELLED  |
      | FAILED     |

  @AC-13
  Scenario Outline: A pipeline failure makes the scan FAILED with the reason
    Given a started scan over ["C:/a"] whose ScanRunner throws <exception>
    When the task runs
    Then GetScanStatus reports FAILED with failure message "<message>"

    Examples:
      | exception                                 | message                                       |
      | new NoSuchFileException("C:/a")           | java.nio.file.NoSuchFileException: C:/a       |
      | new IllegalStateException("bug in stage") | java.lang.IllegalStateException: bug in stage |

  @AC-14
  Scenario: An Error still leaves the scan FAILED
    Given a started scan over ["C:/a"] whose ScanRunner throws new OutOfMemoryError("test")
    When the task runs
    Then the OutOfMemoryError propagates out of the task
    And GetScanStatus reports FAILED

  @AC-15
  Scenario: A throwing listener is detached and the scan carries on
    Given a listener that throws IllegalStateException("console closed") on its second event and records every event it receives
    When StartScan is invoked with ["C:/a"] and that listener, and the task runs to completion
    Then the listener received exactly two events
    And GetScanStatus reports COMPLETED with listenerFailure "java.lang.IllegalStateException: console closed"

  @AC-16
  Scenario Outline: A null argument is a caller bug
    When <call> is invoked
    Then it throws NullPointerException
    And the ManualExecutor holds no task

    Examples:
      | call                                   |
      | StartScan with null locations          |
      | StartScan with a null location element |
      | StartScan with a null config           |
      | StartScan with a null listener         |
      | GetScanStatus with a null ScanId       |
      | CancelScan with a null ScanId          |
      | GetScanResult with a null ScanId       |

  @AC-17
  Scenario: Two concurrent scans on real threads and real files do not share state
    Given a @TempDir holding folder "one" with identical files "one/a.bin" and "one/b.bin", and folder "two" with identical files "two/c.bin" and "two/d.bin" of different content
    And a scan service built from a virtual-thread executor, the in-memory repository and the real NIO adapters
    When scans over ["one"] and ["two"] are started one after the other without waiting
    And both reach a terminal status within 10 seconds
    Then both are COMPLETED
    And the first scan's result holds exactly one set, of "one/a.bin" and "one/b.bin"
    And the second scan's result holds exactly one set, of "two/c.bin" and "two/d.bin"

  @AC-18
  Scenario: Concurrent updates to one scan are never lost
    Given the in-memory repository holding one scan record
    When 8 threads each apply 1000 updates that increment the record's files-walked count
    Then the record's files-walked count is 8000
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: status and result types, if they change
- `us.pochaev.dfs.scanner.domain.usecases.command` and `.query`: `StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult`
- `us.pochaev.dfs.scanner.application`: the scan lifecycle service implementing the use cases, and the package-private `ScanRunner` seam; `ScanPipeline` changes only by implementing `ScanRunner` (behaviour unchanged, DFS-005's tests untouched; decision 12)
- `us.pochaev.dfs.scanner.infra.port` and `.adapter`: `ScanRepository` and its in-memory adapter

**Out of scope:**
- The scan pipeline itself (DFS-005)
- `ScannerFactories`, `module-info` exports, `allowEmptyShould(true)` (DFS-019)
- Location overlaps, deletion plans, quarantine and undo/purge
- Any CLI or Spring wiring in the `cli` subproject
- Removing or evicting finished scans (a `ForgetScan` command or automatic eviction); needed once a long-running server exists (decision 9)

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] No test uses `Thread.sleep`; waiting is on a latch, a future or a bounded poll (decision 10)
- [x] `module-info.java` is unchanged: every new exported type is in an already exported package (`domain.model`, `domain.usecases.command`, `domain.usecases.query`), and `ModuleExportsTest` stays green
- [x] The service implementing the use cases has no field other than its ports, its seams (`ScanRunner`, decision 12) and the executor (decision 8)
- [x] Every item of the `use-case` archetype's edge-case checklist (CCW-011) is addressed in the design doc or marked not applicable with a one-line reason

## Open questions

None.
