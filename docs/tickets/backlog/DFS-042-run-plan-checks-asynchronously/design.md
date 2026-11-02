# DFS-042: Run plan checks asynchronously with status and cancel — design

## Summary

Wraps DFS-034's synchronous `VerifyPlanContent.verify` in a background job: `StartPlanVerify`/`CancelPlanVerify` (`domain.usecases.command`), `GetPlanVerifyStatus`/`GetPlanVerifyResult` (`domain.usecases.query`), `PlanVerifyId`, `JobStatus` (6 states), `PlanVerifySnapshot` and the codes `UNKNOWN_PLAN_VERIFY` and `PLAN_VERIFY_NOT_FINISHED` (`domain.model`, already exported, so `module-info.java` is unchanged). The shared machinery is the package-private `JobRunner<I, P, T>` in `application`, over a generic `Job<P, T>(status, progress, result, failure)` record held in the generic port `JobStore<I, R>` (`infra.port`), backed by `InMemoryJobStore` (`infra.adapter`). `PlanVerifyService` implements the four ports on top of it. `ScannerFactories.planVerifyUseCases(executor)` returns `PlanVerifyUseCases`, and `ScannerAutoConfiguration` adds `planVerifyExecutor` plus five beans. The factory and the beans are real code, not stubs.
State: QUEUED, then RUNNING, then COMPLETED, or CANCELLING then CANCELLED, or FAILED. Every transition is one `JobStore.update` that checks the status first.
- AC-1, AC-2, AC-3, AC-11, AC-12: `PlanVerifyService.start` (both overloads), `status` and `result`, through `JobRunner.submit` and `InMemoryJobStore`.
- AC-4, AC-5, AC-6: `PlanVerifyService.cancel` and `JobRunner.cancel`, plus the finish step inside the task (`VerifyOutcomes.cancelledBeforeStart`/`markCancelled`).
- AC-7, AC-8: `PlanVerifyService.result`/`status`/`cancel` (the job is reached through `start` first; AC-8 reaches the stub directly).
- AC-9: `JobRunner.submit` (rejection). AC-10: the task inside `submit`, plus the service's own listener detach.
- AC-13: the real factory and beans; each test calls `start` through the factory or bean first, so it fails red on the stub.
- AC-14: the same stubs with a real virtual-thread executor and NIO adapters.
**Look closely at:**
1. A completion recorded after a cancel ends CANCELLED, with the full report marked `cancelled` true. Whichever update lands first wins, as the ticket says; the scan behaves differently (there the completion wins).
2. The service detaches the caller's listener itself and copies `listenerFailure` into the report afterwards, so the snapshot keeps updating after the caller's listener throws.
3. The AC-14 scenario's latch alone races the final transition: the test must also wait on the task's `Future`.
Checklist: all 14 items addressed, none not applicable. Expected breakage: none. `ScannerAutoConfigurationTest` line 143 needs a one-line edit by the test-author, because a second `ExecutorService` bean now exists (see "For the test-author").

## Public API

- `StartPlanVerify` in `domain.usecases.command`: `Result<PlanVerifyId> start(DeletionPlan plan)`; `Result<PlanVerifyId> start(DeletionPlan plan, VerifyListener listener)`
- `CancelPlanVerify` in `domain.usecases.command`: `Result<PlanVerifySnapshot> cancel(PlanVerifyId id)`
- `GetPlanVerifyStatus` in `domain.usecases.query`: `Result<PlanVerifySnapshot> status(PlanVerifyId id)`
- `GetPlanVerifyResult` in `domain.usecases.query`: `Result<VerifyReport> result(PlanVerifyId id)` (DFS-034's `VerifyReport`)
- `PlanVerifyId` in `domain.model`: `record(UUID value)`, `static random()`
- `JobStatus` in `domain.model`: `QUEUED, RUNNING, CANCELLING, COMPLETED, CANCELLED, FAILED`
- `PlanVerifySnapshot` in `domain.model`: `record(JobStatus status, String planId, int entriesDone, int entriesTotal, long bytesDone, long bytesTotal, String failure)`
- `ErrorCode` in `domain.model`: adds `UNKNOWN_PLAN_VERIFY` and `PLAN_VERIFY_NOT_FINISHED`; the `EXECUTOR_REJECTED` javadoc now covers plan verify too
- `PlanVerifyUseCases` in `domain.factory`: `record(StartPlanVerify start, CancelPlanVerify cancel, GetPlanVerifyStatus status, GetPlanVerifyResult result)`
- `ScannerFactories.planVerifyUseCases(Executor executor)`: a fresh set per call, on `VerifyPlanContentService(NioFileAttributesReader, NioContentComparator)` and a new `InMemoryJobStore`

No first-slice type is replaced. `ScanStatus`, `ScanRepository` and the scan's ports are untouched.

Record checks the implementer adds to the compact constructors (they are left without checks at design):
- `PlanVerifyId`: `value` non-null.
- `PlanVerifySnapshot`: `status`, `planId` and `failure` non-null; `0 <= entriesDone <= entriesTotal`; `0 <= bytesDone <= bytesTotal`.
- `PlanVerifyUseCases`: all four components non-null.
- `Job`: `status`, `progress` and `failure` non-null.
- `PlanVerifyProgress`: `planId` non-null; the same ranges as the snapshot.

## Layer placement

- `domain.usecases.command` / `domain.usecases.query` (exported): `StartPlanVerify`, `CancelPlanVerify` / `GetPlanVerifyStatus`, `GetPlanVerifyResult`
- `domain.model` (exported): `PlanVerifyId`, `JobStatus`, `PlanVerifySnapshot`, two `ErrorCode` values
- `application`:
  - `PlanVerifyService` (public; implements all four ports; its fields are the `VerifyPlanContent` verifier and the `JobRunner`).
  - `JobRunner<I, P, T>` (package-private; nested `Work`, `Progress` and `Outcomes` interfaces).
  - `Job<P, T>` and `PlanVerifyProgress` (public records, because the factory's `new InMemoryJobStore<>()` infers `Job<PlanVerifyProgress, VerifyReport>`).
- `infra.port` / `infra.adapter`: `JobStore<I, R>` / `InMemoryJobStore<I, R>` (`ConcurrentHashMap`: `putIfAbsent`, `computeIfPresent`)
- `scanner-spring`: `planVerifyExecutor` (virtual thread per task, `destroyMethod = "shutdownNow"`, `@ConditionalOnMissingBean(name = "planVerifyExecutor")`), `planVerifyUseCases(@Qualifier("planVerifyExecutor") Executor)`, and `startPlanVerify` (an anonymous delegate), `cancelPlanVerify`, `getPlanVerifyStatus`, `getPlanVerifyResult` (method references). Each is `@ConditionalOnMissingBean`.

## Behavior (the contract the implementer follows)

**`start(plan[, listener])`** (stub `PlanVerifyService.start`):
1. `requireNonNull(plan)`, plus `listener` in the two-argument overload, before anything else. Nothing is recorded on a throw (AC-11).
2. Compute `entriesTotal = plan.operations().size()` and `bytesTotal = Σ ((Operation.Quarantine) op).size()`.
3. `id = PlanVerifyId.random()`. Call `jobs.submit(id, new PlanVerifyProgress(plan.id(), 0, entriesTotal, 0, bytesTotal), work)`.
4. Return what `submit` returns.

The service's `work` (it runs on the executor thread, inside `JobRunner`):
- Build the verify's listener as `event -> { progress.update(apply(event)); forward(event); }`:
  - `BytesCompared` sets `bytesDone`. `EntryChecked` sets `entriesDone = done`.
  - `forward` calls the caller's listener, if there is one and it is not detached. On its first `RuntimeException`, the service detaches it and keeps `text = message`, or `toString()` when the message is null or blank (DFS-034's format: `"listener down"`).
  - The combined listener never throws, so DFS-034's own detach never triggers and the snapshot keeps updating (AC-10).
- Call `verifier.verify(plan, listener, stop)` and take the `Success` value.
- If `text` is set, return `new VerifyReport(r.planId(), r.checks(), r.cancelled(), text)`. Otherwise return `r` unchanged, so AC-2's equality with the synchronous verify holds.

**`JobRunner.submit`** (stub):
1. `store.create(id, new Job<>(QUEUED, initial, null, ""))`.
2. Call `executor.execute(task)`:
   - On `RejectedExecutionException e`: update QUEUED to FAILED with `failure = e.getClass().getName() + ": " + e.getMessage()` (it contains `"pool closed"`). Return `Failure(EXECUTOR_REJECTED, "executor rejected the plan verify: " + failure)` (AC-9).
   - On any other `RuntimeException`: mark the job FAILED in the same way, then rethrow.
3. Otherwise return `Success(id)`.

The task (on the executor thread):
1. Pick-up: one update, QUEUED to RUNNING. If the job is not RUNNING afterwards (it was cancelled while QUEUED), return without calling `work` (AC-4: the reader and comparator are never called).
2. Run `T t = work.run(progress, stop)`:
   - `progress.update(change)` is one store update. It applies `change` to `progress` only while the status is RUNNING or CANCELLING.
   - `stop = () -> find(id).map(j -> j.status() != RUNNING).orElse(true)`.
3. Finish, in one update:
   - RUNNING: becomes COMPLETED with `result = t`.
   - CANCELLING: becomes CANCELLED with `result = outcomes.markCancelled(t)`.
   - Any other status: unchanged.
4. If the work throws `RuntimeException e`: one update, RUNNING or CANCELLING to FAILED, with `failure = e.getClass().getName() + ": " + e.getMessage()` (`"java.lang.IllegalStateException: boom"`) and `result` null (AC-10).
5. If it throws an `Error`: the same FAILED update in a `finally` or catch, then rethrow it.

**`JobRunner.cancel`** (stub), one update:
- QUEUED: becomes CANCELLED with `result = outcomes.cancelledBeforeStart(progress)`.
- RUNNING: becomes CANCELLING.
- Any other status: unchanged.

It returns the job after the update, or empty for an unknown ID.

**`JobRunner.find`** (stub): `store.find(id)`.

**`VerifyOutcomes`** (stubs):
- `cancelledBeforeStart(p)` returns `new VerifyReport(p.planId(), List.of(), true, "")`.
- `markCancelled(r)` returns `new VerifyReport(r.planId(), r.checks(), true, r.listenerFailure())`.

**`cancel(id)` / `status(id)`** (stubs): call `requireNonNull(id)`, then `jobs.cancel(id)` or `jobs.find(id)`.
- The result maps to `Success(snapshot)`, where `snapshot = new PlanVerifySnapshot(job.status(), p.planId(), p.entriesDone(), p.entriesTotal(), p.bytesDone(), p.bytesTotal(), job.failure())`.
- An unknown ID gives `Failure(UNKNOWN_PLAN_VERIFY, "unknown plan verify: " + id.value())` (AC-8).

**`result(id)`** (stub):
- Unknown ID: as above.
- COMPLETED or CANCELLED: `Success(job.result())`.
- Otherwise: `Failure(PLAN_VERIFY_NOT_FINISHED, "plan verify " + id.value() + " has status " + status + ", no result")` (AC-7).

Listener event order is DFS-034's, unchanged. For each operation: `BytesCompared` events (rising), then `EntryChecked(check, i, total)`. The store update for each event happens before the event is forwarded to the caller's listener.

### For the test-author

- **Fixtures.** Use the plan from the Background, built in code:
  - Two `Operation.Quarantine` (digests d1 and d2), with candidates `B/x.jpg` (5 bytes) and `B/y.bin` (3145728 bytes) and keepers `A/x.jpg` and `A/y.bin`.
  - `PlanTotals`/`Source` as `PreviewDeletionPlanServiceTest` and `VerifyPlanContentServiceTest` build them, and a 64-hex `id`.
- **Service under test.**
  - Build `new PlanVerifyService(new VerifyPlanContentService(fakeReader, fakeComparator), new InMemoryJobStore<>(), executor)` in package `application`.
  - The fake reader returns regular-file attributes with the plan sizes.
  - The fake comparator reports progress in 1 MiB chunks and returns `Identical`. Reuse the fakes' shape from `VerifyPlanContentServiceTest`.
- **Manual executor.** It queues `Runnable`s, and `runNext()` runs one. Use it for AC-1, AC-4, AC-6 to AC-9, AC-11 and AC-12.
  - The rejecting executor for AC-9 throws `new RejectedExecutionException("pool closed")`.
- **Blocking comparator (AC-3, AC-5).**
  - On `B/y.bin`, it reports 1048576 bytes, counts down a "blocked" latch and waits on a "release" latch.
  - Then it reads `cancelled` before the next chunk, as the NIO adapter does.
  - Run the task on a separate thread (`Thread.ofVirtual().start(manual::runNext)`), wait on "blocked", assert, then release and `join()` the thread with a timeout.
- **Race (AC-6).** Use the public API, with no thread race:
  - *Completion first:* run the job to the end, then `cancel`. It returns the COMPLETED snapshot.
  - *Cancel first:* use a comparator that calls `service.cancel(id)` (id kept in a holder after `start`) inside its compare of the last operation, after its last chunk, then returns `Identical`. The verify returns a complete report while the job is CANCELLING, and the job ends CANCELLED with the result's `cancelled == true`.
- **AC-6 FAILED job.** Use the AC-10 throwing comparator, or the AC-9 rejection.
- **AC-13.**
  - Call `start` through `ScannerFactories.planVerifyUseCases(manual)`, or through the `StartPlanVerify` bean, before any wiring assertion, so the test fails red on the stub.
  - Use an empty plan (no file access) for the bean tests.
  - For the app executor test, register `withBean("planVerifyExecutor", ExecutorService.class, ...)` with a counting executor, and assert the count is at least 1.
- **AC-14.**
  - Write the files into a `@TempDir` and use `ScannerFactories.planVerifyUseCases(task -> futures.add(threads.submit(task)))` over `Executors.newVirtualThreadPerTaskExecutor()`.
  - Wait on the listener's latch (bounded `await`), then on `future.get(10, SECONDS)` before asserting COMPLETED. The listener fires before the final status update, so the latch alone is racy.
  - Compare name, size and mtime before and after.
- **Existing-test edit (required).** In `ScannerAutoConfigurationTest.appExecutorReplacesDefault` (DFS-031.AC-2), line 143 asserts `assertEquals(1, context.getBeansOfType(ExecutorService.class).size())`. Now that `planVerifyExecutor` exists, replace it with `assertEquals(Set.of("scanExecutor", "planVerifyExecutor"), context.getBeansOfType(ExecutorService.class).keySet())`.
- **Optional.** An `InMemoryJobStoreTest` mirroring `InMemoryScanRepositoryTest` fails red on the adapter stubs.
- **Waiting.** No `Thread.sleep` anywhere.

## State and transitions

| From | Event | To |
|---|---|---|
| (none) | `start`, recorded | QUEUED |
| QUEUED | executor rejects | FAILED |
| QUEUED | cancel | CANCELLED (result: empty report, `cancelled` true) |
| QUEUED | task picked up | RUNNING |
| RUNNING | cancel | CANCELLING |
| RUNNING | verify returns | COMPLETED |
| CANCELLING | verify returns | CANCELLED (report marked `cancelled` true) |
| RUNNING, CANCELLING | verify throws | FAILED |

COMPLETED, CANCELLED and FAILED are terminal.
- **cancel:**
  - QUEUED: becomes CANCELLED.
  - RUNNING: becomes CANCELLING.
  - CANCELLING or terminal: unchanged snapshot.
- **status:** returns the snapshot in every state.
- **result:**
  - COMPLETED, CANCELLED: the report.
  - QUEUED, RUNNING, CANCELLING, FAILED: `PLAN_VERIFY_NOT_FINISHED`.

## Concurrency and atomicity

- **Threads.** The caller's thread does the null checks, the totals, `create(QUEUED)` and `execute`. The executor's thread does the pick-up, the verify, the progress updates, the caller's listener and the finish.
- **State.** One `Job` per ID in the `JobStore`. Every transition and progress change is one `JobStore.update` (`computeIfPresent`) that checks the current status. The cancel request is the CANCELLING status itself, with no separate flag.
- **Races:**
  - *Cancel versus pick-up:* whichever update lands first wins. If the cancel lands first, the task sees CANCELLED and exits.
  - *Completion versus cancel:* whichever lands first wins. Completion first gives COMPLETED, and the cancel then returns it. Cancel first gives CANCELLING, then CANCELLED with the report marked cancelled.
  - *Progress after a terminal status:* ignored.

## Checklist disposition

1. Unknown or stale IDs: `Failure(UNKNOWN_PLAN_VERIFY, "unknown plan verify: <uuid>")` from cancel, status and result alike (decision 5).
2. Results as values, bugs as exceptions: rejection and not-finished are `Failure`s with two new codes plus the existing `EXECUTOR_REJECTED`. A null argument is an NPE. The listener is optional through an overload. A failed job is a `Success` snapshot that says FAILED.
3. Status model: the six `JobStatus` values. See the transition table.
4. Races and who wins: one atomic update per transition. The first recorded wins, in both orders (see Concurrency).
5. Validation before side effects: only the null checks, which leave no record. An empty plan is valid. No other validation exists to duplicate.
6. State placement: `Job` behind `JobStore`. The service's fields are the verifier and the `JobRunner` (store and executor). There is no cancel flag.
7. Progress and observation: both push (the listener) and pull (the snapshot). Both are fed by the same events, with the store updated first. The QUEUED snapshot has zero done-counts, the totals set, and failure "".
8. Caller-supplied callbacks: detached at the first `RuntimeException`, recorded in `VerifyReport.listenerFailure`. The detach is done by the service so the snapshot keeps updating.
9. Failures off the caller's thread: a `RuntimeException` gives FAILED with `class: message`. An `Error` gives FAILED and is rethrown. It is reported through a `Success` status, never printed.
10. Query results: the whole `VerifyReport`, in plan order, only for COMPLETED and CANCELLED (partial: the checks finished before the cancel). Otherwise `PLAN_VERIFY_NOT_FINISHED`.
11. Executor contract: the job is recorded before `execute`. A rejection turns the job FAILED and returns `Failure(EXECUTOR_REJECTED)`. Callbacks run on the executor's thread (`StartPlanVerify` javadoc).
12. Retention: kept until the process exits. Eviction is out of scope (decision 4, a later ticket for scans and jobs).
13. Testability: a manual executor, latch-blocking comparator fakes, the race through public calls in both orders, and one real-thread test bounded by `Future.get`. There is no seam beyond the injected `VerifyPlanContent`, which is already an interface.
14. Exported surface: the new types sit in the already exported `domain.model`, `domain.usecases.*` and `domain.factory`. `module-info.java` is unchanged. No first-slice type is replaced.

## Configuration

- None. The executor is replaceable by the bean name `planVerifyExecutor`.

## Expected breakage

None.

## Open design notes (as of the design phase)

- Completion after a cancel ends CANCELLED with the full report marked `cancelled` true. This follows the ticket's "whichever is recorded first" and its AC-6 scenario. Unlike the scan (where completion wins), the CANCELLING state is final in intent.
- The listener detach moved from DFS-034's `Emitter` into `PlanVerifyService`. Otherwise a throwing caller's listener would also freeze the snapshot. `listenerFailure` is copied into the report afterwards, in the same text format.
- The AC-14 scenario's latch alone races the final transition. The test also waits on the task's `Future` (see "For the test-author").

## Stubs (as of the design phase)

- Files stubbed:
  - `PlanVerifyService`: `start` x2, `cancel`, `status`, `result`, `VerifyOutcomes.cancelledBeforeStart`, `markCancelled`.
  - `JobRunner`: `submit`, `cancel`, `find`.
  - `InMemoryJobStore`: `create`, `find`, `update`.
- Written for real:
  - Records (left without checks): `Job`, `PlanVerifyProgress`, `PlanVerifyId`, `PlanVerifySnapshot`, `PlanVerifyUseCases`.
  - Enums and interfaces: `JobStatus`, `ErrorCode`, the four ports, `JobStore`.
  - Wiring: `ScannerFactories.planVerifyUseCases` and the `ScannerAutoConfiguration` beans.
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-042")` per D2.
- Commit: filled in after the design commit.
