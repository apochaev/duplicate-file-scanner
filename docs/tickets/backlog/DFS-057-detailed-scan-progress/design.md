# DFS-057: Show the current phase's counts and ETA on the scan screen — design

## Summary

Changes the scan row (`ScanRow`, `server/frontend/src/app/scans/`) only; no new screen, route, API call, Material module or browser API. Two new pure helpers beside it, both stubbed: `scan-phase.ts` (`phaseView(progress): PhaseView | null`) and `scan-eta.ts` (`EtaEstimator.record(progress)`, `formatEta(seconds)`, and the `CLOCK` token, `() => number` in ms, default `Date.now()`).

- **AC-1 to AC-4:** `phaseView` returns `{label, detail, found, percent}`; the row shows `<span class="scan-phase">label</span>: detail` in `p.scan-phase-line` and one `mat-progress-bar` (`aria-label="Scan progress"`), indeterminate when `percent` is null (WALK), else determinate with the unrounded `percent`.
- **AC-5:** `phaseView(...).found` ("Found: ..."; null in WALK) in `p.scan-found`; a zero total gives 0% and value 0 (in `phaseView`).
- **AC-6:** `EtaEstimator.record` (30 s window, 3 s warm-up, reset on any non-FULL_HASH update) and `formatEta`; shown in `p.scan-eta`. The row holds one estimator built from `inject(CLOCK)`; an `effect` hands it every progress the row holds.
- **AC-7:** the row's template is unchanged for COMPLETED, FAILED and CANCELLED; the stub reached is `EtaEstimator.record`, which the effect calls for every progress, terminal ones included (it must reset there).
- **AC-8:** `phaseView` formats through the existing `plural` and `formatBytes`.
- Placement: `src/app/scans/` (the ticket's `touches`); specs beside it (`scan-phase.spec.ts`, `scan-eta.spec.ts`, more cases in `scan-row.spec.ts`).
- Not applicable: checklist 1 (no API change), 4 (no new browser API; the clock is a token for testability), 6 (no input), 12 (only for style iteration).
- Look closely at: the `.scan-phase` span is kept holding the phase label, so DFS-041.AC-6's existing assertions (`.scan-phase` text, the 25 % bar) still hold after implementation; a QUEUED scan (phase null) now shows no counts line (before: "0 folders · 0 files · 0 B").
- Bundle: initial total 701.30 kB before, 701.78 kB with the stubs.
- Expected breakage: none. 13 existing tests (`scan-screen.spec.ts`, `scan-row.spec.ts`, `app.routes.spec.ts`) fail at red, all with `not implemented: DFS-057` (the effect reaches `record` on every rendered row).
- Tests read: `scan-screen.spec.ts`, lines 329-361 (DFS-041.AC-6) only, after a grep for the selectors this design replaces: running the tests shows only red failures, not the assertions an implementation would break.

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `src/app/scans/scan-row.ts` | component (changed) | `entry: input.required<ScanEntry>()` (unchanged) | `ScansService`, `ScanEventsService`, `CLOCK` | adds `view = computed(() => phaseView(progress()))`, `eta` signal, an `EtaEstimator`, and an effect recording each progress; drops `hashing`/`hashPercent`/`phaseLabel` |
| `src/app/scans/scan-row.html` | template (changed) | none | none | the unfinished branch shows phase line, ETA, bar, found line; finished branches unchanged |
| `src/app/scans/scan-row.css` | style (changed) | none | none | margins for the new lines; found line in the variant text color |
| `src/app/scans/scan-phase.ts` | helper (new) | none | none | `PhaseView` and `phaseView(progress)` (stub) |
| `src/app/scans/scan-eta.ts` | helper + token (new) | none | none | `CLOCK` token, `EtaEstimator` (`record` stub), `formatEta` (stub) |

## Page states

Only an unfinished row's progress area changes (status QUEUED, RUNNING or CANCELLING); everything else is as DFS-041/DFS-049 left it.

- Not connected: not applicable: the scan screen shows only when connected (DFS-041), unchanged.
- Loading: not applicable: the row renders from the list's `ScanEntry`, unchanged.
- Empty: not applicable: no list change.
- Loaded, phase null (QUEUED): status and folders only; no phase line, bar, ETA or found line.
- WALK: `.scan-phase-line` "Finding files: 1204 folders · 48210 files · 307.3 GB found"; bar `mode="indeterminate"`; no `.scan-found`, no `.scan-eta`.
- PARTIAL_HASH: "Comparing samples: 3100 of 9400 files · 6300 left · 33%"; bar value `hashedFiles / hashFilesTotal * 100`; `.scan-found` "Found: 48210 files · 307.3 GB".
- FULL_HASH: "Hashing files: 1204 of 5880 files · 4676 left · 3.1 GB of 12.4 GB · 9.3 GB left · 25%"; `.scan-eta` "estimating…" (U+2026) or "about 45 s left" / "about 4 min left" / "about 1 h 20 min left"; bar value `hashedBytes / hashBytesTotal * 100`; found line.
- HARD_LINKS: "Checking hard links: group 40 of 310"; bar value `groupsDone / groupsTotal * 100`; found line; no ETA.
- A zero total: the percentage reads "0%" and the bar value is 0.
- Input refused by the form: not applicable: no input.
- Server refusal (`400`): not applicable: no request.
- Server busy (`503`): not applicable: no request.
- Load failed: not applicable: no request.
- Live updates and their end: as DFS-041 (stream per unfinished row, closed on terminal status); every update goes into `progress`, and the effect records it; on a terminal status the row switches to its finished branch (no phase line, bar or ETA, AC-7). "Live progress lost; reload the page" unchanged.

Text rules: the percentage is the bar's value rounded to the nearest whole number (`Math.round`; 24.99999996 → "25%"); the bar gets the unrounded value, so a test compares it with `toBeCloseTo` (AC-3's 25, AC-4's 12.9). Nouns go through `plural(n, noun)` only where the scenarios put one: the walk and found counts, the "of <total> files" and "group"; the done and "left" counts are bare numbers ("0 of 1 file · 1 left · 0%"). The phase line's whole text is `.scan-phase-line`'s `textContent` (no whitespace inside it: the template writes it on one line).

## API calls

None new. The row's existing `ScansService.cancelScan` and `ScanEventsService.progress` are unchanged; `ScanProgress` already carries every field (DFS-040 decision 3).

## Browser APIs behind wrappers

None. The clock is not a jsdom gap, but it is still a token, `CLOCK` (`InjectionToken<() => number>`, `providedIn: 'root'`, default `() => Date.now()`), so a spec provides `{ provide: CLOCK, useValue: () => now }` and moves `now` itself; no test waits on real time. `EtaEstimator` takes the clock in its constructor, so its spec needs no TestBed.

## Material components

`MatProgressBarModule` (already imported by `ScanRow`), now also in indeterminate mode; tests read it with `MatProgressBarHarness` (`getMode()`, `getValue()`). No new module. Initial total 701.30 kB before, 701.78 kB after the stubs (`./gradlew :server:ngBuild`), far below the 1 MB warning.

## ETA rule (AC-6)

`record(progress)` reads `now()` once per call:
- Not FULL_HASH (another phase, null phase, or a terminal status): empty the window, return null.
- FULL_HASH: if the window is empty, this update starts the phase (`phaseStart = now`). Append `{time: now, bytes: hashedBytes}`, drop entries with `time < now - 30000` (an entry exactly 30 s old stays: the "last 30 s" scenario uses the 30 s and 60 s updates). If `now - phaseStart < 3000` or `hashedBytes === 0`, return "estimating…". Rate = (last.bytes - first.bytes) / ((last.time - first.time) / 1000) over the window; a window of one entry or a rate ≤ 0 gives "estimating…". Else `formatEta((hashBytesTotal - hashedBytes) / rate)`.
- `formatEta(s)`: round to whole seconds; under 60 → "about <s> s left"; else round to whole minutes; under 60 → "about <m> min left"; else "about <h> h <m> min left" from the rounded minutes (`h = floor(m / 60)`, `m % 60`).

## Checklist disposition

1. The generated client: not applicable: no service call added or changed; `src/app/api/` untouched.
2. Promise timing in tests: every story spec that renders `ScanRow` uses `settle(fixture)` after each stream update and clock move; the pure-helper specs need none.
3. Stubs reachable and surfaced: AC-1 to AC-5 and AC-8 reach `phaseView` (via the `view` computed, read in the unfinished branch); AC-6 reaches `EtaEstimator.record` and `formatEta`; AC-7 reaches `record` through the effect (it runs for every progress, in every status). Row specs use `provideStubSurfacing()` (an effect's error goes to the `ErrorHandler`). Running `:server:npmTest` once: 13 existing tests fail, all with `not implemented: DFS-057`.
4. Browser APIs jsdom lacks: not applicable: none used; the clock is a token for testability only.
5. Where the screen sits and when it shows: unchanged: inside each `app-scan-row` of the scan screen, only while the scan is unfinished; no route.
6. Input and validation: not applicable: display only.
7. Loading, empty and error states: unchanged; the only new "state" is a zero total (0%, empty bar) and the null phase (no phase line).
8. Live updates: unchanged stream handling (DFS-041); every update lands in `progress`, which drives both `view` and the estimator; a new phase or a terminal status resets the ETA window.
9. Material components and their harnesses: `mat-progress-bar` only, via `MatProgressBarHarness`.
10. The bundle budget and the system font: +0.48 kB initial (701.30 → 701.78 kB), no new module; no font set.
11. The live check reads the DOM: the verifier scans a several-GB folder on the built jar and reads `.scan-phase-line`, `.scan-eta`, `.scan-found` and the bar's `aria-valuenow` from the DOM in each phase (the ticket's manual done-item).
12. The dev server and its proxy: not applicable: needed only for iterating on styles; the change adds two small rules.
13. Docs: `CLAUDE.md`'s scan screen sentence (in the `server` entry) and `docs/design/ui.md` U2 describe the phase lines, the found line and the ETA rule (the implementer's task).

## Expected breakage

None.

## Open design notes (as of the design phase)

- The phase line is split into `label` and `detail` so the existing `.scan-phase` span keeps holding the label; DFS-041.AC-6's assertions then hold after implementation without editing that spec.
- The ETA sits on its own line (`p.scan-eta`) under the phase line rather than appended to it, so the phase line's text is exactly the scenario's string.
- A QUEUED scan (phase null) shows no counts line; before, it showed "0 folders · 0 files · 0 B". The ticket names no line for it.
- The bar's `aria-label` is now "Scan progress" (it was "Hashing progress"; the bar now shows in every phase).
- The estimator is fed by an `effect` rather than in the stream's `next` callback: an error thrown in an RxJS `next` is rethrown from a timer, outside Angular's `ErrorHandler`, so a stub would not surface. Consequence: two updates arriving between change detections are recorded once, at the later one's time; updates are at least 250 ms apart and each schedules a change detection.
- The window starts at the first FULL_HASH update the row received: a page opened mid-phase estimates from then on.

## Stubs (as of the design phase)

- Files stubbed: `scan-phase.ts` (`phaseView`), `scan-eta.ts` (`EtaEstimator.record`, `formatEta`); `scan-row.ts`/`.html`/`.css` wired to them.
- Each method a criterion goes through throws `new Error('not implemented: DFS-057')`; which stub each criterion reaches: AC-1, AC-2, AC-3, AC-4, AC-5, AC-8: `phaseView`; AC-3 (its ETA) and AC-6: `EtaEstimator.record`, `formatEta`; AC-7: `EtaEstimator.record` (via the row's effect).
- The template's structure and CSS classes are final (`.scan-phase-line`, `.scan-phase`, `.scan-eta`, `.scan-found`, `mat-progress-bar[aria-label="Scan progress"]`); tests find elements by them and by `MatProgressBarHarness`.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: to be filled in after the design commit.
