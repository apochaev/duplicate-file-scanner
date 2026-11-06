# DFS-050: Add the Overlaps tab to the result screen — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`it` name, inside its `describe`) | Spec file | Stub it reaches | Level |
|---|---|---|---|---|---|
| `DFS-050.AC-1` | The tab shows the selection's summary, its folders and its pairs | `DFS-050.AC-1 shows no header under "All folders", then the selected folder's summary above its tables` | `src/app/results/overlaps-tab.spec.ts` | `OverlapsTab.selectionChanged` | component (TestBed) |
| `DFS-050.AC-2` | A folder fully copied elsewhere, and one partly | `DFS-050.AC-2 reads each folder row and the selected folder's header, with the chip only on a fully copied folder` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged`, `copiedElsewhereText`, `duplicatesInsideText`, `summaryTotalText`, `isAllCopiedElsewhere` | component (TestBed) |
| `DFS-050.AC-2` | An empty folder is not "all copied elsewhere" | `DFS-050.AC-2 gives an empty folder no "all copied elsewhere" chip` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged` | component (TestBed) |
| `DFS-050.AC-3` | A pair with one full side and one partial side | `DFS-050.AC-3 shows side A then side B in the API order, "partial" and "all have a copy in the other"` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged`, `pairSideText`, `pairCoverageText` | component (TestBed) |
| `DFS-050.AC-4` | Two folders each fully covered by the other get no verdict | `DFS-050.AC-4 shows the note above the tables and no verdict wording for two folders each fully covered by the other` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged`, `pairCoverageText` | component (TestBed) |
| `DFS-050.AC-5` | Pinning, replacing and unpinning a folder | `DFS-050.AC-5 shows "Pinned: <path>" with an "Unpin" link that emits unpinned, and nothing once unpinned` | `src/app/results/overlaps-tab.spec.ts` | `OverlapsTab.unpin`, `compare` | component (TestBed) |
| `DFS-050.AC-5` | Pinning, replacing and unpinning a folder | `DFS-050.AC-5 offers "Pin for comparison" on every folder node but not on "All folders", and emits the folder pinned` | `src/app/results/folder-tree.spec.ts` | `FolderTree.pin` | component (TestBed) |
| `DFS-050.AC-5` | Pinning, replacing and unpinning a folder | `DFS-050.AC-5 pins a folder from the tree, replaces the pin, and unpins it, with no selection change` | `src/app/results/result-screen.spec.ts` | `FolderTree.pin`, `ResultScreen.pinFolder`, `unpin`, `compare` | component (TestBed) |
| `DFS-050.AC-6` | Comparing two folders from different roots | `DFS-050.AC-6 requests /pair with a=<pinned> and b=<selected> and shows one pair row in the Comparison block` | `src/app/results/overlaps-tab.spec.ts` | `OverlapsTab.compare` | component (TestBed) |
| `DFS-050.AC-6` | Comparing two folders from different roots | `DFS-050.AC-6 compares the pinned folder with a folder selected in the tree` | `src/app/results/result-screen.spec.ts` | `FolderTree.pin`, `compare` | component (TestBed) |
| `DFS-050.AC-7` | Nothing to compare yet | `DFS-050.AC-7 sends no /pair for the pinned folder itself or "All folders" and shows the hint` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged`, `compare` | component (TestBed) |
| `DFS-050.AC-7` | Nested folders | `DFS-050.AC-7 shows the server message when /pair answers 400 NESTED_FOLDERS` | `src/app/results/overlaps-tab.spec.ts` | `compare` | component (TestBed) |
| `DFS-050.AC-8` | The screen opens on Overlaps | `DFS-050.AC-8 shows the tabs "Overlaps" then "Sets", Overlaps selected on open, and the set cards once Sets is selected` | `src/app/results/result-screen.spec.ts` | `selectionChanged` | component (TestBed) |
| `DFS-050.AC-9` | The two tables page independently | `DFS-050.AC-9 shows count lines and pages each table on its own, then both from page 0 for another folder` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged`, `changeSummariesPage`, `changePairsPage` | component (TestBed) |
| `DFS-050.AC-10` | Nothing overlaps | `DFS-050.AC-10 says there is nothing to show and no paginator when neither table has items` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged` | component (TestBed) |
| `DFS-050.AC-10` | Loading, and one table failing | `DFS-050.AC-10 shows a progress bar while a table loads, then its server message while the other table keeps its rows` | `src/app/results/overlaps-tab.spec.ts` | `selectionChanged` | component (TestBed) |
| `DFS-050.AC-10` | Loading, and one table failing (Comparison block) | `DFS-050.AC-10 shows a progress bar while the comparison loads` | `src/app/results/overlaps-tab.spec.ts` | `compare` | component (TestBed) |

Also changed: DFS-049's `result-screen.spec.ts` now answers the Overlaps tab's two requests on open and selects the Sets tab before reading the cards (decision 4); its tests keep their DFS-049 names.

## Fixtures and fakes

- HTTP: `provideHttpClient()`, `provideHttpClientTesting()`; each request answered with `HttpTestingController` and `flush(<body>, {status, statusText})`; `http.verify()` after each test.
- Stub surfacing: `provideStubSurfacing()` in the providers and `await settle(fixture)` after each step (`src/testing/stub-surfacing.ts`).
- Fakes behind tokens: none for the Overlaps tab; `CLIPBOARD` is `null` in `result-screen.spec.ts` as before.
- Data builders: `summary()`, `summaryPage()`, `side()`, `pair()`, `pairPage()`, `expectSummaries()`, `expectPairs()`, `expectPair()` added to `results/result-test-data.ts`.

## Notes for the implementer

- Requests: `GET /api/scans/<id>/summaries|pairs` with `page`, `size` and `under` (absent for "All folders"); `GET /api/scans/<id>/pair?a=<pinned>&b=<selected>`. A selection change requests page 0 of both tables (the tests do not pin the size then).
- Classes and texts as in `design.md`: `.pinned-folder`, `.unpin`, `.comparison-hint`, `.comparison-pair`, `.comparison-error`, `.folder-header .summary-*`, `.summaries-count`, `.pairs-count`, `.summaries-empty`, `.pairs-empty`, `.summaries-error`, `mat-progress-bar` aria-labels "Loading folders", "Loading the comparison".
- `result-screen.spec.ts` selects the Sets tab through `MatTabGroupHarness`; after that the Overlaps tab is detached, so folder selections request only sets.
- The pin button in the tree has aria-label "Pin for comparison: <folder name>" (the result-screen tests find it by that).
- AC-4's wording check reads rows, header, chips, pair sides and column headers; the note is the only text with "safe" or "deleted".
