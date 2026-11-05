# DFS-049: Show a finished scan as a folder tree with a Sets tab — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`it` name, inside its `describe`) | Spec file | Stub it reaches | Level |
|---|---|---|---|---|---|
| `DFS-049.AC-1` | A result has its own hash URL | `DFS-049.AC-1 shows the result screen when the page is loaded at #/scans/<id>` | `src/app/app.routes.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed, App + routes) |
| `DFS-049.AC-1` | Back returns to the scan screen | `DFS-049.AC-1 shows the scan screen at #/ and goes back to it from a result` | `src/app/app.routes.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed, `Location.back()` on a mock location) |
| `DFS-049.AC-2` | Only completed scans can be opened | `DFS-049.AC-2 shows "Open results" to #/scans/<id> on a COMPLETED row, also with 0 duplicate sets` | `src/app/scans/scan-row.spec.ts` | `ScanRow.resultLink` | component (TestBed) |
| `DFS-049.AC-2` | Only completed scans can be opened | `DFS-049.AC-2 shows no "Open results" link on a QUEUED, RUNNING, CANCELLING, CANCELLED or FAILED row` (renders a COMPLETED row first, to reach the stub) | `src/app/scans/scan-row.spec.ts` | `ScanRow.resultLink` | component (TestBed) |
| `DFS-049.AC-3` | The result header names the scan and leads back | `DFS-049.AC-3 shows the scan folders and a "Back to scans" link to #/ in the header`; `DFS-049.AC-3 asks getScan first, with GET /api/scans/<id>` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-4` | A result URL from an earlier server run | `DFS-049.AC-4 says the scan is not in this server run for a 404 and requests nothing else` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-4` | A result URL for a scan still running | `DFS-049.AC-4 says the scan has not finished for a RUNNING scan and requests nothing else` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-4` | A result call answers 409 | `DFS-049.AC-4 says the scan has not finished when listFolders answers 409`; `... when listSets answers 409` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-5` | Roots and lazily loaded children | `DFS-049.AC-5 shows the roots as "<name> · <size> · <n> files" ...`; `... expands only a node with children ...`; `... does not ask again when a node is collapsed and expanded again` | `src/app/results/folder-tree.spec.ts` | `FolderTree.rootRows` | component (TestBed, MatTreeHarness) |
| `DFS-049.AC-5` | Roots and lazily loaded children | `DFS-049.AC-5 shows the roots in the API order and loads the children of Photos only when it is expanded` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-6` | Selecting a folder filters the Sets tab | `DFS-049.AC-6 opens with "All folders" selected ...`; `DFS-049.AC-6 filters the sets to a selected folder on page 0 and clears the filter on "All folders"` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-6` | Selecting a folder filters the Sets tab | `DFS-049.AC-6 emits the folder path on selecting a node and null on "All folders"`; `... shows "All folders" selected on opening and the selected folder pressed` | `src/app/results/folder-tree.spec.ts` | `FolderTree.rootRows` | component (TestBed) |
| `DFS-049.AC-7` | A set with a hard link | `DFS-049.AC-7 shows a card per set ...`; `... lists one line per physical copy ... indents the hard link ...`; `... never shows the digest` | `src/app/results/sets-tab.spec.ts` | `setsSummary` / `setHeading` | component (TestBed) |
| `DFS-049.AC-8` | Copies in the selected folder are highlighted; a common prefix is not "under" | `DFS-049.AC-8 marks only the links under the selected folder`; `... does not treat a sibling with the same prefix as under the folder`; `... marks nothing with "All folders" selected` | `src/app/results/sets-tab.spec.ts` | `setsSummary`, then `SetsTab.inSelectedFolder` | component (TestBed) |
| `DFS-049.AC-8` | (helper) | `DFS-049.AC-8 says <path> under <folder> is <bool>` (9 rows) | `src/app/results/result-path.spec.ts` | `isPathUnderFolder` | pure function |
| `DFS-049.AC-8` | Copies in the selected folder are highlighted | `DFS-049.AC-8 marks the copies under the selected folder once its sets have loaded` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-9` | Paging through the sets | `DFS-049.AC-9 shows the summary line and a paginator of size 50 ...`; `... emits the next page and a chosen page size` | `src/app/results/sets-tab.spec.ts` | `setsSummary`, `SetsTab.changePage` | component (TestBed, MatPaginatorHarness) |
| `DFS-049.AC-9` | Paging through the sets | `DFS-049.AC-9 shows the summary and requests the next page and a chosen page size`; `... requests the next page, then page 0 when another folder is selected` | `src/app/results/result-screen.spec.ts` | `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-10` | No duplicates | `DFS-049.AC-10 says there are no duplicates under "All folders" ...` / `... under a selected folder` | `src/app/results/sets-tab.spec.ts`, `result-screen.spec.ts` | `SetsTab.emptyMessage` / `ResultScreen.ngOnInit` | component (TestBed) |
| `DFS-049.AC-10` | Loading and a failed load | `DFS-049.AC-10 shows a progress bar while the sets load and the server message when they fail`; `... while a level loads ...` (tree); `... when the roots cannot be loaded` | `src/app/results/result-screen.spec.ts`, `folder-tree.spec.ts` | `ResultScreen.ngOnInit` / `FolderTree.rootRows` | component (TestBed) |
| `DFS-049.AC-11` | Copy the directory containing a duplicate (6 rows) | `DFS-049.AC-11 copies the folder of <file> as exactly <folder> and announces it after the write succeeds` | `src/app/results/path-copy.spec.ts` | `PathCopy.copy` | component (TestBed, fake clipboard) |
| `DFS-049.AC-11` | Copy the directory containing a duplicate (6 rows) | `DFS-049.AC-11 takes the containing directory of <file> as <folder>` (7 rows) | `src/app/results/result-path.spec.ts` | `containingDirectory` | pure function |
| `DFS-049.AC-11` | A hard-link alias has its own containing directory | `DFS-049.AC-11 copies the alias folder of a hard link, not the other link` | `src/app/results/path-copy.spec.ts` | `PathCopy.copy` | component (TestBed) |
| `DFS-049.AC-11` | Clipboard failure retains a manual copy option | `DFS-049.AC-11 shows an error and the selectable folder when the clipboard is unavailable` / `... when the write is rejected` | `src/app/results/path-copy.spec.ts` | `PathCopy.copy` | component (TestBed) |
| `DFS-049.AC-11` | (every link has the action) | `DFS-049.AC-11 gives every link, hard link included, its own "Copy folder path" action` | `src/app/results/sets-tab.spec.ts` | `setsSummary` | component (TestBed) |

Also updated, not story tests: `src/app/app.spec.ts` (now provides `provideRouter(routes, withHashLocation())`, `provideLocationMocks()` and starts the router) and `src/app/scans/scan-screen.spec.ts` (provides `provideRouter([], withHashLocation())`).

## Fixtures and fakes

- HTTP: `provideHttpClient()`, `provideHttpClientTesting()`; requests matched by `expectScan` / `expectFolders` / `expectSets` in `results/result-test-data.ts` (URL, method, `parent`/`under`/`page`/`size` params, an absent parameter checked as absent); `http.verify()` after each test.
- Stub surfacing: `provideStubSurfacing()` in every story spec and `await settle(fixture)` after each step.
- Fakes behind tokens: `CLIPBOARD` (a `FakeClipboard` whose write the test resolves or rejects, or `null` for unavailable); `EVENT_SOURCE` (a silent stand-in) for specs rendering `ScanRow`.
- Router: `provideRouter(..., withHashLocation())`; `RouterTestingHarness` for the result screen; `provideLocationMocks()` plus `Location.back()` for browser back.
- Data builders: `scanEntry`, `folder`, `duplicateSet`, `setPage` in `results/result-test-data.ts` (a non-spec helper using no test globals, so the app build still compiles).

## Notes for the implementer

- Texts are exact: "Open results", "Back to scans", "This scan is not in this server run (the server may have restarted)", "This scan has not finished yet", "<n> duplicate sets · <size> reclaimable", "This scan found no duplicates", "No duplicates under this folder", "hard link (same file)", "in selected folder", "Folder path copied", and the two clipboard failure texts in `path-copy.ts`.
- Classes read by tests: `a.open-results`, `a.back-to-scans`, `.result-locations li`, `.result-error`, `button.all-folders`, `button.folder-select`, `.folder-error`, `.sets-summary`, `.sets-empty`, `.sets-error`, `.set-heading`, `li.physical-copy`, `.file-link.copy-line/.hard-link`, `.file-path`, `.hard-link-label`, `.in-selected-folder`, `.selected-label`, `button.copy-folder` (aria-label "Copy folder path of <path>"), `.copy-success`, `.copy-error`, `.copy-fallback`, progress bars labelled "Loading duplicate sets" and "Loading subfolders".
- Request order: `getScan` first; then roots and the first sets page together (a test answers both). A paginator size change is requested at the page the paginator reports (page 0 from page 0).
- A hard-link line shows no date (design note); an empty page shows no summary line.
- `ResultScreen` must read the id from the route's `paramMap`; the result-screen specs use `provideRouter([{ path: 'scans/:id', component: ResultScreen }], withHashLocation())`, `app.routes.spec.ts` the real lazy `routes`.
