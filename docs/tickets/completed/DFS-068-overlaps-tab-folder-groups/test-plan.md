# DFS-068: Overlaps tab shows folder groups — test plan

## Criterion → scenario → test

All specs are under `server/frontend/src/app/results/`. `OT` = `overlaps-tab.spec.ts` (describe `OverlapsTab`), `RS` = `result-screen.spec.ts` (describe `ResultScreen`).

| Criterion | Scenario | Test (`it` name) | Spec file | Stub it reaches | Level |
|---|---|---|---|---|---|
| `DFS-068.AC-1` | The heaviest group from the d:/ scan | `DFS-068.AC-1, DFS-068.AC-2, DFS-068.AC-8 reads the heaviest group of the d:/ scan` | OT | `loadGroups` (via `selectionChanged`) | component (TestBed) |
| `DFS-068.AC-1` | (server order, bars scaled to the page's largest) | `DFS-068.AC-1 lists the groups in the server order, the reclaimable bars scaled to the page's largest` | OT | `loadGroups`, `reclaimableWidth` | component (TestBed) |
| `DFS-068.AC-1` | ("in all" size when sets are not all listed or differ) | `DFS-068.AC-1 shows the total as "in all" when the group has sets not listed or sets of different sizes` | OT | `groupSizeText` | component (TestBed) |
| `DFS-068.AC-1` | Loading, empty, error states | `DFS-068.AC-1 shows a progress bar while /groups loads, then the list`; `... says "No folders share files" with no paginator when there are no groups`; `... shows the server message in an alert when /groups is refused` | OT | `loadGroups` | component (TestBed) |
| `DFS-068.AC-1` | Paginator 10, 25, 50 (G3) | `DFS-068.AC-1 pages the groups: 10 per page by default, 10, 25 and 50 offered` | OT | `loadGroups`, `changePage` | component (TestBed) |
| `DFS-068.AC-1` | Overlaps selected on the result screen | `DFS-060.AC-1, DFS-068.AC-1, DFS-068.AC-9 shows the folder groups, not the old tables, once Overlaps is selected` | RS | `loadGroups` | component (TestBed) |
| `DFS-068.AC-2` | The heaviest group from the d:/ scan | (the AC-1 test above, "5 of 190 files") and `DFS-068.AC-2 cuts a long path from the middle and keeps the whole path as the tooltip` | OT | `shortPath`, `sharedText` | component (TestBed) |
| `DFS-068.AC-2` | Two copies in an auto-save folder | `DFS-068.AC-2 shows the short path with the full path as tooltip, "×N" only above 1, and a copy button` | OT | `shortPath`, `copiesEachText`, `shareWidth` | component (TestBed) |
| `DFS-068.AC-3` | Two copies in an auto-save folder | `DFS-068.AC-2, DFS-068.AC-3, DFS-068.AC-8 reads two copies in an auto-save folder` | OT | `isFullyShared` | component (TestBed) |
| `DFS-068.AC-3` | Two fully shared folders | `DFS-068.AC-3, DFS-068.AC-4 marks two fully shared folders and says any one can go, not all` | OT | `isFullyShared`, `fullySharedCount` | component (TestBed) |
| `DFS-068.AC-3` | A folder in other groups | `DFS-068.AC-3 shows "also in N other groups" only for a folder with otherGroups above 0` | OT | `otherGroupsText` | component (TestBed) |
| `DFS-068.AC-4` | Two fully shared folders | (the AC-3 test above) and `DFS-068.AC-4 shows no "any one can go" line for one fully shared folder, or none` | OT | `fullySharedCount` | component (TestBed) |
| `DFS-068.AC-5` | Expanding a group of 25 sets | `DFS-068.AC-5 lists the sets of a group of 25 when it is expanded, 20 of them, then "and 5 more"` | OT | `setDetailText`, `hiddenSets` | component (TestBed) |
| `DFS-068.AC-6` | Filtering by the selected folder | `DFS-068.AC-6 requests /groups under the selected folder, and without it for "All folders"` | OT | `selectionChanged`, `loadGroups` | component (TestBed) |
| `DFS-068.AC-6` | Filtering by the selected folder (host) | `DFS-068.AC-6, DFS-068.AC-9, DFS-050.AC-6 compares the pinned folder with a folder selected in the tree` (asserts `under=C:\Photos`) | RS | `selectionChanged` | component (TestBed) |
| `DFS-068.AC-7` | Showing and hiding small groups | `DFS-068.AC-7 shows "Show 9 small groups", reloads with minBytes 0, then "Hide small groups" reloads without it` | OT | `showSmallGroups`, `hideSmallGroups`, `loadGroups` | component (TestBed) |
| `DFS-068.AC-7` | (nothing shown when 0 hidden; kept under an empty list) | `DFS-068.AC-7 shows no small-groups button when none are hidden`; `DFS-068.AC-7 keeps "Show N small groups" under an empty list` | OT | `loadGroups` | component (TestBed) |
| `DFS-068.AC-8` | The app tag ignores letter case and plain folders | `DFS-068.AC-8 tags "Photo.AUH/x" case-insensitively and leaves "Photos" and its lookalikes plain` | OT | `isAppManaged` | component (TestBed) |
| `DFS-068.AC-8` | (the pure function, five patterns, both separators) | `DFS-068.AC-8 isAppManaged matches the five patterns on any path element, ignoring letter case` | OT | `isAppManaged` | pure function |
| `DFS-068.AC-9` | The old tables are gone and comparison still works | `DFS-068.AC-9 shows no Folders table, Folder pairs table or fixed note, and calls neither /summaries nor /pairs` | OT | `loadGroups` | component (TestBed) |
| `DFS-068.AC-9` | (Comparison block still works) | `DFS-068.AC-9, DFS-050.AC-6 requests /pair with a=<pinned> ...` (OT); `DFS-068.AC-9, DFS-050.AC-5 pins a folder from the tree, ...` and `DFS-068.AC-6, DFS-068.AC-9, DFS-050.AC-6 compares the pinned folder ...` (RS). Kept DFS-050 tests, now answering `/groups`: `DFS-050.AC-5 shows "Pinned: <path>"...`, `DFS-050.AC-7` (hint, NESTED_FOLDERS), `DFS-050.AC-10 shows a progress bar while the comparison loads` | OT, RS | `selectionChanged` | component (TestBed) |
| `DFS-068.AC-2`, `AC-8` (look: tag, bars, columns at Compact and Comfortable) | n/a | not testable in jsdom | | | by hand on the built jar (verifier's live check; user's post-close check on `d:/`) |

## Fixtures and fakes

- HTTP: `provideHttpClient()`, `provideHttpClientTesting()`; `/groups` answered with `expectGroups(http, id, under, minBytes, page, size)` (null `under` / `minBytes` means the parameter must be absent); `/pair` with `expectPair`; `http.verify()` after each test.
- Stub surfacing: `provideStubSurfacing()` and `await settle(fixture)` after every step.
- Fakes behind tokens: none (`CLIPBOARD` is not exercised by the tab's tests; only the copy button's presence and label).
- Data builders (`result-test-data.ts`): `groupSet`, `groupFolder`, `folderGroup`, `groupPage`, `expectGroups`; `side`, `pair`, `expectPair` kept. The summaries and pairs builders (`summary`, `summaryPage`, `pairPage`, `expectSummaries`, `expectPairs`, `expectOverlaps`) were removed with their last users.

## Notes for the implementer

- Test data uses forward-slash paths and `roots` `['D:/']` (or `['D:/Media']`); result-screen passes its own `rootPaths()`.
- Exact texts: header `5 files` / `1 file`; meta `· 10 copies · 41.2 MB each · shared by 2 folders` (`· 2 copies · 100 B each`, `shared by 1 folder`); "in all" forms `· 50 copies · 25.0 MB in all · shared by 2 folders`, `· 4 copies · 5.0 MB in all · shared by 2 folders` (3 MB + 2 MB), `· 6 copies · 3.0 MB in all · shared by 3 folders` (three copies per file), the size being the files' own size, one copy each: `reclaimableBytes × files / (copies − files)` (not the sum of `sharedBytes`, which counts every copy; test data follows /groups' real figures); `5 of 190 files`; `×2`; `fully shared`; `also in 2 other groups` / `also in 1 other group`; `and 5 more`; `Show 9 small groups` / `Hide small groups`; expander title `25 sets`; set detail `1.0 MB · 2 copies`.
- Classes read: `div.group`, `.group-header` (`.group-files`, `.group-meta`, `.group-reclaimable-bytes`, `.bar-fill` width in percent), `li.folder-line` (`.short-path[title]`, `.path-folders`, `.path-name`, `.copies-each`, `.shared-count`, `.share .bar-fill`, `.fully-shared`, `.other-groups`, `.app-tag[title]`, `app-path-copy`), `.fully-shared-note`, `.group-sets` (`MatExpansionPanelHarness`; sets rendered only after expanding), `.group-set`, `.set-name`, `.set-detail`, `.more-sets`, `.groups-paginator`, `.groups-empty`, `.groups-error[role=alert]`, aria-label "Loading folder groups".
- A path under one root is shown relative to it without the root's name; under several roots, with it (`Photos\2019`).
- `BrowserFlowTest` (`resultScreenOpensOnSets`, `folderBrowserFiltersTheViews`) is not touched by the test phase; the implementer rewrites both to assert groups (ticket decision 1), and should add the layout check the test guide asks for (`isVisible()` and bounding-box width above 0 on a group header, a folder line's path and its share figure), run in `:server:e2eTest`.
