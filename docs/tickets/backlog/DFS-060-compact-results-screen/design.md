# DFS-060: Compact read-only results screen — design

## Summary

Changes the result screen in `server/frontend/src/app/results/` (lazy route `#/scans/<id>`). No new endpoint and no API change. The same three calls as before (`getScan`, `listFolders`, `listSets`), still through the generated client.

- **AC-1**: `result-screen.html` puts `mat-tab label="Sets"` first, so it is selected on open. Overlaps comes second and is unchanged (still `matTabContent`). Reaches the `readDensity` stub, which runs when `ResultScreen` is created.
- **AC-2**: header `dl.result-figures` from `headerFigures(scan, totals)` in `result-format.ts`. `totals` is a new signal holding the latest unfiltered `SetPage`. The start time is `<time>` with `yyyy-MM-dd HH:mm`.
- **AC-3**: `.space-bar` and `.space-legend` from `spaceSegments(scan, totals)`, with `barPercent` giving 0 when the scan has 0 bytes.
- **AC-4**: `sortByDuplicateSpace` (`folder-tree.ts`) orders every tree level. Each row is a grid: `.folder-name`, `.folder-bar` (`duplicateWidth`), `.folder-size`, `.folder-duplicate-size`. "Pin for comparison" keeps its text and label but shows only on hover or focus.
- **AC-5**: `table.sets-table` with one `tbody.duplicate-set` per set: a `tr.set-header` (`setName`, `copiesText`, "… each", `reclaimableWidth` bar), then one `tr.copy-row` per copy.
- **AC-6**: `.links-badge` "<n> links" whose `title` is `linksTitle(copy)`, and `.selected-badge` "in selected" from `copyInSelectedFolder`. No hard-link rows remain.
- **AC-7**: `displayPath(path, roots, budget)` and `fileName` in `result-path.ts`, with `PATH_BUDGET = 62`. The full path is `.short-path`'s `title`. `PathCopy.copy()` now copies the full path (button "Copy").
- **AC-8**: `mat-button-toggle-group.sets-filter` ("All sets" / "Copies under <folderName()>"), shown only while a folder is selected. It emits `filterChanged`, which calls `ResultScreen.setFilter`. A new `filterBySelection` signal decides whether `under` is sent.
- **AC-9**: Compact/Comfortable toggle in the header. `chooseDensity` stores the choice through the new `DENSITY_STORAGE` token (`result-density.ts`), and `SetsTab.densityClass()` applies it.
- **AC-10**: the progress bar, empty messages and alerts are unchanged in text and role. Reached through `densityClass()` on `SetsTab`, or `readDensity` on `ResultScreen`.

Checklist items not applicable: 6 (no input), 8 (no stream), 12 (dev server: optional).

New wrapper: `DENSITY_STORAGE` (local storage). Material: `MatButtonToggleModule` added, `MatCardModule` dropped. The lazy chunk is 261.93 kB and the initial total 705.73 kB, unchanged and with no budget warning.

Expected breakage: `app.routes.spec.ts`, `path-copy.spec.ts`, `folder-tree.spec.ts`, `result-screen.spec.ts`, `sets-tab.spec.ts`, `BrowserFlowTest`.

Look closely at: the path rule's edge cases (Open design notes 1 and 2), the dropped Sets summary line, and that DFS-053.AC-7 changes too, since the copy button now copies the file path.

No tests read: only a grep of the existing specs and `BrowserFlowTest` for selectors and texts this design replaces.

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `src/app/results/result-screen.ts`/`.html`/`.css` | component (changed) | none (route param `id`) | `ActivatedRoute`, `ScansService`, `ResultsService`, `DENSITY_STORAGE` | Header (folders, start time, density toggle, figures, bar); Sets tab first, then Overlaps; new state `totals`, `filterBySelection`, `density`, `rootPaths` |
| `src/app/results/sets-tab.ts`/`.html`/`.css` | component (changed) | in: `page`, `folder`, `filtered`, `roots`, `density`, `loading`, `error`, `pageIndex`, `pageSize`; out: `pageChanged: PageEvent`, `filterChanged: boolean` | none | The grouped table, the filter toggle, paginator, loading/empty/error states |
| `src/app/results/folder-tree.ts`/`.html`/`.css` | component (changed) | unchanged (`scanId`, `roots`, `selected`; `folderSelected`, `folderPinned`) | `ResultsService` | Rows sorted by duplicate space, with a bar and two size columns; `sortByDuplicateSpace` exported |
| `src/app/results/path-copy.ts`/`.html` | component (changed) | in: `path` | `CLIPBOARD` | Copies the full path (was: its folder); button "Copy", status "Copied" |
| `src/app/results/result-path.ts` | helper (changed) | none | none | New `displayPath`, `ShortPath`, `ELLIPSIS`, `fileName`; `isPathUnderFolder` and `containingDirectory` kept |
| `src/app/results/result-format.ts` | helper (changed) | none | none | New `headerFigures`, `spaceSegments`, `barPercent`, `copiesText` and their label constants; `folderLabel`, `setHeading` and `setsSummary` removed (no spec imports them); the Overlaps helpers unchanged |
| `src/app/results/result-density.ts` | helper and token (new) | none | none | `Density`, `DENSITY_KEY` (`dfs.results.density`), `DensityStorage`, `DENSITY_STORAGE`, `browserStorage`, `readDensity`, `writeDensity` |
| `server/src/test/java/.../BrowserFlowTest.java` | e2e test (test-author) | none | none | DFS-053 flows updated to the new markup (see Expected breakage) |
| `CLAUDE.md`, `docs/design/ui.md` | docs (implementer) | none | none | The result screen's sentences and U3 |

## Page states

- Not connected: not applicable. The shell shows the result route only once connected (DFS-049, unchanged).
- Loading: `mat-progress-bar[aria-label="Loading scan results"]` until the roots load. In the Sets tab, `mat-progress-bar[aria-label="Loading duplicate sets"]` while a page loads (AC-10). A folder level shows "Loading subfolders".
- Empty: `.sets-empty` reads "This scan found no duplicates" with "All folders" or with "All sets", and "No duplicates under this folder" with a folder filtering (AC-10). With 0 scanned bytes, the header bar has three segments of width 0 and the legend reads "0 B" for each (AC-3).
- Loaded, header: `.result-locations li` per location. `.result-started` reads "Started <time datetime=startedAt>yyyy-MM-dd HH:mm</time>". `dl.result-figures` holds four `.figure` entries, each a `dt.figure-label` and a `dd.figure-value`: "Reclaimable" / "118.4 GB", "Duplicate sets" / "8412", "Duplicate files" / "21930", "Scanned" / "474.0 GB · 76214 files". `.space-bar` holds `.space-segment.segment-reclaimable|segment-kept|segment-unique`, each with `style.width` in %. `.space-legend li.legend-<kind>` reads "Reclaimable · 118.4 GB", "Last copies, always kept · 47.5 GB" and "Unique · 308.1 GB". `mat-button-toggle-group.density-toggle[aria-label="Row density"]` offers "Compact" and "Comfortable". The figures and bar appear once `totals` is set.
- Loaded, tree: "All folders", then a `.folder-columns` header ("Folder", "Size", "Duplicates"). Each `mat-tree-node` row has `button.folder-select[aria-pressed]` (title: the full path) containing `.folder-name`, `.folder-bar > .folder-bar-fill`, `.folder-size` and `.folder-duplicate-size`. `.folder-toggle` and `.folder-pin` (aria-label "Pin for comparison: <name>") are unchanged.
- Loaded, sets: with a folder selected, `mat-button-toggle-group.sets-filter[aria-label="Sets shown"]` holds `.filter-all` "All sets" and `.filter-under` "Copies under <name>". Then `table.sets-table` (in a `section.sets-tab.density-compact|density-comfortable`) and `mat-paginator[aria-label="Duplicate set pages"]` (25/50/100).
  - `tr.set-header` holds `.set-name` "ubuntu.iso", `.set-count` "3 copies", `.set-size` "1.5 GB each", `.bar-fill` (width %) and `.set-reclaimable-bytes` "3.0 GB".
  - `tr.copy-row` (`.in-selected-folder` when marked) holds `.short-path[title=<full path>]`, made of `.path-folders` and `.path-name`. It may carry `.links-badge` "<n> links" (title: the paths, one per line) and `.selected-badge` "in selected". After the path come `.copy-modified time` (`yyyy-MM-dd`) and `app-path-copy`.
  - `app-path-copy` is a button "Copy" with aria-label "Copy path of <path>". After a resolved write it shows `.copy-success` (`role="status"`) "Copied".
- Input refused by the form: not applicable, there is no input.
- Server refusal (`400`): not applicable. The calls take no user input, and paths come from the API.
- Server busy (`503`): not applicable. No command is sent.
- Load failed: unchanged from DFS-049. `p.result-error[role="alert"]` shows "This scan is not in this server run" / "This scan has not finished yet" or the server's message. `p.sets-error[role="alert"]` shows a failed page and `p.folder-error[role="alert"]` a failed level. In `PathCopy`, `.copy-error[role="alert"]` shows `CLIPBOARD_UNAVAILABLE` / `CLIPBOARD_REJECTED`, plus `code.copy-fallback` with the full path.
- Live updates: not applicable. The scan is COMPLETED.

## API calls

- `ScansService.getScan({ id })`: on route change. `200` → `scan`; not COMPLETED → "This scan has not finished yet"; `404` → "This scan is not in this server run" (unchanged).
- `ResultsService.listFolders({ id })` (roots) and `({ id, parent })` (on first expand): unchanged. The answer is sorted in the browser (decision 3); `/folders` answers each level complete.
- `ResultsService.listSets({ id, under?, page, size })`: on load, on folder selection, on paginator change, and on the filter toggle. `under` is sent only while a folder is selected and `filterBySelection` is true (AC-8). Every page answered for a request without `under` is also stored in `totals` (the header's source, decision 1). The first page of a scan is always unfiltered. `409` → fatal "not finished"; other failures → `setsError` (unchanged).
- `src/app/api/` untouched. `FolderNode.duplicateBytes` and `SetPage.totalFiles`/`totalBytes` come from DFS-059 and are in the committed `openapi.json`.

## Browser APIs behind wrappers

- **Local storage**: `DENSITY_STORAGE` (`InjectionToken<DensityStorage | null>`, `providedIn: 'root'`). Its default factory `browserStorage()` returns `window.localStorage`, or null when unavailable or when access throws. The spec provides a fake `{ getItem, setItem }` backed by a `Map`, empty or pre-filled. To check that a choice is "restored on the next load", the test creates the component a second time with the same fake. `readDensity` reads `dfs.results.density` and accepts only `compact`/`comfortable` (anything else, a null storage or a throw → `compact`). `writeDensity` ignores a null storage and a throwing `setItem`.
- **Clipboard**: `CLIPBOARD` (existing, DFS-049), unchanged. It now receives the full path.
- **Tooltips**: native `title` attributes, readable with `getAttribute('title')`. No Material tooltip and no overlay (see Open design notes).

## Material components

- `ResultScreen`: `MatButtonModule`, `MatButtonToggleModule` (new: density toggle), `MatProgressBarModule`, `MatTabsModule`, plus `DatePipe`.
- `SetsTab`: `MatButtonToggleModule` (new: filter toggle), `MatPaginatorModule`, `MatProgressBarModule`, plus `DatePipe`. `MatCardModule` is removed.
- `FolderTree`: `MatButtonModule`, `MatProgressBarModule`, `MatTreeModule` (unchanged). `button.folder-select` is now a plain `<button>`, so the row's grid columns can be styled without reaching into Material's label span.
- `PathCopy`: `MatButtonModule` (unchanged).
- Tests drive the toggles with `MatButtonToggleHarness` / `MatButtonToggleGroupHarness`, tabs with `MatTabHarness`, the paginator with `MatPaginatorHarness`, the tree with `MatTreeNodeHarness`, and buttons with `MatButtonHarness` (the plain `folder-select` button is clicked directly).
- Bundle, after the stubs compile (`./gradlew :server:ngBuild`): initial total 705.73 kB raw. The lazy `result-screen` chunk is 261.93 kB. No budget line in the output. The results screen stays lazy, so the initial bundle does not grow from this ticket.

## Checklist disposition

1. The generated client: the same three methods as above; no new endpoint is needed (DFS-059 supplies the new fields); `src/app/api/` untouched; `under` is left out when not filtering.
2. Promise timing in tests: every story spec uses `settle(fixture)` after each step.
3. Stubs reachable and surfaced: every behavior is reached through a stub, as listed under Stubs. None is inside a `try` that would turn it into a message (`loadScan`/`loadSets` catch every error), and none is in a `subscribe` callback. Template and handler stubs are surfaced with `provideStubSurfacing()`. `readDensity` throws synchronously from `ResultScreen`'s constructor. `:server:npmTest` was run once, and the failures that are not the stub error are listed under Expected breakage.
4. Browser APIs jsdom lacks: local storage goes behind `DENSITY_STORAGE`. jsdom has a `localStorage`, but the ticket wants a token so tests stay isolated and deterministic. The clipboard stays behind `CLIPBOARD`. Tooltips are native `title` attributes, so no overlay or hover simulation is needed.
5. Where the screen sits and when it shows: unchanged, the lazy route `#/scans/:id` reached from "Open results". The header toggle and filter live inside the screen.
6. Input and validation: not applicable. There is no form, only toggles with fixed values.
7. Loading, empty and error states: kept from DFS-049 with the same texts, labels and `role="alert"` (Page states). The empty text follows the filter: "This scan found no duplicates" when "All sets" is chosen.
8. Live updates: not applicable. A COMPLETED scan does not change.
9. Material components and their harnesses: see Material components. Overlaps stays in `matTabContent` (DFS-050.L-4). Sets stays as direct tab content: it is presentational and its parent loads the data, so it does not request anything while hidden.
10. The bundle budget and the system font: measured above, with no warning. The results screen is lazy. Text uses theme tokens (`--mat-sys-title-large` for figure values). Only paths use `monospace`, as `.result-locations` already did. No web font.
11. The live check reads the DOM: the verifier runs the built jar on a real scan with duplicates and hard links. It compares the screen with `docs/design/mockups/results-screen-prototype-2026-11-07.png`, reading figures, legend, row order, short paths and badges from the DOM, checks that the browser console is free of errors, and records the result in `verification.md`.
12. The dev server and its proxy: not applicable to the gates. It is available for style iteration (`bootRun --args=--server.port=8080`, then `npm_run_start` on `127.0.0.1:4200`).
13. Docs: the implementer rewrites the result-screen sentences in `CLAUDE.md`'s `server` entry (Sets first, header figures and bar, folder list order and columns, the grouped table with badges, the path rule, the density toggle and its storage token, the copy button copying the full path) and `docs/design/ui.md` U3.

## Expected breakage

- `app.routes.spec.ts`: navigating to `#/scans/<id>` creates `ResultScreen`, whose constructor reaches the `readDensity` stub. The router swallows the error, so the test times out or leaves `GET /api/scans` open instead of failing with the stub's error. It passes again once the stubs are implemented.
- `path-copy.spec.ts`: the button is now "Copy" (aria-label "Copy path of <path>") and copies the full path, not its folder. The DFS-049.AC-11 tests look for "Copy folder path" and expect the folder. The test-author updates them to DFS-060.AC-7.
- `folder-tree.spec.ts`: fails on the `sortByDuplicateSpace` stub now. After implementation, its assertions on "<name> · <size> · <n> files" labels and on API order no longer hold (rows are sorted by duplicate space, then name). The test-author updates them.
- `result-screen.spec.ts`: fails on the `readDensity` stub now. After implementation, it breaks on Overlaps-first assumptions (DFS-050.AC-8 and `openLoaded(..., 'Overlaps')` without selecting the tab), on the folder labels, on `.sets-summary` (removed), and on `.file-link`/`.in-selected-folder .file-path` selectors. The test-author updates them.
- `sets-tab.spec.ts`: fails on the `densityClass` stub now. After implementation, the cards (`.duplicate-set` as a card, `.set-heading`, `.copy-line`, `.hard-link`, `.hard-link-label`, `.selected-label`), `.sets-summary` and "Copy folder path" are gone. The test-author rewrites them against the table.
- `BrowserFlowTest`: e2e only (`:server:e2eTest`, not part of `check`). The test-author updates these flows:
  - DFS-053.AC-3 and the flow at line 209: Sets is now selected on open; select "Overlaps" first.
  - DFS-053.AC-5: `.duplicate-set` is still one per set, but `.sets-summary`, `.set-heading`, `.physical-copy`, `.hard-link` and `.hard-link-label` become `tr.copy-row` count, `.set-count`/`.set-size`, and `.links-badge` "2 links".
  - The `.selected-label` count at line 142 becomes `.selected-badge`.
  - DFS-053.AC-7: "Copy folder path of" becomes "Copy path of <file path>", and the clipboard now holds the file path.
  - The folder helper's filter on `name + " ·"` becomes `.folder-name` with exact text.

## Open design notes (as of the design phase)

1. **Head with several roots**: the head kept whole is the root's name (only with several roots) plus the first folder under the root. Both are kept when shortening. The ticket leaves open whether the root name counts as "the first folder".
2. **Path edge cases the ticket leaves open**: a path under no root is shown absolute, with the same shortening. A root without a name element (`C:\`, `/`) is spelled without its trailing separator. The name never shrinks below its extension plus three characters, and the head is never cut, so a long head can exceed the budget. The extension starts at the last dot unless the name starts with that dot. Trailing folders are added back nearest the name first, stopping at the first that does not fit. A file directly in its root has empty `folders`.
3. **Budget**: one fixed `PATH_BUDGET = 62` characters (the prototype's ~62-character column). It does not adapt to the window width, which would need `ResizeObserver` behind another token.
4. **Sets summary line dropped**: "<n> duplicate sets · <bytes> reclaimable" duplicated the header figures. With a folder filtering, the paginator's range ("1 – 50 of 120") gives the count.
5. **Copy button copies the full path** (decision 5), so `PathCopy` changes for its only user and `containingDirectory` is now unused. It is kept so `result-path.spec.ts` stays green, and removing it is optional. DFS-053.AC-7's flow changes with it, beyond the AC-3/AC-5 flows the ticket's Context names.
6. **Tooltips are native `title`**, not `MatTooltip`: no overlay module, readable from the DOM in Vitest and Playwright. Unlike `MatTooltip`, a `title` is not shown on keyboard focus.
7. **"Pin for comparison"** keeps its text and aria-label, so existing harness queries and the e2e `getByLabel` still match. In the compact list it is shown only on row hover or button focus (`opacity`), so it stays in the accessibility tree and keyboard order.
8. **The filter toggle shows only with a folder selected.** Selecting a folder, or "All folders", sets `filterBySelection` back to true. With "All sets", copies under the selected folder still carry "in selected". "In selected" marks a copy when any of its links is under the folder.
9. **Tab labels** stay "Sets" and "Overlaps", without counts. Folder pairs is a later ticket.

## Stubs (as of the design phase)

- Files stubbed: `result-density.ts` (new), `result-path.ts`, `result-format.ts`, `result-screen.ts`/`.html`/`.css`, `sets-tab.ts`/`.html`/`.css`, `folder-tree.ts`/`.html`/`.css`, `path-copy.ts`/`.html`.
- Each method a criterion goes through throws `new Error('not implemented: DFS-060')`. Which stub each criterion reaches:
  - AC-1: `readDensity`, from `ResultScreen`'s constructor.
  - AC-2: `headerFigures` (unit), and `readDensity` through the screen.
  - AC-3: `spaceSegments` and `barPercent` (unit), and `readDensity` through the screen.
  - AC-4: `sortByDuplicateSpace` (unit, and on every `FolderTree` render) and `FolderTree.duplicateWidth`.
  - AC-5: `SetsTab.densityClass`, `setName`, `copiesText`, `reclaimableWidth`, and `fileName` (unit).
  - AC-6: `SetsTab.linksTitle` and `copyInSelectedFolder` (after `densityClass`).
  - AC-7: `displayPath` (unit), `SetsTab.shortPath` and `PathCopy.copy`.
  - AC-8: `SetsTab.folderName`, `chooseFilter`, and `ResultScreen.setFilter`.
  - AC-9: `readDensity`, `writeDensity`, `browserStorage`, `ResultScreen.chooseDensity` and `SetsTab.densityClass`.
  - AC-10: `SetsTab.densityClass` (always evaluated on the section), or `readDensity` through the screen.
- For the implementer: `ResultScreen.loadScan`, `loadSets` and `selectFolder`, and `SetsTab.emptyMessage`, are still DFS-049 code. Their comments say what DFS-060 adds: resetting `totals` and `filterBySelection`, `under` only while filtering, storing unfiltered pages in `totals`, and `NO_DUPLICATES` under "All sets".
- The template's structure and CSS classes are final. Tests find elements by them and by Material harnesses.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: filled in by the orchestrator after the design commit.
