# DFS-067: verification

## Product owner review

### What you can do now

The server can now answer "which folders share which duplicate files" in one list: `GET /api/scans/{id}/groups` groups a finished scan's duplicate sets by the exact folders that directly hold their copies, heaviest first, so a finding such as five RAW files in a folder and a bundle subfolder shows once instead of on every in-between folder. There is no screen for it yet; DFS-068 builds the Overlaps tab on it.

### Acceptance criteria

- DFS-067.AC-1: met. Sets with the same folders and the same copies per folder merge into one group; a different copy count gives a different group.
- DFS-067.AC-2: met. Only the folders that directly hold a copy are listed, once each, sorted by path; in-between folders never appear.
- DFS-067.AC-3: met. Every group and folder figure (`files`, `copies`, `reclaimableBytes`, `copiesEach`, `sharedFiles`, `sharedBytes`, `folderFiles`, `folderBytes`, `otherGroups`) is computed as the ticket defines and checked against exact numbers.
- DFS-067.AC-4: met. Hard links add no copy; a copy linked into several folders appears in each (checked in memory and with a real hard link on disk).
- DFS-067.AC-5: met. Heaviest first, then first folder path, then the folder lists.
- DFS-067.AC-6: met. `under` keeps groups with a folder at or under it; `totalItems` and `hiddenSmall` count only those.
- DFS-067.AC-7: met. Groups below `minBytes` (default 1 MiB) are hidden and counted; `minBytes=0` shows all, boundary included.
- DFS-067.AC-8: met. A group lists its 20 largest sets and `totalSets` counts all.
- DFS-067.AC-9: met. Same refusals as `/summaries` (409, 404, 400 `RELATIVE_PATH`, 403 `PATH_OUTSIDE_ROOTS`, `INVALID_PAGE`, cookie and Host checks), in the same order.
- DFS-067.AC-10: met. `minBytes=-1` answers 400 `INVALID_MIN_BYTES` ("minBytes must be 0 or more"); `0` is accepted.

### Try it

1. `./gradlew :server:bootRun`, open the `Open http://127.0.0.1:<port>/?token=...` link it logs, start a scan of a folder with some duplicates and wait for it to complete.
2. In the same browser (it holds the session cookie), open `http://127.0.0.1:<port>/api/scans/<id>/groups?minBytes=0` (the scan ID is in `/api/scans`).
3. Look for `items[]`, each with `folders[]` (only the folders that directly hold copies), `copies`, `reclaimableBytes` and `sets[]`; and `hiddenSmall`, which rises when you drop `minBytes=0` (groups under 1 MiB are hidden then).
4. `...&minBytes=-1` answers `{"code":"INVALID_MIN_BYTES", ...}`.

Or run the tests: `./gradlew :server:test --tests "*GroupApiTest" --tests "*FolderGroups*"`.

### Decided without you

- `totalItems` counts only the matching groups at or above `minBytes` (the paging total); `hiddenSmall` counts the rest (design).
- `otherGroups` counts across every group of the scan, whatever `under` and `minBytes` ask for, so a hidden small group still counts as sharing the folder (design).
- Two groups with the same folders but different `copiesEach` are ordered by `copiesEach` after path; a folder list that is a prefix of another comes first (design).
- Sets inside a group with equal reclaimable bytes are ordered by digest (design).
- The endpoint sits on the existing `OverlapController` (tag `overlaps`, so the client method is `OverlapsService.listGroups`) to share its checks (design).
- The scale budget is 3 seconds for building the groups on the 150,000-file case (architect, measured 0.6 s cold).
- `folderFiles`/`folderBytes` answer 0 for a folder with no folder total (cannot happen for a counted file's parent) (design).

### Known limits

- No screen: the Overlaps tab is DFS-068.
- The "built once per scan", "a failed build is retried" and "a refused request builds nothing" contracts have no unit test: the test for them (`GroupStateTest`) would sit in the `result` test package, outside the ticket's `touches`, so it was dropped. I checked the code by hand: `GroupState.index()` is a line-for-line copy of `OverlapState.index()`, whose contract DFS-048 tests, and the controller calls it only after every refusal check. Over HTTP, only "concurrent and repeated requests give the same answer" is tested.
- Scenario sizes over HTTP are scaled down (KB and a few MiB instead of 41 or 206 MB); the in-memory tests use the scenario's own sizes.
- Checklist item 11 (a GET through the `ng serve` dev proxy) is a by-hand check the design puts at the done step; I did not run it.
- Mirrored trees merged into one row (G7) and a plan action per group are later work, as the ticket says.

### Evidence

- `GroupApiTest`: 38 tests, 0 failures, 0 skipped (the real hard-link test ran, not skipped). `FolderGroupsTest`: 9, 0 failures. `FolderGroupsScaleTest`: 1, 0 failures, 529 ms for 49,991 groups against the 3 s budget. `OverlapAnalysisScaleTest` unchanged (no diff) and green; `OverlapApiTest` (36) green after the `path` helper refactor; `OpenApiSpecTest` (3) green. JUnit XML in `server/build/test-results/test/`.
- `./gradlew build` green, re-run by the verifier (exit 0).
- Every changed file is inside `touches` or the story folder (checked from `git diff e79ec22 HEAD --name-only`).
- `server/frontend/openapi.json` has `listGroups` (`minBytes` default 1048576) and the schemas `GroupPage`, `FolderGroupView`, `GroupFolderView`, `GroupSetView`.

### How it went

Design: 1 attempt (design gate approved). Tests: 1 attempt; the first hand-back failed the touches gate because of `GroupStateTest` outside `touches`, fixed by the same agent by dropping it. Implement: 1 attempt, green on the first run (1201 passed, 3 skipped repo-wide). No defects routed back, no escalations.

Ready for your approval.

## Summary

- DFS-067.AC-1 met: equal folder shapes merge, differing `copiesEach` split (FolderGroupsTest, GroupApiTest).
- DFS-067.AC-2 met: only direct folders, once, path-sorted; in-between folders with files of their own are not listed.
- DFS-067.AC-3 met: every group and folder figure asserted exactly, including `otherGroups` and `folderFiles`/`folderBytes`.
- DFS-067.AC-4 met: per physical copy; in memory and with a real hard link over HTTP (not skipped).
- DFS-067.AC-5 met: order by reclaimable, first folder, list; paging tested.
- DFS-067.AC-6 met: 9-row parameterized `under` test, counts restricted to matches.
- DFS-067.AC-7 met: default, 0, exact 2 MiB boundary and one above.
- DFS-067.AC-8 met: 20 of 25 sets, order, name, size, copies, digest.
- DFS-067.AC-9 met: 409, 404 (unknown and malformed), 400/403 paths, `INVALID_PAGE`, check order against `minBytes`, cookie and Host.
- DFS-067.AC-10 met: 400 `INVALID_MIN_BYTES` naming `minBytes`; 0 accepted.
- Gap: the build-once / retry / refused-builds-nothing contracts have no unit test (outside touches); verified by inspection against `OverlapState`.
- Ready for the human gate.

## Criteria

See the Summary and Product owner review above; detail per criterion:

- AC-1/AC-2: `FolderGroups.of` keys each set by its `TreeMap`-sorted (folder, copiesEach) list; folders are `link.path().getParent()` only, so ancestors never enter. `rawFilesInABundle` puts files in the in-between folders so their absence is a real check.
- AC-3: `sharedFiles` counts link paths, `sharedBytes` link paths × size, `copiesEach` distinct physical copies per folder; `otherGroups` from one folder → group count map minus one. Asserted with exact numbers in both levels.
- AC-4: `seen` set per copy makes `copiesEach` count a copy once per folder; `copies`/`reclaimable` use `set.copies().size()`.
- AC-5: one sort with the three keys; `compareLists` compares path then `copiesEach`, prefix first.
- AC-6/AC-7: `GroupIndex.page` filters by `startsWith(under)` (normalized, platform rules as `OverlapState.insideRoots`), splits by `minBytes`, pages over the matching remainder.
- AC-8: listing sorted by reclaimable desc then digest, first 20 kept; `totalSets` = set count.
- AC-9/AC-10: `listGroups` order: `completedState` (404/409), `path` (400/403), `checkPage`, `minBytes < 0`, then `index()`. Matches design.md's check order.

## Test quality

- Assertions are on output values, not on implementation: exact figures from hand-computed fixtures, folder lists, order, counts. Not vacuous.
- `differentCopyCountsAreDifferentGroups` picks groups by file count since both reclaim 4000; also pins `otherGroups` 1 for the shared folder. Good.
- The scale test checks group count and the 500-folder group's `copies`, so a broken but fast algorithm would fail.
- `concurrentRequestsAgree` only shows equal answers, not that the build ran once; the build-once contract is untested (see Known limits).
- The HTTP AC-8 test checks names and first size, not digests (the in-memory test does).

## Design and invariants

- Diff matches design.md: package placement, records, state under its own `completedState` key, `OverlapAnalysis`/`OverlapState` unchanged, lazy build after every check, `path` helper generalized to `Predicate<Path>` (behaviour unchanged; `OverlapApiTest` green). Checklist dispositions 1-10 and 12 honoured; 11 is a by-hand done-step check, not run by the verifier.
- `GroupState.index()` is identical in structure to `OverlapState.index()` (CAS on an `AtomicReference<CompletableFuture>`, failed build cleared and rethrown, waiters unwrap the cause): build-once, concurrent callers share, failed build retried. `GroupState` construction only stores the result, so a 409/404 builds nothing (completedState returns Failure before calling the builder) and 400/403 refusals happen before `index()`.
- Invariants: preview/pure query holds (read-only, no disk access); hard links not duplicates holds (counts per `PhysicalCopy`); never-delete-last-copy, execution lock, re-verify, symlink and quarantine/system-folder rules are not touched by this read-only endpoint; empty files are excluded upstream by the scan.
- Docs: CLAUDE.md server entry, ui.md U3 "As built (DFS-067)" bullet and folder-groups.md status line updated as `touches` requires.
