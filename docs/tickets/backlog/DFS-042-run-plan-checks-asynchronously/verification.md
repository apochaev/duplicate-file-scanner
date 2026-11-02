# DFS-042: Run plan checks asynchronously with status and cancel — verification

## Product owner review

### What you can do now

The scanner library can now run DFS-034's content check of a deletion plan in the background: start it, get an ID at once, check on its progress (entries and bytes done), cancel it at any time, and fetch the report when it has finished. A Spring app (the server, later) gets all of this as beans, on its own thread pool, separate from scans. Nothing is visible in the CLI or UI yet; the server endpoints are a frontend-lane ticket.

### Acceptance criteria

- **DFS-042.AC-1** met: a started job is QUEUED with an ID before anything runs, RUNNING once the pool picks it up, COMPLETED when the check ends.
- **DFS-042.AC-2** met: the finished job's report is exactly equal to the report the synchronous check gives for the same plan; counts show everything done.
- **DFS-042.AC-3** met: the status follows the check mid-run (1 entry, 1048581 bytes), and a listener gets every progress and entry event in order.
- **DFS-042.AC-4** met: cancelling a waiting job ends it at once; when the pool runs it, no file is touched; the report is empty and marked cancelled.
- **DFS-042.AC-5** met: cancelling a running job makes it CANCELLING; the check reads no further chunk and ends CANCELLED with only the finished checks.
- **DFS-042.AC-6** met: cancel on a cancelling or finished job changes nothing; both orders of the completion-versus-cancel race are tested and each ends in one terminal status.
- **DFS-042.AC-7** met: asking for the result of an unfinished or failed job is refused with a message naming the status.
- **DFS-042.AC-8** met: an unknown ID is refused by cancel, status and result alike, with the ID in the message.
- **DFS-042.AC-9** met: a pool that refuses the job gives an "executor rejected" failure, and the recorded job is FAILED with the reason.
- **DFS-042.AC-10** met: a bug inside the check fails the job with the exception's class and message; a listener that throws is cut off and the job still completes, the report noting why.
- **DFS-042.AC-11** met: a missing plan or listener is refused before any job is recorded; an empty plan completes with no checks.
- **DFS-042.AC-12** met: two jobs keep their own status, counts and reports.
- **DFS-042.AC-13** met: the factory gives working ports; the Spring configuration provides the pool and the five beans, and an app's own pool or use-case set replaces the default.
- **DFS-042.AC-14** met: on real threads over real files the job completes with two IDENTICAL checks and no file's name, size or modified time changes.

### Try it

Nothing user-visible yet (library and Spring wiring only). Run the tests:

```
./gradlew :file-scanner:test --tests "*PlanVerify*" --tests "*InMemoryJobStore*"
./gradlew :scanner-spring:test
```

Look for 22 tests in `PlanVerifyServiceTest`, 1 in `PlanVerifyRealThreadTest`, 4 in `InMemoryJobStoreTest`, and 17 in `ScannerAutoConfigurationTest` (3 of them new), all passing.

### Decided without you

- A completion that lands after a cancel ends CANCELLED, with the full report marked `cancelled` true (the scan does the opposite: there, completion wins). Approved at the design gate.
- The background service cuts off a throwing listener itself (instead of DFS-034's built-in cut-off), so the status keeps updating after the listener breaks. Approved at the design gate.
- The job machinery is a package-private `JobRunner` over a generic `Job` record and a generic `JobStore` port with an in-memory adapter; `Job` and `PlanVerifyProgress` are public classes in `application` (not exported) only so the factory can name them.
- A pool that throws anything other than a rejection from `execute` marks the job FAILED and rethrows the exception to the caller.
- The Spring tests wait for a job by polling its status in a tight loop (`Thread.onSpinWait`, capped at 30 seconds) rather than sleeping.

### Known limits

- No server endpoints or event stream yet (frontend lane).
- Finished jobs are never removed; they stay until the process exits (a later ticket, together with scans).
- The scan still uses its own job model; merging it onto the shared machinery is a later refactor.
- The comparator's chunk-boundary cancel case (DFS-034.L-2) can drop at most one fully compared entry from a cancelled job's partial report.
- The `Error` path (the job is marked FAILED and the error rethrown) is implemented but has no test. A pool that throws an `Error` (not an exception) from `execute` would leave the job QUEUED. Both are edge cases outside the criteria.
- The shared `JobRunner`'s rejection message reads "executor rejected the plan verify", so plan execution has to make it generic when it reuses the machinery.

### Evidence

- Gate on implement commit 7f68296: green (551 passed, 3 skipped), disabled, freeze, touches, stubs, traceability (14 criteria, 15 scenarios), consistency, all PASS.
- I re-ran the story tests (`--rerun`): `PlanVerifyServiceTest` 22/22, `PlanVerifyRealThreadTest` 1/1, `InMemoryJobStoreTest` 4/4, `ScannerAutoConfigurationTest` 17/17.
- `git diff e162851..HEAD` is empty for `module-info.java`, `ScanStatus` and `ScanRepository`; no scan port file changed.
- Test sources: `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/PlanVerifyServiceTest.java`, `.../application/PlanVerifyRealThreadTest.java`, `.../infra/adapter/InMemoryJobStoreTest.java`, `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`.

### How it went

- Design: 1 attempt (opus), but resumed once because the first run hit the architect's 25-turn cap before design.md existed; 37 turns in total. The design gate was approved.
- Tests: 1 attempt (sonnet, 20 turns); 30 story tests were red on the stubs.
- Implement: 1 attempt (sonnet, 20 turns), resumed once after an API usage-limit cutoff; no deviations from the design.
- Verify: attempt 1. No defects were recorded and nothing was retried or escalated.

Ready for the user's approval.

## Summary

Verdict: every criterion is met. The story is ready for the human `done` gate.

- DFS-042.AC-1 met: QUEUED before run, RUNNING on pick-up, COMPLETED at the end (manual executor).
- DFS-042.AC-2 met: the report equals the synchronous `VerifyPlanContentService.verify` report (`assertEquals`).
- DFS-042.AC-3 met: mid-run snapshot (1, 1048581) under a latch-blocked comparator; listener events checked for order and totals.
- DFS-042.AC-4 met: the reader and comparator call counts are 0 after a queued cancel; empty cancelled report.
- DFS-042.AC-5 met: exactly 1 chunk of y.bin read; CANCELLED with only x.jpg.
- DFS-042.AC-6 met: the parameterized no-op over 4 statuses, plus both race orders driven through public calls.
- DFS-042.AC-7 met: parameterized over 4 statuses; the message contains the status.
- DFS-042.AC-8 met: all three ports refuse an unknown ID, with the UUID in the message.
- DFS-042.AC-9 met: the recording store finds the FAILED job containing "pool closed".
- DFS-042.AC-10 met: exact failure text; the detached listener was called once, the snapshot kept updating, `listenerFailure` is set.
- DFS-042.AC-11 met: NPE for all 3 null combinations with nothing recorded; the empty plan completes.
- DFS-042.AC-12 met: full snapshot equality for both jobs.
- DFS-042.AC-13 met: factory test plus 3 Spring tests; the existing DFS-031 test edit is as the design required.
- DFS-042.AC-14 met: virtual threads and NIO in a `@TempDir`; latch then `Future.get`; files unchanged.

Test quality: the tests assert on behavior (exact snapshots, reports, call counts, chunk counts), not on implementation detail. Timing is pinned by a manual executor and latches. The only spin-wait is the bounded Spring poll, which the archetype's done-item allows ("a latch, a future or a bounded poll"). It burns a core for the job's few milliseconds; acceptable, not a defect.

Design conformance: every Behavior step, transition and all 14 checklist dispositions match the code. Minor notes, none blocking: the `Error` path has no test, an `Error` from `execute` leaves the job QUEUED, and the shared runner's message says "plan verify".

Invariants: this ticket deletes nothing and moves nothing (read-only, which AC-14 confirms). Link, junction and empty-file handling are inherited unchanged from DFS-034's verify. Last-copy and execution-time re-verification are not affected.
