# DFS-050: Add the Overlaps tab to the result screen — design

## Summary

Adds `OverlapsTab` (`src/app/results/overlaps-tab.*`), a container component that is the result screen's first tab. It loads its own data through the generated `OverlapsService` (`listSummaries`, `listPairs`, `getPair`). The result screen keeps the selection and gets a new `pinnedFolder` signal, shared by the tree (which pins) and the tab (which shows and unpins it). Format helpers go in `results/result-format.ts`. The tree gets a "Pin for comparison" button on every folder node. No new browser API or wrapper, and no API change.

- **AC-1** The tab's effect on (`scanId`, `folder`) calls `selectionChanged()`, which calls `loadSummaries()` and `loadPairs()` (`under` is left out for "All folders"). The header comes from `SummaryPage.folder`, through the `header` computed.
- **AC-2** `copiedElsewhereText`, `duplicatesInsideText`, `summaryTotalText` and `isAllCopiedElsewhere` (the `.all-copied` chip). In the table each label is the column header; the header block shows label and value together.
- **AC-3** `pairSideText` and `pairCoverageText` (the constants `SIDE_FULL` and `SIDE_PARTIAL`), in `.pair-side` cells A then B.
- **AC-4** `OVERLAP_NOTE` in `.overlap-note`, above the header and tables. No other text on the tab uses "safe", "delete" or "can go".
- **AC-5** `FolderTree.pin`, which emits `folderPinned`; `ResultScreen.pinFolder` and `unpin`; `OverlapsTab.unpin`, which emits `unpinned`. Shows `.pinned-folder` "Pinned: <path>" and an `.unpin` button.
- **AC-6, AC-7** The effect on (`scanId`, `pinned`, `folder`) calls `compare()`. It sets the `Comparison` union: `hint` (no request), `loading`, `loaded` (one `.comparison-pair`), or `failed` (the server's message, `role="alert"`).
- **AC-8** Tabs are now `Overlaps` then `Sets`. The tab group's default index 0 selects Overlaps.
- **AC-9** `changeSummariesPage` and `changePairsPage` each reload only their own table. Page size 10, choices 10/25/50, count line `plural(totalItems, 'folder' | 'folder pair')`.
- **AC-10** Each table and the Comparison block has its own progress bar, alert and empty text (`NO_FOLDERS`, `NO_PAIRS`), set by `loadSummaries`, `loadPairs` and `compare`.

**Placement:** everything is in `server/frontend/src/app/results/`, beside DFS-049's files. Nothing outside `touches`.

**New Material module:** `MatTableModule` (and `MatChipsModule` in this lazy chunk). Initial total goes from 697.22 kB to 701.30 kB; the lazy `result-screen` chunk goes from 205.81 kB to 258.65 kB. No budget warning.

**Not applicable:** checklist items 4 (no browser API), 6 (no input field), 8 (no stream) and 12 (no new style iteration needed).

**Expected breakage:** none. Ten tests in `result-screen.spec.ts` fail, but with `not implemented: DFS-050` (the Overlaps tab now renders on open). The test-author updates them in the test phase (decision 4).

**Look closely at:**
- The tab is a container, not presentational like `SetsTab`.
- The pin button's visible text "Pin for comparison" sits on every tree row, so it costs width.
- Switching to Sets and back re-creates the Overlaps tab, which reloads page 0 of both tables (see open notes).

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `src/app/results/overlaps-tab.ts` (+ `.html`, `.css`) | component (new) | in: `scanId` (string, required), `folder` (string \| null), `pinned` (string \| null); out: `unpinned` (void) | `OverlapsService` | The Overlaps tab: pinned label and Comparison block, note, selected-folder header, Folders table, Folder pairs table, each paged and with its own states |
| `src/app/results/result-format.ts` | helper (changed) | none | none | Adds `copiedElsewhereText`, `duplicatesInsideText`, `summaryTotalText`, `isAllCopiedElsewhere`, `pairSideText`, `pairCoverageText`, `SIDE_FULL`, `SIDE_PARTIAL` |
| `src/app/results/folder-tree.ts` (+ `.html`, `.css`) | component (changed) | adds out: `folderPinned` (string) | unchanged | A `.folder-pin` "Pin for comparison" button on every folder node, not on "All folders" |
| `src/app/results/result-screen.ts` (+ `.html`) | component (changed) | none | unchanged | Owns `pinnedFolder`; `pinFolder(path)` and `unpin()`; tabs "Overlaps" (first, selected) then "Sets" |

Text constants are exported from `overlaps-tab.ts`: `OVERLAP_NOTE`, `NO_FOLDERS`, `NO_PAIRS`, `COMPARE_HINT` and `OVERLAPS_PAGE_SIZE` (10).

The tab's template, top to bottom, with the classes tests use:
1. While a folder is pinned: `.pinned-bar`, containing `.pinned-folder` ("Pinned: <path>") and the `.unpin` button "Unpin".
2. Also while pinned: `section.comparison` (aria-label "Comparison", heading "Comparison"), holding `.comparison-hint`, a progress bar, `.comparison-error[role=alert]`, or `.comparison-pair` with `.pair-side.side-a` and `.pair-side.side-b`. Each side has `.side-text` and `.side-coverage` (`.side-full` when full).
3. `.overlap-note`.
4. `section.folder-header` (aria-label "Selected folder"): `.summary-folder`, then `.summary-copied`, `.summary-inside` and `.summary-total` (each `.summary-label` plus its value), and `mat-chip.all-copied`.
5. `section.summaries` (aria-label "Folders"): a progress bar, `.summaries-error[role=alert]`, `.summaries-count`, `table.summaries-table` (`mat-table`; columns `folder`, `copied`, `inside`, `total`, headed "Folder", "Copied elsewhere", "Duplicates inside" and "Total"; rows `tr.summary-row`; a `mat-chip.all-copied` in the folder cell), `mat-paginator.summaries-paginator` (aria-label "Folder pages") and `.summaries-empty`.
6. `section.pairs` (aria-label "Folder pairs"): the same structure with the `pairs-` prefix. Columns `a` and `b`, headed "Folder A" and "Folder B"; rows `tr.pair-row`; each cell holds a `.pair-side`. Paginator aria-label "Folder pair pages".

## Page states

- **Not connected:** not applicable. The result screen is reached only from the connected scan screen, and its own `getScan` handling (DFS-049) covers a `403`.
- **Loading:** each table shows its own `mat-progress-bar` while its request runs ("Loading folders", "Loading folder pairs"); the Comparison block shows "Loading the comparison".
  - After a new selection, both pages are cleared, so no header and no rows from the previous folder remain.
  - On a page change, the current page stays shown and its paginator is disabled.
- **Empty:**
  - Folders reads "No folders with copies here".
  - Folder pairs reads "No folder pairs share copies here".
  - Neither shows a count line or paginator.
  - Under "All folders" there is no header.
- **Loaded:**
  - Count lines read "34 folders" and "12 folder pairs".
  - A summary row: `D:\Backup\2019`, `340 / 340 files · 1.2 GB`, `0 files · 0 B`, `340 files · 1.2 GB`, and the chip "all copied elsewhere".
  - The header reads "D:\Backup\2019 Copied elsewhere 340 / 340 files · 1.2 GB Duplicates inside … Total …".
  - A pair side reads "C:\Photos\2019: 340 / 400 files · 1.2 GB" followed by "partial" or "all have a copy in the other".
  - The note: "A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan."
- **Pinned:**
  - The tab shows "Pinned: D:\Backup" and "Unpin".
  - With the pinned folder or "All folders" selected, the Comparison block reads "Select another folder to compare with the pinned one".
  - Unpinned: there is no label and no block.
- **Input refused by the form:** not applicable. The tab has no input.
- **Server refusal (`400`, `{code, message}`):** `/pair`'s `NESTED_FOLDERS` message ("C:\Photos contains C:\Photos\2019") shows in `.comparison-error`. A `400` or `403` from `/summaries` or `/pairs` shows in that table's place.
- **Server busy (`503`):** not applicable. These are GETs on an analysis the server already has; any other failure shows `failureMessage` (the server's message, or `LOAD_FAILED`).
- **Load failed:** the message shows with `role="alert"` in place of that table, or in the Comparison block. The other table keeps its rows.
- **Live updates and their end:** not applicable. The tab shows a COMPLETED scan.

## API calls

The service is `OverlapsService` (the client generated from DFS-048's spec). `src/app/api/` is not touched.

- **`listSummaries({ id: scanId, under?, page, size })`**
  - When: on creation, on each new scan or folder (page 0, current size), and on a Folders page change.
  - `under` is left out for "All folders".
  - `200` → `summaries` (with `SummaryPage.folder` as the header).
  - Any failure → `summariesError = failureMessage(failure)`.
- **`listPairs({ id: scanId, under?, page, size })`**
  - When: the same, for Folder pairs.
  - `200` → `pairs`.
  - Any failure → `pairsError`.
- **`getPair({ id: scanId, a: pinned, b: folder })`**
  - When: a folder is pinned and a different, non-null folder is selected, either when either one changes or when the scan does.
  - Not sent when the pinned folder or "All folders" is selected.
  - `200` → `{kind: 'loaded', pair}`.
  - `400` (`NESTED_FOLDERS`, ...), `403` or any other failure → `{kind: 'failed', message: failureMessage(failure)}`.
- **Superseded requests:** every load counts its requests, and an answer to a superseded request is ignored (as in `ResultScreen.loadSets`).

## Browser APIs behind wrappers

None.

## Material components

`OverlapsTab` imports:
- `MatButtonModule` (Unpin)
- `MatChipsModule` (the "all copied elsewhere" chip: a static `mat-chip` in a `mat-chip-set`)
- `MatPaginatorModule`
- `MatProgressBarModule`
- `MatTableModule` (new to the app)

`FolderTree` adds no module; its pin button is a `mat-button`. `ResultScreen` imports `OverlapsTab`.

Harnesses: `MatTableHarness`, `MatPaginatorHarness`, `MatChipHarness`, `MatButtonHarness`, `MatTabGroupHarness` and `MatTreeHarness`.

Bundle (`./gradlew :server:ngBuild`):

| | Before | After the stubs |
|---|---|---|
| Initial total | 697.22 kB | 701.30 kB |
| Lazy `result-screen` chunk | 205.81 kB | 258.65 kB |

No budget warning. Everything new is in the lazily loaded result screen.

## Checklist disposition

1. **The generated client:** `listSummaries`, `listPairs` and `getPair` as they are. No endpoint is missing; `src/app/api/` is not touched. `under` is left out for "All folders".
2. **Promise timing in tests:** every story spec settles with `settle(fixture)` after each step. Both effects run on change detection, which `settle` triggers.
3. **Stubs reachable and surfaced:**
   - Every behavior goes through a throwing stub (see Stubs).
   - The effects call `selectionChanged()` and `compare()` (an async stub, so its rejection is unhandled). Both reach `provideStubSurfacing()` and fail at `settle`.
   - `npmTest` after the stubs: ten tests in `result-screen.spec.ts` fail, each with `not implemented: DFS-050`. No other spec fails.
4. **Browser APIs jsdom lacks:** not applicable: no clipboard, observer or stream is used.
5. **Where the screen sits and when it shows:** first tab of `ResultScreen`'s `mat-tab-group`, under the existing lazy route `#/scans/:id`. It shows once the roots have loaded (DFS-049). No new route or link.
6. **Input and validation:** not applicable: no field. Folders are chosen in the tree, so `RELATIVE_PATH` and `PATH_OUTSIDE_ROOTS` cannot come from typing (decision 3).
7. **Loading, empty and error states:** see Page states. Each table and the Comparison block has its own; alerts use `role="alert"`; sections and tables have aria-labels.
8. **Live updates:** not applicable: the scan is COMPLETED.
9. **Material components and their harnesses:** see Material components; tests use harnesses for the table, paginator, chip, tab group, tree and buttons.
10. **The bundle budget and the system font:** measured above, with no warning. The CSS uses only theme color tokens and `monospace` for paths (as in `sets-tab.css`); no web font.
11. **The live check reads the DOM:** the verifier checks it once on the built jar per the definition of done. Two overlapping folders (one a full copy of part of the other), the tab read on open, a folder selected, a pin, a compare, a nested pair; the text is read from the DOM into `verification.md`.
12. **The dev server and its proxy:** not applicable for the design. Available to the implementer for style iteration, as described in the archetype.
13. **Docs:** the implementer updates `CLAUDE.md`'s `server` entry (the Overlaps tab first, the pin comparison) and `docs/design/ui.md` (U3 as built, M-1.5 and M-1 done).

## Expected breakage

None.

## Open design notes (as of the design phase)

- **State split.** `OverlapsTab` is a container that loads its own pages, unlike `SetsTab`, whose data `ResultScreen` loads. Putting both tables and the comparison into `ResultScreen` would roughly double that component.
  - The selection and the pin stay in `ResultScreen`, since the tree and the tab both need them.
  - The tab follows its inputs through two `effect`s, which read their inputs and run the stub methods `untracked`.
- **Decision 4 is not quite accurate.** Without `preserveContent`, Material attaches only the active tab's content. So the Sets tab's component is created only once it is selected, but `ResultScreen` still requests sets on open, so DFS-049 AC-6 holds.
  - Consequence: going to Sets and back re-creates `OverlapsTab`, which reloads page 0 of both tables (and the comparison). The pin survives.
  - The alternative is `preserveContent` on the tab group, which keeps the page positions but leaves the hidden tab's DOM in place. I chose not to use it; the gate can choose otherwise.
- **The pin button** shows the visible text "Pin for comparison" (aria-label "Pin for comparison: <name>") on every node, pushed to the row's end. The alternatives were a row menu (`MatMenu`, more harness work) or "Pin" alone (which loses the ticket's wording).
- **The table cells hold only the value** ("340 / 340 files · 1.2 GB"). AC-2's label ("Copied elsewhere") is the column header. The selected-folder header shows label and value together.
- **The implementer adds `this.pinnedFolder.set(null)` to `ResultScreen.loadScan`**, so a pin does not carry over to another scan's route. I did not add that line to the stubbed code.
- `SummaryPage.folder` is generated as optional (`folder?:`), but the server sends `null` without `under`. The `header` computed handles both.

## Stubs (as of the design phase)

- **Files stubbed:** `results/overlaps-tab.ts`, `.html` and `.css` (new); `results/result-format.ts`, `results/folder-tree.ts`, `.html` and `.css`, and `results/result-screen.ts` and `.html` (changed).
- **Each method a criterion goes through throws `new Error('not implemented: DFS-050')`.** Which stub each criterion reaches:
  - **AC-1:** `OverlapsTab.selectionChanged`, then `loadSummaries` and `loadPairs`.
  - **AC-2:** `copiedElsewhereText`, `duplicatesInsideText`, `summaryTotalText`, `isAllCopiedElsewhere`, reached after `loadSummaries`.
  - **AC-3:** `pairSideText` and `pairCoverageText`, reached after `loadPairs`.
  - **AC-4:** `selectionChanged`, `loadSummaries`, `loadPairs` and `pairCoverageText`. The note itself is static, so the test reaches a stub only through loading.
  - **AC-5:** `FolderTree.pin`, `ResultScreen.pinFolder` and `ResultScreen.unpin`, `OverlapsTab.unpin`, plus `compare` on every pin change.
  - **AC-6 and AC-7:** `OverlapsTab.compare`.
  - **AC-8:** `selectionChanged`, since the Overlaps tab renders and loads on open.
  - **AC-9:** `changeSummariesPage`, `changePairsPage` and `selectionChanged`.
  - **AC-10:** `loadSummaries`, `loadPairs` and `compare`.
- The template's structure and CSS classes are final; tests find elements by them and by Material harnesses.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: filled in after the design commit.
