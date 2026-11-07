# DFS-057: Show the current phase's counts and ETA on the scan screen — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`it` name) | Spec file | Stub it reaches | Level |
|---|---|---|---|---|---|
| `DFS-057.AC-1` | Walking shows what has been found | `DFS-057.AC-1, DFS-057.AC-8 shows what a walk has found and has no total`; `... writes singular counts with plural` | `src/app/scans/scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-1` | Walking shows what has been found | `DFS-057.AC-1 has no view for a scan with no phase` | `src/app/scans/scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-1`, `DFS-057.AC-8` | Walking shows what has been found | `DFS-057.AC-1, DFS-057.AC-8 shows what a walk has found over an indeterminate bar` | `src/app/scans/scan-row.spec.ts` | `phaseView` | component (TestBed) |
| `DFS-057.AC-2` | Comparing samples shows files done and left | `DFS-057.AC-2 shows files done, left and the percentage, and no sample bytes` | `scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-2` | Comparing samples shows files done and left | `DFS-057.AC-2 shows the samples compared, the files left and a bar following files` | `scan-row.spec.ts` | `phaseView` | component (TestBed) |
| `DFS-057.AC-3` | Hashing shows files and bytes done and left | `DFS-057.AC-3, DFS-057.AC-8 shows files and bytes done and left, the percentage following bytes` | `scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-3`, `DFS-057.AC-8` | Hashing shows files and bytes done and left | `DFS-057.AC-3, DFS-057.AC-8 shows files and bytes done and left, a bar following bytes and an ETA` | `scan-row.spec.ts` | `phaseView`, `EtaEstimator.record` | component (TestBed) |
| `DFS-057.AC-4` | Hard links show groups | `DFS-057.AC-4 shows the hard-link groups done of total, ...` | `scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-4` | Hard links show groups | `DFS-057.AC-4 shows the hard-link groups done and a bar following groups` | `scan-row.spec.ts` | `phaseView` | component (TestBed) |
| `DFS-057.AC-5` | The found totals stay in view | `DFS-057.AC-5 keeps the found totals in every phase after WALK` | `scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-5` | The found totals stay in view | `DFS-057.AC-5 keeps the found line after WALK and shows none in WALK` | `scan-row.spec.ts` | `phaseView` | component (TestBed) |
| `DFS-057.AC-5` | Empty totals do not break the line | `DFS-057.AC-5 never divides by zero when a total is 0` | `scan-phase.spec.ts` | `phaseView` | pure function |
| `DFS-057.AC-5` | Empty totals do not break the line | `DFS-057.AC-5 reads 0% and an empty bar when a total is 0` | `scan-row.spec.ts` | `phaseView` | component (TestBed) |
| `DFS-057.AC-6` | The ETA waits, then follows the recent rate | `DFS-057.AC-6 waits, then follows the recent rate`; `... keeps estimating while no bytes are hashed ...` | `src/app/scans/scan-eta.spec.ts` | `EtaEstimator.record` | pure function (fake clock) |
| `DFS-057.AC-6` | The ETA waits, then follows the recent rate | `DFS-057.AC-6 shows "estimating…", then the ETA from the recent rate, and none in the next phase` | `scan-row.spec.ts` | `EtaEstimator.record`, `formatEta` | component (TestBed) |
| `DFS-057.AC-6` | Only the last 30 seconds count | `DFS-057.AC-6 counts only the updates of the last 30 seconds`; row: `DFS-057.AC-6 follows only the last 30 seconds of updates` | `scan-eta.spec.ts`, `scan-row.spec.ts` | `EtaEstimator.record` | pure function; component |
| `DFS-057.AC-6` | The ETA is rounded to a readable time | `DFS-057.AC-6 rounds %s seconds to a readable time` (45, 250, 4800); `DFS-057.AC-6 chooses the unit after rounding` | `scan-eta.spec.ts` | `formatEta` | pure function |
| `DFS-057.AC-6` | A stalled rate and a new phase | `DFS-057.AC-6 reads "estimating…" while the recent rate is 0, and shows no ETA in another phase`; `... starts a new phase afresh`; row: `DFS-057.AC-6 goes back to "estimating…" when no bytes were hashed for 30 seconds` | `scan-eta.spec.ts`, `scan-row.spec.ts` | `EtaEstimator.record` | pure function; component |
| `DFS-057.AC-6`, `DFS-057.AC-7` | (terminal status resets) | `DFS-057.AC-6, DFS-057.AC-7 gives no ETA outside FULL_HASH or for a finished scan` | `scan-eta.spec.ts` | `EtaEstimator.record` | pure function |
| `DFS-057.AC-7` | A finished scan shows its totals as before | `DFS-057.AC-7 shows a COMPLETED scan its totals and "Open results" ...`; `... CANCELLED ...`; `... FAILED ...` | `scan-row.spec.ts` | `EtaEstimator.record` (effect) | component (TestBed) |
| `DFS-057.AC-8` | Walking / Hashing scenarios | covered with AC-1 and AC-3 tests above (`plural`, `formatBytes` output) | `scan-phase.spec.ts`, `scan-row.spec.ts` | `phaseView` | pure function; component |

## Fixtures and fakes

- HTTP: `provideHttpClient()`, `provideHttpClientTesting()`; `http.verify()` after each row test (no request is expected).
- Stub surfacing: `provideStubSurfacing()` and `await settle(fixture)` after each render and stream update.
- Fakes behind tokens: a hand-driven `FakeEventSource` through `EVENT_SOURCE`; `CLOCK` provided as `() => now`, a variable the test moves (no test waits on real time).
- Data builders: `progress()` and `scanEntry()` from `src/app/results/result-test-data.ts`.

## Notes for the implementer

- Selectors: `.scan-phase-line` (whole text exactly the scenario's string), `.scan-eta`, `.scan-found`, `.scan-counts`, `.scan-failure`, `a.open-results`, `mat-progress-bar` read by `MatProgressBarHarness`.
- The ETA text uses U+2026 in "estimating…". `EtaEstimator` is built with the clock, so its spec needs no TestBed; the row specs send updates at fake times via `CLOCK`.
- The estimator's window starts at the first FULL_HASH update (the specs send one with 0 bytes at the phase start), keeps updates with time >= now - 30 s.
- The percent in `phaseView` is unrounded for the bar (`toBeCloseTo`), rounded in the text.
