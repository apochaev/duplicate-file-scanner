# DFS-059: Duplicate totals for folders and sets in the result API — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) | Level | Timing-sensitive |
|---|---|---|---|---|
| `DFS-059.AC-1` | Folders carry their duplicate numbers | `DFS-059.AC-1 Folders carry their duplicate numbers` | real server over HTTP | no |
| `DFS-059.AC-1` | The root and a folder without duplicates | `DFS-059.AC-1 The root, a subfolder, and a folder without duplicates` | real server over HTTP | no |
| `DFS-059.AC-2` | Hard links alone are not duplicates | `DFS-059.AC-2 Hard links alone are not duplicates` | real server, real hard link (skipped where refused) | no |
| `DFS-059.AC-2` | A hard-linked copy with another copy | `DFS-059.AC-2 A hard-linked copy with another copy counts like files counts it` | real server, real hard link (skipped where refused) | no |
| `DFS-059.AC-3` | Set totals over every page | `DFS-059.AC-3 Set totals over every page` | real server over HTTP | no |
| `DFS-059.AC-3` | Set totals under a folder | `DFS-059.AC-3 Set totals under a folder` | real server over HTTP | no |
| `DFS-059.AC-4` | Refusals are unchanged | `DFS-059.AC-4 Refusals are unchanged: 409 for a scan not completed, 400 for a relative path` | real server over HTTP (two servers: queued, then normal) | no |

## Fixtures and test configurations

- Folders: `totalsTree` in `ResultApiTest`, written into a `@TempDir` (A, B, C with deep, D lone file; contents 3000/2000/500/700 bytes). Hard-link tests add E/ or B/song-link.mp3 with `Files.createLink`.
- Bean overrides: `HoldingExecutorConfiguration` for the QUEUED scan in AC-4.
- Session: `TestServer.start(...)`, `server.login(server.token(output.getOut()))` with `OutputCaptureExtension`
- Streams: none.

## Notes for the implementer

- The `409` step of AC-4 passes against the stubs; the test fails red on its `RELATIVE_PATH` step (the index build reaches the stub).
- Existing DFS-047.AC-10 tests the docs; AC-3 of DFS-059 docs are checked by the verifier by reading.
