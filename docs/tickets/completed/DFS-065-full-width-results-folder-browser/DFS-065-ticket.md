---
id: DFS-065
title: Full-width results screen with a folder browser
status: done
type: feature
scope: []
archetype: none
priority: high
lane: frontend
depends_on: [DFS-060]
touches:
  - server/frontend/src/app/app.html
  - server/frontend/src/app/app.css
  - server/frontend/src/app/app.spec.ts
  - server/frontend/src/app/scans/scan-screen.css
  - server/frontend/src/app/results
  - server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-10
updated: 2026-11-11
completed: 2026-11-11
---

# DFS-065: Full-width results screen with a folder browser

## Story

As a user reviewing a scan on a wide monitor
I want the results screen to use the whole window and to browse folders by drilling in from a ranked list
So that I see full paths and where the duplicates are without expanding a narrow tree level by level

## Context

DFS-060 built the compact results screen inside the app shell's 960 px column (`app.css`, `main { max-width: 960px }`), with a lazily loaded `mat-tree` in a ~16rem left column (`folder-tree.ts`) and a fixed 56-character path budget (`PATH_BUDGET` in `sets-tab.ts`) sized for that column. On a wide monitor two thirds of the window stays empty, paths are cut to `Media\…\` although there is room, and with a single scanned root the tree shows one collapsed row that tells nothing until expanded. The tree's only jobs are choosing the folder that scopes the Sets tab (`under=`, DFS-049 AC-6, DFS-060 AC-8) and the Overlaps tab, and pinning a folder for comparison (DFS-050 AC-5).

Design agreed with the user on 2026-11-10 (interview): full width with 24 px gutters; the tree is replaced by a breadcrumb and a table of the current folder's subfolders above the tabs; paths fit the measured column; the shell's toolbar and "Connected" line become one slim bar. The API is unchanged: `GET /api/scans/{id}/folders[?parent=]` already answers every `FolderNode` field used here (DFS-047, DFS-059).

## Acceptance criteria

The full ID of each criterion is `DFS-065.AC-<n>`.

- **AC-1** The shell is one slim toolbar holding the title "Duplicate File Scanner" and, once `/api/session` answers authenticated, "Connected" at its right end; there is no separate "Connected" paragraph. `main` has no maximum width and 24 px side padding (16 px below 600 px). The scan screen keeps its 960 px column, centred.
- **AC-2** The results screen has no left column: the header, the folder browser and the Sets/Overlaps tabs are stacked, each as wide as `main`.
- **AC-3** The folder browser lists the *current level*: the scan's roots when there are several and no folder is selected; the single root's subfolders when there is one root and no folder is selected; otherwise the selected folder's subfolders. A folder's subfolders are requested with `listFolders({ id, parent })` the first time that level is shown, then kept for the screen's life. The list is in `sortByDuplicateSpace` order (DFS-060 AC-4); the first 10 rows show, and a "Show all <n> folders" button shows the rest ("Show fewer" hides them again; the choice resets when the level changes).
- **AC-4** A row shows the folder's name (with "▸" when it `hasChildren`), a bar of `duplicateBytes` over `bytes`, the duplicate size, the duplicate file count, the size and the file count, with the numbers right-aligned in columns.
- **AC-5** Above the list, a breadcrumb starts with "All folders" (pressed when nothing is selected) followed by one crumb per folder from the root down to the selected folder (with one root, the root itself is not a crumb). Selecting a row selects that folder; selecting a crumb selects that folder; "All folders" clears the selection. Selecting behaves as the tree's selection did (DFS-049 AC-6, DFS-060 AC-8: page 0, `under=`, the filter toggle back to "Copies under"). Beside the breadcrumb, the selected folder shows "<size> · <n> files · <duplicate size> in duplicates".
- **AC-6** Every row has a "Pin" button labelled "Pin for comparison: <name>" that pins the folder (DFS-050 AC-5) without selecting it.
- **AC-7** While a level loads, the browser shows a progress bar labelled "Loading subfolders"; a failed load shows the server's message as an alert; a selected folder without subfolders shows "No subfolders".
- **AC-8** The Sets table's path budget follows the File column's measured width: the column's content width divided by the width of one character of the path font, rounded down, less 9 characters for a "<n> links" badge and 13 for an "in selected" badge on that row, never below 24. When the column cannot be measured (no `ResizeObserver`, or zero width) the budget is `PATH_BUDGET` (56) as before.
- **AC-9** The browser flows in `BrowserFlowTest` that used the tree use the folder browser instead and pass in `./gradlew :server:e2eTest`.

## Scenarios

```gherkin
Feature: Full-width results screen with a folder browser

  @AC-1
  Scenario: Slim shell
    Given /api/session answers authenticated
    When the page opens
    Then the toolbar reads "Duplicate File Scanner" and "Connected"

  @AC-3 @AC-5
  Scenario: One root opens inside it
    Given a COMPLETED scan of C:\Archive holding Backups and Downloads
    When the results screen opens
    Then the subfolders of C:\Archive are requested and listed, Backups first
    And "All folders" is pressed

  @AC-3 @AC-5
  Scenario: Drill in and back out
    Given the Archive scan's results are open
    When I select Backups, then old-drive
    Then the breadcrumb reads "All folders › Backups › old-drive" and the sets are requested under C:\Archive\Backups\old-drive
    When I select the "Backups" crumb
    Then Backups' subfolders are listed again without a new folders request

  @AC-3
  Scenario: Several roots
    Given a COMPLETED scan of C:\Photos and D:\Backup
    When the results screen opens
    Then both roots are listed and no subfolders are requested

  @AC-3
  Scenario: Long levels fold
    Given a level of 12 folders
    Then 10 rows show and "Show all 12 folders" shows the other 2

  @AC-6
  Scenario: Pin from a row
    When I press "Pin for comparison: Backup"
    Then the Overlaps tab shows "Pinned: D:\Backup" and the selection is unchanged

  @AC-7
  Scenario: Leaf folder
    When I select a folder without subfolders
    Then "No subfolders" shows and no folders request is made

  @AC-8
  Scenario: Path budget from the column width
    Given the File column is 720 px wide inside its padding and a path character is 7.2 px wide
    Then a copy row without badges gets a budget of 100 characters, one with a links badge 91
```

## Scope

**Touches:**
- `server/frontend/src/app/app.*`, `scans/scan-screen.css`: the slim shell and the full-width `main`.
- `server/frontend/src/app/results`: `folder-browser.*` replaces `folder-tree.*`; `result-screen.*` stacks the layout; `sets-tab.*` measures its File column; specs follow.
- `server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java`: flows use the browser.
- `CLAUDE.md`, `docs/design/ui.md`: describe the screen as built.

**Out of scope:**
- Any API change; the Overlaps tab's own layout; a phone layout.

## Definition of done

- [x] Every scenario above is an automated test and passes (Vitest 279/279; AC-1 to AC-8 tests carry their IDs, AC-9 is the e2e run)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests) (BUILD SUCCESSFUL 2026-11-11)
- [x] `./gradlew :server:e2eTest` is green (BrowserFlowTest 8 tests, 0 failures, 0 skipped)
- [x] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted) (`git status` checked; `.idea/*` and `advisory/responses/.gitkeep` left out of the commit)
- [x] Checked in a browser at a wide window against a real scan (by the user, 2026-11-11: "checked looks good")

## Open questions

None.
