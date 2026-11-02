# DFS-048: Serve folder summaries and overlap pairs for a finished scan — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) | Level | Timing-sensitive |
|---|---|---|---|---|
| `DFS-048.AC-1` | Every listed summary, in order | `DFS-048.AC-1 Every listed summary, in order` | real server over HTTP | no |
| `DFS-048.AC-1` | Summaries under a folder, with the folder's own summary | `DFS-048.AC-1 Summaries under a folder, with the own summary of that folder` | real server over HTTP | no |
| `DFS-048.AC-2` | Every listed pair, in order | `DFS-048.AC-2 Every listed pair, in order` | real server over HTTP | no |
| `DFS-048.AC-2` | Pairs under a folder keep their other side | `DFS-048.AC-2 Pairs under a folder keep their other side` | real server over HTTP | no |
| `DFS-048.AC-3` | The pair of two folders, recursively, in either order | `DFS-048.AC-3 The pair of two folders, counted recursively, in either order` | real server over HTTP | no |
| `DFS-048.AC-3` | A folder the scan holds nothing for | `DFS-048.AC-3 A folder the scan holds nothing for is a side of zeros` | real server over HTTP | no |
| `DFS-048.AC-4` | Refused pair queries (outline, 8 rows) | `DFS-048.AC-4 Refused pair queries` (parameterized) | real server over HTTP | no |
| `DFS-048.AC-5` | The folder filter follows the scan's roots (outline, 5 rows) | `DFS-048.AC-5 The folder filter follows the roots of the scan` (parameterized) | real server over HTTP | no |
| `DFS-048.AC-5` | Paging (page, past the end) | `DFS-048.AC-5 A page of the ordered list; a page past the end is empty with the same total` | real server over HTTP | no |
| `DFS-048.AC-5` | Paging (INVALID_PAGE rows) | `DFS-048.AC-5 Bad paging is refused` (parameterized) | real server over HTTP | no |
| `DFS-048.AC-6` | Unfinished scans | `DFS-048.AC-6 A scan that is not completed answers 409` (parameterized, 3 endpoints) | real server, queued scan | no |
| `DFS-048.AC-6` | Unknown scans | `DFS-048.AC-6 Unknown or malformed scan IDs answer 404` (parameterized) | real server over HTTP | no |
| `DFS-048.AC-7` | Hard links are not copies through the API | `DFS-048.AC-7 Hard links are not copies through the API` | real server, real hard link (skipped via Assumptions if not creatable) | no |
| `DFS-048.AC-8` | The analysis is computed once per scan | `DFS-048.AC-8 The analysis is computed once for concurrent and later requests` | unit test of `OverlapState` (package `result`), latches | no sleep; waiting seen by bounded poll of thread state, 10 s deadline |
| `DFS-048.AC-9` | The overlaps endpoints are secured (4 rows) | `DFS-048.AC-9 The overlaps endpoints are secured` (parameterized) | real server, raw socket for Host | no |
| `DFS-048.AC-10` | The spec and the generated client | `DFS-048.AC-10 openapi.json describes the three overlaps operations and five schemas, and the responses carry their required properties` | real server over HTTP, then reads `openapi.json` (system property `dfs.openapi.file`) and the generated `overlaps.service.ts`; `OpenApiSpecTest` itself also covers the spec-matches-controllers half | no |
| `DFS-048.AC-11` | Docs describe the overlaps API | `DFS-048.AC-11 CLAUDE.md's server entry and ui.md U3 name the three overlaps endpoints, and U5b names the analysis package and OverlapAnalysis.of` | real server over HTTP (red against the stubs), then reads `CLAUDE.md` and `docs/design/ui.md` from the repository root derived from `dfs.openapi.file`, as DFS-047.AC-10 does; also recorded in `verification.md` | no |

## Fixtures and test configurations

- Folders: `@TempDir` tree T as in the ticket background (Photos/2019: a 300 KB, b 200 KB, u 100 KB; Backup/2019: a, b; Old/Pictures: a; Empty/ with no file), content from `ScanApiTest.bytes` via `ResultApiTest.content`; AC-7 builds T2 (A/x.bin, B/x.bin hard link by `Files.createLink`, C/x.bin copy, 100 KB). Roots are real paths (`toRealPath`).
- Bean overrides: `HoldingExecutorConfiguration` for the QUEUED scan (AC-6).
- Session: `TestServer.start(...)`, `server.login(server.token(output.getOut()))` with `OutputCaptureExtension`.
- Streams: none.

## Notes for the implementer

- Expected `summaries` order: Backup, Backup/2019, Photos, Photos/2019, Old, Old/Pictures, T (DFS-046 order, passed through unchanged).
- `SummaryPage.folder` absent without `under` is accepted as JSON null or missing.
- `NESTED_FOLDERS` message contains "both folders are" for equal folders and "contains" for nested ones; every refusal has a non-empty `message`.
- The AC-6 unknown-scan test first calls `summaries` on a completed scan so it is red against stubs; once implemented that call returns 200.
- AC-9 tests send the same request with cookie and right Host afterwards and expect 200 (reaches the stubs now).
- AC-10 and AC-11 (as DFS-047.AC-9 and AC-10) first call the three endpoints on a completed scan, so they are red against the stubs. AC-10 then checks the operation IDs and the single tag `overlaps` on `GET /api/scans/{id}/summaries`, `/pairs`, `/pair`, the five schemas, every `required` property on real responses (`SummaryPage.folder` taken with `under=<T>/Backup`), and that `server/frontend/src/app/api/services/overlaps.service.ts` holds `class OverlapsService`. AC-11 requires the three paths (`/api/scans/{id}/summaries`, `/pairs`, `/pair` not followed by `s`) in CLAUDE.md's `- `server`:` line and in ui.md `### U3.`, and `us.pochaev.dfs.server.analysis` and `OverlapAnalysis.of` in `### U5b.`.
- AC-8 test constructs `new OverlapState(new ScanResult(List.of(root), List.of(), List.of(), List.of(), 0), fn)` and asserts `assertSame` on the two `OverlapIndex` results and the call count.
