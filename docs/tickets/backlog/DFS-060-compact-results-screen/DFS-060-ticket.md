---
id: DFS-060
title: Compact read-only results screen
status: ready
type: feature
scope: []
archetype: component
gates: [design, done]
autorun: true
priority: high
lane: frontend
depends_on: [DFS-059]
touches:
  - server/frontend/src/app/results
  - server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# DFS-060: Compact read-only results screen

## Story

As a user looking at a finished scan
I want the results screen to show sizes as bars and numbers in columns, with short paths, instead of sentences and full paths
So that I can see at a glance where my duplicate space is and which copies sit where, without reading long lines of text

## Context

The product owner tried the result screen (DFS-049 Sets, DFS-050 Overlaps) on 2026-11-07: it works, but it is "a lot of text for relatively small space". Today every set is a Material card listing full absolute paths, hard links take a line each, and every table cell is a phrase such as "1204 files · 3.1 GB".

A prototype was made in Claude Design from a brief drawing on similar apps (Gemini 2, dupeGuru and Czkawka for grouped duplicate lists; DaisyDisk and WizTree for size shown visually; Carbon and Material compact density for dense tables). Its screenshots are this ticket's visual reference:

- `docs/design/mockups/results-screen-prototype-2026-11-07.png`: the whole results screen.
- `docs/design/mockups/path-display-rule-2026-11-07.png`: how a path is shortened.

This ticket builds the **read-only** part of the prototype: what you see, not what you select. The prototype's plan controls (checkboxes, Keep/Quarantine labels, auto-select buttons, a folder's "move dupes"/"keep" tag, the "In current plan" number and bar segment, the sticky footer, "Review plan", "Quarantine folder", "New scan") are a later ticket on DFS-054, and the Folder pairs view a later one too.

The screen lives in `server/frontend/src/app/results/` (`result-screen`, `folder-tree`, `sets-tab`, `overlaps-tab`, `path-copy`, `result-format`, `result-path`). It reads the result API through the generated client: `FolderNode` with `duplicateFiles` and `duplicateBytes`, and `SetPage` with `totalFiles` and `totalBytes`, both added by DFS-059; the scan's `locations`, `startedAt` and `progress.files`/`progress.bytes` from `ScanEntry`. The API has no finish time, so the header shows when the scan started, not how long it took.

DFS-053's browser flows (`BrowserFlowTest`) assert today's markup: AC-3 expects the Overlaps tab selected on open, AC-5 expects `.duplicate-set` cards and a "hard link (same file)" line. This ticket changes both, so it updates those assertions to the new screen while keeping what each flow proves.

## Decisions

1. **Header totals from the sets page** (2026-11-07). The header shows the scan's folders and start time, then Reclaimable (`totalReclaimableBytes`), Duplicate sets (`totalItems`), Duplicate files (`totalFiles`) and Scanned (`progress.bytes` and `progress.files`), all from the unfiltered sets page and the scan entry, over one bar of three segments: reclaimable, last copies kept (`totalBytes - totalReclaimableBytes`) and unique (`progress.bytes - totalBytes`, never below 0), each with a legend entry and its size.
2. **Sets opens first; Overlaps unchanged** (2026-11-07). The results screen selects Sets on open and Overlaps becomes the second tab, as it is otherwise. Overlaps' tables, summary and pin flow are left for the Folder pairs ticket, so this one does not grow.
3. **Folder list by duplicate space** (2026-11-07). Each level of the tree (the roots, and each folder's children, which `/folders` answers complete, unpaged) is sorted in the browser by `duplicateBytes`, largest first, then by name. A row shows the folder's name, a bar of `duplicateBytes` over `bytes`, its size and its duplicate size in right-aligned columns. Lazy expansion, selection and "Pin for comparison" work as today.
4. **Sets as one grouped table** (2026-11-07). One table replaces the cards: per set a header row (the first link's file name, "N copies", the size of each, the reclaimable bytes with a bar scaled to the page's largest), then one row per physical copy (its short path, its modified date, a copy button). Hard links are a "N links" badge on their copy's row, the full paths of every link in its tooltip, not rows of their own. A copy under the selected folder carries an "in selected" badge. Above the table, a toggle "All sets" / "Copies under <folder name>" chooses whether the selected folder filters the sets (the default when a folder is selected, as today) or not. The paginator (25/50/100) stays.
5. **Path display rule** (2026-11-07, the prototype's board). A path is shown relative to the scan root that holds it, the root shown once in the header; with more than one root, the root's last name element comes first. When it is longer than its column's character budget, folders are cut from the middle and replaced by "…", keeping the first folder under the root and the file name, then adding trailing folders back while they fit; a file name that alone does not fit is cut in its middle, keeping its extension. Folders in a secondary colour, the file name in normal ink, monospace. The separator is the server's (backslashes on Windows). The full absolute path is the tooltip and what the copy button copies.
6. **Compact / Comfortable, remembered** (2026-11-07). A toggle in the header switches row density (Compact by default); the choice is kept in the browser's local storage, behind an injection token so tests use a fake, and restored on the next visit.
7. **Archetype `component`, gates `[design, done]`** (2026-11-07). A screen change in `server/frontend` over an endpoint that DFS-059 provides first. The design gate lets the product owner check the layout and the path helper's rules before tests are written; nothing here plans or moves files.

## Acceptance criteria

The full ID of each criterion is `DFS-060.AC-<n>`.

- **AC-1** Opening a COMPLETED scan's results selects the Sets tab; the Overlaps tab is second and shows what it showed before.
- **AC-2** The header shows the scan's folders, its start time, and Reclaimable, Duplicate sets, Duplicate files and Scanned (bytes and files) from the unfiltered sets page and the scan entry, formatted by `formatBytes` and without thousands separators.
- **AC-3** The header's bar has three segments, reclaimable, last copies kept (`totalBytes - totalReclaimableBytes`) and unique (`progress.bytes - totalBytes`, at least 0), sized in proportion to scanned bytes, each with a legend entry naming its size; a scan with 0 bytes shows an empty bar and no division by zero.
- **AC-4** Every level of the folder list is ordered by `duplicateBytes`, largest first, then by name; each row shows the name, a bar of `duplicateBytes` over `bytes`, and the size and duplicate size right-aligned; expanding, selecting and pinning a folder work as before.
- **AC-5** The Sets view is one table: each set a header row with its first link's file name, its copy count, its size per copy and its reclaimable bytes with a bar scaled to the largest on the page, followed by one row per physical copy with its short path (AC-7), its modified date and a copy button.
- **AC-6** A physical copy with several links is one row carrying a "<n> links" badge whose tooltip lists every link's full path; a copy under the selected folder carries an "in selected" badge; no row says "hard link (same file)".
- **AC-7** A path is shown relative to its scan root (prefixed with the root's last name element when the scan has several roots); over the character budget, middle folders become "…" while the first folder and the file name stay, trailing folders are added back while they fit, and a file name too long alone is cut in its middle keeping its extension; the full absolute path is the tooltip and what the copy button copies.
- **AC-8** With a folder selected, the toggle reads "Copies under <folder name>" and the table lists only sets with a copy under it, as today; "All sets" lists every set whatever the selection; the paginator (25/50/100) pages either list.
- **AC-9** The Compact/Comfortable toggle changes the table's row density, starts as Compact, and the last choice is restored on the next load from local storage (read through an injection token).
- **AC-10** Loading, empty and error states keep their messages and roles from DFS-049 (a progress bar while loading, the empty-sets message, `role="alert"` errors, the alert for a scan missing or not finished).

## Scenarios

```gherkin
Feature: Compact results screen

  Background:
    Given a COMPLETED scan "s1" of root "C:\Archive" started 2026-11-07 14:02, with progress files 76214 and bytes 508 900 000 000
    And its unfiltered sets page answers totalItems 8412, totalFiles 21930, totalBytes 178 100 000 000 and totalReclaimableBytes 127 100 000 000
    And the generated client is faked

  @AC-1
  Scenario: Sets opens first
    When I open "#/scans/s1"
    Then the selected tab is "Sets" and the second tab is "Overlaps"
    When I select "Overlaps"
    Then it shows the folder summary and the Folders table as before

  @AC-2
  Scenario: The header's numbers
    When I open "#/scans/s1"
    Then the header shows "C:\Archive" and the start time
    And it shows Reclaimable "118.4 GB", Duplicate sets "8412", Duplicate files "21930" and Scanned "474.0 GB · 76214 files"

  @AC-3
  Scenario: The header's bar
    When I open "#/scans/s1"
    Then the bar's segments are reclaimable about 25.0 %, last copies kept about 10.0 % and unique about 65.0 %
    And the legend reads "Reclaimable · 118.4 GB", "Last copies, always kept · 47.5 GB" and "Unique · 308.1 GB"

  @AC-3
  Scenario: An empty scan
    Given the scan's progress bytes are 0 and its sets page answers all totals 0
    When I open its results
    Then the bar shows three segments of width 0 and the legend reads 0 B for each

  @AC-4
  Scenario: Folders are ordered by duplicate space
    Given the root's children answer:
      | name      | bytes          | duplicateBytes |
      | Documents | 1 288 490 188  | 0              |
      | Backups   | 76 235 669 504 | 40 802 189 312 |
      | Photos    | 65 712 999 219 | 22 548 578 304 |
      | Music     | 2 684 354 560  | 0              |
    When I expand "C:\Archive"
    Then the children are listed Backups, Photos, Documents, Music
    And Backups' bar is about 53.5 % and its row reads "71.0 GB" and "38.0 GB"

  @AC-5
  Scenario: A set is a header row and its copies
    Given the first set on the page has size 1 610 612 736, reclaimable 3 221 225 472 and three copies:
      | link path                                                        | modified   |
      | C:\Archive\Downloads\ubuntu.iso                                  | 2023-09-14 |
      | C:\Archive\Backups\MacBook\Users\me\Downloads\ubuntu.iso         | 2023-09-14 |
      | C:\Archive\Backups\old-drive\isos\linux\ubuntu.iso               | 2023-10-02 |
    When I open "#/scans/s1"
    Then the set's header row reads "ubuntu.iso", "3 copies", "1.5 GB each" and "3.0 GB"
    And its reclaimable bar is full, being the largest on the page
    And three copy rows follow, each with its short path, its modified date and a copy button

  @AC-6
  Scenario: Hard links are a badge
    Given a set whose second copy has links "C:\Archive\Video\GX01.MP4" and "C:\Archive\Video\GX01-link.MP4"
    When I open "#/scans/s1"
    Then that copy is one row with a "2 links" badge
    And the badge's tooltip lists both full paths
    And no row reads "hard link (same file)"

  @AC-6
  Scenario: Copies under the selected folder are marked
    When I select the folder "C:\Archive\Backups\old-drive"
    Then the copy "C:\Archive\Backups\old-drive\isos\linux\ubuntu.iso" carries an "in selected" badge
    And the copy "C:\Archive\Downloads\ubuntu.iso" carries none

  @AC-7
  Scenario Outline: Paths are shortened from the middle
    Given the scan's roots are <roots> and the column budget is 36 characters
    When the path <path> is shown
    Then it reads <shown>
    And its tooltip is <path>

    Examples:
      | roots                        | path                                                                      | shown                                         |
      | "C:\Archive"                 | "C:\Archive\Video\GoPro\GX01.MP4"                                         | "Video\GoPro\GX01.MP4"                        |
      | "C:\Archive"                 | "C:\Archive\Backups\MacBook-2020\Users\me\Pictures\Iceland 2019\pano.tif" | "Backups\…\Iceland 2019\pano.tif"             |
      | "C:\Archive" and "D:\Backup" | "D:\Backup\Photos\cat.jpg"                                                | "Backup\Photos\cat.jpg"                       |

  @AC-7
  Scenario: A file name too long alone keeps its extension
    Given the scan's root is "C:\Archive" and the column budget is 36 characters
    When the path "C:\Archive\Downloads\Screen Recording 2024-03-05 at 12.41.07 long.mov" is shown
    Then it is 36 characters long, starts with "Downloads\Screen", contains one "…" and ends with "long.mov"

  @AC-7
  Scenario: The copy button copies the full path
    When I press the copy button on the row showing "Backups\…\Iceland 2019\pano.tif"
    Then the clipboard receives "C:\Archive\Backups\MacBook-2020\Users\me\Pictures\Iceland 2019\pano.tif"

  @AC-8
  Scenario: The folder filter toggle
    Given the folder "C:\Archive\Backups\old-drive" is selected
    Then the toggle reads "Copies under old-drive" and is on
    And the sets request carries under "C:\Archive\Backups\old-drive"
    When I choose "All sets"
    Then the sets request carries no under and the selected folder stays selected
    When I choose page size 50
    Then the request asks page 0 with size 50

  @AC-9
  Scenario: Density is remembered
    Given the fake local storage is empty
    When I open "#/scans/s1"
    Then the density toggle reads Compact
    When I choose Comfortable
    Then the table's rows use the comfortable density and the fake storage holds "comfortable"
    When I open "#/scans/s1" again
    Then the density toggle reads Comfortable

  @AC-10
  Scenario: Loading, empty and failing sets
    When the sets request has not answered
    Then a progress bar labelled "Loading duplicate sets" shows
    When it answers totalItems 0
    Then the empty-sets message from DFS-049 shows
    When a sets request fails
    Then an error with role "alert" shows
```

## Scope

**Touches:**
- `server/frontend/src/app/results`: the result screen's header and tab order, the folder tree's rows and order, the sets table replacing the cards, the path helper (`result-path.ts`), the density toggle with its storage token, and their Vitest specs.
- `server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java`: DFS-053's AC-3 and AC-5 flows updated to the new markup (Sets selected on open, the table, the links badge), still proving what they proved.
- `CLAUDE.md`: the result screen's sentences in the `server` entry.
- `docs/design/ui.md`: U3's description of the result screen.

**Out of scope:**
- Building a deletion plan on the screen (checkboxes, Keep/Quarantine, auto-select, folder tags, the "In current plan" segment, the footer, Review plan): a later ticket on DFS-054.
- Overlaps' and Folder pairs' redesign (decision 2; a later ticket once its board is designed).
- Quarantine folder and New scan buttons; the scan's duration (the API has no finish time).
- Infinite scrolling; a dark theme.
- Any change to the API (DFS-059 adds what this needs).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] Each test's name given to `it` starts with its criterion ID (`DFS-060.AC-n`)
- [ ] `./gradlew build` is green (includes the ArchUnit tests and the Vitest tests)
- [ ] `./gradlew :server:e2eTest` passes with DFS-053's flows updated
- [ ] Every component-archetype checklist item is addressed or marked not applicable with a reason
- [ ] `CLAUDE.md`'s result screen sentences and `docs/design/ui.md` U3 describe the header, the folder list, the sets table, the path rule and the density toggle (the verifier reads them)
- [ ] Started by hand once on Windows on a real scan with duplicates, compared with `docs/design/mockups/results-screen-prototype-2026-11-07.png`; the browser console free of errors
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
