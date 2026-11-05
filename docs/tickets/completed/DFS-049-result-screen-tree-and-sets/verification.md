# DFS-049 verification

## Product owner review

### What you can do now

Open a finished scan in the browser. Every COMPLETED scan row has an "Open results" link. It leads to `#/scans/<id>`, which shows a folder tree of the scanned roots and a Sets tab listing the duplicate sets, largest reclaimable first. Selecting a folder filters the sets to it and marks the copies inside it. Each file line has a "Copy folder path" button that copies the folder containing the file. The result has its own URL: reload keeps it, and the browser's back button returns to the scan list.

### Acceptance criteria

- **DFS-049.AC-1 (met):** hash routes. `#/` shows the scan screen and `#/scans/<id>` the result. Reload and back both work, in tests and by hand.
- **DFS-049.AC-2 (met):** "Open results" appears on COMPLETED rows only, including a scan with 0 sets.
- **DFS-049.AC-3 (met):** the header lists the scan's folders and has a "Back to scans" link to `#/`.
- **DFS-049.AC-4 (met):** the scan is asked for first. An unknown scan shows "not in this server run". A scan that is not finished, or a 409 answer, shows "has not finished yet". Each is an alert with the back link and no tree.
- **DFS-049.AC-5 (met):** roots read "<name> · <size> · <n> files" in the API's order. Subfolders are fetched once, on first expand, and only for nodes that have children.
- **DFS-049.AC-6 (met):** "All folders" is selected on opening. Selecting a folder filters the sets (page 0), and "All folders" clears the filter.
- **DFS-049.AC-7 (met):** one card per set, headed "<n> copies · <size> each · <reclaimable> reclaimable", where n counts physical copies. A hard link appears indented under its copy, marked "hard link (same file)". The digest is never shown.
- **DFS-049.AC-8 (met):** only links inside the selected folder are marked "in selected folder". `C:\Photos2` does not count as inside `C:\Photos`.
- **DFS-049.AC-9 (met):** a summary line and a paginator (50 per page, choices 25/50/100). Changing the page or size requests that page, and changing the folder goes back to page 0.
- **DFS-049.AC-10 (met):** clear "no duplicates" messages with no paginator, a progress bar while loading, and the server's message as an alert when a load fails.
- **DFS-049.AC-11 (met):** "Copy folder path" on every link, hard links included. It copies the exact parent folder (drive, UNC and POSIX roots, spaces, Unicode). "Copied" appears only after the write succeeds. On failure you get an alert and the path to select by hand.

### Try it

```
./gradlew :server:bootJar
java -jar server/build/libs/dfs-server.jar
```

1. Open the `Open http://127.0.0.1:<port>/?token=...` link it logs.
2. Scan two folders that share files.
3. When the row says COMPLETED, click "Open results".
4. Look for:
   - the folder tree on the left;
   - cards like "2 copies · 293.0 KB each · 293.0 KB reclaimable";
   - a hard link listed under its copy as "hard link (same file)";
   - after you click a folder, the lines inside it marked "in selected folder".
5. Press "Copy folder path" and paste the result somewhere.
6. Reload the page: the result stays. Press Back: you are on the scan list.

### Decided without you

- **The result screen loads only when opened** (lazy loading). Without this the first page download would have been 896 kB instead of 697 kB, close to the 1 MB budget warning.
- **The folder list and the first page of sets are requested together** once the scan is confirmed COMPLETED. Decision 9 only fixed that the scan comes first.
- **No summary line when there are no sets.** With 0 sets you see only the "no duplicates" message, never "0 duplicate sets · 0 B reclaimable".
- **A hard-link line shows no date.** It is the same file as its copy line, so the date would be the same.
- **Wording the ticket left open:**
  - "The results could not be loaded; reload the page" for a failure that has no server message;
  - "Clipboard access is not available; select the folder path below and copy it";
  - "The folder path could not be copied; select it below and copy it".
- **A failed subfolder load shows its error under that tree row only**; the rest of the screen stays usable.
- **"1 file" and "1 duplicate set" are singular**, using the scan screen's own helper.

### Known limits

- The Overlaps tab is DFS-050.
- Tree nodes show size and file count, not reclaimable bytes (decision 3).
- No end-to-end Playwright flow yet (decision 8; a separate draft).
- **The AC-1 "reload" test is approximate.** It navigates to `/scans/<id>` after the app has started rather than starting the app at that URL. A real reload was checked by hand on the jar, see below.
- **Stale comments (not blocking):** four component classes still carry a design-phase comment saying their methods are stubs that throw (`results/result-screen.ts`, `folder-tree.ts`, `sets-tab.ts`, `path-copy.ts`). The methods are implemented, so the comments are wrong. Remove them in a follow-up or at close.
- **Folder tree totals:** a folder's size and file count include a hard link as a separate file (for example "Photos · 590.8 KB · 3 files" with one hard link). That number comes from the result API (DFS-047), not this screen.

### Evidence

- **`./gradlew build`:** green. `./gradlew :server:npmTest --rerun`: 9 files, 82 tests, all passing, no unhandled errors. The gate on b8c96ea: green, 718 passed, 3 skipped, all from before this ticket.
- **Story specs:**
  - `src/app/app.routes.spec.ts`: 2
  - `scans/scan-row.spec.ts`: 2
  - `results/result-screen.spec.ts`: 15
  - `results/folder-tree.spec.ts`: 6
  - `results/sets-tab.spec.ts`: 11
  - `results/path-copy.spec.ts`: 9
  - `results/result-path.spec.ts`: 16
- **Production build:** initial total 697.22 kB raw (144.78 kB transfer), lazy `result-screen` chunk 205.81 kB, no budget warning.
- **`package.json`/`package-lock.json` vs `main`:** only `@angular/router` `22.2.4`.
- **By-hand run on `dfs-server.jar`:** text read from the DOM by a headless Chrome over the DevTools protocol. Details under "By-hand check on the built jar" below.

### How it went

- **Design:** 1 run (architect; picked up an interrupted earlier attempt's stubs). The user approved the design gate.
- **Tests:** 1 run, then reopened once for defect 1. The AC-1 back test never started the router's location listener; the fix was one line in the spec (29d24ff).
- **Implement:** attempt 1 failed the green gate only on that test defect. The implementer diagnosed it correctly (L-10) and it was sent to the test-author. Attempt 2 was a reconcile with no rerun: every gate passed.
- **Verify:** attempt 1 (this one), no defects that block.
- **Escalations:** none.

**Ready for the user's approval.** The stale design-phase comments are a cosmetic follow-up and do not need a phase rerun.

## Summary

- **DFS-049.AC-1 met:** hash routes, back and reload, in tests and by hand.
- **DFS-049.AC-2 met:** "Open results" only on COMPLETED rows, including 0 sets.
- **DFS-049.AC-3 met:** header folders and "Back to scans" to `#/`.
- **DFS-049.AC-4 met:** `getScan` first; 404, not-COMPLETED and 409 (folders or sets) map to their alerts; no result calls are made after a failing `getScan`.
- **DFS-049.AC-5 met:** root labels in API order; children requested once on first expand; only `hasChildren` nodes expand.
- **DFS-049.AC-6 met:** "All folders" on open; `under` set on folder select and cleared on "All folders"; page 0.
- **DFS-049.AC-7 met:** cards, heading counts physical copies, hard link indented and labelled, no digest.
- **DFS-049.AC-8 met:** separator-aware "under", prefix siblings excluded, nothing marked under "All folders".
- **DFS-049.AC-9 met:** summary line, paginator 50 (25/50/100), page and size sent, page 0 on folder change.
- **DFS-049.AC-10 met:** empty messages without paginator, progress bars on the sets and tree levels, server message alerts.
- **DFS-049.AC-11 met:** per-link copy, exact parent folder for all six example rows, success only after write, fallback on unavailable or rejected; real-browser success and rejection both seen.
- **Non-blocking:**
  - stale "Stubs (design phase)" class comments in four `results/*.ts` files;
  - the AC-1 reload test navigates in-app instead of booting at the URL (covered by hand).
- **Ready for the human `done` gate.**

## Criteria

- **DFS-049.AC-1: met.**
  - Code: `app.routes.ts` (`''`, lazy `scans/:id`, `**` → `''`) and `provideRouter(routes, withHashLocation())` in `app.config.ts`. `app.html` renders `<router-outlet />` only when connected.
  - Tests: `app.routes.spec.ts` checks that navigating to `/scans/s-1` renders `app-result-screen` (not `app-scan-screen`) and issues `GET /api/scans/s-1`. Its second test checks that `Location.back()` returns to `/` and the scan screen.
  - The "page loaded at the URL" half is simulated by an in-app navigation, not by booting at that URL. A real reload at `#/scans/<id>` was confirmed on the jar.
- **DFS-049.AC-2: met.**
  - Code: `scan-row.html` puts `a.open-results [routerLink]="resultLink()"` only in the `COMPLETED` case.
  - Tests: `scan-row.spec.ts` checks the `href` `#/scans/s-3` and `#/scans/s-0` (0 sets), and that there is no link or text for QUEUED, RUNNING, CANCELLING, CANCELLED and FAILED.
  - By hand: two COMPLETED rows showed `Open results -> #/scans/<id>`.
- **DFS-049.AC-3: met.**
  - Tests: `result-screen.spec.ts` checks the locations list `['C:\\Photos','D:\\Backup']`, and that the back link has text "Back to scans" and `href="#/"`. A second test asserts `GET /api/scans/<id>` with no folder or sets request before it is answered.
- **DFS-049.AC-4: met.**
  - Code: `loadScan` stops on a non-COMPLETED status. `unavailableMessage` maps 404 `UNKNOWN_SCAN` and 409 `SCAN_NOT_COMPLETED`. A 409 from `listSets` becomes `fatalError` too.
  - Tests: four screen tests (404; RUNNING; 409 from folders; 409 from sets) check `role="alert"`, the exact text, the back link, no tree or tab, and (for the first two) `expectNone` on folders and sets.
  - By hand: a dead UUID URL showed the 404 alert with `role="alert"` and no tree.
- **DFS-049.AC-5: met.**
  - Tests: `folder-tree.spec.ts` checks:
    - the labels "Photos · 12.0 GB · 3210 files" and "Backup · 900 B · 2 files" in order, with no `parent` request;
    - `MatTreeNodeHarness.isExpandable` true and false;
    - after expand, exactly one `parent=C:\Photos` request, children at level+1;
    - collapse and re-expand makes no new request.
  - A screen-level test repeats this through the real screen.
  - By hand: "Expand Photos" was the only toggle (Backup has no subfolders). Expanding added "2024 · 293.0 KB · 1 file".
- **DFS-049.AC-6: met.**
  - Tests: screen tests check `aria-pressed="true"` on "All folders", the first sets request without `under` (the `expectSets` helper asserts the parameter is absent), `under=C:\Photos` at page 0 after a select, and no `under` after "All folders". Tree tests check the emitted path and null, and `aria-pressed` per state.
  - By hand: selecting Backup, then 2024, then All folders changed the summary as expected ("2 duplicate sets", "1 duplicate set", "2 duplicate sets").
- **DFS-049.AC-7: met.**
  - Code: `setHeading` uses `copies.length`.
  - Tests: `sets-tab.spec.ts` checks the exact headings for two sets in order, one `li.physical-copy` per copy, a copy line with path, date text and `datetime`, the hard-link line with "hard link (same file)", no label on copy lines, and the digest string absent from the page.
  - By hand: a set with a real NTFS hard link read "2 copies · 293.0 KB each · 293.0 KB reclaimable", and the hard link was a `.hard-link` line under `Photos\2024\a.jpg`.
- **DFS-049.AC-8: met.**
  - Code: `isPathUnderFolder` treats equality, a separator after the prefix, and a folder that already ends in a separator (a root) as "under".
  - Tests: tab tests cover "D:\Backup" marking only its copy (not the C: copy or its hard link), the `C:\Photos2` sibling, and nothing marked under "All folders". The helper has 9 table rows, including roots and POSIX paths.
  - By hand: Backup marked both Backup files. 2024 marked only `Photos\2024\a.jpg`, not the hard link in `Photos`. "All folders" marked nothing.
- **DFS-049.AC-9: met.**
  - Tests: tab tests check the summary "120 duplicate sets · 5.0 GB reclaimable", page size 50, and emitted `[1,50]` then size 100. Screen tests check size 100 at page 0, next page at `(1,100)`, next page at `(1,50)`, then a folder at `(0,50)`.
  - The `pageSizeOptions` 25/50/100 are in the code but not asserted by a test (minor gap).
- **DFS-049.AC-10: met.**
  - Tests cover:
    - both empty messages, with no paginator and no summary;
    - the "Loading duplicate sets" progress bar while pending, gone after a 400;
    - the 400 message "The path is not absolute" with `role="alert"`;
    - the tree's "Loading subfolders" bar and its `folder-error` alert;
    - a failed roots load showing a `result-error` alert.
- **DFS-049.AC-11: met.**
  - Code: `containingDirectory` is lexical, keeping the separator on drive, UNC-share and POSIX roots. `PathCopy.copy` sets `copied` only after `writeText` resolves.
  - Tests: `path-copy.spec.ts` runs all six example rows. Each asserts the exact text written, no `.copy-success` before the Promise resolves, then `role="status"` "Folder path copied". The alias test checks `D:\Aliases`. Unavailable (null token) and rejected writes show `role="alert"`, the fallback `code` with `C:\Photos`, and no success. Every link, the hard link included, has its own button (sets-tab test).
  - By hand in Chrome:
    - unfocused, the browser refused the write: the rejection alert and the selectable fallback path appeared;
    - with focus emulated: `role=status` "Folder path copied", and the clipboard read back as exactly the hard link's own folder (`...\dfs049\Photos`, not `...\Photos\2024`).

## Test quality

- **The tests check behavior, not internals.** They assert on rendered DOM text, attributes (`href`, `role`, `aria-pressed`, `datetime`), HTTP requests with exact parameters (absent parameters are checked as absent), emitted outputs, and Material harness state. None reads private state or restates the implementation.
- **Timing claims are tested properly.** "Announced only after the write succeeds" uses a fake clipboard whose Promise the test resolves itself, so the assertion before `accept()` is meaningful. "Requested once" uses `expectNone` after collapse and re-expand. "Before any result call" uses `expectNone` before `getScan` is answered.
- **Each behavior is tested at two levels**: the presentational components on their own (tree, tab, copy) and through the routed screen (`RouterTestingHarness`), so the wiring between them is covered too.
- **Minor gaps (non-blocking):**
  - the 25/50/100 page-size options are not asserted;
  - the second copy line's date is not asserted;
  - the AC-1 "load at URL" test navigates in-app rather than booting at the URL.
- **`result-path.spec.ts`** is a pure-function spec with no fixture, so `provideStubSurfacing()`/`settle()` do not apply to it. Every other story spec uses both.
- **`app.spec.ts` and `scan-screen.spec.ts`** were changed only to add router providers, as the design declared.

## Design and checklist

- **The diff matches `design.md`:** components, files, classes, texts, page states and API call order. `app.html` uses a router outlet, the result route is lazy-loaded, and the clipboard sits behind the `CLIPBOARD` token.
- **All 13 checklist dispositions hold:**
  1. Only the generated client is used for the API; `src/app/api/` is untouched.
  2. Stub surfacing and settle are used in the story specs.
  3. Every criterion's path reached a stub; the gate's red check confirmed this.
  4. The clipboard is behind a token.
  5. The screen sits in the router outlet, shown only when connected.
  6. Not applicable, as the design says.
  7. Loading, empty and error states exist, with `role="alert"` and the regions labelled.
  8. Not applicable, as the design says.
  9. Material's own expansion model is used, so the harnesses work.
  10. Budget: 697 kB initial, no warning.
  11. The live check reads the DOM: done, below.
  12. The dev server is unchanged.
  13. `CLAUDE.md` and `ui.md` are updated.
- **Design drift:** the four class comments still say "Stubs (design phase): ... throw". This is documentation now contradicted by the code. It is not a behavior defect, but it should be removed.
- **Decision 7:** `package.json` and `package-lock.json` differ from `main` only by `@angular/router` 22.2.4 (pinned exactly, peers 22.2.4).
- **Touches:** every changed file is inside `touches` or the story folder (43 files, the same as the gate).

## Safety invariants

- **Never delete the last copy, re-verify at execution time, preview is a pure query:** nothing here deletes, plans or previews anything. The screen issues only `GET` requests. "Copy folder path" writes only to the clipboard (and is lexical, no disk access).
- **Hard links are not duplicates:** this holds. The heading counts `copies.length` (physical copies), and extra links render as "hard link (same file)" under their copy, never as copy lines. Confirmed by hand with a real NTFS hard link: "2 copies" for three paths.
- **Symlinks and junctions, empty files:** these are backend concerns, unaffected by this frontend-only change.

## By-hand check on the built jar

Run on 2026-11-05 against `server/build/libs/dfs-server.jar` (built from b8c96ea), started with `java -jar ... --dfs.server.open-browser=false`. The fixture, created in a scratch folder outside the worktree and deleted afterwards:

- `Photos\2024\a.jpg` (300 000 random bytes);
- `Photos\link-to-a.jpg`, an NTFS hard link to it (`mklink /H`);
- `Backup\a.jpg`, a copy;
- `Photos\b.txt` with its copy `Backup\b.txt` (5 000 bytes);
- `Backup\u.txt`, unique.

Scanned folders: `...\dfs049\Photos` and `...\dfs049\Backup`. A headless Chrome 156 was driven over the DevTools protocol (scratch Node script). It opened the token link, started the scan through the page's session, reloaded the list, clicked the row's link, and then read each value with `querySelectorAll(...).textContent`. Paths below are shortened to `...\dfs049\`; the full scratch prefix was present in the DOM.

```
url after token: http://127.0.0.1:61973/#/
row counts: 6 files · 888.7 KB · 2 duplicate sets
open-results: Open results -> #/scans/ce661056-3c35-4469-a8ec-039496e7dba4
url after open: http://127.0.0.1:61973/#/scans/ce661056-3c35-4469-a8ec-039496e7dba4
header locations: ...\dfs049\Photos | ...\dfs049\Backup
back link: Back to scans -> #/
tree nodes: Backup · 297.9 KB · 3 files | Photos · 590.8 KB · 3 files
all-folders aria-pressed: true
expand toggles: Expand Photos
summary: 2 duplicate sets · 297.9 KB reclaimable
.set-heading: 2 copies · 293.0 KB each · 293.0 KB reclaimable | 2 copies · 4.9 KB each · 4.9 KB reclaimable
.copy-line: ...\dfs049\Backup\a.jpg Nov 5, 2026, 6:16:21 AM Copy folder path
            ...\dfs049\Photos\2024\a.jpg Nov 5, 2026, 6:16:20 AM Copy folder path
            ...\dfs049\Backup\b.txt Nov 5, 2026, 6:16:21 AM Copy folder path
            ...\dfs049\Photos\b.txt Nov 5, 2026, 6:16:21 AM Copy folder path
.hard-link: ...\dfs049\Photos\link-to-a.jpg hard link (same file) Copy folder path
paginator present: true
tree after expanding Photos: Backup · 297.9 KB · 3 files | Photos · 590.8 KB · 3 files | 2024 · 293.0 KB · 1 file
select Backup -> summary: 2 duplicate sets · 297.9 KB reclaimable
  .in-selected-folder: ...\dfs049\Backup\a.jpg | ...\dfs049\Backup\b.txt (labels: in selected folder x2)
select 2024 -> summary: 1 duplicate set · 293.0 KB reclaimable
  .in-selected-folder: ...\dfs049\Photos\2024\a.jpg   (the hard link in Photos is not marked)
Copy folder path on the hard link (page unfocused): alert "The folder path could not be copied; select it below and copy it", fallback "...\dfs049\Photos"
Copy folder path on the hard link (focus emulated): status "Folder path copied"; navigator.clipboard.readText() = "...\dfs049\Photos"
select All folders -> summary: 2 duplicate sets · 297.9 KB reclaimable; .in-selected-folder: (none)
reload at #/scans/ce66...: same URL, same two .set-heading texts
history.back(): http://127.0.0.1:61973/#/, scan screen shown, result screen gone
#/scans/00000000-0000-0000-0000-000000000000: role=alert "This scan is not in this server run (the server may have restarted)", no folder tree
```

Afterwards the server was stopped by its logged PID (25392), the headless Chrome by its own profile's processes, and the scratch fixture and profile were deleted.
