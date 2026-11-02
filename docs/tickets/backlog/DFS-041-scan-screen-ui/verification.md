# DFS-041 verification

## Product owner review

### What you can do now

The page the server opens is now a scan screen. Once you are connected, you can:
- type or paste absolute folder paths; each becomes a chip you can remove
- optionally open Settings to set a minimum file size (bytes, KB or MB) or to include `.DS_Store` and `._*` files
- press Start and watch the scan in the list below: phase, folders, files and bytes found, and a bar while hashing
- cancel a queued or running scan
- see every scan of this server run, newest first, with its totals (files, bytes, duplicate sets), its failure message, or "Cancelled"

A folder the server refuses, such as a relative path, shows the server's message under the field, and your chips stay. Opening a scan's result is the next step (`ui.md` items 6 and 7).

### Acceptance criteria

- **AC-1** met (2 tests): paths become chips and the field clears; a repeated path is skipped; a chip's remove button removes it; Start is disabled until there is a folder.
- **AC-2** met (test): Start sends `{"locations": [...]}` in chip order and nothing else; after the `201` the chips clear and the new scan is the first row, above the older one.
- **AC-3** met (5 test runs):
  - 2 MB, 3 KB and 5 bytes are sent as `minSize` 2097152, 3072 and 5
  - Settings starts closed; unchecking the exclusion sends `defaultExcludes: false`
  - 0 bytes shows "At least 1 byte", disables Start and sends nothing
- **AC-4** met (2 tests): a `400` `RELATIVE_LOCATION` shows its message under the field and keeps the chip; a `503` `EXECUTOR_REJECTED` shows "The server is busy, try again". Neither adds a row.
- **AC-5** met (test): three scans are listed in the server's order with their folders, start times (`<time datetime>` equal to `startedAt`) and status words.
- **AC-6** met (test): a RUNNING row opens exactly one stream on `/api/scans/s-1/events`. It shows:
  - "Finding files", 12 folders, 340 files, "1.5 MB"
  - then "Hashing files" with the bar at 25%
  - then Completed with 3 duplicate sets, and the stream closes
- **AC-7** met (test): Cancel sends one `POST /api/scans/s-1/cancel` and disables itself. The row shows Cancelling, then Cancelled from the stream; the stream closes and the button is gone.
- **AC-8** met (same test as AC-5):
  - Completed shows "1200 files", "5.0 MB" and "7 duplicate sets"
  - Failed shows "Disk not readable"
  - Cancelled shows only its status
  - no stream and no Cancel button on any of them
- **AC-9** met (2 tests): a stream the browser closed for good keeps "340 files" and shows "Live progress lost; reload the page"; a stream that reconnects shows the next event (500 files) without that message.
- **AC-10** met (3 test runs plus 3 existing tests): connected, the page holds the scan screen. On `403` or `authenticated: false` it shows only the console-link message and makes no `/api/scans` call. DFS-037's and DFS-038's three tests still pass; the "Connected" one now also answers the screen's `GET /api/scans`.
- **Live run** (done-item), on the built jar on port 8090 in Chrome, logged in through the token link:
  - a relative path `photos` showed "Give an absolute path, not photos" under the field and kept the chip
  - a scan of this repository folder (29,949 files, 610.6 MB) ran to Completed with 823 duplicate sets
  - a scan of `C:/Users/apoch/Projects` (260,743 files, 3.3 GB) was cancelled while comparing samples: Cancelling, then Cancelled
  - a scan of the `dfs` folder (67,196 files, 1.5 GB) ran to Completed with 21,606 duplicate sets

### Try it

```
$env:JAVA_HOME = "C:\Users\apoch\.jdks\temurin-25.0.4.1"
cd ..\duplicate-file-scanner-DFS-041-scan-screen-ui
.\gradlew.bat :server:bootRun
```

Your browser opens the page. If it doesn't, open the `Open http://127.0.0.1:<port>/?token=...` link from the console. Then:
1. Paste a folder path, press Enter, press Start, and watch the row.
2. Start a larger folder and press Cancel.
3. Type `photos`, press Enter and Start, to see the error.

Stop the server with Ctrl+C.

### Decided without you

- **"Left untouched" means "at its default".** A minimum size of 1 byte and a checked exclusion are left out of the request, even if you changed them and changed them back. The server applies the same defaults, so the result is identical.
- **A Cancelling row keeps a disabled Cancel button**, so the row doesn't jump while the scan stops.
- **Counts are plain numbers** ("29949 files"). Bytes use the CLI's format (1024-based, one decimal), but the CLI's thousands separators aren't used, to keep the tests locale-free. One line in `scan-format.ts` would change that.
- **Other start failures:** a server that can't be reached shows "The server cannot be reached", and an unknown failure shows "The server answered <status>".
- **A failed list load** shows "The scans could not be loaded; reload the page". This isn't in the criteria.
- **Layout:** the page is capped at 960 px and centred; chips are separated from the field by Material's chip grid.

### Known limits

- **The production bundle is 560 kB, over Angular's 500 kB warning budget** (the hard limit is 1 MB; about 111 kB compressed). The build prints a warning but passes. Material's chips, expansion panel, toggles and progress bar account for most of it. The budget lives in `angular.json`, outside this ticket's touches. Raising it to fit a local-only app is a one-line follow-up, if you want it.
- **The page uses a serif fallback font**, because Material's Roboto font isn't loaded (the app loads nothing from the internet). Setting the Material typography to `system-ui` in `styles.css` is a small follow-up.
- **Stale screenshots during the live run:** twice, a screenshot showed a row as Running with a full hashing bar after the server had finished. In the second case the page's DOM already said Completed, and the next screenshot showed it too. In a long run logged event by event, the page matched the server exactly. This looks like the browser not repainting a window in the background, not a page bug. Worth a glance when you try it yourself.
- **Scans and the list live in memory** and vanish on restart, as U2 says. A page left open across a restart shows "Live progress lost" on running rows (AC-9).

### Evidence

- Red run before the implementation (`4e8891a`, a stub screen that throws): 21 tests, 17 failed and 4 passed. The 4 passing are the two existing no-session tests and the two AC-10 no-session cases, which already held before the screen existed.
- Green:
  - `6178b15` is the implementation, `dc79a77` the docs
  - `./gradlew build` green with 21 Vitest runs (18 new) and the unchanged JUnit suites
- Live run: as above. No console errors in the page and no warnings in the server log.
- Scope: `git diff --name-only main...HEAD` lists only paths under Touches.

### How it went

Implemented by hand in one session, tests first. All 21 tests passed on the first green run. Writing files through a shell heredoc broke on apostrophes again, so they were written with the editor. The live run used Chrome, driven from this session.

## Summary

10 criteria met:
- AC-1 to AC-10 by 15 new automated tests (18 runs with the parameterized cases), next to the 3 kept
- the live run by hand in Chrome

Tests ran red before the implementation and green after it. Build green, with a bundle-size warning; scope respected. Next comes D7's Angular component archetype, written from this screen.
