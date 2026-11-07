# DFS-057 progress

## Design (architect, 2026-11-07)

- Wrote `design.md` and the stubs: new `server/frontend/src/app/scans/scan-phase.ts` (`phaseView`), new `scan-eta.ts` (`CLOCK`, `EtaEstimator.record`, `formatEta`); changed `scan-row.ts`, `scan-row.html`, `scan-row.css`. Every stub throws `new Error('not implemented: DFS-057')`.
- `./gradlew :server:ngBuild` green; initial total 701.30 kB before, 701.78 kB after.
- `./gradlew :server:npmTest --continue` once: 13 existing tests fail (`scan-screen.spec.ts`, `scan-row.spec.ts`, `app.routes.spec.ts`), all with `not implemented: DFS-057`; Expected breakage: none.
- Expected commit: `feat(server): design detailed scan progress stubs [DFS-057]` (design.md, progress.md, learnings.md, the five frontend files).
