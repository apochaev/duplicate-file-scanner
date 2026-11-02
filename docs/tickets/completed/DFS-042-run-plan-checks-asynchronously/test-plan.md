# DFS-042: Run plan checks asynchronously with status and cancel — test plan

## Criterion → scenario → test

Files: `A` = `file-scanner/.../application/PlanVerifyServiceTest`, `R` = `.../application/PlanVerifyRealThreadTest`, `S` = `.../infra/adapter/InMemoryJobStoreTest`, `C` = `scanner-spring/.../ScannerAutoConfigurationTest`.

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-042.AC-1`, `DFS-042.AC-2` | A job is queued, runs and completes with the verify's report | A: `DFS-042.AC-1, DFS-042.AC-2 A job is queued, runs and completes with the verify's report` |
| `DFS-042.AC-1`, `DFS-042.AC-3` | Status and listener follow the verify while it runs | A: `DFS-042.AC-1, DFS-042.AC-3 Status and listener follow the verify while it runs` |
| `DFS-042.AC-4` | Cancelling a queued job | A: `DFS-042.AC-4 Cancelling a queued job` |
| `DFS-042.AC-5` | Cancelling a running job stops within one chunk | A: `DFS-042.AC-5 Cancelling a running job stops within one chunk` |
| `DFS-042.AC-6` | Cancel on a job that is cancelling or finished changes nothing (4 examples) | A: `DFS-042.AC-6 Cancel on a <status> job changes nothing` (parameterized) |
| `DFS-042.AC-6` | A completion and a cancel that race (completion first) | A: `DFS-042.AC-6 A completion recorded before the cancel wins` |
| `DFS-042.AC-6` | A completion and a cancel that race (cancel first) | A: `DFS-042.AC-6 A cancel recorded before the completion wins` |
| `DFS-042.AC-6` | (store atomicity behind the transitions) | S: `...Update of an unknown ID applies nothing; creating an ID twice is refused`, `...Null arguments and a null change result throw NullPointerException`, `...No update is lost when many threads update one record` |
| `DFS-042.AC-7` | The result is refused until the job has finished (4 examples) | A: `DFS-042.AC-7 The result of a <status> job is refused` (parameterized) |
| `DFS-042.AC-8` | An ID that was never issued | A: `DFS-042.AC-8 An ID that was never issued` |
| `DFS-042.AC-9` | The executor rejects the task | A: `DFS-042.AC-9 The executor rejects the task` |
| `DFS-042.AC-10` | A bug in the verify fails the job | A: `DFS-042.AC-10 A bug in the verify fails the job` |
| `DFS-042.AC-10` | A throwing listener is detached and the job completes | A: `DFS-042.AC-10 A throwing listener is detached and the job completes` |
| `DFS-042.AC-11` | Null input and an empty plan | A: `DFS-042.AC-11 A null plan or listener throws and records no job`, `DFS-042.AC-11 An empty plan completes with no checks` |
| `DFS-042.AC-12` | Two jobs keep separate state | A: `DFS-042.AC-12 Two jobs keep separate state`; S: `DFS-042.AC-12 Records of different IDs are kept apart, an unknown ID is empty` |
| `DFS-042.AC-13` | Factory and Spring wiring | A: `DFS-042.AC-13 The factory returns working ports`; C: `DFS-042.AC-13 The autoconfiguration provides the plan verify beans, sharing one job store`, `DFS-042.AC-13 An app's own planVerifyExecutor replaces the default`, `DFS-042.AC-13 An app's own PlanVerifyUseCases replaces the default` |
| `DFS-042.AC-14` | End to end on real files | R: `DFS-042.AC-14 A verify on a real executor over real files completes and changes nothing` |

Existing test edited (required by design.md): `ScannerAutoConfigurationTest.appExecutorReplacesDefault` (DFS-031.AC-2) now asserts the ExecutorService bean names are exactly `scanExecutor` and `planVerifyExecutor`.

## Fakes and fixtures used

- `FileAttributesReader`: `FakeReader`, regular files with the plan's sizes (5 and 3145728 bytes), counts its calls.
- `ContentComparator`: `FakeComparator`, 1 MiB chunks, reads `cancelled` before each chunk, can block on a latch after the first chunk of `B/y.bin`, throw `IllegalStateException("boom")` on it, or run a hook after its last chunk (the cancel-first race).
- `JobStore`: `RecordingStore` delegating to `InMemoryJobStore`, exposing the IDs created (AC-9, AC-11 "no job recorded").
- Executor: `ManualExecutor` (queues until `runNext()`); blocked/running cases run `runNext` on a virtual thread, awaited with latches and `join(10s)`; AC-14 uses a real virtual-thread executor and captures each task's `Future`.
- Fixture files: none from `fixtures/`; AC-13 factory and AC-14 write files (5 bytes and 3 MiB, generated in code) into a `@TempDir`. Everything else uses hand-built plans on relative paths that the fake reader knows.
- Spring tests use an empty plan (no file access), wait with a bounded `Thread.onSpinWait` poll on the status (no sleep).

## Notes for the implementer

- The cancel-first race is driven through the public API: the comparator calls `service.cancel(id)` after the last chunk of `y.bin`; the verify then returns a complete report while the job is CANCELLING, and the job must end CANCELLED with `cancelled` true and both checks kept.
- AC-14 waits on the listener latch and then on the task's `Future`; the final status update comes after the last `EntryChecked`.
- The AC-9 assertion finds the recorded job through a recording store (the failure carries no ID).
- `ScannerAutoConfigurationTest.appExecutorReplacesDefault` passes against the stubs already (it is an existing test, edited as design.md requires); it asserts the executor bean names.
