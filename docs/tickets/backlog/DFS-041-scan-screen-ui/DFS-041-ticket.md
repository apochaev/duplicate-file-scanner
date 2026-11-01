---
id: DFS-041
title: Add the scan screen to start, follow and cancel scans
status: in-progress
type: feature
scope: [build]
archetype: none
priority: high
lane: frontend
depends_on: [DFS-040]
touches:
  - server/frontend/src/app
  - server/frontend/src/styles.css
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-041: Add the scan screen to start, follow and cancel scans

## Story

As the user of the local DFS server
I want to enter folders, start a scan, watch its progress live, cancel it, and see this session's scans listed
So that I can scan from the browser instead of the CLI, as the first step toward exploring and cleaning up duplicates there

## Context

`docs/design/ui.md` item 5, second half (split 2026-11-01; DFS-040 is the server API it uses). U2: a start screen takes folders and settings, shows live progress from the scanner's events, then opens the result (opening the result arrives with `ui.md` items 6 and 7). D3: the start screen lists this session's scans (running or finished) with cancel. The page calls the API only through the client generated from `server/frontend/openapi.json` (DFS-038): `ScansService` with `startScan`, `listScans`, `getScan` and `cancelScan`, and the models `StartScanRequest`, `ScanEntry`, `ScanProgress` and `ApiError`. The one exception is the progress stream: the generated `streamScanEvents` cannot read Server-Sent Events, so the page opens `GET /api/scans/{id}/events` with the browser's `EventSource` (same origin, so the session cookie goes along) and parses each `progress` event's data as the generated `ScanProgress` type. The server sends the current progress on every connect, so `EventSource`'s own reconnect needs no extra handling, and it ends the stream after the terminal status (DFS-040).

`ScanProgress.status` is one of `QUEUED`, `RUNNING`, `CANCELLING`, `COMPLETED`, `CANCELLED`, `FAILED`; `phase` one of `WALK`, `PARTIAL_HASH`, `FULL_HASH`, `HARD_LINKS` or null. Once `COMPLETED`, `groupsTotal` is the number of duplicate sets. A `400`'s body is an `ApiError` (`code`, `message`); the start codes are `NO_LOCATIONS`, `RELATIVE_LOCATION`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`, `INVALID_MIN_SIZE`, plus `503` `EXECUTOR_REJECTED`.

## Decisions

1. **Folders are typed as absolute paths** (2026-11-01). A text field adds each path as a removable chip (pasting from Explorer's address bar works); the API's codes (`RELATIVE_LOCATION`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`, `NO_LOCATIONS`) are shown next to the field. The browser's own folder picker cannot give a page absolute paths. A server-side folder browser is a later endpoint ticket (it reads the disk outside any scan, an exception to U10 that needs its own decision), and a natural first live use of the endpoint archetype (CCW-019).
2. **One page, no router** (2026-11-01). The form on top (folder chips, the two settings, Start); below it this server run's scans, newest first, each row with its folders, start time and status. A running scan's row shows live progress from its stream (phase, folders, files and bytes found, a hashing progress bar) and a Cancel button; a finished row shows its totals (files, bytes, duplicate sets) or the failure message. The server serves the page only for `GET /`, so routes would need a single-page-app fallback on the server; routing and that fallback come with the first result view (`ui.md` items 6 and 7), which needs deep links.
3. **Both settings, in a "Settings" panel closed by default** (2026-11-01). "Minimum file size", a number with a unit (bytes, KB, MB; 1024-based like the CLI's report), default 1 byte, and "Skip `.DS_Store` and `._*` files", checked by default. A setting left untouched is left out of the request, so the server's defaults apply; a size below 1 byte is refused in the form, and a server `INVALID_MIN_SIZE` is shown too.
4. **`archetype: none`, built by hand** (2026-11-01). D7's Angular component archetype does not exist yet; this ticket is its raw material (CCW-019 decision 1), as DFS-038 and DFS-040 were for the endpoint archetype.
5. **The stream is opened through a small injectable wrapper** (2026-11-01, settled while writing the criteria). A `ScanEventsService` turns `EventSource` on `/api/scans/{id}/events` into an Observable of `ScanProgress` (closing the `EventSource` on unsubscribe or a terminal status); its `EventSource` constructor comes from an injection token, so Vitest (jsdom has no `EventSource`) supplies a fake that the test drives event by event. Every other call goes through the generated `ScansService`.
6. **The scan screen shows only when connected** (2026-11-01, settled while writing the criteria). Without a session every `/api/**` call answers `403`, so the page keeps DFS-037's "Open the link printed in the console" and makes no scan call.

## Acceptance criteria

The full ID of each criterion is `DFS-041.AC-<n>`.

- **AC-1** When connected, the page shows the scan form; typing an absolute path and pressing Enter (or pasting it and pressing Enter) adds it as a chip, the field clears, a chip's remove button removes it, the same path is not added twice, and Start is disabled while there are no chips.
- **AC-2** Start sends one `POST /api/scans` through the generated `ScansService.startScan` with the chips' paths in order as `locations` and no `minSize` or `defaultExcludes` when the settings were left untouched; after a `201` the chips clear and the new scan appears at the top of the list.
- **AC-3** The Settings panel is closed by default; a minimum size of 2 MB is sent as `minSize: 2097152`, 3 KB as `3072`, 5 bytes as `5`; unchecking "Skip `.DS_Store` and `._*` files" sends `defaultExcludes: false`; a size of 0 or empty after editing shows "At least 1 byte" and disables Start.
- **AC-4** A `400` from start shows its `ApiError` message next to the folder field and keeps the chips; a `503` shows "The server is busy, try again" for `EXECUTOR_REJECTED`; nothing is added to the list in either case.
- **AC-5** On load, the page lists `GET /api/scans`' entries in the server's order (newest first), each row with its folders, its start time and its status in words (Queued, Running, Cancelling, Completed, Cancelled, Failed).
- **AC-6** A row whose status is `QUEUED`, `RUNNING` or `CANCELLING` opens one stream on `/api/scans/{id}/events`; each `progress` event updates the row's phase in words (Finding files, Comparing samples, Hashing files, Checking hard links), folders, files and bytes found (bytes 1024-based like the CLI), and during `PARTIAL_HASH` and `FULL_HASH` a progress bar at `hashedBytes / hashBytesTotal`; a terminal status closes the stream and shows the finished row.
- **AC-7** A `QUEUED` or `RUNNING` row has a Cancel button that sends `POST /api/scans/{id}/cancel` through `ScansService.cancelScan` once and is disabled afterwards; the row shows the returned status (`CANCELLING` or `CANCELLED`) and the stream carries it to `CANCELLED`.
- **AC-8** A `COMPLETED` row shows files, bytes and duplicate sets (`groupsTotal`), a `FAILED` row shows its `failureMessage`, a `CANCELLED` row shows "Cancelled"; finished rows open no stream and have no Cancel button.
- **AC-9** When a stream fails for good (the `EventSource` is closed by the browser, for example after a server restart), the row keeps its last values and shows "Live progress lost; reload the page"; a stream that the browser reconnects shows the next event normally.
- **AC-10** Without a session (`/api/session` answers `403` or `authenticated: false`) the page shows only "Open the link printed in the console" and makes no `/api/scans` call; DFS-037's and DFS-038's existing tests still pass.

## Scenarios

AC-1 to AC-10 are Vitest tests in `server/frontend/src/app` with `HttpTestingController` for the REST calls and a fake `EventSource` through the injection token (decision 5). The live run against a real server is a done-item, checked by hand and recorded in `verification.md`.

```gherkin
Feature: Scan screen

  Background:
    Given /api/session answers authenticated true
    And GET /api/scans answers an empty list unless a scenario says otherwise

  @AC-1
  Scenario: Folders are added and removed as chips
    When the user types "C:/photos" and presses Enter
    And types "D:/backup/photos" and presses Enter
    Then two chips show "C:/photos" and "D:/backup/photos" and the field is empty
    When the user types "C:/photos" and presses Enter again
    Then there are still two chips
    When the user removes the "C:/photos" chip
    Then one chip "D:/backup/photos" remains

  @AC-1
  Scenario: Start is disabled without folders
    Then the Start button is disabled
    When the user adds "C:/photos"
    Then the Start button is enabled

  @AC-2
  Scenario: Start sends the folders with the server's defaults
    Given chips "C:/photos" and "D:/backup/photos"
    When the user presses Start
    Then one POST /api/scans is sent with body {"locations": ["C:/photos", "D:/backup/photos"]}
    When it answers 201 with a QUEUED ScanEntry with id "s-1"
    Then the chips are cleared
    And the first row of the list is scan "s-1" with folders "C:/photos" and "D:/backup/photos" and status "Queued"

  @AC-3
  Scenario Outline: The minimum size is sent in bytes
    Given chip "C:/photos"
    When the user opens Settings and sets the minimum size to <value> <unit>
    And presses Start
    Then the POST /api/scans body has "minSize": <bytes> and no "defaultExcludes"

    Examples:
      | value | unit  | bytes   |
      | 2     | MB    | 2097152 |
      | 3     | KB    | 3072    |
      | 5     | bytes | 5       |

  @AC-3
  Scenario: The settings panel starts closed and the exclusion can be turned off
    Then the Settings panel is closed
    Given chip "C:/photos"
    When the user opens Settings, unchecks "Skip .DS_Store and ._* files" and presses Start
    Then the POST /api/scans body is {"locations": ["C:/photos"], "defaultExcludes": false}

  @AC-3
  Scenario: A size below one byte is refused in the form
    Given chip "C:/photos"
    When the user sets the minimum size to 0 bytes
    Then "At least 1 byte" is shown and Start is disabled
    And no POST /api/scans is sent

  @AC-4
  Scenario: A rejected folder keeps the form
    Given chip "photos"
    When the user presses Start
    And POST /api/scans answers 400 {"code": "RELATIVE_LOCATION", "message": "Not an absolute path: photos"}
    Then "Not an absolute path: photos" is shown next to the folder field
    And the chip "photos" is still there
    And the list is still empty

  @AC-4
  Scenario: A busy server is reported
    Given chip "C:/photos"
    When the user presses Start
    And POST /api/scans answers 503 {"code": "EXECUTOR_REJECTED", "message": "..."}
    Then "The server is busy, try again" is shown
    And the list is still empty

  @AC-5 @AC-8
  Scenario: The scans of this server run are listed
    Given GET /api/scans answers, in this order:
      | id  | locations   | startedAt            | status    | files | bytes   | groupsTotal | failureMessage    |
      | s-3 | C:/a        | 2026-11-01T10:30:00Z | COMPLETED | 1200  | 5242880 | 7           |                   |
      | s-2 | C:/b, D:/c  | 2026-11-01T10:20:00Z | FAILED    | 0     | 0       | 0           | Disk not readable |
      | s-1 | C:/d        | 2026-11-01T10:10:00Z | CANCELLED | 10    | 100     | 0           |                   |
    Then the rows show s-3, s-2, s-1 in that order with their folders and start times
    And s-3 shows "Completed", 1200 files, "5.0 MB" and 7 duplicate sets
    And s-2 shows "Failed" and "Disk not readable"
    And s-1 shows "Cancelled"
    And no stream is opened and no row has a Cancel button

  @AC-6
  Scenario: A running scan shows live progress until it completes
    Given GET /api/scans answers one RUNNING scan "s-1" in phase WALK with 0 files
    Then one stream is opened on /api/scans/s-1/events
    When the stream sends progress RUNNING, phase WALK, 12 directories, 340 files, 1572864 bytes
    Then the row shows "Finding files", 12 folders, 340 files and "1.5 MB"
    When the stream sends progress RUNNING, phase FULL_HASH, hashedBytes 262144 of hashBytesTotal 1048576
    Then the row shows "Hashing files" and a progress bar at 25%
    When the stream sends progress COMPLETED with groupsTotal 3
    Then the row shows "Completed" and 3 duplicate sets
    And the stream is closed

  @AC-7
  Scenario: Cancelling a running scan
    Given GET /api/scans answers one RUNNING scan "s-1"
    When the user presses Cancel on s-1
    Then one POST /api/scans/s-1/cancel is sent and the Cancel button is disabled
    When it answers a ScanEntry with status CANCELLING
    Then the row shows "Cancelling"
    When the stream sends progress CANCELLED
    Then the row shows "Cancelled", the stream is closed and there is no Cancel button

  @AC-9
  Scenario: A lost stream is reported
    Given GET /api/scans answers one RUNNING scan "s-1" and its stream has sent 340 files
    When the stream fails and the browser closes it
    Then the row still shows 340 files
    And shows "Live progress lost; reload the page"

  @AC-9
  Scenario: A reconnected stream continues
    Given GET /api/scans answers one RUNNING scan "s-1"
    When the stream reports an error while the browser reconnects
    And then sends progress RUNNING with 500 files
    Then the row shows 500 files and no "Live progress lost" message

  @AC-10
  Scenario Outline: No scan screen without a session
    Given /api/session answers <answer>
    Then the page shows "Open the link printed in the console"
    And no scan form is shown and no /api/scans request is made

    Examples:
      | answer                      |
      | 403                         |
      | 200 authenticated false     |
```

## Scope

**Touches:**
- `server/frontend/src/app`: the scan screen component(s), `ScanEventsService` and its injection token, the page shell (`app.ts`, `app.html`, `app.css`, `app.config.ts`) and their Vitest tests.
- `server/frontend/src/styles.css`: page-wide styles, if needed.
- `CLAUDE.md`: the `server` entry says the page is the scan screen.
- `docs/design/ui.md`: item 5 done.

**Out of scope:**
- Showing a finished scan's result (`ui.md` items 6 and 7), and routing with the server's single-page-app fallback (decision 2).
- A server-side folder browser (decision 1; a later endpoint ticket).
- Any server change: if the API turns out to need one, it is reported and becomes its own ticket.
- The Angular component archetype (D7; after this ticket).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests and the Vitest tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] A live run, recorded in `verification.md`: the server on port 8080 with `ng serve` through the proxy, or the built jar: start a scan of a real folder and watch its progress to Completed with its totals; start a larger one and cancel it to Cancelled; a relative path shows its error
- [ ] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
