---
id: DFS-047
title: Serve a finished scan's folder tree and duplicate sets
status: in-review
type: feature
scope: [build]
archetype: endpoint
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
autorun: true
priority: high
lane: frontend
depends_on: []
touches:
  - server/src/main/java/us/pochaev/dfs/server/result
  - server/src/main/java/us/pochaev/dfs/server/api
  - server/src/main/java/us/pochaev/dfs/server/scan
  - server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java
  - server/src/test/java/us/pochaev/dfs/server/TestServer.java
  - server/frontend/openapi.json
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-02
completed:
---

# DFS-047: Serve a finished scan's folder tree and duplicate sets

## Story

As the developer of the DFS UI
I want endpoints that list a finished scan's folders one level at a time, with their totals, and its duplicate sets under any folder, largest reclaimable first
So that the result screen (DFS-049) can show the folder tree and the Sets tab on a tested, secured API

## Context

Milestone M-1 ("see the results of a scan"), step 2 of 5 (`docs/design/ui.md`, "Milestones"). It does not depend on the overlap analysis (DFS-046), so the result screen can start before it.

- **U3:** the result view is a lazily loaded folder tree of the scanned roots on the left and tabs on the right, filtered to the folder selected in the tree. The **Sets** tab lists duplicate sets, largest reclaimable first. (The **Overlaps** tab is DFS-048 and DFS-050.)
- **U10, DFS-036 decision 1, DFS-040 "Out of scope":** paths inside a scan are accepted only inside the scan's roots; the first endpoint that takes such a path carries the criterion "a path outside the scan's roots → 403". These are the first such endpoints.
- **The core:** `GetScanResult.result(ScanId)` returns the whole `ScanResult` for a COMPLETED scan, `Failure(SCAN_NOT_COMPLETED)` (message names the status) otherwise, or `Failure(UNKNOWN_SCAN)`. `ScanResult` holds `roots`, `folders` (`FolderTotal(folder, files, bytes)`, one per folder that directly holds counted files, `files ≥ 1`) and `sets` (`DuplicateSet(size, digest, files, copies)`; each `PhysicalCopy(links)` groups the hard-linked paths of one physical copy; at least two copies per set). Folders that hold no counted file in their whole subtree are not in the result.
- **The scan API (DFS-040):** `us.pochaev.dfs.server.scan` holds `ScanController`, `ScanRegistry` (this server run's scans) and the error records `ApiError(code, message)` and `ApiException`, mapped by `ApiExceptionHandler`. An unknown or malformed scan ID answers `404` `UNKNOWN_SCAN`. The endpoint archetype says the first ticket adding a second feature package moves the error types to a shared `us.pochaev.dfs.server.api` package; this ticket is that one.
- **Hard links are not duplicates** (CLAUDE.md safety invariant): reclaimable space counts physical copies, not paths.

## Decisions

1. **Two endpoints in a new feature package `us.pochaev.dfs.server.result`, tag `results`** (2026-11-01). The generated client gets a `ResultsService`. The API's own records, never core types.
2. **`GET /api/scans/{id}/folders[?parent=<absolute path>]`** (`listFolders`) (2026-11-01). Without `parent`: the scan's roots. With `parent`: its immediate subfolders that hold at least one counted file in their subtree. Each item is a `FolderNode(path, name, files, bytes, directFiles, directBytes, hasChildren)`, with `files` and `bytes` counting the whole subtree (rolled up from `ScanResult.folders`), ordered by `name`, case-insensitively. Not paged: one level of one folder.
3. **`GET /api/scans/{id}/sets[?under=<absolute path>][&page=0][&size=50]`** (`listSets`) (2026-11-01). Sets with at least one path inside `under` (all sets without it), ordered by reclaimable bytes (`size × (physical copies − 1)`), largest first, then `size` largest first, then `digest`. Answers a `SetPage(items, page, size, totalItems, totalReclaimableBytes)`, where `totalReclaimableBytes` sums every matching set, not only the page. Each item is a `DuplicateSetView(digest, size, reclaimableBytes, copies)`; each copy a `CopyView(links)`; each link a `LinkView(path, modified)`, links within a copy and copies within a set ordered by path. Every copy is shown, also those outside `under`, since the user wants to see where the other copies are. `size` must be 1 to 500.
4. **Paths** (2026-11-01). Path strings in and out are `Path.toString()` of the scan's paths (backslashes on Windows). An input path is parsed with `Path.of(...).normalize()` and must be absolute and inside a root by `Path.startsWith` (the platform's rules: case-insensitive on Windows), so `C:/data/../other` is outside a root `C:\data`.
5. **Errors** (2026-11-01). `404` `UNKNOWN_SCAN` for an unknown or malformed ID; `409` `SCAN_NOT_COMPLETED` with the core's message for a scan not COMPLETED (queued, running, cancelled, failed); `400` `RELATIVE_PATH` for a relative `parent` or `under`; `400` `INVALID_PAGE` for `page` < 0 or `size` outside 1–500; `403` `PATH_OUTSIDE_ROOTS` for an absolute path outside every root. A path inside a root that the scan holds nothing for answers `200` with an empty list or page, so the API never reveals whether a folder exists. The order of checks: ID, completion, path, page.
6. **A per-scan index, built once** (2026-11-01). The first request for a scan's result builds an index (the folder tree with rolled-up totals and the sets sorted once) from `GetScanResult` and keeps it beside the registry entry for this server run; later requests read it. Building it is thread-safe and happens at most once per scan. DFS-048 adds the overlap analysis to the same per-scan state.
7. **Shared error types move to `us.pochaev.dfs.server.api`** (2026-11-01, the endpoint archetype's rule). `ApiError`, `ApiException` and `ApiExceptionHandler` move there, the handler covering both packages; `ApiError` keeps its schema name, so `openapi.json` changes only by the new operations and schemas, and the existing scan API tests pass unchanged.
8. **Gates `[design, done]`** (2026-11-01). The endpoint archetype's first live run (CCW-019): the user reviews `design.md` and the regenerated spec before tests are written, then the product-owner review.

## Acceptance criteria

The full ID of each criterion is `DFS-047.AC-<n>`.

- **AC-1** `GET /api/scans/{id}/folders` on a COMPLETED scan answers the roots as `FolderNode`s with subtree `files` and `bytes`, direct `directFiles` and `directBytes`, and `hasChildren`; with `parent` set to a folder, it answers that folder's immediate subfolders holding counted files, ordered by name case-insensitively, each with its subtree totals.
- **AC-2** A folder that holds only subfolders appears with the totals of its whole subtree and `directFiles` 0; a folder with no counted file in its subtree does not appear.
- **AC-3** `GET /api/scans/{id}/sets` answers a `SetPage` of every set, ordered by reclaimable bytes largest first, then size largest first, then digest; each set shows its digest, size, reclaimable bytes and every physical copy with its links (path and modified time); `totalItems` and `totalReclaimableBytes` cover every matching set.
- **AC-4** Reclaimable bytes count physical copies: a set of three paths where two are hard links of one physical copy has reclaimable bytes of one size, and its two linked paths appear in one `CopyView`.
- **AC-5** `under` keeps only the sets with at least one path inside that folder's subtree, still showing each kept set's copies outside it; `page` and `size` page the ordered list, and a page past the end is empty with the same totals.
- **AC-6** A `parent` or `under` outside every root of the scan answers `403` with code `PATH_OUTSIDE_ROOTS`, also when written with `..` that normalizes outside a root; a relative one answers `400` `RELATIVE_PATH`; an absolute path inside a root that the scan holds nothing for answers `200` with an empty list or page.
- **AC-7** A scan that is not COMPLETED answers `409` with code `SCAN_NOT_COMPLETED` on both endpoints; an unknown or malformed scan ID answers `404` `UNKNOWN_SCAN`; `page` −1, `size` 0 and `size` 501 answer `400` `INVALID_PAGE`.
- **AC-8** Both endpoints answer `403` without the session cookie and with a foreign `Host`.
- **AC-9** `server/frontend/openapi.json` describes `listFolders` and `listSets` (tag `results`) and the schemas `FolderNode`, `SetPage`, `DuplicateSetView`, `CopyView` and `LinkView`; `ApiError` keeps its name; `OpenApiSpecTest` passes; the generated client has a `ResultsService`; the scan API tests (`ScanApiTest`) pass unchanged after `ApiError`, `ApiException` and `ApiExceptionHandler` move to `us.pochaev.dfs.server.api`.
- **AC-10** `CLAUDE.md`'s `server` entry lists the two endpoints and the path-inside-roots rule, and `docs/design/ui.md` U3 and U10 name them.

## Scenarios

AC-1 to AC-9 are JUnit tests in `server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java` against a real server on a random port (`TestServer`), scanning folders built in a `@TempDir`; the foreign `Host` over a plain socket, as in DFS-036 and DFS-040. Hard links are created at test time with `Files.createLink` and the scenario is skipped (`Assumptions`) where that fails. A queued scan comes from `HoldingExecutorConfiguration`. AC-10 is read and recorded in `verification.md`.

```gherkin
Feature: Scan result API: folder tree and duplicate sets

  Background:
    Given the server runs on 127.0.0.1 port P with the browser opener off
    And C is a session cookie from the printed token
    And T is a temp folder holding:
      | path                | content                              |
      | Photos/a.jpg        | 300 KB, content A                    |
      | Photos/b.jpg        | 200 KB, content B                    |
      | Backup/2019/a.jpg   | content A                            |
      | Backup/2019/b.jpg   | content B                            |
      | Backup/2019/c.jpg   | 100 KB, content C, unique            |
      | Backup/old/a2.jpg   | content A                            |
      | Empty/              | no files                             |
    And a scan of T has COMPLETED with ID S

  @AC-1
  Scenario: The roots and one level below
    When a client sends GET /api/scans/S/folders with C
    Then the response is 200 with one FolderNode: path T, 6 files, 1,400 KB, 0 direct files, hasChildren true
    When a client sends GET /api/scans/S/folders?parent=<T> with C
    Then the items are Backup then Photos
    And Backup shows 4 files, 900 KB, 0 direct files, hasChildren true
    And Photos shows 2 files, 500 KB, 2 direct files, 500 KB direct, hasChildren false

  @AC-2
  Scenario: Folders without counted files do not appear
    When a client sends GET /api/scans/S/folders?parent=<T> with C
    Then no item is named Empty
    When a client sends GET /api/scans/S/folders?parent=<T>/Backup with C
    Then the items are 2019 (3 files, 600 KB) then old (1 file, 300 KB)

  @AC-3
  Scenario: Every set, largest reclaimable first
    When a client sends GET /api/scans/S/sets with C
    Then the page holds 2 sets, totalItems 2 and totalReclaimableBytes 800 KB
    And the first set has size 300 KB, reclaimable 600 KB and 3 copies: Backup/2019/a.jpg, Backup/old/a2.jpg, Photos/a.jpg
    And the second set has size 200 KB, reclaimable 200 KB and 2 copies
    And every link shows its path and modified time

  @AC-3
  Scenario: Equal reclaimable bytes are ordered by size, then digest
    Given T2 holds two sets: one of size 100 KB with 3 copies, one of size 200 KB with 2 copies
    And a scan of T2 has COMPLETED with ID S2
    When a client sends GET /api/scans/S2/sets with C
    Then the 200 KB set comes first, both with reclaimable 200 KB

  @AC-4
  Scenario: A hard link is not a reclaimable copy
    Given T3 holds x.bin (100 KB), x-link.bin as a hard link of x.bin, and copy/x.bin with the same content
    And a scan of T3 has COMPLETED with ID S3
    When a client sends GET /api/scans/S3/sets with C
    Then the one set has reclaimable bytes 100 KB and 2 copies
    And one copy has the links x-link.bin and x.bin

  @AC-5
  Scenario: Sets under a folder keep their other copies
    When a client sends GET /api/scans/S/sets?under=<T>/Backup/old with C
    Then the page holds 1 set, totalItems 1, totalReclaimableBytes 600 KB
    And that set still lists Photos/a.jpg and Backup/2019/a.jpg among its copies

  @AC-5
  Scenario: Paging
    When a client sends GET /api/scans/S/sets?page=1&size=1 with C
    Then the page holds the 200 KB set, page 1, size 1, totalItems 2
    When a client sends GET /api/scans/S/sets?page=5&size=1 with C
    Then the page holds no set, totalItems 2, totalReclaimableBytes 800 KB

  @AC-6
  Scenario Outline: Paths are checked against the scan's roots
    When a client sends <request> with C
    Then the response is <status> <body>

    Examples:
      | request                                               | status | body                          |
      | GET /api/scans/S/folders?parent=<outside T>           | 403    | with code PATH_OUTSIDE_ROOTS  |
      | GET /api/scans/S/sets?under=<outside T>               | 403    | with code PATH_OUTSIDE_ROOTS  |
      | GET /api/scans/S/folders?parent=<T>/Photos/../..      | 403    | with code PATH_OUTSIDE_ROOTS  |
      | GET /api/scans/S/folders?parent=Photos                | 400    | with code RELATIVE_PATH       |
      | GET /api/scans/S/sets?under=Photos                    | 400    | with code RELATIVE_PATH       |
      | GET /api/scans/S/folders?parent=<T>/Empty             | 200    | with no items                 |
      | GET /api/scans/S/sets?under=<T>/no-such-folder        | 200    | with no sets, totalItems 0    |

  @AC-7
  Scenario Outline: Unfinished and unknown scans, bad paging
    When a client sends <request> with C
    Then the response is <status> with code "<code>"

    Examples:
      | request                                                     | status | code               |
      | GET /api/scans/<queued Q>/folders                           | 409    | SCAN_NOT_COMPLETED |
      | GET /api/scans/<queued Q>/sets                              | 409    | SCAN_NOT_COMPLETED |
      | GET /api/scans/00000000-0000-0000-0000-000000000000/folders | 404    | UNKNOWN_SCAN       |
      | GET /api/scans/not-a-uuid/sets                              | 404    | UNKNOWN_SCAN       |
      | GET /api/scans/S/sets?page=-1                               | 400    | INVALID_PAGE       |
      | GET /api/scans/S/sets?size=0                                | 400    | INVALID_PAGE       |
      | GET /api/scans/S/sets?size=501                              | 400    | INVALID_PAGE       |

  @AC-8
  Scenario Outline: The result endpoints are secured
    When a client sends <request> <condition>
    Then the response is 403

    Examples:
      | request                     | condition                       |
      | GET /api/scans/S/folders    | without C                       |
      | GET /api/scans/S/sets       | without C                       |
      | GET /api/scans/S/folders    | with C and Host evil.example:P  |

  @AC-9
  Scenario: The spec, the client and the moved error types
    Then server/frontend/openapi.json has the operations listFolders and listSets, tagged "results"
    And it has the schemas FolderNode, SetPage, DuplicateSetView, CopyView, LinkView and ApiError
    And OpenApiSpecTest passes and the generated client has a ResultsService
    And ApiError, ApiException and ApiExceptionHandler are in us.pochaev.dfs.server.api
    And ScanApiTest passes without changes

  @AC-10
  Scenario: Docs describe the result API
    Then CLAUDE.md's server entry names GET /api/scans/{id}/folders and GET /api/scans/{id}/sets and the 403 for paths outside the roots
    And docs/design/ui.md U3 and U10 name the two endpoints
```

## Scope

**Touches:**
- `server/src/main/java/us/pochaev/dfs/server/result`: the new feature package (controller, the per-scan index, API records).
- `server/src/main/java/us/pochaev/dfs/server/api`: the new shared package holding the moved error types (decision 7).
- `server/src/main/java/us/pochaev/dfs/server/scan`: the error types leave; `ScanController` imports them from `api`; `ScanRegistry` gains the per-scan state the index lives in.
- `server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java`: the result API tests (one class, or more classes named `ResultApi*Test.java` beside it).
- `server/src/test/java/us/pochaev/dfs/server/TestServer.java`: helpers if needed.
- Narrow on purpose: DFS-046 works in parallel in `server/.../analysis` and the server's `ArchitectureTest`, which this ticket leaves alone.
- `server/frontend/openapi.json`: regenerated with `:server:updateOpenApi` in the design phase.
- `CLAUDE.md`: the `server` entry.
- `docs/design/ui.md`: U3, U10.

**Out of scope:**
- The overlap analysis and its endpoints (DFS-046, DFS-048).
- Any screen (DFS-049).
- Listing a folder's files, live from disk (U4, milestone M-2).
- Skipped and excluded files in the result view.
- Evicting a scan's index or the scan itself (kept for the server run, as scans are, U2).
- Sorting or filtering sets other than by `under` (size thresholds, file types): display choices for later.

## Definition of done

- [x] Every scenario above is an automated test and passes, except AC-10 (read), recorded in `verification.md` (ResultApiTest 26/26, 0 skipped; AC-10 also read)
- [x] Each test's `@DisplayName` starts with its criterion ID (`DFS-047.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit tests, `OpenApiSpecTest` and the Vitest tests)
- [x] Every endpoint-archetype checklist item is addressed in `design.md` or marked not applicable with a reason
- [x] Started by hand once on Windows: a real folder scanned, then both endpoints exercised with `curl` (the roots, two levels down, a page of sets, a path outside the roots), the server log free of warnings and errors (2026-11-02, bootJar, fixtures folder: 23 files, 2 sets, 45 bytes reclaimable; 403 for C:/Windows; no result-endpoint warnings)
- [x] No files changed outside the paths listed under Touches

## Open questions

None.
