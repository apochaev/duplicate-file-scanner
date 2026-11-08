# DFS-060 verification

## Product owner review

### What you can do now

Open a finished scan's results and see Sets first. The header shows the scan's totals and a bar of reclaimable, kept and unique space. The folder list is ordered by duplicate space, with a bar and two size columns per folder. Duplicate sets appear as one compact table: a header row per set, then one row per copy with a short path, its date and a Copy button. Hard links show as a "2 links" badge. A Compact/Comfortable switch is remembered between visits.

### Acceptance criteria

- **AC-1** met: Sets is selected on open; Overlaps is second and unchanged.
- **AC-2** met: folders, start time and the four figures come from the unfiltered page and stay the same when a folder filters the sets.
- **AC-3** met: the three bar segments are sized from scanned bytes, each with a legend; a 0-byte scan shows empty segments and 0 B.
- **AC-4** partially met. On a desktop-width window (1440 px) it is fully met:
  - every level is ordered by duplicate space, and each folder name is visible, about 210 px wide;
  - each row shows the bar, the size and the duplicate size;
  - a click in a row's middle selects the folder;
  - "Pin for comparison" appears on hover or focus at the end of the name line.

  **In the one-column layout below 960 px** (measured at 800 px), the invisible "Pin for comparison" button's Material touch target covers the middle of every folder row. A click in the middle of a folder would pin it instead of selecting it. The automated flows run at desktop width, so they do not see this.
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
4. Narrow the window below 960 px and click the middle of a folder row. Today this pins the folder (Overlaps would show "Pinned:") instead of selecting it.

### Decided without you

- **The path budget is 56 characters (`PATH_BUDGET`), not the prototype's ~62** (the second layout fix), so that the table fits the app's content width. It is fixed and does not adapt to the window. `CLAUDE.md` and `ui.md` say 56.
- **Folder rows are two lines**: the name on the first line, then the bar and the two sizes on the second (second layout fix). The prototype has one line per folder.
- The Sets table uses fixed column widths (second layout fix).
- "Pin for comparison" is drawn only on hover or focus. On a wide window it floats at the end of the name line; below 960 px it sits after the numbers, in the row's own layout.
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
- I drove folder selection, the filter toggle and the density switch only through the Vitest and browser-flow tests. By hand I expanded the tree and measured hit points.

### Evidence

- **Build and unit tests:**
  - `./gradlew build` green.
  - `:server:npmTest`: 14 files, 200 tests (my run before the layout fixes; the implementer reports green after them).
  - `:server:ngBuild`: no budget warning.
  - Every story component spec uses `provideStubSurfacing()` and `settle`.
- **Browser flows:** `server/build/test-results/e2eTest/TEST-us.pochaev.dfs.server.BrowserFlowTest.xml` has tests 8, skipped 0, failures 0, errors 0 (after the second fix).
- **Final live check** on a freshly built jar (commit d4c1e74), with a fresh Windows scan of three duplicate sets and one NTFS hard link. I measured over the Chrome DevTools Protocol in headless Chromium, with my own script, `C:\Users\apoch\AppData\Local\Temp\claude\02ad29a6de71421f\52309bff-bc3c-4ce5-ac5b-7de2bf874e6f\scratchpad\verify-scripts\measure.mjs`:
  - **At 1440 px:**
    - folder names "Archive", "Backups", "Downloads", "Video", "Documents" are 208–220 px wide and not clipped;
    - the centre of every `folder-select` hits the select button;
    - the Sets tab's `scrollWidth` equals its `clientWidth` (688 px);
    - all three Reclaimable values are inside the tab;
    - no copy-path cell overflows;
    - no console errors or exceptions.
  - **At 800 px:** the same, except the centre of every `folder-select` (x 40–640) hits the `folder-pin` button ("Pin for comparison: <name>", box x 644, width 110, height 16) through its touch target.
  - Header figures 6.4 MB / 3 / 8 / 10.5 MB · 9 files; bar 60.9 / 33.6 / 5.5 %.
  - Screenshots are in `C:\Users\apoch\AppData\Local\Temp\claude\02ad29a6de71421f\52309bff-bc3c-4ce5-ac5b-7de2bf874e6f\scratchpad\dfs060\`: `final-1440.png`, `final-800.png`, plus the earlier `results.png` and `results-fixed.png`.
  - I stopped each server by its logged PID (27448, 8412, 13068, 25880).
- **Compared with the prototype:** the header, the bar with legend, the grouped table, the badges and the density toggle match its structure. The folder list differs in having two lines per row.

### How it went

- **Design:** one attempt, passed; the design gate was approved.
- **Tests:** attempt 1 failed the gate (defect 1: a spec outside `touches` and a `not.toThrow` stub wrapper). Attempt 2 passed. It was reopened once for the e2e pin flow (defect 2).
- **Implement:** one attempt passed the gates. My live check then sent it back twice:
  1. Folder names were invisible and paths were clipped.
  2. Folder names were still invisible, and the Sets table overflowed sideways.
- **Verify, final:** both are fixed at desktop width. One narrow-layout defect remains: the pin button catches row clicks below 960 px.

**Back to implement (small fix)**: below 960 px, keep the `folder-pin` button's touch target from covering the select button, e.g. hide `.folder-pin .mat-mdc-button-touch-target` or keep the pin from overlapping. Alternatively, the product owner may accept this as a known limit, since the screen is used at desktop width. If so, it is ready for approval as it stands.

## Summary

- DFS-060.AC-1 met: Sets first, Overlaps second and unchanged.
- DFS-060.AC-2 met: figures from the unfiltered page, unchanged by filtering.
- DFS-060.AC-3 met: segments and legends correct, including the 0-byte case.
- DFS-060.AC-4 partially met: order, names (now visible), bar and sizes correct, and selecting works at 1440 px. Below 960 px the invisible pin's touch target captures clicks in the middle of a row.
- DFS-060.AC-5 met: header row plus copy rows; no sideways scrollbar; Reclaimable visible at 1440 and 800 px.
- DFS-060.AC-6 met: links badge with tooltip, "in selected", no hard-link rows.
- DFS-060.AC-7 met: `displayPath` exact, file names unclipped (budget now 56, a decision made without the PO), copy copies the full path.
- DFS-060.AC-8 met: the toggle sends or leaves out `under`, and the paginator works.
- DFS-060.AC-9 met: Compact by default, choice stored and restored through `DENSITY_STORAGE`.
- DFS-060.AC-10 met: states and roles kept.
- e2e 8/8 from the XML; build green; no safety invariant affected (read-only, GET requests only).
- Verdict: back to **implement** for the one narrow-layout pin overlap, or approve with it as a known limit.

## Acceptance criteria

See the Product owner review. Checked against the diff `903db8e..HEAD`, the specs, and the live DOM and hit tests.

## Test quality

- The specs assert rendered text, inline widths, the requests sent (`under`, page, size), clipboard contents and stored values. They do not restate the implementation.
- The `displayPath` examples are exact strings: the ticket's budget-36 examples and the long-name case.
- Gap: no test asserts visibility, layout or hit-testing. All three layout defects passed Vitest and e2e, and the pin overlap is only reachable below 960 px, while the e2e flows run wider.

## Design and checklist conformance

- The diff matches `design.md`, except `PATH_BUDGET` is 56 (design said 62) and folder rows have two lines. Both are layout fixes, listed under "Decided without you".
- Checklist items 1–10, 12 and 13 are handled as `design.md` disposed them. Item 11 (live check) was done three times.
- All touched files are inside `touches`.

## Safety invariants

- Only "preview is a pure query" is relevant, and it holds: the screen sends only GET requests through the generated client and changes no disk state.
- Hard links are shown as one copy, consistent with "hard links are not duplicates".

## Live check

See Evidence.

## Done-items

- Ticked:
  - scenarios automated;
  - test names;
  - build green;
  - e2e (8/8 from the XML);
  - checklist addressed;
  - docs;
  - touches;
  - by-hand run on Windows, compared with the prototype, no console errors. The desktop layout matches the prototype's structure. The narrow-layout pin overlap is reported above as a defect against AC-4, not against the comparison.
