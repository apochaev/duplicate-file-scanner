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
