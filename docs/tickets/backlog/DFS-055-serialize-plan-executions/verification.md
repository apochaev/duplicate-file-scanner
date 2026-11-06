# DFS-055 verification

## Product owner review

### What you can do now

Only one plan execution can move files at a time. If you start a second one while the first is still queued, running or cancelling, it is refused with `EXECUTION_ALREADY_RUNNING`. Nothing about the second plan is recorded or moved. This also applies across processes that share a manifest folder (for example the CLI and the server), through an OS lock on `<manifest folder>/execution.lock`. Two crosswise plans (one keeps X, the other keeps Y) can no longer leave a set with no copy outside quarantine.

### Acceptance criteria

- **DFS-055.AC-1, met:** while one run is unfinished, a second run of the same service moves nothing. The crosswise case keeps a copy in place.
- **DFS-055.AC-2, met:** a second start is refused with a message naming the first run's ID. Once that run is COMPLETED, CANCELLED or FAILED, the next start succeeds. That includes a run cancelled while queued and one the executor rejected.
- **DFS-055.AC-3, met:** while another process holds the lock, a start gets the exact message "another process is executing a plan". When that process ends, a start succeeds. A run holds the lock from its start until it is recorded finished.
- **DFS-055.AC-4, met:** plan problems are reported before the lock is touched. A refused start leaves no record and no lock held. Of two simultaneous starts, exactly one wins (checked over 20 rounds). A run that is cancelled while queued, or rejected by the executor, releases the lock.

### Try it

Nothing is user-visible yet: the Execute button and any CLI entry point come with M-5. To see it working, run the tests:

```
./gradlew :file-scanner:test --tests "*PlanExecutionServiceTest" --tests "*FileChannelExecutionLockTest" --tests "*InMemoryJobStoreTest"
./gradlew :scanner-spring:test --tests "*ScannerAutoConfigurationTest"
```

Look for the tests named `DFS-055.AC-*` in `file-scanner/build/reports/tests/test/index.html` (32 of them, all passing). The cross-process tests start a second Java process (`ExecutionLockProbe`) that holds or probes the lock file.

### Decided without you

- **No Spring bean for the lock** (approved at the design gate). The lock port lives in a package the Spring module may not depend on. To override the lock, an app replaces the whole `planExecutionUseCases` bean.
- **The run is recorded before its manifest file is created** (approved at the design gate). This avoids a stray manifest file on every refused start. The cost: if the manifest file alone cannot be created, the start is still refused with `MANIFEST_UNWRITABLE`, but it leaves a FAILED record whose ID was never returned.
- **One process-wide lock registry, with shares** (approved at the design gate). Starts through one use-case set share the OS lock, so the job store can name the blocking run. A second use-case set in the same JVM with the same manifest folder is refused with the "another process is executing a plan" message, which is slightly inaccurate wording for that case.
- **An unwritable manifest folder is now caught at the lock step.** The code is the same as before (`MANIFEST_UNWRITABLE`), still with no record left behind.
- **The lock file `execution.lock` stays in the manifest folder for good**, as decision 3 said. Tests that list manifests now filter on `*.jsonl`.

### Known limits

- Processes configured with different manifest folders are not coordinated (decision 3).
- Later hard links of a copy are still checked only by size and modified time. DFS-056 adds the identity check.
- No UI or CLI shows the refusal yet (M-5).
- The rare FAILED record left when the manifest file alone fails (see "Decided without you").

### Evidence

- I re-ran `./gradlew build` on this branch (HEAD 9ccf539) and it is green. JUnit: 725 tests, 3 skipped (pre-existing: `ScanCommandTest`, `NioFileIdentityTest`, `NioFileTreeWalkerTest`), 0 failures. With Vitest, that matches the gate's 804 passed and 3 skipped.
- Story tests, none skipped per JUnit XML:
  - `PlanExecutionServiceTest`: 57 tests in total, of which 22 are DFS-055 (counting the 3 parameterized endings).
  - `FileChannelExecutionLockTest`: 6.
  - `InMemoryJobStoreTest`: 4 new.
  - `ScannerAutoConfigurationTest`: 1 new.
  - 32 test cases are named `DFS-055.*`.
- Deterministic gate on e22db17: green, disabled, freeze, touches (23 files), stubs, traceability (4 criteria, 9 scenarios), consistency.
- After the build, no stray Java child process was left running (only the Gradle daemon).

### How it went

- **Design:** 1 attempt (opus), approved at the design gate with three deviations.
- **Tests:** 1 attempt (sonnet) plus 1 rework for defect 1. The FAILED case of `lockIsHeldUntilTheRunIsRecordedTerminal` failed a manifest line that was already written. The implement-phase green gate caught it, and it went back to the test-author. Closed.
- **Implement:** 1 attempt (sonnet). It first failed the gate on that test defect only, then was reconciled and committed after the test fix (e22db17). No escalations.

Ready for the user's approval.

## Summary

- **DFS-055.AC-1, met:** the crosswise-plans test shows the second start refused, no move for plan 2, and a copy still in place.
- **DFS-055.AC-2, met:** the refusal names the e1 ID. The slot is free after COMPLETED, FAILED and CANCELLED, after a cancel while QUEUED, and after an executor rejection, all tested.
- **DFS-055.AC-3, met:** a child-JVM probe holds the lock and gets the exact message; when it ends, a start succeeds. The lock is HELD while RUNNING and FREE after the run is terminal, using both a real file and a fake lock.
- **DFS-055.AC-4, met:** the plan is checked before the lock and the lock before the claim, each proven with a sound plan reaching the step first. Refusals leave no record or lease. The latch race gives one winner over 20 rounds, and the store's 16-thread race also gives exactly one. Cancel while QUEUED and executor rejection both release the lock.
- **Design conformance:** the implementation matches design.md's Behavior, State and Concurrency sections. All 14 checklist items are dispositioned. The three gate-approved deviations are implemented as described.
- **Invariants:** this ticket strengthens "never delete the last copy". Re-verify at execution time is unchanged. Preview is untouched. Hard links, symlinks and empty files are not affected.
- **Minor notes (not blocking):** listed under Design conformance below.
- **Verdict:** ready for the human gate.

## Criteria

- **AC-1, met.** In `PlanExecutionService.launch`, the claim is `JobRunner.claim` → `JobStore.createUnless` with a predicate matching QUEUED, RUNNING or CANCELLING. Only the winner reaches `manifests.create` and `dispatch`. The test `crosswisePlansNeverQuarantineBothCopies` uses 2 MB files and holds run 1 in its compare on a separate executor thread. It shows:
  - plan 2 is refused with nothing pending and no move recorded;
  - after release, only `b` was moved and `a` still exists.
- **AC-2, met.**
  - The blocker ID is returned by `createUnless` and appears in the message as `"plan execution <id> is not finished"`.
  - `Outcomes.ended` releases the lease and is called exactly once per job, on every terminal path:
    - in a `finally` in `runTask`, after the work and `Settled`;
    - in `cancel`, only when it turned QUEUED into CANCELLED;
    - in `markFailed`, only from QUEUED;
    - in `abandon`.
  - The slot itself is the store's unfinished records, so it frees as soon as the terminal status is stored.
  - Tests cover RUNNING, QUEUED and CANCELLING refusal (the message contains e1, `store.created == [e1]`, no moves), and start-after-COMPLETED, FAILED, CANCELLED, cancel-while-QUEUED and executor rejection.
- **AC-3, met.**
  - `FileChannelExecutionLock` uses `tryLock`: `null` and `OverlappingFileLockException` both answer `HeldElsewhere`, which the service maps to the exact message.
  - `anotherProcessIsExecuting` uses a child JVM holding the lock. It asserts the exact message, no record, no pending task and no quarantined files, then a start succeeds once the probe ends.
  - `lockIsHeldExactlyWhileTheExecutionIsUnfinished` (child JVM probe: HELD, then FREE) and the parameterized fake-lock test (one live lease from start through all three terminal endings, then zero) cover "held from start until terminal".
  - The adapter tests cover shares, idempotent release, folder creation, a file passed as the folder, and a lock held by another process.
- **AC-4, met.**
  - Order: `begin` runs the plan checks, then calls `launch`, which takes the lock and then claims. `thePlanIsCheckedBeforeTheLock` first proves a sound plan reaches the lock (`acquires == 1`), then shows a conflicted plan does not touch it. `thePlansOwnProblemsAreReportedFirst` gets `PLAN_HAS_CONFLICTS` while e1 is RUNNING.
  - No trace on refusal: the held-elsewhere, IOException and claim-blocked tests each assert an empty `store.created` (or only e1) and zero extra live leases.
  - One winner: `twoSimultaneousStartsOneWinner` (one latch, 20 rounds, exactly 1 Success, `created.size()==1`, 1 live lease) plus `exactlyOneCreateUnlessWins` (16 virtual threads).
  - Lock released when cancelled while QUEUED (child-JVM probe FREE) and when rejected by the executor (fake: 0 live).

## Test quality

- The tests assert on behavior: result codes, exact messages, records in the store, files on disk and in quarantine, and the OS lock state seen from a separate JVM. They do not restate the implementation.
- The ordering tests avoid the trap of passing against a stub (learnings L-5): each first shows a sound plan reaching the step under test.
- No `Thread.sleep` was added. The only `Thread.sleep` in `ScannerAutoConfigurationTest` (line 86) predates this ticket. Waiting uses latches, `join(30_000)` and `waitFor(30, SECONDS)`.
- The child processes are bounded: `Holder.close` closes stdin, then `waitFor(30 s)`, with `destroyForcibly` in a `finally`; `tryLock` uses the same pattern.
- The `@AfterEach` cancels every created run, so no locked file blocks `@TempDir` cleanup on Windows.
- **Minor:** the AC-1 scenario says "both executions on separate threads". Plan 2's start happens on the test thread and is refused, so it never gets a thread. That is the behavior the criterion wants, so it is acceptable.

## Design conformance

- `launch` follows the Behavior section step by step:
  - lock `IOException` → `MANIFEST_UNWRITABLE`, with the DFS-051 message;
  - `HeldElsewhere` → exact message;
  - a `RuntimeException` or `Error` around the claim releases the lease;
  - a claim blocker releases the lease;
  - a manifest failure after the claim calls `abandon`.
- `JobRunner.dispatch`, `claim` and `abandon` match the design. `submit` now delegates to `dispatch`.
- `InMemoryJobStore.create` and `createUnless` are both `synchronized`; `update` stays lock-free. A record that turns terminal during the scan may still block, which is a safe refusal, as the design says.
- `FileChannelExecutionLock` matches the design: a static registry keyed by real path, an owner check, shares, an `AtomicBoolean` per lease, and swallowed release errors.
- `PlanExecutionProgress.lease` is non-null-checked and never shown in `snapshot`.
- All 14 checklist dispositions hold in the code. Items 7, 8, 10 and 12 are N/A for good reason.
- The CLAUDE.md line was added under Safety invariants, as worded in design.md.
- **Minor note 1:** on POSIX, if code in this JVM other than this adapter held a lock on `execution.lock`, the `OverlappingFileLockException` path opens and closes a channel on that file. Closing it could release that other code's lock. No product code does this, so this is not a defect.
- **Minor note 2:** a second use-case set in the same JVM is told "another process is executing a plan". This is accepted deviation 3.

## Invariants

- **Never delete the last copy:** strengthened. Cross-plan races inside a process and across processes sharing a manifest folder are closed.
- **Re-verify at execution time:** unchanged. The compare-then-move in `execute` is untouched apart from carrying the lease along.
- **Preview is a pure query:** not touched.
- **Hard links are not duplicates:** not touched (DFS-056 handles later links).
- **No symlink or junction following:** not touched. The lock adapter only creates the folder and one regular file inside it.
- **Empty files excluded:** not touched.
- **No state in singleton fields:** the service's new field is only the `lock` port. The slot lives in the store and the lease in the job record. The adapter's static registry mirrors the JVM's own OS lock table (deviation 3).

## Done-items

All nine verified and ticked in the ticket:

- scenarios automated and passing;
- build green (re-run by me);
- touches respected (diff stat: 23 files, all in touches or the story folder);
- checklist dispositioned (14 of 14);
- no `Thread.sleep` added;
- service fields are only ports, seams and the job runner;
- `ModuleExportsTest` green and no `module-info` change;
- child processes bounded and none left running;
- the CLAUDE.md line is present.
