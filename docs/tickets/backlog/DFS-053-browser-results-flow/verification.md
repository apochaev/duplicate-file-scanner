# DFS-053 verification

Implemented by hand (`archetype: none`) on branch `tickets/DFS-053-browser-results-flow`; no phase agents ran.

## Product owner review

### What you can do now
Run `./gradlew :server:e2eTest` to drive a real Chromium through the whole current flow against a real server: open the token link, scan a small fixture folder, open the results, use the folder tree, Sets (with a hard link marked), pin and compare folders, copy a folder path to the real clipboard, and reload a result URL. `./gradlew build` is unchanged: it needs no browser and does not run these tests.

### Acceptance criteria
- DFS-053.AC-1: met. The token link ends on the scan screen showing "Connected", and the `DFS_SESSION` cookie is set.
- DFS-053.AC-2: met. Adding the fixture root and pressing Start runs the scan to Completed and the row offers "Open results".
- DFS-053.AC-3: met. "Open results" lands on `#/scans/<id>` with the Overlaps tab selected and the Folders table listing both fixture folders.
- DFS-053.AC-4: met. Expanding the root shows the subfolders; selecting `extra` shows it in the Overlaps summary and marks its two copies ("in selected folder") on the Sets tab.
- DFS-053.AC-5: met. Two duplicate sets of two copies each, reclaimable equal to one copy's size, and the hard link is marked under the copy it belongs to.
- DFS-053.AC-6: met. Pinning `keep` and selecting `extra` shows the Comparison block with both folders; Unpin removes the pin and the block.
- DFS-053.AC-7: met. After "Copy folder path", `navigator.clipboard.readText()` returns the exact folder path.
- DFS-053.AC-8: met. Reloading `#/scans/<id>` shows the same result screen on the Overlaps tab with no login prompt.

### Try it
`./gradlew :server:e2eTest` (the first run downloads Chromium, about 1 minute; later runs about 25 seconds). Look for `BUILD SUCCESSFUL` and 8 tests in `server/build/test-results/e2eTest/`. `./gradlew build` should stay green and create no `server/.gradle/playwright-browsers` folder.

### Decided without you
- Only Chromium is installed, by running Playwright's installer in a child JVM (`CLI install chromium`) after skipping its default download of every browser (Firefox and WebKit are not needed).
- Each test starts its own server and browser context (about 3 seconds each), so tests share nothing.
- The hard-link check asserts that the physical copy carrying the hard-link mark lists both names, not which of the two the scan lists first (that is the scan's choice).
- `e2eTest` always reruns (`upToDateWhen { false }`), like `updateOpenApi`.

### Known limits
- The flows are not in `check`, so they run only when someone runs `e2eTest`; ui.md D6 and CLAUDE.md say tickets that change the flow must.
- Without hard-link support the AC-5 hard-link check is skipped by an assumption; this machine supports them, so it ran.
- Rule, preview and verify steps from D6 wait for those screens.
- The first run needs network (Chromium download).

### Evidence
- `./gradlew :server:e2eTest` from an empty `server/.gradle/playwright-browsers`: `BUILD SUCCESSFUL in 1m 3s`, `BrowserFlowTest` 8 tests, 0 skipped, 0 failures, 0 errors; folder then holds `chromium-1248`, `chromium_headless_shell-1248`, `ffmpeg-1013`, `winldd-1007` (no Firefox or WebKit).
- `./gradlew build` (after deleting that folder): `BUILD SUCCESSFUL in 2m 14s`; 14 result files in `server/build/test-results/test/`, none for `BrowserFlowTest`; no browser folder created.
- Files changed: `server/build.gradle.kts`, `server/src/test/java/us/pochaev/dfs/server/BrowserFlowTest.java`, `BrowserFixture.java`, `docs/design/ui.md` (D6), `CLAUDE.md`, this ticket and `verification.md`. All within Touches.

### How it went
One pass, by hand. Two fixes along the way: the installer's `main` ended the test JVM (moved to a child process), and the first AC-5 assertion assumed which of the two linked names the scan lists first. No production code changed.

Ready for the user's approval.
