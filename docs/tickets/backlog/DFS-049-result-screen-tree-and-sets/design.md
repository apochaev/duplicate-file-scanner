# DFS-049: Show a finished scan as a folder tree with a Sets tab — design

## Summary

**What this adds:** the Angular router with hash URLs (`@angular/router` 22.2.4, added with `--save-exact`, which changed `package.json` and `package-lock.json` by that package only). The shell's `<app-scan-screen />` becomes `<router-outlet />`. A COMPLETED scan row gets an "Open results" link. A new `results/` folder holds the result screen (`ResultScreen`), the folder tree (`FolderTree`, a flat `mat-tree`), the Sets tab (`SetsTab`, cards and a `mat-paginator`), the copy action (`PathCopy`) and pure helpers. Calls: `ScansService.getScan`, then `ResultsService.listFolders` and `listSets`. The clipboard sits behind the `CLIPBOARD` token. The result route is **lazy-loaded** (`loadComponent`): the initial bundle grows from about 563 kB to **697 kB** (the router), and the result screen's 202 kB lazy chunk stays out of it, with no budget warning. Loading it eagerly measured 896 kB, close to the 1 MB warning.

- **AC-1:** `app.routes.ts` (`''` → `ScanScreen`, `scans/:id` → `ResultScreen` lazy, `**` → `''`), `provideRouter(routes, withHashLocation())`. Reaches the stub `ResultScreen.ngOnInit`.
- **AC-2:** `ScanRow`, an `a.open-results` link `[routerLink]="resultLink()"` inside the COMPLETED case only. Stub `resultLink()`.
- **AC-3:** the header's `ul.result-locations` from `getScan`, and `a.back-to-scans` (`routerLink="/"`), shown in every state. Stub `loadScan`.
- **AC-4:** `loadScan` maps the failure through `unavailableMessage`/`failureMessage` (stubs) to `p.result-error[role=alert]`. No tree or tab is shown in that state.
- **AC-5:** `FolderTree`: `rootRows`, `folderLabel` and `expandedChange` (stubs). Children are loaded on the first expand, then cached.
- **AC-6:** `FolderTree.select` emits; `ResultScreen.selectFolder`/`loadSets` (stubs) send `under`, or leave it out.
- **AC-7:** `SetsTab` cards, `setHeading` (stub), `.copy-line` and `.hard-link` lines; the digest is not rendered.
- **AC-8:** `SetsTab.inSelectedFolder` and `isPathUnderFolder` (stubs).
- **AC-9:** `setsSummary` and `SetsTab.changePage`, then `ResultScreen.changePage`/`loadSets` (stubs); page size 50, options 25, 50, 100.
- **AC-10:** `SetsTab.emptyMessage` (stub); progress bars on the screen, the tab and the tree row; alerts with the server's message.
- **AC-11:** `PathCopy.copy`, `containingDirectory` and `browserClipboard` (stubs); the `CLIPBOARD` token is faked in specs.

**Not applicable:** checklist item 6 (no input fields) and item 8 (no stream).

**Expected breakage:** `app.spec.ts` (no router in its TestBed, so no route renders) and `scan-screen.spec.ts` (a COMPLETED row's `RouterLink` needs `ActivatedRoute`). Both were confirmed by running `:server:npmTest`.

**Look closely at:** the lazy route (a deviation not in the ticket, made for the budget), and the tree driving expansion through Material's own model (`matTreeNodeToggle` with `expansionKey` = path) while `rows` holds only the visible rows.

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `package.json`, `package-lock.json` | build | — | — | `@angular/router` `22.2.4` only (decision 7) |
| `src/app/app.config.ts` | config | — | — | adds `provideRouter(routes, withHashLocation())` |
| `src/app/app.routes.ts` | config | — | — | `''` → `ScanScreen`; `scans/:id` → `ResultScreen` (`loadComponent`); `**` → `''` |
| `src/app/app.ts`/`.html` | component | none | `SessionService` | imports `RouterOutlet` instead of `ScanScreen`; `<router-outlet />` once connected |
| `src/app/scans/scan-row.ts`/`.html`/`.css` | component | unchanged | unchanged | imports `RouterLink`; COMPLETED case: `div.scan-done` > `p.scan-counts` + `a.open-results` (stub `resultLink(): string[]`) |
| `src/app/results/result-screen.ts`/`.html`/`.css` | component | none (reads the route) | `ActivatedRoute`, `ScansService`, `ResultsService`, `DestroyRef` | owns the scan, roots, selection, page state; loads in decision 9's order |
| `src/app/results/folder-tree.ts`/`.html`/`.css` | component | `scanId`, `roots: FolderNode[]`, `selected: string \| null`; `folderSelected: string \| null` | `ResultsService` | "All folders" + lazy flat `mat-tree`; exports `FolderTreeRow` |
| `src/app/results/sets-tab.ts`/`.html`/`.css` | component | `page: SetPage \| null`, `folder`, `loading`, `error`, `pageIndex`, `pageSize`; `pageChanged: PageEvent` | none | presentational: summary, cards, paginator, empty/loading/error |
| `src/app/results/path-copy.ts`/`.html`/`.css` | component | `path: string` | `CLIPBOARD` | "Copy folder path" per link, success/failure/fallback |
| `src/app/results/clipboard.ts` | wrapper | — | — | `ClipboardWriter`, token `CLIPBOARD` (`ClipboardWriter \| null`), default `browserClipboard()` |
| `src/app/results/result-format.ts` | helper | — | — | `folderLabel`, `setHeading`, `setsSummary` (reuse `scans/scan-format.ts`'s `formatBytes`, `plural`) |
| `src/app/results/result-path.ts` | helper | — | — | `isPathUnderFolder`, `containingDirectory` (lexical) |
| `src/app/results/result-error.ts` | helper | — | — | message constants; `unavailableMessage`, `failureMessage` |

## Page states

- **Not connected:** as before: "Open the link printed in the console"; no route is rendered, so no scan call is made.
- **Loading:** the result screen shows `mat-progress-bar[aria-label="Loading scan results"]` until `getScan` and the roots have answered. The Sets tab shows `mat-progress-bar[aria-label="Loading duplicate sets"]` while a page loads (the old page stays, the paginator is disabled). A tree row shows `mat-progress-bar[aria-label="Loading subfolders"]` while its children load.
- **Empty:** `p.sets-empty` reads "This scan found no duplicates" under "All folders", or "No duplicates under this folder" with a folder selected. There is no summary and no paginator in this state.
- **Loaded:**
  - The header holds `a.back-to-scans` "Back to scans" (to `#/`) and `ul.result-locations` with one `li` per `ScanEntry.locations` entry.
  - The left column is `nav.folder-tree`. `button.all-folders` "All folders" has `aria-pressed="true"` when selected. Each `mat-tree-node.folder-node` holds `button.folder-select` reading "<name> · <bytes> · <n> files", plus a `button.folder-toggle[matTreeNodeToggle]` (aria-label "Expand <name>"/"Collapse <name>") only when `hasChildren`.
  - The right column is a `mat-tab-group` with one tab, "Sets". `p.sets-summary` reads "<n> duplicate sets · <size> reclaimable". There is one `mat-card.duplicate-set` per set, headed by `mat-card-title.set-heading` "<n> copies · <size> each · <size> reclaimable".
  - Each set has one `li.physical-copy` per copy: the first link is a `div.file-link.copy-line` (`span.file-path` and `time`), and the other links are `div.file-link.hard-link` with `span.hard-link-label` "hard link (same file)". A link under the selected folder gets the class `in-selected-folder` and `span.selected-label` "in selected folder". Every link has an `app-path-copy`.
  - The `mat-paginator` has the options 25, 50 and 100.
- **Input refused by the form:** not applicable: the screen has no input field.
- **Server refusal (`400`, `{code, message}`):** a failed sets page shows `p.sets-error[role=alert]` with the server's `message`. A failed children load shows `p.folder-error[role=alert]` under its row. A failed `getScan` or roots load shows `p.result-error[role=alert]` with the message.
- **Scan unavailable (AC-4):** `p.result-error[role=alert]`. For `404 UNKNOWN_SCAN` it reads "This scan is not in this server run (the server may have restarted)". For a status other than COMPLETED, or a `409 SCAN_NOT_COMPLETED` from `listFolders`/`listSets`, it reads "This scan has not finished yet". The header (with "Back to scans") stays; there is no tree and no tab.
- **Server busy (`503`):** not applicable: only scan start answers `503`. Any `5xx` falls back to the server's message, or to "The results could not be loaded; reload the page" when there is no body.
- **Load failed (no session, `403`):** not reachable from the shell (no route renders without a session). A `403` from a result call (`PATH_OUTSIDE_ROOTS`) shows its message like a `400`.
- **Copy:**
  - Success shows `span.copy-success[role=status]` "Folder path copied", but only after `writeText` resolves.
  - With `CLIPBOARD` null, it shows `span.copy-error[role=alert]` "Clipboard access is not available; select the folder path below and copy it".
  - On a rejected write, it shows "The folder path could not be copied; select it below and copy it".
  - Both failures show `code.copy-fallback` with the folder path, selectable (`user-select: all`).
- **Live updates and their end:** not applicable: a COMPLETED scan does not change.

## API calls

- **`ScansService.getScan({ id })`**
  - When: on each route `id`, first.
  - Answers: `200` COMPLETED → the next two calls; `200` any other status → the "not finished" alert and nothing else; `404 UNKNOWN_SCAN` → the "not in this server run" alert; any other failure → `failureMessage`.
- **`ResultsService.listFolders({ id })`** (the roots)
  - When: after a COMPLETED `getScan`, together with the first `listSets`.
  - Answers: `200` → `roots`, which shows the layout; `409 SCAN_NOT_COMPLETED` → the "not finished" alert; any other failure → a fatal alert with the message.
- **`ResultsService.listFolders({ id, parent })`**
  - When: the first time a node with `hasChildren` is expanded; never again for that node.
  - Answers: `200` → child rows at level+1 beneath it; a failure → the row's `folder-error`.
- **`ResultsService.listSets({ id, under?, page, size })`**
  - When:
    - on opening, with `{ id, page: 0, size: 50 }` and no `under`;
    - on selecting a folder, with `under: path, page: 0`;
    - on selecting "All folders", with `under` left out and `page: 0`;
    - on a paginator event, with its `pageIndex`/`pageSize`.
  - Answers: `200` → `page`; `409 SCAN_NOT_COMPLETED` → the fatal "not finished" alert; any other failure → `setsError`. An answer superseded by a later request is ignored.
- **Unchanged:** `src/app/api/` and `openapi.json`. No endpoint is needed.

## Browser APIs behind wrappers

- **`CLIPBOARD`** (`InjectionToken<ClipboardWriter | null>`, `providedIn: 'root'`) in `results/clipboard.ts`.
  - The default factory, `browserClipboard()`, returns `navigator.clipboard` when it has `writeText`, else null.
  - Specs provide `{ provide: CLIPBOARD, useValue: fake }`, where the fake's `writeText` records its text and returns a Promise the test resolves or rejects, or `useValue: null` for "unavailable".
  - A spec rendering `SetsTab` with sets should provide it too, or the default runs.
- **The router's location:** for an `href` check (AC-2, AC-3), use `provideRouter(routes, withHashLocation())` so links render `#/...`. For browser back (AC-1), use `Location.back()` after two navigations, or `RouterTestingHarness`. The test-author chooses.

## Material components

- **`ResultScreen`:** `MatButtonModule` (the back link), `MatProgressBarModule`, `MatTabsModule`.
- **`FolderTree`:** `MatTreeModule` (`mat-tree`, `mat-tree-node`, `matTreeNodeDef`, `matTreeNodePadding`, `matTreeNodeToggle`), `MatButtonModule`, `MatProgressBarModule`.
- **`SetsTab`:** `MatCardModule`, `MatPaginatorModule`, `MatProgressBarModule`, and `DatePipe` from `@angular/common`.
- **`PathCopy`:** `MatButtonModule`.
- **`ScanRow`:** adds `RouterLink`.
- **Harnesses for tests:**
  - `MatTreeHarness`/`MatTreeNodeHarness`: `expand()` clicks the `matTreeNodeToggle`; `isExpandable()` is false for a node without `hasChildren`; `getLevel()` is the depth plus 1.
  - `getText()` excludes buttons, so read a node's label from `button.folder-select` or through `MatButtonHarness`.
  - The others are `MatButtonHarness`, `MatPaginatorHarness`, `MatTabGroupHarness`, `MatCardHarness` and `MatProgressBarHarness`.
- **Bundle:**
  - Before: about 563 kB, from the last build's `main` plus `styles` on this branch.
  - After: an initial total of 697.14 kB (raw), 144.67 kB transfer. The router accounts for the increase.
  - The `result-screen` lazy chunk is 202.37 kB.
  - No budget warning.

## Checklist disposition

1. **The generated client:** `getScan`, `listFolders` (with and without `parent`) and `listSets` (`under` left out for "All folders"). All of them exist (DFS-047); `src/app/api/` is untouched.
2. **Promise timing in tests:** every story spec uses `provideStubSurfacing()` and `await settle(fixture)` after each step. The result screen's calls are sequential (`getScan`, then roots and sets together), so a test flushes `getScan`, settles, then expects both result requests.
3. **Stubs reachable and surfaced:**
   - Each criterion's path ends in a stub (see "Stubs" below). Template-reached stubs (`folderLabel`, `setHeading`, `setsSummary`, `inSelectedFolder`, `emptyMessage`, `resultLink`, and `rootRows` via `linkedSignal`) surface through `provideStubSurfacing()`.
   - An AC-2 test must render a COMPLETED row, since only that branch reads `resultLink()`.
   - An AC-1 test must navigate to `scans/<id>`, which reaches `ResultScreen.ngOnInit`, because `#/` alone renders the real scan screen.
4. **Browser APIs jsdom lacks:** the clipboard, behind `CLIPBOARD` (above). No `ResizeObserver`/`matchMedia` use of our own. `MatPaginator` and `MatTabs` run in jsdom (Material's own harness tests do).
5. **Where the screen sits and when it shows:**
   - The shell's `router-outlet`, rendered only when connected.
   - Hash routes need no server fallback (decision 1).
   - The result route is lazy, so a spec using `routes` waits for `navigateByUrl`.
6. **Input and validation:** not applicable: the screen has no form fields; the folder filter comes from the tree's own paths.
7. **Loading, empty and error states:** see "Page states". Every alert has `role="alert"`; the regions carry labels: "Scan results", "Scanned folders", "Scan folders", "Folders", "Result views", "Duplicate sets", "Duplicate set pages".
8. **Live updates:** not applicable: only COMPLETED scans are shown, and a completed result does not change; there is no stream.
9. **Material components and their harnesses:** listed under "Material components". The tree uses Material's own expansion model (`expansionKey` = path), so `MatTreeNodeHarness.expand()` and `isExpandable()` work.
10. **The bundle budget and the system font:** the initial total is 697 kB, under 1 MB, because the result route is lazy. The component styles are each about 1 kB, under 4 kB. No `font-family` is set except `monospace` on paths, which matches `scan-row.css`; no web font is loaded.
11. **The live check reads the DOM:** done in verify, on the jar. Scan two folders with duplicates, including a hard link created at test time; open the result from the row; expand a node; select a folder; read the `.set-heading`, `.hard-link` and `.in-selected-folder` text from the DOM; press Copy folder path. Record all of it in `verification.md`.
12. **The dev server and its proxy:** unchanged. A hash route works under `ng serve` as it does on the jar (`/` is always served).
13. **Docs:** the implementer updates `CLAUDE.md`'s `server` entry (the result screen and hash routes, `#/scans/<id>`) and `docs/design/ui.md` (U3 as built, M-1.4 done). Both are in `touches`.

## Expected breakage

- `app.spec.ts`: the shell now hosts `<router-outlet />` and the spec provides no router, so no route renders. "DFS-037.AC-3 shows the heading and Connected" and "DFS-041.AC-10 shows the scan screen once connected" find no `/api/scans` request. The test-author adds `provideRouter(routes, withHashLocation())`.
- `scan-screen.spec.ts`: a COMPLETED row now holds a `RouterLink`, which needs `ActivatedRoute` ("No provider found for `ActivatedRoute`"). DFS-041.AC-2, AC-5/AC-8 and AC-6 fail. The test-author adds `provideRouter([], withHashLocation())`.

## Open design notes (as of the design phase)

- **The result route is lazy-loaded** (`loadComponent`); the ticket did not say. Eager loading measured 895.6 kB initial, about 104 kB under the 1 MB warning, leaving little room for DFS-050's Overlaps tab. Lazy loading measured 697 kB.
- **The roots and the first sets page are requested together** after a COMPLETED `getScan`. Decision 9 fixes only `getScan` first. The layout appears with the roots, and the Sets tab shows its own bar until its page arrives (the AC-10 loading scenario).
- **The summary line is shown only when `totalItems > 0`.** With no sets, only the empty message shows, never "0 duplicate sets · 0 B reclaimable".
- **A hard-link line shows no date** (it is the same file as its copy line, so the date is the same). The ticket asks for the date only on copy lines.
- **Error and fallback texts the ticket left open:** "The results could not be loaded; reload the page" (a failure with no server message), and the two clipboard failure texts under "Page states".
- **A tree children-load failure stays on its row** and does not replace the screen. A `409` can only come from the roots or sets calls.
- **The earlier, interrupted attempt's stubs were reworked:**
  - `plural(n, 'copy')` would read "copys", so the heading text moved to `setHeading`.
  - The tree moved from a self-managed `aria-expanded` to Material's toggle and model.
  - `ngOnChanges` became `linkedSignal`.
  - The unused `loadFailed`/`treeFailure` outputs and `present()` were dropped.
  - The path and format helpers got stubs.
- **Implementation hints:**
  - `ngOnInit` follows `route.paramMap` with `takeUntilDestroyed(destroyRef)`; a new `id` resets the state.
  - Re-expanding a node rebuilds its subtree from `loadedChildren`, honouring the tree's model for descendants that are still expanded (or collapses them with it).
  - `isPathUnderFolder` accepts both separators (`\` and `/`), since a scan holds one kind.

## Stubs (as of the design phase)

- **Files stubbed:**
  - `scans/scan-row.ts` (`resultLink`)
  - `results/result-screen.ts` (`ngOnInit`, `loadScan`, `loadSets`, `selectFolder`, `changePage`)
  - `results/folder-tree.ts` (`rootRows`, `expandedChange`, `select`)
  - `results/sets-tab.ts` (`inSelectedFolder`, `emptyMessage`, `changePage`)
  - `results/path-copy.ts` (`copy`)
  - `results/clipboard.ts` (`browserClipboard`)
  - `results/result-format.ts` (`folderLabel`, `setHeading`, `setsSummary`)
  - `results/result-path.ts` (`isPathUnderFolder`, `containingDirectory`)
  - `results/result-error.ts` (`unavailableMessage`, `failureMessage`)
- **Each throws `new Error('not implemented: DFS-049')`.** The first stub each criterion reaches:
  - **AC-1:** `ResultScreen.ngOnInit`, through a navigation to `scans/<id>`.
  - **AC-2:** `ScanRow.resultLink`, through a COMPLETED row.
  - **AC-3:** `ResultScreen.ngOnInit`, then `loadScan`.
  - **AC-4:** `loadScan`, then `unavailableMessage`.
  - **AC-5:** `FolderTree.rootRows`, then `folderLabel` and `expandedChange`, in a `folder-tree` spec with `scanId` and `roots` set as inputs. In a screen spec it is `loadScan`.
  - **AC-6:** `loadScan`/`loadSets`, then `FolderTree.select` and `ResultScreen.selectFolder`.
  - **AC-7:** `setsSummary`, then `setHeading` (they render in that order), in a `sets-tab` spec with `page` set.
  - **AC-8:** `SetsTab.inSelectedFolder`, then `isPathUnderFolder`. It is also directly testable as a helper.
  - **AC-9:** `setsSummary`, then `SetsTab.changePage`, `ResultScreen.changePage` and `loadSets`.
  - **AC-10:** `SetsTab.emptyMessage`; in the screen, `loadScan`/`loadSets`, then `failureMessage`.
  - **AC-11:** `PathCopy.copy`, then `containingDirectory` (also directly testable for the six-row outline).
- **Final for the test phase:** the template's structure and CSS classes; tests find elements by them and by Material harnesses.
- **Compiled with `./gradlew :server:ngBuild`:** green, no budget warning.
- **Commit:** filled in after the design commit.
