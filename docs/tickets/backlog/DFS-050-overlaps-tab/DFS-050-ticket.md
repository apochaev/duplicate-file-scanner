---
id: DFS-050
title: Add the Overlaps tab to the result screen
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-048, DFS-049]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-05
completed:
---

# DFS-050: Add the Overlaps tab to the result screen

## Story

As the user of the local DFS server
I want an Overlaps tab that ranks the folders under my selection by how much of them is duplicated elsewhere, shows which folder pairs share copies, and compares any two folders I pick
So that I can decide which folder can go, for example "every file in folder2 has a copy in folder1"

## Context

Milestone M-1 ("see the results of a scan"), step 5 of 5 (`docs/design/ui.md`, "Milestones"). It completes the milestone.

- **U3:** the Overlaps tab is the landing tab of the result view, filtered to the folder selected in the tree: folder summaries and folder pairs ranked by reclaimable bytes. Charts (overlap matrix, treemap) come later.
- **`folder-overlap-analysis.md` decision 1:** pairs are never combined into a verdict; two folders each fully covered by the other cannot both be deleted. The tab must not suggest otherwise.
- **DFS-048:** the summaries, pairs and two-folder pair endpoints.
- **DFS-049:** the result screen with its tree and tabs.

## Decisions

1. **One Overlaps tab, stacked: the selected folder's summary, a Folders table, a Folder pairs table** (2026-11-05). With a folder selected in the tree, a header line shows that folder's own summary (`SummaryPage.folder` from `/summaries?under=<path>`); under "All folders" there is no header. Below it a "Folders" table of summaries (`/summaries?under=`), then a "Folder pairs" table (`/pairs?under=`), each with its own paginator, both filtered to the selection. Both questions, "can this folder go?" and "where are the copies?", are on screen at once. Not a "Folders | Pairs" toggle (a click to see the other half) and not the pairs table alone (it loses the ranking of the most redundant folders, and coverage spread over several folders shows in no single pair: `folder-overlap-analysis.md` decision 1).
2. **Rows show exact fractions; "full" is a marker, never a verdict; one fixed note above the tables** (2026-11-05). Sizes and counts use the shared helpers (`formatBytes`, `plural` in `scans/scan-format.ts`, as DFS-049). The Folders table has the columns Folder (its path), "Copied elsewhere" (`<filesWithCopyElsewhere> / <totalFiles> files · <bytesWithCopyElsewhere>`), "Duplicates inside" (`<internalDuplicateFiles> files · <internalBytes>`) and "Total" (`<totalFiles> files · <totalBytes>`); a row with `totalFiles` > 0 and `filesWithCopyElsewhere` = `totalFiles` carries the chip "all copied elsewhere". The header for the selected folder (decision 1) reads the same way. The Folder pairs table has one row per pair with two side columns, A and B in the API's order, each reading `<folder>: <filesWithCopy> / <totalFiles> files · <bytesWithCopy>` followed by "all have a copy in the other" when the side's `full` is true and "partial" otherwise. Above the tables a fixed note reads "A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan." No row, chip or note uses "safe", "delete" or "can go" about a folder (`folder-overlap-analysis.md` decision 1: pairs are never combined into a verdict; DFS-022 checks a plan as a whole). Not percentages with bars (99.9% and 100% read alike, while one unique file decides whether a folder can go, and a full bar invites "delete") and not a sentence per row (long, hard to compare down a column).
3. **Two folders are compared by pinning one in the tree and selecting the other** (2026-11-05). Every folder node of the tree (not "All folders") has a "Pin for comparison" action; at most one folder is pinned, and pinning another replaces it. While a folder is pinned, the Overlaps tab shows "Pinned: <path>" with an "Unpin" link at its top. With a folder pinned and a different folder selected, a "Comparison" block above the note (decision 2) shows `/pair?a=<pinned>&b=<selected>` as one row in decision 2's side format (sides in the API's order). Selecting the pinned folder itself, or "All folders", sends no `/pair` request and the block reads "Select another folder to compare with the pinned one". Two nested folders are sent, and the block shows the server's `NESTED_FOLDERS` message (for example "C:\Photos contains C:\Photos\2019"). The pin lasts while the result screen is open and is not part of the URL. Not two path fields (more controls, typed paths bring `RELATIVE_PATH` and `PATH_OUTSIDE_ROOTS` to the UI, slower) and not a later ticket (the Story names it, and `/pairs` covers only immediate folders: two ancestors such as `C:\Photos` and `D:\Backup`, CLAUDE.md's folder1/folder2 case, only `/pair` answers). The "Pin for comparison" action is added to DFS-049's `results/folder-tree.*`.

## Acceptance criteria

The full ID of each criterion is `DFS-050.AC-<n>`.

- **AC-1** The Overlaps tab shows, top to bottom, the selected folder's own summary as a header (none under "All folders"), a "Folders" table of summaries and a "Folder pairs" table, each table with its own paginator, both requested with `under=<the selected folder's path>` and without `under` for "All folders".
- **AC-2** A Folders row, and the selected folder's header, read "<path>", "Copied elsewhere <filesWithCopyElsewhere> / <totalFiles> files · <bytesWithCopyElsewhere>", "Duplicates inside <internalDuplicateFiles> files · <internalBytes>" and "Total <totalFiles> files · <totalBytes>", with the chip "all copied elsewhere" exactly when `totalFiles` > 0 and `filesWithCopyElsewhere` equals `totalFiles`.
- **AC-3** A Folder pairs row shows side A then side B in the API's order, each as "<folder>: <filesWithCopy> / <totalFiles> files · <bytesWithCopy>" followed by "all have a copy in the other" when that side's `full` is true and "partial" when it is false.
- **AC-4** The Overlaps tab shows the note "A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan." above its tables, and no row, header or chip on the tab contains the words "safe", "delete" or "can go".
- **AC-5** Every folder node of the tree, but not "All folders", offers "Pin for comparison"; pinning a folder shows "Pinned: <path>" with an "Unpin" link on the Overlaps tab, pinning another folder replaces it, and "Unpin" removes the label and the Comparison block.
- **AC-6** With a folder pinned and a different folder selected, the Overlaps tab requests `/pair` with `a=<pinned path>` and `b=<selected path>` and shows its result in a "Comparison" block as one pair row in AC-3's format.
- **AC-7** With a folder pinned and the pinned folder itself or "All folders" selected, no `/pair` request is sent and the Comparison block reads "Select another folder to compare with the pinned one"; when `/pair` answers `400 NESTED_FOLDERS`, the block shows the server's message.

## Scenarios

```gherkin
Feature: Overlaps tab

  @AC-1
  Scenario: The tab shows the selection's summary, its folders and its pairs
    Given the result screen of COMPLETED scan "7f3c…" is shown with "All folders" selected
    Then the Overlaps tab shows no folder header, a "Folders" table and a "Folder pairs" table, each with a paginator
    And summaries and pairs were requested without under
    When the user selects folder "D:\Backup"
    Then summaries and pairs are requested with under "D:\Backup"
    And a header above the "Folders" table shows the summary of "D:\Backup"

  @AC-2
  Scenario: A folder fully copied elsewhere, and one partly
    Given listSummaries answers two items:
      | folder               | totalFiles | totalBytes | filesWithCopyElsewhere | bytesWithCopyElsewhere | internalDuplicateFiles | internalBytes |
      | D:\Backup\2019       | 340        | 1288490188 | 340                    | 1288490188             | 0                      | 0             |
      | C:\Photos\2019       | 400        | 1503238553 | 340                    | 1288490188             | 12                     | 83886080      |
    When the Overlaps tab is shown
    Then the row "D:\Backup\2019" reads "Copied elsewhere 340 / 340 files · 1.2 GB", "Duplicates inside 0 files · 0 B" and "Total 340 files · 1.2 GB" and carries the chip "all copied elsewhere"
    And the row "C:\Photos\2019" reads "Copied elsewhere 340 / 400 files · 1.2 GB", "Duplicates inside 12 files · 80.0 MB" and "Total 400 files · 1.4 GB" and carries no chip

  @AC-2
  Scenario: An empty folder is not "all copied elsewhere"
    Given listSummaries answers a folder "D:\Empty" with totalFiles 0 and filesWithCopyElsewhere 0
    Then its row carries no "all copied elsewhere" chip

  @AC-3
  Scenario: A pair with one full side and one partial side
    Given listPairs answers one pair: a "C:\Photos\2019" with filesWithCopy 340, totalFiles 400, bytesWithCopy 1288490188, full false; b "D:\Backup\2019" with filesWithCopy 340, totalFiles 340, bytesWithCopy 1288490188, full true
    When the Overlaps tab is shown
    Then the pair row shows "C:\Photos\2019: 340 / 400 files · 1.2 GB" with "partial" before "D:\Backup\2019: 340 / 340 files · 1.2 GB" with "all have a copy in the other"

  @AC-4
  Scenario: Two folders each fully covered by the other get no verdict
    Given listPairs answers a pair "D:\A" and "D:\B" with both sides full
    When the Overlaps tab is shown
    Then both sides read "all have a copy in the other"
    And the note "A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan." is shown above the tables
    And no row, header or chip contains "safe", "delete" or "can go"

  @AC-5
  Scenario: Pinning, replacing and unpinning a folder
    Given the result screen of scan "7f3c…" shows roots "C:\Photos" and "D:\Backup"
    Then "All folders" offers no "Pin for comparison" action
    When the user pins "D:\Backup"
    Then the Overlaps tab shows "Pinned: D:\Backup" with an "Unpin" link
    When the user pins "C:\Photos"
    Then it shows "Pinned: C:\Photos" and no longer "Pinned: D:\Backup"
    When the user clicks "Unpin"
    Then neither the pinned label nor a Comparison block is shown

  @AC-6
  Scenario: Comparing two folders from different roots
    Given "D:\Backup" is pinned
    And /pair answers (sides sorted by folder) side a "C:\Photos" with filesWithCopy 340, totalFiles 3210, bytesWithCopy 1288490188, full false and side b "D:Backup" with filesWithCopy 2, totalFiles 2, bytesWithCopy 900, full true
    When the user selects "C:\Photos"
    Then /pair is requested with a "D:\Backup" and b "C:\Photos"
    And the Comparison block shows "C:\Photos: 340 / 3210 files · 1.2 GB" with "partial" before "D:\Backup: 2 / 2 files · 900 B" with "all have a copy in the other"

  @AC-7
  Scenario: Nothing to compare yet
    Given "D:\Backup" is pinned
    When the user selects "D:\Backup", then "All folders"
    Then no /pair request was sent
    And the Comparison block reads "Select another folder to compare with the pinned one"

  @AC-7
  Scenario: Nested folders
    Given "C:\Photos" is pinned
    And /pair answers 400 NESTED_FOLDERS with message "C:\Photos contains C:\Photos\2019"
    When the user selects "C:\Photos\2019"
    Then the Comparison block shows "C:\Photos contains C:\Photos\2019"
```

## Scope

**Touches:**
- <to be refined; likely `server/frontend/src/app`, `docs/design/ui.md`>

**Out of scope:**
- Charts: overlap matrix, treemap (U3, later; see also DFS-045).
- Building a rule from a pair ("keep A, quarantine in B"): milestone M-3 (U6).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the Vitest tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Becoming the landing tab: Overlaps first, Sets second (U3).
- Archetype, gates and autorun.
