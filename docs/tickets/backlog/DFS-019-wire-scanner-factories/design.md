# DFS-019: Wire ScannerFactories — design

## Summary

`ScannerFactories.scanUseCases(Executor)` builds a new `ScanService`, wired to the default adapters, and returns it as `ScanUseCases`: a public record in `domain.factory` with four port-typed components. This adds no new use case and changes no behavior. It is pure wiring over DFS-018's `ScanService`. There is no new package and no `module-info.java` change: `domain.factory` is already exported.

- **AC-1** All four components are the same `ScanService` instance, upcast to `StartScan`, `CancelScan`, `GetScanStatus` and `GetScanResult`. `ScanUseCases`'s compact constructor rejects `null` components.
- **AC-2** One `ScanService` means one `InMemoryScanRepository`, so all four ports see the same scan records.
- **AC-3** Each call constructs a new repository, pipeline and service. `ScannerFactories` has no fields.
- **AC-4** The service is wired to `NioLocationResolver`, `NioFileTreeWalker`, `NioContentHasher` and `NioFileIdentity`, with one resolver instance shared by the service and the pipeline. Empty-file exclusion, hard-link detection and `LOCATION_NOT_FOUND` come from DFS-018 and the pipeline unchanged.
- **AC-5** `Objects.requireNonNull(executor, "executor")` runs first, before any object is built.
- **AC-6** The caller's executor goes straight into `ScanService`'s constructor. The factory creates no thread or executor.

**Stubs:** `ScannerFactories.scanUseCases` throws `not implemented: DFS-019`. `ScanUseCases` is a data record and is not stubbed. Its non-null checks are listed for the implementer.

**Not applicable** (no lifecycle or behavior of its own): checklist items 1, 3, 4, 5, 7, 8, 9, 10 and 12. Each inherits DFS-018's design unchanged.

**Look at closely:**
1. The `ArchitectureTest` edit (decision 5) is assigned to the test-author, not done by the architect. I checked it locally: with the two flags removed, `ArchitectureTest` passes, and the edit is then reverted.
2. `ScanUseCases`'s constructor is public, because a record's canonical constructor must be. Callers can therefore assemble their own set, for example from fakes in `cli` tests.

## Public API

- `ScannerFactories` in `domain.factory`: `public static ScanUseCases scanUseCases(Executor executor)`. It has a private constructor and no fields. A `null` executor throws `NullPointerException`. Replaces the empty first-slice class: the method is added and the class Javadoc is reworded.
- `ScanUseCases` in `domain.factory` (new): `public record ScanUseCases(StartScan start, CancelScan cancel, GetScanStatus status, GetScanResult result)`. The implementer adds `Objects.requireNonNull(<component>, "<component>")` for each of the four components to the existing empty compact constructor. No other members.
- Unchanged and reused: `StartScan`, `CancelScan` (in `domain.usecases.command`), `GetScanStatus`, `GetScanResult` (in `domain.usecases.query`), and everything in `domain.model`.

## Layer placement

- `domain.usecases.command` / `domain.usecases.query` (exported): none changed.
- `domain.model` (exported): none.
- `domain.factory` (exported): `ScannerFactories` (changed) and `ScanUseCases` (new). This is the one domain package allowed to depend on `application` and `infra.adapter`. It also needs `infra.port` for the local `LocationResolver` variable, and the layer rule already allows that ("Port" may be accessed by "Factory").
- `application`: none changed. The factory calls `ScanService`'s public production constructor `(LocationResolver, ScanPipeline, ScanRepository, Executor)` and `ScanPipeline`'s public constructor `(LocationResolver, FileTreeWalker, ContentHasher, FileIdentity)`.
- `infra.port` / `infra.adapter`: none changed. The factory calls the implicit public no-arg constructors of `NioLocationResolver`, `NioFileTreeWalker`, `NioContentHasher`, `NioFileIdentity` and `InMemoryScanRepository`.
- Test placement: `file-scanner/src/test/java/us/pochaev/dfs/scanner/domain/factory/ScannerFactoriesTest.java`, which falls under the `domain.factory` package entry in `touches`. The edit to `file-scanner/src/test/java/us/pochaev/dfs/scanner/ArchitectureTest.java` is described under Open design notes.

Implementation sketch for `scanUseCases`, in this order:
1. `requireNonNull(executor)`.
2. Create one `NioLocationResolver`.
3. Create a `ScanPipeline` from that resolver plus a new walker, hasher and identity.
4. Create a `ScanService` from the same resolver, the pipeline, a new `InMemoryScanRepository` and `executor`.
5. Return `new ScanUseCases(service, service, service, service)`.

## State and transitions

None: this use case has no lifecycle. Scan states and transitions are DFS-018's, owned by `ScanService` and unchanged.

## Concurrency and atomicity

- Which thread runs what: `scanUseCases` runs entirely on the caller's thread and only constructs objects. It submits nothing and starts no thread. Scans later run on the caller's `executor`, exactly as DFS-018 designed.
- Where per-request state lives: one `InMemoryScanRepository` per call, owned by that call's `ScanService`. DFS-018's atomic `ScanRepository.update` is unchanged. `ScannerFactories` keeps no state, and the adapters are stateless.
- Races: none introduced. Concurrent calls to `scanUseCases` share nothing, and one `ScanUseCases` may be used from several threads because `ScanService` and `InMemoryScanRepository` already allow it (DFS-018).

## Checklist disposition

1. Unknown or stale IDs: not applicable. The factory takes no ID. AC-3 pins that an ID from another set is `Failure(UNKNOWN_SCAN)`, which is DFS-018's existing answer.
2. Results as values, bugs as exceptions: `scanUseCases(null)` is a caller bug and throws `NullPointerException` (AC-5). There is no overload without an executor, no default executor and no new `ErrorCode`. The factory has no expected failure to return as a value.
3. Status model: not applicable. The factory has no lifecycle, and scan status is DFS-018's.
4. Races and who wins: not applicable. Each call builds disjoint objects, and DFS-018 settles scan races.
5. Validation before side effects: the only check is the `null` executor, done first, before any adapter or service is constructed. Location validation stays in `ScanService.start` (DFS-018).
6. State placement: `ScannerFactories` has no fields. Per-scan state lives in each set's own `InMemoryScanRepository`, never in a static, so sets are independent (AC-3) and several concurrent scans work.
7. Progress and observation: not applicable. The factory reports no progress. A `ScanListener` is passed per scan through `start` (DFS-018).
8. Caller-supplied callbacks: not applicable. The factory calls no caller code. It only stores the executor, and DFS-018 owns the listener contract.
9. Failures off the caller's thread: not applicable. The factory runs nothing on the executor.
10. Query results: not applicable. The factory adds no query.
11. Executor contract: the caller's executor reaches `ScanService` unchanged and unwrapped. The factory creates no executor and never shuts one down. AC-6 pins that a scan's task goes to that executor: the scan is `QUEUED` while the task is held and `COMPLETED` after it runs. Rejection handling stays DFS-018's `Failure(EXECUTOR_REJECTED)`.
12. Retention: not applicable. Scan records are kept for the life of their `ScanUseCases` set (DFS-018 decision 9). Dropping the set makes its repository unreachable.
13. Testability: every AC is testable without timing, through the public factory alone:
    - AC-1 to AC-4 use a same-thread executor (`Runnable::run`).
    - AC-6 uses a test-local executor that queues each `Runnable` in a list until the test runs it. It cannot reuse `application`'s package-private test helpers from `domain.factory`.
    - AC-4's hard link is created with `Files.createLink` at test time and skipped through `Assumptions` when the platform refuses it (CLAUDE.md fixtures rule).
    - No `Thread.sleep`, and no seam is needed.
14. Exported surface: callers see `ScannerFactories.scanUseCases` and `ScanUseCases`, both in the already exported `domain.factory`, and the four ports in the already exported `domain.usecases.*`. No `module-info.java` change, and `ModuleExportsTest` is unaffected. The first-slice `ScannerFactories` is replaced in place. `CancelScan` and `GetScanStatus` are reused unchanged.

## Configuration

- None. `ScanConfig` is passed per scan to `start` (DFS-018). The factory takes only the executor. Secondary-port overrides are deferred (ticket decision 3).

## Open design notes

- **`ArchitectureTest` edit (decision 5) is assigned to the test-author.** It is a change to an existing test file, outside the architect's role. In `file-scanner/src/test/java/us/pochaev/dfs/scanner/ArchitectureTest.java`:
  - Remove `.allowEmptyShould(true)` from `layersRespectDependencyRules` and `coreDoesNotDependOnSpring`.
  - Keep it on `topLevelUtilDoesNotDependOnLayers`, with a one-line comment saying the top-level `util` package holds only `package-info.java`.
  - Reword the class Javadoc's sentence "Rules allow empty matches so they stay green while layers are still unpopulated" to match.

  I checked this during design: with the two flags removed, the test passes against the current classes, because every layer, including Factory, now holds a class. The file was then reverted. The edit adds no AC test and stays green throughout, so it does not affect the red gate.
- `ScanUseCases`'s compact constructor is left without checks, as the archetype prescribes for data records. Its body is an empty block with a comment pointing here, which does not use the `not implemented:` prefix.

## Stubs

- Files stubbed:
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/factory/ScannerFactories.java`: `scanUseCases(Executor)`.
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/factory/ScanUseCases.java`: new data record, not stubbed.
- `scanUseCases` throws `new UnsupportedOperationException("not implemented: DFS-019")` per D2.
- Commit: to be filled in after the design commit.
