# DFS-047 progress

## design (architect)

- Moved `ApiError`, `ApiException` and `ApiExceptionHandler` from `server/.../scan` to `server/.../api` with `git mv` (staged as renames). `ApiException` and its constructor and `unknownScan` are now public; the advice covers `basePackages = "us.pochaev.dfs.server"`. `ScanController` imports them from `api`.
- `ScanRegistry` is now `public`, with the new stub `completedState(String, Class<T>, Function<ScanResult, ? extends T>)`.
- New package `server/.../result`: the records `FolderNode`, `SetPage`, `DuplicateSetView`, `CopyView` and `LinkView`, and the stubs `ResultController` (`listFolders`, `listSets`) and `ResultIndex` (`of`, `insideRoots`, `roots`, `children`, `sets`).
- Ran `./gradlew :server:updateOpenApi`: `server/frontend/openapi.json` gained 299 lines, none removed.
- `:server:compileJava` passes. `:server:npmTest` passes, and so does `:server:test` filtered to `OpenApiSpecTest`, `ScanApiTest`, `ArchitectureTest` and `SecurityTest`. The generated client has `results.service.ts`.
- Wrote `design.md`; friction is in `learnings.md`.
- Expected commit: `feat(server): design result API stubs and move error types to api [DFS-047]` (stubs, the moved error types, the regenerated spec, design.md, progress.md, learnings.md).

## design: feedback from the design gate (user, 2026-11-01)

For the next design attempt. In `ScanRegistry.completedState`, do not build per-scan state inside `ConcurrentHashMap.computeIfAbsent`. Store a memoized future per kind instead: create it once with `putIfAbsent`, then compute it outside the map's lock, so concurrent callers still share one build. Reason: DFS-048 will put the overlap analysis (up to ~10 s on a large scan, DFS-046 AC-10) in the same map. `computeIfAbsent` would hold the bin lock for the whole build, and a build that asks `completedState` for another kind would throw "Recursive update". A failed build must not be cached; the next request retries it. Keep everything else in the design as is.

## design rework (architect, after the design gate)

- `ScanRegistry.completedState`: signature unchanged, body still throws `not implemented: DFS-047`; the Javadoc now states the memoized-future contract (one `CompletableFuture` per kind, `putIfAbsent`, build outside the map's lock on the first caller's thread, concurrent callers share it, a failed build removed with `remove(kind, future)` and its exception rethrown unwrapped to every waiter) and an `@throws`.
- `design.md`: the Summary, "State and threads" (Where, How, Threads), checklist item 8, and a new open design note. No criterion covers the memoization, so `ResultApiTest` does not test it; the note says how a direct `ScanRegistry` test could show the retry and the shared build if one is ever wanted.
- `:server:compileJava` passes. `openapi.json` unchanged (Javadoc only, no rerun of `updateOpenApi` needed).
- Expected commit: amend-free follow-up, e.g. `docs(server): memoize per-scan state per kind in the DFS-047 design [DFS-047]` (ScanRegistry.java, design.md, progress.md).

## tests (test-author)

- Wrote `server/src/test/java/us/pochaev/dfs/server/ResultApiTest.java` (25 test invocations covering AC-1 to AC-9; AC-10 is by reading) and `test-plan.md`.
- `./gradlew :server:test --tests "*ResultApiTest"`: all 25 fail with `UnsupportedOperationException: not implemented: DFS-047`, none pass, none skip.
- Expected commit: `test(server): add result API story tests [DFS-047]` (ResultApiTest.java, test-plan.md, progress.md, learnings.md).

## tests, attempt 2 (test-author)

- The gate failed traceability: no test for AC-10. Added `DFS-047.AC-10 CLAUDE.md's server entry and ui.md U3 and U10 name the two result endpoints` to `ResultApiTest`: calls both endpoints on a completed scan, then checks that CLAUDE.md's `server` entry contains `GET /api/scans/{id}/folders`, `GET /api/scans/{id}/sets` and, after them, `403` and `roots`, and that ui.md's U3 and U10 sections contain both paths. The implementer must write the docs in that form for the test to pass.
- `./gradlew :server:test --tests "*ResultApiTest"`: all 26 fail with `UnsupportedOperationException: not implemented: DFS-047`. test-plan.md updated.
- Expected commit unchanged: `test(server): add result API story tests [DFS-047]`.

## implement (implementer)

- Implemented `ResultIndex` (tree with rolled-up totals, sets sorted once), `ResultController` (check order: ID, completion, path, page) and `ScanRegistry.completedState` (memoized future per kind via `putIfAbsent`, build outside the map, failed build removed, exception unwrapped). Wrote the docs for AC-10 in CLAUDE.md's `server` entry and ui.md U3 and U10.
- `./gradlew build` green; no stub markers left; no test file touched; no deviation from design.md.
- Expected commit: `feat(server): serve a completed scan's folder tree and duplicate sets [DFS-047]`.
