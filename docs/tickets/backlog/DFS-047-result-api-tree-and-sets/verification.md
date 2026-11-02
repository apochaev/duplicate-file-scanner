# DFS-047 verification

## Product owner review

### What you can do now

The local server can now show a finished scan's results over its API. `GET /api/scans/{id}/folders` walks the scanned folders one level at a time, with file and byte totals per folder. `GET /api/scans/{id}/sets` lists the duplicate sets with the largest reclaimable space first, paged, and can be limited to one folder. There is no screen for this yet; the result screen (DFS-049) will use these endpoints.

### Acceptance criteria

- **AC-1** met: the roots, then one level down, with subtree and direct totals and `hasChildren`, ordered by name ignoring case.
- **AC-2** met: a folder holding only subfolders shows its whole subtree's totals with 0 direct files, and a folder with no counted files (`Empty`) is left out.
- **AC-3** met: sets come largest reclaimable first, then largest size, with every copy, link path and modified time, and totals for every matching set. The final tie-break by digest is in the code but has no test.
- **AC-4** met: hard links count as one physical copy, both for reclaimable bytes and for the grouping in one `CopyView`. The test ran on Windows and was not skipped.
- **AC-5** met: `under` keeps sets that have a copy in that folder and still shows the copies elsewhere. Paging works, and a page past the end is empty but keeps the totals.
- **AC-6** met: a path outside the roots, including one written with `..`, answers `403 PATH_OUTSIDE_ROOTS`. A relative path answers `400 RELATIVE_PATH`. An empty or missing folder inside a root answers `200` with nothing in it.
- **AC-7** met: a queued scan answers `409 SCAN_NOT_COMPLETED`, an unknown or malformed ID `404 UNKNOWN_SCAN`, and bad paging `400 INVALID_PAGE`.
- **AC-8** met: both endpoints refuse a request without the session cookie, and a foreign `Host` is refused on `/folders`. Each refusal is paired with a successful request that has the cookie.
- **AC-9** met: the spec has `listFolders` and `listSets` (tag `results`) and the five schemas, and `ApiError` keeps its name. `OpenApiSpecTest` passes, the generated `results.service.ts` exists, the error types are now in `us.pochaev.dfs.server.api`, and `ScanApiTest` is unchanged and passes.
- **AC-10** met: CLAUDE.md's `server` entry names both endpoints and the `403` rule, and ui.md U3 and U10 name them. I read both, and a test also checks them.

### Try it

```
./gradlew :server:bootRun          # open the printed ?token= link once (or curl it with -c jar.txt)
# start a scan from the page, or: curl -b jar.txt -H "Origin: http://127.0.0.1:<port>" -H "Content-Type: application/json" --data-binary @body.json http://127.0.0.1:<port>/api/scans
curl -b jar.txt "http://127.0.0.1:<port>/api/scans/<id>/folders"
curl -b jar.txt "http://127.0.0.1:<port>/api/scans/<id>/folders?parent=C:/your/scanned/folder"
curl -b jar.txt "http://127.0.0.1:<port>/api/scans/<id>/sets?size=10"
curl -b jar.txt "http://127.0.0.1:<port>/api/scans/<id>/folders?parent=C:/Windows"   # 403 PATH_OUTSIDE_ROOTS
```

What to look for: the folder list has `files`/`bytes` (whole subtree) and `directFiles`/`directBytes`, and the sets come largest `reclaimableBytes` first. Paths come back with backslashes on Windows. Put a JSON body containing Windows paths in a file, or use forward slashes, because backslashes typed in a shell break the JSON.

### Decided without you

These choices were in design.md, which you approved at the design gate, but the ticket did not settle them:

- The error handler covers every controller under `us.pochaev.dfs.server`, not only the `scan` and `result` packages, so later feature packages need no change there.
- A path that cannot be parsed (for example `C:/a<b`) answers `403 PATH_OUTSIDE_ROOTS`. A blank one answers `400 RELATIVE_PATH`.
- A non-integer `page` or `size` (`?page=x`) gets Spring's default `400` body, not an `ApiError`.
- `ScanRegistry` is now `public`, with one public method, `completedState`.
- Folders with the same name (ignoring case) are then ordered by path. Copies in a set are ordered by their first link's path, compared case-sensitively as strings.
- A scanned root with no counted files is still listed among the roots, with zero totals and `hasChildren` false.
- AC-10 was planned as "checked by reading", but it also has a test that checks the doc wording, added to pass the traceability gate. That test fails if someone rewords those doc lines.

### Known limits

- No test covers the digest tie-break (AC-3, last level), because digests of generated content cannot be chosen. It is checked by reading only.
- No test covers `ScanRegistry.completedState`'s build-once and retry-after-failure behaviour, because no criterion asks for it. I read it and it matches the design-gate feedback (see Design and invariants below).
- The foreign-`Host` refusal is tested on `/folders` only. `/sets` uses the same filter chain, as the ticket's scenario specified.
- There is no eviction: a scan's index stays for the whole server run, as the ticket put out of scope.
- I ran the hand check against the built `bootJar`, not `bootRun`. It is the same application. The dev proxy does not apply, because both endpoints are GET only and there is no stream.

### Evidence

- I re-ran `./gradlew build`: BUILD SUCCESSFUL. `ResultApiTest` 26 tests, 0 failures, 0 skipped. `ScanApiTest` 27/0/0. `OpenApiSpecTest` 3/0/0. The story gate on 756e042: green (600 passed, 3 skipped), and disabled, freeze, touches, stubs, traceability and consistency all PASS. The only commit after 756e042 changes `plan.json`.
- `git diff f4641c4 HEAD`: every changed file is inside `touches` or the story folder. Under `server/src/test`, only `ResultApiTest.java` was added. `ApiError`, `ApiException` and `ApiExceptionHandler` exist only under `server/.../api`.
- Hand run on Windows (2026-11-02): the server jar was started with the browser opener off, and `file-scanner/src/test/resources/fixtures` was scanned (23 files, COMPLETED). Results:
  - roots: 1 node, 23 files, 329 bytes.
  - one level down: `basic`, `folders`, `names`, `nested`, `same-size`.
  - two levels down (`folders`): `folder1`, `folder2`.
  - a lower-case, forward-slash `parent` matched (Windows path rules).
  - a page of sets: 2 sets, totalReclaimableBytes 45.
  - `under=…\folders&size=1`: 1 set.
  - refused as expected: `parent=C:/Windows` 403 `PATH_OUTSIDE_ROOTS`; `…\basic\..\..` 403; `under=basic` 400 `RELATIVE_PATH`; `size=501` 400 `INVALID_PAGE`; no cookie 403; `nope` 404 `UNKNOWN_SCAN`; `C:/a<b` 403.
  - server log: no warnings or errors from the result endpoints. The only WARN came from my own first `POST /api/scans`, whose JSON shell quoting had broken, and it was not a result-API call.

### How it went

- Design: 2 runs. Run 1 passed, and the design gate came back with feedback: build per-scan state as a memoized future per kind, not with `computeIfAbsent`. The rework run passed and the gate approved it.
- Tests: 2 attempts. Attempt 1 (sonnet) failed traceability because AC-10 had no test (defect 1, sent back to the test-author, closed). Attempt 2 was escalated to opus and passed, with all 26 story tests red against the stubs.
- Implement: 1 attempt (sonnet), green on the first run.
- Verify: this run. No defects found.

Ready for the user's approval.

## Summary

- DFS-047.AC-1 met: roots and one level down with subtree, direct and `hasChildren`, ordered by name ignoring case. Exact values asserted.
- DFS-047.AC-2 met: roll-up through a folder that holds only subfolders, and `Empty` left out.
- DFS-047.AC-3 met: ordering, copies, links, mtime and totals tested. The digest tie-break is implemented but untested (cannot be made deterministic).
- DFS-047.AC-4 met: hard links form one `CopyView`, and reclaimable bytes count physical copies. Ran on Windows, not skipped.
- DFS-047.AC-5 met: `under` keeps the copies outside it, paging works, and a page past the end keeps the totals.
- DFS-047.AC-6 met: 403 for outside and `..`, 400 for relative, 200 empty inside a root.
- DFS-047.AC-7 met: 409 for queued on both endpoints, 404 for unknown and malformed IDs, 400 for page -1, size 0 and size 501.
- DFS-047.AC-8 met: no cookie on both endpoints and a foreign Host on `/folders` give 403, each with a positive control.
- DFS-047.AC-9 met: spec operations, tag, schemas and `ApiError` name, `OpenApiSpecTest` green, `ResultsService` generated, error types moved, `ScanApiTest` unchanged.
- DFS-047.AC-10 met: read in CLAUDE.md and ui.md U3 and U10, and also tested.
- Tests assert behaviour and exact output over HTTP against a real server, not the implementation. Gaps: the digest tie-break and `completedState`'s memoization (no criterion), both checked by reading.
- Invariants: GET only, nothing on disk is touched, hard links are not counted as copies, and paths are confined to the roots after `normalize()`. Deletion, symlink and empty-file rules do not apply.
- Design: matches design.md, including every checklist disposition and the memoized future per kind.
- Ready for the human gate (`done`).

## Design and invariants

- **`ScanRegistry.completedState`** (design-gate feedback, judged by reading):
  - One `ConcurrentHashMap<Class<?>, CompletableFuture<?>>` per `Tracked` scan.
  - A mapped future is awaited without calling the core.
  - Otherwise `GetScanResult` runs first, so a not-COMPLETED scan maps nothing, then `putIfAbsent`.
  - A loser awaits the winner's future. The winner runs `build` outside any map operation, so no bin lock is held and asking for another kind cannot cause a recursive update.
  - On failure: `completeExceptionally` and `remove(kind, mine)`, then a rethrow. Waiters get the cause, unwrapped from `CompletionException`. A failed build is never cached.
  - Matches the contract in design.md "State and threads". Re-checking completion is not needed once a future is mapped, because COMPLETED is terminal.
- **`ResultIndex`**:
  - Totals roll up only through ancestors inside a root.
  - Children are linked only for nodes with `files > 0`.
  - `HashMap<Path, …>` lookup uses `WindowsPath` equality, which is case-insensitive. Confirmed by the lower-case hand request.
  - The paging offset is computed as a `long`.
  - Built immutable (`Map.copyOf`, `List.copyOf`). The node child lists are never changed after the build and are published through the future.
- **Check order** in `ResultController`: ID, completion (`index()`), path (relative, then outside), then page. Matches decision 5.
- **Safety invariants:**
  - Preview stays a pure query and execution a separate command: both endpoints only read the in-memory result.
  - Hard links are not duplicates: `reclaimableBytes = size × (physical copies − 1)` and one `CopyView` per `PhysicalCopy` (AC-4).
  - Symlinks and junctions are not followed: the endpoints never touch the disk, and `parent`/`under` are only compared with `Path.startsWith` after `normalize()`.
  - Never delete the last copy, re-verify at execution time, and exclude empty files do not apply (no deletion, no walking).
