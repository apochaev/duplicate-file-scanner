# DFS-060 verification

## Product owner review

### What you can do now

Open a finished scan's results and see Sets first. The header shows the scan's totals and a bar of reclaimable, kept and unique space. The folder list is ordered by duplicate space, with a bar and two size columns per folder. Duplicate sets appear as one compact table: a header row per set, then one row per copy with a short path, its date and a Copy button. Hard links show as a "2 links" badge. A Compact/Comfortable switch is remembered between visits.

### Acceptance criteria

- **AC-1** met: Sets is selected on open; Overlaps is second and unchanged.
- **AC-2** met: folders, start time and the four figures come from the unfiltered page and stay the same when a folder filters the sets.
- **AC-3** met: the three bar segments are sized from scanned bytes, each with a legend; a 0-byte scan shows empty segments and 0 B.
- **AC-4** met, at desktop width (1440 px) and in the one-column layout (800 px):
  - every level is ordered by duplicate space, and each folder name is visible and not clipped;
  - each row shows the bar, the size and the duplicate size;
  - a click in a row's middle selects the folder ("Copies under <name>" appears);
  - "Pin for comparison" appears on hover or keyboard focus, and clicking it pins the folder without selecting it.
- **AC-5** met: each set is a header row (name, copy count, size each, reclaimable bytes with a bar scaled to the page's largest) followed by one row per copy. The table fits its tab with no sideways scrollbar at 1440 px and at 800 px, and the Reclaimable bytes are visible.
- **AC-6** met: one row per physical copy, a "2 links" badge with both full paths in its tooltip, "in selected" on matching copies, and no "hard link (same file)" line.
- **AC-7** met: paths are relative to the root and shortened by the rule. Every file name is shown without being clipped, and a too-long name is cut in its middle with "…" while keeping its extension. The tooltip and the Copy button carry the full path.
- **AC-8** met: the "Copies under <name>" / "All sets" toggle sends or leaves out `under`, the selection stays, and the paginator pages either list.
- **AC-9** met: starts Compact; Comfortable applies and is stored, and the stored value is restored on open.
- **AC-10** met: the loading bar, empty messages and `role="alert"` errors are unchanged.

### Try it

1. Run `./gradlew :server:bootJar`, then `java --enable-native-access=ALL-UNNAMED -jar server/build/libs/dfs-server.jar`.
2. Open the logged link, scan a folder with duplicates and a hard link, and click "Open results".
3. In a wide window, check that:
   - the folder names, bars and sizes are visible;
   - the Sets table shows Modified and Reclaimable without scrolling sideways;
   - every path ends with its file name.
4. Narrow the window below 960 px. Click the middle of a folder row: the Sets tab shows "Copies under <name>". Hover the row and click "Pin for comparison" at its end: the Overlaps tab then shows "Pinned: <path>".

### Decided without you

- **The path budget is 56 characters (`PATH_BUDGET`), not the prototype's ~62** (the second layout fix), so that the table fits the app's content width. It is fixed and does not adapt to the window. `CLAUDE.md` and `ui.md` say 56.
- **Folder rows are two lines**: the name on the first line, then the bar and the two sizes on the second (second layout fix). The prototype has one line per folder.
- The Sets table uses fixed column widths (second layout fix).
- "Pin for comparison" is drawn only on hover or focus. On a wide window it floats at the end of the name line; below 960 px it sits after the numbers, in the row's own layout.
- The pin button has no enlarged invisible touch area (Material's 48 px touch target is hidden), so it can no longer take clicks meant for the folder row (third fix). On a touch screen it is a smaller target (about 110 x 16 px).
- The Sets summary line ("N duplicate sets · X reclaimable") was dropped, since the header carries those numbers (design note 4).
- The filter toggle appears only while a folder is selected, and selecting a folder turns filtering back on (design note 8).
- "In selected" marks a copy when any of its links is under the folder (design note 8).
- Tooltips are native `title` attributes, so they do not show on keyboard focus (design note 6).
- A path under no root is shown absolute. A root like `C:\` is spelled without its trailing separator (design note 2).
- The header shows scan locations as typed (`C:/...` keeps its forward slashes). Row paths and the clipboard use the server's backslashes.

### Known limits

- Plan controls, the Folder pairs redesign and the scan duration are out of scope, as the ticket says.
- With the ticket's formula, "Unique" counts the extra bytes of hard links, because scanned bytes include every link. In the live check, 500 KB of a hard-linked file appeared as unique.
- Below 960 px the folder tree scrolls in its own box, and its "Size / Duplicates" headings do not line up with the numbers under them (cosmetic).
- No automated test checks layout or click targets; the three layout defects were caught only by the by-hand check. The browser flows run at desktop width.

### Evidence

- **Build and unit tests** (after the pin fix c8a7b81):
  - `./gradlew build` green.
  - `:server:npmTest`: 200 tests, 0 failures (`server/build/test-results/vitest/TEST-vitest.xml`).
  - Every story component spec uses `provideStubSurfacing()` and `settle`.
- **Browser flows:** `./gradlew :server:e2eTest --rerun` after c8a7b81; `server/build/test-results/e2eTest/TEST-us.pochaev.dfs.server.BrowserFlowTest.xml` has tests 8, skipped 0, failures 0, errors 0.
- **Live check** on the built jar (checked that its bundled CSS holds the fix), a fresh Windows scan of three duplicate sets and one NTFS hard link, headless Chrome over the DevTools Protocol, real mouse events. Scripts: `C:\Users\apoch\AppData\Local\Temp\claude\02ad29a6de71421f\52309bff-bc3c-4ce5-ac5b-7de2bf874e6f\scratchpad\verify-scripts\pincheck.mjs` and `dbg2.mjs` there.
  - **At 800 px:** the points at 25, 50 and 75 % across every folder row (Archive, Backups, Downloads, Video, Documents) hit the select button. A click in the middle of Backups, then Video, showed "Copies under Backups", then "Copies under Video". The pin (x 659, 110 x 16 px) is invisible at rest, opacity 1 on hover and on focus. A click on Documents' pin left "Copies under Video" unchanged, and Overlaps showed "Pinned: …\Archive\Documents" with the Comparison block. Names 387–399 px wide, not clipped. Sets tab `scrollWidth` = `clientWidth` (753 px), page 785 px in an 800 px window, all three Reclaimable values visible, no path cell overflows.
  - **At 1440 px:** the same results. Names 208–220 px wide, not clipped; Sets tab 688 = 688 px.
  - No console errors or exceptions.
  - Screenshots in `C:\Users\apoch\AppData\Local\Temp\claude\02ad29a6de71421f\52309bff-bc3c-4ce5-ac5b-7de2bf874e6f\scratchpad\dfs060\`: `pin-800-select.png`, `pin-800-pinned.png`, `pin-1440-select.png`, `pin-1440-pinned.png`, plus the earlier `final-1440.png` and `final-800.png`.
  - I stopped the server by its logged PID (7992) and the Chrome I started.
- **Compared with the prototype:** the header, the bar with legend, the grouped table, the badges and the density toggle match its structure. The folder list differs in having two lines per row.

### How it went

- **Design:** one attempt, passed; the design gate was approved.
- **Tests:** attempt 1 failed the gate (defect 1: a spec outside `touches` and a `not.toThrow` stub wrapper). Attempt 2 passed. It was reopened once for the e2e pin flow (defect 2).
- **Implement:** one attempt passed the gates. The verifier's live checks then sent it back three times, each a CSS/layout fix:
  1. Folder names were invisible and paths were clipped.
  2. Folder names were still invisible, and the Sets table overflowed sideways.
  3. Below 960 px the pin button's invisible touch target took clicks meant for the folder row (sent back at the done gate).
- **Verify, final:** all three are fixed; nothing open.

**Ready for the user's approval.**

## Summary

- DFS-060.AC-1 met: Sets first, Overlaps second and unchanged.
- DFS-060.AC-2 met: figures from the unfiltered page, unchanged by filtering.
- DFS-060.AC-3 met: segments and legends correct, including the 0-byte case.
- DFS-060.AC-4 met: order, visible names, bar and sizes; a click in a row's middle selects at 800 and 1440 px (pin touch target fixed in c8a7b81); pin shows on hover/focus and pins on its own.
- DFS-060.AC-5 met: header row plus copy rows; no sideways scrollbar; Reclaimable visible at 1440 and 800 px.
- DFS-060.AC-6 met: links badge with tooltip, "in selected", no hard-link rows.
- DFS-060.AC-7 met: `displayPath` exact, file names unclipped (budget 56, a decision made without the PO), copy copies the full path.
- DFS-060.AC-8 met: the toggle sends or leaves out `under`, and the paginator works.
- DFS-060.AC-9 met: Compact by default, choice stored and restored through `DENSITY_STORAGE`.
- DFS-060.AC-10 met: states and roles kept.
- e2e 8/8 from the XML after `--rerun`; Vitest 200/200; build green; no safety invariant affected (read-only, GET requests only).
- Verdict: ready for the human gate.

## Acceptance criteria

See the Product owner review. Checked against the diff `903db8e..HEAD`, the specs, and the live DOM, hit tests and real mouse clicks.

## Test quality

- The specs assert rendered text, inline widths, the requests sent (`under`, page, size), clipboard contents and stored values. They do not restate the implementation.
- The `displayPath` examples are exact strings: the ticket's budget-36 examples and the long-name case.
- Gap: no test asserts visibility, layout or hit-testing. All three layout defects passed Vitest and e2e; the pin overlap was reachable only below 960 px, while the e2e flows run wider.

## Design and checklist conformance

- The diff matches `design.md`, except `PATH_BUDGET` is 56 (design said 62), folder rows have two lines, and the pin's touch target is hidden. All are layout fixes, listed under "Decided without you".
- Checklist items 1–10, 12 and 13 are handled as `design.md` disposed them. Item 11 (live check) was done four times.
- All touched files are inside `touches` (c8a7b81 changes only `folder-tree.css` and `progress.md`).

## Safety invariants

- Only "preview is a pure query" is relevant, and it holds: the screen sends only GET requests through the generated client and changes no disk state.
- Hard links are shown as one copy, consistent with "hard links are not duplicates".

## Live check

See Evidence.

## Done-items

- All ticked:
  - scenarios automated;
  - test names;
  - build green;
  - e2e (8/8 from the XML after c8a7b81);
  - checklist addressed;
  - docs;
  - touches;
  - by-hand run on Windows (jar at c8a7b81), compared with the prototype, no console errors.
