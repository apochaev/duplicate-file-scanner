---
id: DFS-042
title: Run plan checks asynchronously with status and cancel
status: in-progress
type: feature
scope: [file-scanner]
archetype: use-case
gates: [design, done]
autorun: true
priority: medium
lane: backend
depends_on: [DFS-034]
touches:
  - us.pochaev.dfs.scanner.domain.usecases.command
  - us.pochaev.dfs.scanner.domain.usecases.query
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.domain.factory
  - us.pochaev.dfs.scanner.infra.port
  - us.pochaev.dfs.scanner.infra.adapter
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-042: Run plan checks asynchronously with status and cancel

## Story

As a user verifying a large deletion plan in the local UI
I want the content verify to run in the background, with a status I can poll and a cancel button
So that a check of many gigabytes does not block the page and can be stopped at any time

## Context

Split from DFS-034 (its decision 1): DFS-034 builds the synchronous `VerifyPlanContent.verify(plan, listener, cancelled)` with progress events and a cancel signal; this ticket wraps it in an asynchronous job a server can start, poll and cancel (`docs/design/ui.md` U7.3), on a caller-supplied executor (CLAUDE.md "Spring-friendliness rules"), with per-job state behind a port ("no state in singleton fields"). Executing a plan (quarantine, CLAUDE.md decision 2) will be long-running too, so the job model should be designed to run it later without a copy, rather than repeating the scan's model (DFS-018: `StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult`, six-state `ScanStatus`, `ScanRepository`).

### Decisions

1. **Typed ports per use case, shared job machinery inside `application`.** The exported API is concrete, like the scan's: `StartPlanVerify` and `CancelPlanVerify` in `domain.usecases.command`, `GetPlanVerifyStatus` and `GetPlanVerifyResult` in `domain.usecases.query`, and a `PlanVerifyId`, so the server maps them onto endpoints and an event stream as it did for DFS-040. What repeats across long-running use cases lives once, unexported: the state transitions, cancel handling, executor rejection and the per-job state store (a port in `infra.port` with an in-memory adapter). Plan execution reuses that machinery later behind its own typed ports. The scan keeps its DFS-018 model unchanged; moving it onto the shared machinery is a later refactor, once execution has shown the machinery fits two use cases.
2. **Six statuses in a new `JobStatus` enum (`domain.model`), with DFS-018's cancel rules.** QUEUED, RUNNING, CANCELLING, COMPLETED, CANCELLED, FAILED; owned by the shared machinery, used by verify now and execution later (the scan keeps `ScanStatus` until the refactor merges them). `StartPlanVerify` records the job QUEUED before submitting it; it becomes RUNNING when the executor starts the task. Cancel on a QUEUED job makes it CANCELLED at once and the task exits without reading anything. Cancel on a RUNNING job makes it CANCELLING; the verify's `cancelled` supplier reads that status, so the verify stops within one chunk (DFS-034 decisions 4 and 6), and the job becomes CANCELLED only when `verify` has returned. COMPLETED, CANCELLED and FAILED are terminal; cancel on a terminal or CANCELLING job changes nothing and returns the current snapshot. Every transition is one atomic update of the job's record, so a completion and a cancel that race are settled by whichever update lands first, and both orders are tested.
3. **Progress pushed and pulled; a result for COMPLETED and CANCELLED.** `StartPlanVerify` has two overloads, with and without a `VerifyListener` (DFS-034's `BytesCompared` and `EntryChecked`, forwarded from the executor's thread), as `StartScan` does (DFS-018 decision 11; null is a caller bug). `GetPlanVerifyStatus` returns `PlanVerifySnapshot(JobStatus status, String planId, int entriesDone, int entriesTotal, long bytesDone, long bytesTotal, String failure)`, kept current from the same events (`failure` empty unless FAILED). `GetPlanVerifyResult` returns DFS-034's `VerifyReport`: the full report for a COMPLETED job, the partial one (`cancelled` true, the checks finished before the cancel) for a CANCELLED job, and `Failure(PLAN_VERIFY_NOT_FINISHED, ...)`, a new `ErrorCode` whose message names the status, for QUEUED, RUNNING, CANCELLING and FAILED. A job cancelled while QUEUED has a report with no checks and `cancelled` true. Live per-entry marks come from the listener, so the snapshot carries counts only.
4. **Job state behind one generic port, kept until the process exits.** One immutable record per job (its snapshot and, once finished, its report), changed only by an atomic `update(id, change)`, as `ScanRepository` does (DFS-018 decision 8). The port, in `infra.port`, is generic over the record type so execution stores its own records through it; the in-memory adapter, in `infra.adapter`, uses a `ConcurrentHashMap` and `compute`. The cancel request is the status itself, not a separate flag. Nothing removes or evicts a job, as with scans (DFS-018 decision 9): a report vanishing while the user reads it would surface as an unknown job right before a deletion step. Removal or eviction for scans and jobs together is a later ticket.
5. **Failure paths mirror the scan's.** An ID never issued gives `Failure(UNKNOWN_PLAN_VERIFY, ...)` (a new `ErrorCode`, the ID in the message) from cancel, status and result. `StartPlanVerify` checks on the caller's thread before recording anything: a `null` plan or listener throws `NullPointerException`; an empty plan is accepted and its job completes with a report with no checks (DFS-034 decision 2). If the executor rejects the task, the job already recorded QUEUED becomes FAILED with the rejection message and `StartPlanVerify` returns `Failure(EXECUTOR_REJECTED, ...)` (the existing code) instead of an ID (DFS-018 decision 11). The verify reports per-entry I/O problems as outcomes, so a `RuntimeException` from it is a bug: the job becomes FAILED with `failure` set to the exception's class name and message. An `Error` propagates, after a `finally` block has marked the job FAILED, so no job stays RUNNING or CANCELLING (DFS-018 decision 7). A caller's listener that throws is detached at its first exception and the verify goes on; the report's `listenerFailure` records it (DFS-034 decision 5).
6. **Exposure on its own executor.** `ScannerFactories.planVerifyUseCases(Executor executor)` returns `PlanVerifyUseCases(StartPlanVerify start, CancelPlanVerify cancel, GetPlanVerifyStatus status, GetPlanVerifyResult result)`, a record in `domain.factory` like `ScanUseCases`, built on the NIO adapters and a fresh in-memory job store per call. `ScannerAutoConfiguration` gains a `planVerifyExecutor` bean (a virtual thread per task, `destroyMethod = "shutdownNow"`, `@ConditionalOnMissingBean(name = "planVerifyExecutor")`), a `PlanVerifyUseCases` bean that takes it by `@Qualifier("planVerifyExecutor")`, and one bean per port, each `@ConditionalOnMissingBean`, so an app sizes or replaces verify work without touching scans. The comparator's chunk-boundary cancel case (DFS-034 "Known limits", DFS-034.L-2) is out of scope: it drops at most one fully compared entry from a cancelled job's partial report.
7. **Deterministic tests for the logic, one real-thread test, no `Thread.sleep`** (DFS-018 decision 10). `application` is tested against fakes: a manual executor that queues tasks until the test runs them (pins QUEUED), a fake `ContentComparator` that blocks on a latch partway through a file (pins RUNNING and CANCELLING), and fakes of the job store and attributes reader where useful. The completion-versus-cancel race is tested by applying both transitions in each order, not by racing threads. One end-to-end test wires a real virtual-thread executor, the in-memory job store and the NIO adapters on files in a `@TempDir`, and waits on the job through a latch released by the listener, never by sleeping. Use-case checklist: items 1 (decision 5), 2 (decisions 3 and 5), 3 and 4 (decision 2), 5 (decision 5), 6 and 12 (decision 4), 7 and 10 (decision 3), 8 and 9 (decision 5), 11 (decisions 5 and 6), 13 (this decision), 14 (decisions 1, 3 and 6: new types in already exported packages, so `module-info.java` is unchanged).
8. **Gates `[design, done]`.** The shared job machinery is the foundation plan execution, the deleting step, will reuse; its shape and concurrency are reviewed before tests and code (CLAUDE.md safety invariants).

## Acceptance criteria

The full ID of each criterion is `DFS-042.AC-<n>`.

- **AC-1** `StartPlanVerify` records the job QUEUED, returns its `PlanVerifyId` before the executor runs it, and the job becomes RUNNING when the task starts and COMPLETED when the verify returns.
- **AC-2** A COMPLETED job's `GetPlanVerifyResult` returns the same `VerifyReport` the synchronous `VerifyPlanContent.verify` gives for that plan, and its snapshot shows all entries and bytes done.
- **AC-3** `GetPlanVerifyStatus` returns a `PlanVerifySnapshot` whose entry and byte counts follow the verify's events, and the listener overload receives every `BytesCompared` and `EntryChecked` event.
- **AC-4** Cancel on a QUEUED job makes it CANCELLED at once; the task, when run, reads no file, and the result is a report with no checks and `cancelled` true.
- **AC-5** Cancel on a RUNNING job makes it CANCELLING, the verify stops within one chunk, and the job becomes CANCELLED with a partial report holding the checks finished before the cancel.
- **AC-6** Cancel on a CANCELLING, COMPLETED, CANCELLED or FAILED job changes nothing and returns the current snapshot; a completion and a cancel that race end in one terminal status, whichever is recorded first.
- **AC-7** `GetPlanVerifyResult` on a QUEUED, RUNNING, CANCELLING or FAILED job returns `Failure(PLAN_VERIFY_NOT_FINISHED)` with a message naming the status.
- **AC-8** Cancel, status and result for an ID never issued return `Failure(UNKNOWN_PLAN_VERIFY)` with the ID in the message.
- **AC-9** When the executor rejects the task, `StartPlanVerify` returns `Failure(EXECUTOR_REJECTED)` and the recorded job is FAILED with the rejection message.
- **AC-10** A `RuntimeException` from the verify makes the job FAILED with `failure` set to the exception's class name and message; a listener that throws is detached and the job still completes, with `listenerFailure` set.
- **AC-11** A `null` plan or listener throws `NullPointerException` before any job is recorded, and an empty plan completes with a report with no checks.
- **AC-12** Two jobs started on one `PlanVerifyUseCases` keep separate status, counts and reports.
- **AC-13** `ScannerFactories.planVerifyUseCases(executor)` returns working ports; `ScannerAutoConfiguration` provides a `planVerifyExecutor`, a `PlanVerifyUseCases` and the four port beans; an app's own `planVerifyExecutor` or `PlanVerifyUseCases` replaces the default.
- **AC-14** A verify run on a real virtual-thread executor over real files reaches COMPLETED with the expected outcomes and leaves every file's name, size and modified time unchanged.

## Scenarios

```gherkin
Feature: Asynchronous plan checks

  Background:
    Given a plan with two operations, in this order:
      | digest | candidate (first link) | keeper  | size    |
      | d1     | B/x.jpg                | A/x.jpg | 5       |
      | d2     | B/y.bin                | A/y.bin | 3145728 |
    And each candidate has the same bytes as its keeper
    And a manual executor that runs a queued task only when the test says so

  @AC-1 @AC-2
  Scenario: A job is queued, runs and completes with the verify's report
    When StartPlanVerify is called with the plan
    Then it returns Success with a PlanVerifyId
    And GetPlanVerifyStatus reports QUEUED with entriesTotal 2 and bytesTotal 3145733
    When the executor runs the queued task to the end
    Then GetPlanVerifyStatus reports COMPLETED with entriesDone 2 and bytesDone 3145733
    And GetPlanVerifyResult returns the report VerifyPlanContent.verify gives for the plan: two IDENTICAL checks, cancelled false

  @AC-1 @AC-3
  Scenario: Status and listener follow the verify while it runs
    Given a comparator that blocks after the first 1048576 bytes of B/y.bin
    And StartPlanVerify was called with the plan and a recording listener
    When the executor starts the task and the comparator blocks
    Then GetPlanVerifyStatus reports RUNNING with entriesDone 1 and bytesDone 1048581
    And the listener has received EntryChecked for x.jpg with done 1 of 2
    When the comparator is released and the task ends
    Then the listener has received BytesCompared events ending at 3145733, and EntryChecked with done 2 of 2

  @AC-4
  Scenario: Cancelling a queued job
    Given StartPlanVerify was called and the task has not run
    When CancelPlanVerify is called with its ID
    Then it returns a snapshot with status CANCELLED
    When the executor runs the task
    Then the comparator and the attributes reader were never called
    And GetPlanVerifyResult returns a report with no checks and cancelled true

  @AC-5
  Scenario: Cancelling a running job stops within one chunk
    Given a comparator that blocks after the first 1048576 bytes of B/y.bin
    And the job is RUNNING with the comparator blocked
    When CancelPlanVerify is called with its ID
    Then it returns a snapshot with status CANCELLING
    When the comparator is released
    Then it reads no further chunk of B/y.bin
    And GetPlanVerifyStatus reports CANCELLED
    And GetPlanVerifyResult returns cancelled true with only the IDENTICAL check for x.jpg

  @AC-6
  Scenario Outline: Cancel on a job that is cancelling or finished changes nothing
    Given a job whose status is <status>
    When CancelPlanVerify is called with its ID
    Then it returns Success with a snapshot whose status is still <status>

    Examples:
      | status     |
      | CANCELLING |
      | COMPLETED  |
      | CANCELLED  |
      | FAILED     |

  @AC-6
  Scenario: A completion and a cancel that race end in one terminal status
    Given a RUNNING job whose verify has returned a complete report
    When the completion is recorded before the cancel
    Then the job is COMPLETED and the cancel returns the COMPLETED snapshot
    And for a second such job, when the cancel is recorded before the completion
    Then that job is CANCELLED and its result has cancelled true

  @AC-7
  Scenario Outline: The result is refused until the job has finished
    Given a job whose status is <status>
    When GetPlanVerifyResult is called with its ID
    Then it returns Failure with code PLAN_VERIFY_NOT_FINISHED and a message containing "<status>"

    Examples:
      | status     |
      | QUEUED     |
      | RUNNING    |
      | CANCELLING |
      | FAILED     |

  @AC-8
  Scenario: An ID that was never issued
    Given a PlanVerifyId that no StartPlanVerify call returned
    When CancelPlanVerify, GetPlanVerifyStatus and GetPlanVerifyResult are each called with it
    Then each returns Failure with code UNKNOWN_PLAN_VERIFY and a message containing the ID

  @AC-9
  Scenario: The executor rejects the task
    Given an executor that throws RejectedExecutionException("pool closed")
    When StartPlanVerify is called with the plan
    Then it returns Failure with code EXECUTOR_REJECTED
    And the job that was recorded is FAILED with a failure containing "pool closed"

  @AC-10
  Scenario: A bug in the verify fails the job
    Given a comparator that throws IllegalStateException("boom") on B/y.bin
    When the job runs
    Then GetPlanVerifyStatus reports FAILED with failure "java.lang.IllegalStateException: boom"
    And GetPlanVerifyResult returns Failure with code PLAN_VERIFY_NOT_FINISHED

  @AC-10
  Scenario: A throwing listener is detached and the job completes
    Given a listener that throws IllegalStateException("listener down") on its first event
    When the job runs to the end
    Then the listener was called once
    And GetPlanVerifyStatus reports COMPLETED
    And GetPlanVerifyResult has two IDENTICAL checks and listenerFailure "listener down"

  @AC-11
  Scenario: Null input and an empty plan
    When StartPlanVerify is called with a null plan, and separately with a null listener
    Then each throws NullPointerException and no job is recorded
    When StartPlanVerify is called with a plan that has no operations and the task runs
    Then the job is COMPLETED and its result has no checks and cancelled false

  @AC-12
  Scenario: Two jobs keep separate state
    Given job 1 started with the plan, and job 2 started with a plan holding only the x.jpg operation
    When job 2 is cancelled while QUEUED and job 1 runs to the end
    Then job 1 is COMPLETED with two IDENTICAL checks
    And job 2 is CANCELLED with no checks

  @AC-13
  Scenario: Factory and Spring wiring
    Given ScannerFactories.planVerifyUseCases(executor) with a manual executor
    When its start port is called with a plan and the task runs
    Then its result port returns the verify's report
    And an application context with ScannerAutoConfiguration has exactly one bean each of PlanVerifyUseCases, StartPlanVerify, CancelPlanVerify, GetPlanVerifyStatus and GetPlanVerifyResult, and a bean named planVerifyExecutor
    And a verify started through the StartPlanVerify bean completes
    And a context whose app declares its own planVerifyExecutor bean runs the job on that executor
    And a context whose app declares its own PlanVerifyUseCases bean gets no second one

  @AC-14
  Scenario: End to end on real files
    Given the two operations' files written in a temporary folder, with the same bytes per pair
    And a real virtual-thread executor, the in-memory job store and the NIO adapters
    When StartPlanVerify is called with a listener that releases a latch on the last EntryChecked
    And the test waits on that latch
    Then GetPlanVerifyStatus reports COMPLETED
    And GetPlanVerifyResult has two IDENTICAL checks
    And every file's name, size and modified time are what they were before the job
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.usecases.command`: `StartPlanVerify`, `CancelPlanVerify`
- `us.pochaev.dfs.scanner.domain.usecases.query`: `GetPlanVerifyStatus`, `GetPlanVerifyResult`
- `us.pochaev.dfs.scanner.domain.model`: `PlanVerifyId`, `JobStatus`, `PlanVerifySnapshot`, two new `ErrorCode` values
- `us.pochaev.dfs.scanner.application`: the shared job machinery, the plan verify service, their tests
- `us.pochaev.dfs.scanner.domain.factory`: `PlanVerifyUseCases`, `ScannerFactories.planVerifyUseCases`
- `us.pochaev.dfs.scanner.infra.port`: the generic job store port
- `us.pochaev.dfs.scanner.infra.adapter`: its in-memory adapter, the end-to-end test
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`

**Out of scope:**
- Plan execution itself (a later ticket runs it through this job model).
- The server endpoints and event stream (frontend lane).
- Moving the scan onto the shared job machinery and merging `ScanStatus` into `JobStatus` (a later refactor, decision 1).
- Removing or evicting finished jobs (decision 4; one later ticket for scans and jobs).
- The comparator's chunk-boundary cancel case (decision 6, DFS-034.L-2).

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `design.md` addresses every item of the `use-case` edge-case checklist or marks it not applicable with a one-line reason (decision 7 lists the expected dispositions)
- [x] The scan's ports, `ScanStatus` and `ScanRepository` are unchanged, and the existing scan tests pass untouched
- [x] No test this ticket adds or changes uses `Thread.sleep`, and no test reads or writes outside a `@TempDir`
- [x] `module-info.java` is unchanged and `ModuleExportsTest` stays green

## Open questions

None.
