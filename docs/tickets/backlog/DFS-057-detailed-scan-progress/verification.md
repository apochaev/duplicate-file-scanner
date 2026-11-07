# DFS-057 verification

## Product owner review

### What you can do now

While a scan runs, its row on the scan screen shows the current phase with real numbers: folders, files and size found while walking; files done and left with a percentage while comparing samples; files and bytes done and left, a percentage and a time-left estimate while hashing; groups while checking hard links. After the walk, a "Found:" line keeps the walk's totals in view.

### Acceptance criteria

- DFS-057.AC-1: met. The walk line and a moving (indeterminate) bar.
- DFS-057.AC-2: met. Samples done of total, left, percentage; no byte count.
- DFS-057.AC-3: met. Files and bytes done and left, percentage by bytes, with the ETA.
- DFS-057.AC-4: met. "Checking hard links: group 40 of 310", bar by groups.
- DFS-057.AC-5: met. "Found:" line after the walk; a zero total reads 0% with an empty bar.
- DFS-057.AC-6: met. ETA from the last 30 s of bytes, "estimating…" for the first 3 s and when stalled, readable rounding, reset on a new phase.
- DFS-057.AC-7: met. Finished, cancelled and failed rows look as before.
- DFS-057.AC-8: met. Counts and sizes in the page's existing format.

### Try it

`./gradlew :server:bootRun`, open the `Open http://127.0.0.1:<port>/?token=...` link, add a folder of several GB with duplicates (fewer, larger files reach the hashing phase sooner), Start. Watch the row: "Finding files: ..." with a moving bar, then "Comparing samples: X of Y files · Z left · N%" with "Found: ..." underneath, then "Hashing files: ..." with "estimating…" changing to "about N min left" after a few seconds, then "Checking hard links: group X of Y". Check that the browser's developer console shows no errors.

### Decided without you

- The ETA sits on its own line under the phase line, not at the end of it.
- A queued scan (no phase yet) shows no counts line; before, it showed "0 folders · 0 files · 0 B".
- The bar's accessible name changed from "Hashing progress" to "Scan progress" (it now shows in every phase).
- The ETA window starts at the first hashing update the page receives: a page opened mid-phase estimates from then on.
- Two updates arriving within one screen refresh are timed once, at the later one (updates are at least 250 ms apart).
- An exact hour reads "about 1 h 0 min left".

### Known limits

- The ETA is optimistic near the end when many small files remain (decision 2; the counts stay exact).
- Finished phases' totals are not kept (DFS-058).
- The verifier's live check saw only the walk and sample phases on a real 2.4 GB, 100 000-file folder; hashing, the ETA settling, hard links and the console check were not observed live. That done-item is unticked.
- `CLAUDE.md` says "Comparing samples" shows bytes; it does not (a one-word fix at close).

### Evidence

- Vitest: `scan-phase.spec.ts` 8, `scan-eta.spec.ts` 10, `scan-row.spec.ts` 14 (12 new), `scan-screen.spec.ts` 15 unchanged; whole frontend 129 passed in 12 files (`./gradlew :server:npmTest --rerun`).
- `./gradlew build` green (up to date against the implement commit); story-gate green, freeze, touches, stubs and traceability passed (`plan.json`, implement run).
- Live DOM log: `C:/Users/apoch/AppData/Local/Temp/claude/02ad29a6de71421f/bf0f7892-5e76-4556-af00-1220b2d226a8/scratchpad/live.log`.

### How it went

Design, tests and implement each passed on the first attempt (architect 37 turns, test-author 24, implementer 11); no defects, retries or escalations. The verifier hit its turn cap during the live check.

Ready for the user's approval; the live done-item is left for the product owner's own run on a real scan.

## Summary

- DFS-057.AC-1: met. Walk line plus indeterminate bar; tested pure and through the row.
- DFS-057.AC-2: met. Files done/left/%, bar by files, no bytes.
- DFS-057.AC-3: met. Files and bytes done/left, % and bar by bytes, ETA shown.
- DFS-057.AC-4: met. Groups line, bar by groups, no ETA.
- DFS-057.AC-5: met. Found line after WALK; zero totals give 0% and an empty bar.
- DFS-057.AC-6: met. 30 s window, 3 s warm-up, stall and phase reset, rounding; fake clock throughout.
- DFS-057.AC-7: met. Terminal branches unchanged; tests for all three statuses.
- DFS-057.AC-8: met. `plural` and `formatBytes`.
- Tests assert on visible output and harness state, not on implementation; no real-time waits.
- Design matched, including every checklist disposition; no safety invariant touched.
- Minor: `CLAUDE.md` wording says the sample phase shows bytes.
- Live check partial (WALK and PARTIAL_HASH seen on a real folder); done-item unticked.
- Ready for the human gate.

## Criteria

- **DFS-057.AC-1: met.** `phaseView` in WALK returns "Finding files" / "<n> folders · <n> files · <size> found", `percent: null`, `found: null`; the template renders `mat-progress-bar mode="indeterminate"` when `percent === null`. Row test reads `.scan-phase-line` text exactly and `MatProgressBarHarness.getMode() === 'indeterminate'`; pure test also covers singular "1 folder · 1 file · 10 B" and a null phase (QUEUED) giving no view.
- **DFS-057.AC-2: met.** PARTIAL_HASH detail is "<done> of <plural total> · <left> left · <round%>", percent = files share; no bytes in the line. Row test checks the exact text, determinate mode, value close to 33, and that the row text contains no "MB" (the scenario's 400 MB / 1.2 GB would have shown up).
- **DFS-057.AC-3: met.** FULL_HASH detail adds "<hashed> of <total> · <left> left · <round%>" by bytes; the scenario string matches exactly in both the pure and the row test; bar value 25; the row also shows `.scan-eta` "estimating…" on the first update.
- **DFS-057.AC-4: met.** "Checking hard links: group 40 of 310", bar by groups (12.9), no ETA (row test asserts `.scan-eta` absent).
- **DFS-057.AC-5: met.** `found` is "Found: <files> · <size>" in PARTIAL_HASH, FULL_HASH and HARD_LINKS (looped in the pure test), null in WALK; `share()` returns 0 for a zero total. Tests cover all three phases with 0 totals (percent 0) and the row's "0 of 0 files · 0 left · 0%" with bar value 0.
- **DFS-057.AC-6: met.** `EtaEstimator.record` follows design.md's rule exactly: window reset outside FULL_HASH or on a terminal status, phase start at the first FULL_HASH update, entries with `time >= now - 30 s` kept (the 30 s entry stays, as the scenario needs), warm-up 3 s and `hashedBytes === 0` give "estimating…", a non-positive rate gives "estimating…", otherwise `formatEta(left / rate)`. Clock is the `CLOCK` token (`() => Date.now()` default). Every scenario is tested both on the pure estimator and through the row with a fake clock: wait-then-rate (76 s → "about 1 min left"), last 30 s only (→ "about 20 s left"), the three rounding examples plus unit-after-rounding (59.6 s → "about 1 min left"), the stall (back to "estimating…") and the new phase (no ETA in HARD_LINKS; a second FULL_HASH starts afresh with its own warm-up). I checked the arithmetic of each expected value by hand.
- **DFS-057.AC-7: met.** The template's COMPLETED, FAILED and CANCELLED branches are byte-for-byte the old ones; the new lines live only in `@default`. Row tests move a RUNNING row to each terminal status and assert totals plus "Open results" / the failure message / nothing, and no `.scan-phase-line`, `mat-progress-bar`, `.scan-eta` or `.scan-found`. The estimator also resets on a terminal status (pure test).
- **DFS-057.AC-8: met.** Counts go through `plural` (no separators, "1 file"), sizes through `formatBytes` (307.3 GB, 3.1 GB, 12.4 GB, 9.3 GB in the scenarios). The done and "left" counts are bare numbers, as the ticket's own strings show ("3100 of 9400 files · 6300 left").

## Test quality

- The tests assert on output, not on implementation: exact visible strings (`.scan-phase-line` textContent), the bar read through `MatProgressBarHarness` (mode and value), presence/absence of `.scan-eta`, `.scan-found`, `mat-progress-bar`, and the ETA text computed from real numbers. Expected values are the ticket's own scenario strings; I recomputed the ETA ones (76 s, 20 s, 240 s, 55 s, 20 s) and they follow from the rule, not from the code.
- Each scenario is proved twice: on the pure helpers (`scan-phase.spec.ts` 8 tests, `scan-eta.spec.ts` 10 tests) and through the real component with a hand-driven `FakeEventSource` and a fake `CLOCK` (`scan-row.spec.ts`, 12 new tests). No test waits on real time: the clock is a variable the test moves; `settle(fixture)` only flushes Angular.
- Good negative checks: no "MB" in a PARTIAL_HASH row, no ETA in HARD_LINKS, no found line in WALK, the window boundary (the 4 s update dropped at 60 s, the 30 s one kept), and unit choice after rounding (59.6 s).
- Minor gaps, not blocking: the row-level AC-2 "no byte count" check only looks for "MB" (enough for the scenario's numbers); the AC-4 bar check uses `toBeCloseTo(12.9, 0)` (tolerance 0.5), while the pure test pins 12.9 to one decimal. No test covers CANCELLING (the `@default` branch handles it like RUNNING) or the "two updates between change detections are recorded once" consequence the design notes.
- Existing DFS-041.AC-6 assertions in `scan-screen.spec.ts` (`.scan-phase` holds the label) still pass unchanged, as the design intended.

## Design conformance

- Files and structure match `design.md`: `scan-phase.ts` (`phaseView` → `{label, detail, found, percent}`), `scan-eta.ts` (`CLOCK` `providedIn: 'root'` default `Date.now()`, `EtaEstimator` taking the clock in its constructor, `formatEta`), `scan-row.ts` with `view` computed, `eta` signal and an `effect` feeding the estimator; `hashing`/`hashPercent`/`phaseLabel` dropped. Selectors as committed (`.scan-phase-line`, `.scan-phase`, `.scan-eta`, `.scan-found`, `aria-label="Scan progress"`).
- ETA rule matches the design's "ETA rule" section line for line (window filter `>= now - 30000`, warm-up 3000 ms, one-entry window and rate <= 0 give "estimating…", terminal status resets).
- Checklist disposition: 1 (no client change: `src/app/api/` untouched, no `openapi.json` change), 2 (`settle` after each update), 3 (stubs surfaced; red recorded in `plan.json`), 4 (no browser API), 5 (same placement), 6 n/a, 7 (zero total, null phase), 8 (stream unchanged; reset on phase change and terminal status), 9 (harness used), 10 (no new module; I did not re-measure the bundle, the build has no budget warning), 11 (live check: see below), 12 n/a, 13 (docs: see done-items). All hold in the diff.
- Docs: `CLAUDE.md` (the `server` entry's scan-screen sentence) and `docs/design/ui.md` U2 both describe the phase lines, the bar, the found line and the ETA. One small wording slip in `CLAUDE.md`: it says "Comparing samples" and "Hashing files" show "files and bytes done, left and a percentage", but "Comparing samples" deliberately shows no bytes (AC-2). `ui.md` gets it right. Not blocking; worth a one-word fix at close.
- Touched files: all inside `touches` (`server/frontend/src/app/scans`, `CLAUDE.md`, `docs/design/ui.md`) plus the story folder.

## CLAUDE.md invariants

Display-only change in the Angular scan row; no request is added, nothing reads, moves or deletes files. Never delete the last copy, one plan execution at a time, re-verify at execution time, preview as a pure query, hard links not duplicates, no symlink/junction following, empty files excluded: none is touched by this diff (no server, core or API change; `ScanProgress` unchanged). The frontend rule "call the API only through the generated services, except the SSE stream" still holds: the row uses the same `ScansService.cancelScan` and `ScanEventsService.progress` as before.

## By-hand live check

Partly done. Built `dfs-server.jar` (bundle confirmed to hold the implementation, no stub text), started with `java -jar` (PID 27200, stopped with `taskkill` after the check), driven headless in Edge through Playwright for Java from a scratch program outside the worktree; scanned `C:\Users\apoch\Projects\us.pochaev\dfs` (102 724 files, 2.4 GB found) and read the row's DOM every 250 ms (log: `C:/Users/apoch/AppData/Local/Temp/claude/02ad29a6de71421f/bf0f7892-5e76-4556-af00-1220b2d226a8/scratchpad/live.log`).

- Seen: WALK, e.g. "Finding files: 6616 folders · 49665 files · 1.1 GB found", bar `mode=indeterminate`, `aria-label="Scan progress"`, no found line, no ETA. PARTIAL_HASH, e.g. "Comparing samples: 182 of 102175 files · 101993 left · 0%", "Found: 102724 files · 2.4 GB", determinate bar with `aria-valuenow` 0.178 (follows files), no ETA. (The "·" shows as a replacement character only in the scratch program's console output, a Windows stdout encoding artefact.)
- Not seen: FULL_HASH, the ETA settling, HARD_LINKS, the final COMPLETED row, and the console-error count (stopped after 22 s in the sample phase at the turn cap, before the program printed its console summary). Sampling ~100 000 small files from a cold cache ran at about 10 files/s, so this folder was a poor choice for reaching hashing quickly.
- That done-item stays unticked; the product owner's own review on a real scan (decision 3) covers it.

## Done-items

Ticked: every scenario automated and passing; each `it` name starts with its criterion ID; `./gradlew build` green; component checklist addressed; no test waits on real time; `CLAUDE.md` and `ui.md` U2 describe the change (with the wording slip noted above); no files outside Touches. Not ticked: the by-hand run on Windows (partly done, above).
