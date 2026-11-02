---
id: DFS-048
title: Serve folder summaries and overlap pairs for a finished scan
status: in-progress
type: feature
scope: [build]
archetype: endpoint
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [done]
autorun: true
priority: high
lane: frontend
depends_on: [DFS-046, DFS-047]
touches:
  - server/src/main/java/us/pochaev/dfs/server/result
  - server/src/main/java/us/pochaev/dfs/server/scan
  - server/src/test/java/us/pochaev/dfs/server/OverlapApiTest.java
  - server/src/test/java/us/pochaev/dfs/server/result
  - server/src/test/java/us/pochaev/dfs/server/TestServer.java
  - server/frontend/openapi.json
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-02
completed:
---

# DFS-048: Serve folder summaries and overlap pairs for a finished scan

## Story

As the developer of the DFS UI
I want endpoints that answer a finished scan's folder summaries and overlap pairs under any folder, and the overlap of any two folders I pick
So that the Overlaps tab (DFS-050) can rank folders by reclaimable space and show where their copies are

## Context

Milestone M-1 ("see the results of a scan"), step 3 of 5 (`docs/design/ui.md`, "Milestones").

- **U3:** the Overlaps tab is the landing tab of the result view: folder summaries and folder pairs ranked by reclaimable bytes, filtered to the folder selected in the tree.
- **U5b:** the server answers small paged queries (summary for a folder, pairs under a folder, pair for two chosen folders) rather than sending the whole result.
- **DFS-046** provides `OverlapAnalysis.of(ScanResult)` in `us.pochaev.dfs.server.analysis`: `summaries()`, `summary(folder)`, `pairs()`, `pair(x, y)` (which throws `IllegalArgumentException` for equal, nested or out-of-root folders), and the records `FolderSummary`, `PairSide`, `FolderPair`.
- **DFS-047** provides the result feature package (`us.pochaev.dfs.server.result`), the per-scan index kept beside the registry, the path checks (`RELATIVE_PATH`, `PATH_OUTSIDE_ROOTS`), the paging conventions (`page`, `size` 1–500, `INVALID_PAGE`) and the shared error types in `us.pochaev.dfs.server.api`.

## Decisions

1. **Three read-only endpoints in DFS-047's `result` package, tag `overlaps`** (2026-11-01). `GET /api/scans/{id}/summaries?under=&page=&size=`, `GET /api/scans/{id}/pairs?under=&page=&size=` and `GET /api/scans/{id}/pair?a=&b=`, in their own controller, reusing DFS-047's per-scan index, path checks and paging; the generated client gets an `OverlapsService`.
2. **`summaries?under=X` lists every listed descendant of X, ranked, plus X's own summary** (2026-11-01). The items are DFS-046's `summaries()` restricted to X's subtree, excluding X, in DFS-046's fixed order; X's own `summary(X)` comes as a separate field of the page, also when X itself would not be listed. Without `under`, the items are every listed summary and the separate field is empty. This shows a covered folder however deep it sits, which the tree cannot. A covered folder's ancestors often appear near it: an ancestor counts the same covered files unless their copies lie inside the ancestor's own subtree. Not only immediate children (the tree already shows one level); a `depth` parameter can be added if DFS-050 needs it.
3. **`pairs?under=X` lists every listed pair with at least one side inside X's subtree, X itself included** (2026-11-01). In DFS-046's fixed order; without `under`, every listed pair. The other side may lie anywhere, since it is where the copies are (selecting `Old-laptop` shows `Old-laptop/Pictures` with `Photos/2019`). Not "both sides inside X" (would hide exactly that case). The API does not mark which side lies under X; the screen compares paths.
4. **`pair?a=&b=` refusals: one shape, one mapping** (2026-11-01). `a` and `b` are optional in the spec and checked by the controller, so every refusal is an `ApiError`. Checks in order: unknown or malformed ID `404` `UNKNOWN_SCAN`; not COMPLETED `409` `SCAN_NOT_COMPLETED`; `a` or `b` absent `400` `MISSING_FOLDER`; relative `400` `RELATIVE_PATH`; outside every root `403` `PATH_OUTSIDE_ROOTS`; equal or nested `400` `NESTED_FOLDERS`, whose message names the case ("both folders are <a>" or "<a> contains <b>"). A folder inside a root that the scan holds nothing for answers `200` with zeros on that side, as DFS-047 never reveals whether a folder exists. `a` and `b` may come in either order; the answer always has `a` before `b` by path (DFS-046). Not zeros for nested folders (`folder-overlap-analysis.md` decision 3 refuses them); not Spring's own `400` for a missing parameter (a second error shape).
5. **The analysis is computed lazily, at most once per scan, and kept for the server run** (2026-11-01). The first overlaps request for a scan runs `OverlapAnalysis.of` on its `ScanResult`; requests arriving meanwhile wait for that same computation (one memoized computation per scan, beside DFS-047's index), and later requests read the result. Kept as long as the scan (U2); eviction is out of scope. Not eagerly on completion (a new pending state for the API and the screen, and work spent on scans nobody opens) and not with its own progress stream; if DFS-046's measured time on the user's drive is noticeably slow, starting the computation on completion is a follow-up that changes neither the API nor the screen.
6. **The API records follow DFS-046's records one to one** (2026-11-01). `FolderSummaryView(folder, totalFiles, totalBytes, filesWithCopyElsewhere, bytesWithCopyElsewhere, internalDuplicateFiles, internalBytes)`; `SummaryPage(folder, items, page, size, totalItems)`, where `folder` is the `FolderSummaryView` of `under` (nullable: absent without `under`); `PairSideView(folder, filesWithCopy, totalFiles, bytesWithCopy, totalBytes, full)`; `FolderPairView(a, b)`; `PairPage(items, page, size, totalItems)`. `full` is true only when the side has at least one file and every file has a copy (a side with no files is never "full"). Operation IDs `listSummaries`, `listPairs`, `getPair`. Paths as `Path.toString()`, as in DFS-047.
7. **Gates `[done]`, `autorun: true`** (2026-11-01). No deletion is planned or executed; the shapes a design review would check are settled in decisions 1-6, and DFS-047 makes the endpoint archetype's first live run, with a design gate, before this one. Starts itself when DFS-046 and DFS-047 are both done.

## Acceptance criteria

The full ID of each criterion is `DFS-048.AC-<n>`.

- **AC-1** `GET /api/scans/{id}/summaries` on a COMPLETED scan answers a `SummaryPage` of every listed folder summary in DFS-046's order with `folder` absent; with `under` set to a folder X, its items are the listed summaries strictly inside X's subtree in the same order, and `folder` is X's own summary, also when X itself is not listed.
- **AC-2** `GET /api/scans/{id}/pairs` answers a `PairPage` of every listed pair in DFS-046's order; with `under` set to X, only the pairs with at least one side inside X's subtree, X included, whatever the other side.
- **AC-3** `GET /api/scans/{id}/pair?a=&b=` answers the `FolderPairView` of any two folders inside the roots, each counted recursively, with `a` before `b` by path whatever order they were given in; a folder the scan holds nothing for answers a side of zeros with `full` false.
- **AC-4** `pair` refuses with an `ApiError`: `400` `MISSING_FOLDER` when `a` or `b` is absent, `400` `RELATIVE_PATH` for a relative folder, `403` `PATH_OUTSIDE_ROOTS` for a folder outside every root (also through `..`), and `400` `NESTED_FOLDERS` for equal folders or one containing the other, the message naming which.
- **AC-5** On `summaries` and `pairs`, `under` follows DFS-047's path rules (`403` `PATH_OUTSIDE_ROOTS`, `400` `RELATIVE_PATH`, `200` with no items for a folder inside a root that holds nothing, `summaries` then showing that folder's summary as zeros) and `page`/`size` page the ordered list (`400` `INVALID_PAGE` for `page` < 0 or `size` outside 1-500; a page past the end is empty with the same `totalItems`).
- **AC-6** All three endpoints answer `409` `SCAN_NOT_COMPLETED` for a scan that is not COMPLETED and `404` `UNKNOWN_SCAN` for an unknown or malformed scan ID, checked before any parameter.
- **AC-7** Hard links do not count through the API: when folder B holds a hard link of a file in folder A, `pairs` lists no pair of A and B for it, and `pair` of A and B shows 0 files with a copy on both sides, while a real copy in a third folder still pairs with each of them.
- **AC-8** A scan's analysis is computed at most once: concurrent first requests for the same scan wait for one computation and get the same result, and later requests do not compute again.
- **AC-9** All three endpoints answer `403` without the session cookie and with a foreign `Host`.
- **AC-10** `server/frontend/openapi.json` describes `listSummaries`, `listPairs` and `getPair` (tag `overlaps`) and the schemas `SummaryPage`, `FolderSummaryView`, `PairPage`, `FolderPairView` and `PairSideView`; `OpenApiSpecTest` passes and the generated client has an `OverlapsService`.
- **AC-11** `CLAUDE.md`'s `server` entry lists the three endpoints, and `docs/design/ui.md` U3 names them and U5b names DFS-046's package `us.pochaev.dfs.server.analysis` and `OverlapAnalysis.of`.

## Scenarios

AC-1 to AC-7, AC-9 and AC-10 are JUnit tests in `server/src/test/java/us/pochaev/dfs/server/OverlapApiTest.java` against a real server on a random port (`TestServer`), scanning folders built in a `@TempDir`; the foreign `Host` over a plain socket, as in DFS-036 and DFS-040; a queued scan from `HoldingExecutorConfiguration`; hard links created at test time with `Files.createLink`, the scenario skipped (`Assumptions`) where that fails. AC-8 is a unit test in `server/src/test/java/us/pochaev/dfs/server/result` of the per-scan state, with an analysis function that counts its calls and blocks on a latch until both requests are waiting; no `Thread.sleep`. AC-11 is read and recorded in `verification.md`. Sizes are in KB of 1,024 bytes.

```gherkin
Feature: Overlaps API

  Background:
    Given the server runs on 127.0.0.1 port P with the browser opener off
    And C is a session cookie from the printed token
    And T is a temp folder holding:
      | path                | content                     |
      | Photos/2019/a.jpg   | 300 KB, content A           |
      | Photos/2019/b.jpg   | 200 KB, content B           |
      | Photos/2019/u.jpg   | 100 KB, unique              |
      | Backup/2019/a.jpg   | content A                   |
      | Backup/2019/b.jpg   | content B                   |
      | Old/Pictures/a.jpg  | content A                   |
      | Empty/              | no files                    |
    And a scan of T has COMPLETED with ID S

  @AC-1
  Scenario: Every listed summary, in order
    When a client sends GET /api/scans/S/summaries with C
    Then folder is absent and totalItems is 7
    And the items are Backup, Backup/2019, Photos, Photos/2019, Old, Old/Pictures, T, in that order
    And Photos/2019 shows 3 total files, 600 KB, 2 files and 500 KB with a copy elsewhere, 0 internal duplicates
    And T shows 6 total files, 0 with a copy elsewhere, 3 internal duplicate files and 800 KB internal bytes

  @AC-1
  Scenario: Summaries under a folder, with the folder's own summary
    When a client sends GET /api/scans/S/summaries?under=<T>/Backup with C
    Then the items are Backup/2019 alone
    And folder is Backup with 2 total files, 2 with a copy elsewhere, 500 KB
    When a client sends GET /api/scans/S/summaries?under=<T>/Photos/2019 with C
    Then there are no items
    And folder is Photos/2019 with 3 total files and 2 with a copy elsewhere

  @AC-2
  Scenario: Every listed pair, in order
    When a client sends GET /api/scans/S/pairs with C
    Then totalItems is 3 and the pairs are, in order:
      | a            | a side                   | b            | b side                    |
      | Backup/2019  | 2 / 2, 500 KB, full      | Photos/2019  | 2 / 3, 500 of 600 KB      |
      | Backup/2019  | 1 / 2, 300 of 500 KB     | Old/Pictures | 1 / 1, 300 KB, full       |
      | Old/Pictures | 1 / 1, 300 KB, full      | Photos/2019  | 1 / 3, 300 of 600 KB      |

  @AC-2
  Scenario: Pairs under a folder keep their other side
    When a client sends GET /api/scans/S/pairs?under=<T>/Old with C
    Then the pairs are (Backup/2019, Old/Pictures) then (Old/Pictures, Photos/2019)

  @AC-3
  Scenario: The pair of two folders, counted recursively, in either order
    When a client sends GET /api/scans/S/pair?a=<T>/Photos&b=<T>/Backup with C
    Then a is Backup with 2 / 2 files with a copy, 500 KB, full
    And b is Photos with 2 / 3 files with a copy, 500 of 600 KB, not full
    When a client sends GET /api/scans/S/pair?a=<T>/Backup&b=<T>/Photos with C
    Then the answer is the same

  @AC-3
  Scenario: A folder the scan holds nothing for
    When a client sends GET /api/scans/S/pair?a=<T>/Empty&b=<T>/Photos with C
    Then a is Empty with 0 / 0 files, 0 bytes, not full
    And b is Photos with 0 / 3 files with a copy

  @AC-4
  Scenario Outline: Refused pair queries
    When a client sends GET /api/scans/S/pair?<query> with C
    Then the response is <status> with code "<code>"

    Examples:
      | query                                   | status | code               |
      | a=<T>/Photos                            | 400    | MISSING_FOLDER     |
      | b=<T>/Photos                            | 400    | MISSING_FOLDER     |
      | a=Photos&b=<T>/Backup                   | 400    | RELATIVE_PATH      |
      | a=<outside T>&b=<T>/Backup              | 403    | PATH_OUTSIDE_ROOTS |
      | a=<T>/Photos/../..&b=<T>/Backup         | 403    | PATH_OUTSIDE_ROOTS |
      | a=<T>/Photos&b=<T>/Photos               | 400    | NESTED_FOLDERS     |
      | a=<T>/Photos&b=<T>/Photos/2019          | 400    | NESTED_FOLDERS     |
      | a=<T>/Photos/2019&b=<T>/Photos          | 400    | NESTED_FOLDERS     |

  @AC-5
  Scenario Outline: The folder filter follows the scan's roots
    When a client sends <request> with C
    Then the response is <status> <body>

    Examples:
      | request                                                | status | body                                         |
      | GET /api/scans/S/summaries?under=<outside T>           | 403    | with code PATH_OUTSIDE_ROOTS                 |
      | GET /api/scans/S/pairs?under=<T>/Photos/../..          | 403    | with code PATH_OUTSIDE_ROOTS                 |
      | GET /api/scans/S/pairs?under=Photos                    | 400    | with code RELATIVE_PATH                      |
      | GET /api/scans/S/pairs?under=<T>/Empty                 | 200    | with no items, totalItems 0                  |
      | GET /api/scans/S/summaries?under=<T>/Empty             | 200    | with no items and folder Empty, all zeros    |

  @AC-5
  Scenario Outline: Paging
    When a client sends <request> with C
    Then the response is <outcome>

    Examples:
      | request                                        | outcome                                                     |
      | GET /api/scans/S/pairs?page=1&size=2           | 200 with the third pair alone, page 1, size 2, totalItems 3 |
      | GET /api/scans/S/summaries?page=9&size=5       | 200 with no items, totalItems 7                             |
      | GET /api/scans/S/pairs?page=-1                 | 400 with code INVALID_PAGE                                  |
      | GET /api/scans/S/summaries?size=0              | 400 with code INVALID_PAGE                                  |
      | GET /api/scans/S/summaries?size=501            | 400 with code INVALID_PAGE                                  |

  @AC-6
  Scenario Outline: Unfinished and unknown scans
    When a client sends <request> with C
    Then the response is <status> with code "<code>"

    Examples:
      | request                                                                  | status | code               |
      | GET /api/scans/<queued Q>/summaries                                      | 409    | SCAN_NOT_COMPLETED |
      | GET /api/scans/<queued Q>/pairs                                          | 409    | SCAN_NOT_COMPLETED |
      | GET /api/scans/<queued Q>/pair                                           | 409    | SCAN_NOT_COMPLETED |
      | GET /api/scans/00000000-0000-0000-0000-000000000000/summaries            | 404    | UNKNOWN_SCAN       |
      | GET /api/scans/not-a-uuid/pairs                                          | 404    | UNKNOWN_SCAN       |
      | GET /api/scans/not-a-uuid/pair?a=relative                                | 404    | UNKNOWN_SCAN       |

  @AC-7
  Scenario: Hard links are not copies through the API
    Given T2 holds A/x.bin (100 KB), B/x.bin as a hard link of A/x.bin, and C/x.bin with the same content
    And a scan of T2 has COMPLETED with ID S2
    When a client sends GET /api/scans/S2/pairs with C
    Then the pairs are (A, C) and (B, C), and no pair of A and B
    When a client sends GET /api/scans/S2/pair?a=<T2>/A&b=<T2>/B with C
    Then both sides show 0 files with a copy

  @AC-8
  Scenario: The analysis is computed once per scan
    Given the per-scan state of a completed scan with an analysis function that counts its calls and holds until released
    When two requests ask for the scan's analysis at the same time
    And the function is released
    Then the function was called once and both requests got the same analysis
    When a third request asks for it
    Then the function is still called only once

  @AC-9
  Scenario Outline: The overlaps endpoints are secured
    When a client sends <request> <condition>
    Then the response is 403

    Examples:
      | request                                        | condition                       |
      | GET /api/scans/S/summaries                     | without C                       |
      | GET /api/scans/S/pairs                         | without C                       |
      | GET /api/scans/S/pair?a=<T>/Photos&b=<T>/Old   | without C                       |
      | GET /api/scans/S/pairs                         | with C and Host evil.example:P  |

  @AC-10
  Scenario: The spec and the generated client
    Then server/frontend/openapi.json has the operations listSummaries, listPairs and getPair, tagged "overlaps"
    And it has the schemas SummaryPage, FolderSummaryView, PairPage, FolderPairView and PairSideView
    And OpenApiSpecTest passes and the generated client has an OverlapsService

  @AC-11
  Scenario: Docs describe the overlaps API
    Then CLAUDE.md's server entry names the summaries, pairs and pair endpoints
    And docs/design/ui.md U3 names them, and U5b names us.pochaev.dfs.server.analysis and OverlapAnalysis.of
```

## Scope

**Touches:**
- `server/src/main/java/us/pochaev/dfs/server/result`: the overlaps controller, the API records (decision 6), the memoized analysis in the per-scan state (decision 5).
- `server/src/main/java/us/pochaev/dfs/server/scan`: `ScanRegistry`, only if the per-scan state DFS-047 placed there needs a field for the analysis.
- `server/src/test/java/us/pochaev/dfs/server/OverlapApiTest.java`: the API tests (one class, or more classes named `OverlapApi*Test.java` beside it).
- `server/src/test/java/us/pochaev/dfs/server/result`: the AC-8 unit test.
- `server/src/test/java/us/pochaev/dfs/server/TestServer.java`: helpers if needed.
- `server/frontend/openapi.json`: regenerated with `:server:updateOpenApi` in the design phase.
- `CLAUDE.md`: the `server` entry.
- `docs/design/ui.md`: U3, U5b.

**Out of scope:**
- The analysis itself (DFS-046); this ticket only calls `OverlapAnalysis.of` and maps its records.
- The Overlaps tab (DFS-050).
- Charts (overlap matrix, treemap): a later addition (U3).
- Computing the analysis when a scan completes, or streaming its progress (decision 5).
- A `depth` parameter for summaries, or a filter for pairs with both sides under a folder (decisions 2 and 3).
- Evicting a scan's analysis (kept for the server run, decision 5).
- Changes to the dev proxy: every new endpoint is a `GET` without a stream.

## Definition of done

- [x] Every scenario above is an automated test and passes, except AC-11 (read), recorded in `verification.md`
- [x] Each test's `@DisplayName` starts with its criterion ID (`DFS-048.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit tests, `OpenApiSpecTest` and the Vitest tests)
- [x] Every endpoint-archetype checklist item is addressed in `design.md` or marked not applicable with a reason
- [x] No test this ticket adds uses `Thread.sleep` (AC-8 waits on latches)
- [x] Started by hand once on Windows: a real folder scanned, then the three endpoints exercised with `curl` (summaries with and without `under`, pairs under a folder, one pair, one refused pair), the server log free of warnings and errors (verifier, 2026-11-02: `bootRun` on Windows 10, scratch tree plus the worktree's `docs/` as a real folder, 206 files under `docs/tickets`; summaries with and without `under`, pairs under `Old`, pair Photos/Backup, pair across roots, refused NESTED_FOLDERS, MISSING_FOLDER, PATH_OUTSIDE_ROOTS, 403 without cookie; no WARN/ERROR line in the log)
- [x] No files changed outside the paths listed under Touches

## Open questions

None.
