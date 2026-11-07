# DFS-057 progress

## Design (architect, 2026-11-07)

- Wrote `design.md` and the stubs: new `server/frontend/src/app/scans/scan-phase.ts` (`phaseView`), new `scan-eta.ts` (`CLOCK`, `EtaEstimator.record`, `formatEta`); changed `scan-row.ts`, `scan-row.html`, `scan-row.css`. Every stub throws `new Error('not implemented: DFS-057')`.
- `./gradlew :server:ngBuild` green; initial total 701.30 kB before, 701.78 kB after.
- `./gradlew :server:npmTest --continue` once: 13 existing tests fail (`scan-screen.spec.ts`, `scan-row.spec.ts`, `app.routes.spec.ts`), all with `not implemented: DFS-057`; Expected breakage: none.
- Expected commit: `feat(server): design detailed scan progress stubs [DFS-057]` (design.md, progress.md, learnings.md, the five frontend files).

## Test phase

Test-author: wrote scan-phase.spec.ts, scan-eta.spec.ts and a "ScanRow phase lines" describe in scan-row.spec.ts, plus test-plan.md. All DFS-057 tests fail with "not implemented: DFS-057". Expected commit: test(server): add DFS-057 specs [DFS-057].

## Implement phase
Implemented `phaseView` (scan-phase.ts), `EtaEstimator.record` and `formatEta` (scan-eta.ts) per design.md; updated CLAUDE.md and docs/design/ui.md U2. Expected commit: `feat(server): show phase counts and ETA on the scan row [DFS-057]`.
