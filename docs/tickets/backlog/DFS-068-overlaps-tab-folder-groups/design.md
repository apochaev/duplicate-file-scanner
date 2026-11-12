# DFS-068: Overlaps tab shows folder groups — design

## Summary

The Overlaps tab (`overlaps-tab.ts`, the result screen's second tab, lazy via `matTabContent`) swaps its Folders and Folder pairs tables, the fixed note and the folder header for one list of groups from `GET /api/scans/{id}/groups` (`OverlapsService.listGroups`, already in the generated client). The Pinned bar and Comparison block (`getPair`) stay as built in DFS-050. No new browser API, no new route, no new file: the change is in the three `overlaps-tab.*` files plus a two-input binding in `result-screen.html` (real, not a stub); the specs `overlaps-tab.spec.ts` and `result-screen.spec.ts` are rewritten by the test-author.

Acceptance criteria and the stub each reaches (all stubs throw `not implemented: DFS-068`):
- AC-1 header rows from `/groups`: `loadGroups` (every list test goes through it), `filesText`, `groupMetaText`, `groupSizeText`, `reclaimableWidth`.
- AC-2 folder line: `shortPath` (`displayPath` with `roots()` and a fixed 64-character budget), `sharedText`, `shareWidth`, `copiesEachText`; the `app-path-copy` button.
- AC-3 "fully shared", "also in N other groups": `isFullyShared`, `otherGroupsText`.
- AC-4 "Fully shared folders: any one can go, not all.": `fullySharedCount` (shows at 2 or more).
- AC-5 expander (`mat-expansion-panel.group-sets`, content created on expand): `setDetailText`, `hiddenSets` ("and N more").
- AC-6 `under=` follows the browser selection: `selectionChanged`, `loadGroups`.
- AC-7 "Show N small groups" / "Hide small groups": `showSmallGroups`, `hideSmallGroups`, `loadGroups` (`minBytes: 0` or none).
- AC-8 "app" tag: exported pure `isAppManaged(path)` and `APP_FOLDER_PATTERNS`.
- AC-9 old tables gone: removed from the template; the tab calls `listGroups` and `getPair` only. `compare()` and `unpin()` are kept real, unchanged.

Placement: `server/frontend/src/app/results/`, inside the existing lazy `OverlapsTab`. Checklist items marked not applicable: 4 (browser APIs), 6 (input and validation), 8 (live updates), 12 (dev server).

Look at closely:
- **Wiring in `result-screen.html` (done).** The tab gets two new optional inputs, `roots` (for `displayPath`, as Sets) and `density` (`density-compact`/`density-comfortable`). The architect added the real binding `[roots]="rootPaths()" [density]="density()"` to `<app-overlaps-tab>` (both files are now in `touches`); `:server:ngBuild` is green.
- **`result-screen.spec.ts` (in `touches`; the test-author rewrites its three Overlaps tests).** They answer `/summaries` and `/pairs` today and must answer `/groups` (see Expected breakage). The architect does not edit it.
- "<size> each" needs every set of the group listed, but `/groups` lists only 20: with `totalSets` above the listed sets the header shows "<total> in all" (sum of the folders' `sharedBytes`).
- A new folder selection or scan hides small groups again.

Expected breakage (the user approves this list): `overlaps-tab.spec.ts` (the old tables' tests), `result-screen.spec.ts` (three Overlaps tests, `DFS-060.AC-1`, `DFS-050.AC-5`, `DFS-050.AC-6`), both rewritten by the test-author and both in `touches`; `BrowserFlowTest` (two flows, `e2eTest` only). Existing tests read for the design: none (`result-screen.spec.ts` and `BrowserFlowTest` were grepped for the selectors the design replaces, not read in full).

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `src/app/results/overlaps-tab.ts` | component (`OverlapsTab`, `app-overlaps-tab`) | in: `scanId` (required), `folder`, `pinned`, new `roots` (`readonly string[]`, `[]`), new `density` (`Density`, `'compact'`); out: `unpinned` | `OverlapsService` | the groups list, paginator, small-group button; Comparison unchanged |
| `src/app/results/overlaps-tab.html` | template | | | structure below |
| `src/app/results/overlaps-tab.css` | style | | | group, header, folder line, bar, tag styles; old table styles dropped |
| `src/app/results/overlaps-tab.spec.ts` | spec | | | test-author rewrites it |
| `src/app/results/result-screen.html` | template (real change) | | | `<app-overlaps-tab>` gains `[roots]="rootPaths()" [density]="density()"` |
| `src/app/results/result-screen.spec.ts` | spec | | | test-author rewrites the three Overlaps tests to answer `/groups` |
| `src/app/results/result-test-data.ts` | test data | | | test-author adds group builders (`folderGroup`, `groupFolder`, `groupPage`, `expectGroups`); not written by the architect |

Template structure (CSS classes final): `section.overlaps-tab.density-*` > `.pinned-bar` (`.pinned-folder`, `button.unpin`), `section.comparison` (unchanged), then `section.groups[aria-busy]` > `mat-progress-bar` (label "Loading folder groups"), `p.groups-error[role=alert]`, `p.groups-empty`, and per group `div.group` > `div.group-header` (`.group-files`, `.group-meta`, `.group-reclaimable` with `.bar`/`.bar-fill` and `.group-reclaimable-bytes`), `ul.group-folders` > `li.folder-line` (`.short-path[title]` with `.path-folders` and `.path-name`, `.copies-each`, `.share` with `.bar` and `.shared-count`, `.tag.fully-shared`, `.other-groups`, `.tag.app-tag[title]`, `app-path-copy.folder-copy`), `p.fully-shared-note`, `mat-expansion-panel.group-sets` (title `plural(totalSets, 'set')`, content `ul.group-set-list` > `li.group-set` with `.set-name` and `.set-detail`, `p.more-sets`). After the groups: `mat-paginator.groups-paginator` (label "Folder group pages", 10/25/50, default 10), `button.hide-small` ("Hide small groups", while `smallShown`) or `button.show-small` ("Show N small groups", when `hiddenSmall` > 0).

## Page states

- Not connected: not applicable: the result screen shows only a connected page; the tab is created only with a loaded scan.
- Loading: `mat-progress-bar` "Loading folder groups", `aria-busy` on `section.groups`.
- Empty: `totalItems` 0: "No folders share files" (`p.groups-empty`), no paginator; "Show N small groups" still shows when `hiddenSmall` > 0.
- Loaded: header row per group in the server's order; text `filesText` "5 files" (`plural`), `groupMetaText` "· 10 copies · 41.2 MB each · shared by 2 folders", the reclaimable bytes through `formatBytes`; folder line texts "×2", "5 of 190 files", "fully shared", "also in 2 other groups" ("1 other group"), "app".
- Input refused by the form: not applicable: no input.
- Server refusal (`400`, `403`, `409`): `failureMessage(failure)` in `p.groups-error` (`role="alert"`), the old pattern.
- Server busy (`503`): not applicable: `/groups` has no 503.
- Load failed: as the refusal, `LOAD_FAILED` when the failure has no message.
- Live updates: not applicable: the scan is COMPLETED.

## API calls

- `OverlapsService.listGroups({ id, under?, minBytes?, page, size })`: on creation and when `scanId` or `folder` changes (effect → `selectionChanged` → `loadGroups`), on paginator change, on "Show/Hide small groups". `under` is the selected folder, left out for "All folders"; `minBytes` is left out unless `smallShown` (then `0`); `page` from 0, `size` 10 by default. `200` → `groups`; `400`/`403`/`404`/`409` → `error`. An answer to a superseded request is ignored.
- `OverlapsService.getPair({ id, a, b })`: unchanged (`compare()`).
- `listSummaries` and `listPairs` are no longer called (AC-9).

## Browser APIs behind wrappers

None. The copy button is the existing `PathCopy`, which already uses the `CLIPBOARD` token. The path is not measured (no `ResizeObserver`): `FOLDER_PATH_BUDGET` is a constant 64.

## Material components

`MatExpansionModule` (new to this tab; `mat-expansion-panel`, `matExpansionPanelContent`; harness `@angular/material/expansion/testing`), `MatPaginatorModule`, `MatProgressBarModule`, `MatButtonModule` (as before; `MatChipsModule` and `MatTableModule` leave the tab's imports). Production build after the stubs compile: Initial total 689.04 kB (lazy `result-screen` chunk 218.27 kB), no budget warning; the tab is in the lazy chunk, so the initial bundle is unaffected.

## Checklist disposition

1. The generated client: `listGroups` and `getPair` only; `src/app/api/` untouched (`listGroups`, `GroupPage`, `FolderGroupView`, `GroupFolderView`, `GroupSetView` are already generated from the committed `openapi.json`). `under` and `minBytes` are left out when not chosen.
2. Promise timing in tests: specs use `settle(fixture)` after every step.
3. Stubs reachable and surfaced: every group test goes through `loadGroups` (called from an effect, so its rejection reaches `provideStubSurfacing()`); helper stubs are called from the template. No stub is reached from an RxJS `subscribe`. Existing specs that break are listed below. Pure-helper tests (`isAppManaged`) hit the exported function's stub.
4. Browser APIs jsdom lacks: not applicable: the tab uses none new (clipboard already behind `CLIPBOARD`).
5. Where the screen sits: unchanged, the second tab inside `<ng-template matTabContent>` of `result-screen.html`. New inputs `roots` and `density` are bound in `result-screen.html` (`[roots]="rootPaths()" [density]="density()"`, in `touches`, done).
6. Input and validation: not applicable: no input.
7. Loading, empty and error states: see Page states.
8. Live updates: not applicable: COMPLETED scans only.
9. Material components and their harnesses: see above; the expander is driven with `MatExpansionPanelHarness`, the paginator with `MatPaginatorHarness`, the buttons with `MatButtonHarness`.
10. The bundle budget and the system font: no budget warning; no font set.
11. The live check reads the DOM: the verifier's job (fixture folder with an `X.auh/Contents` subfolder, Compact and Comfortable); the post-close check on `d:/` is the user's.
12. The dev server and its proxy: not applicable: no style iteration needed at design time.
13. Docs: `CLAUDE.md` `server` entry (Overlaps tab text), `docs/design/ui.md` U3 "as built", `docs/design/folder-groups.md` status line, all in `touches`, written in the implement phase.

## Expected breakage

- `overlaps-tab.spec.ts`: asserts the Folders and Folder pairs tables, the note and `/summaries`/`/pairs` requests; the test-author rewrites it (in `touches`).
- `result-screen.spec.ts`: three tests (`DFS-060.AC-1 shows the folder summary and the Folders table once Overlaps is selected`, `DFS-050.AC-5 pins a folder...`, `DFS-050.AC-6 compares the pinned folder...`) answer `/summaries` and `/pairs` through `openOverlaps` and `expectSummaries`; they must ask for `/groups` instead. In `touches`; the test-author rewrites them.
- `BrowserFlowTest`: `resultScreenOpensOnSets` asserts `.summaries-table` and `folderBrowserFiltersTheViews` asserts `.folder-header .summary-folder`; both are rewritten (ticket decision 1). Runs only in `:server:e2eTest`, not in the red gate.

## Open design notes (as of the design phase)

- `roots` and `density` inputs, and the binding in `result-screen.html`, are added by the architect (the ticket says "using Sets' rule" and "Compact and Comfortable" but names no input); `result-screen.html` is now in `touches`.
- The path budget is the constant `FOLDER_PATH_BUDGET` = 64 rather than a measured one (no `ResizeObserver` in the tab).
- "<size> each" requires all sets listed and equal; otherwise "<total> in all", the sum of `sharedBytes`.
- "Show N small groups" uses `plural` ("Show 1 small group"); "also in N other groups" likewise ("1 other group"). "N of M files" is always "files".
- Selecting a folder (or opening another scan) resets small groups to hidden and the page to 0; changing the page size keeps both.
- The old exports `OVERLAP_NOTE`, `NO_FOLDERS`, `NO_PAIRS` leave `overlaps-tab.ts`; `copiedElsewhereText`, `duplicatesInsideText`, `summaryTotalText`, `isAllCopiedElsewhere` stay in `result-format.ts` (not in `touches`), now unused by the tab, with their spec.
- `result-test-data.ts` is left to the test-author (the group builders are test code).

## Stubs (as of the design phase)

- Files stubbed: `overlaps-tab.ts` (with `overlaps-tab.html` and `overlaps-tab.css`, structure final). `result-screen.html` carries the real two-input binding (no stub).
- Throwing `new Error('not implemented: DFS-068')`: `isAppManaged`, `selectionChanged`, `loadGroups`, `showSmallGroups`, `hideSmallGroups`, `changePage`, `filesText`, `groupMetaText`, `groupSizeText`, `reclaimableWidth`, `shortPath`, `copiesEachText`, `sharedText`, `shareWidth`, `isFullyShared`, `otherGroupsText`, `fullySharedCount`, `setDetailText`, `hiddenSets`. Real and unchanged: `compare`, `unpin`, `densityClass`, the two effects.
- The template's structure and CSS classes are final; tests find elements by them and by Material harnesses.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: filled in after the design commit.
