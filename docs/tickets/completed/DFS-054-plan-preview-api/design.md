# DFS-054: Preview a deletion plan through the server API — design

## Summary

Four endpoints in the new package `us.pochaev.dfs.server.plan`, tag `plans` (`PlansService`): `POST /api/scans/{id}/plans` (`previewPlan`), `GET .../plans/{planId}` (`getPlan`), `GET .../plans/{planId}/operations` (`listPlanOperations`), `GET .../plans/{planId}/conflicts` (`listPlanConflicts`). No stream. Kept state: one `PlanStore` per COMPLETED scan, kept by the existing `ScanRegistry.completedState(id, PlanStore.class, PlanStore::new)`, so **`scan` and `result` are not changed** (path check written again in `plan`, naming the field). New error code `UNKNOWN_PLAN` (404); the rest are existing codes or the core's.

- AC-1: `PlanController.previewPlan` -> `PlanRequests.toCore`, `PlanStore.preview`, `PlanViews.keptPlan`; `PlanSummary(planId, PlanTotalsView, List<LeftBehindView>, operationCount, conflictCount)`, `201` + `Location`.
- AC-2: `PlanController.getPlan` -> `PlanStore.find`; `putIfAbsent` by the core's content-derived ID; a store per scan, so another scan's ID is `UNKNOWN_PLAN`.
- AC-3: `listPlanOperations` -> `KeptPlan.operationPage`; `PlanOperation(digest, size, links, keeper, PlanSource)`, sorted by `PlanViews.keptPlan`.
- AC-4: `listPlanConflicts` -> `KeptPlan.conflictPage`; `PlanConflict(digest, size, List<ConflictCopy>, sources)`, `ConflictCopy(links, quarantinedBy, keptBy)`, joined in `PlanViews.keptPlan`.
- AC-5: both list stubs; `INVALID_PAGE` and `UNKNOWN_PLAN` in the controller, page slicing in `KeptPlan`.
- AC-6, AC-7: the core's plan passed through `PlanStore.preview` / `PlanViews.keptPlan` unchanged in substance (no operation or keeper invented by the server).
- AC-8: `previewPlan` (the core is a pure query; the server writes memory only).
- AC-9: `PlanRequests.toCore` (400 `RELATIVE_PATH` / 403 `PATH_OUTSIDE_ROOTS`, field named, request order) with `PlanStore.insideRoots`.
- AC-10: `PlanStore.preview` returns the core's `Failure` unchanged -> 400 with code and message; an empty request previews to zero totals.
- AC-11: every controller method starts with `completedState` (404 `UNKNOWN_SCAN`, 409 `SCAN_NOT_COMPLETED`) before paths, plan ID or paging.
- AC-12: existing `HostOriginFilter`/Spring Security cover `/api/**`; tests reach the stubs through a plan created via the API first.
- AC-13: `PlanStore` concurrent map + `completedState`'s once-per-scan build.
- AC-14: spec regenerated in this phase (12 new schemas, 4 operations); the AC-14 story test must call an endpoint first (test-guide), `OpenApiSpecTest` stays green.

Look closely at: `ConflictCopy.keptBy` uses **keeper** semantics (the copy a rule keeps against), not "any copy under keep" (Open design notes); path checks run before the core, so a later bad path beats an earlier `INVALID_RULE` (decision 5).
Checklist items not applicable: 9 (streams), 10 (no asynchronous state beyond a COMPLETED scan and a RUNNING one, reached as in DFS-047). Expected breakage: None. Existing tests read: No tests read. Core sources read for the mapping (outside `touches`, read-only): `PreviewDeletionPlan`, `DeletionPlan`, `Operation`, `Conflict`, `Source`, `PlanRequest`, `KeepAndQuarantine`, `KeepCopy`, `LeftBehind`, `PreviewDeletionPlanService` (for the conflict rule; see `learnings.md`).

## Endpoints

| Method | Path | Tag | Operation ID | Request body | Success | Errors (status: code) |
|---|---|---|---|---|---|---|
| `POST` | `/api/scans/{id}/plans` | `plans` | `previewPlan` | `PreviewPlanRequest` | `201 PlanSummary`, `Location /api/scans/{id}/plans/{planId}` | `400: RELATIVE_PATH, INVALID_RULE, UNKNOWN_SET, PATH_NOT_IN_SET, DUPLICATE_PICK; 403: PATH_OUTSIDE_ROOTS; 404: UNKNOWN_SCAN; 409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/plans/{planId}` | `plans` | `getPlan` | none | `200 PlanSummary` | `404: UNKNOWN_SCAN, UNKNOWN_PLAN; 409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/plans/{planId}/operations?page=0&size=50` | `plans` | `listPlanOperations` | none | `200 OperationPage` | `400: INVALID_PAGE; 404: UNKNOWN_SCAN, UNKNOWN_PLAN; 409: SCAN_NOT_COMPLETED` |
| `GET` | `/api/scans/{id}/plans/{planId}/conflicts?page=0&size=50` | `plans` | `listPlanConflicts` | none | `200 ConflictPage` | `400: INVALID_PAGE; 404: UNKNOWN_SCAN, UNKNOWN_PLAN; 409: SCAN_NOT_COMPLETED` |

Check order (AC-11): scan ID and completion first (`completedState`); POST: paths (`PlanRequests.toCore`), then the core; GETs: plan ID, then paging. `page`/`size` defaults 0/50, as `/sets`. The `Location` uses the scan ID as the client sent it (it parsed as a UUID).

## Security matrix

| Path | Cookie | `Host` | `Origin` (state-changing only) | Path inside a scan's roots | Side effect checked absent after a refusal |
|---|---|---|---|---|---|
| `POST /api/scans/{id}/plans` | AC-12 test: no cookie -> 403 | AC-12 test: `rawGet`/raw request with `evil.example` | AC-12 test: `Origin: http://evil.example` -> 403 | AC-9 outline (four fields, `..` included) -> 403 `PATH_OUTSIDE_ROOTS` / 400 `RELATIVE_PATH` | no `Location` on a refusal (AC-9); a plan previewed before the refusals reads back unchanged afterwards (see note); AC-8 checks the disk |
| `GET .../plans/{planId}` | AC-12 test | AC-12 test (raw socket) | not state-changing | not applicable: takes no path | not applicable: read-only |
| `GET .../plans/{planId}/operations` | AC-12 test | AC-12 test (raw socket) | not state-changing | not applicable: takes no path | not applicable: read-only |
| `GET .../plans/{planId}/conflicts` | AC-12 test | AC-12 test (raw socket) | not state-changing | not applicable: takes no path | not applicable: read-only |

Note on "no side effect" for a refused POST: a refused preview keeps nothing and answers no `Location` (AC-9 asserts the missing header). A store that kept refused requests has no ID to look up, so the test proves absence through the API by previewing a valid plan first and reading it back unchanged after the refusals (the reads reach the stubs, keeping the test red), plus AC-8's disk snapshot.

## API records

All `public` in `plan`, `@Schema(name = ...)` equal to the class name (no clash with existing schemas), every component `requiredMode = REQUIRED` unless marked nullable. Paths are `Path.toString()` of the normalized path (backslashes on Windows), as `LinkView`.

- `PreviewPlanRequest(List<PlanRuleRequest> rules, List<String> exceptions, List<PlanPickRequest> picks)`: onto `PlanRequest`; required in the schema (the generated client sends all three), but a `null` list counts as empty on the server.
- `PlanRuleRequest(String keep, String quarantineIn)`: onto `KeepAndQuarantine`.
- `PlanPickRequest(String digest, String keep)`: onto `KeepCopy`; a `null` digest becomes `""` (core: `UNKNOWN_SET`).
- `PlanSummary(String planId, PlanTotalsView totals, List<LeftBehindView> leftBehind, int operationCount, int conflictCount)`: from `DeletionPlan`.
- `PlanTotalsView(long files, long bytes, int sets)`: from `PlanTotals`.
- `LeftBehindView(int ruleIndex, String folder, long files, long bytes)`: from `LeftBehind`.
- `PlanSource(String kind, Integer ruleIndex, String digest)`: from `Source`; `kind` allowable `rule`, `pick`; `ruleIndex` and `digest` nullable (exactly one set, by kind; spec: `["integer","null"]`, `["string","null"]`).
- `PlanOperation(String digest, long size, List<String> links, String keeper, PlanSource source)`: from `Operation.Quarantine`.
- `OperationPage(List<PlanOperation> items, int page, int size, int totalItems)`: order size desc, digest, first link.
- `PlanConflict(String digest, long size, List<ConflictCopy> copies, List<PlanSource> sources)`: from `Conflict` joined with the scan's `DuplicateSet` of that digest (size, copies by first link).
- `ConflictCopy(List<String> links, List<Integer> quarantinedBy, List<Integer> keptBy)`: from `PhysicalCopy` + the request; both lists ascending, restricted to the conflict's rule sources. `quarantinedBy`: every link under the rule's `quarantineIn`, none under its `keep`, none at or under an exception (the core's candidate test). `keptBy`: the rule's keeper is this copy (of the copies with a link under `keep`, the one whose first link sorts first).
- `ConflictPage(List<PlanConflict> items, int page, int size, int totalItems)`: order size desc, digest.

## State and threads

- `PlanStore` (package-private, one per COMPLETED scan): holds the `ScanResult` and a `ConcurrentHashMap<String, KeptPlan>`; created by `ScanRegistry.completedState(id, PlanStore.class, PlanStore::new)` on the first plan request for the scan (constructor does no work), kept for the server run beside the scan's entry, with no limit (decision 2). `completedState` builds it once per scan even under concurrent first requests.
- Written by request threads only: `preview` runs the core on the request thread, maps with `PlanViews.keptPlan` into an immutable `KeptPlan` (lists copied, sorted once), then `putIfAbsent` by plan ID and answers the mapped value (so two previews of one request keep and answer one copy, AC-2; two different requests are both kept, AC-13). A reader sees a plan only once it is fully built. No core callback, no background thread.
- No stream.

## Layer and package placement

- `us.pochaev.dfs.server.plan`: `PlanController` (package-private, `@RestController`), `PlanStore`, `KeptPlan` (record), `PlanViews` (mapping), `PlanRequests` (path checks and conversion), and the twelve public API records above.
- `us.pochaev.dfs.server.scan`: unchanged (the existing public `completedState` suffices).
- `us.pochaev.dfs.server.result`: unchanged (the private path check of `ResultController`/`OverlapController` is not shared; `plan`'s version must name the field, so a shared helper would need a new signature in `result` for one caller).
- `us.pochaev.dfs.server.api` (shared): unchanged (`ApiExceptionHandler` covers the new package).
- `server/src/main/resources/application.properties`: none.
- `server/frontend/openapi.json`: regenerated in this phase (commit below).
- Frontend code changed to keep the build green with the regenerated client: none (only new schemas; `ngBuild` green).

## Spec and client

- `./gradlew :server:updateOpenApi` run after the stubs: 4 operations added under tag `plans` (`previewPlan`, `getPlan`, `listPlanOperations`, `listPlanConflicts`), 12 schemas added (`PreviewPlanRequest`, `PlanRuleRequest`, `PlanPickRequest`, `PlanSummary`, `PlanTotalsView`, `LeftBehindView`, `PlanSource`, `PlanOperation`, `OperationPage`, `PlanConflict`, `ConflictCopy`, `ConflictPage`); nothing existing changed (+592 lines).
- `ng-openapi-gen` generates `src/app/api/services/plans.service.ts` (`PlansService`, verified present after `ngBuild`) with Promise-returning `previewPlan`, `getPlan`, `listPlanOperations`, `listPlanConflicts`, and a model per schema; `PlanSource.ruleIndex`/`digest` come out as optional fields. No screen uses them yet (the plan screen is a later frontend ticket).

## Checklist disposition

1. Security on every new path: all four under `/api/scans/...` (cookie, foreign `Host`, AC-12); the POST relies on the `Origin` check (AC-12); a refused POST keeps nothing and answers no `Location` (AC-9), proven through the API as in the security matrix note; no CORS.
2. Paths inside a scan: every request path (rule `keep`/`quarantineIn`, exceptions, pick `keep`) must be inside a root, else `403 PATH_OUTSIDE_ROOTS` naming the field, also through `..` (normalized first) and for an unparsable path (AC-9).
3. Input validation and error codes: server refuses `RELATIVE_PATH` (400; null, blank or relative) and `PATH_OUTSIDE_ROOTS` (403) in request order before the core; the core's `INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET`, `DUPLICATE_PICK` pass through as 400 with the core's message (AC-10); `INVALID_PAGE` 400; `UNKNOWN_PLAN` 404 (new). Defaults: a missing list is empty, a missing pick digest is `""`, `page`=0, `size`=50. A malformed JSON body is refused by Spring before the controller (not part of AC-11, which the scenarios exercise with a valid body).
4. Status codes and headers: `201` + `Location: /api/scans/{id}/plans/{planId}` on preview (also for a request previewed before: same ID, same copy); `200` reads; scan ID and plan ID taken as `String`, so malformed IDs are `404 UNKNOWN_SCAN` / `404 UNKNOWN_PLAN`.
5. The API records: listed above; no core type in any signature; enum-like `kind` with `allowableValues`; nullable `ruleIndex`/`digest`; everything else required; no times (operations carry paths only, as the ticket asks).
6. The spec: tag `plans`, operation IDs as AC-14 names them, `produces`/`consumes` JSON, only `/api/**`, server URL unchanged; regenerated here; spec assertions go after an endpoint call in the AC-14 story test (test-guide), `OpenApiSpecTest` unchanged.
7. The generated client: `PlansService` with four Promise-returning methods; no existing frontend code uses a changed record (none changed); `src/app/api/` regeneration untouched.
8. State kept in `server`: `PlanStore` per scan via `completedState` (lifetime of the scan, this server run), concurrent map with `putIfAbsent`, immutable `KeptPlan`; nothing a background thread writes. Its unit tests in `server/src/test/java/.../plan` reach `PlanStore`, `KeptPlan`, `PlanViews` and `PlanRequests` stubs from the same package, so they can fail red there.
9. Streams: not applicable: no Server-Sent Events endpoint.
10. Asynchronous states in tests: not applicable beyond the existing means: COMPLETED is reached by polling a small `@TempDir` scan with a deadline, and the RUNNING/QUEUED scan of AC-11 by `HoldingExecutorConfiguration` (QUEUED is "not COMPLETED" for the 409). AC-13's concurrency waits on latches, no `Thread.sleep`.
11. The dev proxy: the POST passes the proxy's `Origin` rewrite like `POST /api/scans`; checked once by hand in the verify phase (`bootRun --args=--server.port=8080`, `npm_run_start`).
12. Docs: `CLAUDE.md`'s `server` entry and `docs/design/ui.md` U6, U7, D4 to name the four endpoints; written by the implementer (both are in `touches`), not in this phase.

## Expected breakage

None.

(`./gradlew :server:test --continue` after the stubs: `BUILD SUCCESSFUL`, no failing class; `ngBuild` green with the regenerated client.)

## Open design notes (as of the design phase)

- Plan store placement (decision 6's open point): a `PlanStore` kind in `ScanRegistry.completedState`, not a field of `ScanRegistry` and not a separate registry keyed by scan ID. It inherits `UNKNOWN_SCAN`/`SCAN_NOT_COMPLETED`, the per-scan lifetime and the once-only build with no change to `scan`.
- `ConflictCopy.keptBy` means "this copy is the rule's keeper", the copy the core keeps against, since that is what makes a conflict (a keeper another rule quarantines). Another copy under the same `keep` is in neither list for that rule. In the ticket's scenarios (one copy per folder) both readings agree.
- Request order vs decision 5: every path is checked before any core rule, so `rules[0]` with `INVALID_RULE` and `picks[0].keep` relative answers `RELATIVE_PATH` for `picks[0].keep`. "First problem in request order" holds within each stage (paths, then the core).
- `leftBehind.folder` is the normalized `quarantineIn` the core received, so it may differ in spelling from what the client sent (a `..` resolved).

## Stubs (as of the design phase)

- Files stubbed (`server/src/main/java/us/pochaev/dfs/server/plan/`): `PlanController` (`previewPlan`, `getPlan`, `listPlanOperations`, `listPlanConflicts`), `PlanStore` (`insideRoots`, `preview`, `find`), `KeptPlan` (`operationPage`, `conflictPage`), `PlanViews` (`keptPlan`), `PlanRequests` (`toCore`). Records not stubbed: `PreviewPlanRequest`, `PlanRuleRequest`, `PlanPickRequest`, `PlanSummary`, `PlanTotalsView`, `LeftBehindView`, `PlanSource`, `PlanOperation`, `OperationPage`, `PlanConflict`, `ConflictCopy`, `ConflictPage`.
- Each method throws `new UnsupportedOperationException("not implemented: DFS-054")`. Over HTTP the client sees a `500`; `TestServer` rethrows the recorded exception, so the story test fails with the stub's own exception (`StubSurfacingConfiguration`, CCW-019).
- Commit (stubs and the regenerated spec): filled in by the orchestrator after the design commit.
