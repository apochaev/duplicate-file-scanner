# DFS-061: Skip known operating-system folders in scans — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-061.AC-1`, `DFS-061.AC-2` | A recycle bin copy is not reported as a duplicate | `SystemFolderEndToEndTest`: DFS-061.AC-1, DFS-061.AC-2 A recycle bin copy is not a duplicate and the folder is skipped once; `ScanCommandTest`: DFS-061.AC-1, DFS-061.AC-2 A recycle bin copy is not reported as a duplicate |
| `DFS-061.AC-2` | The text report lists the skipped folder | `ScanCommandTest`: DFS-061.AC-2 The text report lists the skipped folder |
| `DFS-061.AC-2` | Files under a skipped folder are not counted as found | `ScanCommandTest`: DFS-061.AC-2 Files under a skipped folder are not counted as found; `SystemFolderEndToEndTest`: same title; `SystemFolderWalkerTest`: DFS-061.AC-2 A skipped folder is not entered, so it is not counted as a directory |
| `DFS-061.AC-1` | Each known name is skipped | `SystemFolderWalkerTest`: DFS-061.AC-1, DFS-061.AC-2 Each known name is skipped (13 rows); name table: DFS-061.AC-1 matches the known name |
| `DFS-061.AC-1` | A known folder is skipped at any depth | `SystemFolderWalkerTest`: DFS-061.AC-1 A known folder is skipped at any depth; `ScanCommandTest`: DFS-061.AC-1 A known folder is skipped at any depth and look-alikes are scanned |
| `DFS-061.AC-1` | Windows names match in any letter case | `SystemFolderWalkerTest`: DFS-061.AC-1 Windows names match in any letter case |
| `DFS-061.AC-1` | Names that only resemble a known folder are scanned | `SystemFolderWalkerTest`: DFS-061.AC-1 Names that only resemble a known folder are scanned; DFS-061.AC-1 does not match the look-alike |
| `DFS-061.AC-3` | With --no-default-excludes the folders are scanned | `ScanCommandTest`: DFS-061.AC-3 With --no-default-excludes the folders are scanned; `SystemFolderWalkerTest`: DFS-061.AC-3 With the switch off the folders are walked like any other |
| `DFS-061.AC-3` | The core setting reaches the walker | `SystemFolderEndToEndTest`: DFS-061.AC-3 The core setting reaches the walker: default excludes off scans the folders; DFS-061.AC-3 ScanConfig.defaults has default excludes on and the folders skipped |
| `DFS-061.AC-4` | A location inside a system folder is walked | `SystemFolderWalkerTest` and `ScanCommandTest`: DFS-061.AC-4 A location inside a system folder is walked / A start inside a system folder is walked |
| `DFS-061.AC-4` | A location named like a system folder is walked, folders below still skipped | `SystemFolderWalkerTest` and `ScanCommandTest`: DFS-061.AC-4 A location named like a system folder is walked, folders below it are skipped |
| `DFS-061.AC-5` | The quarantine folder is skipped with or without the switch | `SystemFolderWalkerTest`: DFS-061.AC-5 The quarantine folder is skipped as QUARANTINE with the switch on or off; `SystemFolderEndToEndTest` and `ScanCommandTest`: DFS-061.AC-5 ... keeps its reason |
| `DFS-061.AC-6` | A regular file with a known name is scanned | `SystemFolderWalkerTest`, `ScanCommandTest`: DFS-061.AC-6 A regular file with a known name is scanned |
| `DFS-061.AC-6` | A symbolic link with a known name keeps its link reason | `SystemFolderWalkerTest`, `ScanCommandTest`: DFS-061.AC-6 A symbolic link with a known name keeps its SYMLINK reason (aborts without symlink privilege) |

## Fakes and fixtures used

- Ports: the existing fakes `ScanPipelineTest.FakeFileTreeWalker` and `ScanFolderTotalsTest.FakeWalker` now override `walk(Path, boolean, WalkListener)` (decision 6); no new fake.
- Executor: real virtual-thread executor through `ScannerFactories` in `ScanCommandTest` (as its other scans); none elsewhere.
- Fixture files: written in code into `@TempDir`; the real `NioFileTreeWalker` is used on purpose (the flag must reach it).

## Notes for the implementer

- Every walker test calls the three-argument `walk` explicitly.
- `ScanPipelineTest`/`ScanFolderTotalsTest` fakes were edited only for the signature.
- Deviation: the progress scenario asserts "1 files, 15 B found" (the tree uses the class's 15-byte `DUP` content, not 6 KB).
- Deviation: AC-5 pipeline test sits in the new `SystemFolderEndToEndTest`, not `ScanQuarantineEndToEndTest`.
- The 10 DFS-061 `ScanCommandTest` tests run the scan through `sysScan`, which rethrows the stub text found in stderr/stdout as `UnsupportedOperationException("not implemented: DFS-061")` before any assertion; a no-op once the feature works.
