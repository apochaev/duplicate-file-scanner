# DFS-052: Skip quarantine folders when scanning — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-052.AC-1` | The walker prunes a quarantine folder and reports it | `DFS-052.AC-1 The walker prunes a quarantine folder and reports it` |
| `DFS-052.AC-1` | A deeper quarantine folder is pruned too | `DFS-052.AC-1 A deeper quarantine folder is pruned too` |
| `DFS-052.AC-2` | Only the exact folder name is pruned (.DFS-Quarantine) | `DFS-052.AC-2 A directory named .DFS-Quarantine is walked` |
| `DFS-052.AC-2` | Only the exact folder name is pruned (.dfs-quarantine-old) | `DFS-052.AC-2 A directory named .dfs-quarantine-old is walked` |
| `DFS-052.AC-2` | Only the exact folder name is pruned (regular file) | `DFS-052.AC-2 A regular file named .dfs-quarantine is yielded` |
| `DFS-052.AC-2` | A link named like a quarantine is still a link (symlink) | `DFS-052.AC-2 A symbolic link named .dfs-quarantine is still a symlink and never followed` |
| `DFS-052.AC-2` | A link named like a quarantine is still a link (junction, Windows) | `DFS-052.AC-2 A junction named .dfs-quarantine is still a special file and never followed` |
| `DFS-052.AC-3` | A quarantined copy is not a duplicate | `DFS-052.AC-3 A quarantined copy is not a duplicate` |
| `DFS-052.AC-3` | Turning off default excludes does not bring quarantines back | `DFS-052.AC-3 Turning off default excludes does not bring quarantines back` |
| `DFS-052.AC-4` | A location in a quarantine is refused (4 rows) | `DFS-052.AC-4 <row> is refused` (parameterized: itself, below, second of two, missing below) |
| `DFS-052.AC-4` | Another casing is not a quarantine | `DFS-052.AC-4 Another casing is not a quarantine` |
| `DFS-052.AC-5` | The CLI reports the pruned folder (JSON Lines) | `DFS-052.AC-5 JSON Lines hold a skipped line with reason QUARANTINE and no set line` |
| `DFS-052.AC-5` | The CLI reports the pruned folder (text) | `DFS-052.AC-5 The text report lists the quarantine folder under Skipped and counts it` |
| `DFS-052.AC-5` | The CLI refuses a location in a quarantine | `DFS-052.AC-5 A location in a quarantine is refused with exit 1 and the message on stderr` |
| `DFS-052.AC-6` | The server refuses a location in a quarantine | `DFS-052.AC-6 A location in a quarantine is refused with 400 LOCATION_IN_QUARANTINE and starts no scan` |

Files: `NioFileTreeWalkerQuarantineTest`, `ScanQuarantineEndToEndTest` (infra.adapter), `ScanServiceQuarantineTest` (application), `ScanCommandTest` (appended), `ScanApiTest` (appended).

**Deviation from the ticket text (exit code).** The ticket (decision 3, AC-5, scenario) says the CLI exits 2 for a location in a quarantine. The user decided on exit 1 (`ScanCommand.ERROR`, the code of every refused start, DFS-020.AC-13; 2 is `USAGE`); design.md open note 6. The test asserts `ScanCommand.ERROR`.

## Fakes and fixtures used

- `LocationResolver`: recording fake (`ScanServiceQuarantineTest`) proving no disk lookup on refusal; `ScanRepository`: `ScanServiceTest.ListingRepository`; executor: `ScanServiceTest.ManualExecutor`; runner: `ScanServiceTest.ScriptedRunner`.
- Walker, pipeline, CLI and server tests use real directories in a `@TempDir`; `A/photo.jpg` (2 MB, or the small `DUP` text in the CLI test) and the quarantine copy are generated at test time, nothing committed. Symlink/junction cases use `Fixtures.createSymlinkOrSkip` / `createJunction` (junction only on Windows).
- The AC-4 paths are absolute but never touched on disk.

## Notes for the implementer

- AC-4 "second of two" has a good location first; the refusal must come from one pass before any lookup (resolver must record zero calls), so `LOCATION_IN_QUARANTINE` also beats a missing location.
- The `.DFS-Quarantine` walker test first deletes the Background quarantine, since on a case-insensitive disk they are the same folder.
- The walker's detail is asserted via `NioFileTreeWalker.QUARANTINE_DETAIL`.
