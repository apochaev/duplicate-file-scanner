---
id: DFS-049
title: Show a finished scan as a folder tree with a Sets tab
status: in-review
type: feature
scope: [build, docs]
archetype: component
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
autorun: true
priority: high
lane: frontend
depends_on: [DFS-047, CCW-020]
touches:
  - server/frontend/src/app
  - server/frontend/package.json
  - server/frontend/package-lock.json
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-05
completed:
---

# DFS-049: Show a finished scan as a folder tree with a Sets tab

## Story

As the user of the local DFS server
I want to open a finished scan and browse its folders in a tree, seeing the duplicate sets under the folder I select, largest reclaimable first
So that I can see duplicate groups in the browser and copy the paths of their containing directories

## Context

Milestone M-1 ("see the results of a scan"), step 4 of 5 (`docs/design/ui.md`, "Milestones"). With this ticket the UI first shows duplicates.

- **U3:** left, a lazily loaded folder tree of the scanned roots; right, tabs filtered to the selected folder. This ticket builds the tree and the **Sets** tab (duplicate sets, largest reclaimable first). The **Overlaps** tab (the landing tab once it exists) is DFS-050.
- **U9:** Angular Material and the CDK (tree, paged tables, tabs).
- **DFS-041:** the scan screen in `server/frontend/src/app/scans/` is one page without a router; it lists this server run's scans. A finished scan needs a way to open its result.
- **DFS-047:** `ResultsService` with `listFolders(id, parent?)` (`FolderNode`: path, name, subtree and direct totals, `hasChildren`) and `listSets(id, under?, page, size)` (`SetPage` of `DuplicateSetView`, every physical copy with its links).
- **CCW-020:** the Angular component archetype, written from the scan screen; this is its first live use.

## Decisions

1. **Angular router with hash URLs** (2026-11-02). `provideRouter(routes, withHashLocation())`: `/#/` is the scan screen, `/#/scans/<id>` the result screen. A result has a URL, the back button works, and a reload keeps the open result. The server always serves `/`, so no server-side fallback is needed and the ticket stays inside the `component` archetype and the frontend lane (the archetype excludes how the server serves the page; checklist item 5). Not path URLs (`/scans/<id>` needs a Spring fallback for its routes, a backend-lane request DFS-049 would wait on; can replace the hash later together with that fallback) and not a signal-switched view without a router (no URL, back leaves the app, a reload loses the result). Ends DFS-041 decision 2's "no router".
2. **An "Open results" link on every COMPLETED row; a dead result URL says why** (2026-11-02). A COMPLETED row of the scan screen shows an "Open results" link (a `routerLink` to `#/scans/<id>`) beside its counts line, also when the scan found 0 sets; no other status shows it. The result screen's header shows the scan's folders and a "Back to scans" link. Scans live only for one server run (`ScanRegistry`), so a URL kept across a restart answers `404 UNKNOWN_SCAN`: the screen shows "This scan is not in this server run (the server may have restarted)"; `409 SCAN_NOT_COMPLETED` shows "This scan has not finished yet"; both with `role="alert"` and the back link. Not the whole row clickable (nested interactive elements, ambiguous next to Cancel) and not jumping to the result when a scan finishes (surprising with several scans running).
3. **Tree nodes show name, subtree size and file count in the API's name order; selection filters the Sets tab** (2026-11-02). A node reads "<name> · <subtree bytes> · <files> files" (`FolderNode.name`, `bytes`, `files`), in the order `listFolders` returns (by name). Sizes and counts use the scan screen's helpers in `scans/scan-format.ts` (`formatBytes`, binary units labelled KB/MB/GB; `plural`), so both screens read alike. The roots load with the screen; a node's children load from `listFolders(id, parent=<path>)` when it is first expanded, and only a node with `hasChildren` can expand. On opening, an "All folders" entry above the roots is selected and the Sets tab shows every set of the scan (no `under`); selecting a folder filters the Sets tab to `under=<its path>`, and selecting "All folders" clears the filter. Not each level sorted by size (an explorer that is not alphabetical, and size is not duplication) and not a reclaimable figure per node (needs a new `FolderNode` field from the backend lane, which DFS-049 would wait on; can come later as its own endpoint and screen ticket).
4. **One open card per set; hard links indented under their copy; the selected folder's copies highlighted** (2026-11-02). Each set on the Sets tab is a card, always open, in the order the API returns (largest reclaimable first). Header: "<n> copies · <size> each · <reclaimable> reclaimable", where n is the number of physical copies (`copies.length`). Body: one line per physical copy, its first link's path and modified date (Angular's `date: 'medium'`, as the scan screen); a copy's other links are indented beneath it and marked "hard link (same file)", so a hard link never reads as a copy (CLAUDE.md, "Hard links are not duplicates"). With a folder selected, every link whose path lies under it is marked "in selected folder": its path equals the folder's or starts with the folder's path plus a separator, so `C:\Photos2\x.jpg` is not under `C:\Photos`. The digest is not shown. Not collapsed cards (a click to see where the copies are, which is the screen's purpose) and not a flat table of paths (hard links would look like copies).
5. **A Material paginator, a summary line per folder, and explicit loading, empty and error states** (2026-11-02). Under the cards a `mat-paginator`, page size 50 (the API's default), choices 25, 50 and 100; changing the folder returns to page 0. Above the cards: "<totalItems> duplicate sets · <totalReclaimableBytes> reclaimable" for the current filter. With no sets: "This scan found no duplicates" under "All folders", "No duplicates under this folder" for a folder, and no paginator. A progress bar while a page of sets or a tree level loads; a failed load shows an alert (`role="alert"`) with the server's message. Not "Load more" (the page grows without bound on a big scan) and not Previous/Next only (no page-size choice, no sense of how many pages).
6. **Archetype `component`, gates `[design, done]`** (2026-11-02). An Angular screen with its services and Vitest tests and no endpoint change: the `component` archetype, in its first live use. The pipeline pauses after the architect, then runs unattended to the product-owner review: this ticket brings in the router and the app's first nested-data feature, so the stubs, routes and component split are checked before tests and code are written against them. Not `[done]` (a disliked component split or routing found only after tests depend on it) and not a third gate after the tests (the gate's checks already trace every criterion by ID). No deletion is involved, so the design gate is a choice, not a safety requirement.
7. **DFS-049 adds `@angular/router` itself** (2026-11-02). Not installed today (absent from `server/frontend/package.json`, `package-lock.json` and `node_modules/@angular/` on 2026-11-02). The architect adds `@angular/router` pinned to `22.2.4`, the version of the other `@angular/*` runtime packages, with `npm install --save-exact` so `package.json` and `package-lock.json` change together, and confirms `./gradlew :server:ngBuild`. A deliberate, single exception to the `component` archetype's exclusion of `package.json` ("the frontend build itself"): that exclusion is about build configuration (`angular.json`, budgets, Gradle tasks), and one runtime package of the same framework release changes none of it; the budget check (checklist item 10) covers the router's size. Flag for `/story learn` as a possible archetype clarification, together with `archetype.md`'s "routing does not exist yet (DFS-041 decision 2)", which this ticket makes stale (`.claude/archetypes/` is outside `touches`). Not a separate hand-built ticket first (a ticket and a close before the pipeline can start) and not dropping the router (decision 1).
8. **No Playwright here; a separate draft after DFS-050** (2026-11-02). Playwright (a dev dependency, a browser download, a Gradle task, a server per run) is build work the `component` archetype excludes. DFS-049 relies on its Vitest tests and the archetype's manual check on the built jar, read from the DOM (checklist item 11). A frontend-lane draft for the first end-to-end flow (scan a fixture folder, open the result, tree, Sets and Overlaps; `ui.md` D6), `depends_on: [DFS-050]`, is filed right after this ticket's `ready` commit. Not a first flow here (outside the archetype, the slowest route to the screen).
9. **The result screen loads `getScan` first, then the result** (2026-11-02). Opening `#/scans/<id>` calls `ScansService` `GET /api/scans/{id}` first: its `locations` fill the header (AC-3); a `404` shows the "not in this server run" alert; a `progress.status` other than COMPLETED shows the "has not finished yet" alert, and the screen stops there without calling `listFolders` or `listSets`. Only for a COMPLETED scan does it call `listFolders(id)` and `listSets(id)`; a `409 SCAN_NOT_COMPLETED` from either still maps to the "has not finished yet" alert as a fallback. One predictable order, the header shows the folders the user chose, and each alert traces to one call. Not the three calls in parallel (three requests racing to report the same failure, tests pinning which wins, and the header waits for `getScan` anyway) and not the header from the roots `listFolders` returns (a root given as a link is resolved, so it may not read as the user typed it, and `404`/`409` would come only from `/folders`).

10. **Copy the containing directory from each duplicate path** (2026-11-02, user priority). Every displayed file link, including a hard-link alias, has a "Copy folder path" action. It copies that link's immediate parent directory as plain text, without the filename or added quotes; preserve drive roots, UNC share roots, POSIX roots, spaces and Unicode. Show "Folder path copied" only after success. If clipboard access fails or is unavailable, show a clear error and the selectable directory path for manual copying. Put the browser clipboard behind an injectable wrapper so tests can fake success and failure. Copying changes no scan or disk state. This is part of the first useful result screen; opening Explorer and deletion remain out of scope.
## Acceptance criteria

The full ID of each criterion is `DFS-049.AC-<n>`.

- **AC-1** The app routes with hash URLs: `#/` shows the scan screen and `#/scans/<id>` shows the result screen for scan `<id>`; loading the page at `#/scans/<id>` directly (a reload) shows that result, and the browser's back from a result returns to the scan screen.
- **AC-2** On the scan screen, every COMPLETED scan's row (including one with 0 duplicate sets) shows an "Open results" link to `#/scans/<id>`; a QUEUED, RUNNING, CANCELLING, CANCELLED or FAILED row shows none.
- **AC-3** The result screen's header shows the scan's folders and a "Back to scans" link to `#/`.
- **AC-4** The result screen calls `getScan(id)` before any result call: when it answers `404 UNKNOWN_SCAN` the screen shows "This scan is not in this server run (the server may have restarted)"; when the scan's status is not COMPLETED it shows "This scan has not finished yet" and calls neither `listFolders` nor `listSets`; a `409 SCAN_NOT_COMPLETED` from `listFolders` or `listSets` also shows "This scan has not finished yet"; each message has `role="alert"` and the "Back to scans" link, and no tree or Sets tab is shown.
- **AC-5** The result screen shows the scan's roots from `listFolders(id)` as tree nodes reading "<name> · <subtree size> · <n> files", in the order the API returns; a node expands only when `hasChildren` is true, and its children are requested with `parent=<its path>` only when it is first expanded.
- **AC-6** On opening, "All folders" is selected and the Sets tab requests sets without `under`; selecting a folder requests the Sets tab's first page with `under=<its path>`, and selecting "All folders" again requests it without `under`.
- **AC-7** Each set is a card, in the API's order, headed "<n> copies · <size> each · <reclaimable> reclaimable" with n the number of physical copies; each physical copy is one line with its first link's path and modified date, and its other links are indented beneath it and marked "hard link (same file)"; the digest is not shown.
- **AC-8** With a folder selected, every link whose path equals that folder's or starts with it plus a separator is marked "in selected folder", and no other link is (a sibling with the same prefix, such as `C:\Photos2` for `C:\Photos`, is not); with "All folders" selected, no link is marked.
- **AC-9** The Sets tab shows "<totalItems> duplicate sets · <totalReclaimableBytes> reclaimable" for the current filter and a paginator of page size 50 (choices 25, 50, 100); choosing another page or size requests it with that `page` and `size`, and changing the folder requests page 0.
- **AC-10** With no sets, the Sets tab shows "This scan found no duplicates" under "All folders" and "No duplicates under this folder" for a folder, without a paginator; while a page of sets or a tree level loads a progress bar is shown; a failed load shows the server's message with `role="alert"`.

- **AC-11** Every file link shown in a duplicate set has a "Copy folder path" action that copies its immediate parent directory as plain text, preserving Windows drive/UNC roots and POSIX roots, spaces and Unicode; success is announced only after the write succeeds, while rejection or an unavailable clipboard shows an error and a selectable parent path for manual copy, without changing the scan.
## Scenarios

```gherkin
Feature: Result screen: folder tree and Sets tab

  @AC-1
  Scenario: A result has its own hash URL
    Given the session is connected and scan "7f3c…" is COMPLETED
    When the page is loaded at "/#/scans/7f3c…"
    Then the result screen for scan "7f3c…" is shown, not the scan screen

  @AC-1
  Scenario: Back returns to the scan screen
    Given the result screen for a COMPLETED scan was reached from the scan screen
    When the user goes back in the browser
    Then the URL is "/#/" and the scan screen is shown

  @AC-2
  Scenario: Only completed scans can be opened
    Given the scan list holds a COMPLETED scan with 3 duplicate sets, a COMPLETED scan with 0 sets, a RUNNING scan and a FAILED scan
    Then both COMPLETED rows show an "Open results" link to "#/scans/<their id>"
    And the RUNNING and FAILED rows show no such link

  @AC-3
  Scenario: The result header names the scan and leads back
    Given scan "7f3c…" of "C:\Photos" and "D:\Backup" is COMPLETED
    When its result screen is shown
    Then the header lists "C:\Photos" and "D:\Backup"
    And a "Back to scans" link points to "#/"

  @AC-4
  Scenario: A result URL from an earlier server run
    Given getScan answers 404 UNKNOWN_SCAN for scan "0bad…"
    When the page is loaded at "/#/scans/0bad…"
    Then an alert says "This scan is not in this server run (the server may have restarted)"
    And the "Back to scans" link is shown and no folder tree or Sets tab is
    And neither listFolders nor listSets was called

  @AC-4
  Scenario: A result URL for a scan still running
    Given getScan answers scan "7f3c…" with status RUNNING
    When the page is loaded at "/#/scans/7f3c…"
    Then an alert says "This scan has not finished yet" with the "Back to scans" link
    And neither listFolders nor listSets was called

  @AC-4
  Scenario: A result call answers 409
    Given getScan answers scan "7f3c…" with status COMPLETED
    And listFolders answers 409 SCAN_NOT_COMPLETED
    When the page is loaded at "/#/scans/7f3c…"
    Then an alert says "This scan has not finished yet" with the "Back to scans" link
    And no folder tree or Sets tab is shown

  @AC-5
  Scenario: Roots and lazily loaded children
    Given listFolders for scan "7f3c…" returns roots "C:\Photos" (12884901888 bytes, 3210 files, hasChildren true) and "D:\Backup" (900 bytes, 2 files, hasChildren false)
    When the result screen is shown
    Then the tree shows "Photos · 12.0 GB · 3210 files" before "Backup · 900 B · 2 files"
    And no request with a parent was made yet
    When the user expands "Photos"
    Then listFolders is called once with parent "C:\Photos" and its children appear under it
    And "Backup" offers no expand control

  @AC-6
  Scenario: Selecting a folder filters the Sets tab
    Given the result screen of scan "7f3c…" is shown
    Then "All folders" is selected and listSets was called without under
    When the user selects "Photos"
    Then listSets is called with under "C:\Photos" and page 0
    When the user selects "All folders"
    Then listSets is called without under and page 0

  @AC-7
  Scenario: A set with a hard link
    Given listSets returns one set of size 2097152 and reclaimableBytes 2097152 with two copies: copy 1 with links "C:\Photos\a.jpg" and "C:\Photos\link-to-a.jpg", copy 2 with link "D:\Backup\a.jpg"
    When the Sets tab is shown
    Then the card's header reads "2 copies · 2.0 MB each · 2.0 MB reclaimable"
    And "C:\Photos\a.jpg" and "D:\Backup\a.jpg" are copy lines with their modified dates
    And "C:\Photos\link-to-a.jpg" is indented under "C:\Photos\a.jpg" and marked "hard link (same file)"
    And the set's digest appears nowhere on the page

  @AC-8
  Scenario: Copies in the selected folder are highlighted
    Given the set above is shown and the user selects folder "D:\Backup"
    Then "D:\Backup\a.jpg" is marked "in selected folder"
    And neither "C:\Photos\a.jpg" nor its hard link is marked

  @AC-8
  Scenario: A folder with a common prefix is not "under"
    Given a set with links "C:\Photos\a.jpg" and "C:\Photos2\a.jpg"
    When the user selects folder "C:\Photos"
    Then "C:\Photos\a.jpg" is marked "in selected folder" and "C:\Photos2\a.jpg" is not

  @AC-9
  Scenario: Paging through the sets
    Given listSets answers totalItems 120 and totalReclaimableBytes 5368709120 for scan "7f3c…" without under
    When the Sets tab is shown
    Then it reads "120 duplicate sets · 5.0 GB reclaimable" and listSets was called with page 0 and size 50
    When the user goes to the next page through the paginator
    Then listSets is called with page 1 and size 50
    When the user chooses page size 100
    Then listSets is called with size 100
    When the user selects folder "C:\Photos"
    Then listSets is called with under "C:\Photos" and page 0

  @AC-10
  Scenario: No duplicates
    Given listSets answers no items and totalItems 0
    Then the Sets tab says "This scan found no duplicates" under "All folders" and shows no paginator
    When the user selects folder "D:\Backup" and listSets again answers no items
    Then it says "No duplicates under this folder"

  @AC-10
  Scenario: Loading and a failed load
    Given listSets has not answered yet
    Then a progress bar is shown on the Sets tab
    When listSets fails with 400 and message "The path is not absolute"
    Then an alert shows "The path is not absolute"

  @AC-11
  Scenario Outline: Copy the directory containing a duplicate
    Given a duplicate link with path "<file>"
    When the user chooses its "Copy folder path" action and the clipboard accepts the write
    Then the clipboard contains exactly "<folder>"
    And "Folder path copied" is announced
    Examples:
      | file                         | folder                |
      | C:\Photos\a.jpg              | C:\Photos             |
      | C:\a.jpg                     | C:\                   |
      | \\nas\share\a.jpg            | \\nas\share\          |
      | /photos/a.jpg                | /photos               |
      | /a.jpg                       | /                     |
      | C:\Family Photos\été\a.jpg   | C:\Family Photos\été  |

  @AC-11
  Scenario: A hard-link alias has its own containing directory
    Given a physical copy has links "C:\Photos\a.jpg" and "D:\Aliases\a.jpg"
    When the user copies the folder path for "D:\Aliases\a.jpg"
    Then the clipboard receives "D:\Aliases", not "C:\Photos"

  @AC-11
  Scenario Outline: Clipboard failure retains a manual copy option
    Given a duplicate link "C:\Photos\a.jpg" and clipboard access is "<state>"
    When the user chooses "Copy folder path"
    Then an alert explains the failure and "C:\Photos" is visible and selectable
    And no success message is shown and the scan is unchanged
    Examples:
      | state       |
      | unavailable |
      | rejected    |
```

## Scope

**Touches:**
- `server/frontend/src/app`: the routes and `provideRouter(routes, withHashLocation())` in `app.config.ts`, the shell (`app.ts`/`.html`) hosting a `router-outlet` once connected, the "Open results" link in `scans/scan-row.*`, and a new feature folder `results/` (the result screen, the folder tree, the Sets tab, their specs). Existing specs that assert what the shell or a row renders will need updating for the router, by the test-author in the test phase: `app.spec.ts` (the shell shows the scan screen once connected) and `scans/scan-screen.spec.ts` (rows by status).
- `server/frontend/package.json`, `server/frontend/package-lock.json`: `@angular/router` `22.2.4` only (decision 7).
- `CLAUDE.md`: the `server` entry says what the page shows (the result screen, hash routes).
- `docs/design/ui.md`: U3 describes the screen as built; the milestone table marks M-1.4 done.

**Out of scope:**
- The Overlaps tab (DFS-050).
- The folder detail view listed live from disk (U4, milestone M-2).
- Building a deletion plan, single-set picks (U6, milestone M-3).
- Show in Explorer and file previews (U8, milestone M-4).
- A reclaimable figure per tree node (decision 3; needs a new `FolderNode` field).
- Path URLs and a server-side route fallback (decision 1).
- Playwright end-to-end flows (decision 8; a separate draft).
- Editing `.claude/archetypes/component/` (decision 7's notes go to `/story learn`).

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the Vitest tests)
- [x] No files changed outside the paths listed under Touches
- [x] Every checklist item of the `component` archetype is addressed in `design.md` or marked not applicable with a one-line reason
- [x] Every story spec uses `provideStubSurfacing()` and `settle(fixture)`
- [x] The production build prints no budget warning, with `@angular/router` and the new Material modules (tree, tabs, paginator) included (verified: initial total 697.22 kB raw, 144.78 kB transfer; lazy `result-screen` chunk 205.81 kB; no warning)
- [x] `server/frontend/package.json` and `package-lock.json` differ from `main` only by `@angular/router` `22.2.4` (decision 7)
- [x] Checked by hand once on the built jar: a real scan of two folders with duplicates opened from its row, the tree expanded, a folder selected, a hard link shown under its copy, the text read from the DOM and recorded in `verification.md`
- [x] `CLAUDE.md`'s `server` entry and `docs/design/ui.md` (U3, M-1.4) describe the result screen

## Open questions

None.
