# DFS-018: Track scans asynchronously — verification

## Summary

- DFS-018.AC-1 met: `start` records QUEUED, then submits. The test pins QUEUED with `ManualExecutor` and checks the full zero snapshot.
- DFS-018.AC-2 met: RUNNING while the runner is blocked, then COMPLETED, and `result` equals the runner's `ScanResult`.
- DFS-018.AC-3 met: all 4 examples fail before `create`/`execute`. The tests assert that there are no records and no tasks.
- DFS-018.AC-4 met: the record goes FAILED with the message containing "queue full", and the call returns `Failure(EXECUTOR_REJECTED)`.
- DFS-018.AC-5 met: QUEUED shows no phase and zero counts. RUNNING shows FULL_HASH, 48210 files and 37/120 groups.
- DFS-018.AC-6 met: the listener gets the exact events in order, all on the task thread (not the test thread). The overload without a listener completes.
- DFS-018.AC-7 met: QUEUED goes to CANCELLED at once, the runner is never called, and the status stays CANCELLED.
- DFS-018.AC-8 met: cancel returns CANCELLING. The stop signal reads false before, then true. The scan ends CANCELLED and `result` returns SCAN_NOT_COMPLETED.
- DFS-018.AC-9 met: all 4 statuses are reached through real transitions. The returned and stored snapshots both equal the snapshot from before the cancel.
- DFS-018.AC-10 met: both orders were tested deterministically, and completion wins in both. In the code, `finish` accepts RUNNING or CANCELLING and `cancel` leaves terminal states alone.
- DFS-018.AC-11 met: UNKNOWN_SCAN for status, cancel and result.
- DFS-018.AC-12 met: all 5 statuses. The message names the status, and the message format avoids the word COMPLETED.
- DFS-018.AC-13 met: both examples give the exact `<class>: <message>`.
- DFS-018.AC-14 met: the OOM propagates out of `runNext()` and the snapshot is FAILED.
- DFS-018.AC-15 met: the listener sees exactly 2 events. `listenerFailure` is exact, the scan still completes, and it counts 3 files walked.
- DFS-018.AC-16 met: all 7 rows throw NPE, with no task and no record.
- DFS-018.AC-17 met: two scans on virtual threads with the real NIO adapters. Each result holds only its own pair. Flakiness risk is low (see below).
- DFS-018.AC-18 met: 8 threads x 1000 increments = 8000 through `computeIfPresent`.
- Verdict: **ready for the human gate.** I found no blocking defects. There are two minor notes: the `catch (Error)` vs `finally` wording, and LOCATION_UNREADABLE plus a non-rejection executor failure being untested by design.

## Basis

- The deterministic gate passed against HEAD 0608c04 (green: 169 passed, 1 pre-existing skip; freeze, touches, stubs, traceability). Nothing was committed after the gate, so I did not re-run it. `git status` shows only the orchestrator's `plan.json` modified.
- I reviewed `git diff main..HEAD`: 13 main-source files, 3 test files and the story folder. `module-info.java` is not in the diff.

## Per-criterion notes

- **AC-1**: `begin` runs `create(QUEUED)` before `executor.execute`, so a task the executor runs inline always finds the record (decision 11). The test also asserts `runner.calls == 0`.
- **AC-2**: the test uses a real blocked state (runner latch) on a test-owned thread, not a mocked status.
- **AC-3**: the fourth example (`[a, missing]`) shows that validation covers every location, not just the first. Null checks run before the empty check, and the empty check runs before resolution, as the design says.
- **AC-4**: `markQueuedFailed` changes only a QUEUED record (a guarded atomic update).
- **AC-5 / AC-6**: each event updates the snapshot first, then goes to the caller's listener, as the design says. `PhaseStarted` resets the group counts. `Skipped` changes nothing.
- **AC-8**: the test checks the stop signal three times: false before cancel, true after cancel, and still true after CANCELLED. That covers "from then on".
- **AC-9 / AC-12**: `Env.reach` drives every status through the service's real transitions; it never seeds the repository directly. The tests therefore also exercise the transition paths.
- **AC-14**: the code catches `Error`, marks the scan FAILED, then rethrows. The test asserts both the propagation and the FAILED status.
- **AC-15**: the detached flag is a task-local `boolean[]`, not a field, and is touched only by the task thread. `listenerFailure` is set once, by a guarded update.

## Concurrency review

- **Every transition is one guarded atomic update.** Pickup (QUEUED to RUNNING), cancel (QUEUED to CANCELLED, RUNNING to CANCELLING), the finish transitions (RUNNING or CANCELLING to the terminal state), the executor-rejection transition (QUEUED to FAILED), each event and the listener failure are each one `repository.update(id, op)`. Each `op` is a pure function of the current record and checks the status inside the lambda. The service decides from `update`'s return value (pickup, cancel), never from a separate `find`. I found no check-then-act across calls.
- **The only reads outside `update`** are `status`, `result` (a single `find` each, a consistent immutable snapshot) and the stop signal (`find`, read-only). None of them writes.
- **Completion beats a racing cancel.** If cancel commits first (CANCELLING), `finish(COMPLETED)` still accepts CANCELLING. If finish commits first, `cancel` sees a terminal status and returns it unchanged. In both cases the result is stored in the same update that writes COMPLETED, so no reader can see COMPLETED without its result.
- **Progress updates cannot undo a cancel.** `applyEvent` copies the current status and has no effect on terminal records.
- **Cancel during pickup.** Cancel first gives CANCELLED, and pickup's guarded update returns it unchanged, so the task exits before calling the runner. Pickup first gives RUNNING, and the cancel then gives CANCELLING.
- **`InMemoryScanRepository`**: `computeIfPresent` on a `ConcurrentHashMap` is atomic per key. The `op` functions are side-effect free; they do not call back into the map, so they cannot re-enter `computeIfPresent`. `create` uses `putIfAbsent`.
- **Service fields**: exactly `resolver`, `runner`, `repository` and `executor`, all `final`. There is no per-scan state in fields.

## The three design-gate choices

- **`ErrorCode.LOCATION_UNREADABLE`**: it is implemented as designed (`catch (IOException)` after the two specific catches, message "location unreadable: <class>: <message>"). No scenario or test exercises it, which matches the approved design and is stated in `test-plan.md`. It is a small, obviously correct branch. I am noting the gap, not counting it as a defect.
- **Stop signal "status is not RUNNING"**: correct. While the runner runs, the status can only be RUNNING or CANCELLING (terminal states are written only by this task's own `finish`). So it is the same as "== CANCELLING", and it reads true when the record is missing. AC-8 covers it.
- **An unrequested `CancellationException` gives CANCELLED**: the catch is unconditional, as approved. Only a misbehaving runner can trigger it. The real pipeline throws it only after the stop signal reads true. No test covers that exact path, which is acceptable.

## Design conformance (checklist disposition 1–14)

All 14 lines hold in the code. Two minor deviations, neither blocking:

- **Item 9** says a `finally` block marks the scan FAILED when an `Error` is thrown. The code uses `catch (Error e) { finish(FAILED); throw e; }` instead. Every `Error` and every declared exception type behaves the same either way. The only difference is a checked exception other than `IOException` thrown "sneakily" by a runner (the signature forbids it). That would leave a record RUNNING, where a `finally` would not. The real `ScanPipeline` cannot do this, so this is a robustness nit only.
- **Item 11**: when `execute` throws a `RuntimeException` that is not a rejection, the record goes FAILED and the exception is rethrown. This is implemented but not tested; no criterion asks for it.

Other notes:

- `result()` additionally requires `r.result() != null`. A runner that returned null would yield "status COMPLETED, no result". This is unreachable with `ScanPipeline` and harmless.
- `ScanPipeline` changes only by `implements ScanRunner` and a one-line `run` that delegates to `scan` (decision 12). DFS-005's tests are untouched.

## Test quality

- The tests assert observable behaviour (returned `Result`s, snapshots read back through `status`, listener recordings, runner call counts, thread identity, propagated throwables), not internal calls. The fakes are scripted at the boundaries (runner, executor, resolver, repository). States are reached through the service's own API, not by seeding records.
- Waiting uses latches, `Thread.join` with a timeout, and `Future.get(10s)`. There is no `Thread.sleep` in the test tree (grep found none). The runner's `release.await` is bounded at 10 s, so a failing test cannot hang.
- `ListingRepository` (the fake) has the same atomicity as the real adapter, and AC-18 tests the real adapter separately.
- **AC-17 flakiness risk is low.** Each task's own `Future` is awaited for at most 10 s, with no polling and no timing assumptions. `futures.size() == 2` is deterministic because the executor lambda adds the future synchronously inside `start`. The expected paths come from `toRealPath()` of the `@TempDir`, so Windows 8.3 short names do not matter. The order inside each set is the pipeline's path order, which is deterministic. The files are tiny. The only realistic slowdown is antivirus on a cold `@TempDir`, and 10 s gives a large margin.

## CLAUDE.md invariants

- **Never delete the last copy, re-verify at execution time, preview is a pure query**: not applicable. There is no deletion in this ticket.
- **Hard links, symlinks/junctions, empty files**: unchanged. They stay in DFS-005's pipeline, which AC-17 runs unmodified, and `ScanService` passes `ScanConfig` through untouched.
- **Spring-friendliness**: the executor is supplied by the caller, state lives behind `ScanRepository`, progress goes through a listener, the core never prints, and there are no Spring types.
- **Layering**: `ScanRecord`/`ScanRepository` are in `infra.port` and depend only on `domain.model`. `InMemoryScanRepository` is in `infra.adapter`. `ScanService` depends on `domain.*` and `infra.port`. `module-info.java` is unchanged, and the new public types are in packages that are already exported.

## Done-items

I ticked all seven after checking them. The "every scenario automated" item rests on the test-plan mapping plus my reading of each test. The "build green" item rests on the gate's run at HEAD.
