# DFS-069 verification

## Product owner review

### What you can do now

On the result screen's Overlaps tab, every folder group has a "Mark as dealt with" checkbox. A ticked group moves out of the list into a collapsed "Dealt with on this page (K)" section below the paginator. A line above the list reads "N groups left · M dealt with". The marks stay after a reload. They are kept per scan in this browser only, and nothing is sent to the server.

### Acceptance criteria

- DFS-069.AC-1: met. Every group header has the checkbox. Ticking it moves the group into "Dealt with" and makes no request.
- DFS-069.AC-2: met. The "Dealt with on this page (K)" section is collapsed, missing when nothing on the page is marked, and its groups are dimmed with the box ticked. Unticking a group puts it back in its original position.
- DFS-069.AC-3: met. "N groups left · M dealt with" counts the marks under the selected folder (every mark for "All folders"), and N never goes below 0.
- DFS-069.AC-4: met. Marks are stored under `dfs.results.done.<scanId>` and found again after a reload, even when the folders come back in another order. A different scan does not see them.
- DFS-069.AC-5: met. Marks survive paging, a change of folder and the small-groups button. The paginator and the "Show N small groups" count keep the server's numbers.
- DFS-069.AC-6: met. With no storage, a storage that throws or a corrupt stored value, the tab still loads and marking still works for the visit.
- DFS-069.AC-7: met. A page whose groups are all marked shows "All groups on this page are dealt with." and the paginator still works.

### Try it

1. `./gradlew :server:bootJar`, then `java -jar server/build/libs/dfs-server.jar` and open the `Open http://127.0.0.1:<port>/?token=...` link it prints.
2. Scan a folder that holds duplicates of at least 1 MB in two or more folder pairs. Click "Open results", then the "Overlaps" tab.
3. Tick "Mark as dealt with" on a group. It leaves the list, "Dealt with on this page (1)" appears below the paginator, and the line above the list changes to "N-1 groups left · 1 dealt with".
4. Reload the page and open Overlaps again. The group is still under "Dealt with". Open that section and untick the group to put it back.
5. Tick every group on the page. The list then reads "All groups on this page are dealt with." and the paginator stays usable.

### Decided without you

- The group key joins the sorted folder paths with a line feed (the ticket did not say which separator).
- The "Dealt with" section sits below the paginator. Its content is built even while collapsed.
- The counts line and the "all dealt with" note appear only when the server reports at least one group. With 0 groups, the existing "No folders share files" shows instead.
- "groups left" is always plural, even for 1 ("1 groups left"), as the ticket's own scenario wrote it.
- A mark counts as "under" the selected folder when any of the group's folders is that folder or lies below it. Letter case matters, and both `\` and `/` count as separators.

### Known limits

- "Dealt with on this page" covers only the loaded page, so a page can show fewer groups than its page size. N and M are approximations: a mark on a small group that "Hide small groups" hides again still counts.
- Marks are per browser. They are not shared between browsers or devices, and the server never sees them.
- There is no "show dealt-with groups inside the list" toggle and no "reclaimable still open" figure (out of scope).
- The Playwright browser flow was not changed or run (out of scope). The unit tests and one check by hand on the built jar cover this ticket.
- Small test gap: the "no storage" test's "nothing was written anywhere" line checks a fake storage the component never received, so it proves nothing. Production code cannot write anyway, because it holds no storage when the token is null.

### Evidence

- Vitest (`./gradlew :server:npmTest --rerun`): 17 files, 323 tests passed. `result-done-groups.spec.ts` has 11 tests. `overlaps-tab.spec.ts` has 39, including the 13 new `OverlapsTab dealt with` tests, one per scenario.
- `./gradlew build`: green (up to date at `60fb0af`, which the implementer built green: 1238 passed, 3 skipped).
- `./gradlew :server:ngBuild --rerun`: initial total 689.74 kB, lazy `result-screen` 222.42 kB, no budget warning.
- `server/frontend/openapi.json` and `result-test-data.ts` are unchanged. Every changed file is inside `touches`.
- Check by hand on the built jar (headless Edge, values read from the DOM, details below): ticking, the dimmed look (opacity 0.6), the collapsed section, the stored key, reload, the all-marked note and unticking all behaved as specified.

### How it went

Each phase ran once and passed: design (architect, 48 turns, 520 s), tests (test-author, 18 turns, 137 s), implement (implementer, 27 turns, 341 s). No defects were raised, nothing was retried and nothing escalated. The verifier found no defect.

Ready for the user's approval.

## Summary

- DFS-069.AC-1: met. There is a checkbox per header, ticking moves the group to the "Dealt with" section, and the spec asserts `expectNone` on `/groups`.
- DFS-069.AC-2: met. The section is collapsed, eager, absent at K 0, its groups muted and ticked, and unticking restores the server order.
- DFS-069.AC-3: met. `countsText` uses `countMarksUnder` and clamps N at 0. The three scenarios and the helper edge cases are tested.
- DFS-069.AC-4: met. The key is the sorted paths joined by `\n`, stored per scan. Reload, another scan and reversed folder order are tested and the stored key was confirmed live.
- DFS-069.AC-5: met. `marks` is a `linkedSignal` reset only on a `scanId` change. Paging, selection and the small-groups toggle are tested, with the server figures checked.
- DFS-069.AC-6: met. `readDoneMarks`/`writeDoneMarks` catch every failure and the in-memory signal keeps the marks for the visit. One assertion in the no-storage test is vacuous (minor).
- DFS-069.AC-7: met. The note shows and the paginator's next page stays enabled, both tested and seen live.
- Gate: build green, Vitest 323/323, no budget warning, openapi unchanged, every file in `touches`.
- Invariants: no deletion, preview or scan path is touched, and no request is added.
- Ready for the human gate (done).

## Acceptance criteria

- DFS-069.AC-1: met. The checkbox sits in `div.group-header` through the shared `#groupCard` template. `toggleDone` only updates `marks` and storage. The spec asserts three labelled checkboxes, the move, and `http.expectNone` on `/groups`.
- DFS-069.AC-2: met. The expander comes after the paginator, is collapsed, its content is eager, and it is wrapped in `@if (doneGroups().length > 0)`. `openGroups`/`doneGroups` filter the page in server order. The spec checks the title, `isExpanded()` false, the `group-done` class, the ticked checkbox, and that unticking restores the order `/a/x, /a/y, /a/z`.
- DFS-069.AC-3: met. `countsText` = `max(0, totalItems - countMarksUnder(marks, folder))`. The component tests cover "1 groups left · 2 dealt with", the `/b` selection (1/1) and the clamp (0/5). Helper tests cover a sibling prefix, a trailing separator, case and backslashes.
- DFS-069.AC-4: met. `groupKey` sorts and joins with `\n`, and `doneStorageKey` = `dfs.results.done.<id>`. The spec checks the stored JSON, a recreated tab, scan `s2`, and reversed folder order.
- DFS-069.AC-5: met. `marks` is a `linkedSignal` over `scanId` only. The spec goes through page 2 and back, `/a` and back, and Show/Hide small groups, asserting the range label "of 30", the "Show 4 small groups" button, and "29 groups left · 1 dealt with".
- DFS-069.AC-6: met. Every storage call is in try/catch and a null storage is short-circuited. The component tests cover null, a throwing storage, and `{not json`/`{"a":1}`/`[1,2]`. The helper tests add `["a",1]`, `"text"` and `null`.
- DFS-069.AC-7: met. `p.all-done-note` shows when `openGroups()` is empty and the page has items. The spec checks the note, no open groups, `isNextPageDisabled()` false, and "Dealt with on this page (3)".

## Test quality

- The tests assert on behaviour through the DOM and Material harnesses (`MatCheckboxHarness`, `MatExpansionPanelHarness`, `MatPaginatorHarness`) and on the storage contents. They do not restate the implementation. Groups are told apart by their first folder's tooltip, and order is checked explicitly.
- Every story spec uses `provideStubSurfacing()` and `settle(fixture)` (archetype extra done-item, checklist item 3).
- Weak spot: in the AC-6 "no storage" test, `expect(storage.data.size).toBe(0)` checks a `FakeStorage` that was never provided (the token is `null`), so it is vacuous. It does no harm, because the component has no other way to write.
- Not asserted: after a throwing or null storage, a recreated tab has lost its marks ("last only until the tab is left"). This follows from `readDoneMarks` returning `[]`, which the helper tests cover.
- The opacity is not asserted in jsdom, which is expected. It was confirmed live (0.6).

## Design and checklist

- The diff matches `design.md`: the files, the `DONE_STORAGE` token with `browserStorage` as its factory, the `linkedSignal` `marks`, the `openGroups`/`doneGroups` computeds, the selectors and the texts (U+00B7 confirmed in the live DOM, char code 183).
- Checklist dispositions: 1 (no client change) holds, 2 and 3 hold, 4 (token) holds, 5, 6, 8 and 12 are not applicable as stated, 7 (all-marked note) holds, 9 (harnesses) holds, 10 (689.74 kB, no warning) was re-confirmed, 11 was done here (below), and 13 holds (the `CLAUDE.md` Overlaps sentence and the `ui.md` U3 DFS-069 entry).
- Minor cosmetic point: inside `ng-template #groupCard` the markup keeps its old deeper indentation. It has no effect on behaviour.
- The design said "22 existing tests" in `overlaps-tab.spec.ts`. The file now has 39 = 26 existing + 13 new. This is a miscount in the design text only.

## Invariants

This is client-side only: no API, scan, deletion or preview code changed. "Never delete the last copy", "re-verify at execution time", "preview is a pure query", "hard links", "symlinks", "quarantine/system folders" and "empty files" do not apply and are untouched. No new request is made (AC-1 asserts this).

## Build and gate

- `./gradlew build`: BUILD SUCCESSFUL (all tasks up to date at HEAD `60fb0af`, no later commits).
- `./gradlew :server:npmTest --rerun`: 17 files, 323 tests passed.
- `./gradlew :server:ngBuild --rerun`: initial total 689.74 kB, no budget warning.
- `git diff 72edfc4 HEAD --stat`: every changed file is in `touches` or the story folder. `openapi.json` and `result-test-data.ts` are unchanged.

## Live check (checklist item 11)

Built jar `server/build/libs/dfs-server.jar` run with `--dfs.server.open-browser=false` from a scratch Playwright-for-Java script (headless Edge, `setChannel("msedge")`). The fixture was three 2 MiB duplicate pairs (`a/x`+`b/x`, `a/y`+`b/y`, `a/z`+`c/z`) in a scratch folder outside the worktree, scanned through `POST /api/scans`. All values were read from the DOM:

- Initial: `p.groups-counts` "3 groups left · 0 dealt with" (the dot is char code 183), three groups `a\x | a\y | a\z`, three checkboxes labelled "Mark as dealt with", no expander.
- After ticking the second group: "2 groups left · 1 dealt with", open `a\x | a\z`, expander title "Dealt with on this page (1)", not expanded, holding `a\y` with `group-done` and the box ticked. Computed opacity 0.6. `localStorage["dfs.results.done.<scanId>"]` = a JSON array with one key, `<fx>\a\y` + `\n` + `<fx>\b\y`.
- After reload: the same state (the mark persisted).
- After ticking the other two: "0 groups left · 3 dealt with", note "All groups on this page are dealt with.", "Dealt with on this page (3)", paginator not disabled.
- After expanding and unticking the middle group: "1 groups left · 2 dealt with", the group back in the main list, "Dealt with on this page (2)".
- The server (its own PID) was stopped by the script (`alive=false`), the browser context was closed, and the fixture was deleted.
