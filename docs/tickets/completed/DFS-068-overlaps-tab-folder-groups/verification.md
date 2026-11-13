# DFS-068 verification (second run, after the done-gate rework)

## Product owner review

### What you can do now

The Overlaps tab now lists folder groups: folders that hold copies of the same files, heaviest first. Each group shows how much of each folder is shared, which folders are fully shared, which folders an application manages ("app"), and the files involved. The old Folders and Folder pairs tables are gone. Pin and Comparison still work as before. Since your done-gate feedback, the "<total> in all" figure in a group header shows the files' own size, one copy each, instead of adding up every copy.

### Acceptance criteria

- AC-1 met: one header row per group, in the server's order, with files, copies, folder count and the reclaimable size with a bar scaled to the page's largest. The "in all" size is now right (checked live: 3 MB + 2 MB files in two folders read "5.0 MB in all"; 1 MB + 2 MB files in three folders read "3.0 MB in all").
- AC-2 met: each folder line shows a short path with the full path as tooltip, "N of M files" with a bar, "×N" above 1, and a Copy button.
- AC-3 met: "fully shared" and "also in N other groups" appear only when they apply.
- AC-4 met: two or more fully shared folders add "Fully shared folders: any one can go, not all."
- AC-5 met: expanding a group lists up to 20 files (name, size, copies), then "and N more".
- AC-6 met: selecting a folder reloads the groups for that folder only; "All folders" drops the filter.
- AC-7 met: "Show N small groups" and "Hide small groups" reload with and without the small groups (seen live again).
- AC-8 met: `*.auh`, `Adobe Premiere Pro Auto-Save`, `*.lrdata`, `*.photoslibrary` and `*.fcpbundle` path elements get the "app" tag with its tooltip, in any letter case. Lookalike names do not.
- AC-9 met: the old tables, note and folder header are gone, `/summaries` and `/pairs` are never requested, and Comparison still works.

### Try it

1. `./gradlew :server:bootRun`, then open the `Open http://127.0.0.1:<port>/?token=...` link it logs.
2. Scan a folder holding a few large duplicate files, for example `Photos/big1.bin` (3 MB) and `Photos/big2.bin` (2 MB) copied into `Backup`, a 1 MB and a 2 MB file copied into three folders `T1`, `T2`, `T3`, and a copy inside a folder like `Proj/X.auh/Contents`. Click Open results, then the Overlaps tab.
3. What to look for: the `T1/T2/T3` group reads "· 6 copies · 3.0 MB in all · shared by 3 folders" with 6.0 MB reclaimable; the `Backup/Photos` group reads "· 4 copies · 5.0 MB in all · shared by 2 folders" with 5.0 MB; the "app" tag on the `X.auh\Contents` line; the "any one can go" line under groups with two or more fully shared folders; "Show N small groups" at the bottom; the Compact/Comfortable toggle changing the row heights.

### Decided without you

- "<total> in all" is computed as reclaimable × files / (copies − files), your formula from the done gate. It is exact because `/groups` puts sets with the same copy count per folder in one group. One rare exception: a hard-linked file with links in several folders counts once in "copies" but appears in each folder. Two sets can then share a group with different copy counts, and the figure becomes an approximation. Not seen in practice; no test covers it.
- `design.md` still says "sum of the folders' `sharedBytes`" (lines 23 and 94). Your done-gate feedback superseded that text. The architect's file was not edited in the rework; `test-plan.md` and `progress.md` record the new rule.
- "<size> each" is shown only when the server listed every file of the group (at most 20) and they all have one size. Otherwise the header says "in all".
- Paths are cut to a fixed 64 characters. The Sets tab measures its column width; this tab does not.
- Choosing a folder, or opening another scan, hides the small groups again and goes back to page 1. Changing the page size keeps both.
- Singular forms: "Show 1 small group", "also in 1 other group". "N of M files" always says "files", for example "1 of 1 files".
- The tab now takes the result screen's roots (for short paths) and density as inputs.
- The fixed note and the selected folder's summary block were dropped (ticket decision 1). Their text helpers stay unused in `result-format.ts`, which was outside `touches`.
- The browser test fixture's groups are below 1 MiB, so the browser flows click "Show N small groups" first.

### Known limits

- Folder lines are a flowing row, not columns: when paths differ in length, the "N of M files" figures do not line up between lines (`Proj` vs `Proj\X.auh\Contents`). No criterion covers this; worth a look in your post-close `d:/` check.
- In Comfortable, a folder line is 56 px tall (Material's 40 px Copy button plus padding); in Compact it is 28 px.
- The hard-link edge case of the "in all" formula (above).
- Merging mirrored folder trees, a "keep here, remove elsewhere" action and an editable app-pattern list are out of scope (G7 and later).
- The real `d:/` check is your post-close check.

### Evidence

- Rework diff since the first verify (`cdc2d89..2f61c46`): `overlaps-tab.ts` (`groupSizeText` only, 6 lines plus its doc comment) and `overlaps-tab.spec.ts` (the "in all" test only), both in `touches`, plus the story folder's `test-plan.md`, `progress.md` and `plan.json`. No other file changed.
- Vitest (`server/build/test-results/vitest/TEST-vitest.xml`): 299 tests, 0 failures, 0 skipped. The "in all" test now uses data built the way `/groups` computes it and expects 25.0 MB, 5.0 MB and 3.0 MB. Before the fix it failed with 50.0, 10.0 and 9.0 MB (test-author's run, `plan.json`).
- `./gradlew build :server:e2eTest :server:bootJar`: BUILD SUCCESSFUL. Build and npmTest were up to date with the current tree. `e2eTest` ran 9 flows, 0 failures, 0 skipped (`server/build/test-results/e2eTest/`).
- Deterministic gate (implement rework): green (1214 passed, 3 skipped), disabled, freeze, touches (18 files), stubs, traceability (9 criteria), consistency all PASS.
- Live check on `server/build/libs/dfs-server.jar` with Playwright over a scratch fixture. Figures and box sizes are under "Live check" below.

### How it went

- Design: 1 attempt plus 1 rework after design-gate feedback (the host's `result-screen.html` and `.spec.ts` were added to `touches`).
- Tests: 1 attempt (red: 24 story tests failed on stubs), then 1 rework after the done gate (the "in all" data and expectation; it failed on the old formula, as intended).
- Implement: 1 attempt, gate green; then 1 rework (the formula), gate green.
- Verify: 1st attempt found the doubled "in all" size, routed by you at the done gate to tests and then implement. This 2nd attempt confirms the fix. No escalations.

Ready for your approval.

## Summary

- DFS-068.AC-1: met. Header rows in server order with files, copies, folders and the scaled reclaimable bar. The "in all" defect is fixed (live: 5.0 MB for 3 + 2 MB in two folders, 3.0 MB for 1 + 2 MB in three folders).
- DFS-068.AC-2: met. Short path with tooltip, ×N, "N of M files" with bar, Copy button.
- DFS-068.AC-3: met. "fully shared" and "also in N other group(s)".
- DFS-068.AC-4: met. Note at 2 or more fully shared folders, not at 0 or 1.
- DFS-068.AC-5: met. Lazy expander, 20 sets, "and N more".
- DFS-068.AC-6: met. `under=` follows the selection (unit and earlier live run; code unchanged in the rework).
- DFS-068.AC-7: met. `minBytes=0` toggle, hidden when nothing is hidden (unit and live).
- DFS-068.AC-8: met. Five patterns, case-insensitive, both separators, lookalikes excluded (unit and live).
- DFS-068.AC-9: met. Old tables, note and calls gone; Comparison intact.
- Rework scope: `groupSizeText` and one test, both in `touches`. `design.md` still has the superseded "sum of sharedBytes" text.
- Build, npmTest (299/0), e2eTest (9/0) green. Live check repeated in both densities.
- Verdict: ready for the human gate.

## Criteria

- **AC-1** met. `loadGroups` renders `result.items` in order; header shows `filesText`, `groupMetaText` (copies, size, `shared by N folders`), `formatBytes(reclaimableBytes)` and a bar from `barPercent` over the page's largest. `groupSizeText` now returns `formatBytes(round(reclaimableBytes × files / (copies − files)))`, falling back to `reclaimableBytes` when `copies` equals `files` (impossible for a duplicate set, but safe). Server side (`FolderGroups`): `copies` sums each set's physical copies, `reclaimable` sums `(copies − 1) × size`, and the group key holds every folder's `copiesEach`, so with no cross-folder hard links every set has k = copies / files physical copies and the formula gives Σ size exactly. Tests: heaviest group (exact texts), server order with widths `[50, 100, 25]`, the reworked "in all" test (three groups, `/groups`-consistent data, 25.0 / 5.0 / 3.0 MB), loading/empty/error, paginator 10/25/50.
- **AC-2** met. `displayPath(path, roots(), 64)` with `title` = full path; `×N` only for `copiesEach > 1`; `<s> of <f> files` with a bar; `app-path-copy`. Unchanged in the rework.
- **AC-3** met. Unchanged; tests cover `otherGroups` 2, 0, 1.
- **AC-4** met. Unchanged; tests cover 1, 0 and 2 fully shared folders. Live: note on T1/T2/T3, Proj/X.auh and small1/small2; none for Backup (fully) / Photos (2 of 3).
- **AC-5** met. Unchanged; lazy `matExpansionPanelContent`, 20 sets then `and 5 more`.
- **AC-6** met. Unchanged in the rework; unit tests in both specs, and the first verify's live run (`under=...%5CPhotos`, then no `under`).
- **AC-7** met. Live again: `groups?page=0&size=10`, then after "Show 1 small group" `groups?minBytes=0&page=0&size=10`, 4 groups and the button reading "Hide small groups".
- **AC-8** met. Unchanged; live: only `Proj\X.auh\Contents` tagged, tooltip "Managed by an application; deleting here can break it".
- **AC-9** met. Unchanged; live: `.summaries-table`, `.overlap-note`, `section.pairs` count 0; only `/groups` requested by the tab.

## Test quality

- Tests assert rendered text, attributes, bar widths and exact request parameters (`expectGroups` with null meaning "absent", `http.verify()` after each). Material harnesses drive the expander, paginator and buttons. Negative cases are present.
- The first verify's weak spot is fixed: the "in all" test now builds multi-folder groups the way `/groups` does (each folder's `sharedBytes` = copies there × size, `copies` and `reclaimableBytes` consistent), and covers two copies with unlisted sets, mixed sizes, and three copies per file. The three-copies case rules out "use reclaimableBytes" (6.0 MB). All three cases have `copiesEach` 1 in every folder, so a "sum of sharedBytes ÷ folders" shortcut would also pass (25 / 5 / 3 MB). A case with `copiesEach` 2 would rule it out. Minor: the shipped formula is the right one, and the test's comment states the meaning.
- No test for the cross-folder hard-link case, where the formula is approximate (rare; noted in Known limits).
- No test checks that a superseded `/groups` answer is dropped (rapid folder changes). Minor; the guard is simple.

## Design conformance

- Matches design.md in everything except the "in all" formula: inputs `roots`/`density`, template structure and classes, `FOLDER_PATH_BUDGET` 64, `MatExpansionModule`, page sizes 10/25/50, small groups reset on selection, plural forms, page states. Checklist dispositions 1-13 hold (only `listGroups`/`getPair` called; `src/app/api/` untouched; no new browser API; docs updated).
- design.md lines 23 and 94 still say "sum of the folders' `sharedBytes`". The user's done-gate feedback superseded this (recorded in `progress.md` and `test-plan.md`); the code's doc comment states the new rule. `CLAUDE.md` and `ui.md` say only "in all", so no doc there is stale.

## Safety invariants

Read-only frontend view issuing only GETs (`/groups`, `/pair`); the rework changes one display formula. Never delete the last copy, one execution at a time, re-verify at execution, preview purity: not touched. Hard links: the tab shows the server's `copiesEach`, `copies` and `sharedFiles` as given; the new formula divides by physical extra copies (`copies − files`) and never counts link paths as copies. Symlinks/junctions, quarantine and system folders, empty files: scanner-side, unaffected.

## Live check

`server/build/libs/dfs-server.jar` (rebuilt by `:server:bootJar`, up to date with HEAD `2f61c46`; started with `--dfs.server.open-browser=false` by the check script and stopped by it, PID 16272 gone afterwards), driven by Playwright 1.64 Chromium at a 1400×1000 viewport, over a scratch fixture outside the worktree (15 files: `Photos/big1.bin` 3 MiB, `Photos/big2.bin` 2 MiB, `Photos/unique.txt`, copies of both in `Backup`; `T1/one.bin` 1 MiB and `T1/two.bin` 2 MiB copied into `T2` and `T3`; `Proj/clip.mov` 4 MiB and its copy in `Proj/X.auh/Contents`; `small1/t.txt` and `small2/t.txt` 8 B). Scan COMPLETED; the fixture was deleted afterwards.

Compact (section class `overlaps-tab density-compact`):
- G0: `2 files` `· 6 copies · 3.0 MB in all · shared by 3 folders` `6.0 MB`, bar 100%, header 1350×29. Lines `T1`, `T2`, `T3` each `2 of 2 files | fully shared | Copy`, 1350×28; note shown.
- G1: `2 files` `· 4 copies · 5.0 MB in all · shared by 2 folders` `5.0 MB`, bar 83.3%. Lines `Backup | 2 of 2 files | fully shared` and `Photos | 2 of 3 files`; no note.
- G2: `1 file` `· 2 copies · 4.0 MB each · shared by 2 folders` `4.0 MB`, bar 66.7%. Lines `Proj | 1 of 1 files | fully shared` and `Proj\X.auh\ | Contents | 1 of 1 files | fully shared | app` (tooltip "Managed by an application; deleting here can break it"); note shown.
- Old `.summaries-table`, `.overlap-note`, `section.pairs`: 0. "Show 1 small group" added G3 (`8 B each`) and the button then read "Hide small groups".

Comfortable (after the toggle; section class `overlaps-tab density-comfortable`): same texts, header 1350×37, folder lines 1350×56, nothing zero-sized. Full paths in every line's tooltip.

Side effect: `Playwright.create()` without `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` downloaded Firefox and WebKit (about 180 MB) into `server/.gradle/playwright-browsers/` of this worktree. The folder is git-ignored (`.gradle`), so the tree stays clean; it goes away with the worktree.
