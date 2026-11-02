# DFS-048: Serve folder summaries and overlap pairs for a finished scan — design

## Summary

Three read-only GETs in DFS-047's `us.pochaev.dfs.server.result` package, in a new `OverlapController` (tag `overlaps`, generated `OverlapsService`): `listSummaries` `/api/scans/{id}/summaries?under=&page=&size=`, `listPairs` `/api/scans/{id}/pairs?under=&page=&size=`, `getPair` `/api/scans/{id}/pair?a=&b=`. No stream; one new error code, `400 NESTED_FOLDERS`, and `400 MISSING_FOLDER`.

State: per scan, `ScanRegistry.completedState(id, OverlapState.class, ...)` keeps an `OverlapState` (built instantly, holds the `ScanResult`), whose `index()` runs `OverlapAnalysis.of` lazily, at most once, concurrent callers waiting on the same computation. `ScanRegistry` is unchanged.

Criteria, and the stub each reaches:
- AC-1 `listSummaries` → `OverlapIndex.summaries` (`SummaryPage`, `folder` null without `under`).
- AC-2 `listPairs` → `OverlapIndex.pairs` (`PairPage`).
- AC-3 `getPair` → `OverlapIndex.pair` (`FolderPairView`, `a` before `b`).
- AC-4 `getPair` (refusals in the controller; `NESTED_FOLDERS` from `OverlapIndex.pair`).
- AC-5 `listSummaries` / `listPairs` (path checks via `OverlapState.insideRoots`, paging).
- AC-6 all three controller stubs (404/409 from `completedState`, before any parameter).
- AC-7 `listPairs`, `getPair` (hard links are DFS-046's; the API only maps).
- AC-8 `OverlapState.index()`, unit-tested directly in `result` with a counting, blocking function.
- AC-9 refusals pass on stubs; each test also sends the same request with the cookie and right `Host` (reaches a controller stub).
- AC-10 spec regenerated now; checked by `OpenApiSpecTest` and the generated `overlaps.service.ts`, not a story test.
- AC-11 read, recorded in `verification.md`.

Look closely at: the `OverlapState` lazy holder inside `completedState` (a second memo, added so AC-8 is unit-testable and red; see open notes), and `SummaryPage.folder` generating as `folder?: FolderSummaryView` in TypeScript while the server sends `null`.
Not applicable: checklist 9 (streams), 10 beyond the queued scan (no timing), 11 (GETs only). Expected breakage: none.

## Endpoints

| Method | Path | Tag | Operation ID | Request body | Success | Errors (status: code) |
|---|---|---|---|---|---|---|
| `GET` | `/api/scans/{id}/summaries?under=&page=0&size=50` | `overlaps` | `listSummaries` | none | `200 SummaryPage` | `400: RELATIVE_PATH, INVALID_PAGE; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/pairs?under=&page=0&size=50` | `overlaps` | `listPairs` | none | `200 PairPage` | `400: RELATIVE_PATH, INVALID_PAGE; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/pair?a=&b=` | `overlaps` | `getPair` | none | `200 FolderPairView` | `400: MISSING_FOLDER, RELATIVE_PATH, NESTED_FOLDERS; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |

Check order (decision 4, as `ResultController`): ID (`404`, malformed included) → completion (`409`) → `a`/`b` present (`pair` only) → each path: blank or not absolute `RELATIVE_PATH`, unparsable or outside every root (after `normalize()`, so `..` too) `PATH_OUTSIDE_ROOTS` → paging (`page < 0`, `size` outside 1–500) or nesting. Only then `OverlapState.index()`, so a refusal never starts the analysis. `NESTED_FOLDERS` messages: "both folders are <a>" / "<outer> contains <inner>".

## Security matrix

| Path | Cookie | `Host` | `Origin` (state-changing only) | Path inside a scan's roots | Side effect checked absent after a refusal |
|---|---|---|---|---|---|
| `/api/scans/{id}/summaries` | AC-9 test, without C | covered on `/pairs` | not state-changing | AC-5 test (`under` outside, `..`, relative) | not applicable: read-only; the test then sends the same request with C and gets `200` |
| `/api/scans/{id}/pairs` | AC-9 test, without C | AC-9 test, `Host evil.example:P` over a raw socket | not state-changing | AC-5 test | same |
| `/api/scans/{id}/pair` | AC-9 test, without C | covered on `/pairs` | not state-changing | AC-4 test (`a` outside, `..`) | same |

## API records

All public, in `result`, mapped one to one from DFS-046's records (decision 6); paths as `Path.toString()`. Every component `requiredMode = REQUIRED` except `SummaryPage.folder`.

- `FolderSummaryView(String folder, long totalFiles, long totalBytes, long filesWithCopyElsewhere, long bytesWithCopyElsewhere, long internalDuplicateFiles, long internalBytes)`: from `analysis.FolderSummary`.
- `SummaryPage(FolderSummaryView folder, List<FolderSummaryView> items, int page, int size, int totalItems)`: `folder` nullable, `under`'s own `summary(under)` (zeros for a folder the scan holds nothing for), JSON `null` without `under`.
- `PairSideView(String folder, long filesWithCopy, long totalFiles, long bytesWithCopy, long totalBytes, boolean full)`: from `analysis.PairSide`, `full` = `PairSide.full()`.
- `FolderPairView(PairSideView a, PairSideView b)`: from `analysis.FolderPair`.
- `PairPage(List<FolderPairView> items, int page, int size, int totalItems)`.

`totalItems` counts every matching item; a page past the end is empty with the same total. Orders are DFS-046's, documented on the `items` schemas.

## State and threads

- `OverlapState` (package-private, `result`): created by `ScanRegistry.completedState(id, OverlapState.class, r -> new OverlapState(r, OverlapAnalysis::of))`, so one instance per COMPLETED scan, kept for the server run (no eviction, decision 5). Holds the `ScanResult` and the analysis function.
- `OverlapState.index()`: the first caller runs the function on its request thread; concurrent callers wait for that computation and get the same `OverlapIndex`; later callers read it. A throwing function caches nothing, the next call retries, the builder and every waiter get the exception unwrapped (same contract as `completedState`). A `CompletableFuture` in an `AtomicReference` (compare-and-set) fits.
- `OverlapState.insideRoots(Path)`: from `ScanResult.roots()`, no analysis needed.
- `OverlapIndex` (package-private): immutable wrapper of one `OverlapAnalysis`; filters with `Path.startsWith`, pages, maps to the records. `pair` refuses equal or nested folders itself (`ApiException 400 NESTED_FOLDERS`) before calling DFS-046's `pair`, so its `IllegalArgumentException` is never reached.
- No stream, no listener, nothing a core thread writes.

## Layer and package placement

- `us.pochaev.dfs.server.result`: `OverlapController`, `OverlapState`, `OverlapIndex`, and the five records. `result` depends on `analysis` (allowed; DFS-046.AC-11 only limits `analysis`'s own dependencies) and on the scanner domain only.
- `us.pochaev.dfs.server.scan`: unchanged (`completedState` already supports a second kind).
- `us.pochaev.dfs.server.api` (shared): unchanged; the advice already covers the root package.
- `server/src/main/resources/application.properties`: none.
- `server/frontend/openapi.json`: regenerated in this phase (commit below).
- Frontend code changed to keep the build green with the regenerated client: none (`ngBuild` passes).

The path check and page check duplicate `ResultController`'s private helpers; the implementer may extract them into a package-private helper used by both controllers, without changing `ResultController`'s behavior (both in `touches`).

## Spec and client

- `./gradlew :server:updateOpenApi` run after the stubs; added: operations `listSummaries`, `listPairs`, `getPair` (tag `overlaps`), schemas `SummaryPage`, `FolderSummaryView`, `PairPage`, `FolderPairView`, `PairSideView`. Nothing existing changed.
- `ng-openapi-gen` generates `OverlapsService` with Promise-returning `listSummaries`, `listPairs`, `getPair` (and `$Response` variants), and models `summary-page.ts` etc. `SummaryPage.folder` is spec'd as OpenAPI 3.1 `"type": "null"` beside the `$ref`, which generates `folder?: FolderSummaryView` (no `| null`); DFS-050 should treat `null` and absent alike.

## Checklist disposition

1. Security on every new path: all under `/api` (cookie); no state-changing method, so no `Origin` test; foreign `Host` tested on `/pairs`; every security test also sends the allowed request so it reaches a stub (see Security matrix). No CORS.
2. Paths inside a scan: `under`, `a` and `b` must be absolute and inside a root after `normalize()`, else `400 RELATIVE_PATH` / `403 PATH_OUTSIDE_ROOTS` (AC-4, AC-5); a folder inside a root holding nothing answers `200` with zeros or no items.
3. Input validation and error codes: `MISSING_FOLDER`, `RELATIVE_PATH`, `PATH_OUTSIDE_ROOTS`, `NESTED_FOLDERS`, `INVALID_PAGE`, all before the analysis is asked; no core `Result.Failure` passes through except `completedState`'s `UNKNOWN_SCAN` (`404`) and `SCAN_NOT_COMPLETED` (`409`, core message). Defaults `page=0`, `size=50`, as DFS-047. A non-integer `page`/`size` gets Spring's own `400`, as in DFS-047 (no criterion).
4. Status codes and headers: `200` only; no created resource; the ID is a `String` parsed by the registry, so a malformed one is `404 UNKNOWN_SCAN` (AC-6, also `not-a-uuid/pair?a=relative`).
5. The API records: above; required marks on every component, `SummaryPage.folder` nullable; no enums; no times.
6. The spec: tag `overlaps`, operation IDs `listSummaries`/`listPairs`/`getPair`; regenerated in this phase; spec checks stay in `OpenApiSpecTest` (AC-10), never in a story test.
7. The generated client: `OverlapsService` with Promise methods; no existing frontend code uses these records; no stream operation; `src/app/api/` regeneration untouched.
8. State kept in `server`: `OverlapState` per scan via `completedState`, server run lifetime, thread-safe lazy memo (AC-8); nothing written by a background thread.
9. Streams: not applicable: no stream.
10. Asynchronous states in tests: the `409` case uses a queued scan from `HoldingExecutorConfiguration`; completed scans by bounded polls on `GET /api/scans/{id}`; AC-8 uses latches with timeouts, no timing assertions.
11. The dev proxy: not applicable: three GETs without a stream; the proxy forwards `/api` GETs already (ticket, out of scope).
12. Docs: AC-11, `CLAUDE.md`'s `server` entry and `docs/design/ui.md` U3 and U5b, done by the implementer.

## Expected breakage

None.

## Open design notes (as of the design phase)

- **`OverlapState`, a lazy holder inside `completedState`.** `completedState` already memoizes per scan and kind, so `completedState(id, OverlapIndex.class, ...)` alone would satisfy decision 5, but a test of it could neither go red (it is implemented) nor be written in `result` (`ScanRegistry`'s constructor and `start` are package-private to `scan`). The ticket puts the AC-8 test in `result`, so the memo the test exercises is `OverlapState.index()`, constructed directly: `new OverlapState(new ScanResult(List.of(root), List.of(), List.of(), List.of(), 0), countingFunction)`, the function returning `OverlapAnalysis.of(r)`; assert `assertSame` on the `OverlapIndex`es. Flagged in `learnings.md`.
- **AC-8 test against the stub:** `index()` throws at once and the function is never called, so every latch wait must have a timeout, and on timeout the test should first `get()` the worker futures, so the stub's `UnsupportedOperationException` is the root cause rather than a timeout assertion. "Both requests waiting": the first worker signals from inside the function; the second's waiting is seen by a bounded poll of its `Thread.getState()` (`WAITING`/`BLOCKED`/`TIMED_WAITING`), not a sleep.
- **`folder` absent means JSON `null`** (Jackson default inclusion); tests should accept `null` or a missing property.
- **Roots are resolved real paths** (`ScanResult.roots`), so tests use `T.toRealPath()` for `<T>`, as in DFS-047.
- The AC-1 scenario's expected `T` summary and the order of `Backup`/`Photos` follow DFS-046's tie-breaks (bytes with copy elsewhere, then internal bytes, then path); the API passes the order through unchanged.

## Stubs (as of the design phase)

- Files stubbed: `OverlapController` (`listSummaries`, `listPairs`, `getPair`), `OverlapState` (`index`, `insideRoots`), `OverlapIndex` (`summaries`, `pairs`, `pair`; its constructor only stores the analysis). Records written in full.
- Each method throws `new UnsupportedOperationException("not implemented: DFS-048")`. Over HTTP the client sees a `500`; `TestServer` rethrows the recorded exception, so the story test fails with the stub's own exception (`StubSurfacingConfiguration`, CCW-019).
- Commit (stubs and the regenerated spec): filled in after the design commit.
