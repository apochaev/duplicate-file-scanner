# DFS-055: Run plan executions one at a time — design

## Summary

`StartPlanExecution.start` gets three new steps after DFS-051's plan checks: take a lease on the manifest folder's OS lock, claim the one run slot in the job store atomically, then create the manifest and dispatch as before. The lock is released when the job is terminal, on every path, through a new `JobRunner.Outcomes.ended` hook.

- **AC-1** (no second run moves files): a second start never gets a job while the first is QUEUED, RUNNING or CANCELLING. `JobStore.createUnless` (stub in `InMemoryJobStore`) through `JobRunner.claim` (stub), called from `PlanExecutionService.launch` (stub).
- **AC-2** (refused with `EXECUTION_ALREADY_RUNNING` naming the ID; slot free again after COMPLETED, CANCELLED, FAILED, cancel while QUEUED, or rejection): `claim` returns the blocking ID. `ended` is called exactly once per job, on every terminal path, and releases the lease (`ExecutionOutcomes.ended` stub; `JobRunner.dispatch` and `abandon` stubs).
- **AC-3** (another process holds `execution.lock`): new port `ExecutionLock` (`infra.port`) with adapter `FileChannelExecutionLock` (`infra.adapter`, `acquire` stub). `HeldElsewhere` gives the message "another process is executing a plan". `ScannerFactories.planExecutionUseCases` wires the adapter for real.
- **AC-4** (order: plan checks, then lock, then claim; no trace on refusal; one winner; lock freed on cancel while QUEUED or rejection): the order is in `launch`. Every refusal releases the lease it took. Two starts in one set both get a lease, and `createUnless` picks the winner.
- **Placement:** `ErrorCode.EXECUTION_ALREADY_RUNNING` goes in `domain.model`. `ExecutionLock`, `JobStore.createUnless` and `ManifestWriter.manifestPath` (default method) go in `infra.port`. `FileChannelExecutionLock` and `InMemoryJobStore.createUnless` go in `infra.adapter`. `JobRunner`, `PlanExecutionService` and `PlanExecutionProgress` (new `lease` component) go in `application`. Nothing new is exported.
- **Look closely at these deviations:**
  1. **No `scanner-spring` bean for the lock.** `ExecutionLock` lives in `infra.port`. That package is not exported, and `scanner-spring`'s `ArchitectureTest` forbids depending on it. DFS-051's secondary ports are not beans either. An app overrides the lock by declaring its own `planExecutionUseCases`.
  2. **The job is now recorded before its manifest is created.** If the manifest file itself cannot be created after the claim (the folder and lock file were writable), the start still returns `MANIFEST_UNWRITABLE`. It leaves a FAILED record whose ID was never returned. An unwritable folder is refused at the lock step and still leaves no trace.
  3. **The lock adapter keeps a process-wide registry.** It shares the OS lock among one adapter instance's starts. Another instance in the same JVM is refused as "another process".
- **Checklist:** items 7, 8, 10 and 12 are not applicable (nothing new is observed, called back, queried or retained).
- **Expected breakage:** none.
- **The test-author must edit existing tests:**
  - `PlanExecutionServiceTest` and `PlanExecutionRealThreadTest`: the new constructor argument.
  - `CountingStore` in `PlanExecutionServiceTest`, and `RecordingStore` in `PlanVerifyServiceTest`: add `createUnless`.
  - Tests that list the manifest folder: ignore `execution.lock`.
  - Tests that end with an unfinished run: cancel it. On Windows, a locked file blocks `@TempDir` cleanup.

## Public API

Nothing new is exported. These are the internal signatures callers of `application` and `infra` depend on:

- `ErrorCode` in `domain.model`: new constant `EXECUTION_ALREADY_RUNNING`.
- `ExecutionLock` in `infra.port`: `Acquisition acquire(Path manifestFolder) throws IOException`. Nested types:
  - `sealed interface Acquisition` with `record Acquired(Lease lease)` and `record HeldElsewhere()`.
  - `interface Lease { void release(); }`: idempotent, never throws.
- `JobStore<I, R>` in `infra.port`: new abstract `Optional<I> createUnless(I id, R record, Predicate<? super R> blocking)`. It returns empty when the record was stored, else the ID of a stored record that matches.
- `ManifestWriter` in `infra.port`: new `default Path manifestPath(Path folder, PlanExecutionId runId)`. It returns `<folder>/<run ID>.jsonl` as an absolute path and does no disk I/O.
- `FileChannelExecutionLock` in `infra.adapter`: public no-arg constructor (real), `FILE_NAME = "execution.lock"`, and `acquire` (stub).
- `InMemoryJobStore.createUnless` in `infra.adapter` (stub).
- `JobRunner` in `application`, package-private:
  - `Optional<I> claim(I id, P initial, Predicate<? super Job<P,T>> blocks)`
  - `void abandon(I id, String failure)`
  - `Result<I> dispatch(I id, Work<P,T> work, Settled<P,T> settled)`
  - `Outcomes.default void ended(Job<P,T> job)`, a no-op by default (`PlanVerifyService` keeps the default).
- `PlanExecutionProgress` in `application`: new last component `ExecutionLock.Lease lease`. The implementer adds `requireNonNull(lease, "lease")` to the compact constructor.
- `PlanExecutionService` constructor: `(reader, comparator, deleter, manifests, ExecutionLock lock, store, executor, config)`. The new parameter is the fifth.

## Layer placement

- `domain.usecases.command` / `domain.usecases.query` (exported): unchanged.
- `domain.model` (exported): `ErrorCode.EXECUTION_ALREADY_RUNNING`.
- `application`:
  - `PlanExecutionService`: new `launch` (stub) and `ExecutionOutcomes.ended` (stub).
  - `JobRunner`: `claim`, `abandon` and `dispatch` (stubs) and `Outcomes.ended`.
  - `PlanExecutionProgress`: the `lease` component.
- `infra.port`: `ExecutionLock` (new), `JobStore.createUnless`, `ManifestWriter.manifestPath`.
- `infra.adapter`: `FileChannelExecutionLock` (new), `InMemoryJobStore.createUnless`.
- `domain.factory`: `ScannerFactories.planExecutionUseCases` passes `new FileChannelExecutionLock()`, one instance per built set.
- `scanner-spring`: no main-code change (see Open design notes 1). A test is added to `ScannerAutoConfigurationTest`.
- `CLAUDE.md`: one line, written by the implementer. Under "Safety invariants", after "Never delete the last copy", add: "One plan execution runs at a time, per process (a second start is refused with `EXECUTION_ALREADY_RUNNING`) and per manifest folder across processes (an OS lock on `<manifest folder>/execution.lock`, DFS-055); processes with different manifest folders are not coordinated."

## Behavior (the contract the implementer follows)

`start(scan, plan[, listener])`:

1. Null checks, unchanged.
2. `begin` runs DFS-051's plan checks unchanged, refusing with `PLAN_EDITED`, `PLAN_HAS_CONFLICTS`, `PLAN_NOT_FROM_SCAN` or `KEEPER_NOT_SEPARATE_COPY`. A refusal here touches no lock, no store and no disk. Then `begin` returns `launch(...)`.
3. `launch` acquires the lock with `lock.acquire(config.manifestFolder())`:
   - An `IOException` returns `Failure(MANIFEST_UNWRITABLE, "manifest folder " + folder + ": " + describe(e))`, the message DFS-051 already uses.
   - `HeldElsewhere` returns `Failure(EXECUTION_ALREADY_RUNNING, "another process is executing a plan")`. The message is exact.
   - `Acquired(lease)`: from here on, every path out of `launch` either releases `lease` or hands it to the job.
4. It builds the job's starting progress:
   - `runId = PlanExecutionId.random()`.
   - `manifest = manifests.manifestPath(folder, runId)`.
   - `initial = new PlanExecutionProgress(runId, manifest, plan, 0, 0, bytesTotal, 0, 0, lease)`.
5. It claims the slot with `jobs.claim(runId, initial, job -> job.status() is QUEUED, RUNNING or CANCELLING)`. A `RuntimeException` or `Error` from it means `lease.release()` and rethrow.
   - If a blocker ID comes back: `lease.release()`, then return `Failure(EXECUTION_ALREADY_RUNNING, "plan execution " + blocker.value() + " is not finished")`.
6. It creates the manifest with `manifests.create(folder, new ManifestLine.Run(runId, plan.id(), scan.roots(), Instant.now()))`:
   - On `IOException`: `msg = "manifest folder " + folder + ": " + describe(e)`, then `jobs.abandon(runId, msg)`, then return `Failure(MANIFEST_UNWRITABLE, msg)`. `abandon` turns QUEUED into FAILED and calls `ended`, which releases the lease.
   - On a `RuntimeException`: `jobs.abandon(runId, e.getClass().getName() + ": " + e.getMessage())`, then rethrow.
7. It dispatches with `Run run = new Run(); return jobs.dispatch(runId, (progress, stop) -> execute(...), job -> settle(job, run))`. This is the work and settle hook DFS-051 already has, unchanged. `execute` reads `manifest` from step 4.

`ExecutionOutcomes.ended(job)` calls `job.progress().lease().release()`.

`JobRunner`:

- **`claim`** calls `store.createUnless(id, new Job<>(QUEUED, initial, null, ""), blocks)` and returns its result.
- **`dispatch`** is today's `submitInternal` minus the `store.create`. `submit(id, initial, work[, settled])` becomes `store.create` followed by `dispatch`.
- **`abandon`** does one update: QUEUED becomes `FAILED(progress, null, failure)`, anything else stays as it is.
- **`ended` exactly once.** Each path calls `ended` only if its own update changed the status as listed below. Capture that inside the update lambda, which is applied exactly once per call.
  - **The task (`runTask`):** if the pickup turned QUEUED into RUNNING, it calls `outcomes.ended(store.find(id).get())` in a `finally` after `settle(...)`. That covers the normal return, the `RuntimeException` path, the `Error` path and a throwing `Settled`. A pickup that finds the job no longer QUEUED returns without calling it.
  - **`cancel`:** only when its update turned QUEUED into CANCELLED. It is called on the cancelling caller's thread.
  - **`markFailed` from `dispatch` or `submit`** (a rejection or another executor exception): only when it turned QUEUED into FAILED. If it turned RUNNING into FAILED, the work is running, and the task calls `ended` when the work returns.
  - **`abandon`:** only when it turned QUEUED into FAILED.

`InMemoryJobStore.createUnless`: `create` and `createUnless` are `synchronized` on the store. `createUnless` scans `records`. On the first value that `blocking` accepts, it returns that key. If nothing matches, it does `putIfAbsent`, throwing `IllegalStateException("job already exists: " + id)` if the ID is taken, and returns empty. `update` stays lock-free (`computeIfPresent`). A record that becomes terminal during the scan may still block, which is a safe refusal. A terminal record never becomes unfinished again.

`FileChannelExecutionLock.acquire(folder)`:

- It holds one static registry, `Map<Path, Holder>`, guarded by one static monitor. `Holder` holds `owner` (the adapter instance), `FileChannel`, `FileLock` and `shares`.
- Steps: `Files.createDirectories(folder)`, then `key = folder.toRealPath()`, then:
  - The entry is owned by `this`: `shares++`, return `Acquired`.
  - The entry is owned by another instance: return `HeldElsewhere`.
  - There is no entry: `FileChannel.open(key.resolve(FILE_NAME), CREATE, WRITE)`, then `tryLock()`:
    - `null`: close the channel, return `HeldElsewhere`.
    - `OverlappingFileLockException`: close the channel, return `HeldElsewhere`.
    - A lock: store an entry with `shares = 1`, return `Acquired`.
  - Any `IOException` closes the channel and propagates.
- Each `Lease` has its own `AtomicBoolean`, so a second `release` does nothing. On the first release, under the monitor: `shares--`. At 0: `lock.release()`, close the channel, remove the entry. Exceptions there are swallowed.

| Criterion | Reaches stub |
|---|---|
| AC-1, AC-2, AC-4 (service) | `PlanExecutionService.launch`; later `JobRunner.claim`, `dispatch`, `abandon`, `InMemoryJobStore.createUnless`, `ExecutionOutcomes.ended`, `ManifestWriter.manifestPath` |
| AC-3 (service and adapter) | `PlanExecutionService.launch`, `FileChannelExecutionLock.acquire` |
| AC-4 plan-check-first while e1 RUNNING | `launch` (to start e1) |
| `createUnless` tests | `InMemoryJobStore.createUnless` |

### For the test-author

**Edits to existing tests** (all inside `touches`):

- `PlanExecutionServiceTest`:
  - `Rig` passes a lock as the fifth constructor argument. Use `new FileChannelExecutionLock()` by default, and add a `Rig` variant that takes a fake.
  - `CountingStore` overrides `createUnless`: delegate to `inner`, and add the ID to `created` only when the result is empty.
  - In `movingLineIsOnDiskBeforeTheRename`, find the manifest by `*.jsonl`, not with `findFirst()`.
  - `assertNoTrace` stays as it is. Plan-check refusals never create the folder.
  - Add an `@AfterEach` that calls `cancel` on every ID in every rig's `store.created`. That releases a QUEUED run's lease at once. Tests with a RUNNING run already join it. An unreleased lock leaves a locked file that blocks `@TempDir` deletion on Windows.
- `PlanExecutionRealThreadTest`: the constructor change, if it builds the service directly. It uses the factory at line 72, so likely no edit.
- `PlanVerifyServiceTest`: `RecordingStore` gets a `createUnless` that delegates to its inner store.
- `ScannerAutoConfigurationTest`: add one test for AC-3 wiring.
  1. Declare an app `planExecutionExecutor` that queues tasks without running them.
  2. Start a run through the `StartPlanExecution` bean, so it is QUEUED.
  3. A raw `FileChannel.open(<manifests>/execution.lock, WRITE).tryLock()` in the test throws `OverlappingFileLockException`. This shows the JVM holds the lock.
  4. Cancel through the bean. The same raw `tryLock()` now succeeds; release it.
  5. Call the use case first, so the test is red on the stub.

**New tests:**

- **Fake lock**, for service tests that need no disk: a `CountingLock implements ExecutionLock` that records acquires and live leases, and can answer `HeldElsewhere` or throw an `IOException`. "The lock is held only on behalf of e1" means one live lease after the refused start.
- **Cross-process probe:** `ExecutionLockProbe`, a public class with a `main` in `infra.adapter`, test tree. Model it on `NativeAccessProbe` and `NioFileIdentityTest` (`ProcessHandle.current().info().command()`, `-cp java.class.path`). It has two modes:
  - `hold <file>`: open with `CREATE, WRITE`, call `tryLock`, print `LOCKED` or `BUSY`, then block reading stdin until EOF and exit.
  - `try <file>`: call `tryLock`, print `FREE` (then release) or `HELD`, and exit.

  The test reads the first line, closes stdin to end `hold`, and calls `waitFor(30, SECONDS)` with `destroyForcibly()` in a `finally`. Application tests can use it too: it is the same test source set.
- **AC-1:** a real `FileChannelExecutionLock`, the `blockedRig` pattern (the comparator blocks on a latch, so run 1 is RUNNING after its checks), and plan 2 crosswise. Start 2 is refused, `deleter.onMove` records no move for plan 2, then release run 1. At least one of the two files exists outside quarantine.
- **AC-2:**
  - The same RUNNING rig: the message contains `e1.value().toString()`, and `store.created` has one ID.
  - "Recorded COMPLETED": run e1 to its end with `manual.runNext()`, which joins, so `ended` has run. Then start plan 2.
  - Cancel while QUEUED: start, cancel, start again.
  - Executor rejection: an executor that rejects its first task and queues later ones.
- **AC-3:**
  - The probe holds the lock: start returns `EXECUTION_ALREADY_RUNNING` with the exact message, and no move. End the probe, then start again: success.
  - "Held exactly while unfinished": the probe in `try` mode says `HELD` while RUNNING, and `FREE` after `runNext()` has joined. With a real executor, poll the probe with a bound.
- **AC-4:**
  - A conflicted plan while e1 is RUNNING returns `PLAN_HAS_CONFLICTS`.
  - Two threads on one `CountDownLatch`, each starting a plan on one service with a manual executor: exactly one `Success`, one `EXECUTION_ALREADY_RUNNING`, and `created.size() == 1`.
  - The refused-start lease check, using the fake or the probe.
- **Adapter tests** (`FileChannelExecutionLockTest`):
  - `acquire` creates the folder and `execution.lock`.
  - Two acquires by one instance give two leases. Another instance answers `HeldElsewhere`.
  - Releasing one lease keeps the lock: the probe says `HELD`. Releasing the last frees it: the probe says `FREE`.
  - `release` is idempotent.
  - A folder path that is a file throws `IOException`.
  - A probe holding the lock gives `HeldElsewhere`.
- **`InMemoryJobStoreTest`:** `createUnless` stores when nothing matches. It returns the blocker's ID and stores nothing when something matches. A duplicate ID with no match throws `IllegalStateException`. Many threads racing with a predicate that matches any record: exactly one stores.
- **No `Thread.sleep`:** use latches, joins, `waitFor` with a timeout, or bounded polls.

## State and transitions

`JobStatus` is unchanged (DFS-042/051). What is new is when the lease and the slot are held:

| From | Event | To | Lease |
|---|---|---|---|
| — | start: plan checks fail | — (no record) | never taken |
| — | start: lock `HeldElsewhere` or `IOException` | — (no record) | never taken |
| — | start: claim blocked by an unfinished record | — (no record) | released at once |
| — | start: claim succeeds | QUEUED | held by the job |
| QUEUED | manifest create fails (`abandon`) | FAILED | released by `ended` |
| QUEUED | executor rejects or throws | FAILED | released by `ended` |
| QUEUED | cancel | CANCELLED | released by `ended` (caller's thread) |
| QUEUED | task pickup | RUNNING | held |
| RUNNING | cancel | CANCELLING | held |
| RUNNING / CANCELLING | work returns or throws | COMPLETED / CANCELLED / FAILED | released by `ended` after `Settled` (task's thread) |

The slot is free exactly when no record is QUEUED, RUNNING or CANCELLING. Commands on terminal records are unchanged: cancel has no effect, status and result are as in DFS-051.

## Concurrency and atomicity

- **Threads.** The caller's thread runs the plan checks, the lock acquire, the claim, the manifest create, the dispatch and, on rejection or abandon, `ended`. The executor's thread runs the work, `Settled` and then `ended`. A QUEUED cancel runs `ended` on the cancelling thread.
- **State.** The slot is the set of unfinished records in the `JobStore`. It is decided only inside `createUnless`, which is atomic with every other create. The lease lives in the job's progress, the one immutable record per job. The service has no new field other than the `lock` port.
- **Two simultaneous starts in one set.** Both get a lease (shares), and `createUnless` serializes them: one stores, the other sees it and releases its lease.
- **A start racing a finishing run.** The run records its terminal status before `ended` releases its share. A start in between still gets a share and sees the record terminal, so it succeeds. The OS lock never drops while either holds a share.
- **Cancel racing pickup.** This is DFS-042's existing atomic update. Whoever moves the job out of QUEUED decides who calls `ended`, so it is called exactly once.
- **Another process, or another set in this JVM.** The OS lock, or the registry owner check, refuses before any record exists.

## Checklist disposition

1. **Unknown or stale IDs:** unchanged (`UNKNOWN_PLAN_EXECUTION`). Start takes no ID.
2. **Results as values, bugs as exceptions:** the new refusals are `Failure` values (`EXECUTION_ALREADY_RUNNING`, and `MANIFEST_UNWRITABLE` for lock I/O). Null arguments stay `NullPointerException`. A store or executor `RuntimeException` still propagates, after the lease is released or handed to the job.
3. **Status model:** `JobStatus` is unchanged. "Unfinished" means QUEUED, RUNNING or CANCELLING (State table).
4. **Races and who wins:** the claim is one `createUnless`. `ended` is called by whichever single update ended the job (Concurrency section).
5. **Validation before side effects:** plan checks, then the lock, then the claim. Each refusal leaves no record, no ID and no lease. The manifest is created after the claim, which is a deviation (Open design notes 2).
6. **State placement:** the slot is in the store and the lease is in the job's progress. The service has no flag or field beyond its ports, the config and the job runner.
7. **Progress and observation:** not applicable: no new progress. Snapshots do not show the lease.
8. **Caller-supplied callbacks:** not applicable: the listener handling is unchanged.
9. **Failures off the caller's thread:** `ended` runs in a `finally` on the task path, so an `Error` or a throwing `Settled` still releases the lease. Failures are recorded as before.
10. **Query results:** not applicable: queries are unchanged.
11. **Executor contract:** the record exists before submission, as in DFS-042. A rejection or executor exception records FAILED and calls `ended`, which releases the lease. `EXECUTOR_REJECTED` is unchanged.
12. **Retention:** not applicable: records are kept until exit, as before. The lock file stays and is reused (decision 3).
13. **Testability:** a fake `ExecutionLock`, `ManualExecutor`, latch-blocked comparators, a latch-released two-thread race, and a child JVM probe with timeouts. No sleeps.
14. **Exported surface:** one new `ErrorCode` constant in exported `domain.model`. No new package and no `module-info` change. No first-slice type is replaced.

## Configuration

- None new. The lock file is `<dfs.execution.manifest-folder>/execution.lock`, using DFS-051's setting. Processes with different manifest folders are not coordinated (decision 3, known limit).

## Expected breakage

None.

## Open design notes (as of the design phase)

1. **No `scanner-spring` bean for the lock.** This deviates from decision 3 and the Scope. `ExecutionLock` is a secondary port in `infra.port`, which is not exported. `scanner-spring`'s `ArchitectureTest` forbids depending on `..scanner.infra..` and forbids beans from internal packages. DFS-051's own secondary ports (`FileDeleter`, `ManifestWriter`) are not beans either; only its use-case ports are. The lock is overridden by overriding `planExecutionUseCases`. `ScannerAutoConfigurationTest` gets a wiring test instead (For the test-author).
2. **The job is recorded before the manifest.** Decision 5 orders plan checks, lock, then claim. Creating the manifest before the claim would leave a stray header-only manifest on every in-process refusal. So the manifest is created after the claim, at the path from the new `ManifestWriter.manifestPath`. The cost: a manifest-file failure after a successful lock and claim is still refused with `MANIFEST_UNWRITABLE`, but leaves an unreachable FAILED record, whose ID is never returned. The folder being unwritable, DFS-051's tested case, is caught by the lock step with no record.
3. **Shares and a process-wide registry in the lock adapter.** A JVM cannot `tryLock` a region it already holds. With the claim after the lock, a second start in the same set must get past the lock so that the claim can name the unfinished ID (AC-2). So one adapter instance shares the OS lock among its starts. Another instance in the same JVM is refused with "another process is executing a plan", which is accurate enough: it is another use-case set. The registry is static because the OS lock table is per JVM.
4. **Lease in `PlanExecutionProgress`.** `ended` must reach the lease from `cancel(id)`, which has only the ID. The job record is the only per-job state, and keeping the lease there respects "no state in singleton fields".

## Stubs (as of the design phase)

- Files stubbed:
  - `application/PlanExecutionService.java`: `launch`, `ExecutionOutcomes.ended`
  - `application/JobRunner.java`: `claim`, `abandon`, `dispatch`
  - `infra/adapter/InMemoryJobStore.java`: `createUnless`
  - `infra/adapter/FileChannelExecutionLock.java` (new): `acquire`
  - `infra/port/ManifestWriter.java`: default `manifestPath`
- Written for real at design: `ExecutionLock` (port, records), `JobStore.createUnless` (abstract), `ErrorCode.EXECUTION_ALREADY_RUNNING`, `PlanExecutionProgress.lease`, the `PlanExecutionService` constructor and `lock` field, `Outcomes.ended` (default no-op), and the `ScannerFactories.planExecutionUseCases` wiring.
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-055")` per D2.
- Commit: filled in after the design commit.
