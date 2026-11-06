# DFS-050 verification

## Product owner review

### What you can do now

Open a finished scan's results and you land on a new **Overlaps** tab. It shows how much of each folder is copied elsewhere, which folder pairs share copies, and, after you click "Pin for comparison" on one folder in the tree and then select another, how those two folders compare. This works for any two folders, including two roots such as `C:\Photos` and `D:\Backup`. The duplicate-set cards are still there on the second tab, **Sets**.

### Acceptance criteria

- **DFS-050.AC-1 met.** The tab shows a header for the selected folder (none under "All folders"), a Folders table and a Folder pairs table, each with its own paginator, both filtered to the selection.
- **DFS-050.AC-2 met.** Rows and the header read "Copied elsewhere 340 / 400 files · 1.2 GB", "Duplicates inside …" and "Total …". The "all copied elsewhere" chip appears only when every file has a copy elsewhere, never on an empty folder. In the table the label is the column heading, as design.md said it would be.
- **DFS-050.AC-3 met.** Pair rows show side A then side B in the server's order, each followed by "partial" or "all have a copy in the other".
- **DFS-050.AC-4 met.** The fixed note sits above the tables. No row, header, chip or column heading says "safe", "delete" or "can go"; I checked this in the tests and in the live page.
- **DFS-050.AC-5 met.** Every tree folder has "Pin for comparison" and "All folders" has none. The tab shows "Pinned: <path>" and "Unpin". Pinning another folder replaces the pin, and Unpin removes both the label and the Comparison block.
- **DFS-050.AC-6 met.** The pinned folder and the selected folder are sent as `a` and `b`, and the result shows as one pair row.
- **DFS-050.AC-7 met.** Selecting the pinned folder itself or "All folders" sends no request and shows "Select another folder to compare with the pinned one". Nested folders show the server's message ("… contains …").
- **DFS-050.AC-8 met.** The tabs read "Overlaps" then "Sets", Overlaps is selected on open, and the Sets tab shows the cards.
- **DFS-050.AC-9 met.** Each table pages on its own (10 per page; 10/25/50) with a count line ("4 folders", "1 folder pair"). Selecting another folder goes back to page 0 of both.
- **DFS-050.AC-10 met.** Empty tables show their texts and no paginator. Each table and the Comparison block has its own progress bar. If one table fails, its error shows as an alert while the other table keeps its rows.

### Try it

```
./gradlew :server:bootJar
java --enable-native-access=ALL-UNNAMED -jar server/build/libs/dfs-server.jar
```

1. Open the `Open http://127.0.0.1:<port>/?token=…` link it logs.
2. Scan two folders where one holds a copy of part of the other, then click "Open results".
3. Look for "Overlaps" selected, the note, the Folders table with "all copied elsewhere" on the copy folder, and a Folder pairs row reading "… all have a copy in the other" beside "… partial".
4. Click "Pin for comparison" on one root and select the other root: a Comparison block appears.
5. Expand the pinned root, select one of its subfolders, and read "<pinned> contains <subfolder>".

### Decided without you

- **The Overlaps tab is built only while it is shown and dropped when you switch to Sets** (`matTabContent`, an implementer deviation). Going Sets → Overlaps reloads both tables at page 0. The pin and the selected folder are kept. I agree with this: otherwise a hidden tab would keep sending requests on every folder click.
- **The tab switch has no slide animation** (`animationDuration="0ms"`, an implementer deviation). It was made so that the tests see the switch at once, but it also changes the real app. This is cosmetic, and you may reject it. The alternative is to turn off the animation only in the specs (a `MAT_TABS_CONFIG` provider), which would need a test-phase change.
- **The Overlaps tab loads its own data** (design.md). The selection and the pin stay in the result screen.
- **The pin button shows the full text "Pin for comparison" on every tree row**, pushed to the right. In the narrow tree this wraps the folder label onto two lines ("Backup · 585.9 KB" / "KB · 3 files"; seen in the live check). The alternatives were a row menu or "Pin" alone.
- **Table cells show only the value; the label is the column heading** ("Copied elsewhere" etc.). The selected folder's header shows the label and the value together.
- **The pin is cleared when another scan's result screen opens.**
- **The tables show full absolute paths, not paths relative to the scan root**, so long paths wrap across several lines (monospace).

### Known limits

- **`docs/design/ui.md` is not finished; this is the one open defect.** The milestone table still lists DFS-050 (M-1.5) without "done" and does not mark M-1 done, which the ticket's Scope and its last done-item require. The new "As built (DFS-050, M-1.5)" bullet was also appended to the end of the DFS-049 bullet's line instead of starting a line of its own, so it renders as one run-on bullet with a literal "- **As built…" inside it.
- Charts (overlap matrix, treemap), building a rule from a pair, and keeping the pin or the tab in the URL are out of scope, as the ticket says.
- Page positions are lost when you switch to Sets and back.

### Evidence

- **Tests:** `./gradlew build` is green. I re-ran `:server:npmTest --rerun`: 10 files, 99 Vitest tests passed, 0 skipped in `server/build/test-results/vitest/TEST-vitest.xml`. The story's tests:
  - `overlaps-tab.spec.ts`: 13 tests;
  - `folder-tree.spec.ts`: 1 new (AC-5);
  - `result-screen.spec.ts`: 3 new (AC-5, AC-6, AC-8), plus 10 DFS-049 tests updated to select Sets first.
- **Deterministic gate on 8182892:** green 735 passed, disabled, freeze, touches (21 files), stubs, traceability and consistency all passed.
- **Bundle:** `ngBuild --rerun` gives initial total 701.30 kB and lazy `result-screen` 260.31 kB, with no budget warning.
- **Live check:** on the built `dfs-server.jar` with headless Chrome. The DOM text is recorded under "By-hand check on the built jar" below.

### How it went

| Phase | Attempts | Outcome |
|---|---|---|
| Design | 1 | Passed; you approved it at the design gate. |
| Tests | 1, reopened once | Defect 1: four story tests used single-backslash path literals (`'D:\Backup'` is `D:Backup` in JS). The implementer caught it, it went to the test-author, and it was fixed in 50c4e2b. |
| Implement | 1 | Failed the gate on defect 1 at first, then passed after the test fix (no code change needed). |
| Verify | 1 | This review. |

There were no escalations.

**Verdict: send back to implement for the `docs/design/ui.md` fix only** (mark M-1.5 and M-1 done in the milestone table, and put the DFS-050 "As built" bullet on its own line). All acceptance criteria are met. If you would rather take that two-line docs fix at close, the story is otherwise ready for your approval.

## Summary

- DFS-050.AC-1 met: header only for a selected folder, two tables with their own paginators, `under` sent or left out correctly.
- DFS-050.AC-2 met: exact fractions; chip only when `totalFiles > 0` and every file is copied elsewhere.
- DFS-050.AC-3 met: sides in API order, "partial" or "all have a copy in the other".
- DFS-050.AC-4 met: the note is above the tables; no verdict words anywhere else (tests and live DOM).
- DFS-050.AC-5 met: pin on folder nodes only; replace and unpin work through the tree and the tab.
- DFS-050.AC-6 met: `/pair?a=<pinned>&b=<selected>`, one row.
- DFS-050.AC-7 met: hint with no request for the pinned folder itself or "All folders"; `NESTED_FOLDERS` message shown.
- DFS-050.AC-8 met: Overlaps then Sets, Overlaps selected, Sets cards shown.
- DFS-050.AC-9 met: independent paging, count lines, page 0 on a new selection.
- DFS-050.AC-10 met: empty texts, per-table and comparison progress bars, a per-table alert.
- Tests are behavioral: rendered DOM text, exact request params (`under` absent vs present), harnesses. They do not restate the code.
- Implementer deviations: `matTabContent` is correct and needed. `animationDuration="0ms"` is cosmetic and a product-owner choice.
- No `CLAUDE.md` safety invariant is touched (read-only UI, no deletion).
- Open defect: `docs/design/ui.md` does not mark M-1.5 and M-1 done, and the DFS-050 bullet is fused onto the DFS-049 line. The docs done-item is not met.
- Back to implement for that docs fix, or the product owner accepts it as a fix at close. Everything else is ready for the human gate.

## Acceptance criteria

- **AC-1 met.** `OverlapsTab` has an effect on (`scanId`, `folder`) that calls `selectionChanged()`, which loads both tables with `under` spread in only when non-null. The header comes from `SummaryPage.folder`.
  - The test `DFS-050.AC-1 …` checks that there is no `.folder-header` under All folders, the two headings and both paginators, the requests (`expectOverlaps` checks `params.has('under') === (under !== null)`), and the header placed before `section.summaries`.
  - Live: no header on open; with Backup selected, its header.
- **AC-2 met.** `copiedElsewhereText`, `duplicatesInsideText`, `summaryTotalText` and `isAllCopiedElsewhere` (`totalFiles > 0 && filesWithCopyElsewhere === totalFiles`).
  - Both AC-2 tests check the exact cell texts from the scenario (1.2 GB, 80.0 MB, 1.4 GB), the column headings, the header's label-plus-value strings, and the chip only on full rows (an empty folder has none).
  - The table puts the label in the column heading rather than in each cell. design.md committed to this, and the gate approved it. The AC wording ("A Folders row … read 'Copied elsewhere …'") is satisfied through the heading.
- **AC-3 met.** `pairSideText` and `pairCoverageText`. The test checks the order (side-a then side-b), both texts and both coverage words.
- **AC-4 met.**
  - The test checks the exact note, that the note precedes the header and both tables, and that `/safe|delete|can go/i` matches no text in `tr`, `.folder-header`, `mat-chip`, `.pair-side` or `th` with both sides full.
  - Live: the overlaps tab's whole text, minus the note, has no match in any state.
- **AC-5 met.** Tested in three places:
  - folder-tree: a pin on each node, none on "All folders", it emits the path and does not select;
  - overlaps-tab: label, Unpin emits, replace, null removes the label and the block;
  - result-screen: end to end through the tree, replace, Unpin, and the selection unchanged.
  - Live: pinned Backup, then Photos replaced it, then Unpin cleared it.
- **AC-6 met.** `compare()` sends `getPair({id, a: pinned, b: folder})`. Tested in the tab (exact `a`/`b`, the texts from the scenario) and through the tree in `result-screen.spec.ts`. Live: Backup pinned and Photos selected gave the comparison row.
- **AC-7 met.**
  - `compare()` returns `hint` without a request when `folder === null || folder === pinned`. The test uses `http.expectNone` for both cases, plus `http.verify()`.
  - `NESTED_FOLDERS` goes to `failed` with `failureMessage`, and the test checks `role="alert"` and the message.
  - Live: Photos pinned with Photos\2019 selected showed "…\Photos contains …\Photos\2019"; then All folders showed the hint.
- **AC-8 met.** The test checks the labels' order, that the first tab is selected, that no Sets cards show while Overlaps is selected, and the cards after selecting Sets. Live: the same, with 3 cards.
- **AC-9 met.** The test checks the count lines, page size 10 on both paginators, the next page on Folders requesting `page=1&size=10` with `expectNone` on pairs, size 25 on pairs with `expectNone` on summaries, and the new folder requesting page 0 for both.
- **AC-10 met.** Three tests:
  - empty texts with no paginator or count;
  - the progress bar while summaries load, then a 403 message with `role="alert"` while the pairs row stays;
  - the comparison progress bar.

  The pairs table's own progress bar ("Loading folder pairs") is in the template but no test asserts it. It mirrors the summaries one; this is a minor gap and not required by the scenarios.

## Test quality

- **The assertions are on behavior.** They check rendered DOM text (normalized `textContent`), exact query parameters (including that `under` is absent, not empty), `role` attributes and DOM order (`compareDocumentPosition`). They use Material harnesses for the paginator, buttons, tabs and progress bars, and `http.verify()` after each test. None of them restates the implementation.
- Every story spec uses `provideStubSurfacing()` and `settle(fixture)`.
- **AC-5 and AC-6 are tested both in isolation and through the tree in the result screen**, so the wiring (`folderPinned` → `pinFolder` → `[pinned]`) is proven, not only the parts.
- **Defect 1 (single-backslash literals) is fixed.** I found no remaining unescaped backslash path in the three specs.
- **The superseded-request guards are not tested.** These are the request counters in `loadSummaries`, `loadPairs` and `compare`. They mirror DFS-049's `loadSets` and are not a criterion; noted, not a defect.

## Design conformance and the implementer's deviations

- **Components, classes, texts, the API calls and the page states match design.md.** All 13 checklist items have a disposition (items 4, 6, 8 and 12 are marked not applicable with reasons).
- **Deviation 1, `<ng-template matTabContent>` around the Overlaps tab, is correct and necessary.** Content projected directly into a `mat-tab` is created once and only detached, so without this a hidden Overlaps tab would keep requesting summaries, pairs and `/pair` on every folder click while Sets is shown. `result-screen.spec.ts` rightly forbids that, and the live check confirmed `app-overlaps-tab` is absent while Sets is selected.
  - The effect is what design.md's open note already expected: returning to Overlaps reloads page 0. The design's reasoning about why was wrong; its learnings L-2 claimed the opposite of what Material does with directly projected content.
  - ui.md's as-built text records the effect.
- **Deviation 2, `animationDuration="0ms"` on the tab group, is acceptable but a product choice.** It makes the switch immediate in jsdom (the implementer measured about 500 ms otherwise, longer than `settle()` waits), and it also removes the slide in the real app. This is cosmetic. A test-only `MAT_TABS_CONFIG` provider would have kept the product animation, but the specs were frozen for the implementer. I list it for the product owner.
- **`pinnedFolder.set(null)` in `loadScan`**, which design.md asked for, is present.
- Small template change: `{{ ' ' }}` after the folder path in the table cell, so the cell text reads "<path> all copied elsewhere". Harmless.

## Safety invariants

This ticket is a read-only UI over GET endpoints, so most invariants do not apply:
- **Never delete the last copy, one execution at a time, re-verify at execution time:** no deletion, plan or execution code is touched.
- **Preview is a pure query:** the tab issues only GETs and changes no state.
- **Hard links are not duplicates, no symlink or junction following, empty files excluded:** server-side concerns, not touched. The UI only renders the server's counts.
- **folder-overlap-analysis.md decision 1 (never combine pairs into a verdict)** is the invariant that matters here, and it holds. Each pair side is labelled only "partial" or "all have a copy in the other", the chip says "all copied elsewhere", and the note warns that a copy may itself be deleted. No verdict words appear (AC-4; live DOM confirmed). Two folders each fully covering the other both read "all have a copy in the other", with no suggestion that both can go.

## Docs

- **`CLAUDE.md`, met.** The `server` entry gains one sentence on the Overlaps tab (first, selected on open, own data, note, header, two paged tables, the pin and the Comparison block, Sets second). It is accurate.
- **`docs/design/ui.md`, not met.**
  - U3 gains an accurate "As built (DFS-050, M-1.5)" description. However, it was appended to the same line as the DFS-049 "As built" bullet (`… a "Copy folder path" action per link. - **As built (DFS-050, M-1.5):** …`), so it is not a bullet of its own.
  - The Milestones table (line 164) still reads `DFS-050 Overlaps tab (M-1.5)` without "done", and M-1 is not marked done. The ticket's Scope ("the milestone table marks M-1.5 and M-1 done") and the last done-item require both.

## By-hand check on the built jar

- **Setup:**
  - `dfs-server.jar` built from 8182892, run with `java --enable-native-access=ALL-UNNAMED -jar … --dfs.server.open-browser=false` (PID 13496, stopped afterwards).
  - Headless Chrome 156, driven over DevTools from the project's Node 24 (component test-guide, "The by-hand check without a person").
  - Scratch data, outside the worktree and deleted afterwards:
    - `…\data\Photos\2019` holds a.jpg, b.jpg, c.jpg (200 kB each, random) and unique.jpg (3 kB);
    - `…\data\Photos\2020` holds d.jpg;
    - `…\data\Backup\2019` holds byte copies of a, b and c. Backup is a full copy of part of Photos.
  - Scanned both roots with `POST /api/scans` (minSize 1).
- **Paths:** below, `<D>` stands for the scratch folder `C:\Users\apoch\AppData\Local\Temp\claude\02ad29a6de71421f\f4858304-88e1-47f6-bc82-616e4b79720f\scratchpad\data`. Text is read from the DOM.

1. **Opened on the result screen.**
   - Tabs `["Overlaps","Sets"]`, selected `Overlaps`. No pinned label, no Comparison block, no header.
   - Note: "A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan."
   - Folders "4 folders", headings Folder / Copied elsewhere / Duplicates inside / Total:
     - `<D>\Backup all copied elsewhere | 3 / 3 files · 585.9 KB | 0 files · 0 B | 3 files · 585.9 KB`
     - `<D>\Backup\2019 all copied elsewhere | 3 / 3 files · 585.9 KB | 0 files · 0 B | 3 files · 585.9 KB`
     - `<D>\Photos | 3 / 5 files · 585.9 KB | 0 files · 0 B | 5 files · 637.7 KB`
     - `<D>\Photos\2019 | 3 / 4 files · 585.9 KB | 0 files · 0 B | 4 files · 588.9 KB`
   - Folder pairs "1 folder pair": `<D>\Backup\2019: 3 / 3 files · 585.9 KB` "all have a copy in the other" | `<D>\Photos\2019: 3 / 4 files · 585.9 KB` "partial".
   - 2 paginators, no alert, no verdict word.
2. **Selected Backup.**
   - Header: `<D>\Backup` "Copied elsewhere 3 / 3 files · 585.9 KB", "Duplicates inside 0 files · 0 B", "Total 3 files · 585.9 KB", chip "all copied elsewhere".
   - Folders "1 folder" (`<D>\Backup\2019 …`). Pairs "1 folder pair" (the same pair).
3. **Pinned Backup (Backup still selected).** "Pinned: `<D>\Backup`", "Unpin". Comparison: "Select another folder to compare with the pinned one".
4. **Selected Photos.**
   - Comparison: `<D>\Backup: 3 / 3 files · 585.9 KB` "all have a copy in the other" | `<D>\Photos: 3 / 5 files · 585.9 KB` "partial". Sides are in API order, Backup before Photos.
   - Header `<D>\Photos` "Copied elsewhere 3 / 5 files · 585.9 KB … Total 5 files · 637.7 KB", with no chip.
5. **Expanded Photos, pinned Photos (replacing Backup), selected Photos\2019.**
   - "Pinned: `<D>\Photos`".
   - Comparison alert: "`<D>\Photos` contains `<D>\Photos\2019`" (`role="alert"`).
   - Folders "No folders with copies here" with no paginator (2019 has no subfolders). Pairs still shows its row.
6. **Selected All folders.** Comparison: "Select another folder to compare with the pinned one". No header. 4 folders.
7. **Sets tab.**
   - 3 set cards; `app-overlaps-tab` is not in the DOM.
   - Back to Overlaps: reloaded, with the pin and the hint kept.
8. **Unpin.** No pinned label, no Unpin, no Comparison block.
- **Screenshot (state 4):** the layout reads as designed. The note and the comparison sides are visually separate lines. In the narrow tree column, "Pin for comparison" takes about half the row and wraps the folder label onto two lines.

## Done-items

Verified and ticked:
- every scenario is an automated test and passes;
- `./gradlew build` is green;
- no files outside touches;
- checklist dispositions in design.md;
- `provideStubSurfacing()` and `settle(fixture)` in every story spec;
- no budget warning (initial 701.30 kB, lazy result-screen 260.31 kB);
- the by-hand check on the built jar.

Left unticked: "`CLAUDE.md`'s `server` entry and `docs/design/ui.md` (U3, M-1.5, M-1) describe the Overlaps tab". CLAUDE.md and U3 are done, but M-1.5 and M-1 are not marked in the milestone table (see Docs).
