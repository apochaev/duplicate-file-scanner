# DFS-054 verification

## Product owner review

### What you can do now

For a finished scan, the server now takes your rules ("keep A, quarantine in B"), exceptions and single-set picks and answers the plan they would produce: how many files and bytes would be moved, which copy each moved file keeps, and where your rules clash. Nothing on disk is touched. The plan is kept under an ID for this server run, so you can read it back and page through its operations and conflicts. There is no screen for it yet; the plan screen is a separate frontend ticket.

### Acceptance criteria

- **AC-1** met: a preview answers 201, a Location, and a summary whose plan ID equals the core's; totals, left-behind files and counts are right.
- **AC-2** met: every plan stays readable, the same request gives the same ID, and another scan's plan ID answers 404 `UNKNOWN_PLAN`.
- **AC-3** met: operations come largest first, each with its links, keeper and source (rule index or pick).
- **AC-4** met: conflicts come largest first, with each copy's links and the rules that would quarantine it or keep it (keep means "this is the rule's keeper", as approved at the design gate).
- **AC-5** met: both lists page like `/sets`; bad paging is 400 `INVALID_PAGE`, a page past the end is empty.
- **AC-6** met: crossing rules become conflicts, no operation moves them, and five different plans each leave every set a surviving copy.
- **AC-7** partially met: a hard-linked copy moves as one operation with all its links, and its keeper is a different copy. But the criterion's own example (link in B, quarantine A) cannot happen: the core moves a copy only when all its links are in the quarantine folder. So the test puts both links in A. The ticket text should be fixed; the behaviour is the safe one.
- **AC-8** met: a disk snapshot before and after previews, refused ones included, is identical.
- **AC-9** met: relative or out-of-scan paths are refused (400 or 403, also through `..`), the field is named, and no Location is sent.
- **AC-10** met: the core's four refusals pass through with its exact code and message, the first problem wins, and an empty request is an empty plan.
- **AC-11** met: an unfinished scan answers 409 and an unknown or malformed scan ID answers 404 on all four endpoints, before anything else is checked.
- **AC-12** met: no cookie, a foreign Host, or a missing or foreign Origin on the POST all answer 403.
- **AC-13** met: two different previews sent at the same moment are both kept, with different IDs.
- **AC-14** met: the committed spec has the four operations under `plans` and their 12 schemas, `OpenApiSpecTest` passes, and `PlansService` is generated.

### Try it

```
./gradlew :server:bootRun --args=--server.port=8080
```
Open the `Open http://127.0.0.1:8080/?token=...` link once (or `curl -c jar.txt "<that link>"`). Start a scan of folders that hold duplicates (`POST /api/scans`), wait until `GET /api/scans/{id}` shows `COMPLETED`, then:
```
curl -i -b jar.txt -H "Origin: http://127.0.0.1:8080" -H "Content-Type: application/json" \
  -d '{"rules":[{"keep":"C:/x/A","quarantineIn":"C:/x/B"}],"exceptions":[],"picks":[]}' \
  http://127.0.0.1:8080/api/scans/<id>/plans
curl -b jar.txt "http://127.0.0.1:8080/api/scans/<id>/plans/<planId>/operations?page=0&size=1"
curl -b jar.txt "http://127.0.0.1:8080/api/scans/<id>/plans/<planId>/conflicts"
```
Look for `201` with a `Location`, a summary with `totals`, `leftBehind`, `operationCount` and `conflictCount`, and operations largest first. Adding the reverse rule (`keep B, quarantine in A`) gives `conflictCount` above 0 and no operation for those sets. On Windows, the paths in answers come back with backslashes.

### Decided without you

- The plan store lives inside the existing per-scan state of `ScanRegistry`, so the `scan` and `result` packages were not changed (design gate saw this).
- `keptBy` on a conflict copy means "this copy is the rule's keeper", not "this copy is under the rule's keep folder" (approved at the design gate).
- All request paths are checked before the core's own rules, so a bad path later in the request beats an earlier invalid rule; "first problem in request order" holds within each stage.
- A missing list in the request counts as empty and a missing pick digest as `""` (the core then answers `UNKNOWN_SET`).
- `leftBehind.folder` is the normalized path (a `..` resolved, backslashes on Windows), so it can be spelled differently from what you sent.
- A path that cannot be parsed at all is answered 403 `PATH_OUTSIDE_ROOTS`, not 400.
- The path-check code was written again in the `plan` package instead of shared with `result`, because the plan version names the field.
- The AC-7 test links two files inside A instead of the scenario's A-and-B link (the scenario contradicts the core).

### Known limits

- No plan screen yet (follow-up frontend ticket).
- Plans are kept with no limit until the server stops (decision 2); they cannot be deleted.
- No drift check, content verify or execution of a plan (later M-3 and M-5 tickets).
- AC-7's wording and its scenario need correcting in the ticket (see above); no code change is needed.
- Crossing rules turn a whole set into a conflict even when a third copy elsewhere would survive (core behaviour, DFS-022, unchanged here; seen in the by-hand run for the song in A, B and C).
- The design's checklist item 11 (the POST through the `ng serve` dev proxy, checked by hand) was not run; the POST uses the same `Origin` check as `POST /api/scans`, which already passes the proxy.

### Evidence

- `server/src/test/java/us/pochaev/dfs/server/PlanApiTest.java`: 26 tests, 0 skipped, 0 failures (JUnit XML `server/build/test-results/test/TEST-us.pochaev.dfs.server.PlanApiTest.xml`; the AC-7 hard-link test ran, not skipped). Server test suite: 171 tests, 0 skipped, 0 failed.
- `./gradlew build` green (ArchUnit, `OpenApiSpecTest`, Vitest, `ngBuild`), re-run by the verifier.
- Deterministic gate (`story-gate`): passed before this phase (green, freeze, touches, stubs, traceability).
- By-hand run on Windows (verifier, built `dfs-server.jar`, port 18454): scan of A/B/C with the ticket's background files; `keep A, quarantine B` -> 201, 2 files, 7340032 bytes, 2 sets, leftBehind 1 file 1024 bytes, 2 operations; crossing rules -> 201, 0 operations, 2 conflicts with `quarantinedBy`/`keptBy` as expected; relative path -> 400 `RELATIVE_PATH` naming `rules[0].keep`; `..` outside -> 403 `PATH_OUTSIDE_ROOTS` naming `rules[0].quarantineIn`; operations and conflicts paged with size 1, page 5 empty with the same total, size 501 -> 400, unknown plan -> 404. Files unchanged afterwards. Log: no warning or error from any plan request; the one `WARN` came from the verifier's own malformed JSON sent to `POST /api/scans` before the scan.
- `server/frontend/src/app/api/services/plans.service.ts` present after the build.

### How it went

- design (architect, opus): 1 attempt, passed; design gate approved.
- tests (test-author, sonnet): 1 attempt, 26 story tests red on the stubs.
- implement (implementer, sonnet): 1 attempt, green (847 passed, 3 skipped across the build), all gate checks passed.
- verify: 1 attempt. No defects routed back, no retries or escalations. The one real issue found (AC-7's scenario contradicting the core) was caught in the test phase and is a ticket-text fix.

Ready for the user's approval, with one decision: approve rewording AC-7 and its scenario (both links of the moved copy inside the quarantine folder), or reject.

## Summary

- DFS-054.AC-1 met: 201, Location, summary with the core's plan ID, totals, leftBehind and counts.
- DFS-054.AC-2 met: plans kept per scan; same request same ID; other scan's ID 404 `UNKNOWN_PLAN`.
- DFS-054.AC-3 met: operations largest first with links, keeper, source.
- DFS-054.AC-4 met: conflicts largest first with copies, `quarantinedBy`/`keptBy` (keeper semantics, gate-approved), sources.
- DFS-054.AC-5 met: paging, `INVALID_PAGE`, past-end page, `UNKNOWN_PLAN` on both lists.
- DFS-054.AC-6 met: crossing rules are conflicts; invariant checked over five plans.
- DFS-054.AC-7 partially met: links move together and the keeper is another copy, but the criterion's example (link in B) is impossible under the core's all-links-under rule; the test uses two links in A. Ticket wording fix needed, no code change.
- DFS-054.AC-8 met: disk snapshot unchanged after answered and refused previews.
- DFS-054.AC-9 met: four-row outline, field named, no Location.
- DFS-054.AC-10 met: core codes and exact messages, first problem, empty plan.
- DFS-054.AC-11 met: 409 and 404 checked first on all four endpoints.
- DFS-054.AC-12 met: cookie, Host and Origin guards.
- DFS-054.AC-13 met: concurrent previews both kept, latches only.
- DFS-054.AC-14 met: spec operations and schemas, `OpenApiSpecTest`, `PlansService`.

Ready for the human gate; the only open point is the AC-7 ticket wording (a refinement edit, not a phase rerun).

## Criteria

See the product owner review for verdicts. Notes per criterion:

- AC-1: `ruleCreatesPlan` compares `planId` with the core bean's `DeletionPlan.id` on the same scan, and every summary field with fixed expected values.
- AC-2: three tests; the cross-scan test uses a second COMPLETED scan in the same server.
- AC-3: asserts order, both song operations' pick source and keeper, and the photo operation's exact links, keeper and rule index.
- AC-4: asserts photo's conflict exactly; `PlanViews.conflict` computes `quarantinedBy` with the core's candidate test (`allUnder quarantineIn`, not `anyUnder keep`, no exception) and `keptBy` by the core's keeper choice (the copy under `keep` with the smallest first link). I compared it with `PreviewDeletionPlanService.planRules` line by line: same predicates, same keeper choice.
- AC-5, AC-6, AC-8 to AC-14: as summarized.
- AC-7: see the summary. The core's `allUnder` rule means a copy with a link outside the quarantine folder is never moved, which is the safe reading; the scenario text is what is wrong.

## Test quality

- The tests assert on HTTP behaviour and values derived independently: fixed sizes and paths from the background, the core bean's own plan ID and failure message, a disk snapshot, and an invariant check that joins operations against `/sets`. None restates the implementation.
- AC-6's invariant test covers five request shapes (single rule, two rules, pick only, rules from C), not only the scenario.
- AC-12 proves a refused POST kept nothing via the core-computed ID answering 404, which is a sound absence check.
- AC-11 uses a QUEUED scan (holding executor) for "not COMPLETED", per the design; acceptable since the code path is the same `completedState` failure.
- AC-4 does not pin whether song (in A, B and C) is also a conflict under crossing rules; the by-hand run shows it is (core behaviour). Not a gap for this ticket.
- No `Thread.sleep` in `PlanApiTest`.

## Design conformance

- Every endpoint, record, status and code in `design.md` matches the controller and records. Check order (scan, then paths, then core; scan, then plan ID, then paging) matches.
- Checklist dispositions 1-10 and 12 hold in the diff. Item 11 (dev proxy by hand in verify) was not run by the verifier; noted under Known limits.
- `scan` and `result` packages unchanged, as designed. No core type in any API record.
- Minor: `PlanRequests.toCore` dereferences `rule.quarantineIn()` / `pick.digest()` after a `null` element check that throws first, so a `null` list element is safely `RELATIVE_PATH`; fine.

## Safety invariants

- Never delete the last copy: the server passes the core's operations and conflicts through without inventing or dropping any; AC-6 tests prove it over the API.
- Re-verify at execution time: not applicable (no execution here).
- Preview is a pure query: `PlanStore` writes only memory; AC-8 snapshot and the by-hand run confirm.
- Hard links are not duplicates: operations carry every link of a physical copy; AC-7 test (real hard link, ran on Windows) confirms.
- No symlink/junction following, empty files excluded: not touched by this ticket (scan behaviour unchanged).
- One plan execution at a time: not applicable.

## Definition of done

- Every scenario is an automated test and passes: left unticked. Every scenario except AC-7 is automated as written; the AC-7 test differs from its scenario (both links in A), because the scenario as written cannot produce an operation. Tick it once the user approves rewording AC-7.
- `@DisplayName` starts with the criterion ID: ticked (all 26 XML names).
- `./gradlew build` green: ticked.
- Checklist items addressed in `design.md`: ticked.
- `CLAUDE.md` and `ui.md` U6, U7, D4 name the endpoints: ticked.
- No `Thread.sleep`: ticked.
- By-hand Windows run: ticked (see Evidence; the one log `WARN` was the verifier's own malformed scan request, not plan traffic).
- No files outside Touches: ticked (26 changed files, all inside `touches` or the story folder).
