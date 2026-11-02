# DFS-048 verification

## Product owner review

### What you can do now

The local server now answers, for a finished scan, which folders have the most reclaimable space (folder summaries), which folder pairs share copies (overlap pairs), and how any two folders you pick overlap. These answers feed the Overlaps tab (DFS-050). There is no screen for them yet; you can call them with `curl`.

### Acceptance criteria

- **DFS-048.AC-1** met: summaries come in the analysis's ranked order. With `under`, you get only that folder's descendants, plus that folder's own summary.
- **DFS-048.AC-2** met: pairs come in ranked order. With `under`, you get every pair with a side inside the folder, and the other side can be anywhere.
- **DFS-048.AC-3** met: the pair of any two folders is counted recursively, and the earlier path always comes first. A folder with nothing in it shows zeros and is not "full".
- **DFS-048.AC-4** met: a refused pair query answers `MISSING_FOLDER`, `RELATIVE_PATH`, `PATH_OUTSIDE_ROOTS` (also through `..`) or `NESTED_FOLDERS`, and the message names the case.
- **DFS-048.AC-5** met: `under` follows the same path rules as the sets API, and paging works the same way (`INVALID_PAGE`; a page past the end is empty).
- **DFS-048.AC-6** met: an unfinished scan answers `409`, and an unknown or malformed scan ID answers `404`, before any parameter is checked.
- **DFS-048.AC-7** met: a hard link is never counted as a copy. The test ran with real NTFS hard links and was not skipped.
- **DFS-048.AC-8** met: the analysis runs at most once per scan, even when two requests arrive at the same time.
- **DFS-048.AC-9** met: all three endpoints answer `403` without the session cookie, and with a foreign `Host` (tested on `/pairs`).
- **DFS-048.AC-10** met: the spec has the three operations and five schemas, and the generated client has an `OverlapsService`.
- **DFS-048.AC-11** met: I read `CLAUDE.md` and `docs/design/ui.md` myself (details below). A test checks the same text.

### Try it

1. `./gradlew :server:bootRun --args=--dfs.server.open-browser=false`, then log in: `curl -c jar.txt "http://127.0.0.1:<port>/?token=<token>"`.
2. Start a scan: `curl -b jar.txt -H "Origin: http://127.0.0.1:<port>" -H "Content-Type: application/json" -X POST http://127.0.0.1:<port>/api/scans -d "{\"locations\":[\"C:/some/folder\"],\"minSize\":1,\"defaultExcludes\":true}"`. Wait until `GET /api/scans/<id>` shows `COMPLETED`.
3. Try these calls, each with `-b jar.txt` and URL-encoded paths:
   - `GET /api/scans/<id>/summaries`: the folders with the most reclaimable space come first.
   - `GET /api/scans/<id>/summaries?under=<folder>`: `folder` holds that folder's own totals.
   - `GET /api/scans/<id>/pairs?under=<folder>`
   - `GET /api/scans/<id>/pair?a=<x>&b=<y>`
   - `pair?a=<x>&b=<x>\sub`: answers `400 NESTED_FOLDERS`, "x contains x\sub".

### Decided without you

- **A second memo for AC-8.** The architect added a small `OverlapState` holder, kept per scan by `ScanRegistry.completedState`, that runs the analysis lazily inside its own `index()`. `completedState` already caches per scan, so this is a second layer. It exists so the AC-8 test could be written in the `result` package and fail before the code was written. It does not change behavior.
- **`folder` can come back as `null`.** The generated TypeScript client types `SummaryPage.folder` as optional (`folder?: FolderSummaryView`), not `| null`, but the server sends JSON `null` without `under`. DFS-050 should treat `null` and a missing field the same way.
- **Duplicated helpers.** The path and paging checks are copied into `OverlapController` rather than shared with `ResultController`. The design allowed either, and `ResultController` is untouched.
- **Malformed paths.** A path the platform cannot parse answers `403 PATH_OUTSIDE_ROOTS`, the same as in DFS-047.
- **Error messages.** `MISSING_FOLDER` says "Both a and b are required". `NESTED_FOLDERS` says "both folders are <a>" or "<outer> contains <inner>".
- **Case on Windows.** Equal and nested folders are compared with the platform's rules, so on Windows `Photos` and `PHOTOS` count as the same folder (`NESTED_FOLDERS`). I confirmed this by hand.

### Known limits

- There is no UI yet (DFS-050).
- The analysis runs on the first request and is not computed when the scan finishes. It is never evicted.
- There is no `depth` parameter, and no "both sides under the folder" filter for pairs.
- The foreign-`Host` refusal is tested on `/pairs` only. The other two endpoints share the same filter.

### Evidence

- The deterministic gate passed on 1739600: green (657 passed, 3 skipped), disabled, freeze, touches (19 files), stubs, traceability (11 criteria), consistency. The working tree matches 1739600 for `server/`, `CLAUDE.md` and `docs/design/`.
- `server/build/test-results/test/TEST-us.pochaev.dfs.server.OverlapApiTest.xml`: 36 tests, 0 skipped, 0 failures (AC-1 to AC-7, AC-9 to AC-11).
- `TEST-us.pochaev.dfs.server.result.OverlapStateTest.xml`: 1 test, passed (AC-8).
- By-hand run on Windows 10 (verifier, `bootRun`). I scanned a scratch tree laid out like the ticket's example, together with the worktree's `docs/` as a real folder.
  - The numbers matched the scenarios: Backup 2 files and 500 KB with copies; Photos/2019 3 files and 600 KB.
  - Pairs under `Old` gave two pairs, each keeping its other side.
  - `pair` of Photos and Backup put Backup first. A pair across the two roots answered `200` with zeros with a copy.
  - Refusals: `NESTED_FOLDERS` (nested, and a case-variant of the same folder), `MISSING_FOLDER`, `PATH_OUTSIDE_ROOTS` for `C:\Windows`, and `403` without the cookie.
  - The server log had no WARN, ERROR or exception lines. I stopped the server afterwards and deleted the scratch files.

### How it went

- Design: 1 attempt, passed.
- Tests: 2 attempts. Attempt 1 failed the traceability gate because AC-10 and AC-11 had no named story test (the ticket said "read" / "covered by OpenApiSpecTest"). Attempt 2 added them and passed.
- Implement: 1 attempt, passed every gate.
- Verify: no defects found. Nothing was escalated.

Ready for the user's approval.

## Summary

- DFS-048.AC-1 met: ranked summaries; with `under`, strict descendants plus the folder's own summary (zeros when empty).
- DFS-048.AC-2 met: ranked pairs; with `under`, pairs with either side inside, X included.
- DFS-048.AC-3 met: recursive pair, `a` before `b` regardless of argument order; zero side not full.
- DFS-048.AC-4 met: MISSING_FOLDER, RELATIVE_PATH, PATH_OUTSIDE_ROOTS (incl. `..`), NESTED_FOLDERS with the case named; checks run before the analysis.
- DFS-048.AC-5 met: DFS-047 path rules and paging (`INVALID_PAGE`, empty page past the end, same total).
- DFS-048.AC-6 met: 404/409 from `completedState` before any parameter, malformed ID included.
- DFS-048.AC-7 met: tested with real hard links (not skipped); analysis is DFS-046's, API maps only.
- DFS-048.AC-8 met: CAS'd `CompletableFuture` memo with retry on failure. The test uses latches and a bounded thread-state poll; it asserts the same instance and a call count of 1 for concurrent and later callers.
- DFS-048.AC-9 met: no cookie on all three, foreign Host on `/pairs`, each with a positive control.
- DFS-048.AC-10 met: operations, tag, five schemas, required properties checked on real responses, `OverlapsService` generated.
- DFS-048.AC-11 met: read by the verifier and test-checked.

Test quality is good. The tests assert concrete numbers, order and error codes from real HTTP responses, and every security test has a positive control.

Invariants:
- Preview is a pure query: all three endpoints are read-only GETs.
- Hard links are not duplicates: holds through the API (AC-7).
- Nothing in this ticket deletes files, follows links or changes empty-file handling.

The diff matches design.md, including the checklist dispositions.

Ready for the human gate (done).
