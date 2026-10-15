# DFS-027: Exclude known system files by default — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-027.AC-1`, `DFS-027.AC-3` | macOS metadata files are left out by default and counted | `DFS-027.AC-1, DFS-027.AC-3 macOS metadata files are left out by default and counted` (ScanCommandTest) |
| `DFS-027.AC-1` | (same, at pipeline level) | `DFS-027.AC-1 By default .DS_Store and ._* files are in no duplicate set and are counted` (ScanPipelineTest) |
| `DFS-027.AC-3` | The text report ends with the excluded count | `DFS-027.AC-3 The text report ends with the excluded count` (ScanCommandTest); `DFS-027.AC-3 The summary line ends with the excluded count`, `DFS-027.AC-3 With nothing excluded the summary ends with 0 excluded` (TextReportTest) |
| `DFS-027.AC-1` | The match is case-sensitive | `DFS-027.AC-1 The match is case-sensitive, so .ds_store and .DS_STORE are scanned` (ScanCommandTest); `DFS-027.AC-1 Excluded names` / `Kept names` (DefaultExcludeFilterTest, parameterized) |
| `DFS-027.AC-1` | Only a name that starts with "._" or is exactly ".DS_Store" matches | `DFS-027.AC-1 Only a name starting with ._ or exactly .DS_Store matches` (ScanCommandTest); `DFS-027.AC-1 The name match is case-sensitive and anchored...` (ScanPipelineTest); `DFS-027.AC-1 Only the file name is matched, never a folder in the path`, `A path with no file name is kept`, `A null entry is rejected` (DefaultExcludeFilterTest) |
| `DFS-027.AC-2`, `DFS-027.AC-3` | --no-default-excludes scans metadata files like any other | `DFS-027.AC-2, DFS-027.AC-3 --no-default-excludes scans metadata files like any other` (ScanCommandTest); `DFS-027.AC-2 --no-default-excludes reaches the scanner as a config with the flag off` |
| `DFS-027.AC-3` | Nothing matched gives an excluded count of 0 | `DFS-027.AC-3 Nothing matched gives an excluded count of 0, in both formats`; `DFS-027.AC-3 The summary reports the excluded count of the scan result`; `DFS-027.AC-3 A scan result carries the excluded count`, `A negative excluded count is rejected` (ScanResultTest); `DFS-027.AC-3 The count grows by one per excluded file...`, `Two filters count independently` (DefaultExcludeFilterTest); `DFS-027.AC-3 The excluded count belongs to one scan...` (ScanPipelineTest) |
| `DFS-027.AC-2` | --no-default-excludes given twice is a usage error | `DFS-027.AC-2 --no-default-excludes given twice is a usage error and starts no scan` (ScanCommandTest) |
| `DFS-027.AC-4` | Excluded files still count as found in the walk progress | `DFS-027.AC-4 Excluded files still count as found in the walk progress` (ScanCommandTest, "6 files, 32.0 KB found"); `DFS-027.AC-4 Excluded files are counted in the walk progress exactly as if they were not excluded` (ScanPipelineTest) |
| `DFS-027.AC-5` | The default configuration has the excludes on | `DFS-027.AC-5 The default config has the default excludes on, matching the filter's own default` (ScanConfigTest); `DFS-027.AC-5 The no-argument filter is enabled, matching DEFAULT_ENABLED` |
| `DFS-027.AC-5` | A configuration with the flag off scans metadata files | `DFS-027.AC-5 With default excludes off, .DS_Store and ._* files are scanned like any other and nothing is counted` (ScanPipelineTest); `DFS-027.AC-5 A disabled filter keeps every entry and counts nothing` (DefaultExcludeFilterTest) |
| `DFS-027.AC-6` | An empty metadata file counts as excluded, not as too small | `DFS-027.AC-6 An empty metadata file is counted as excluded, not dropped as too small` (ScanCommandTest); `... not dropped by the size filter` (ScanPipelineTest); `DFS-027.AC-6 The name decides whatever the size...` (DefaultExcludeFilterTest) |
| `DFS-027.AC-6` | With the excludes off, an empty metadata file is dropped by size | `DFS-027.AC-6 With --no-default-excludes an empty metadata file is dropped by size and counted nowhere` (ScanCommandTest); `DFS-027.AC-6 With default excludes off, an empty metadata file is dropped by size and counted nowhere` (ScanPipelineTest) |

## Fakes and fixtures used

- `ScanPipelineTest`: its existing hand-written fakes (`FakeLocationResolver`, `FakeFileTreeWalker`, `FakeContentHasher`, `FakeFileIdentity`); entries built in memory, digests fixed by file name.
- `DefaultExcludeFilterTest`, `ScanResultTest`: hand-built `FileEntry` values, no I/O.
- `ScanCommandTest`: real scanner through `ScannerFactories.scanUseCases` over a `@TempDir` tree (the ticket's Background, 6 KB / 4 KB files written in code); `FakePorts` (now also records the `ScanConfig` passed to `start`) for the usage and config tests.
- Existing tests updated for the new components: every `new ScanConfig(a, b, c)` gained `, true`, every `new ScanResult(s, k)` gained `, 0`, and the JSONL/text summary assertions gained `"excluded":0` / `, 0 excluded`. `ScanServiceTest`'s static `CONFIG` field became a `config()` method (see Notes).

## Notes for the implementer

- `ScanServiceTest.CONFIG` became a static method `config()` so a stubbed `ScanConfig.defaults()` fails each test on its own instead of the class initializer; behavior unchanged.
- `DefaultExcludeFilter.keep(null)` must throw `NullPointerException`; `ScanResult` must reject `excluded < 0` with `IllegalArgumentException`. Both tests use try/catch, not `assertThrows`.
- AC-4 expects the finished `--progress=log` Walking line to say `6 files, 32.0 KB found` (the Background tree: 4 x 6 KB + 2 x 4 KB).
- The `ScanPipeline` case-sensitivity test puts the six kept files into one set (same size and fake digest); it asserts on names, not set count.
