# DFS-019: Wire ScannerFactories — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-019.AC-1` | The factory returns all four use cases | `DFS-019.AC-1 The factory returns all four use cases` |
| `DFS-019.AC-2` | The four ports share one scan state | `DFS-019.AC-2 The four ports share one scan state` |
| `DFS-019.AC-3` | Separate builds do not share state | `DFS-019.AC-3 Separate builds do not share state` |
| `DFS-019.AC-4` | The default adapters read the real disk | `DFS-019.AC-4 The default adapters read the real disk` |
| `DFS-019.AC-4` | A missing location is rejected by the real resolver | `DFS-019.AC-4 A missing location is rejected by the real resolver` |
| `DFS-019.AC-5` | A null executor is a caller bug | `DFS-019.AC-5 A null executor is a caller bug` |
| `DFS-019.AC-6` | Scans run on the caller's executor | `DFS-019.AC-6 Scans run on the caller's executor` |

All in `file-scanner/src/test/java/us/pochaev/dfs/scanner/domain/factory/ScannerFactoriesTest.java`.

## Fakes and fixtures used

- Ports: none faked. The factory wires the real adapters, so AC-2, AC-4 and AC-6 use real temp directories (`@TempDir`).
- Executor: `Runnable::run` (same thread) for AC-1 to AC-4; a test-local holding executor that queues tasks until the test runs them for AC-6.
- Fixture files: generated in code in the `@TempDir`; the hard link is created with `Files.createLink` and the test is skipped through `Assumptions` if the platform refuses.

## Notes for the implementer

- `ArchitectureTest` was edited as design.md specifies (flags removed from the layer and no-Spring rules; the util rule keeps it with a comment). It stays green.
- Result paths are compared by file name only, since the resolver may return real paths.
