# DFS-065 verification

## Product owner review

### What you can do now
Review a scan's results across the whole window instead of a 960 px column. Drill into folders from a ranked list with a breadcrumb instead of expanding a narrow tree, and see longer paths because the Sets table fits them to its column.

### Acceptance criteria
- AC-1 met: one slim toolbar with the title and "Connected" at its right; `main` is full width with 24 px gutters; the scan screen keeps its 960 px column (tests).
- AC-2 met: header, folder browser and tabs are stacked at full width, no left column (tests).
- AC-3 met: roots, the single root's subfolders or the selected folder's subfolders are listed, each level requested once, sorted by duplicate space, folded to 10 with "Show all n folders" (tests and e2e).
- AC-4 met: each row shows name, bar, duplicate size and files, size and files (tests).
- AC-5 met: breadcrumb from "All folders" down, row and crumb selection filter the Sets tab as the tree did, summary beside it (tests and e2e).
- AC-6 met: "Pin for comparison: <name>" pins without selecting (tests and e2e).
- AC-7 met: "Loading subfolders" bar, server message on failure, "No subfolders" for a leaf (tests).
- AC-8 met: the path budget follows the measured File column, 56 when it cannot be measured (tests).
- AC-9 met: the browser flows use the folder browser and pass in `:server:e2eTest`.

### Try it
`./gradlew :server:bootRun`, open the `Open ...?token=` link, scan a folder with duplicates across two subfolders and open its results in a wide window. Look for: the breadcrumb and folder table above the tabs, paths in the Sets table using the full column, selecting a folder scoping the sets, "Pin" on a row.

### Decided without you
- None recorded: this was a hand-implemented ticket and no design note was kept beyond the ticket's own decisions.

### Known limits
- The wide-window check was done by you by hand; my own attempt in Chrome failed (renderer timeouts) and is not evidence.
- Phone layout and the Overlaps tab's own layout are out of scope, as the ticket says.

### Evidence
- `./gradlew build` green. `:server:npmTest`: 15 files, 279 tests passed.
- `./gradlew :server:e2eTest`: `BrowserFlowTest` 8 tests, 0 failures, 0 skipped.
- A first `build` failed `LaunchScriptTest` on stale `cli*` scripts in the ignored `cli/build/bootScripts/`; removing them fixed it. Not a DFS-065 change.
- Browser check at a wide window against a real scan: by the user, "checked looks good".

### How it went
- Hand-implemented, no pipeline phases, no defects found in this final pass.

**Ready for the user's approval.**
