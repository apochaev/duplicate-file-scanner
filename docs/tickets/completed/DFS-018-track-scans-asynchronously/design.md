# DFS-018: Track scans asynchronously — design

## Summary

One service, `ScanService` (`application`), implements four use cases: `StartScan`, `CancelScan` (commands) and `GetScanStatus`, `GetScanResult` (queries). Each returns a sealed `Result<T>` (`Success`/`Failure(ErrorCode, message)`). Per-scan state is one immutable `ScanRecord` (`infra.port`) behind `ScanRepository` (`infra.port`), backed by `InMemoryScanRepository` (`infra.adapter`, `ConcurrentHashMap.computeIfPresent`). The service runs DFS-005's pipeline through the package-private `ScanRunner` seam, which `ScanPipeline` now implements. State model: QUEUED → RUNNING → (CANCELLING →) COMPLETED | CANCELLED | FAILED, every transition one atomic `update` that checks the current status. `module-info.java` is unchanged.

- AC-1: `start` validates, records `QUEUED`, submits; `Success(ScanId)`.
- AC-2: task moves QUEUED→RUNNING, runs `ScanRunner`, stores `ScanResult` with COMPLETED; `GetScanResult.result` returns it.
- AC-3: `NO_LOCATIONS` / `LOCATION_NOT_FOUND` / `NOT_A_DIRECTORY` from `LocationResolver` before `create`/`execute`.
- AC-4: `RejectedExecutionException` → record FAILED (`<class>: <message>`), `Failure(EXECUTOR_REJECTED)`.
- AC-5: `ScanSnapshot(status, phase, filesWalked, groupsDone, groupsTotal, failureMessage, listenerFailure)`, updated per event.
- AC-6: two `start` overloads; listener called on the task's thread after the snapshot update.
- AC-7, AC-8, AC-9, AC-10: `cancel` transition table below; stop signal = "status is not RUNNING"; completion wins.
- AC-11, AC-12: `Failure(UNKNOWN_SCAN)` on missing record; `Failure(SCAN_NOT_COMPLETED)` naming the status.
- AC-13, AC-14: catch `IOException`/`RuntimeException` → FAILED; a `finally` marks FAILED on an `Error`, which propagates.
- AC-15: task-local detach flag; `listenerFailure` set once.
- AC-16: `Objects.requireNonNull` first, before any validation or side effect.
- AC-17: public constructor `ScanService(LocationResolver, ScanPipeline, ScanRepository, Executor)` with real adapters.
- AC-18: `InMemoryScanRepository.update` on `computeIfPresent`.

Look closely at: the added `ErrorCode.LOCATION_UNREADABLE` (not in the ticket's list; see Open design notes), the stop signal reading "not RUNNING", and a `CancellationException` without a cancel request ending CANCELLED. No checklist item is marked not applicable.

## Public API

- `StartScan` in `domain.usecases.command` (new): `Result<ScanId> start(List<Path> locations, ScanConfig config)` and `Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener)`.
- `CancelScan` in `domain.usecases.command` (replaces first slice `void cancel(ScanId)`): `Result<ScanSnapshot> cancel(ScanId scanId)`.
- `GetScanStatus` in `domain.usecases.query` (replaces first slice `ScanStatus status(ScanId)`): `Result<ScanSnapshot> status(ScanId scanId)`.
- `GetScanResult` in `domain.usecases.query` (new; the sketch's `ListDuplicateSets`): `Result<ScanResult> result(ScanId scanId)`.
- `Result<T>` in `domain.model` (new): sealed; `record Success<T>(T value)`, `record Failure<T>(ErrorCode code, String message)`.
- `ErrorCode` in `domain.model` (new): `UNKNOWN_SCAN, NO_LOCATIONS, LOCATION_NOT_FOUND, NOT_A_DIRECTORY, LOCATION_UNREADABLE, EXECUTOR_REJECTED, SCAN_NOT_COMPLETED`.
- `ScanStatus` in `domain.model` (replaced in place): `QUEUED, RUNNING, CANCELLING, COMPLETED, CANCELLED, FAILED`.
- `ScanSnapshot` in `domain.model` (new): `(ScanStatus status, ScanPhase phase, long filesWalked, int groupsDone, int groupsTotal, String failureMessage, String listenerFailure)`. `phase` is null until the first `PhaseStarted`; `PhaseStarted` resets `groupsDone`/`groupsTotal` to 0; `GroupDone` sets them; `FileWalked` sets `filesWalked`; `Skipped` changes nothing.
- Unchanged and reused: `ScanId`, `ScanConfig`, `ScanListener`, `ScanEvent`, `ScanPhase`, `ScanResult`.

Failure messages (tests check only the parts the ticket names): `UNKNOWN_SCAN` "unknown scan: <uuid>"; `NO_LOCATIONS` "no locations to scan"; `LOCATION_NOT_FOUND` "location not found: <location>"; `NOT_A_DIRECTORY` "not a directory: <location>"; `LOCATION_UNREADABLE` "location unreadable: <class>: <message>"; `EXECUTOR_REJECTED` "executor rejected the scan: <class>: <message>"; `SCAN_NOT_COMPLETED` "scan <uuid> has status <STATUS>, no result" (avoid the word COMPLETED in it).

Records stay plain in the stubs. The implementer adds `Objects.requireNonNull` for `Success.value`, `Failure.code`/`message`, `ScanSnapshot.status`, `ScanRecord.id`/`snapshot`; no other record validation.

## Layer placement

- `domain.usecases.command` / `domain.usecases.query` (exported): `StartScan`, `CancelScan` / `GetScanStatus`, `GetScanResult`
- `domain.model` (exported): `Result`, `ErrorCode`, `ScanSnapshot`, `ScanStatus` (changed)
- `application`: `ScanService` (public, final, implements all four ports); `ScanRunner` (package-private `@FunctionalInterface`); `ScanPipeline` gains `implements ScanRunner` and a `run` method that delegates to `scan`, nothing else
- `infra.port` / `infra.adapter`: `ScanRecord(ScanId id, ScanSnapshot snapshot, ScanResult result)` and `ScanRepository` (`create`, `find`, `update`) / `InMemoryScanRepository`

`ScanRecord` sits in `infra.port`, not `domain.model`: it is internal state, never seen by callers (the existing `WalkListener` already makes `infra.port` → `domain.model` a used dependency). `ScanService` has two constructors: public `(LocationResolver, ScanPipeline, ScanRepository, Executor)` for production and DFS-019's factories, and package-private `(LocationResolver, ScanRunner, ScanRepository, Executor)` for tests. Overload resolution picks the `ScanPipeline` one for a `ScanPipeline` argument; a lambda can only match `ScanRunner`.

## State and transitions

| From | Event | To |
|---|---|---|
| (none) | `start` passes validation | `QUEUED` (record created, then task submitted) |
| `QUEUED` | executor throws on `execute` | `FAILED` (`failureMessage` = `<class>: <message>`) |
| `QUEUED` | `cancel` | `CANCELLED` |
| `QUEUED` | task picked up | `RUNNING` |
| `CANCELLED` | task picked up | `CANCELLED` (task exits, runner never called) |
| `RUNNING` | `cancel` | `CANCELLING` |
| `RUNNING`, `CANCELLING` | pipeline event | same status, progress fields updated |
| `RUNNING`, `CANCELLING` | runner returns | `COMPLETED` (result stored) |
| `RUNNING`, `CANCELLING` | runner throws `CancellationException` | `CANCELLED` |
| `RUNNING`, `CANCELLING` | runner throws `IOException` / other `RuntimeException` | `FAILED` (`<class>: <message>`) |
| `RUNNING`, `CANCELLING` | runner throws an `Error` | `FAILED` in `finally`; the `Error` propagates |

Terminal: `COMPLETED`, `CANCELLED`, `FAILED`. No transition leaves them; every update on a terminal record returns it unchanged.

- `start`: no state yet. Creates QUEUED, or returns a `Failure` and creates nothing (validation).
- `cancel`: QUEUED → CANCELLED; RUNNING → CANCELLING; CANCELLING, COMPLETED, CANCELLED, FAILED → unchanged. Always returns `Success(snapshot after the update)`; `UNKNOWN_SCAN` if no record.
- `status`: any state → `Success(snapshot)`; `UNKNOWN_SCAN` if no record.
- `result`: COMPLETED → `Success(result)`; any other state → `Failure(SCAN_NOT_COMPLETED)`; `UNKNOWN_SCAN` if no record.

## Concurrency and atomicity

- Which thread runs what: the caller's thread does the null checks, validation (`LocationResolver.realPath` per location), `create` and `executor.execute`. The executor's thread runs the task: pickup transition, `ScanRunner.run`, every snapshot update, every call to the caller's listener, and the terminal transition. If the executor runs the task inline (caller-runs), "the executor's thread" is the caller's; the contract says "the thread that runs the task".
- Where per-request state lives: one `ScanRecord` per scan in `ScanRepository`. Every read-decide-write goes through `ScanRepository.update(id, op)`, where `op` is a pure function of the current record. The service reads the outcome from `update`'s return value, never from a following `find`. The service has exactly four fields: `resolver`, `runner`, `repository`, `executor`.
- Detached-listener flag: task-local (a local in the task's closure), not a service field. It is mirrored in the snapshot's `listenerFailure`.
- Stop signal passed to the runner: `() -> repository.find(id).map(r -> r.snapshot().status() != RUNNING).orElse(true)`. It reads true from the moment `cancel` commits CANCELLING and stays true (AC-8: "from then on"). No separate cancel flag.
- Races:
  - cancel vs pickup: both are one `update` on the record. Cancel first gives CANCELLED and the pickup sees it and exits. Pickup first gives RUNNING, and the cancel then gives CANCELLING.
  - cancel vs completion: completion first gives COMPLETED, and the cancel is a no-op returning COMPLETED. Cancel first gives CANCELLING, and the completion update (RUNNING or CANCELLING → COMPLETED) still gives COMPLETED. Completion wins both ways (AC-10).
  - concurrent progress updates and cancel: the progress op copies the record's current status, so it never overwrites CANCELLING back to RUNNING.

Per-event cost: one `computeIfPresent` per event, which means one per walked file. That is acceptable for the in-memory adapter and is not throttled in this ticket.

## Checklist disposition

1. Unknown or stale IDs: every operation that takes a `ScanId` returns `Failure(UNKNOWN_SCAN)` when `find`/`update` is empty. A previous process's IDs are unknown, because the repository is in-memory.
2. Results as values, bugs as exceptions: the `Failure` codes are listed above. A null argument (and a null element of `locations`) throws `NullPointerException` before anything else. The optional listener is a second overload, not a nullable argument. A runtime failure of a scan is `Success(snapshot with FAILED)`.
3. Status model: six statuses and the table above. Each command's behaviour in each state, including terminal ones, is listed under State and transitions.
4. Races and who wins: settled inside `ScanRepository.update`. Completion wins over cancel. A cancel during pickup gives either CANCELLED (and the task exits) or CANCELLING, never a lost RUNNING.
5. Validation before side effects: null checks, then empty list (`NO_LOCATIONS`), then each location through `LocationResolver.realPath` in order. The first failure maps `NoSuchFileException` to `LOCATION_NOT_FOUND`, `NotDirectoryException` to `NOT_A_DIRECTORY`, and any other `IOException` to `LOCATION_UNREADABLE`. Only then does `create` + `execute` happen. The pipeline resolves again (stated duplication). A location that vanishes in between fails the scan through the pipeline (FAILED with the `IOException`'s message). The unresolved `locations` (copied with `List.copyOf`) are passed to the runner.
6. State placement: one immutable `ScanRecord` per scan behind `ScanRepository`, changed only by `update`. The cancel request is the status itself (CANCELLING). Service fields are ports, seam and executor only.
7. Progress and observation: both push and pull. The task wraps the caller's listener. For each event it first applies the event to the snapshot (one `update`), then forwards it. The no-listener overload forwards nowhere. QUEUED snapshot: `(QUEUED, null, 0, 0, 0, null, null)`.
8. Caller-supplied callbacks: a `RuntimeException` from the caller's listener detaches it for the rest of the scan. `listenerFailure` is set to `<class>: <message>` once, by an `update`, and the scan continues. An `Error` from the listener is not caught: it goes through the pipeline into item 9's handling.
9. Failures off the caller's thread: `CancellationException` gives CANCELLED. `IOException`/other `RuntimeException` gives FAILED with `failureMessage = e.getClass().getName() + ": " + e.getMessage()`. On an `Error`, a `finally` applies "RUNNING or CANCELLING → FAILED" (message `<class>: <message>` of the `Error`, captured by a `catch (Error e) { ...; throw e; }`) and the `Error` propagates out of the task's `run()`. A scan failure is reported by `status` as `Success` with FAILED. A `CancellationException` thrown without a cancel request (status still RUNNING) also gives CANCELLED.
10. Query results: `result` returns the whole `ScanResult`, unpaged, in pipeline order, only when COMPLETED. Otherwise it returns `Failure(SCAN_NOT_COMPLETED)` naming the status. There is never a partial result (DFS-005 decision 7).
11. Executor contract: a `java.util.concurrent.Executor` is supplied by the caller (Spring's `TaskExecutor` is one). The record is created before `execute`. `RejectedExecutionException` gives a FAILED record and `Failure(EXECUTOR_REJECTED)`. Any other `RuntimeException` from `execute` is a broken executor: the record goes FAILED and the exception is rethrown, so no record stays QUEUED forever. The listener runs on the thread that runs the task, as documented on `StartScan`.
12. Retention: records are kept until the process exits. `ScanRepository` has no remove. `ForgetScan`/eviction is out of scope (ticket decision 9, deferred to the long-running-server ticket).
13. Testability: `ManualExecutor` (test tree) pins QUEUED. A scripted `ScanRunner` fake blocks on a latch to pin RUNNING/CANCELLING and captures the stop signal. Races are tested in both orders. AC-17 is the only real-thread test. No `Thread.sleep`. The seam is `ScanRunner`, and `ScanPipeline`'s change (`implements ScanRunner`, a delegating `run`) is inside `touches` (`application`).
14. Exported surface: new `StartScan`, `GetScanResult`, `Result`, `ErrorCode`, `ScanSnapshot`, and changed `ScanStatus`, `CancelScan`, `GetScanStatus`, all in already exported packages. `ScanRecord`/`ScanRepository` are in non-exported `infra.port`. `module-info.java` is unchanged.

## Configuration

- None new. `ScanConfig` is passed through to the runner unchanged. The executor is a constructor argument.

## Open design notes

- **`ErrorCode.LOCATION_UNREADABLE` added.** The ticket's code list covers only `NoSuchFileException` and `NotDirectoryException` from `LocationResolver`, but the port may throw any other `IOException` (for example `AccessDeniedException`). `start` cannot throw a checked exception, and wrapping it as unchecked would make an expected outcome an exception (decision 4). No scenario covers it. The implementer maps it anyway; the human may prefer another name or reuse.
- **Stop signal reads "status is not RUNNING"**, not "== CANCELLING", so it stays true after cancel even if something else moves the record. This is the same as `== CANCELLING` in every reachable state while the runner runs.
- **`CancellationException` without a cancel request** ends CANCELLED rather than FAILED. Only a misbehaving runner can do this; the pipeline throws it only when the stop signal read true.
- **`ScanRunner.run` vs `ScanPipeline.scan`:** the ticket names the seam method `run`, so `ScanPipeline` gains a one-line `run` that delegates to `scan` (stubbed now). DFS-005's `scan` and its tests are untouched.
- **Test hints for the test-author:**
  - AC-3/AC-4 inspect the repository's records. `ScanRepository` has no listing method (none is needed in production; decision 9), so use a hand-written fake repository in the test tree that exposes what it holds (decision 10 names fakes of `ScanRepository`).
  - For AC-6's thread check, run the task on a thread the test creates.

## Stubs

- Files stubbed (new): `domain/model/Result.java`, `domain/model/ErrorCode.java`, `domain/model/ScanSnapshot.java`, `domain/usecases/command/StartScan.java`, `domain/usecases/query/GetScanResult.java`, `application/ScanRunner.java`, `application/ScanService.java`, `infra/port/ScanRecord.java`, `infra/port/ScanRepository.java`, `infra/adapter/InMemoryScanRepository.java`.
- Files changed: `domain/model/ScanStatus.java`, `domain/usecases/command/CancelScan.java`, `domain/usecases/query/GetScanStatus.java`, `application/ScanPipeline.java` (`implements ScanRunner` + stubbed `run`).
- Throwing stubs: `ScanService`'s two constructors and `start` (both overloads), `cancel`, `status`, `result`; `InMemoryScanRepository.create`, `find`, `update`; `ScanPipeline.run`. Each throws `new UnsupportedOperationException("not implemented: DFS-018")` per D2.
- Commit: filled in by the orchestrator after the design commit.
