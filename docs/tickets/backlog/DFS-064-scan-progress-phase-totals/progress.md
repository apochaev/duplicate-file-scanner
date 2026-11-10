# DFS-064 progress

## Design phase

- Wrote `design.md`; added `PhaseTotals`, `ScanProgress.phases`, `ScanEntry.finishedAt`, and in `ScanRegistry` the `Clock` constructors, `Tracked.finished` and the stubs `sweep()`, `finishedAt(Tracked, String)` and `Counters.phases(ScanStatus)` (all throw `not implemented: DFS-064`).
- Regenerated `server/frontend/openapi.json`; added `phases: []` and `finishedAt: null` to the literals in `server/frontend/src/app/results/result-test-data.ts` and `server/frontend/src/app/scans/scan-screen.spec.ts`. `:server:npmTest` passes (200).
- Red run of `:server:test --continue`: failures only in `ScanApiTest`, `ResultApiTest`, `PlanApiTest`, `OverlapApiTest`, each carrying the stub message; declared `BrowserFlowTest` (e2e only).
- Files touched: `server/src/main/java/us/pochaev/dfs/server/scan/{PhaseTotals,ScanProgress,ScanEntry,ScanRegistry}.java`, `server/frontend/openapi.json`, the two frontend files above, `design.md`, `progress.md`, `learnings.md`. Not committed; the orchestrator commits (expected: `feat(server): ... stubs and spec [DFS-064]`).

## Design gate (orchestrator note)

Design approved without feedback. So AC-5 is read as decision 3 has it: `phases` is in the progress event, `GET /api/scans/{id}` and `GET /api/scans`; `finishedAt` is on `ScanEntry` only (the two GETs), not in the event stream. Write the AC-5 test accordingly.

## Test phase

- Wrote `server/src/test/java/us/pochaev/dfs/server/scan/ScanRegistryPhasesTest.java` (10 invocations, registry with fake ports and clock) and `server/src/test/java/us/pochaev/dfs/server/ScanPhasesApiTest.java` (2 tests over HTTP), and `test-plan.md`.
- Run: 12 of 12 fail, each with `UnsupportedOperationException: not implemented: DFS-064`. Not committed; the orchestrator commits (expected: `test(server): phase totals and finishedAt tests [DFS-064]`).

## Implementation phase

- Implemented `Counters.phases` (volatile immutable entry list, `finished` derived at read), `finishedAt` (compare-and-set stamp) and `sweep()` with a 50 ms daemon scheduler and `@PreDestroy shutdown()` in `ScanRegistry`; edited the `server` entry of `CLAUDE.md`. `./gradlew build` green. Not committed; expected: `feat(server): phase totals and finishedAt [DFS-064]`.

## Verify found a defect (orchestrator note)

AC-2 is not met on a real scan: `ScanRegistry.Counters.onEvent`'s `GroupDone` branch (around line 378) rewrites the open entry as `new PhaseTotals(phase, false, 0, 0, 0, total)` for every phase. The core sends `GroupDone` after each group of every stage (`ScanPipeline.java:196`), so PARTIAL_HASH and FULL_HASH end with `files` 0, `bytes` 0 and a non-zero `groups` (seen by hand: `{"phase":"PARTIAL_HASH","finished":true,"directories":0,"files":0,"bytes":0,"groups":2}`, expected 2 files of 300000 bytes). design.md: only `GroupDone(HARD_LINKS, ...)` sets `groups`, the other fields stay as they were.

For the test-author: add tests that fail on this defect, with a real assertion failure, not a stub message: a registry test whose fake core sends `GroupDone` in PARTIAL_HASH and FULL_HASH after `HashProgress`, in the core's real event order, and checks the hash entries keep their `files` and `bytes` with `groups` 0; and have the real-scan test in `ScanPhasesApiTest` check the hash phases' files and bytes too (fixture of known sizes, at least 2 identical files over the sample threshold). Do not edit src/main. Keep existing tests unchanged.
