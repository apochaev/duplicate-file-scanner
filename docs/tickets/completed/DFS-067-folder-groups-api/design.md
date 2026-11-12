# DFS-067: Folder groups in the overlaps API — design

## Summary

One new read-only operation, `GET /api/scans/{id}/groups?under=&minBytes=&page=&size=` (operation ID `listGroups`, tag `overlaps`, so the generated `OverlapsService` gains `listGroups`). It is a fourth method on `OverlapController` in `us.pochaev.dfs.server.result`, so it reuses that controller's path and paging checks. Nothing is stateful beyond a per-scan structure; no stream.

- AC-1, AC-2: `analysis.FolderGroups.of(ScanResult)` (stub) keys each set by its sorted `(folder, copiesEach)` list and merges sets with equal keys into one `FolderGroup`; folders are the parents of link paths only.
- AC-3: `GroupFolder` carries `copiesEach`, `sharedFiles`, `sharedBytes`, `folderFiles`, `folderBytes` (from `ScanResult.folders`) and `otherGroups` (groups listing the folder, minus one, over all groups of the scan); `FolderGroup` carries `files`, `copies`, `reclaimableBytes`.
- AC-4: copies are per physical copy: `copiesEach` counts a copy once per folder however many links it has there; `copies` and `reclaimableBytes` use `set.copies().size()`; a copy with links in several folders appears in each (ticket decision 2).
- AC-5: `FolderGroups.groups()` is sorted once (reclaimable descending, first folder path, folder lists path by path).
- AC-6, AC-7: `result.GroupIndex.page(under, minBytes, page, size)` (stub) filters by `under`, splits by `minBytes`, pages, and maps to `GroupPage` (`totalItems`, `hiddenSmall`).
- AC-8: `FolderGroup.sets` holds the first 20 sets, `totalSets` all of them.
- AC-9, AC-10: `OverlapController.listGroups` (stub) refuses in the order scan, path, paging, `minBytes` (`INVALID_MIN_BYTES`), then builds.
- Package placement: groups structure in `us.pochaev.dfs.server.analysis` (no Spring), endpoint, state, index and API records in `us.pochaev.dfs.server.result`. No `module-info` change (server is not a module).
- State: `GroupState` kept by `ScanRegistry.completedState(id, GroupState.class, ...)` under its own key, groups built lazily on first `/groups` (decision 4). `OverlapAnalysis` and `OverlapState` are unchanged.
- Scale budget (fixed by measurement): building the groups for the 150,000-file case of `OverlapAnalysisScaleTest` must take under 3 seconds. A prototype of the same algorithm took 0.6 s on its first (cold) run and 0.36 to 0.42 s warm (50k sets, 49,991 groups, one 500-folder group). 3 s is five times the cold figure and still fails on any pairwise or per-ancestor blow-up. The existing test stays unchanged.
- Look closely: `totalItems` counts matching groups at or above `minBytes` (the paging total; `hiddenSmall` counts those below); `otherGroups` counts across every group of the scan whatever `under` and `minBytes` ask for; `files` and `totalSets` are always equal (the spec names both).
- Checklist items marked not applicable: 9 (no stream). Item 11 (dev proxy) is applicable only as a GET, checked by hand once. Items 1 and 10 are covered by the matrix and notes below.
- Expected breakage: None. (Verified by running `:server:test --continue` against the stubs.)
- Tests read for the design: No tests read (the red run was run, not read).

## Endpoints

| Method | Path | Tag | Operation ID | Request body | Success | Errors (status: code) |
|---|---|---|---|---|---|---|
| `GET` | `/api/scans/{id}/groups` | `overlaps` | `listGroups` | none; query `under` (optional), `minBytes` (default 1048576), `page` (0), `size` (50) | `200 GroupPage` | `400: RELATIVE_PATH, INVALID_PAGE, INVALID_MIN_BYTES; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |

Check order (so a refused request builds nothing): scan ID and completion, `under` (`RELATIVE_PATH` then `PATH_OUTSIDE_ROOTS`, an unparsable path included), `page`/`size` (`INVALID_PAGE`, as `/summaries`), `minBytes < 0` (`INVALID_MIN_BYTES`), then `GroupState.index()`. A non-numeric `minBytes`, `page` or `size` is Spring's own `400` (ticket decision 3).

## Security matrix

| Path | Cookie | `Host` | `Origin` (state-changing only) | Path inside a scan's roots | Side effect checked absent after a refusal |
|---|---|---|---|---|---|
| `/api/scans/{id}/groups` | `GroupApiTest` missing cookie answers 403 | `GroupApiTest` foreign `Host` is refused | not state-changing (GET) | `GroupApiTest` `under` outside the roots answers 403 `PATH_OUTSIDE_ROOTS`; relative answers 400 | a refused request (`409`, `400`, `403`) never builds the groups: `GroupState` is constructed with a counting build function in an `analysis`/`result` test, as DFS-048's AC-8 does for `OverlapState` |

The test names are the test-author's; the rules are fixed here.

## API records

All in `us.pochaev.dfs.server.result`, public, with `@Schema(name=...)`, mapped from the `analysis` records (never core types). Every component is `requiredMode = REQUIRED`; none is nullable.

- `GroupPage(items: List<FolderGroupView>, page, size, totalItems, hiddenSmall)`: from `GroupIndex.page`. `totalItems`: matching groups at or above `minBytes`; `hiddenSmall`: matching groups below it (ints).
- `FolderGroupView(files: long, copies: long, reclaimableBytes: long, folders: List<GroupFolderView>, sets: List<GroupSetView>, totalSets: int)`: from `FolderGroup`. `files == totalSets`.
- `GroupFolderView(folder: String (Path.toString()), copiesEach: int, sharedFiles: long, sharedBytes: long, folderFiles: long, folderBytes: long, otherGroups: int)`: from `GroupFolder`.
- `GroupSetView(digest, name, size: long, copies: int)`: from `GroupSet`; `digest` is the same lowercase hex string `/sets` answers.

Analysis side (`us.pochaev.dfs.server.analysis`, plain records with `Path`): `FolderGroup`, `GroupFolder`, `GroupSet`, and the final class `FolderGroups` (`of(ScanResult)`, `groups()`).

## State and threads

`GroupState(ScanResult, Function<ScanResult, FolderGroups>)` is created by `ScanRegistry.completedState(id, GroupState.class, r -> new GroupState(r, FolderGroups::of))`, once per scan, kept for the server run. It only holds the result; `index()` builds `FolderGroups` and a `GroupIndex` lazily, at most once, with the same contract as `OverlapState.index()` (first caller builds, concurrent callers wait for the same instance, a failed build is not kept and retries; exceptions unwrapped). `GroupIndex` and `FolderGroups` are immutable. No background thread, no core callback, no stream (checklist 9 is not applicable). The groups and the pair analysis are independent: asking for one never builds the other.

Algorithm (G6), for the implementer: one pass over `result.sets()`; per set, per physical copy, the distinct parent folders of its links; per folder `copiesEach` (copies with a link there) and the link count; the key is the folders sorted by path with their `copiesEach`; one accumulator per key (set count, copies, reclaimable, per-folder shared files and bytes, sets for the listing); then `otherGroups` from one folder to group-count map; sort; sets inside a group by reclaimable descending then digest, first 20 kept.

## Layer and package placement

- `us.pochaev.dfs.server.analysis`: `FolderGroups`, `FolderGroup`, `GroupFolder`, `GroupSet` (new).
- `us.pochaev.dfs.server.result`: `OverlapController` (new method `listGroups`), `GroupState`, `GroupIndex`, `GroupPage`, `FolderGroupView`, `GroupFolderView`, `GroupSetView` (new).
- `us.pochaev.dfs.server.api` (shared): unchanged; `INVALID_MIN_BYTES` is an `ApiException` code string like the others.
- `server/src/main/resources/application.properties`: none.
- `server/frontend/openapi.json`: regenerated in this phase.
- Frontend code changed: none (new operation and schemas only; no existing record changes).
- The controller's private `path(OverlapState, String)` takes an `OverlapState` for `insideRoots`; the implementer generalizes it to take the `Predicate<Path>` (or the `GroupState`) so `listGroups` reuses it; this is a behaviour-preserving refactor, covered by the existing `OverlapApiTest`.

## Spec and client

- `:server:updateOpenApi` run after the stubs. Added: operation `listGroups` under `/api/scans/{id}/groups` (query `under`, `minBytes` default 1048576, `page`, `size`; responses 200, 400, 403, 404, 409), schemas `GroupPage`, `FolderGroupView`, `GroupFolderView`, `GroupSetView`. No other operation or schema changed.
- `ng-openapi-gen`: `OverlapsService.listGroups(params)` returning a Promise of `GroupPage`, plus the four models. Not used by any screen yet (DFS-068 builds the tab on it). `integer` fields come out as `number`.

## Checklist disposition

1. Security on every new path: a GET under `/api`, so the cookie rule applies; foreign `Host` and missing cookie tested on the path (matrix); no state-changing method; no CORS. A refusal builds nothing (the lazy `GroupState.index()` comes after every check).
2. Paths inside a scan: `under` must be absolute (`400 RELATIVE_PATH`) and inside a root (`403 PATH_OUTSIDE_ROOTS`), AC-9.
3. Input validation and error codes: `RELATIVE_PATH`, `PATH_OUTSIDE_ROOTS`, `INVALID_PAGE` (page below 0, size outside 1 to 500), new `INVALID_MIN_BYTES` (`minBytes < 0`, message names `minBytes`), AC-10. Default `minBytes` 1048576 is the controller's `defaultValue`. Core `SCAN_NOT_COMPLETED` and `UNKNOWN_SCAN` pass through `completedState`.
4. Status codes and headers: `200` only on success; `404` for an unknown or malformed ID (ID taken as a String); `409` for an unfinished scan; no `Location` (nothing created).
5. The API records: above; all components required, none nullable, ints/longs for counts, no enum-like values, no times.
6. The spec: tag `overlaps`, operation ID `listGroups`, only `/api/**`, server URL unchanged; regenerated here; spec assertions stay in `OpenApiSpecTest`, not in story tests.
7. The generated client: `OverlapsService.listGroups`; the frontend build ran inside `updateOpenApi` and is green; no existing record changed, so no frontend edit.
8. State kept in `server`: `GroupState` per scan under its own `completedState` key, lazy and thread-safe; the red gate can fail from the endpoint (controller stub) and from `FolderGroups.of` (grouping tests in `server/src/test/.../analysis`), so no extra layer was added for the gate.
9. Streams: not applicable: no stream.
10. Asynchronous states in tests: a running scan for AC-9's `409` is reached as in `OverlapApiTest` (`HoldingExecutorConfiguration`); the first-request-builds-once and refused-builds-nothing checks use a counting build function on `GroupState`; no timing assertion except the scale test's budget (3 s, measured: 0.6 s cold).
11. The dev proxy: a plain GET passes through the proxy unchanged; checked once by hand with `bootRun --args=--server.port=8080` and `npm_run_start` at the done step.
12. Docs: `CLAUDE.md` `server` entry and `docs/design/ui.md` U3 name the endpoint, and `docs/design/folder-groups.md` is set to implemented for G5 and G6, in the implement phase (all in `touches`).

## Expected breakage

None.

## Open design notes (as of the design phase)

- The ticket leaves `totalItems` open: it is the paging total, so it counts the matching groups that reach `minBytes` and equals what `items` pages over; `hiddenSmall` is the rest (AC-6 and AC-7 hold either way).
- `otherGroups` counts every group of the scan that lists the folder, minus one, independent of `under` and `minBytes`: a hidden small group still shares the folder, and the number answers "is this folder's other content shared elsewhere".
- Folder list comparison for order (AC-5): by path then `copiesEach`, a list that is a prefix of another first; two groups differing only in `copiesEach` cannot tie.
- `folderFiles` and `folderBytes` are 0 for a folder with no `ScanResult.folders` entry (cannot happen for a counted file's parent).
- The endpoint sits on `OverlapController` (tag `overlaps`) rather than a new controller, to share its checks; the class comment's "three operations" becomes four.
- The scale test for the groups: same 150,000-file generator as `OverlapAnalysisScaleTest` (copy the generator into the new test, since the old test stays unchanged), budget 3 seconds for `FolderGroups.of`, and check the 500-folder group and about 49,990 groups are present.

## Stubs (as of the design phase)

- Files stubbed: `FolderGroups.of` and `FolderGroups.groups`, `GroupIndex.page`, `GroupState.index` and `GroupState.insideRoots`, `OverlapController.listGroups`. Records (`FolderGroup`, `GroupFolder`, `GroupSet`, `GroupPage`, `FolderGroupView`, `GroupFolderView`, `GroupSetView`) are not stubbed. Constructors of `GroupIndex` and `GroupState` only store their arguments.
- Each stubbed method throws `new UnsupportedOperationException("not implemented: DFS-067")`. Over HTTP the client sees a `500`; `TestServer` rethrows the recorded exception, so the story test fails with the stub's own exception (`StubSurfacingConfiguration`, CCW-019).
- Which stub each criterion reaches: AC-1 to AC-5 and AC-8 `FolderGroups.of` (also through the endpoint); AC-6, AC-7 `GroupIndex.page`; AC-9, AC-10 `OverlapController.listGroups`; the scale test `FolderGroups.of`.
- Commit (stubs and the regenerated spec): filled in after the design commit.
