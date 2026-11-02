# DFS-047: Serve a finished scan's folder tree and duplicate sets - test plan

## Criterion -> scenario -> test

All tests are in `server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java`.

| Criterion | Scenario | Test (`@DisplayName`) | Level | Timing-sensitive |
|---|---|---|---|---|
| `DFS-047.AC-1` | The roots and one level below | `DFS-047.AC-1 The roots, then one level below with subtree and direct totals` | real server over HTTP | no |
| `DFS-047.AC-2` | Folders without counted files do not appear | `DFS-047.AC-2 Folders without counted files do not appear; folders holding only subfolders roll up` | real server over HTTP | no |
| `DFS-047.AC-3` | Every set, largest reclaimable first | `DFS-047.AC-3 Every set, largest reclaimable first, with copies, links and totals` | real server over HTTP | no |
| `DFS-047.AC-3` | Equal reclaimable bytes are ordered by size, then digest | `DFS-047.AC-3 Equal reclaimable bytes are ordered by size, largest first` (digest tiebreak not testable without controlling digests) | real server over HTTP | no |
| `DFS-047.AC-4` | A hard link is not a reclaimable copy | `DFS-047.AC-4 A hard link is not a reclaimable copy` (aborts via Assumptions if no hard links) | real server, real temp files | no |
| `DFS-047.AC-5` | Sets under a folder keep their other copies | `DFS-047.AC-5 Sets under a folder keep their copies outside it` | real server over HTTP | no |
| `DFS-047.AC-5` | Paging | `DFS-047.AC-5 Paging: a page of the ordered list, and a page past the end is empty with the same totals` | real server over HTTP | no |
| `DFS-047.AC-6` | Paths are checked against the scan's roots (outline, 7 rows) | `DFS-047.AC-6 {endpoint} {param} answers {status} {code}` | real server over HTTP | no |
| `DFS-047.AC-7` | Unfinished scans (queued, both endpoints) | `DFS-047.AC-7 GET {endpoint} answers 409 SCAN_NOT_COMPLETED for a queued scan` | real server, HoldingExecutorConfiguration | no |
| `DFS-047.AC-7` | Unknown, malformed ID; bad paging (outline) | `DFS-047.AC-7 GET {scan}{rest} answers {status} {code}` | real server over HTTP | no |
| `DFS-047.AC-8` | The result endpoints are secured (outline) | `DFS-047.AC-8 GET {endpoint} {condition} is refused with 403` | real server over HTTP and raw socket (foreign Host) | no |
| `DFS-047.AC-9` | The spec, the client and the moved error types | `DFS-047.AC-9 Both responses carry every required property of their schema in openapi.json` (responses vs committed schemas); spec equality by `OpenApiSpecTest`; moved types and unchanged `ScanApiTest` by `git diff` in `verification.md` | real server + spec (OpenApiSpecTest) | no |
| `DFS-047.AC-10` | Docs describe the result API | `DFS-047.AC-10 CLAUDE.md's server entry and ui.md U3 and U10 name the two result endpoints` (after real calls to both endpoints on a completed scan, checks that CLAUDE.md's `server` entry contains `GET /api/scans/{id}/folders`, `GET /api/scans/{id}/sets` and, after them, the `403` for paths outside the roots, and that ui.md's U3 and U10 sections contain both paths); the wording is still read in `verification.md` | real server + repository files | no |

## Fixtures and test configurations

- Folders: `@TempDir` trees built in the test, files 100-300 KB generated in code. T (`data/`) is the ticket's background tree (Photos, Backup/2019, Backup/old, an Empty folder); T2 (`small/` three 100 KB copies, `big/` two 200 KB copies); T3 (`x.bin`, hard link `x-link.bin`, `copy/x.bin`). `<T>` is always `toRealPath()`. Photos/a.jpg gets a fixed modified time.
- Bean overrides: `HoldingExecutorConfiguration` for the QUEUED scan (AC-7); none elsewhere.
- Session: `TestServer.start(...)`, `server.login(server.token(output.getOut()))` with `OutputCaptureExtension`. Scans are started through the real scan API and awaited with `ScanApiTest.awaitTerminal`.
- Streams: none.

## Notes for the implementer

- Folder items are checked by `name` order and `path` equal to `Path.toString()` of the real path.
- `modified` is read with `Instant.parse`, so it must serialize as an ISO-8601 string.
- AC-8 tests pair each refusal with the same request carrying the cookie (200), so they fail red against the stubs.
- AC-9's test reads the committed `openapi.json` via the `dfs.openapi.file` system property and checks real responses contain every required property.
- Not tested (no criterion): `ScanRegistry.completedState` memoization, a failed build not cached; see design.md open notes. The digest tiebreak of AC-3 has no deterministic test.
