---
id: DFS-040
title: Add the scan API to start, list, follow and cancel scans
status: done
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-038]
touches:
  - server/build.gradle.kts
  - server/src/main/java/us/pochaev/dfs/server
  - server/src/test/java/us/pochaev/dfs/server
  - server/frontend/openapi.json
  - server/frontend/proxy.conf.json
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed: 2026-11-01
---

# DFS-040: Add the scan API to start, list, follow and cancel scans

## Story

As the developer of the DFS UI
I want server endpoints that start a scan, list this session's scans, stream a scan's progress and cancel it, all secured and described in the generated client
So that the scan screen (DFS-041) can be built on a finished, tested API, and scans can be run from the browser instead of the CLI

## Context

`docs/design/ui.md` item 5 of the suggested ticket order: "Scan screen: start, progress over SSE, list, cancel (U2, D3)". Split in two (2026-11-01): this ticket is the server API; DFS-041 is the Angular scan screen on top of it. The first server endpoints that do real work, after DFS-035 (skeleton), DFS-036 (security), DFS-037 (Angular) and DFS-038 (generated OpenAPI client).

- **U2:** a start screen takes folders and settings, shows live progress from the scanner's `ScanEvent`s, then opens the result. It goes through the core ports `StartScan` → `GetScanStatus`/`GetScanResult`. Results live in the in-memory `ScanRepository` and are lost on restart.
- **D2:** JSON REST under `/api`; Server-Sent Events for scan progress. The spec and client come from DFS-038's chain (`OpenApiSpecTest`, `:server:updateOpenApi`, `ng-openapi-gen`).
- **D3:** several scans can exist in memory at once; the start screen lists this session's scans (running or finished) with cancel.
- **D1, DFS-035 decision 1:** this ticket switches `server` from `file-scanner` alone to `implementation(project(":scanner-spring"))`, whose `ScannerAutoConfiguration` provides `StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult` and the `scanExecutor` bean.
- **The core today:** `StartScan.start(locations, config[, listener])` returns `Result<ScanId>` at once and validates the locations (`NO_LOCATIONS`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`, `LOCATION_UNREADABLE`); the listener is called on the scanning thread with `ScanEvent`s (`PhaseStarted`, `DirectoryWalked`, `FileWalked`, `HashProgress`, `GroupDone`, `Skipped`). `GetScanStatus` returns a `ScanSnapshot` (status, phase, files walked, groups done/total, failure). `CancelScan` moves QUEUED → CANCELLED and RUNNING → CANCELLING → CANCELLED. There is no port that lists scans.
- **Security (DFS-036):** every `/api/**` call needs the session cookie; state-changing requests need a matching `Origin`. DFS-037 decision 4 left the dev proxy's `Origin` handling to the first ticket with a `POST`, which this is.

## Decisions

1. **Split: the API here, the screen in DFS-041** (2026-11-01). This ticket switches `server` to `scanner-spring`, adds the scan endpoints and the SSE progress stream with their security, and updates `server/frontend/openapi.json` (so the generated client has the scan services before DFS-041 uses them). It is reviewed through tests and `curl`; DFS-041 builds the screen. D7's endpoint archetype is drafted between the two, from these endpoints.
2. **This session's scans are listed from a registry in `server`** (2026-11-01). A `ScanRegistry` bean records each scan the API starts: its `ScanId`, folders and start time. `GET /api/scans` returns those entries with each one's current `GetScanStatus` snapshot, newest first. "This session" is this server run (one user on localhost); the list empties on restart, like the scans themselves (U2). No core change, so no request to the backend lane; a later `ListScans` port (for example with saved scans) can replace the registry without changing the API.
3. **One SSE stream per scan, sending a throttled progress summary** (2026-11-01). The registry's `ScanListener` folds every `ScanEvent` into an in-memory `ScanProgress` for that scan (status, phase, folders, files and bytes found, files and bytes hashed and their totals, groups done and total); the scanning thread only updates memory and never waits on the network. `GET /api/scans/{id}/events` (`text/event-stream`) sends the current `ScanProgress` on connect, then at most every 250 ms while it changes, then once at the terminal status (COMPLETED, CANCELLED, FAILED), and closes. A dropped connection reconnects and gets the current state first: no event IDs, no replay. `ScanProgress` is a named schema in the spec, so the screen gets a generated type for the stream's data (not a generated stream call). Not every raw event (floods the browser, and a slow client would hold up the scan); not polling (D2 asks for SSE).
4. **`POST /api/scans` takes folders plus two settings** (2026-11-01). Body `{"locations": ["C:/a", "D:/b"], "minSize": 1, "defaultExcludes": true}`; both settings optional, defaulting to `ScanConfig.defaults()`; `sampleSize` and `partialHashThreshold` always stay at their defaults (CLAUDE.md decision 3, internal tuning). A started scan answers `201` with its ID and `Location: /api/scans/{id}`. Bad input answers `400` with `{"code": ..., "message": ...}`: a relative path or a `minSize` below 1 is refused by the server before the core is called; `NO_LOCATIONS`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY` and `LOCATION_UNREADABLE` come from `StartScan` with its message.
5. **The dev proxy rewrites `Origin`** (2026-11-01; DFS-037 decision 4 left it here). `server/frontend/proxy.conf.json` sets `"headers": {"Origin": "http://127.0.0.1:8080"}`, so a `POST` through `ng serve` passes the `Origin` check; the server's rules are unchanged. Still safe while `ng serve` runs: another site's `POST` to port 4200 gets its `Origin` rewritten, but the browser does not send the `SameSite=Strict` cookie cross-site, so `/api` answers `403`. Checked once by hand. Not extra allowed origins on the server (production code would gain a way to weaken the check).
6. **`archetype: none`, tests written first** (2026-11-01), as in DFS-036 to DFS-038. JUnit tests against a real server run red before the implementation; `verification.md` records the red run and the commit it preceded. Right after this ticket closes, D7's endpoint archetype is drafted as a `CCW` ticket from these endpoints, before DFS-041.
7. **Implementation shape** (2026-11-01, settled while writing the criteria).
   - `server/build.gradle.kts` adds `implementation(project(":scanner-spring"))`; the scan beans and the `scanExecutor` (virtual threads, shut down with the context) come from `ScannerAutoConfiguration`. The server's `ArchitectureTest` rule (nothing under `us.pochaev.dfs.scanner` outside `domain`) stays and must still pass.
   - The scan code lives in a new package `us.pochaev.dfs.server.scan`: `ScanController`, `ScanRegistry`, and the API records `StartScanRequest`, `ScanEntry` (`id`, `locations`, `startedAt`, `progress`), `ScanProgress` (`status`, `phase`, `directories`, `files`, `bytes`, `hashedFiles`, `hashFilesTotal`, `hashedBytes`, `hashBytesTotal`, `groupsDone`, `groupsTotal`, `failureMessage`) and `ApiError` (`code`, `message`). The API's own records, never the core's types, appear in the spec.
   - Endpoints, all tagged `scans`: `POST /api/scans` (`startScan`), `GET /api/scans` (`listScans`), `GET /api/scans/{id}` (`getScan`), `POST /api/scans/{id}/cancel` (`cancelScan`), `GET /api/scans/{id}/events` (`streamScanEvents`, `text/event-stream`, each event named `progress` with a `ScanProgress` as JSON data). An unknown or malformed ID answers `404` with code `UNKNOWN_SCAN`. The server refuses a relative path with `RELATIVE_LOCATION` and a `minSize` below 1 with `INVALID_MIN_SIZE`.
   - The stream uses Spring MVC's `SseEmitter`, fed by one shared scheduler every 250 ms, sending only when the progress changed; the registry's listener never throws (a throwing listener is detached by the core).
   - Cancel tests: a QUEUED scan comes from a test configuration overriding the `scanExecutor` bean with one that holds tasks until released; a RUNNING scan comes from a generated tree large enough that the cancel lands while it is hashing (the core's own tests, DFS-018, pin the state machine precisely).

## Acceptance criteria

The full ID of each criterion is `DFS-040.AC-<n>`.

- **AC-1** `POST /api/scans` with an existing absolute folder, the session cookie and a matching `Origin` answers `201` with `Location: /api/scans/{id}` and a `ScanEntry` (the ID, the folders as given, the start time, the progress); the scan then reaches `COMPLETED`, and `GET /api/scans/{id}` shows the folder, file and byte counts and the groups of the scanned tree.
- **AC-2** Bad input answers `400` with `{"code", "message"}` and starts no scan (the list is unchanged): `RELATIVE_LOCATION` for a relative path, `INVALID_MIN_SIZE` for `minSize` 0, `NO_LOCATIONS` for `[]`, `LOCATION_NOT_FOUND` for a missing folder, `NOT_A_DIRECTORY` for a file; `minSize` and `defaultExcludes` reach the scan (a file under `minSize` is not counted in any group, a `.DS_Store` is excluded only while `defaultExcludes` is true).
- **AC-3** `GET /api/scans` answers `[]` on a fresh server, and after two starts lists both scans newest first, each with its folders, start time and current progress.
- **AC-4** `GET /api/scans/{id}/events` answers `text/event-stream`; its first `progress` event arrives at once with the current progress; no two events are less than 200 ms apart; the last event carries the terminal status and the stream then ends; on a scan that has already completed it sends exactly one event (`COMPLETED`) and ends.
- **AC-5** `POST /api/scans/{id}/cancel` turns a QUEUED scan `CANCELLED` at once, and a RUNNING one `CANCELLING` and then `CANCELLED` (also the stream's last event); on a scan that has already finished it answers `200` with the status unchanged.
- **AC-6** An unknown or malformed scan ID answers `404` with code `UNKNOWN_SCAN` for `GET /api/scans/{id}`, its cancel and its events.
- **AC-7** Every new endpoint answers `403` without the session cookie and with a foreign `Host`; `POST /api/scans` and the cancel answer `403` without a matching `Origin` and then start or cancel nothing.
- **AC-8** `server/frontend/openapi.json` describes the five operations (tag `scans`; operation IDs `startScan`, `listScans`, `getScan`, `cancelScan`, `streamScanEvents`) and the schemas `StartScanRequest`, `ScanEntry`, `ScanProgress` and `ApiError`, and `OpenApiSpecTest` passes; the generated client has a `ScansService` and the frontend build is green.
- **AC-9** Through the dev proxy (`bootRun` on 8080, `ng serve` on 4200, logged in), a `POST /api/scans` to `http://127.0.0.1:4200` starts a scan, and its events stream through the proxy.
- **AC-10** `CLAUDE.md` describes the scan API and that `server` uses `scanner-spring` (the "does not use `scanner-spring` yet" sentence goes), and `docs/design/ui.md` U2 names the endpoints and the stream.

## Scenarios

AC-1 to AC-8 are JUnit tests in `server/src/test/java/us/pochaev/dfs/server` against a real server on a random port, scanning folders built in a `@TempDir` (the foreign `Host` over a plain socket, as in DFS-036; the stream read line by line with `java.net.http.HttpClient`). AC-9 is run once by hand and AC-10 is read, each recorded in `verification.md`.

```gherkin
Feature: Scan API

  Background:
    Given the server runs on 127.0.0.1 port P with the browser opener off
    And C is a session cookie from the printed token
    And O is the header "Origin: http://127.0.0.1:P"
    And T is a temp folder holding a.bin and b.bin (identical, 300 KB), c.bin (unique, 300 KB) and sub/d.bin (a copy of a.bin)

  @AC-1
  Scenario: A scan starts and completes
    When a client sends POST /api/scans with C, O and {"locations": ["<T>"]}
    Then the response is 201 with Location "/api/scans/<id>"
    And the body has that id, locations ["<T>"], a startedAt time and a progress
    When the client polls GET /api/scans/<id> until the status is terminal
    Then the status is COMPLETED
    And the progress counts 2 directories, 4 files and 1200 KB found, and 1 group

  @AC-2
  Scenario Outline: Bad input is refused and starts nothing
    When a client sends POST /api/scans with C, O and <body>
    Then the response is 400 with code "<code>"
    And GET /api/scans lists no scan

    Examples:
      | body                                          | code               |
      | {"locations": ["relative/dir"]}               | RELATIVE_LOCATION  |
      | {"locations": ["<T>"], "minSize": 0}          | INVALID_MIN_SIZE   |
      | {"locations": []}                             | NO_LOCATIONS       |
      | {"locations": ["<T>/missing"]}                | LOCATION_NOT_FOUND |
      | {"locations": ["<T>/c.bin"]}                  | NOT_A_DIRECTORY    |

  @AC-2
  Scenario: The settings reach the scan
    Given T also holds .DS_Store and a copy of it in sub/
    When a scan of T runs with {"minSize": 400000}
    Then it completes with 0 groups
    When a scan of T runs with {"defaultExcludes": false}
    Then it completes with 2 groups

  @AC-3
  Scenario: This session's scans are listed newest first
    Given a fresh server
    Then GET /api/scans answers []
    When a client starts a scan of T and then a scan of T/sub
    Then GET /api/scans lists two scans, the T/sub scan first
    And each has its locations, a startedAt time and a progress with a status

  @AC-4
  Scenario: The stream follows a scan to its end
    Given a scan of a generated tree with 2,000 files is started
    When a client opens GET /api/scans/<id>/events with C
    Then the content type is text/event-stream
    And the first "progress" event arrives within 1 second
    And no two events are less than 200 ms apart
    And the last event has status COMPLETED and the stream ends

  @AC-4
  Scenario: The stream of a finished scan
    Given a scan of T has completed
    When a client opens GET /api/scans/<id>/events with C
    Then exactly one "progress" event arrives, with status COMPLETED
    And the stream ends

  @AC-5
  Scenario: Cancelling a queued scan
    Given the scanExecutor holds every task until released
    And a scan of T was started and is QUEUED
    When a client sends POST /api/scans/<id>/cancel with C and O
    Then the response is 200 with status CANCELLED

  @AC-5
  Scenario: Cancelling a running scan
    Given a scan of a generated tree large enough to hash for several seconds is RUNNING
    When a client sends POST /api/scans/<id>/cancel with C and O
    Then the response is 200 with status CANCELLING or CANCELLED
    And the scan's stream ends with an event whose status is CANCELLED

  @AC-5
  Scenario: Cancelling a finished scan changes nothing
    Given a scan of T has completed
    When a client sends POST /api/scans/<id>/cancel with C and O
    Then the response is 200 with status COMPLETED

  @AC-6
  Scenario Outline: An unknown scan ID
    When a client sends <request> with C (and O for a POST)
    Then the response is 404 with code "UNKNOWN_SCAN"

    Examples:
      | request                                                          |
      | GET /api/scans/00000000-0000-0000-0000-000000000000              |
      | POST /api/scans/00000000-0000-0000-0000-000000000000/cancel      |
      | GET /api/scans/00000000-0000-0000-0000-000000000000/events       |
      | GET /api/scans/not-a-uuid                                        |

  @AC-7
  Scenario Outline: The scan endpoints are secured
    When a client sends <request> <condition>
    Then the response is 403
    And no scan was started or cancelled

    Examples:
      | request                        | condition                          |
      | POST /api/scans                | with O, without C                  |
      | GET /api/scans                 | without C                          |
      | GET /api/scans/<id>            | without C                          |
      | GET /api/scans/<id>/events     | without C                          |
      | POST /api/scans/<id>/cancel    | with O, without C                  |
      | POST /api/scans                | with C, without an Origin          |
      | POST /api/scans                | with C and Origin http://evil.example |
      | POST /api/scans/<id>/cancel    | with C and Origin http://evil.example |
      | GET /api/scans                 | with C and Host evil.example:P     |

  @AC-8
  Scenario: The spec and the generated client
    Then server/frontend/openapi.json has the operations startScan, listScans, getScan, cancelScan and streamScanEvents, tagged "scans"
    And it has the schemas StartScanRequest, ScanEntry, ScanProgress and ApiError
    And OpenApiSpecTest passes
    And the generated client has a ScansService, and ./gradlew build is green

  @AC-9
  Scenario: POST and the stream through the dev proxy
    Given bootRun on port 8080 and ng serve on 4200, and the user logged in through the token link
    When a POST /api/scans is sent to http://127.0.0.1:4200 with the cookie and Origin http://127.0.0.1:4200
    Then it answers 201
    And GET http://127.0.0.1:4200/api/scans/<id>/events streams progress events to COMPLETED

  @AC-10
  Scenario: Docs describe the scan API
    Then CLAUDE.md's server entry names the scan endpoints, the stream and scanner-spring, and no longer says the server does not use scanner-spring
    And docs/design/ui.md U2 names the endpoints and the stream
```

## Scope

**Touches:**
- `server/build.gradle.kts`: `implementation(project(":scanner-spring"))`.
- `server/src/main/java/us/pochaev/dfs/server`: the new `scan` package (controller, registry, API records, error mapping, SSE).
- `server/src/test/java/us/pochaev/dfs/server`: the scan API tests and their test configuration (the holding executor); `TestServer` helpers for `POST` with a body and for reading a stream, if needed.
- `server/frontend/openapi.json`: regenerated with `:server:updateOpenApi`.
- `server/frontend/proxy.conf.json`: the `Origin` rewrite (decision 5).
- `CLAUDE.md`: the `server` entry.
- `docs/design/ui.md`: U2.

**Out of scope:**
- The Angular scan screen (DFS-041).
- Showing a finished scan's result (overlaps, sets, tree: `ui.md` items 6 and 7).
- Saving and reopening scans (U2, a later ticket behind `ScanRepository`).
- The path-inside-roots rule (U10): the folders a user scans define the roots, so this ticket has no path to check against them; the first endpoint that reads a path inside a scan carries it.

## Definition of done

- [x] Every scenario above is an automated test and passes, except AC-9 (by hand) and AC-10 (read), each recorded in `verification.md`
- [x] The tests ran red before the implementation; the failing run and the commit it preceded are recorded in `verification.md` (decision 6)
- [x] Each test's `@DisplayName` starts with its criterion ID (`DFS-040.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests, `OpenApiSpecTest` and the Vitest tests)
- [x] Started by hand once on Windows: a real folder scanned with `curl` (start, stream, list, cancel), the server log free of errors
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
