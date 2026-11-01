# DFS-040 verification

## Product owner review

### What you can do now

The server can run scans. You can:
- start a scan of absolute folders, optionally with a minimum size or with `.DS_Store` files included
- list this server run's scans, newest first
- read one scan's progress, or cancel it
- follow a scan live through a Server-Sent Events stream, which sends a progress summary at most every 250 ms and ends when the scan does

Every endpoint needs the session cookie, and both `POST`s need the server's own `Origin`. The spec and the generated Angular client include it all (a `ScansService`), ready for the scan screen (DFS-041). Nothing in the browser uses it yet: you review this ticket through tests and `curl`.

### Acceptance criteria

- **AC-1** met (test): `POST /api/scans` answers `201` with `Location: /api/scans/<id>` and the entry. The scan completes with the fixture's 2 folders, 4 files, 1,228,800 bytes and 1 duplicate set.
- **AC-2** met (5 cases + 1 test):
  - refused with `400` and nothing started: `RELATIVE_LOCATION`, `INVALID_MIN_SIZE`, `NO_LOCATIONS`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`
  - `minSize` 400000 gives 0 sets, `defaultExcludes: false` gives 2 sets (the `.DS_Store` pair counts), and the defaults give 1
- **AC-3** met (test): `[]` on a fresh server; after two starts, both are listed, the newer first, each with folders, start time and progress.
- **AC-4** met (2 tests): the stream is `text/event-stream`, the first event arrives at once, no two events come less than 200 ms apart, and the last event is COMPLETED, after which the stream ends. A finished scan's stream sends exactly one COMPLETED event. A one-off measurement on the 2,000-file test tree showed 9 events over 2.3 s, about 250–300 ms apart, so the spacing check isn't vacuous.
- **AC-5** met (3 tests): a QUEUED scan (held by a test executor) is CANCELLED at once. A RUNNING one (24 files of 16 MB) answers CANCELLING or CANCELLED, and its stream ends with CANCELLED. A finished one stays COMPLETED.
- **AC-6** met (4 cases): an unknown UUID on get, cancel and events, and a malformed ID, all answer `404` `UNKNOWN_SCAN`.
- **AC-7** met (9 cases): without the cookie, every endpoint answers `403`. So do the `POST`s without an `Origin` or with `http://evil.example`, and a foreign `Host`. Afterwards the list still holds exactly the one QUEUED scan, so nothing was started or cancelled.
- **AC-8** met (test + build): `openapi.json` has the five operations tagged `scans` and the four schemas, and the events operation is `text/event-stream`. `OpenApiSpecTest` passes, the generated `ScansService` exists, and the frontend build is green.
- **AC-9** met (by hand), with `bootRun` on 8080 and `ng serve` on 4200, logged in through the token link:
  - `POST http://127.0.0.1:4200/api/scans`, sent with `Origin: http://127.0.0.1:4200`, answered `201`; the proxy rewrote the `Origin`
  - a scan of `C:/Users/apoch/Projects/us.pochaev/dfs` streamed through 4200: 11 events in 3 s, walking up to 78,075 files, then partial hashing
- **AC-10** met (read):
  - `CLAUDE.md` names `scanner-spring` as a `server` dependency, and the "does not use `scanner-spring` yet" sentence is gone
  - the `server` entry lists the five endpoints, the stream and the registry, and the dev-proxy paragraph explains the `Origin` rewrite
  - `ui.md` U2 names the endpoints and the stream

### Try it

```
$env:JAVA_HOME = "C:\Users\apoch\.jdks\temurin-25.0.4.1"
cd ..\duplicate-file-scanner-DFS-040-scan-screen
.\gradlew.bat :server:bootRun --args="--server.port=8080 --dfs.server.open-browser=false"
```

In a second PowerShell window, use `curl.exe`, not `curl`, which PowerShell treats as an alias:

1. Log in and keep the cookie:
   ```
   curl.exe -s -c cookies.txt "<the Open http://127.0.0.1:8080/?token=... link from the first window>"
   ```
2. Start a scan:
   ```
   curl.exe -s -b cookies.txt -H "Origin: http://127.0.0.1:8080" -H "Content-Type: application/json" -d '{\"locations\":[\"C:/Users/apoch/Projects/us.pochaev/dfs\"]}' http://127.0.0.1:8080/api/scans
   ```
3. Watch it live, using the `id` from step 2:
   ```
   curl.exe -s -N -b cookies.txt http://127.0.0.1:8080/api/scans/<id>/events
   ```
4. Cancel or list:
   ```
   curl.exe -s -b cookies.txt -X POST -H "Origin: http://127.0.0.1:8080" http://127.0.0.1:8080/api/scans/<id>/cancel
   curl.exe -s -b cookies.txt http://127.0.0.1:8080/api/scans
   ```

Stop the server with Ctrl+C.

### Decided without you

- **`groupsDone`/`groupsTotal` change meaning at the end.** While a scan runs, they count the current phase's groups, as the core reports them. Once it is COMPLETED, both are the number of duplicate sets found, read once from `GetScanResult`. This is written in `ScanProgress`'s Javadoc.
- **The hash counts reset at each phase start**, so they always describe the current hashing phase.
- **An invalid path string** (one Windows can't parse) answers `400` `LOCATION_NOT_FOUND` with "Not a valid path", and the core's `EXECUTOR_REJECTED` answers `503`. Neither is in the criteria.
- **The stream checks every 50 ms** and sends only when 250 ms have passed since the last event and the progress changed. The terminal event follows the same spacing, so it can arrive up to about 300 ms after the scan ends.
- **Streams have no timeout** (`SseEmitter(0)`). A stream ends with its scan, when the client disconnects, or at server shutdown.

### Known limits

- **The generated client has a `streamScanEvents` method, but it can't read a stream.** The screen (DFS-041) will open the stream with the browser's `EventSource` and use the generated `ScanProgress` type for the data.
- **Two tests depend on timing:** the 200 ms spacing, and cancelling while hashing. They passed in 3 of 3 runs here, with wide margins: gaps of 250–300 ms against the 200 ms floor, and a 384 MB tree against a cancel that lands within milliseconds. A much slower machine could still flake.
- **The AC-4 and AC-5 tests write test trees:** 4 MB and 384 MB under the system temp folder, deleted after each test. The server test run takes about 20 s longer.
- **Scans and the list live in memory** and vanish on restart, as U2 says.
- **`bootRun` and `ng serve` outlived their Gradle tasks again** and were killed by PID (as on DFS-037).

### Evidence

- Red run before the implementation (`237ffe1`): 27 tests, 27 failed, since no scan endpoints existed.
- Green:
  - `ac244c0` is the implementation, `7b59a38` the docs
  - `./gradlew build` green, with 62 JUnit tests in `server` (27 new) and 3 Vitest tests
  - `ScanApiTest` rerun twice more: 27 of 27 passed each time
- Manual run: as under AC-9. The cancel answered CANCELLING, and its stream ended CANCELLED. The list showed both scans newest first. No warnings or errors in the server log.
- Scope: `git diff --name-only main...HEAD` lists only paths under Touches.

### How it went

Implemented by hand in one session, tests first. All 27 tests passed on the first green run. The only stumble was a shell heredoc that broke on an apostrophe, so two files were written with the editor instead.

## Summary

10 criteria met:
- AC-1 to AC-8 by 27 automated tests
- AC-9 by hand through the dev proxy
- AC-10 by reading

Tests ran red before the implementation and green after it, three times. Build green, scope respected. Next comes D7's endpoint archetype, from these endpoints, then DFS-041.
