# DFS-018: Track scans asynchronously — test plan

## Criterion → scenario → test

Files: `S` = `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/ScanServiceTest.java`, `R` = `.../application/ScanServiceRealThreadTest.java`, `M` = `.../infra/adapter/InMemoryScanRepositoryTest.java`.

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-018.AC-1` | Starting a scan returns an ID while the scan waits in the queue | S: `DFS-018.AC-1 Starting a scan returns an ID while the scan waits in the queue` |
| `DFS-018.AC-2` | A scan runs and completes with its result | S: `DFS-018.AC-2 A scan runs, is RUNNING while the pipeline works and completes with its result` |
| `DFS-018.AC-3` | An invalid start is rejected before anything is queued (4 examples) | S: `DFS-018.AC-3 An empty location list is rejected before anything is queued`; `... A missing location is rejected ...`; `... A location that is a file is rejected ...`; `... A good location followed by a missing one is rejected ...` |
| `DFS-018.AC-4` | The executor rejects the task | S: `DFS-018.AC-4 An executor that rejects the task fails the start and the scan record` |
| `DFS-018.AC-5` | The snapshot follows the pipeline's progress | S: `DFS-018.AC-5 The snapshot follows the pipeline's progress` |
| `DFS-018.AC-6` | A listener receives every event on the executor's thread | S: `DFS-018.AC-6 A listener receives every event in order, on the thread that runs the task` |
| `DFS-018.AC-6` | The overload without a listener runs the same scan | S: `DFS-018.AC-6 The overload without a listener runs the same scan` |
| `DFS-018.AC-7` | Cancelling a queued scan cancels it at once and it never walks | S: `DFS-018.AC-7 Cancelling a queued scan cancels it at once and it never runs the pipeline` |
| `DFS-018.AC-8` | Cancelling a running scan goes through CANCELLING | S: `DFS-018.AC-8 Cancelling a running scan goes through CANCELLING to CANCELLED` |
| `DFS-018.AC-9` | Cancelling a scan that is already stopping or finished changes nothing (CANCELLING, COMPLETED, CANCELLED, FAILED) | S: `DFS-018.AC-9 Cancelling a scan that is already stopping or finished changes nothing` (parameterized, 4 invocations named `DFS-018.AC-9 Cancelling a {0} scan changes nothing`) |
| `DFS-018.AC-10` | Completion wins over a cancel request that lost the race | S: `DFS-018.AC-10 Completion wins over a cancel request that arrived first` |
| `DFS-018.AC-10` | A cancel request after completion is a no-op | S: `DFS-018.AC-10 A cancel request after completion is a no-op` |
| `DFS-018.AC-11` | An unknown scan ID is a failure, not an exception (3 operations) | S: three tests `DFS-018.AC-11 GetScanStatus / CancelScan / GetScanResult fails with UNKNOWN_SCAN ...` |
| `DFS-018.AC-12` | The result is only available once the scan completed | S: `DFS-018.AC-12 The result is only available once the scan completed` (parameterized: QUEUED, RUNNING, CANCELLING, CANCELLED, FAILED; invocations named `DFS-018.AC-12 The result is not available while the scan is {0}`) |
| `DFS-018.AC-13` | A pipeline failure makes the scan FAILED with the reason (2 examples) | S: `DFS-018.AC-13 A pipeline IOException makes ...`; `DFS-018.AC-13 A pipeline RuntimeException makes ...` |
| `DFS-018.AC-14` | An Error still leaves the scan FAILED | S: `DFS-018.AC-14 An Error leaves the scan FAILED and still propagates out of the task` |
| `DFS-018.AC-15` | A throwing listener is detached and the scan carries on | S: `DFS-018.AC-15 A throwing listener is detached and the scan carries on` |
| `DFS-018.AC-16` | A null argument is a caller bug (7 rows) | S: seven tests `DFS-018.AC-16 StartScan with null locations / a null location element / a null config / a null listener; GetScanStatus / CancelScan / GetScanResult with a null ScanId ...` |
| `DFS-018.AC-17` | Two concurrent scans on real threads and real files do not share state | R: `DFS-018.AC-17 Two concurrent scans on real threads and real files do not share state` |
| `DFS-018.AC-18` | Concurrent updates to one scan are never lost | M: `DFS-018.AC-18 Concurrent updates to one scan are never lost` |

## Fakes and fixtures used

- `ScanRunner`: `ScriptedRunner` (in `ScanServiceTest`): emits chosen events, blocks on a latch after N events, returns a chosen result or throws a chosen `Throwable`, and captures the stop signal it was given.
- `LocationResolver`: `FakeResolver`: resolves a path to itself; `missing` throws `NoSuchFileException`, `a/file.txt` throws `NotDirectoryException`.
- `ScanRepository`: `ListingRepository`, a `ConcurrentHashMap` fake exposing `records()` (the port has no listing method), used by AC-3, AC-4, AC-16.
- Executor: `ManualExecutor` (queues tasks, `runNext()`, can be told to reject) for every `application` test; a real virtual-thread executor only in `ScanServiceRealThreadTest` (AC-17), where each submitted task's `Future` is awaited for at most 10 seconds.
- Blocked-state tests run `runNext()` on a thread the test creates and wait on latches with 10 second bounds. No `Thread.sleep`.
- Fixture files: AC-17 writes four small files in code into a `@TempDir` (expected paths from `toRealPath()`); no shared fixtures are needed.

## Notes for the implementer

- Tests call the package-private `ScanService(LocationResolver, ScanRunner, ScanRepository, Executor)` constructor from the `application` test package; AC-17 uses the public `ScanPipeline` constructor.
- AC-8 reads the stop signal the runner received: false before `cancel`, true after, and still true after the scan is CANCELLED.
- AC-12 checks the failure message contains the status name (`QUEUED`, `RUNNING`, `CANCELLING`, `CANCELLED`, `FAILED`).
- AC-15: the listener throws on its 2nd event; the runner emits 4; the test expects the listener saw exactly 2 and the snapshot still counts all files walked (3).
- AC-16 asserts nothing was submitted and no record was created.
- `ErrorCode.LOCATION_UNREADABLE` has no scenario and no test (per the approved design).
