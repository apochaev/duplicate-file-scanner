# DFS-059 verification

## Product owner review

### What you can do now

The result API now tells how much of each folder is duplicated (`duplicateFiles`, `duplicateBytes` on every folder) and, for any page of sets, how many files and bytes all matching sets hold (`totalFiles`, `totalBytes`). Nothing changes on screen yet: these are the numbers the compact results screen (DFS-060) will draw as bars and a "Dupes" column.

### Acceptance criteria

- **DFS-059.AC-1** met: every folder, roots and children, carries both numbers; a folder with no duplicates shows 0 and 0.
- **DFS-059.AC-2** met: two hard links of one file with no other copy count 0; a hard link of a real duplicate counts like `files` counts it, and the duplicate numbers never exceed `files`/`bytes`.
- **DFS-059.AC-3** met: `totalFiles` and `totalBytes` cover every matching set across all pages, and only the sets under the folder when `under` is given.
- **DFS-059.AC-4** met: other fields, ordering, paging and refusals unchanged; all 26 earlier result-API tests still pass, and the new test re-checks `409 SCAN_NOT_COMPLETED` and `400 RELATIVE_PATH`.

### Try it

1. `./gradlew :server:bootRun`, open the `Open http://127.0.0.1:<port>/?token=...` link it logs, and run a scan of a folder with some duplicates.
2. In the same browser, open `http://127.0.0.1:<port>/api/scans/<id>/folders` and `.../folders?parent=<a root path>`: each folder has `duplicateFiles` and `duplicateBytes`, never above `files` and `bytes`.
3. Open `http://127.0.0.1:<port>/api/scans/<id>/sets?size=1`: besides `totalItems` and `totalReclaimableBytes` you see `totalFiles` and `totalBytes`, the same on every page. `totalBytes - totalReclaimableBytes` is what the kept last copies occupy.

Or run the tests: `./gradlew :server:test --tests "*ResultApiTest"` (the seven tests named `DFS-059.AC-...`).

### Decided without you

- The new folder fields sit after `bytes`, the new set fields after `totalItems` (JSON field order is not part of the contract).
- `totalFiles` counts every link path, so hard links each count (the ticket said "link paths"; the spec description says so explicitly).
- The AC-4 test checks the `409` on a scan held QUEUED (the existing test helper that keeps a scan unfinished) rather than one actually RUNNING; both are "not completed" and answer the same way.
- The frontend test builders (`result-test-data.ts`) default the four new fields to 0; `touches` was widened for this with your approval during design.

### Known limits

- No screen shows the numbers yet (DFS-060).
- `totalFiles`/`totalBytes` are not tested on a scan with hard links inside a set; the code counts link paths and physical copies as specified, and the folder tests cover the hard-link rule.
- Unchanged pre-existing text: `CLAUDE.md`'s `server` entry repeats the sentence "registry's listener folds the core's `ScanEvent`s ..." twice (not introduced by this ticket).

### Evidence

- `server/build/test-results/test/TEST-us.pochaev.dfs.server.ResultApiTest.xml`: 33 tests, 0 failures, 0 skipped, including all 7 DFS-059 tests; the two hard-link tests really ran on this Windows machine (no `<skipped>`).
- `./gradlew build` green (re-run by the verifier, up to date with HEAD `2a4349a`), which includes `OpenApiSpecTest`, the ArchUnit tests, `ngBuild` and the Vitest tests.
- `server/frontend/openapi.json`: `FolderNode.duplicateFiles/duplicateBytes` and `SetPage.totalFiles/totalBytes`, all `integer`/`int64` and in `required`.
- Deterministic gate at implement: green (902 passed, 3 skipped elsewhere), freeze, touches, stubs, traceability (4 criteria, 7 scenarios), consistency all PASS.

### How it went

Design 1 attempt (opus): found that the regenerated client breaks `ng build` through `result-test-data.ts`, outside `touches`; `touches` was widened with the user's approval before tests. Tests 1 attempt (sonnet, 7 tests red on the stubs). Implement 1 attempt (sonnet, green). No defects, retries or escalations.

Ready for the user's approval.

## Summary

- **DFS-059.AC-1** met: `duplicateFiles`/`duplicateBytes` on every `FolderNode`, rolled up per subtree exactly like `files`/`bytes`; scenario numbers (A 1/3000, B 2/5000, C 2/4000, root 5/12000, deep 1/2000, D 0/0) asserted over HTTP.
- **DFS-059.AC-2** met: sets with fewer than two physical copies are skipped; each link path of a real duplicate counts once; both hard-link tests ran (not skipped) and assert `duplicateFiles == files == 3` for B plus `<=` over every folder returned.
- **DFS-059.AC-3** met: `totalFiles` (link paths) and `totalBytes` (size x physical copies) summed over the same matching sets as `totalItems`; page 0/size 1 and `under=A` asserted.
- **DFS-059.AC-4** met: `sets()` matching/paging logic unchanged, controller untouched, all 26 earlier tests in `ResultApiTest` pass, new test re-asserts 409 on both endpoints and 400 `RELATIVE_PATH`.
- Test quality good: assertions on response values that distinguish counting `files()` vs `copies()` and the copies-of-one filter.
- Design followed, every checklist disposition holds; no invariant concern.
- Ready for the human gate.

## Criteria

- **AC-1 met.** `ResultIndex.addDuplicateTotals` walks each link path's parent chain up to the root with the same `inside(roots, p)` stop the file totals use, so subtree semantics match `files`/`bytes`. Tests `foldersCarryDuplicateNumbers` and `rootAndFolderWithoutDuplicates` assert every Background number in the scenarios (the scan root is `<tmp>/tot`, an equivalent of `<tmp>`).
- **AC-2 met.** `set.copies().size() < 2` skips a set that is only hard links of one file; `set.files()` holds every link path, the same paths `FolderTotal` counts. `hardLinksAloneAreNotDuplicates` (E 0/0) and `hardLinkedCopyWithAnotherCopy` (B dup == files == 3, dup bytes == bytes, `<=` over roots, root's children and C's children) pass and were not skipped.
- **AC-3 met.** `sets()` adds `s.paths.size()` and `size * copies().size()` inside the same `under` predicate that feeds `totalItems`. Tests assert 2/5/12000/7000 on a one-item page and 1/2/6000 under A.
- **AC-4 met.** Controller unchanged; `sets()` keeps DFS-047's matching and paging verbatim. The existing DFS-047 tests (fields, order, paging, `UNKNOWN_SCAN`, `PATH_OUTSIDE_ROOTS`, `INVALID_PAGE`) still pass; `refusalsAreUnchanged` checks `409` on both endpoints and `400 RELATIVE_PATH`.

## Test quality

- Assertions are on HTTP response values computed from a real scan of files written at test time, not on implementation internals.
- The hard-link test discriminates the two plausible wrong implementations: counting physical copies instead of links (B would be 2, not 3) and not filtering single-copy sets (E would be 2, not 0).
- Minor gap: no test puts a hard link inside a set when checking `totalFiles`/`totalBytes` (the scenarios do not ask for it).
- AC-4's not-completed scan is QUEUED (held executor), not RUNNING; same code path (`409` for any non-COMPLETED status).

## Design and checklist

- Records, field placement, `requiredMode = REQUIRED`, record Javadoc and the computation inside `of()` before publishing match `design.md`.
- Checklist 1, 9, 10, 11 not applicable as stated; 2-4 unchanged and re-asserted; 5-7 done (spec regenerated, builder defaults added, frontend compiles); 8 index stays immutable after `of()`; 12 `CLAUDE.md` and `docs/design/ui.md` name all four fields.
- All changed files are in `touches` or the story folder.

## Invariants

- Hard links are not duplicates: respected (copies-of-one filter, tested).
- Preview is a pure query / never delete the last copy / re-verify at execution / one execution at a time: not touched; the change is read-only over an in-memory index and touches no disk state.
- No symlink following, empty files excluded, quarantine left out: unaffected (the index reads the scan result only).
