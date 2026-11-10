# DFS-063 progress

- Design phase (architect): wrote design.md and stubs in server/frontend/src/app/scans (scan-sections.ts new; scan-format.ts, scan-eta.ts, scan-row.ts/.html/.css changed). `./gradlew :server:ngBuild` passes (initial total 693.37 kB, no budget warning). `:server:npmTest` run once: 31 failures in scan-row, scan-screen, app.routes, scan-eta specs, declared under Expected breakage.
- Expected commit: `feat(server): stub the four-section scan row [DFS-063]` (design.md, progress.md, learnings.md, the six stub files).

- Test phase (test-author): wrote scan-test-data.ts (builders), scan-format.spec.ts, scan-sections.spec.ts (new); rewrote scan-eta.spec.ts, scan-row.spec.ts; updated scan-screen.spec.ts (stub surfacing, new row text); deleted scan-phase.spec.ts. 109 tests fail, all with `not implemented: DFS-063` (plus 37 "Uncaught Exception" entries with the same message, accepted by the gate). test-plan.md written. app.routes.spec.ts unchanged.
- Expected commit: `test(server): scan progress row tests [DFS-063]` (test-plan.md, progress.md, learnings.md, the spec files, scan-test-data.ts, scan-phase.spec.ts deletion).
