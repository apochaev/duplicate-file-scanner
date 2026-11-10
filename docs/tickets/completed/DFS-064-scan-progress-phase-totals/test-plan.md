# DFS-064: Keep each scan phase's totals and the finish time in the progress stream — test plan

## Criterion → scenario → test

Registry tests are `ScanRegistryPhasesTest` (package `us.pochaev.dfs.server.scan`, fakes of the four core ports, a fake clock, `registry.sweep()` called directly); HTTP tests are `ScanPhasesApiTest`.

| Criterion | Scenario | Test (`@DisplayName`) | Level | Timing-sensitive |
|---|---|---|---|---|
| `DFS-064.AC-1` | Every byte hashed, hard links not started yet | `DFS-064.AC-1 Every byte hashed: FULL_HASH shows 3 of 3 files and 300 of 300 bytes until HARD_LINKS starts` | registry with fakes | no |
| `DFS-064.AC-2` | Finished phases keep their totals | `DFS-064.AC-2, DFS-064.AC-3 Finished phases keep their totals while the flat fields keep their meaning` | registry with fakes | no |
| `DFS-064.AC-2` | Hash phases keep files and bytes after GroupDone (rework) | `DFS-064.AC-2 GroupDone in a hash phase leaves that phase's files and bytes alone and its groups 0, as the core sends it` (registry, core event order) and `DFS-064.AC-2 A real scan's hash phases keep their files and bytes and have no groups` (HTTP, >= 3 files, >= 3 x 300 KB, groups 0) | registry with fakes; real server over HTTP | no |
| `DFS-064.AC-2` | Only started phases are listed | `DFS-064.AC-2 Only started phases are listed` | registry with fakes | no |
| `DFS-064.AC-2` | A phase that has sent no hash progress yet has totals 0 | `DFS-064.AC-2 A phase that has sent no hash progress yet has totals 0` | registry with fakes | no |
| `DFS-064.AC-2` | A completed scan finishes every entry | `DFS-064.AC-2, DFS-064.AC-4 A completed scan finishes every entry, keeps its totals and lists them on its entry`; and over HTTP `DFS-064.AC-2, DFS-064.AC-4, DFS-064.AC-5 A real scan lists its four phases finished in run order, with one fixed finishedAt` | registry with fakes; real server over HTTP | the HTTP test sleeps 150 ms before re-reading finishedAt (no assertion on the delay itself) |
| `DFS-064.AC-3` | The flat fields are unchanged | `DFS-064.AC-2, DFS-064.AC-3 Finished phases ...` (flat fields after HARD_LINKS starts) and `DFS-064.AC-3 The flat hash counters are the current phase's: ...` | registry with fakes | no |
| `DFS-064.AC-4` | finishedAt is set when the scan ends | `DFS-064.AC-4 finishedAt is stamped when the scan ends, not when someone first reads it, and never changes` | registry with fake clock (10:00, 10:41, 11:30), `sweep()` | no |
| `DFS-064.AC-4` | A running scan has no finishedAt | `DFS-064.AC-4 A running scan has no finishedAt, and neither does a cancelling one` | registry with fakes | no |
| `DFS-064.AC-4` | A scan that ends any other way has a finishedAt (outline: CANCELLED, FAILED) | `DFS-064.AC-4 A scan that ends {0} has status {1} and finishedAt 10:05:00` (parameterized) | registry with fakes | no |
| `DFS-064.AC-5` | A queued scan has an empty phases list and no finishedAt | `DFS-064.AC-5 A queued scan shows phases [] and finishedAt null on GET, on the list and in the first progress event` | real server over HTTP, `HoldingExecutorConfiguration` | no |

## Fixtures and test configurations

- Folders: AC-5 and the real-scan test use `ScanApiTest.fixture` (4 files of 300 KB in 2 folders, 3 identical). The registry tests build no files; they feed `ScanEvent`s to the listener the registry gives the fake `StartScan`.
- Bean overrides: `HoldingExecutorConfiguration` (QUEUED scan for AC-5).
- Session: `TestServer.start(...)`, `server.login(server.token(output.getOut()))` with `OutputCaptureExtension`.
- Streams: the first `data:` line of `/api/scans/{id}/events` read with `HttpClient` and `ofLines()`, 20 s request timeout, then the stream is closed.

## Notes for the implementer

- AC-5 is read per decision 3: `phases` is in the progress event, `GET /api/scans/{id}` and `GET /api/scans`; `finishedAt` is on `ScanEntry` only, so the stream test checks `phases` only.
- Registry tests call the package-private `new ScanRegistry(start, cancel, status, result, Clock)` and `registry.sweep()` and read via `start`, `find`, `list`, `progress`; they expect `finishedAt` stamped at `sweep()` time and not moved by a later clock or read, and `finishedAt` never before `startedAt`.
- `PhaseTotals` components are `(phase, finished, directories, files, bytes, groups)`; the tests expect the HARD_LINKS entry's `groups` to be the `GroupDone` total (1 after `GroupDone(HARD_LINKS,1,1)`), a hash entry's `files`/`bytes` the `HashProgress` totals, other components 0.
- A CANCELLED or FAILED scan leaves its last entry `finished` false (asserted).
- The real-scan HTTP test asserts the WALK entry exactly (2 folders, 4 files, 4 x 300 KB) and the phase order and `finished` flags, nothing about the other phases' numbers.
