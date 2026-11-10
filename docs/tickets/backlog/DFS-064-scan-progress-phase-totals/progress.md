# DFS-064 progress

## Design phase

- Wrote `design.md`; added `PhaseTotals`, `ScanProgress.phases`, `ScanEntry.finishedAt`, and in `ScanRegistry` the `Clock` constructors, `Tracked.finished` and the stubs `sweep()`, `finishedAt(Tracked, String)` and `Counters.phases(ScanStatus)` (all throw `not implemented: DFS-064`).
- Regenerated `server/frontend/openapi.json`; added `phases: []` and `finishedAt: null` to the literals in `server/frontend/src/app/results/result-test-data.ts` and `server/frontend/src/app/scans/scan-screen.spec.ts`. `:server:npmTest` passes (200).
- Red run of `:server:test --continue`: failures only in `ScanApiTest`, `ResultApiTest`, `PlanApiTest`, `OverlapApiTest`, each carrying the stub message; declared `BrowserFlowTest` (e2e only).
- Files touched: `server/src/main/java/us/pochaev/dfs/server/scan/{PhaseTotals,ScanProgress,ScanEntry,ScanRegistry}.java`, `server/frontend/openapi.json`, the two frontend files above, `design.md`, `progress.md`, `learnings.md`. Not committed; the orchestrator commits (expected: `feat(server): ... stubs and spec [DFS-064]`).

## Design gate (orchestrator note)

Design approved without feedback. So AC-5 is read as decision 3 has it: `phases` is in the progress event, `GET /api/scans/{id}` and `GET /api/scans`; `finishedAt` is on `ScanEntry` only (the two GETs), not in the event stream. Write the AC-5 test accordingly.
