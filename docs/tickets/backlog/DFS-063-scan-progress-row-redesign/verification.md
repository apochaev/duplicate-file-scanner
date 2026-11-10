# DFS-063 verification

## Product owner review

### What you can do now
Watch a scan in the browser as one bar of four fixed sections (finding files, comparing samples, hashing, hard links), with an overall percentage that only grows, a time left once hashing starts, and the totals of each finished step. A finished scan folds into one line with its totals, how long it took and the Open results link.

### Acceptance criteria
- AC-1 met: a queued scan shows four sections not started, "—", "Waiting to start", Cancel enabled (tests).
- AC-2 met: while cancelling, the percentage holds, "Cancelling…" shows, Cancel is disabled; once cancelled the section is "stopped" (tests and the live run).
- AC-3 met: the percentage follows the weights, "—" in the walk, never above 99 before Completed (tests; live 30% → 87% → 100%).
- AC-4 met: "estimating…" for the first 30 s of hashing, then "about 29 s left" counting down (tests and live).
- AC-5 met: hard-link time left from the groups rate (tests only; the phase was too short to see live).
- AC-6 met: "finishing…" when nothing is left to read, also inside the first 30 s (tests only; not caught live).
- AC-7 met: the running walk, compare and hashing lines read as specified (tests and live).
- AC-8 met: finished sections show their totals, and an arrow line appears only once its number is known (tests and live).
- AC-9 met: "not started / total known after step N", "not run" after a stopped step, the stopped step's counts (tests and live).
- AC-10 met: every state is written as text (done, now, finishing, stopped, failed) (tests and live).
- AC-11 met: a failed scan shows "Scan failed while hashing files. <message>" (tests only).
- AC-12 met (after a fix): a finished scan folds into one line with its totals, "took …", "finished HH:MM" and Open results. This now also works for a scan that finishes while you watch it: the row fetches the scan once more when it completes (tests and live: "took 8 s · finished 23:31" with no reload).
- AC-13 met: durations read "42 s", "14 min 38 s", "1 h 05 min" (tests).
- AC-14 retired (phone layout not built).
- AC-15 met: with reduced motion the stripes stay but do not move; otherwise they move (test, and checked live in Chromium).

### Try it
`./gradlew :server:bootJar`, then `java -jar server/build/libs/dfs-server.jar` and open the `Open ...?token=` link it logs. Scan a folder with several GB of large duplicate files. Look for: the step line "Step 3 of 4 · Hashing files", the percentage rising, "estimating…" turning into "about N s left" after 30 s, done sections with ticks and totals. When the scan completes, the row collapses to one line ending in "took … · finished HH:MM · Open results". For a split second "took" and "finished" may be blank while the page fetches them.

### Decided without you
- "Finishing" needs a known total: hashing with totals still 0 shows "estimating…", not "finishing…" (design note 2 left this open).
- Hard links with nothing left after the 30 s warm-up read "finishing…". There is no test for it.
- Step-line variants "Cancelled during step N · …" and "Failed during step N · …", and the captions "of whole scan" / "when stopped" / "total not known yet", are taken from the canvas.
- The canvas's "ran 7 min 52 s / cancelled 14:10" cell, the rate line under the time left ("34.2 MB/s over the last 30 s") and the walk/compare "not yet" text are not built.
- The expanded row keeps the status word ("Cancelling") next to the new "Cancelling…" text, so it appears twice.
- State icons are text glyphs (✓ ● ■ ✕ ○) rather than the canvas's SVGs, to stay inside the 4 kB component style budget.
- A failed section has a red outline on a grey background (the canvas uses a pink background).
- The AC-12 fix fetches `GET /api/scans/{id}` once the stream reports COMPLETED, rather than adding `finishedAt` to the event stream (the server is unchanged). Only COMPLETED fetches: a CANCELLED or FAILED row does not show a duration anyway. If the fetch fails, the row stays without a duration and there is no retry.

### Known limits
- Right after a scan completes, "took" and "finished" are blank for the length of one API request. In the live run this was under 1.5 s.
- The strip's `role="progressbar"` has no `aria-valuenow`, which the canvas has (accessibility, small).
- Not seen live: QUEUED, finishing, hard links and FAILED. All have unit tests.

### Evidence
- `./gradlew build` green (re-run in the second pass, after the fix). Vitest: 272 tests, 0 failures, 0 skipped (`server/build/test-results/vitest/TEST-vitest.xml`). The two new ones are the AC-12 completed-while-open tests in `scan-row.spec.ts` and `scan-screen.spec.ts`.
- `./gradlew :server:e2eTest` (second pass): `BrowserFlowTest` 8 tests, 0 failures, 0 skipped.
- `ng build`: initial total 704.59 kB, no budget warning.
- By-hand run on the built jar (Chromium over CDP, DOM read every 250–500 ms), detailed below.

### How it went
- Design: 1 attempt. Tests: 1 attempt, reopened once for defect 1 (the AC-15 regex could never pass; routed to test-author and fixed).
- Implement: attempt 1 failed only on that test defect; attempt 2 was a gate re-run and passed.
- Verify, attempt 1: found defect 2 (AC-12: a scan finishing in view showed empty duration and finish time). It went to the test-author, who added a stream-to-COMPLETED test, and then to the implementer, who added the refetch. That made existing specs leave an unanswered request open (defect 3). The test-author fixed it with a shared `answerFinishFetches` helper. The gate passed after that.
- Verify, attempt 2: no new defects. The fix holds in the tests and live.

**Ready for the user's approval.**

## Summary

- AC-1 met; AC-2 met (also live); AC-3 met (live); AC-4 met (live); AC-5 met (tests); AC-6 met (tests); AC-7 met (live); AC-8 met (live); AC-9 met (live); AC-10 met; AC-11 met (tests); AC-13 met; AC-15 met (live).
- AC-12 met after the fix: `ScanRow` keeps `finishedAt` in a `linkedSignal` and calls `GET /api/scans/{id}` once the stream reports COMPLETED. Both new specs drive the stream to COMPLETED and assert the duration and finish time. Live on the jar: "took 8 s · finished 23:31" with no reload.
- AC-14 retired.
- Build green, Vitest 272/272, e2eTest 8/8, no budget warning, design checklist followed. Every done-item is ticked.
- Verdict: ready for the human gate.

## Criteria

See the Product owner review. The pure helpers (`scan-sections.ts`, `scan-format.ts`, `scan-eta.ts`) match decisions 1–5. The CANCELLING hold is a `linkedSignal` in `ScanRow`. `noBytesLeft` requires `hashBytesTotal > 0`.

## Test quality

The tests assert rendered text and state classes using the canvas's numbers, and drive time with the `CLOCK` fake. They are behavioural, not restatements of the code. Gaps:
- (First pass) No test took a row from the stream into COMPLETED. Fixed in the second pass: `scan-row.spec.ts` checks for exactly one `GET /api/scans/s-1`, answers it and asserts "took 14 min 38 s" / "finished 14:17". `scan-screen.spec.ts` does the same through the whole screen and also checks the `datetime` attribute. Both are behavioural. The `answerFinishFetches` helper in `afterEach` answers stray refetches in the other specs, so none of them asserts that no refetch happens for CANCELLED or FAILED. That is low risk, because the code checks the status.
- No test covers hard-link "finishing…".
- The 0-of-0 hashing case is not pinned, by design.
- The AC-15 test reads the component's styles; the real look was checked by hand.

## Design conformance

Matches design.md, including the template class contract and checklist items 1–13. `scan-phase.ts` and its spec are deleted. CLAUDE.md and ui.md describe the new row.

## Invariants

Frontend-only change: no deletion, preview or walk code touched. The deletion, hard-link and symlink invariants do not apply.

## By-hand run on the built jar

Server `dfs-server.jar` (PID 28944), headless Chromium 1248, desktop width 1440.
- **Run 1: 16 GB folder** (4 random 1 GB files × 4 copies, outside the repo). Hashing showed "estimating…" until about 30 s, then "about 29 s left" down to "about 2 s left". The percentage went 30% → 87% → 100%. Done sections read "16 files · 16.0 GB / found in 5 folders" and "16 compared / → 16 candidates". The running hashing section read "8.2 of 16.0 GB · 7.8 GB left / 8 of 16 files · 8 left", and hard links "not started / total known after step 3". The collapsed row read "Completed · started 23:06", "16 files · 16.0 GB found → 16 compared → 16 hashed · 16.0 GB → 4 duplicate groups", "100%", but "took" and "finished" were empty. After a reload it read "took 1 min 1 s" and "finished 23:07" (defect).
- **Stall check:** the bar did not sit still after the last file was read. It went from 96% at 59.8 s to collapsed at 61.8 s, and "finishing" was not even caught between samples. No follow-up ticket is needed.
- **Run 2: C:\Program Files with reduced motion.** The walk section was running and striped, its stripe image present, `animation-name: none`. Lines read "137905 files · 56.2 GB found / in 17064 folders" and the percentage "—" with "total not known yet". After Cancel: "Cancelled during step 1", walk "stopped", the rest "not run", caption "when stopped".
- **Run 3: same folder without reduced motion.** The walk stripes animate (`mv`). Compare read "28 of 233359 files / 233331 left" at 12%. Cancel then showed "Cancelling…", the percentage held at 12%, Cancel was disabled and the section stayed running. Within 100 ms the scan was CANCELLED, with the compare section stopped ("1504 of 233359 files") and the later sections "not run".
- Compared with the canvas: layout, section states, texts and captions match. Differences are listed under Decided without you.
- Chromium (PID 31792 and children) and the server were stopped by PID. The scratch files were deleted.
- **Second pass, AC-12 only** (jar rebuilt with the fix, server PID 12588, headless Chromium 1248 over CDP, DOM read every 400 ms). The folder was 3 random 400 MB files × 2 copies (2.3 GB), outside the repo. The scan was started through the API, then the page was reloaded while it was hashing ("Step 3 of 4 · Hashing files", 34%), and it rose to 87%. At 8.2 s the row collapsed ("Completed · started 23:31 / 6 files · 2.3 GB found → 6 compared → 6 hashed · 2.3 GB → 3 duplicate groups / 100%"), with "took"/"finished" still blank in that first sample. 1.5 s later, with no reload, it read "took 8 s finished 23:31 Open results". Chromium (PID 30848 and children) and the server were stopped by PID. The scratch data and profile were deleted.

## Concerns

1. The AC-12 defect: fixed and re-verified in the second pass (not blocking). What remains is a blank duration for one request after completion, and no retry if that request fails.
2. The strip has no `aria-valuenow`.
3. Hard-link "finishing…" has no test.
