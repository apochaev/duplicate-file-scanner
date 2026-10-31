# DFS-031: Extract the scanner wiring into a scanner-spring module — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) | Level |
|---|---|---|---|
| `DFS-031.AC-1` | The autoconfiguration provides the four scan ports | `DFS-031.AC-1 The autoconfiguration provides the four scan ports, sharing one scan repository` | Spring context |
| `DFS-031.AC-1` | The autoconfiguration is registered for Spring Boot | `DFS-031.AC-1 The autoconfiguration is registered in AutoConfiguration.imports` | Spring context |
| `DFS-031.AC-2` | The default scan executor uses virtual threads | `DFS-031.AC-2 The default scanExecutor runs tasks on virtual threads` | Spring context |
| `DFS-031.AC-2` | An app's own scanExecutor replaces the default | `DFS-031.AC-2 An app's own scanExecutor replaces the default` | Spring context |
| `DFS-031.AC-3` | An app's own port bean replaces only that port | `DFS-031.AC-3 An app's own port bean replaces only that port` | Spring context |
| `DFS-031.AC-3` | An app's own ScanUseCases backs the four ports | `DFS-031.AC-3 An app's own ScanUseCases backs the four ports` | Spring context |
| `DFS-031.AC-4` | scanner-spring depends only on the scanner's domain | `DFS-031.AC-4 scanner-spring depends only on the scanner's domain` | ArchUnit plus Spring context (runtime check of bean packages) |
| `DFS-031.AC-5` | The CLI scans through the autoconfigured beans | `DFS-031.AC-5 The CLI scans through the autoconfigured beans` | Spring application |

## Fakes and fixtures used

- Ports: real, from the autoconfiguration; the user-supplied `GetScanResult` is a lambda returning `Failure(UNKNOWN_SCAN)`.
- Executor: a `CountingExecutor` (virtual-thread `ThreadPoolExecutor` counting `execute` calls), shut down in `finally`.
- Output capture: `OutputCaptureExtension` for the Spring application (AC-5).
- Files: `@TempDir` folders only (empty for the context tests; a.txt and b.txt with the same 10 bytes for AC-5).

## Notes for the implementer

- Tests touch the context through `getBean` first, so a startup failure surfaces with its root cause.
- The AC-4 test also starts the autoconfiguration and asserts no port bean's class is in `..scanner.application..` or `..scanner.infra..`.
- The AC-5 test also asserts `us.pochaev.dfs.cli.ScannerConfiguration` does not exist.
- The AC-1 shared-repository check: a scan started through `StartScan` is awaited through `GetScanStatus` (polling every 10 ms, 30 s cap) and read through `GetScanResult` and `CancelScan`.
- `scanner-spring/build.gradle.kts` needed no change: ArchUnit comes from the root build's `testImplementation`.
