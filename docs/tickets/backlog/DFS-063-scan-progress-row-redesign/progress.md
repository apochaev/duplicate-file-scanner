# DFS-063 progress

- Design phase (architect): wrote design.md and stubs in server/frontend/src/app/scans (scan-sections.ts new; scan-format.ts, scan-eta.ts, scan-row.ts/.html/.css changed). `./gradlew :server:ngBuild` passes (initial total 693.37 kB, no budget warning). `:server:npmTest` run once: 31 failures in scan-row, scan-screen, app.routes, scan-eta specs, declared under Expected breakage.
- Expected commit: `feat(server): stub the four-section scan row [DFS-063]` (design.md, progress.md, learnings.md, the six stub files).

- Test phase (test-author): wrote scan-test-data.ts (builders), scan-format.spec.ts, scan-sections.spec.ts (new); rewrote scan-eta.spec.ts, scan-row.spec.ts; updated scan-screen.spec.ts (stub surfacing, new row text); deleted scan-phase.spec.ts. 109 tests fail, all with `not implemented: DFS-063` (plus 37 "Uncaught Exception" entries with the same message, accepted by the gate). test-plan.md written. app.routes.spec.ts unchanged.
- Expected commit: `test(server): scan progress row tests [DFS-063]` (test-plan.md, progress.md, learnings.md, the spec files, scan-test-data.ts, scan-phase.spec.ts deletion).

- Implement phase: filled every stub (scan-sections.ts, scan-format.ts, scan-eta.ts, ScanRow.overallLabel via a linkedSignal hold and duration), wrote scan-row.css (4 kB component budget: icons are CSS glyphs, no budget warning), deleted scan-phase.ts, rewrote the scan-row text in docs/design/ui.md and the CLAUDE.md server entry. Vitest: 269 of 270 pass; the one red test is the AC-15 spec, whose last assertion cannot pass (see learnings.md).
- Expected commit: `feat(server): four-section scan progress row [DFS-063]`.
- Rework (defect 2): added scan-screen.spec.ts 'DFS-063.AC-12 fills the duration and finish time of a scan that completes while the page is open'; red until the screen refetches GET /api/scans/{id} on a terminal status. Expected commit: test(server): completed-while-open duration test [DFS-063].
