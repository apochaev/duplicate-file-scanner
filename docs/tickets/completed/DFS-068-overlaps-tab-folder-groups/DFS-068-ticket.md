---
id: DFS-068
title: Overlaps tab shows folder groups
status: done
type: feature
scope: []
archetype: component
gates: [design, done]
priority: high
lane: frontend
depends_on: [DFS-067]
touches:
  - server/frontend/src/app/results/overlaps-tab.ts
  - server/frontend/src/app/results/overlaps-tab.html
  - server/frontend/src/app/results/overlaps-tab.css
  - server/frontend/src/app/results/overlaps-tab.spec.ts
  - server/frontend/src/app/results/result-test-data.ts
  - server/frontend/src/app/results/result-screen.html
  - server/frontend/src/app/results/result-screen.spec.ts
  - server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java
  - CLAUDE.md
  - docs/design/ui.md
  - docs/design/folder-groups.md
blocked_reason:
created: 2026-11-12
updated: 2026-11-12
completed: 2026-11-12
---

# DFS-068: Overlaps tab shows folder groups

## Story

As a user deciding which copies to remove
I want the Overlaps tab to list groups of folders that share the same files, heaviest first, with how much of each folder is shared
So that I can see that a set of files exists in these folders and decide which folder keeps them

## Context

Design: `docs/design/folder-groups.md` (G2, G3, G4), mockup in `docs/design/mockups/folder-groups-2026-11-12/index.html`. The essentials are repeated here.

Today the Overlaps tab (`overlaps-tab.ts`, DFS-050) shows a fixed note, the selected folder's summary, a Folders table and a Folder pairs table, each from `/summaries` and `/pairs`. This ticket replaces all of that with one list of groups from `GET /api/scans/{id}/groups` (DFS-067), called through the generated client. The Comparison block (Pin, then select another folder; `/pair`) stays as it is, below the list.

**A group (G2)** shows:
- **A header row:** "<files> file(s)", then "· <copies> copies · <size> each · shared by <n> folders" in muted text, and the reclaimable bytes with a bar scaled to the page's largest, like the Sets header rows. "<size> each" appears when every set in the group has the same size; otherwise the header shows "<total size> in all".
- **One line per folder:**
  - the short path, using Sets' rule: `displayPath`, with the full path as tooltip;
  - "×<copiesEach>" when `copiesEach` > 1;
  - a small bar and "<sharedFiles> of <folderFiles> files";
  - a "fully shared" tag when `sharedFiles` = `folderFiles`;
  - "also in <otherGroups> other groups" when `otherGroups` > 0;
  - the path-copy button (`path-copy`).
- **When more than one folder is fully shared:** the line "Fully shared folders: any one can go, not all."
- **An expander** listing the group's sets: name, size, copies. It shows up to 20 sets, then "and <totalSets − 20> more".

**Order and filtering (G3):** the server's order. With a folder selected in the browser, the tab sends `under=`. A paginator offers 10, 25 and 50 groups per page.

**Small groups:** under the list, "Show <hiddenSmall> small groups" reloads with `minBytes=0`, and "Hide small groups" goes back. Nothing is shown when `hiddenSmall` is 0 and the small groups are hidden.

**App tag (G4, frontend only):** a folder line whose path has an element matching `*.auh`, `Adobe Premiere Pro Auto-Save`, `*.lrdata`, `*.photoslibrary` or `*.fcpbundle` (compared without regard to letter case) gets an "app" tag, with the tooltip "Managed by an application; deleting here can break it". The patterns are a constant in the component.

Loading, empty ("No folders share files") and error states follow the existing tab's pattern.

## Acceptance criteria

The full ID of each criterion is `DFS-068.AC-<n>`.

- **AC-1** The Overlaps tab shows one header row per group from `/groups`, in the server's order, with files, copies, the number of folders and the reclaimable bytes with a bar scaled to the page's largest.
- **AC-2** Each folder line shows the short path (`displayPath`, full path as tooltip), "<sharedFiles> of <folderFiles> files", "×N" when `copiesEach` > 1, and a copy-path button.
- **AC-3** A folder line shows "fully shared" when `sharedFiles` equals `folderFiles`, and "also in N other groups" when `otherGroups` is N > 0.
- **AC-4** A group with two or more fully shared folders shows "Fully shared folders: any one can go, not all."
- **AC-5** Expanding a group lists its sets (name, size, copies), and "and N more" when `totalSets` exceeds the listed sets.
- **AC-6** Selecting a folder in the browser reloads the groups with `under=` that folder; "All folders" reloads without it.
- **AC-7** "Show N small groups" (N = `hiddenSmall`) reloads with `minBytes=0`, and "Hide small groups" reloads without it.
- **AC-8** A folder line whose path has an element matching one of the app patterns, compared case-insensitively, shows the "app" tag with its tooltip; other lines show none.
- **AC-9** The Folders table, the Folder pairs table and the fixed note are gone, and the tab no longer calls `/summaries` or `/pairs`; the Comparison block still works.

## Scenarios

```gherkin
Feature: Overlaps tab shows folder groups

  Background:
    Given the result screen of a completed scan of "D:/" is open on the Overlaps tab

  @AC-1 @AC-2 @AC-8
  Scenario: The heaviest group from the d:/ scan
    Given /groups answers a group of 5 files, 10 copies, reclaimable 206.2 MB, with folders "D:/Media/Photo/Import/2020-07 Camper Van/2020-07-09 Gemini Bridges" (5 of 190) and "D:/Media/Photo/Import/2020-07 Camper Van/2020-07-09 Gemini Bridges/_AVP3481.auh/Contents/History/resources" (5 of 12)
    Then the first header row reads "5 files" and "shared by 2 folders" and shows 206.2 MB
    And the first folder line reads "5 of 190 files" with no tag
    And the second folder line reads "5 of 12 files" with the "app" tag

  @AC-2 @AC-3 @AC-8
  Scenario: Two copies in an auto-save folder
    Given a group with "…/2020-08-20 Sunset GGBr" (copiesEach 1, 1 of 1) and "…/2020-08-20 Sunset GGBr/Adobe Premiere Pro Auto-Save" (copiesEach 2, 2 of 7)
    Then the first line shows "fully shared"
    And the second line shows "×2", "2 of 7 files" and the "app" tag

  @AC-3 @AC-4
  Scenario: Two fully shared folders
    Given a group with "…/2020-06-23 Garmin Road trip/Garmin" (1 of 1) and "…/2020-07-28 Virb Road Trip/Garmin" (1 of 1)
    Then both lines show "fully shared"
    And the group shows "Fully shared folders: any one can go, not all."

  @AC-3
  Scenario: A folder in other groups
    Given a folder line with otherGroups 2
    Then it shows "also in 2 other groups"

  @AC-5
  Scenario: Expanding a group of 25 sets
    Given a group answering 20 sets and totalSets 25
    When I expand it
    Then 20 sets are listed with name, size and copies, followed by "and 5 more"

  @AC-6
  Scenario: Filtering by the selected folder
    When I select "Media" in the folder browser
    Then /groups is requested with under "D:/Media"
    When I select "All folders"
    Then /groups is requested without under

  @AC-7
  Scenario: Showing and hiding small groups
    Given /groups answers hiddenSmall 9
    When I click "Show 9 small groups"
    Then /groups is requested with minBytes 0
    When I click "Hide small groups"
    Then /groups is requested without minBytes

  @AC-8
  Scenario: The app tag ignores letter case and plain folders
    Given folder lines "D:/a/Photo.AUH/x" and "D:/a/Photos"
    Then "D:/a/Photo.AUH/x" shows the "app" tag and "D:/a/Photos" shows none

  @AC-9
  Scenario: The old tables are gone and comparison still works
    Then no Folders table, Folder pairs table or fixed note is shown, and neither /summaries nor /pairs is requested
    When I pin "Media" and select "VirtualBox"
    Then the Comparison block shows that pair
```

## Decisions

1. **No summary header** (2026-11-12). The selected folder's own summary above the list is dropped; the folder browser's breadcrumb and rows already show the selection and its totals. The tab calls `/groups` and `/pair` only. In `BrowserFlowTest`, `resultScreenOpensOnSets` (asserts `.summaries-table`) and `folderBrowserFiltersTheViews` (asserts `.folder-header .summary-folder`) are rewritten to assert groups.
2. **Archetype and gates** (2026-11-12). `component`, `gates: [design, done]`: the pipeline pauses after the architect so the template structure is checked before tests and code.
3. **Live check split** (2026-11-12). The verifier runs the live check on a fixture folder (done-item); the user checks a real `d:/` scan after close (named post-close check), so `done` never waits on it.
4. **Keep 1 MiB** (2026-11-12). The small-group cut is DFS-067's `minBytes` default; the tab sends no `minBytes` unless "Show small groups" is clicked (then `0`).

## Scope

**Touches:**
- `server/frontend/src/app/results/overlaps-tab.ts`, `.html`, `.css`, `.spec.ts`: the groups list replaces the tables; Vitest tests per scenario.
- `server/frontend/src/app/results/result-test-data.ts`: group test data.
- `server/frontend/src/app/results/result-screen.html`, `result-screen.spec.ts`: the host binds the tab's `roots` and `density` inputs; its three Overlaps tests stop answering `/summaries` and `/pairs` (added at the DFS-068 design gate, 2026-11-12).
- `server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java`: the Overlaps flow asserts a group instead of the tables.
- `CLAUDE.md`, `docs/design/ui.md`: the Overlaps tab description and U3 "as built".
- `docs/design/folder-groups.md`: status line set to implemented for G2–G4.

**Out of scope:**
- The server endpoint (DFS-067).
- Merging mirrored folder trees into one row (G7, later).
- A "keep in this folder, remove from the others" action (G7, comes with the plan screen).
- An editable list of app patterns.

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes `:server:npmTest`)
- [x] `./gradlew :server:e2eTest` is green
- [x] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)
- [x] Live check on the built jar over a small fixture folder (a few large duplicate files, plus an `X.auh/Contents` subfolder so the app tag shows): the Overlaps tab's DOM is read in Compact and Comfortable density, with the result in `verification.md`

**Post-close check** (not a done-item): after close, the user opens the Overlaps tab on a real `d:/` scan in both densities (long names, the 206 MB group, app-folder paths, many folders in one group) and records the result as a note under this ticket's `## Post-close check` section in `completed/`; a problem found becomes a new ticket.

## Open questions

None.
