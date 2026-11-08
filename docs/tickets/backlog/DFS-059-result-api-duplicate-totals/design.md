# DFS-059: Duplicate totals for folders and sets in the result API — design

## Summary

**Blocker for the gate:** the regenerated client makes `ng build` (run by `:server:test`) fail. `server/frontend/src/app/results/result-test-data.ts` (the Vitest builders `folder()` and `setPage()`) builds `FolderNode` and `SetPage` literals without the four new required fields (TS2322). The fix is four values (`duplicateFiles: 0, duplicateBytes: 0`, `totalFiles: 0, totalBytes: 0`), but the file is not in `touches`, so it has not been made. Add it to `touches` before the test phase.

Changes two existing operations of the `results` tag. No new path, error, security rule, state or stream:
- **DFS-059.AC-1**: `FolderNode` gains `duplicateFiles` and `duplicateBytes` (int64, required). They are computed once per scan, while the index is built, by the stub `ResultIndex.addDuplicateTotals(nodes, roots, sets)` (called from `ResultIndex.of`), and held in `Node`.
- **DFS-059.AC-2**: same stub. Only sets with at least two physical copies (`copies().size() >= 2`) count, each link path once, rolled up like `files`. So `duplicateFiles <= files` and `duplicateBytes <= bytes` hold.
- **DFS-059.AC-3**: `SetPage` gains `totalFiles` and `totalBytes` (int64, required). The stub is `ResultIndex.sets(under, page, size)`: its whole body is stubbed, and matching and paging are re-implemented as in DFS-047.
- **DFS-059.AC-4**: the controller is unchanged. Any request on a COMPLETED scan builds the index first, so it reaches `addDuplicateTotals`. The `409` step (RUNNING scan) builds no index and passes against the stubs. Keep the scenario as one test, with the `RELATIVE_PATH` step after the `409` step.
- Package: `us.pochaev.dfs.server.result` only (`FolderNode`, `SetPage`, `ResultIndex`). `openapi.json` is regenerated: 4 fields, all required int64.
- Not applicable: 1 (no new path, no state-changing method), 9 (no stream), 10 (no async state beyond the existing RUNNING-scan helper), 11 (no change to the proxy). Items 2 to 4 have no change.
- Expected breakage: none. `ResultApiTest` (22 failures) and `PlanApiTest` (23 failures) fail only with `not implemented: DFS-059`. The run had to exclude `ngBuild` because of the blocker above. That made 8 tests in `FrontendTest`, `SecurityTest`, `ServerApplicationTest` and `BrowserOpenerTest` fail on a missing `static/index.html`. That is a side effect of the excluded build, not of the stubs, and they pass once `result-test-data.ts` is fixed.
- No tests read. I grepped the frontend for uses of `FolderNode` and `SetPage` (checklist item 7) and read `result-test-data.ts` to confirm the compile error.

## Endpoints

| Method | Path | Tag | Operation ID | Request body | Success | Errors (status: code) |
|---|---|---|---|---|---|---|
| `GET` | `/api/scans/{id}/folders[?parent=]` | `results` | `listFolders` | none | `200 FolderNode[]` (two new fields) | unchanged: `400: RELATIVE_PATH`; `403: PATH_OUTSIDE_ROOTS`; `404: UNKNOWN_SCAN`; `409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/sets[?under=&page=&size=]` | `results` | `listSets` | none | `200 SetPage` (two new fields) | unchanged: `400: RELATIVE_PATH, INVALID_PAGE`; `403: PATH_OUTSIDE_ROOTS`; `404: UNKNOWN_SCAN`; `409: SCAN_NOT_COMPLETED` |

## Security matrix

No new path. Both paths are covered by DFS-047's existing tests (`ResultApiTest` AC-8: cookie, foreign `Host`). Both are GETs, so the `Origin` check does not apply. The scan-roots check is DFS-047 AC-6, unchanged.

| Path | Cookie | `Host` | `Origin` (state-changing only) | Path inside a scan's roots | Side effect checked absent after a refusal |
|---|---|---|---|---|---|
| `/api/scans/{id}/folders` | existing DFS-047.AC-8 | existing DFS-047.AC-8 | not state-changing | existing DFS-047.AC-6 | not applicable: read-only |
| `/api/scans/{id}/sets` | existing DFS-047.AC-8 | covered on `/folders` | not state-changing | existing DFS-047.AC-6 | not applicable: read-only |

## API records

- `FolderNode(path, name, files, bytes, duplicateFiles, duplicateBytes, directFiles, directBytes, hasChildren)`: from `ScanResult.roots`, `FolderTotal`s rolled up, and now also `DuplicateSet.copies()`/`files()`. Required: all. Nullable: none. The new `long` fields sit after `bytes`, since they are subtree totals like it. They count every link path of a set with at least two physical copies, in the link's folder and every ancestor up to its scan root. A set of hard links to one physical file contributes 0.
- `SetPage(items, page, size, totalItems, totalFiles, totalBytes, totalReclaimableBytes)`: from `DuplicateSet`s. Required: all. Nullable: none. `totalFiles` is the sum of `files().size()` (link paths) over the matching sets. `totalBytes` is the sum of `size * copies().size()` over the same sets. The sets match on the same predicate as `totalItems` (all sets, or the sets with a link under `under`). `totalBytes - totalReclaimableBytes` is the space the last copies keep. Documented on the record.

## State and threads

Unchanged mechanism. The duplicate totals are two more `long` fields on `ResultIndex.Node`. They are filled once inside `ResultIndex.of`, before the index is published by `ScanRegistry.completedState`, and never written afterwards. So the index stays immutable and any request thread may read it. No stream.

## Layer and package placement

- `us.pochaev.dfs.server.result`: `FolderNode` (2 components), `SetPage` (2 components), `ResultIndex` (`Node` fields, `addDuplicateTotals` stub, `sets` stub). `ResultController` is unchanged.
- `us.pochaev.dfs.server.api` (shared): unchanged
- `server/src/main/resources/application.properties`: none
- `server/frontend/openapi.json`: regenerated in this phase (commit below)
- Frontend code changed to keep the build green with the regenerated client: none yet. **Needed**: `server/frontend/src/app/results/result-test-data.ts` (`folder()` and `setPage()` default the four new fields to 0). This file is outside `touches` (see Summary).

## Spec and client

- I ran `./gradlew :server:updateOpenApi` after the stubs. Schemas changed: `FolderNode` gains `duplicateFiles` and `duplicateBytes`, `SetPage` gains `totalFiles` and `totalBytes`. All four are `integer`/`int64` and listed in `required`. No operation changed.
- `ng-openapi-gen`: `ResultsService.listFolders`/`listSets` are unchanged. The `FolderNode` and `SetPage` models gain four required `number` properties. No production frontend code builds these objects (the only literals are in `result-test-data.ts`), so only that builder needs the fields.

## Checklist disposition

1. Security on every new path: not applicable: no new path, and both existing ones are GETs already covered by DFS-047.AC-8.
2. Paths inside a scan: unchanged. `parent` and `under` keep DFS-047's `RELATIVE_PATH`/`PATH_OUTSIDE_ROOTS` checks. AC-4 re-asserts `RELATIVE_PATH` on `/sets`.
3. Input validation and error codes: no new input or code. AC-4 re-asserts `409 SCAN_NOT_COMPLETED` on both endpoints and `400 RELATIVE_PATH`.
4. Status codes and headers: unchanged (`200`, refusals as DFS-047).
5. The API records: two `long` components on each record, all `requiredMode = REQUIRED`, none nullable, meanings documented on the records. Hard-link semantics: only sets with at least two physical copies count, links counted as `files` counts them (AC-2).
6. The spec: regenerated in this phase. Tag and operation IDs are unchanged. Spec assertions stay out of the story tests (`OpenApiSpecTest` covers drift; DFS-047.AC-9's existing required-properties test now also covers the new fields once implemented).
7. The generated client: models gain required fields. `result-test-data.ts` must add them or `ng build` fails (Summary). The generated `api/` folder is untouched by this ticket.
8. State kept in `server`: the existing per-scan `ResultIndex` via `ScanRegistry.completedState`, extended. The totals are computed once in `of()`, immutable afterwards. The criteria fail red from `ResultApiTest` through `addDuplicateTotals`.
9. Streams: not applicable: no stream.
10. Asynchronous states in tests: the AC-4 RUNNING scan uses whatever `ResultApiTest` already uses for DFS-047's `409` test. No timing assertions. The hard-link scenarios create links with `Files.createLink` and skip through `Assumptions` when the platform refuses.
11. The dev proxy: not applicable: no new path or state-changing method. Two GETs whose bodies grew.
12. Docs: the implementer adds the four fields to `CLAUDE.md`'s result API sentence (the `server` entry) and to `docs/design/ui.md`'s result API description.

## Expected breakage

None.

## Open design notes (as of the design phase)

- `touches` must gain `server/frontend/src/app/results/result-test-data.ts`. Without it, `ngBuild` fails and no `:server:test` run, red or green, can start.
- Field order: the new `FolderNode` fields follow `bytes`, and the new `SetPage` fields follow `totalItems`. JSON order is not part of the contract.
- Test-author: the AC-1/AC-2 scenarios use `parent=<tmp>` on a scan whose root is `<tmp>`. The AC-2 hard-link files (`E/a.bin`, `E/b.bin`, `B/song-link.mp3`) change the Background numbers when present, so give each AC-2 scenario its own `@TempDir` scan, or create the links only in the AC-2 tests.

## Stubs (as of the design phase)

- Files stubbed: `server/src/main/java/us/pochaev/dfs/server/result/ResultIndex.java` (`addDuplicateTotals`, new; `sets`, body replaced). `FolderNode.java` and `SetPage.java` are records, not stubbed. `Node.view()` passes the two new fields (plumbing only).
- Each method throws `new UnsupportedOperationException("not implemented: DFS-059")`. Over HTTP the client sees a `500`; `TestServer` rethrows the recorded exception, so the story test fails with the stub's own exception (`StubSurfacingConfiguration`, CCW-019).
- Commit (stubs and the regenerated spec): filled in after the design commit.
