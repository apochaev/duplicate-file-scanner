# DFS-051: Execute a deletion plan into quarantine — design

## Summary

An asynchronous `StartPlanExecution(scan, plan[, listener])` job on DFS-042's shared `JobRunner`. It moves every planned link to `<base>/.dfs-quarantine/<run ID>/<path below base>` by atomic rename, and logs each step in `<manifest folder>/<run ID>.jsonl`. Ports are in `domain.usecases.{command,query}`, values in `domain.model`, `PlanExecutionService` in `application`, the `FileDeleter` and `ManifestWriter` ports in `infra.port`, and `NioQuarantineDeleter` and `JsonLinesManifestWriter` in `infra.adapter`. There are no new packages and `module-info.java` is unchanged. State: QUEUED → RUNNING → (CANCELLING) → COMPLETED | CANCELLED | FAILED, unchanged from DFS-042.

- AC-1, AC-21: the per-link loop in `PlanExecutionService.start` plus `NioQuarantineDeleter.quarantinePath`/`move` and `JsonLinesManifestWriter`.
- AC-2, AC-3, AC-22, AC-23: caller-thread validation in `start`, in this order: `PLAN_EDITED` (through the stub `PlanIds.of`, shared with the preview), `PLAN_HAS_CONFLICTS`, `PLAN_NOT_FROM_SCAN`, `KEEPER_NOT_SEPARATE_COPY`.
- AC-4 to AC-8, AC-11: per-link `SkippedFile(path, SkipCause, subject, detail)` inside the `start` work. Hard links are compared once and can end `PartlyMoved`.
- AC-9, AC-10, AC-19: `ManifestWriter.create` on the caller's thread (`MANIFEST_UNWRITABLE`), then `Session.append` per line, forced to disk.
- AC-12 to AC-14, AC-17, AC-18: `cancel`/`status`/`result` plus `ExecutionOutcomes`, `PlanExecutionSnapshot`, `ExecutionEvent`, `PlanExecutionReport`.
- AC-15: the stub `JobRunner.rejectionMessage` (generic text, also used by plan verify). AC-16: the existing Error path, tested through `PlanExecutionService`.
- AC-20: `ScannerFactories.planExecutionUseCases` and the `ScannerAutoConfiguration` beans, both written for real. `ExecutionProperties` binds `dfs.execution.*`.

**Look closely at:**
1. **Deviation: `ExecutionConfig` gains `quarantineAnchors`** (property `dfs.execution.quarantine-anchors`, empty by default, so decision 5 is unchanged unless set). Without it, the AC-20 and AC-21 tests would write to `C:\.dfs-quarantine` outside the `@TempDir`, against the DoD. On Linux and macOS, where `/` is not writable, every file would be skipped as quarantine-not-writable.
2. The `moving` line is written before each link's checks, not only before its rename, so every attempted path has `moving` and then `moved` or `skipped` (the literal reading of AC-9). The one exception: a path whose volume cannot be determined gets only `skipped`.
3. The `end` line's status is the run's own view. A cancel that lands after the last operation but before `JobRunner` records COMPLETED makes the job CANCELLED while the manifest says COMPLETED.
4. A run cancelled while QUEUED, or rejected by the executor, leaves a manifest with only its `run` line. The manifest is created before the job is recorded.

Not applicable: none of the 14 checklist items. Expected breakage: `ScannerAutoConfigurationTest` (its executor bean-name set gains `planExecutionExecutor`).

## Public API

- `StartPlanExecution` (command): `Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan)`; `start(ScanResult, DeletionPlan, ExecutionListener)`.
- `CancelPlanExecution` (command): `Result<PlanExecutionSnapshot> cancel(PlanExecutionId id)`.
- `GetPlanExecutionStatus` (query): `Result<PlanExecutionSnapshot> status(PlanExecutionId id)`.
- `GetPlanExecutionResult` (query): `Result<PlanExecutionReport> result(PlanExecutionId id)`.
- `PlanExecutionId(UUID value)` with `static random()` (stub) in `domain.model`.
- `PlanExecutionSnapshot(JobStatus status, String planId, int operationsDone, int operationsTotal, long bytesDone, long bytesTotal, int filesMoved, long bytesMoved, String failure)`.
- `PlanExecutionReport(PlanExecutionId runId, String planId, Path manifest, List<OperationOutcome> outcomes, boolean cancelled, int filesMoved, long bytesMoved, int filesSkipped, long bytesSkipped, String listenerFailure)`.
- `OperationOutcome` (sealed; `digest()`, `size()`, `keeper()`): `Moved(digest, size, keeper, List<MovedFile> moved)`, `PartlyMoved(..., List<MovedFile> moved, List<SkippedFile> skipped)`, `Skipped(..., List<SkippedFile> skipped)`, `NotAttempted(..., List<Path> links)`.
- `MovedFile(Path original, Path quarantine)`; `SkippedFile(Path path, SkipCause cause, Path subject, String detail)`.
- `SkipCause` enum, in precedence order: `MISSING, NOT_A_REGULAR_FILE, UNREADABLE, SIZE_CHANGED, MODIFIED_CHANGED, DIFFERS, QUARANTINE_NOT_WRITABLE, MOVE_FAILED`.
- `ExecutionEvent` (sealed): `BytesCompared(long bytesDone, long bytesTotal)`, `OperationDone(OperationOutcome outcome, int done, int total)`; `ExecutionListener.onEvent(ExecutionEvent)`.
- `ExecutionConfig(Path manifestFolder, List<Path> quarantineAnchors)`, plus the constructor `ExecutionConfig(Path manifestFolder)` with no anchors.
- `ErrorCode`: `PLAN_HAS_CONFLICTS`, `UNKNOWN_PLAN_EXECUTION`, `PLAN_EXECUTION_NOT_FINISHED`, `MANIFEST_UNWRITABLE`, `PLAN_EDITED`, `KEEPER_NOT_SEPARATE_COPY` (the `EXECUTOR_REJECTED` javadoc is widened).
- `PlanExecutionUseCases(start, cancel, status, result)` in `domain.factory`; `ScannerFactories.planExecutionUseCases(Executor executor, ExecutionConfig config)`.

No first-slice type is replaced. Plan verify's ports are unchanged.

Record checks for the implementer to add (the data records are written without checks):
- every component non-null, and lists defensively copied (`List.copyOf`);
- `PlanExecutionSnapshot`: `0 <= operationsDone <= operationsTotal`, `0 <= bytesDone <= bytesTotal`, and `filesMoved`, `bytesMoved` >= 0;
- `PlanExecutionReport`: totals >= 0;
- `BytesCompared`: `0 <= bytesDone <= bytesTotal`;
- `OperationDone`: `1 <= done <= total`;
- `Moved`: non-empty `moved`; `PartlyMoved`: both lists non-empty; `Skipped`: non-empty `skipped`; `NotAttempted`: non-empty `links`;
- `ExecutionConfig`: `manifestFolder` and every anchor absolute (`IllegalArgumentException`);
- `ManifestLine` records: non-null except `Moving.quarantine`, which is never null;
- `PlanExecutionProgress`: the same ranges as the snapshot.

## Layer placement

- `domain.usecases.command` / `domain.usecases.query` (exported): `StartPlanExecution`, `CancelPlanExecution` / `GetPlanExecutionStatus`, `GetPlanExecutionResult`.
- `domain.model` (exported): the types listed above, plus the six `ErrorCode` values.
- `application`:
  - `PlanExecutionService`, with a private `ExecutionOutcomes`;
  - `PlanExecutionProgress` (the job's `P`);
  - `PlanIds.of` (the shared plan-ID encoding);
  - `JobRunner.rejectionMessage` (package-private).
- `infra.port`: `FileDeleter` (with sealed `MoveResult`: `Moved`, `QuarantineNotWritable(detail)`, `Failed(detail)`), `ManifestWriter` (with `Session`), and sealed `ManifestLine` (`Run`, `Moving`, `Moved`, `Skipped`, `End`).
- `infra.adapter`: `NioQuarantineDeleter(List<Path> anchors)` and `JsonLinesManifestWriter`. Job state uses the existing `InMemoryJobStore`.
- `scanner-spring`:
  - `ExecutionProperties` (`@ConfigurationProperties("dfs.execution")`, Strings, defaulting `manifest-folder` to `${user.home}/.dfs/manifests`);
  - `ScannerAutoConfiguration`: `@EnableConfigurationProperties`, the `executionConfig`, `planExecutionExecutor` and `planExecutionUseCases` beans, and four delegate port beans, each `@ConditionalOnMissingBean`.

## Behavior (the contract the implementer follows)

**`start` on the caller's thread.** Each refusal is a `Failure` returned before anything is recorded, created or moved.
1. `requireNonNull` on `scan`, `plan` and (overload) `listener`.
2. `PlanIds.of(plan.operations(), plan.conflicts())` must equal `plan.id()`. Otherwise `PLAN_EDITED`: `"plan <id> does not match its content (<recomputed>)"`.
3. `plan.conflicts()` must be empty. Otherwise `PLAN_HAS_CONFLICTS`: `"plan has <n> conflicts, first in set <digest>"`.
4. For each operation `i`, in order:
   1. There must be a scan set with `q.digest()`. Otherwise `PLAN_NOT_FROM_SCAN`: `"operation <i>: no set with digest <d> in the scan"`.
   2. Every link must be a path of that set's copies. Otherwise `PLAN_NOT_FROM_SCAN`: `"operation <i>: <p> is not a copy of set <d> in the scan"`.
   3. The set of `q.links()` must equal the set of paths of exactly one `PhysicalCopy`, and `q.keeper()` must be a link of a different copy of that set. Otherwise `KEEPER_NOT_SEPARATE_COPY`: `"operation <i> (set <d>): keeper <k> is not a link of another physical copy"`. The links check uses the same message with `"links are not one physical copy"`.

   The keeper is deliberately not checked under `PLAN_NOT_FROM_SCAN`: AC-23's third example (a keeper in no set) expects `KEEPER_NOT_SEPARATE_COPY`. This differs from `CheckPlanDrift`.
5. Create `runId = PlanExecutionId.random()`, then call `manifests.create(config.manifestFolder(), new Run(runId, plan.id(), scan.roots(), Instant.now()))`. An `IOException` gives `MANIFEST_UNWRITABLE`: `"manifest folder <f>: <message>"`.
6. `jobs.submit(runId, new PlanExecutionProgress(runId, manifest, plan, 0, 0, Σsize, 0, 0), work)`. This returns `Success(runId)`, or `Failure(EXECUTOR_REJECTED, JobRunner.rejectionMessage(failure))`, where the message is `"executor rejected the task: <class>: <message>"`.

**The work, on the executor's thread** (`JobRunner.Work`):
1. `manifests.open(manifest)`.
2. For each operation: if `stop` is true, every remaining operation becomes `NotAttempted`, `cancelled = true`, and the loop ends. Cancel is checked only here, between operations (7a, 8).
3. For each link `L` of the operation, in link order:
   1. `target = deleter.quarantinePath(L, runId)`. If this throws `IOException`, `L` is skipped as `QUARANTINE_NOT_WRITABLE` (subject `L`), gets a `Skipped` line only, and the loop goes to the next link.
   2. Append `Moving(L, target, keeper, digest, size, scanEntry(L).lastModified())`.
   3. Drift-check `L` (role CANDIDATE), then the keeper, through `reader.read` (no link following). The precedence is the same as `CheckPlanDriftService`:
      - `NoSuchFileException` gives `MISSING`;
      - any other `IOException` gives `UNREADABLE` (detail: the message);
      - not `isRegularFile && !isOther && !isSymbolicLink` gives `NOT_A_REGULAR_FILE` (detail as `VerifyPlanContentService.what`);
      - a size different from the scan entry gives `SIZE_CHANGED`;
      - a last-modified time different from the scan entry gives `MODIFIED_CHANGED`.

      The subject is the path that drifted.
   4. If there is no skip yet and the operation has no compare result yet, call `comparator.compare(L, keeper, progress, () -> false)` once. The compare is never cancelled mid-file. The result is kept for every later link of the operation:
      - `Differs(o)` skips this and every later link as `DIFFERS`, detail `"first difference at byte <o>"`;
      - an `IOException` gives `UNREADABLE`, detail the message.
   5. If there is no skip, call `deleter.move(L, target)`:
      - `Moved` adds a `MovedFile`;
      - `QuarantineNotWritable(d)` skips as `QUARANTINE_NOT_WRITABLE`;
      - `Failed(d)` skips as `MOVE_FAILED`, with detail `d` (AC-7's `"access denied"`).
   6. Append `Moved(L, target)` or `Skipped(L, cause, subject, detail)`.
4. The operation's outcome is `Moved` if all links moved, `Skipped` if none did, and `PartlyMoved` otherwise.
5. Events, in order: `BytesCompared` from the compare (`base + min(n, size)`); one closing `BytesCompared(base + size)` if that value was not the last one sent; then `OperationDone(outcome, i, total)`. The snapshot progress is updated from the same events. `OperationDone` adds the outcome's moved file count, and its size when `Moved`.
6. After the loop: append `End(cancelled ? CANCELLED : COMPLETED, moved, skipped, notAttempted, Instant.now())`, close the session, and return the report. Its totals follow `PlanExecutionReport`'s javadoc, and its `listenerFailure` comes from the detach.
7. A manifest `IOException` (from open or append) is rethrown as `UncheckedIOException(message, e)`, so no further rename happens and `JobRunner` records FAILED `"java.io.UncheckedIOException: <message>"` (AC-10). On any `RuntimeException` or `Error` leaving the loop, the work tries once to append `End(FAILED, ...)` (a failure of that attempt is added as suppressed), closes the session, and rethrows. `JobRunner` then records FAILED and rethrows an `Error` (AC-16).

**Listener**: wrapped as in `PlanVerifyService.begin`. The progress update runs first, then the listener. The listener is detached on its first `RuntimeException`, and the message (or its `toString()` when blank) becomes `listenerFailure`.

**`cancel`/`status`/`result`**: as in `PlanVerifyService`:
- an unknown ID gives `UNKNOWN_PLAN_EXECUTION`, `"unknown plan execution: <uuid>"`;
- `result` before COMPLETED or CANCELLED gives `PLAN_EXECUTION_NOT_FINISHED`, `"plan execution <uuid> has status <S>, no result"`;
- the snapshot comes from `PlanExecutionProgress` plus the job's status and failure.

**`ExecutionOutcomes`**: `cancelledBeforeStart` returns every operation `NotAttempted`, `cancelled = true`, zero totals and `""`. `markCancelled` sets `cancelled = true`.

**Adapters.**
- `NioQuarantineDeleter.quarantinePath`: `base` is the deepest anchor that `L.startsWith`, else the volume root. To find the volume root, climb from the nearest existing ancestor of `L` (checked with `NOFOLLOW_LINKS`) while the parent's `Files.getFileStore` equals that ancestor's. The result is `base/.dfs-quarantine/<uuid>/base.relativize(L)`.
- `move`: `createDirectories(target.getParent())` (on failure, `QuarantineNotWritable`). If `target` already exists (`NOFOLLOW_LINKS`), the result is `Failed("quarantine path exists: <target>")`. Otherwise `Files.move(L, target, ATOMIC_MOVE)`, where any `IOException` gives `Failed(message)`.
- `JsonLinesManifestWriter`:
  - `create` makes the folder with `createDirectories` and the file with `CREATE_NEW`;
  - `Session.append` writes one line, then `force(true)`;
  - lines use the field names in `ManifestLine`'s javadoc, paths as `toString()`, instants as `Instant.toString()`, `runId` as the UUID string;
  - JSON string escaping is hand-written (`"`, `\`, and control characters as `\uXXXX`).

**Which stub each criterion reaches.**
- AC-1 to AC-14 and AC-17 to AC-19: `PlanExecutionService.start` first (status, cancel and result tests start a run first). AC-14's unknown-ID cases reach `cancel`, `status` and `result` directly.
- AC-15: for plan execution, `start`; for plan verify, `JobRunner.rejectionMessage`.
- AC-16: `start`.
- AC-20, AC-21: `start` through the factory or bean.
- Adapter tests: `NioQuarantineDeleter.quarantinePath`/`move` and `JsonLinesManifestWriter.create`/`open`.
- AC-22, AC-23: `start`, and `PlanIds.of` where the test recomputes an ID.

### For the test-author

- Story fixtures:
  - Build `ScanResult` and `DeletionPlan` in code. For a plan with a valid ID, run `ScannerFactories.previewDeletionPlan()` over the scan.
  - AC-22: rebuild the conflicting plan with `conflicts = List.of()` and the same `id`.
  - AC-23: rebuild the operation with the new keeper and `id = PlanIds.of(...)` (from the `application` test package; it is a stub now, so the test is red on it).
  - The "lib/C" fixtures for AC-23 need hard links: build `PhysicalCopy` with two `FileEntry` links in code. No disk links are needed for `application` tests.
- `application` tests (`PlanExecutionServiceTest`) use `new PlanExecutionService(reader, comparator, deleter, manifests, new InMemoryJobStore<>(), executor, new ExecutionConfig(tmp.resolve("manifests")))` with fakes:
  - a manual executor (QUEUED, AC-12, AC-14);
  - a comparator blocking on a latch (RUNNING/CANCELLING), throwing `OutOfMemoryError` (AC-16), or recording calls (AC-8 "compared once", AC-6 "no read through it");
  - a `FileDeleter` fake that fails chosen paths with `Failed`/`QuarantineNotWritable` and records the manifest's content on `move` (AC-9 scenario 2);
  - a `ManifestWriter` fake failing on a chosen `ManifestLine` (AC-10).

  Real `NioFileAttributesReader`, `JsonLinesManifestWriter` and `NioQuarantineDeleter(List.of(tmp))` over `@TempDir` files are fine. **Always pass the `@TempDir` as an anchor** so nothing lands under a real volume root. The scenarios' `<volume root>` is then `tmp`.
- AC-13 in both orders: an executor that captures the task, then cancel and run, or run and then cancel. For "cancel recorded before completion", block the comparator, cancel, release.
- AC-15 for plan verify: add to `PlanVerifyServiceTest` (or a new class) a rejecting executor and assert the message contains neither `"plan verify"` nor `"plan execution"`. The existing `PlanVerifyServiceTest` rejection test reaches the stub directly; leave its assertions as they are (they assert only the code).
- AC-16 must go through `PlanExecutionService` (a direct `JobRunner` test would be green at the red gate). This pins decision 7c's `Error` path.
- AC-6: create the symlink with `Files.createSymbolicLink`, and skip with `Assumptions` on `IOException` or `UnsupportedOperationException`. Keep the size and mtime equal by copying the keeper's attributes to the link target.
- Adapter tests (`NioQuarantineDeleterTest`, `JsonLinesManifestWriterTest` in `infra.adapter`):
  - the mirrored path under an anchor;
  - an unwritable quarantine, made by putting a regular file named `.dfs-quarantine` in the anchor, gives `QuarantineNotWritable`;
  - an existing target gives `Failed`;
  - lines in order, readable after each `append`.
- AC-21: `PlanExecutionRealThreadTest` with `Executors.newVirtualThreadPerTaskExecutor()` wrapped to capture the `Future`, bounded at 10 s, using `ScannerFactories.planExecutionUseCases(executor, new ExecutionConfig(tmp.resolve("m"), List.of(tmp)))`.
- AC-20 (`ScannerAutoConfigurationTest`):
  - set `dfs.execution.manifest-folder` and `dfs.execution.quarantine-anchors` to `@TempDir` paths, and call `StartPlanExecution` first (red on the stub);
  - assert the default `ExecutionConfig.manifestFolder()` equals `Path.of(System.getProperty("user.home"), ".dfs", "manifests")` and that `Files.exists` is false for a fresh UUID-named folder property. Never touch the real default.
  - Also update `appExecutorReplacesDefault`'s expected set to `{"scanExecutor", "planVerifyExecutor", "planExecutionExecutor"}`.

## State and transitions

| From | Event | To |
|---|---|---|
| (none) | `start` passes validation and the manifest is created | `QUEUED` |
| `QUEUED` | executor rejects the task | `FAILED` |
| `QUEUED` | `cancel` | `CANCELLED` (all `NotAttempted`) |
| `QUEUED` | task starts | `RUNNING` |
| `RUNNING` | `cancel` | `CANCELLING` |
| `RUNNING` | work returns | `COMPLETED` |
| `CANCELLING` | work returns (after the operation in progress) | `CANCELLED` |
| `RUNNING`, `CANCELLING` | work throws (manifest line, bug, `Error`) | `FAILED` |

- `cancel`: QUEUED becomes CANCELLED and RUNNING becomes CANCELLING. CANCELLING and the terminal states are unchanged. It returns the snapshot after the request.
- `status`: returns the snapshot in every state.
- `result`: returns the report in COMPLETED and CANCELLED, and `PLAN_EXECUTION_NOT_FINISHED` otherwise.

## Concurrency and atomicity

- Threads: the caller's thread runs validation, manifest creation (with its `run` line), recording and submission. The executor's thread runs the loop, the manifest appends and the listener events.
- State: one immutable `Job<PlanExecutionProgress, PlanExecutionReport>` per run in `JobStore`. Every transition and progress change is one `JobStore.update` that checks the status (`JobRunner`, unchanged except for its message).
- Completion against cancel: whichever `update` lands first wins (AC-13). The work reads `stop` only between operations, so the operation in progress always finishes, links and lines included.
- Known window: if the cancel lands after the work's last `stop` read and before `JobRunner` records COMPLETED, the job is CANCELLED (`markCancelled`: `cancelled = true`, no `NotAttempted`) while the manifest's `end` line says COMPLETED. Every file is still accounted for by its own lines.
- No per-run state in service fields. The detach flag and counters are local to each run's work.

## Checklist disposition

1. Unknown or stale IDs: `Failure(UNKNOWN_PLAN_EXECUTION)` naming the UUID, for cancel, status and result alike (AC-14).
2. Results as values, bugs as exceptions: the six new codes are `Failure`s, and `EXECUTOR_REJECTED` is reused. A null argument is an NPE, and the listener comes through an overload (AC-19).
3. Status model: `JobStatus`, as in the table above. Terminal states are COMPLETED, CANCELLED and FAILED.
4. Races and who wins: completion against cancel is settled by the first `JobStore.update`. Cancel during pickup: `runTask` starts only from QUEUED. One known manifest-status window, stated above.
5. Validation before side effects: plan ID, conflicts, from-scan, separate copy, then the manifest file. Nothing is recorded until all pass. The executor's per-file drift and content re-check deliberately repeats DFS-033 and DFS-034 just before each move (decision 3). Whatever changes in between becomes a per-file skip.
6. State placement: one immutable `Job` record per run behind `JobStore`. The cancel request is the CANCELLING status. The service's fields are its ports, the config and the runner.
7. Progress and observation: both pushed (`ExecutionListener`) and pulled (`PlanExecutionSnapshot`, zeros before the first event), kept from the same events.
8. Caller-supplied callbacks: the listener is detached on its first `RuntimeException`, recorded as the report's `listenerFailure`, and the run continues (AC-17).
9. Failures off the caller's thread: they end FAILED with `<class>: <message>` in the snapshot's `failure`. An `Error` also ends FAILED and is rethrown (AC-16). A manifest failure stops the run before the next rename (AC-10).
10. Query results: the whole report, in plan order, for COMPLETED and CANCELLED (CANCELLED with `NotAttempted` entries). Otherwise `PLAN_EXECUTION_NOT_FINISHED`.
11. Executor contract: supplied by the caller. The run is recorded QUEUED before `execute`. A rejection gives FAILED plus `Failure(EXECUTOR_REJECTED)` with the generic message (AC-15). Events arrive on the executor's thread.
12. Retention: kept until the process exits, with no eviction (DFS-042's out of scope, unchanged). The manifest file is permanent.
13. Testability: manual executor, latch comparator, failing fakes, both orders of the race, one real-thread test bounded on a `Future`, and no `Thread.sleep`. No seam is needed: every collaborator is a port.
14. Exported surface: all new types sit in packages already exported, `module-info.java` is unchanged, and no first-slice type is replaced.

## Configuration

- `dfs.execution.manifest-folder`: default `${user.home}/.dfs/manifests`, made absolute in `ExecutionProperties.toConfig()`. The folder is created only by `ManifestWriter.create` when a run starts.
- `dfs.execution.quarantine-anchors`: default none (see Open design notes). Used only by `NioQuarantineDeleter.quarantinePath`.

## Expected breakage

- `ScannerAutoConfigurationTest`: `appExecutorReplacesDefault` asserts the `ExecutorService` bean names are exactly `{scanExecutor, planVerifyExecutor}`, and the new `planExecutionExecutor` bean changes that set (a plain assertion failure, not the stub). The test-author updates the expected set.

## Open design notes (as of the design phase)

1. **`ExecutionConfig.quarantineAnchors` (deviation from decision 10's one-component record).**
   - Why: with decision 5 alone, the factory and Spring test (AC-20) and the end-to-end test (AC-21) would write to `C:\.dfs-quarantine\...` outside their `@TempDir`, breaking the DoD. On Linux and macOS, where the volume root `/` is not writable, they would fail outright. Real users there would see every file skipped as quarantine-not-writable.
   - What: anchors are absolute folders that stand in for a volume root for files below them. Moves stay same-volume in practice; a cross-volume rename fails as `MOVE_FAILED`, which is safe. With no anchors set, decision 5 holds exactly.
   - The `ExecutionConfig(Path)` constructor keeps decision 10's call shape.
   - If the gate rejects this, AC-20 and AC-21 cannot be met inside a `@TempDir` without a test-only seam that the Spring test cannot reach.
2. **`moving` before the checks.** Decision 6 lists a `moving` line per operation file, followed by `moved` or `skipped` "with the reason, decision 4", and decision 4's reasons include drift and content. So `moving` is written before a link's drift check and compare, and stays "before the rename". The one exception is a link whose quarantine path cannot be named (its volume is unreadable): it gets only `skipped`.
3. **The compare runs on the first link that passes its drift check**, not literally the first link, so a missing first link does not leave the rest unverified (decision 8).
4. **`NOT_A_REGULAR_FILE` comes from the drift read** (`SkipCause`, a flat enum mirroring `DriftKind` plus `OperationCheck.Outcome`). The comparator is never called for a link or a junction (AC-6).
5. **`bytesMoved`** counts each operation moved whole, once, matching `PlanTotals.bytes`. A `PartlyMoved` operation's size counts as skipped.
6. **QUEUED-cancelled or rejected runs** leave a manifest with only its `run` line (the manifest has to exist before the job is recorded to satisfy AC-10).
7. Follow-up worth filing: a later scan of a volume root or anchor would walk `.dfs-quarantine` and offer quarantined files as duplicates. The walker should skip that folder (not in this ticket's scope).

## Stubs (as of the design phase)

- Files stubbed:
  - `PlanExecutionService` (`start` ×2, `cancel`, `status`, `result`, `ExecutionOutcomes` ×2);
  - `PlanIds.of`;
  - `JobRunner.rejectionMessage`;
  - `PlanExecutionId.random`;
  - `NioQuarantineDeleter.quarantinePath`/`move`;
  - `JsonLinesManifestWriter.create`/`open`.
- Written for real: the ports, data records (unchecked), `PlanExecutionUseCases`, `ScannerFactories.planExecutionUseCases`, `ExecutionProperties`, and the `ScannerAutoConfiguration` beans.
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-051")` per D2.
- Commit: filled in after the design commit.
