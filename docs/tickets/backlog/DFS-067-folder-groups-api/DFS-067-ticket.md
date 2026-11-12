---
id: DFS-067
title: Folder groups in the overlaps API
status: draft
type: feature
scope: []
archetype:
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: high
lane: backend
depends_on: []
touches:
  - server/src/main/java/us/pochaev/dfs/server/analysis
  - server/src/main/java/us/pochaev/dfs/server/result
  - server/src/test/java/us/pochaev/dfs/server/analysis
  - server/src/test/java/us/pochaev/dfs/server/GroupApiTest.java
  - server/frontend/openapi.json
  - CLAUDE.md
  - docs/design/ui.md
  - docs/design/folder-groups.md
blocked_reason:
created: 2026-11-12
updated: 2026-11-12
completed:
---

# DFS-067: Folder groups in the overlaps API

## Story

As a user cleaning up duplicates folder by folder
I want the duplicate sets grouped by the exact folders their copies sit in
So that I see "these folders share these files" in one place and can decide which folder keeps them

## Context

Design: `docs/design/folder-groups.md` (G1, G2, G3, G5, G6), mockup in `docs/design/mockups/folder-groups-2026-11-12/`. The essentials are repeated here.

The Overlaps tab (DFS-050) shows folder summaries and folder pairs from `GET /api/scans/{id}/summaries` and `/pairs` (DFS-048). In the 2026-11-12 scan of `d:/` that view repeats one finding on four nested folders (`_AVP3481.auh`, `\Contents`, `\History`, `\resources`, each "5 files · 206.2 MB"), and most pairs are a folder paired with its own subfolder. This ticket adds the data for the replacement view. DFS-068 builds the tab on it.

**Group (G1).** For each duplicate set, its folders are the folders that **directly** hold its link paths, each with `copiesEach`: the number of the set's physical copies with a path in that folder. Sets with the same folders and the same `copiesEach` per folder form one group. Parents of those folders never appear, so a finding is never repeated up the tree. Hard links follow the Sets rules (CLAUDE.md, "Hard links are not duplicates"): a hard link of a copy adds no copy.

**Endpoint (G5).** `GET /api/scans/{id}/groups?under=&minBytes=&page=&size=` lives in `us.pochaev.dfs.server.result` and is served from a per-scan structure built once, next to `OverlapAnalysis` (G6). It returns a page of groups. Each group has:

- `files`: the number of sets in the group;
- `copies`: physical copies over all its sets;
- `reclaimableBytes`: for each set, (physical copies − 1) × size, summed; the same figure `/sets` uses;
- `folders[]`, sorted by path, each with:
  - `folder`: absolute and normalized;
  - `copiesEach`;
  - `sharedFiles`: the folder's link paths in the group's sets;
  - `sharedBytes`: their summed size;
  - `folderFiles`, `folderBytes`: the folder's direct counted files and bytes, from `ScanResult.folders`;
  - `otherGroups`: how many other groups list this folder;
- `sets[]`: the first 20 sets, largest reclaimable first, each with `digest`, `name` (the first path's file name), `size` and `copies`;
- `totalSets`.

The page also carries `totalItems` and `hiddenSmall`: the groups matching `under` whose `reclaimableBytes` is below `minBytes`.

- **Order (G3):** `reclaimableBytes` descending, then the first folder's path, then the folder lists compared path by path.
- **`under`:** keeps groups with at least one folder at or under it. Path rules as for `/summaries`: absolute, else `400` `RELATIVE_PATH`; inside a scan root, else `403` `PATH_OUTSIDE_ROOTS`.
- **`minBytes`:** defaults to 1048576 (1 MiB); `0` returns every group.
- **Paging and refusals:** as for `/summaries` (`409` `SCAN_NOT_COMPLETED`, `404` `UNKNOWN_SCAN`, the same page and size rules).

`/summaries`, `/pairs` and `/pair` stay unchanged, and so does `OverlapAnalysisScaleTest`: the groups are a separate structure (decision 4) with a scale test of their own on the same 150,000-file case, whose budget the architect fixes in `design.md` from a measurement.

## Decisions

1. **A set whose copies all sit in one folder is a one-folder group.** `IMG_001.jpg` and `IMG_001 (1).jpg` side by side give a group with one folder and `copiesEach` 2. Chosen over leaving it to Sets: the Folders table this view replaces showed internal duplicates (`internalBytes`), so dropping them would silently remove that information from the Overlaps tab. DFS-068 can label such a group "duplicates inside this folder" from the data alone.
2. **A copy whose hard links sit in several folders puts every one of them in the folder list.** With `R/A/f` and `R/B/f` hard links of one file and `R/C/f` a separate copy, the folders are A, B and C, each `copiesEach` 1, and the group answers `copies` 2: the physical-copy count stays 2, the per-folder figures stay true to what each folder holds. Chosen over listing only the first path's folder: a folder holding the file would vanish from the group, and its "N of M files" and "fully shared" claims, which the user relies on to delete a folder, would be wrong, and the result would depend on path order.
3. **A negative `minBytes` is refused with `400` `INVALID_MIN_BYTES`**, raised in the controller after the scan, path and paging checks and before any group is built, so a refused request has no side effect. A distinct code follows the scan API's naming (`INVALID_MIN_SIZE`, `RELATIVE_LOCATION`); reusing `INVALID_PAGE` would carry a false message, and treating a negative as 0 would hide a caller's mistake. A non-numeric `minBytes` (or `page`, `size`) is not handled here: no handler maps Spring's type-mismatch error today, so it answers Spring's own `400`, exactly as `/summaries` does for `page=abc`; the ticket changes that for none of them.
4. **The groups are a separate per-scan structure** (`FolderGroups`, package `analysis`), kept by `ScanRegistry.completedState` under its own key and built on the first `/groups` request; `OverlapAnalysis` is not changed. Chosen over building them inside `OverlapAnalysis.of`: the pair analysis is the expensive part (a pair update for every pair of folders a set spans) and DFS-068's tab no longer calls `/summaries` or `/pairs`, so the first `/groups` request would pay for pairs nothing else needs.

## Acceptance criteria

The full ID of each criterion is `DFS-067.AC-<n>`.

- **AC-1** Sets whose copies sit directly in the same folders, with the same `copiesEach` per folder, are answered as one group; a set whose folders or per-folder copy counts differ is in a different group.
- **AC-2** A group lists each folder that directly holds any of its paths, once, never their ancestors, sorted by path.
- **AC-3** A group answers `files`, `copies`, `reclaimableBytes` (sum of (physical copies − 1) × size), and per folder `copiesEach`, `sharedFiles`, `sharedBytes`, `folderFiles`, `folderBytes` and `otherGroups` as defined in Context.
- **AC-4** Paths that are hard links of one physical copy add no copy: they raise neither `copies` nor `reclaimableBytes`, and `copiesEach` counts physical copies, so two links of one copy in one folder count once; a copy with links in several folders appears in each of them.
- **AC-5** Groups are ordered by `reclaimableBytes` descending, then by first folder path, then by the folder lists compared path by path.
- **AC-6** With `under`, only groups with at least one folder at or under that path are answered, and `totalItems` and `hiddenSmall` count only those.
- **AC-7** Groups below `minBytes` (default 1048576) are left out and counted in `hiddenSmall`; `minBytes=0` answers every group and `hiddenSmall` `0`.
- **AC-8** A group answers its first 20 sets, largest reclaimable first, each with `digest`, `name`, `size` and `copies`, and `totalSets` counting all of them.
- **AC-9** The endpoint refuses like `/summaries`: `409` `SCAN_NOT_COMPLETED`, `404` `UNKNOWN_SCAN`, `400` `RELATIVE_PATH`, `403` `PATH_OUTSIDE_ROOTS`, and the same paging refusals.
- **AC-10** A negative `minBytes` is refused with `400` `INVALID_MIN_BYTES` and a `{code, message}` body naming `minBytes`; `minBytes=0` is accepted.

## Scenarios

```gherkin
Feature: Folder groups in the overlaps API

  Background:
    Given a completed scan of root "R"

  @AC-1 @AC-2 @AC-3
  Scenario: Five RAW files shared by a folder and a bundle subfolder form one group
    Given "R/Gemini Bridges" directly holds 190 files, including A.ARW to E.ARW
    And "R/Gemini Bridges/x.auh/Contents/History/resources" directly holds 12 files, including copies of A.ARW to E.ARW
    And each of the 5 files is 41 MB
    When I GET /api/scans/{id}/groups?minBytes=0
    Then one group has files 5, copies 10 and reclaimableBytes 205 MB
    And its folders are "R/Gemini Bridges" (copiesEach 1, sharedFiles 5, folderFiles 190) and "R/Gemini Bridges/x.auh/Contents/History/resources" (copiesEach 1, sharedFiles 5, folderFiles 12), in that order
    And no group lists "R/Gemini Bridges/x.auh", "R/Gemini Bridges/x.auh/Contents" or "R/Gemini Bridges/x.auh/Contents/History"

  @AC-1 @AC-3
  Scenario: Two copies in one folder and one in its parent
    Given "R/Sunset/p.prproj" and "R/Sunset/Auto-Save/p-1.prproj" and "R/Sunset/Auto-Save/p.prproj" are identical, 87.7 KB
    When I GET /api/scans/{id}/groups?minBytes=0
    Then one group has folders "R/Sunset" with copiesEach 1 and "R/Sunset/Auto-Save" with copiesEach 2
    And its copies is 3 and reclaimableBytes is 175.4 KB

  @AC-1
  Scenario: Same folders but different copy counts are different groups
    Given set S1 has one copy in "R/A" and one in "R/B"
    And set S2 has two copies in "R/A" and one in "R/B"
    When I GET /api/scans/{id}/groups?minBytes=0
    Then S1 and S2 are in different groups

  @AC-1 @AC-2 @AC-3
  Scenario: Copies side by side in one folder form a one-folder group
    Given "R/Photos/IMG_001.jpg" and "R/Photos/IMG_001 (1).jpg" are identical, 3 MB, and no other copy exists
    When I GET /api/scans/{id}/groups?minBytes=0
    Then one group has the single folder "R/Photos" with copiesEach 2 and sharedFiles 2
    And its files is 1, copies 2 and reclaimableBytes 3 MB

  @AC-3
  Scenario: A folder in two groups counts the other group
    Given set S1 has copies in "R/Old" and "R/Photos2019"
    And set S2 has copies in "R/Old" and "R/Photos2020"
    When I GET /api/scans/{id}/groups?minBytes=0
    Then "R/Old" answers otherGroups 1 in each of the two groups
    And "R/Photos2019" answers otherGroups 0

  @AC-4
  Scenario: Hard links add no copy
    Given "R/A/f" and "R/B/f" are hard links of one file and "R/C/f" is a separate copy of the same content
    When I GET /api/scans/{id}/groups?minBytes=0
    Then the group holding f answers copies 2 and reclaimableBytes equal to the size of f
    And its folders are "R/A", "R/B" and "R/C", each with copiesEach 1

  @AC-5
  Scenario: Heaviest group first
    Given a group reclaiming 206 MB and a group reclaiming 175 KB
    When I GET /api/scans/{id}/groups?minBytes=0
    Then the 206 MB group comes first

  @AC-6
  Scenario: Filtering by folder
    Given a group with folders "R/A" and "R/B" and a group with folders "R/C" and "R/D"
    When I GET /api/scans/{id}/groups?under=R/B&minBytes=0
    Then only the first group is answered and totalItems is 1

  @AC-7
  Scenario: Small groups are hidden by default
    Given a group reclaiming 206 MB and three groups reclaiming under 1 MiB each
    When I GET /api/scans/{id}/groups
    Then one group is answered and hiddenSmall is 3
    When I GET /api/scans/{id}/groups?minBytes=0
    Then four groups are answered and hiddenSmall is 0

  @AC-8
  Scenario: A group lists its first 20 sets
    Given a group of 25 sets
    When I GET /api/scans/{id}/groups?minBytes=0
    Then the group answers 20 sets, largest reclaimable first, and totalSets 25

  @AC-9
  Scenario: Refusals match the summaries endpoint
    Given a scan that is still running
    When I GET /api/scans/{id}/groups
    Then the answer is 409 SCAN_NOT_COMPLETED
    And a relative under answers 400 RELATIVE_PATH and an under outside the roots answers 403 PATH_OUTSIDE_ROOTS

  @AC-10
  Scenario: A negative minBytes is refused
    Given a completed scan with one group
    When I GET /api/scans/{id}/groups?minBytes=-1
    Then the answer is 400 with code INVALID_MIN_BYTES and a message naming minBytes
    When I GET /api/scans/{id}/groups?minBytes=0
    Then the answer is 200 with the group
```

## Scope

**Touches:**
- `server/src/main/java/us/pochaev/dfs/server/analysis`: the group structure, built once per scan (G6).
- `server/src/main/java/us/pochaev/dfs/server/result`: the `/groups` endpoint and its API records.
- `server/src/test/java/us/pochaev/dfs/server/analysis`: grouping tests, and the scale test with groups built.
- `server/src/test/java/us/pochaev/dfs/server/GroupApiTest.java`: endpoint tests.
- `server/frontend/openapi.json`: regenerated with `updateOpenApi`.
- `CLAUDE.md`, `docs/design/ui.md`: the endpoint in the API description and U3.
- `docs/design/folder-groups.md`: status line set to implemented for G5 and G6.

**Out of scope:**
- The Overlaps tab itself (DFS-068).
- Merging mirrored folder trees into one row (G7, later).
- A plan action per group (G7, comes with the plan screen).
- Removing `/summaries` and `/pairs`.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)
- [ ] `server/frontend/openapi.json` regenerated and `OpenApiSpecTest` green
- [ ] A scale test builds the groups for the 150,000-file case of `OverlapAnalysisScaleTest` within the budget `design.md` fixes, and `OverlapAnalysisScaleTest` itself is unchanged and green

## Open questions

- Archetype (`endpoint` likely) and gates.
