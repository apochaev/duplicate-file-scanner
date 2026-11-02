# DFS-047: Serve a finished scan's folder tree and duplicate sets — design

## Summary

Two GET endpoints in the new feature package `us.pochaev.dfs.server.result`, tag `results` (generated `ResultsService`): `GET /api/scans/{id}/folders[?parent=]` (`listFolders`) and `GET /api/scans/{id}/sets[?under=&page=&size=]` (`listSets`). No stream, nothing state-changing. State: a per-scan `ResultIndex`, built at most once by the new `ScanRegistry.completedState(id, kind, build)` and kept beside the scan's entry for this server run. The error types move to the shared `us.pochaev.dfs.server.api`, and the advice now covers all of `us.pochaev.dfs.server`. `openapi.json` was regenerated: 299 lines added, none removed.

- AC-1 roots and one level: `ResultController.listFolders` → `ResultIndex.roots` / `children` (`FolderNode`)
- AC-2 rolled-up totals, empty subtrees left out: `listFolders` → `ResultIndex.of` (the rollup) and `children`
- AC-3 every set, ordered, totals: `ResultController.listSets` → `ResultIndex.of` (sorted once) and `sets` (`SetPage`)
- AC-4 reclaimable bytes count physical copies: `listSets` → `ResultIndex.of` (`size × (copies − 1)`, one `CopyView` per `PhysicalCopy`)
- AC-5 `under` and paging: `listSets` → `ResultIndex.sets`
- AC-6 `403 PATH_OUTSIDE_ROOTS` / `400 RELATIVE_PATH` / `200` empty: both controller methods → `ResultIndex.insideRoots`, `children`, `sets`
- AC-7 `409` / `404` / `400 INVALID_PAGE`: both controller methods → `ScanRegistry.completedState` (404, 409); `listSets` (page check)
- AC-8 cookie and Host: the refusals reach no stub, so each test also sends the same request with the cookie, which reaches `listFolders` / `listSets`
- AC-9 spec, client, moved types: the spec half passes from this phase on; the story test also calls both endpoints, which reach the stubs (see the open design notes)
- AC-10 docs: checked by reading, recorded in `verification.md`; no stub

Look closely at: the two new 400 codes `RELATIVE_PATH` and `INVALID_PAGE` and the 403 code `PATH_OUTSIDE_ROOTS`; `ScanRegistry` becoming `public` (one public method); an unparsable path answering `403` (open design notes). Checklist items not applicable: 9 (streams), 10 in part (the only asynchronous state is QUEUED), 11 (GET only). Expected breakage: none.

## Endpoints

| Method | Path | Tag | Operation ID | Request body | Success | Errors (status: code) |
|---|---|---|---|---|---|---|
| `GET` | `/api/scans/{id}/folders?parent=<abs path>` | `results` | `listFolders` | none | `200 FolderNode[]` (the roots, or `parent`'s subfolders) | `400: RELATIVE_PATH; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/sets?under=<abs path>&page=0&size=50` | `results` | `listSets` | none | `200 SetPage` | `400: RELATIVE_PATH, INVALID_PAGE; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |

Check order (decision 5): ID, completion, path (relative, then outside), page. `page` defaults to 0, `size` to 50.

## Security matrix

| Path | Cookie | `Host` | `Origin` (state-changing only) | Path inside a scan's roots | Side effect checked absent after a refusal |
|---|---|---|---|---|---|
| `/api/scans/{id}/folders` | AC-8 "without C" (plus a with-C control) | AC-8 "Host evil.example:P" over a raw socket | not state-changing | AC-6: outside T, `<T>/Photos/../..`, relative `Photos` | not applicable: a GET changes nothing; no index is built for a refused request (the security filter runs before the controller) |
| `/api/scans/{id}/sets` | AC-8 "without C" (plus a with-C control) | covered on `/folders` (same filter chain) | not state-changing | AC-6: outside T, relative `Photos` | as above |

## API records

All `public` records in `us.pochaev.dfs.server.result`, every component `requiredMode = REQUIRED`, nothing nullable, no enum-like values.

- `FolderNode(String path, String name, long files, long bytes, long directFiles, long directBytes, boolean hasChildren)`: from `ScanResult.roots` plus `FolderTotal`s rolled up to every ancestor up to its root. `files`/`bytes` are subtree totals, `direct*` the folder's own `FolderTotal` (0 when it has none). `name` is `getFileName()`, or the whole path for a filesystem root (`C:\`). `hasChildren` is true when the folder has a child node.
- `SetPage(List<DuplicateSetView> items, int page, int size, int totalItems, long totalReclaimableBytes)`: `page` and `size` echo the request; the totals cover every matching set.
- `DuplicateSetView(String digest, long size, long reclaimableBytes, List<CopyView> copies)`: from `DuplicateSet`; `reclaimableBytes = size × (copies.size() − 1)`; copies ordered by their first link's path.
- `CopyView(List<LinkView> links)`: from `PhysicalCopy`; links ordered by path.
- `LinkView(String path, Instant modified)`: from `FileEntry` (`path.toString()`, `lastModified`).

Paths: `Path.toString()` of the scan's own paths, so backslashes on Windows (decision 4).

## State and threads

- **What:** one `ResultIndex` per COMPLETED scan: the folder tree as a map from `Path` to node, holding direct and rolled-up totals and the sorted child lists, plus the sets mapped to `DuplicateSetView`s and sorted once (reclaimable desc, size desc, digest asc). It is immutable once built.
- **Where:** `ScanRegistry`'s private `Tracked` record gains a `ConcurrentHashMap<Class<?>, Object>` of per-scan state, keyed by kind, so DFS-048 can add the overlap analysis as a second kind.
- **How:** `completedState(id, kind, build)`:
  - an unknown or malformed ID gives `Failure(UNKNOWN_SCAN)`;
  - a kind already present is returned without calling the core;
  - otherwise it calls `GetScanResult.result`, passes `Failure(SCAN_NOT_COMPLETED)` and its message through (nothing is kept), and builds with `computeIfAbsent`, so the build runs at most once per scan and kind and concurrent callers get the same instance.
- **Threads:** the build runs on the first requesting Tomcat thread. Reads come from any thread. The core never calls back into this state, so nothing has to exist before a core call.
- **Lifetime:** this server run; there is no eviction (out of scope).
- **Ordering:** folder lists by `name` case-insensitively, then by path; sets as above.

Streams: none.

## Layer and package placement

- `us.pochaev.dfs.server.result`:
  - `ResultController` (package-private, stub);
  - `ResultIndex` (package-private, stub);
  - the records `FolderNode`, `SetPage`, `DuplicateSetView`, `CopyView` and `LinkView` (public).
- `us.pochaev.dfs.server.api` (shared): `ApiError` (public record, unchanged), `ApiException` (now `public`; public constructor and `unknownScan`) and `ApiExceptionHandler` (`@RestControllerAdvice(basePackages = "us.pochaev.dfs.server")`), all moved with `git mv` from `scan`.
- `us.pochaev.dfs.server.scan`: `ScanController` imports the error types from `api`. `ScanRegistry` becomes `public` with one new public method, `completedState` (stub); its other members stay package-private.
- `server/src/main/resources/application.properties`: none.
- `server/frontend/openapi.json`: regenerated in this phase (commit below).
- Frontend code changed to keep the build green with the regenerated client: none. Only additions; `ApiError`, which `scans/scan-form.ts` uses, is unchanged.

## Spec and client

- `./gradlew :server:updateOpenApi` was run after the stubs. It added the paths `/api/scans/{id}/folders` (`listFolders`) and `/api/scans/{id}/sets` (`listSets`), both tagged `results`, with 200/400/403/404/409 responses and the documented query parameters (`page` default 0, `size` default 50, both int32), and the schemas `FolderNode`, `SetPage`, `DuplicateSetView`, `CopyView` and `LinkView`, every property required. Nothing was removed or changed.
- `ng-openapi-gen` generates a `ResultsService` with Promise-returning `listFolders({id, parent?})` and `listSets({id, under?, page?, size?})`, plus the five model interfaces. There is no stream operation.

## Checklist disposition

1. **Security on every new path:** both paths are under `/api`. AC-8 tests the missing cookie on both and a foreign `Host` on `/folders`. Each refusal test pairs with the same request carrying the cookie as its positive control. GET only, so there is no `Origin` check. No CORS. Nothing a refusal could leave behind.
2. **Paths inside a scan:** `parent` and `under` are parsed with `Path.of(s).normalize()` and must be absolute (`400 RELATIVE_PATH`). They must lie inside a root by `Path.startsWith` (`403 PATH_OUTSIDE_ROOTS`, AC-6, including `..`). A path inside a root that the scan holds nothing for answers `200` with an empty result.
3. **Input validation and error codes:**
   - refused before the core's result is read: `400 RELATIVE_PATH` (a blank value counts as relative), `403 PATH_OUTSIDE_ROOTS` and `400 INVALID_PAGE` (`page < 0`, or `size` outside 1–500);
   - passed through from the core: `SCAN_NOT_COMPLETED` (`409`, with the core's message);
   - `UNKNOWN_SCAN` (`404`) for an unknown or malformed ID;
   - defaults: `page = 0` and `size = 50`, from the ticket.
4. **Status codes and headers:** `200` only; nothing is created, so there is no `Location`. The ID is taken as a `String`, so a malformed one answers `404`. A not-COMPLETED scan answers `409` on both endpoints.
5. **The API records:** see "API records". All components required, none nullable, times as `Instant`. No field changes meaning over time: the endpoints answer only for a COMPLETED scan.
6. **The spec:** tag `results` and operation IDs `listFolders`/`listSets`. Only `/api/**` (`springdoc.paths-to-match`, unchanged), server URL unchanged. Regenerated in this phase. The spec assertions for AC-9 belong in the story test only next to an endpoint call (open design notes); `OpenApiSpecTest` already compares the whole spec.
7. **The generated client:** a `ResultsService` with two Promise methods, no stream method, and no existing frontend code affected. `src/app/api/` regeneration is untouched.
8. **State kept in `server`:** see "State and threads". The per-scan index lives in `ScanRegistry` for this server run, is built thread-safely at most once, and needs nothing created before a core call.
9. **Streams:** not applicable: no stream endpoint.
10. **Asynchronous states in tests:** a COMPLETED scan comes from a poll with a deadline (`ScanApiTest.awaitTerminal`'s pattern), a QUEUED one from `HoldingExecutorConfiguration`. There are no timing assertions.
11. **The dev proxy:** not applicable: GET only and no stream, so the proxy needs nothing beyond what `/api/scans` already proves (archetype extra done-item applies only to state-changing methods and streams).
12. **Docs:** AC-10. The `CLAUDE.md` `server` entry lists both endpoints and the path-inside-roots `403`; `docs/design/ui.md` U3 and U10 name them. The implementer writes them.

## Expected breakage

None.

## Open design notes (as of the design phase)

- **Advice scope:** the advice uses `basePackages = "us.pochaev.dfs.server"`, the root package, rather than listing `scan` and `result` (decision 7 says "covering both packages"), so DFS-048 and later feature packages need no edit. Only the API controllers throw `ApiException`.
- **An unparsable path** (`Path.of` throws `InvalidPathException`, for example `C:/a<b` on Windows) answers `403 PATH_OUTSIDE_ROOTS`. Such a string cannot name a path inside a root, and this adds no code beyond decision 5.
- **A non-integer `page` or `size`** (`?page=x`) gets Spring's own `400` without an `ApiError` body. No criterion covers it, and taking them as `String` would make the spec show strings.
- **Roots are resolved real paths** (`ScanResult.roots`). Tests should use `T.toRealPath()` for `<T>`, both in queries and in expected paths; on macOS the temp folder is a symlink.
- **AC-8 and AC-9 cannot fail red by their refusal or spec assertions alone:** the security filter and the regenerated spec both work before any implementation. Each test therefore also makes a request that reaches a stub:
  - AC-8: the same request with C and the right `Host`, answering `200`;
  - AC-9: `listFolders` and `listSets` on a COMPLETED scan, checking that each response carries every required property of its schema in the committed `openapi.json`, which checks the spec against the real JSON.

  Whether the moved types are in `api` and `ScanApiTest` is unchanged is checked by `git diff`, recorded in `verification.md`. Flagged in `learnings.md`.

## Stubs (as of the design phase)

- Files stubbed: `server/src/main/java/us/pochaev/dfs/server/result/ResultController.java` (`listFolders`, `listSets`), `.../result/ResultIndex.java` (`of`, `insideRoots`, `roots`, `children`, `sets`), `.../scan/ScanRegistry.java` (`completedState`).
- Each method throws `new UnsupportedOperationException("not implemented: DFS-047")`. Over HTTP the client sees a `500`; `TestServer` rethrows the recorded exception, so the story test fails with the stub's own exception (`StubSurfacingConfiguration`, CCW-019).
- Commit (stubs and the regenerated spec): not yet made; the orchestrator commits the design phase and records the hash.
