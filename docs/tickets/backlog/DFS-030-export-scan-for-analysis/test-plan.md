# DFS-030: Export a complete scan for analysis — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-030.AC-1` | Nested and repeated locations are recorded once, as walked | `DFS-030.AC-1 Nested and repeated locations are recorded once, as resolved and walked` (ScanFolderTotalsTest) |
| `DFS-030.AC-1` | Several separate locations are recorded sorted | `DFS-030.AC-1 Several separate locations are recorded sorted` (ScanFolderTotalsTest) |
| `DFS-030.AC-2` | Each folder's direct files are totalled | `DFS-030.AC-2 Each folder's direct files are totalled, and a folder with only subfolders has no entry` (ScanFolderTotalsTest); `DFS-030.AC-2 Files are totalled per direct parent folder, sorted by folder path`, `... Nothing counted gives no totals`, `... The order files arrive in does not change the totals` (FolderCounterTest) |
| `DFS-030.AC-2` | Folders without duplicates are totalled too | `DFS-030.AC-2 Folders without duplicates are totalled too` (ScanFolderTotalsTest) |
| `DFS-030.AC-3` | Excluded, too small and empty files are not counted | `DFS-030.AC-3 Excluded, too small and empty files are not counted` (ScanFolderTotalsTest); `DFS-030.AC-3 A file below the minimum size is not counted, one at the minimum is` (FolderCounterTest) |
| `DFS-030.AC-3` | With default excludes off, excluded names are counted | `DFS-030.AC-3 With default excludes off, excluded names are counted` |
| `DFS-030.AC-3` | A path the walk skips is not counted | `DFS-030.AC-3 A path the walk skips is not counted` |
| `DFS-030.AC-4` | A file that fails hashing stays counted | `DFS-030.AC-4 A file that fails hashing stays counted and is also skipped` |
| `DFS-030.AC-5` | Hard links count once per path | `DFS-030.AC-5 Hard links count once per path` (ScanFolderTotalsTest); `DFS-030.AC-5 Two paths of equal size in one folder count as two files` (FolderCounterTest) |
| `DFS-030.AC-6` | FolderTotal rejects invalid values | FolderTotalTest: `DFS-030.AC-6 A folder total keeps the values it is given`, `... rejects a null folder`, `... rejects fewer than one file`, `... rejects negative bytes` |
| `DFS-030.AC-6` | ScanResult rejects nulls and returns immutable lists | ScanResultTest: `DFS-030.AC-6 A scan result rejects null roots` / `null folders` / `a null root` / `a null folder total`; `... The roots and folders lists cannot be changed`; `... Changing the lists passed in leaves the scan result unchanged`; FolderCounterTest: `DFS-030.AC-6 The totals list cannot be changed and a returned list ignores later counting` |
| `DFS-030.AC-7` | The JSONL output starts with the scan header | `DFS-030.AC-7 The JSONL output starts with the scan header`; `... The scan header holds the settings the scan ran with`; `... The scan header times are read before the scan starts and after it finishes` (ScanCommandTest) |
| `DFS-030.AC-8` | Folder lines follow the header | `DFS-030.AC-8 Folder lines follow the header, one per folder total` |
| `DFS-030.AC-9` | Each path in a set carries its modification time | `DFS-030.AC-9 Each path in a set carries its modification time` |
| `DFS-030.AC-10` | Line types come in a fixed order | `DFS-030.AC-10 The line types come in a fixed order and the summary is unchanged` |
| `DFS-030.AC-10` | An empty result still has the header and summary | `DFS-030.AC-10 An empty result still has the header and the summary` |
| `DFS-030.AC-11` | The text report starts with what was scanned | `DFS-030.AC-11 The text report starts with what was scanned` (ScanCommandTest); `DFS-030.AC-11 The report starts with the roots and the times, before the first set`, `... A single root shows no separator` (TextReportTest) |
| `DFS-030.AC-11` | A scan that crosses midnight shows both dates | `DFS-030.AC-11 A scan that crosses midnight shows both dates` (ScanCommandTest); `... shows both dates, and a larger minimum size and excludes off show` (TextReportTest) |

## Fakes and fixtures used

- Pipeline ports (`LocationResolver`, `FileTreeWalker`, `ContentHasher`, `FileIdentity`): hand-written fakes in `ScanFolderTotalsTest`; no real filesystem.
- `StartScan`/`GetScanStatus`/`GetScanResult`: `ScanCommandTest.FakePorts` with a scripted `ScanResult`; a queued `Clock` (`clockReading`) pins the times.
- No fixture files. Windows-style paths such as `D:\Photos` are built with `Path.of`; they are only compared as strings.
- Existing tests updated for the new shapes: `ScanResultTest`, `ScanServiceTest`, `ScanCommandTest` (header and folder lines filtered out of `jsonLines()`, text header skipped by `textBody()`, `copies` helper reads `path`), `TextReportTest`, `CliApplicationTest`.
- `FolderCounterHeapBenchmark`: plain method (not a test), run by hand for decision 10.

## Notes for the implementer

- Expected header order of keys: type, schemaVersion, roots, minSize, sampleSize, partialHashThreshold, defaultExcludes, startedAt, finishedAt (tests compare parsed JSON, so order is not enforced).
- `FolderCounter` is package-private and tested directly; `totals()` must return a snapshot (later `add` does not change a returned list).
- Real-scan tests of other stories (ScanCommandTest, CliApplicationTest, etc.) now fail on the stub through a FAILED scan, as design.md expected.
- The heap benchmark is a plain method because the story gate rejects any disabled test.
