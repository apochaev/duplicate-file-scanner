# DFS-068 verification

## Product owner review

### What you can do now

The Overlaps tab now lists folder groups: folders that hold copies of the same files, heaviest first. Each group shows how much of each folder is shared, which folders are fully shared, which folders are managed by an application ("app"), and the files involved. The old Folders and Folder pairs tables are gone. Pin and Comparison still work as before.

### Acceptance criteria

- AC-1 met: one header row per group, in the server's order, with files, copies, folder count and the reclaimable size with a bar scaled to the page's largest. One defect sits next to it: the "<size> in all" figure is doubled (see Known limits).
- AC-2 met: each folder line shows a short path with the full path as tooltip, "N of M files" with a bar, "×N" above 1, and a Copy button.
- AC-3 met: "fully shared" and "also in N other groups" appear only when they apply.
- AC-4 met: two or more fully shared folders add "Fully shared folders: any one can go, not all."
- AC-5 met: expanding a group lists up to 20 files (name, size, copies), then "and N more".
- AC-6 met: selecting a folder reloads the groups for that folder only; "All folders" drops the filter (seen live as well).
- AC-7 met: "Show N small groups" and "Hide small groups" reload with and without the small groups (seen live).
- AC-8 met: `*.auh`, `Adobe Premiere Pro Auto-Save`, `*.lrdata`, `*.photoslibrary` and `*.fcpbundle` path elements get the "app" tag with its tooltip, in any letter case. Lookalike names do not.
- AC-9 met: the old tables, note and folder header are gone, `/summaries` and `/pairs` are never requested, and Comparison still works.

### Try it

1. `./gradlew :server:bootRun`, then open the `Open http://127.0.0.1:<port>/?token=...` link it logs.
2. Scan a folder that holds a few large duplicate files, plus a copy inside a folder like `Proj/X.auh/Contents`. Click Open results, then the Overlaps tab.
3. What to look for: groups sorted by reclaimable size; "fully shared" tags; the "app" tag on the `X.auh\Contents` line; the "any one can go" line under groups with two fully shared folders; "Show N small groups" at the bottom; the Compact/Comfortable toggle changing the row heights.
4. To see the defect, use a group whose files have different sizes (3 MB + 2 MB copied into two folders). Its header reads "10.0 MB in all" where the files take 5.0 MB.

### Decided without you

- The header's "<total> in all" figure is the sum of every folder's `sharedBytes` (design.md). That counts every copy, so it is double for two folders and triple for three (defect below).
- "<size> each" is shown only when the server listed every file of the group (at most 20) and they all have one size. Otherwise the header says "in all".
- Paths are cut to a fixed 64 characters. The Sets tab measures its column width; this tab does not.
- Choosing a folder, or opening another scan, hides the small groups again and goes back to page 1. Changing the page size keeps both.
- Singular forms: "Show 1 small group", "also in 1 other group". "N of M files" always says "files", for example "1 of 1 files".
- The tab now takes the result screen's roots (for short paths) and density as inputs.
- The fixed note and the selected folder's summary block were dropped (ticket decision 1). Their text helpers stay unused in `result-format.ts`, which was outside `touches`.
- The browser test fixture's groups are below 1 MiB, so the browser flows click "Show N small groups" first.

### Known limits

- **Defect: "<total> in all" is doubled.** Measured live: a group of `big1.bin` (3 MB) and `big2.bin` (2 MB) in `Backup` and `Photos` shows "· 4 copies · 10.0 MB in all", with reclaimable 5.0 MB. The mockup's "41.2 MB each on average" (206.2 MB / 5 files) shows the figure is meant to be the size of the files themselves, one copy each. Summing `sharedBytes` over the folders counts every copy. In `/groups` every file of a group has the same number of physical copies, so an exact fix exists without new API data: `reclaimableBytes × files / (copies − files)`. The Vitest test for "in all" uses one-folder groups whose `sharedBytes` do not match their copies, so it passes with the wrong formula.
- Folder lines are a flowing row, not columns: when paths differ in length, the "N of M files" figures do not line up between lines. Not in any criterion; worth a look in your post-close `d:/` check.
- In Comfortable, a folder line is 56 px tall (Material's 40 px Copy button plus padding); in Compact it is 28 px.
- Merging mirrored folder trees, a "keep here, remove elsewhere" action and an editable app-pattern list are out of scope (G7 and later).
- The real `d:/` check is your post-close check.

### Evidence

- Vitest (`server/build/test-results/vitest/TEST-vitest.xml`): 299 tests, 0 failures, 0 skipped. `overlaps-tab.spec.ts` has 26 tests, 0 failures. `result-screen.spec.ts` has its three rewritten Overlaps tests.
- `./gradlew build :server:e2eTest :server:bootJar`: BUILD SUCCESSFUL. Build and check were up to date at HEAD `6bde082`. `e2eTest` ran 9 flows with 0 failures and 0 skipped (`server/build/test-results/e2eTest/`), including the new `DFS-068.AC-1, DFS-068.AC-2 ... laid out with a width` flow and the rewritten `DFS-053.AC-3` and `DFS-053.AC-4, DFS-065.AC-3` flows.
- Deterministic gate (implement phase): green, disabled, freeze, touches (17 files), stubs, traceability (9 criteria), consistency all PASS.
- Live check on `server/build/libs/dfs-server.jar` (Playwright over a scratch fixture). Figures and box sizes are under "Live check" below.

### How it went

- Design: 1 attempt plus 1 rework after design-gate feedback (the host's `result-screen.html` and `.spec.ts` were added to `touches`, and the real `roots`/`density` binding was made).
- Tests: 1 attempt (red: 24 story tests failed on stubs).
- Implement: 1 attempt, gate green (1214 passed, 3 skipped).
- Verify: 1 attempt. One defect found ("in all" size), no retries or escalations so far. The test-author had already flagged the formula as possible double counting.

Recommendation: go back to the **tests** phase to fix the "in all" test data and expected value, then implement (a one-line formula change). If you accept the doubled figure for now, approve and file a follow-up ticket instead. Everything else is ready.

## Summary

- DFS-068.AC-1: met. Header rows in server order with files, copies, folders and the scaled reclaimable bar. Separate defect: "<total> in all" sums every copy (10.0 MB shown for 5.0 MB of files).
- DFS-068.AC-2: met. Short path with tooltip, ×N, "N of M files" with bar, Copy button.
- DFS-068.AC-3: met. "fully shared" and "also in N other group(s)".
- DFS-068.AC-4: met. Note at 2 or more fully shared folders, not at 0 or 1.
- DFS-068.AC-5: met. Lazy expander, 20 sets, "and N more".
- DFS-068.AC-6: met. `under=` follows the selection (unit and live).
- DFS-068.AC-7: met. `minBytes=0` toggle, hidden when nothing is hidden (unit and live).
- DFS-068.AC-8: met. Five patterns, case-insensitive, both separators, lookalikes excluded (unit and live).
- DFS-068.AC-9: met. Old tables, note and calls gone; Comparison intact.
- Test quality is good overall. Weak spot: the "in all" test's data is inconsistent and hid the double counting.
- Build, npmTest (299/0), e2eTest (9/0) green. Live check done in both densities.
- Verdict: send back to the tests phase (then implement) for the "in all" size, or approve with a follow-up ticket at the user's choice. Otherwise ready for the human gate.

## Criteria

- **AC-1** met (with a size-text defect outside the criterion's wording, see Design conformance). `loadGroups` renders `result.items` in order; header shows `filesText`, `groupMetaText` (copies, size, `shared by N folders`), `formatBytes(reclaimableBytes)` and a bar from `barPercent` over the page's largest. Tests: heaviest group (exact texts), server order with widths `[50, 100, 25]`, loading/empty/error, paginator 10/25/50. Live: bars 100% / 80% / 30% for 5.0 / 4.0 / 1.5 MB.
- **AC-2** met. `displayPath(path, roots(), 64)` with `title` = full path; `×N` only for `copiesEach > 1`; `<s> of <f> files` with a bar; `app-path-copy`. Tests assert relative path text, tooltip, middle cut, ×2 present/absent, bar width 3/7, copy button by aria-label.
- **AC-3** met. `fully shared` when `sharedFiles === folderFiles`; `also in N other group(s)` only when > 0; tests cover 2, 0, 1.
- **AC-4** met. Note shown at `fullySharedCount >= 2`; tests cover 1, 0 and 2 fully shared folders. Live: shown for Proj / X.auh and Mirror1 / Mirror2, not for Backup (fully) / Photos (2 of 3).
- **AC-5** met. Lazy `matExpansionPanelContent`; test asserts no sets before expanding, 20 after, detail `1.0 MB · 2 copies`, `and 5 more`, and none for a fully listed group. Live: `big1.bin 3.0 MB · 2 copies`, `big2.bin 2.0 MB · 2 copies`.
- **AC-6** met. Effect on (`scanId`, `folder`) calls `selectionChanged`; `under` omitted for null. Tests in tab spec and host spec (`under=C:\Photos`). Live: `groups?under=...%5CPhotos&page=0&size=10`, then `groups?page=0&size=10` for "All folders".
- **AC-7** met. `minBytes: 0` only while `smallShown`; button hidden when `hiddenSmall` 0 and not shown; kept under an empty list. Tests click through Show/Hide with exact request parameters. Live: "Show 1 small group" adds the 10 B group; "Hide small groups" shown while on.
- **AC-8** met. `isAppManaged` splits on both separators, glob-to-regex anchored, case-insensitive; tests cover all five patterns, lookalikes (`auh`, `x.auh.bak`, `Auto-Saves`, `photoslibrary`), tooltip. Live: only `Proj\X.auh\Contents` tagged, tooltip "Managed by an application; deleting here can break it".
- **AC-9** met. Template has no tables/note/header; tab injects only `OverlapsService` and calls `listGroups`/`getPair`; `expectNone` on `/summaries`/`/pairs`; Comparison tests (DFS-050 AC-5/6/7/10) kept and green in both specs, plus the e2e `DFS-053.AC-6` flow. Live: `.summaries-table`, `.overlap-note`, `section.pairs` count 0.

## Test quality

- Tests assert rendered text, attributes, bar widths and exact request parameters (`expectGroups` with null meaning "absent", `http.verify()` after each), not implementation details. Material harnesses drive the expander, paginator and buttons. Negative cases are present (no ×1, no tag on lookalikes, no note for 0/1 fully shared, no button with `hiddenSmall` 0, stale-answer ignored is implied by the request counter but not tested directly).
- Weak spot: `DFS-068.AC-1 shows the total as "in all"...` builds one-folder groups whose data are not consistent with the API (one folder, `copiesEach` default, `sharedFiles` 25 for 40 copies; `sharedBytes` 3 MB for two sets of 1 + 2 MB with 4 copies). With real data a folder's `sharedBytes` is copies-in-that-folder × size, so a sum over folders counts every copy. The test therefore restates design.md's formula rather than checking the figure's meaning, and hides the double counting.
- No test checks a superseded `/groups` answer is dropped (rapid folder changes). Minor; the guard is simple.
- `result-format.ts` keeps helpers now unused by the tab (`copiedElsewhereText` etc.); outside `touches`, noted in design.md.

## Design conformance

- Matches design.md: inputs `roots`/`density`, template structure and classes, `FOLDER_PATH_BUDGET` 64, `MatExpansionModule` added and `MatChipsModule`/`MatTableModule` dropped, page sizes 10/25/50, small groups reset on selection, plural forms, page states (loading, empty, error) as listed. Checklist dispositions 1-13 hold: only `listGroups`/`getPair` called; `src/app/api/` untouched; no new browser API; docs updated in `CLAUDE.md`, `ui.md` (U3 as built) and `folder-groups.md` (status line).
- **Defect (design-level, user-visible):** design.md and the implementation compute "<total> in all" as the sum of the folders' `sharedBytes`. Confirmed live: `Backup` + `Photos` group of 3 MB + 2 MB files shows "10.0 MB in all" (reclaimable 5.0 MB). The ticket's "<total size> in all" sits beside "<size> each" (one file's size), and the mockup's "41.2 MB each on average" equals 206.2 MB / 5 files, so the intended total is the files' own size (one copy each). Exact fix from fields already in `FolderGroupView` (every set in a group has the same physical copy count k = copies / files): `reclaimableBytes × files / (copies − files)`. The `CLAUDE.md`/`ui.md` text says only "in all", so no doc change is needed for the fix.

## Safety invariants

The ticket is a read-only frontend view; it issues only GETs (`/groups`, `/pair`). Never delete the last copy, one execution at a time, re-verify at execution, preview purity: not touched (no plan or deletion code in the diff). Hard links: the tab shows the server's `copiesEach` (distinct physical copies) and `sharedFiles` (link paths) as given; no counting of its own. Symlinks/junctions, quarantine and system folders, empty files: scanner-side, unaffected. Nothing in the diff weakens any invariant.

## Live check

`server/build/libs/dfs-server.jar` (built at HEAD, `--dfs.server.open-browser=false`, stopped by its PID afterwards), driven by Playwright 1.64 Chromium at a 1400×1000 viewport, over a scratch fixture outside the worktree (11 files: `Photos/big1.bin` 3 MiB, `Photos/big2.bin` 2 MiB, `Photos/unique.txt`, copies of both in `Backup`; `Proj/clip.mov` 4 MiB and its copy in `Proj/X.auh/Contents`; `Mirror1/a.bin` 1.5 MiB and `Mirror2/a.bin`; `small1/t.txt` and `small2/t.txt` 10 B). Scan COMPLETED; the fixture was deleted afterwards.

Compact (DOM read):
- Group 0: `2 files` `· 4 copies · 10.0 MB in all · shared by 2 folders` `5.0 MB`, bar 100%, header 1350×29. Lines `Backup | 2 of 2 files | fully shared | Copy` and `Photos | 2 of 3 files | Copy`, each 1350×28, path and share boxes non-zero; no note; panel `2 sets`.
- Group 1: `1 file` `· 2 copies · 4.0 MB each · shared by 2 folders` `4.0 MB`, bar 80%. Lines `Proj | 1 of 1 files | fully shared` and `Proj\X.auh\ | Contents | 1 of 1 files | fully shared | app` (title "Managed by an application; deleting here can break it"); note shown.
- Group 2: `1.5 MB each`, bar 30%, Mirror1/Mirror2 both fully shared, note shown.
- Bottom: paginator, `Show 1 small group`; old `.summaries-table`, `.overlap-note`, `section.pairs` absent. After clicking it, group 3 (`10 B each`, bar 0.0002%) appears and the button reads `Hide small groups`. Expanding group 0 lists `big1.bin 3.0 MB · 2 copies`, `big2.bin 2.0 MB · 2 copies`.

Comfortable (DOM read after the toggle; section class `overlaps-tab density-comfortable`): same texts; header 1350×37, folder lines 1350×56 (Copy button at Material's 40 px), path 14 px and share 21 px tall, nothing clipped or zero-sized. Selecting `Photos` requested `/groups?under=<fixture>\Photos&page=0&size=10` and left one group, with the small-groups button gone (hidden again, `hiddenSmall` 0 under Photos). "All folders" requested `/groups?page=0&size=10`.

Look: folder lines flow left to right, so the share figures do not form a column when path lengths differ (`Proj` vs `Proj\X.auh\Contents`); tags and Copy are readable in both densities. Screenshots were taken in the scratch directory and not kept.
