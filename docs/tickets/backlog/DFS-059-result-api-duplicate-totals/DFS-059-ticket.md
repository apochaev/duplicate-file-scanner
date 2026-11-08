---
id: DFS-059
title: Duplicate totals for folders and sets in the result API
status: in-progress
type: feature
scope: []
archetype: endpoint
gates: [done]
autorun: true
priority: high
lane: frontend
depends_on: [DFS-054]
touches:
  - server/src/main/java/us/pochaev/dfs/server/result
  - server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java
  - server/frontend/openapi.json
  - CLAUDE.md
  - docs/design/ui.md
  - server/frontend/src/app/results/result-test-data.ts
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# DFS-059: Duplicate totals for folders and sets in the result API

## Story

As the compact results screen (DFS-060)
I want each folder and each page of sets to carry how much of it is duplicated
So that the screen can draw a folder's duplicate space as a bar, sort folders by it, and split the scan into reclaimable, last copies and unique space without extra requests

## Context

The product owner found the result screen (DFS-049, DFS-050) "a lot of text for relatively small space" (2026-11-07). A prototype made in Claude Design (`docs/design/mockups/results-screen-prototype-2026-11-07.png`) shows a folder list with a bar and a "Dupes" column per folder, and a header over one bar split into reclaimable, last copies kept and unique space. DFS-060 builds that screen; the `component` archetype puts the API change in its own ticket, first. This is it.

Today's result API (DFS-047, `us.pochaev.dfs.server.result`):
- `FolderNode` (`GET /api/scans/{id}/folders[?parent=]`): path, name, files, bytes, directFiles, directBytes, hasChildren. No duplicate numbers.
- `SetPage` (`GET /api/scans/{id}/sets[?under=&page=&size=]`): items, page, size, totalItems, totalReclaimableBytes. No count of the files in the sets, nor their bytes.

Both are built from `ResultIndex`, which holds every duplicate set with its paths (`ScanRegistry.completedState`). The overlap analysis (DFS-048, computed lazily on the first overlaps request) is not needed for this.

## Decisions

1. **Duplicate numbers on every folder** (2026-11-07, revised the same day). `FolderNode` gains `duplicateFiles` and `duplicateBytes`: the counted files in the folder's whole subtree that belong to a duplicate set (a content with at least two physical copies in the scan), and their bytes, counted the way `files` and `bytes` count, so `duplicateFiles <= files` and `duplicateBytes <= bytes` always hold. Computed from `ResultIndex`'s sets once per scan, kept with it. First chosen as the overlap analysis' four numbers (`bytesWithCopyElsewhere`, `internalBytes`, ...); revised because their sum counts a file twice when it has a copy both inside and outside its folder (a bar over 100 %), "copied elsewhere" is 0 for the only root of a one-folder scan, and it would make the first folders request wait for the analysis. Not chosen: one overlaps request per expanded folder, or no "Dupes" column.
2. **Two totals on every page of sets** (2026-11-07). `SetPage` gains `totalFiles` (the link paths of every matching set) and `totalBytes` (each matching set's size times its physical copies), for the same sets `totalItems` counts (all sets, or with `under`, the sets with a copy under that folder). The screen derives last copies kept (`totalBytes - totalReclaimableBytes`) and unique space (scanned bytes minus `totalBytes`). Not chosen: showing only what exists (no "Duplicate files" number, a two-part bar), or a separate scan-summary endpoint (a new endpoint and request for numbers `SetPage` nearly has).
3. **Archetype `endpoint`, gates `[done]`** (2026-11-07). Two read-only fields added to two existing records; no new path, no change to security, paths or errors. Straight to the product-owner review. Runs after DFS-054, which also changes `us.pochaev.dfs.server.result`'s neighbourhood and `openapi.json` (both touch the spec and `TestServer`-based tests), so the two never run at once.

## Acceptance criteria

The full ID of each criterion is `DFS-059.AC-<n>`.

- **AC-1** Every `FolderNode` that `GET /api/scans/{id}/folders` answers, roots and children, carries `duplicateFiles` and `duplicateBytes`: the counted files in its subtree whose content has at least two physical copies in the scan, and their summed bytes; a folder with none answers `0` and `0`.
- **AC-2** Paths that are hard links of one physical file are not duplicates of each other: a folder holding only two hard links of a file with no other copy answers `duplicateFiles` `0`; a hard-linked copy that has another physical copy counts the way `files` counts its links, and `duplicateFiles <= files`, `duplicateBytes <= bytes` hold for every folder.
- **AC-3** `GET /api/scans/{id}/sets` answers `totalFiles`, the number of link paths of every matching set, and `totalBytes`, the sum over matching sets of size times physical copies, over all pages; with `under`, both count only the sets `totalItems` counts.
- **AC-4** The folders and sets endpoints keep their other fields, ordering, paging and refusals unchanged (`409` `SCAN_NOT_COMPLETED`, `404` `UNKNOWN_SCAN`, `400` `RELATIVE_PATH`, `403` `PATH_OUTSIDE_ROOTS`, `400` `INVALID_PAGE`).

## Scenarios

```gherkin
Feature: Duplicate totals in the result API

  Background:
    Given a COMPLETED scan of the @TempDir "<tmp>" holding, created at test time:
      | path                     | content                    |
      | A/song.mp3               | 3 000 bytes "s"            |
      | B/song.mp3               | the same 3 000 bytes       |
      | B/photo.jpg              | 2 000 bytes "p"            |
      | C/photo.jpg              | the same 2 000 bytes       |
      | C/deep/photo-copy.jpg    | the same 2 000 bytes       |
      | C/unique.txt             | 500 bytes "u"              |

  @AC-1
  Scenario: Folders carry their duplicate numbers
    When I GET "/api/scans/<id>/folders?parent=<tmp>"
    Then A answers duplicateFiles 1 and duplicateBytes 3000
    And B answers duplicateFiles 2 and duplicateBytes 5000
    And C answers duplicateFiles 2 and duplicateBytes 4000, with files 3 and bytes 4500

  @AC-1
  Scenario: The root and a folder without duplicates
    When I GET "/api/scans/<id>/folders"
    Then the root "<tmp>" answers duplicateFiles 5 and duplicateBytes 12000
    When I GET "/api/scans/<id>/folders?parent=<tmp>/C"
    Then "deep" answers duplicateFiles 1 and duplicateBytes 2000
    And a folder "<tmp>/D" holding only "lone.bin" (700 bytes, no copy) answers 0 and 0

  @AC-2
  Scenario: Hard links alone are not duplicates
    Given "<tmp>/E/a.bin" (900 bytes, no other copy) and "<tmp>/E/b.bin", a hard link to it, created at test time and skipped where the platform refuses
    When I GET "/api/scans/<id>/folders?parent=<tmp>"
    Then E answers duplicateFiles 0 and duplicateBytes 0

  @AC-2
  Scenario: A hard-linked copy with another copy
    Given "<tmp>/B/song-link.mp3", a hard link to "<tmp>/B/song.mp3", created at test time and skipped where the platform refuses
    When I GET "/api/scans/<id>/folders?parent=<tmp>"
    Then B's duplicateFiles and duplicateBytes count song-link.mp3 the way B's files and bytes count it
    And for every folder answered, duplicateFiles <= files and duplicateBytes <= bytes

  @AC-3
  Scenario: Set totals over every page
    When I GET "/api/scans/<id>/sets?page=0&size=1"
    Then totalItems is 2, totalFiles is 5 and totalBytes is 12000
    And totalReclaimableBytes is 7000

  @AC-3
  Scenario: Set totals under a folder
    When I GET "/api/scans/<id>/sets?under=<tmp>/A"
    Then totalItems is 1, totalFiles is 2 and totalBytes is 6000

  @AC-4
  Scenario: Refusals are unchanged
    When I GET the folders and sets of a RUNNING scan
    Then both answer 409 with code "SCAN_NOT_COMPLETED"
    When I GET "/api/scans/<id>/sets?under=relative/path"
    Then the answer is 400 with code "RELATIVE_PATH"
```

## Scope

**Touches:**
- `server/src/main/java/us/pochaev/dfs/server/result`: `FolderNode`'s two fields and their computation in `ResultIndex`; `SetPage`'s two fields.
- `server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java`: the new scenarios; existing assertions on the records' fields updated.
- `server/frontend/openapi.json`: the two records' new required fields (`./gradlew :server:updateOpenApi`).
- `CLAUDE.md`: the result API sentence in the `server` entry.
- `docs/design/ui.md`: the result API's description.

**Out of scope:**
- The screen that shows these numbers (DFS-060).
- The overlap analysis and its endpoints (DFS-048), unchanged.
- A scan duration or finish time (the header shows the start time only).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] Each test's `@DisplayName` starts with its criterion ID (`DFS-059.AC-n`)
- [ ] `./gradlew build` is green (includes the ArchUnit tests, `OpenApiSpecTest` and the Vitest tests)
- [ ] Every endpoint-archetype checklist item is addressed or marked not applicable with a reason
- [ ] The committed `openapi.json` lists the four new fields as required integers (`int64`), and the frontend still compiles against the regenerated client
- [ ] `CLAUDE.md` and `docs/design/ui.md` name the new fields (the verifier reads them)
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
