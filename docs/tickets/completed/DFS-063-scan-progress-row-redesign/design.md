# DFS-063: Redesign the scan progress row as one four-section bar — design

## Summary

Changes one component, `ScanRow` (`server/frontend/src/app/scans/`), and its pure helpers; no API change, no new Material module, no browser wrapper. Initial bundle 693.37 kB after the stubs (no budget warning); `MatProgressBarModule` leaves the row, nothing is added.

Criteria and the stub each reaches:
- AC-1 (QUEUED), AC-2 (CANCELLING hold, CANCELLED stopped): `sectionViews`, `stepLine`, `ScanRow.overallLabel` (the hold lives here).
- AC-3 (overall percentage): `overallPercent` through `ScanRow.overallLabel`; caption `overallCaption`.
- AC-4, AC-5, AC-6 (time left, hard links, finishing): `EtaEstimator.record` (whole body stubbed; 30 s warm-up, HARD_LINKS and finishing cases).
- AC-7 (running lines): `runningLines` via `sectionViews`. AC-8 (finished totals): `finishedLines`. AC-9 (not started, not run, stopped counts): `notStartedLines`, `stoppedLines`.
- AC-10 (state as text): `SectionView.tag` from `sectionViews`. AC-11 (failure): `failureLine`, `activityLabel`.
- AC-12 (collapsed row): `collapsedTotals`, `ScanRow.duration`. AC-13: `formatDuration`.
- AC-15 (reduced motion): CSS only; its test renders a walk-running row first so it reaches the `sectionViews` stub, then reads the stylesheet.
- AC-14 is retired (phone layout not built).

Placement: all in `src/app/scans/`. New `scan-sections.ts` (overall percentage, four section views, step line); `scan-format.ts` gains the duration and totals lines; `scan-eta.ts` keeps its API and changes behaviour; `scan-row.*` rebuilt. `scan-phase.ts` stays until the implement phase (see Open design notes). Checklist items marked not applicable: 4, 6, 12 (reasons below).

Expected breakage (the user approves this list): `scan-row.spec.ts`, `scan-screen.spec.ts`, `app.routes.spec.ts`, `scan-eta.spec.ts` (the old DFS-057 row and 3 s ETA assertions; the test-author rewrites the first, second and fourth, and `app.routes.spec.ts` only renders the scan screen).

Existing tests read for the design: `scan-row.spec.ts`, `scan-screen.spec.ts`, `scan-eta.spec.ts`, `scan-phase.spec.ts` (selectors, fake event source and clock they use, to keep `.scan-status`, `.open-results` and the `EtaEstimator` API compatible), and `BrowserFlowTest` was grepped (it uses only `.scan-status.status-completed` and `.open-results`, both kept).

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `scans/scan-row.ts` / `.html` / `.css` | component (rebuilt) | `entry` input (unchanged); no outputs | `ScansService`, `ScanEventsService`, `CLOCK` | Expanded row (step line, overall %, time left, Cancel, strip, four sections, failure line) and collapsed COMPLETED row; only renders helper results and holds the CANCELLING percentage |
| `scans/scan-sections.ts` | helper (new) | `ScanProgress` in, plain values out | none | `SECTIONS` (weights 12/18/60/10), `overallPercent`, `overallCaption`, `sectionViews`, `stepLine`, types `SectionState`, `SectionView` |
| `scans/scan-format.ts` | helper (extended) | plain values | none | adds `formatDuration`, `runningLines`, `finishedLines`, `stoppedLines`, `notStartedLines`, `collapsedTotals`, `activityLabel`, `failureLine` |
| `scans/scan-eta.ts` | helper (behaviour change) | `record(ScanProgress): string \| null` unchanged | clock function | 30 s warm-up (`WARM_UP_MS`), HARD_LINKS groups rate, "finishing…" |
| `scans/scan-phase.ts` | helper (to be removed) | n/a | n/a | DFS-057 phase lines, unused by the row after this ticket |

Template contract (final; tests use these classes). Expanded: `.scan-expanded`, `.scan-status.status-<status>` (status label, kept), `.scan-folders li`, `.scan-step`, `.scan-overall-value`, `.scan-overall-caption`, `.scan-cancelling` ("Cancelling…", only while CANCELLING), `.scan-time-left` (only when the estimator returns text), Cancel `mat-stroked-button` (only QUEUED, RUNNING, CANCELLING; disabled when CANCELLING or after the click), `.scan-strip[role=progressbar]` with four `.scan-segment.segment-<state>` (`.striped`) each holding `.scan-segment-fill`, `ol.scan-sections > li.scan-section.section-<state>[data-phase]` with `.section-title`, `.section-tag`, `.section-line1`, `.section-line2`, `.scan-failure[role=alert]` (FAILED), `.stream-lost`. Collapsed: `.scan-collapsed`, `.scan-started` (contains `.scan-status.status-completed`, "· started HH:mm"), `.scan-strip-thin`, `.scan-totals`, `.scan-overall-value` "100%", `.scan-duration` ("took <duration>"), `.scan-finished` ("finished HH:mm"), `a.open-results`. `<state>` is `done | running | finishing | not-started | not-run | stopped | failed`. A section shows no percentage, only its tag (`done`, `now`, `finishing`, `stopped`, `failed`; none for not started or not run, whose first line says it).

## Page states

- Not connected: not applicable: the row sits inside the scan screen, which the shell shows only when connected (unchanged).
- Loading: not applicable: the row renders from the entry it is given.
- Empty: not applicable: the screen's "No scans in this server run yet" is unchanged.
- QUEUED: four `not-started` sections ("not started" over "total known after step N"; the walk has no second line), `.scan-overall-value` "—", `.scan-step` "Waiting to start", enabled Cancel, no time left.
- RUNNING: step line "Step N of 4 · <label>" (", finishing…" when hashing has no bytes left); percentage; sections as in AC-7 to AC-10; time left in hashing and hard links only.
- CANCELLING: the running section keeps its look, percentage frozen at the last value shown, `.scan-cancelling` "Cancelling…", Cancel disabled.
- CANCELLED / FAILED: stay expanded; the stopped section `stopped` or `failed` with the counts it reached, later sections "not run", percentage captioned "when stopped"; FAILED adds `.scan-failure` "Scan failed while <activity>. <message>".
- COMPLETED: collapsed to the one line of AC-12, no sections, no Cancel.
- Server refusal / busy / load failed: not applicable: the row makes no request except Cancel, whose failure re-enables the button (unchanged).
- Stream lost: `.stream-lost` "Live progress lost; reload the page" as before (an unfinished row, `role="alert"`).

## API calls

Unchanged: `ScansService.cancelScan({ id })` on Cancel, a failure re-enables the button; the progress stream is `ScanEventsService.progress(id)` (the `EVENT_SOURCE` token, event `progress`, data `ScanProgress`). Reads `ScanProgress.phases` (`PhaseTotals`: `phase`, `finished`, `directories`, `files`, `bytes`, `groups`) and `ScanEntry.finishedAt`, both already in the generated models (DFS-064); `src/app/api/` is untouched.

## Browser APIs behind wrappers

None. Reduced motion is a CSS media query; the clock stays behind the existing `CLOCK` token (`scan-eta.ts`).

## Material components

`MatButtonModule` only (Cancel `mat-stroked-button`, Open results `mat-button`); `MatProgressBarModule` is dropped from the row since the strip is plain markup. Tests find elements by the classes above and the button by `MatButtonHarness`. Initial total after the stubs: 693.37 kB (before: not measured separately, the row only lost an import); no budget warning.

## Checklist disposition

1. The generated client: only `cancelScan` as before; the new fields are in the existing models; `src/app/api/` untouched.
2. Promise timing in tests: every story spec uses `provideStubSurfacing()` and `settle(fixture)`.
3. Stubs reachable and surfaced: stubs are template-evaluated computeds and methods (`sectionViews`, `overallLabel`, `duration`, ...) and the existing `effect` that feeds `EtaEstimator.record`, never an RxJS `next` callback. Existing specs that render the row without stub surfacing, or via routes, are listed under Expected breakage; `npmTest` was run once after the stubs compiled (31 failures in 4 files).
4. Browser APIs jsdom lacks: not applicable: none used (CSS media query only).
5. Where the screen sits and when it shows: unchanged, `ScanRow` in the scan screen's list; the "Open results" link keeps `resultLink()` and `#/scans/<id>`.
6. Input and validation: not applicable: the row takes no input.
7. Loading, empty and error states: see Page states; the failure line and the stream-lost alert use `role="alert"`.
8. Live updates: unchanged stream handling (opens only for an unfinished scan, unsubscribes on destroy and on a terminal status). New: the CANCELLING percentage hold and the estimator reset on a phase change.
9. Material components and their harnesses: see above.
10. The bundle budget and the system font: 693.37 kB initial, no budget line in the build output; no `font-family` set; the canvas's Roboto is not loaded.
11. The live check reads the DOM: by hand on the built jar with a scan of several GB of large duplicate files (per the ticket's done-items), text and states read from the DOM, compared with `docs/design/mockups/scan-progress-row-2026-11-08/`; records the bar-stall observation too.
12. The dev server and its proxy: not applicable: no proxy or API change; style iteration may use `npm_run_start` as usual.
13. Docs: `docs/design/ui.md` (U2's description of the DFS-057 lines) and `CLAUDE.md`'s `server` entry are rewritten in the implement phase.

## Expected breakage

- `scan-row.spec.ts`: renders the row, which now reaches the stubbed `sectionViews`, `overallLabel` and `EtaEstimator.record`; its DFS-057 phase-line cases also assert text and selectors (`.scan-phase-line`, `.scan-eta`, `.scan-found`, `mat-progress-bar`) the redesign replaces.
- `scan-screen.spec.ts`: lists rows through the stubbed row; its DFS-041 cases read the old phase text (`.scan-phase`, `MatProgressBarHarness`, "N duplicate sets", "Cancelling" in `.scan-status`).
- `app.routes.spec.ts`: shows the scan screen, whose rows render through the stubs.
- `scan-eta.spec.ts`: `record` is stubbed and its cases assume the 3 s warm-up that becomes 30 s.

## Open design notes (as of the design phase)

- **`scan-phase.ts` and `scan-phase.spec.ts` stay for now.** The ticket says they go if the new helpers replace them; they do, but deleting the module now would break compilation of its spec in the shared Vitest bundle. The test-author deletes `scan-phase.spec.ts` in the test phase; the implementer then deletes `scan-phase.ts` (and `phaseLabel` stays in `scan-format.ts`, used by `sectionViews` titles).
- **"Finishing" at 0 of 0 bytes.** Taken literally from the ticket (`hashedBytes` equal to `hashBytesTotal`), so the first update of a FULL_HASH phase whose totals are still 0 reads "finishing…" until totals arrive; the ticket's "percentage the same on both sides of a phase change" case only checks the percentage. If that flash is unwanted, require `hashBytesTotal > 0`; the test-author should not pin the 0-of-0 case either way.
- **Where the CANCELLING hold lives:** `ScanRow.overallLabel()` (a template-evaluated method), not in `overallPercent`, which stays pure; the implementation picks an `effect` or `linkedSignal` to remember the last value shown.
- **Step line, captions and the "→" lines are decisions the ticket left to the canvas:** step line variants ("Cancelled during step 3 · …", "Failed during step 3 · …") and the captions "of whole scan" / "when stopped" / "total not known yet" come from `Row.dc.html`; a stopped section's `fill` is the frozen progress. The right-hand "ran 7 min 52 s / cancelled 14:10" cell of the canvas is not in the ticket and is not built.

## Stubs (as of the design phase)

- Files stubbed: `scans/scan-sections.ts` (new), `scans/scan-format.ts` (8 new functions), `scans/scan-eta.ts` (`EtaEstimator.record`), `scans/scan-row.ts` (`overallLabel`, `duration`; the computeds call stubbed helpers), `scans/scan-row.html`, `scans/scan-row.css` (placeholder with the class contract).
- Every stub throws `new Error('not implemented: DFS-063')`: `overallPercent`, `overallCaption`, `sectionViews`, `stepLine`, `formatDuration`, `runningLines`, `finishedLines`, `stoppedLines`, `notStartedLines`, `collapsedTotals`, `activityLabel`, `failureLine`, `EtaEstimator.record`, `ScanRow.overallLabel`, `ScanRow.duration`.
- Criterion to stub: AC-1 `sectionViews`/`stepLine`; AC-2 `overallLabel`/`sectionViews`; AC-3 `overallLabel`; AC-4 to AC-6 `EtaEstimator.record`; AC-7 `runningLines`; AC-8 `finishedLines`; AC-9 `notStartedLines`/`stoppedLines`; AC-10 `sectionViews`; AC-11 `failureLine`; AC-12 `collapsedTotals`/`duration`; AC-13 `formatDuration`; AC-15 `sectionViews` (the spec renders a walk row first).
- The template's structure and CSS classes are final; tests find elements by them and by Material harnesses.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: filled in by the orchestrator.
