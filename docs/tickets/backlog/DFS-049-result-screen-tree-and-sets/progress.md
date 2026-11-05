# DFS-049 progress

## Design (architect, attempt 1)

- Reviewed the interrupted attempt's uncommitted stubs (`app.routes.ts`, 15 files in `results/`); kept the component split (screen, tree, Sets tab, copy action, clipboard token, path and error helpers), reworked the rest (see `design.md`, "Open design notes") and added `results/result-format.ts`.
- Added `@angular/router` `22.2.4` with `npm install --save-exact` (decision 7): `server/frontend/package.json` and `package-lock.json` changed by that package only.
- Router: `app.routes.ts` (result route lazy via `loadComponent`), `provideRouter(routes, withHashLocation())` in `app.config.ts`, `<router-outlet />` in `app.html`/`app.ts`.
- `scans/scan-row.*`: "Open results" link in the COMPLETED case, stub `resultLink()`.
- Stubs in `results/`: `result-screen.*`, `folder-tree.*`, `sets-tab.*`, `path-copy.*`, `clipboard.ts`, `result-format.ts`, `result-path.ts`, `result-error.ts`; every behavior method throws `new Error('not implemented: DFS-049')`.
- `./gradlew :server:ngBuild` green: initial total 697.14 kB (was about 563 kB; eager result route measured 895.6 kB), lazy `result-screen` chunk 202.37 kB, no budget warning.
- Ran `./gradlew :server:npmTest` to pin the expected breakage: `app.spec.ts` (2 tests, no router in its TestBed) and `scan-screen.spec.ts` (3 tests, `No provider found for ActivatedRoute`); declared in `design.md`.
- Files touched: `server/frontend/package.json`, `server/frontend/package-lock.json`, `server/frontend/src/app/app.config.ts`, `app.routes.ts`, `app.ts`, `app.html`, `scans/scan-row.ts`, `scans/scan-row.html`, `scans/scan-row.css`, `results/*` (16 files), this folder's `design.md`, `progress.md`, `learnings.md`.
- Expected commit: `feat(server): design result screen stubs and hash routes [DFS-049]` (by the orchestrator).

## Tests (test-author, attempt 1)

- Wrote 84 story tests (Vitest) in `server/frontend/src/app/`: `app.routes.spec.ts` (AC-1), `scans/scan-row.spec.ts` (AC-2), `results/result-screen.spec.ts` (AC-3, 4, 5, 6, 8, 9, 10), `results/folder-tree.spec.ts` (AC-5, 6, 10), `results/sets-tab.spec.ts` (AC-7 to AC-11), `results/path-copy.spec.ts` (AC-11), `results/result-path.spec.ts` (AC-8, 11); shared builders and request helpers in `results/result-test-data.ts`.
- Gave `app.spec.ts` (router providers, mock location, initial navigation) and `scans/scan-screen.spec.ts` (`provideRouter([], withHashLocation())`) the providers the router needs. `app.spec.ts` passes; in `scan-screen.spec.ts` the tests that render a COMPLETED row now fail at `ScanRow.resultLink` (the stub), the rest pass.
- `:server:npmTest`: every DFS-049 test fails with `not implemented: DFS-049`; the 21 uncaught exceptions Vitest lists are the same stub error.
- `test-plan.md` written. Expected commit: `test(server): add result screen tests [DFS-049]`.
