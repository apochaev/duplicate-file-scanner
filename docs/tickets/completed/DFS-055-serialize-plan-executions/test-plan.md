# DFS-055: Run plan executions one at a time — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-055.AC-1` | Crosswise plans never quarantine both copies | `DFS-055.AC-1 Crosswise plans never quarantine both copies` |
| `DFS-055.AC-2` | A second start is refused while the first is unfinished | `DFS-055.AC-2 A second start is refused while the first is unfinished`; `DFS-055.AC-2 A second start is also refused while the first is queued or cancelling` |
| `DFS-055.AC-2` | A start right after the first finishes succeeds | `DFS-055.AC-2 A start right after the first is recorded COMPLETED succeeds`; `... FAILED succeeds`; `... CANCELLED succeeds` |
| `DFS-055.AC-2` | A first execution that never ran frees the slot | `DFS-055.AC-2 A run cancelled while queued frees the slot`; `DFS-055.AC-2 A run the executor rejected frees the slot` |
| `DFS-055.AC-2` | (store behind the slot) | `DFS-055.AC-2 createUnless stores the record when no stored record matches`; `... returns the matching record's ID and stores nothing`; `... with no match refuses an ID already taken, and rejects nulls` (`InMemoryJobStoreTest`) |
| `DFS-055.AC-3` | Another process is executing | `DFS-055.AC-3 Another process is executing`; `DFS-055.AC-3 A lock held elsewhere refuses the start with the exact message` (fake lock); adapter: `A lock held by another process is held elsewhere, and free again when that process ends` |
| `DFS-055.AC-3` | The lock is held exactly while the execution is unfinished | `DFS-055.AC-3 The lock is held exactly while the execution is unfinished` (child JVM); `DFS-055.AC-3 The lock is held from the start until the run is recorded {COMPLETED, CANCELLED, FAILED}` (fake lock); adapter tests of shares, release, idempotence, folder creation, IOException (`FileChannelExecutionLockTest`); `DFS-055.AC-3 The plan execution bean holds the manifest folder's lock ...` (`ScannerAutoConfigurationTest`) |
| `DFS-055.AC-4` | The plan's own problems are reported first | `DFS-055.AC-4 The plan's own problems are reported first`; `DFS-055.AC-4 The plan is checked before the lock, even when the lock is held elsewhere` |
| `DFS-055.AC-4` | Two simultaneous starts, one winner | `DFS-055.AC-4 Two simultaneous starts, one winner` (20 rounds, one latch); `DFS-055.AC-4 Of many simultaneous createUnless calls exactly one stores` (`InMemoryJobStoreTest`) |
| `DFS-055.AC-4` | A refused or never-run start leaves the lock free | `DFS-055.AC-4 A refused start leaves the lock held only on behalf of the first run`; `DFS-055.AC-4 A run cancelled while queued releases the lock` (child JVM); `DFS-055.AC-4 A run the executor rejected releases the lock`; `DFS-055.AC-4 A lock that cannot be taken refuses the start and leaves no trace` |

## Fakes and fixtures used

- `ExecutionLock`: `CountingLock` fake counting acquires and live leases, able to answer held elsewhere or throw an `IOException`; the real `FileChannelExecutionLock` where a child JVM probes the file.
- `JobStore`: `CountingStore` over `InMemoryJobStore` (now also overriding `createUnless`); `RecordingStore` in `PlanVerifyServiceTest` likewise.
- Executor: the existing `ManualExecutor`; a rejecting executor for the rejection cases; the comparator's latch (`blockedRig`) holds a run RUNNING.
- Child JVM: `ExecutionLockProbe` (`hold`, `try`), started with a 30 s bounded wait and `destroyForcibly` in a `finally`.
- Fixture files: generated in code under `@TempDir` (27-byte photos; 2 MB generated content for the crosswise scenario). No shared fixtures.

## Notes for the implementer

- Every `Rig` registers itself; an `@AfterEach` cancels every ID in `store.created`, so a QUEUED run's lease is released before `@TempDir` cleanup (a locked file blocks it on Windows).
- The existing DFS-051 execution tests fail against the stub too, since `begin` now delegates to `launch`; they must pass again once `launch` is real.
- `ScannerAutoConfigurationTest` keeps one raw channel open for the whole check, because closing another channel of the same file can release a JVM's locks on POSIX.
- The race test uses the fake lock (both starts get a lease) so the store's `createUnless` decides the winner.
