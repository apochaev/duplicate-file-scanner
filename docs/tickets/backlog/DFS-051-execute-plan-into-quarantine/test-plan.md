# DFS-051: Execute a deletion plan into quarantine — test plan

Classes: `PlanExecutionServiceTest` (S), `PlanVerifyServiceTest` (V), `PlanExecutionRealThreadTest` (R), `NioQuarantineDeleterTest` (D), `JsonLinesManifestWriterTest` (W), `ScannerAutoConfigurationTest` (C: scanner-spring). Titles below are the text after the ID in each `@DisplayName`.

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-051.AC-1` | A plan on unchanged files moves the candidate into quarantine | S: `DFS-051.AC-1, DFS-051.AC-9, DFS-051.AC-18 A plan on unchanged files moves the candidate into quarantine` |
| `DFS-051.AC-1` | (adapter) mirrored path, deepest anchor, discrimination, rename | D: `DFS-051.AC-1 The quarantine path mirrors the path below the anchor`; `... The deepest anchor that holds the file is the base`; `... Different files and different runs get different quarantine paths`; `... Without an anchor the path stays under a quarantine folder named for the run`; `... A move lands the file at the quarantine path and leaves nothing behind` |
| `DFS-051.AC-2` | A plan with a conflict is refused | S: `DFS-051.AC-2 A plan with a conflict is refused` |
| `DFS-051.AC-3` | A plan from another scan is refused | S: `DFS-051.AC-3 A plan from another scan is refused` |
| `DFS-051.AC-4` | A path that drifted since the scan is skipped (4 examples) | S, parameterized over `Drift`: `DFS-051.AC-4 A path that drifted since the scan is skipped: <case>` |
| `DFS-051.AC-5` | A candidate whose bytes now differ is skipped | S: `DFS-051.AC-5 A candidate whose bytes now differ is skipped` |
| `DFS-051.AC-6` | A candidate that became a symlink is neither read nor moved | S: `DFS-051.AC-6 A candidate that became a symlink is neither read nor moved`; also `... A keeper that became a symlink ...` (AC text names the keeper too). The target is a copy with the same bytes, size and mtime, so following the link would compare identical; the spy comparator must record no call. Skipped via `Assumptions.abort` where a symlink cannot be created. |
| `DFS-051.AC-7` | A move that fails is skipped and the run continues | S: `DFS-051.AC-7 A move that fails is skipped and the run continues`; D: `DFS-051.AC-7 A move onto an existing quarantine path fails ...`, `DFS-051.AC-7 A move of a file that is gone fails` |
| `DFS-051.AC-8` | Every hard link of an operation moves | S: `DFS-051.AC-8 Every hard link of an operation moves and the content is compared once` |
| `DFS-051.AC-8` | A link that fails leaves the operation partly moved | S: `DFS-051.AC-8 A link that fails leaves the operation partly moved` |
| `DFS-051.AC-9` | The moving line is on disk before the rename | S: `DFS-051.AC-9 The moving line is on disk before the rename` |
| `DFS-051.AC-9` | (line order, flush, fields, append) | S: AC-1/9/18 test above; W: `DFS-051.AC-9 Create writes the folder and the manifest with the run line first`, `... Each appended line is readable at once, in order, with its fields`, `... A reopened manifest is appended to, never overwritten` |
| `DFS-051.AC-10` | A manifest folder that cannot be written refuses the run | S: `DFS-051.AC-10 A manifest folder that cannot be written refuses the run`; W: `DFS-051.AC-10 A folder path that is a regular file cannot hold a manifest` |
| `DFS-051.AC-10` | A manifest line that cannot be written stops the run | S: `DFS-051.AC-10 A manifest line that cannot be written stops the run` |
| `DFS-051.AC-11` | A volume that cannot hold the quarantine folder is skipped | S: `DFS-051.AC-11 A volume that cannot hold the quarantine folder is skipped`; D: `DFS-051.AC-11 A quarantine folder that cannot be created is not writable` |
| `DFS-051.AC-12` | Cancelling a queued run moves nothing | S: `DFS-051.AC-12 Cancelling a queued run moves nothing` |
| `DFS-051.AC-12` | Cancelling a running run finishes the operation in progress | S: `DFS-051.AC-12 Cancelling a running run finishes the operation in progress` |
| `DFS-051.AC-13` | A cancel recorded before completion wins | S: `DFS-051.AC-13 A cancel recorded before completion wins` |
| `DFS-051.AC-13` | A cancel after completion changes nothing | S: `DFS-051.AC-13 A cancel after completion changes nothing` |
| `DFS-051.AC-14` | An ID never issued is unknown to every operation | S: `DFS-051.AC-14 An ID never issued is unknown to every operation` |
| `DFS-051.AC-14` | The result is not available before the run ends | S: `DFS-051.AC-14 The result is not available before the run ends` |
| `DFS-051.AC-15` | A rejected task fails the run with a generic message | S: `DFS-051.AC-15 A rejected task fails the run with a generic message`; V: `DFS-051.AC-15 The shared rejection message names no particular use case` |
| `DFS-051.AC-16` | An Error in the work fails the run and is rethrown | S: `DFS-051.AC-16 An Error in the work fails the run and is rethrown` (through `PlanExecutionService`, not a direct `JobRunner` test) |
| `DFS-051.AC-17` | Progress is pushed and pulled from the same events | S: `DFS-051.AC-17 Progress is pushed and pulled from the same events` |
| `DFS-051.AC-17` | A listener that throws is detached and the run continues | S: `DFS-051.AC-17 A listener that throws is detached and the run continues` |
| `DFS-051.AC-18` | The report lists every outcome in plan order | S: `DFS-051.AC-18 The report lists every outcome in plan order` (+ the AC-1 test) |
| `DFS-051.AC-19` | An empty plan completes with an empty report | S: `DFS-051.AC-19 An empty plan completes with an empty report` |
| `DFS-051.AC-19` | A null argument is a caller bug | S: `DFS-051.AC-19 A null argument is a caller bug` (try/fail/catch, not `assertThrows`) |
| `DFS-051.AC-20` | The factory and the Spring beans execute a plan | S: `DFS-051.AC-20 The factory's use cases run a plan through the same ports`; C: `... The autoconfiguration provides the plan execution beans, manifest folder from the property`, `... With no property the manifest folder is under the user home and nothing is created at startup`, `... An app's own planExecutionExecutor replaces the default`, `... An app's own StartPlanExecution bean replaces the default` |
| `DFS-051.AC-21` | End to end on a real executor | R: `DFS-051.AC-21 A plan over two identical files executes end to end on a real executor` |
| `DFS-051.AC-22` | A plan edited after its preview is refused | S: `DFS-051.AC-22 A plan edited after its preview is refused`; `DFS-051.AC-22 PlanIds recomputes the ID the preview gave, for operations and for a conflict` |
| `DFS-051.AC-23` | An operation whose keeper is not a separate copy is refused (3 examples) | S, parameterized over `BadKeeper`: `DFS-051.AC-23 An operation whose keeper is not a separate copy is refused: <OWN_LINK, SAME_PHYSICAL_FILE, IN_NO_SET>` |

Existing test changed: `ScannerAutoConfigurationTest.appExecutorReplacesDefault` expects the executor bean names `{scanExecutor, planVerifyExecutor, planExecutionExecutor}` (design.md "Expected breakage"). That test is not a story test; it fails today on the assertion and passes once the bean set matches... it already matches the autoconfiguration, so it is green now. The existing `PlanVerifyServiceTest` rejection test asserts only the code and is unchanged.

## Fakes and fixtures used

- `FileAttributesReader`, `ContentComparator`, `FileDeleter`, `ManifestWriter`: the real NIO adapters over a `@TempDir`, wrapped in spies. `SpyComparator` records candidates and runs a hook first (latch block for RUNNING/CANCELLING, `OutOfMemoryError` for AC-16). `SpyDeleter` fails or reports not-writable for chosen paths and runs a hook before each move (AC-9 reads the manifest there). `SpyManifests` fails the append of a chosen `ManifestLine` with `IOException("disk full")` (AC-10).
- Job store: `CountingStore` over `InMemoryJobStore`, to prove "no job recorded".
- Executor: `ManualExecutor` (QUEUED; each task runs on its own platform thread, joined, so events arrive off the test thread and the manifest is read only after the task returned). Rejecting executor for AC-15. `Executors.newVirtualThreadPerTaskExecutor()` wrapped to capture the `Future`, bounded at 10 s, for AC-21. No `Thread.sleep`; the Spring tests poll with `LockSupport.parkNanos`.
- Fixture files: generated in code under the `@TempDir` (`lib/A/photo.jpg` and `lib/B/photo.jpg`, 27 bytes, mtime fixed to 2024-03-01T10:00:00Z; docs of 10 and 12 bytes for the multi-operation cases). Plans come from `ScannerFactories.previewDeletionPlan()` so their IDs are valid; AC-22/23 rebuild them, AC-23 and AC-19 compute the new ID with `PlanIds.of`. "Hard links" for AC-8 are declared in the scan as one `PhysicalCopy` over two separate identical files (no OS links needed; the `application` layer never asks the OS). Every `NioQuarantineDeleter` is given the `@TempDir` as its anchor, so nothing is written under a real volume root.

## Notes for the implementer

- Red-gate caveat: `ScannerAutoConfigurationTest.appExecutorReplacesDefault` (its executor-name set updated) is green now because the autoconfiguration already has the bean; it is not a story test.
- The AC-6 tests abort via `Assumptions` where a symlink cannot be created (Windows without the privilege).
- The manifest is parsed in tests with regexes over the field names in `ManifestLine`'s javadoc (`type`, `path`, `quarantine`, `keeper`, `digest`, `size`, `reason`, `subject`, `detail`, `status`, `notAttempted`, `runId`, `planId`); `\uXXXX`, `\\` and `\"` escapes are decoded. Any spacing after the colon is accepted.
- `moving` is expected before the checks (design.md Look closely at 2): AC-4 tests only require that a `skipped` line for the candidate exists and no `moved` line.
- AC-23's message check is only that it contains the set digest (`d-photo`).
- Tests of operation order take it from `plan.operations()`, not from an assumed preview order.
