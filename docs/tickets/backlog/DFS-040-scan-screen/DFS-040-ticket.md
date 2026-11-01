---
id: DFS-040
title: Start, follow and cancel scans from the UI
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-038]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-040: Start, follow and cancel scans from the UI

## Story

As the user of the local DFS server
I want to pick folders, start a scan, watch its progress live and cancel it, and see this session's scans listed
So that I can scan from the browser instead of the CLI, as the first step toward exploring and cleaning up duplicates there

## Context

`docs/design/ui.md` item 5 of the suggested ticket order: "Scan screen: start, progress over SSE, list, cancel (U2, D3)". The first server endpoints that do real work, after DFS-035 (skeleton), DFS-036 (security), DFS-037 (Angular) and DFS-038 (generated OpenAPI client).

- **U2:** a start screen takes folders and settings, shows live progress from the scanner's `ScanEvent`s, then opens the result. It goes through the core ports `StartScan` → `GetScanStatus`/`GetScanResult`. Results live in the in-memory `ScanRepository` and are lost on restart.
- **D2:** JSON REST under `/api`; Server-Sent Events for scan progress. The spec and client come from DFS-038's chain (`OpenApiSpecTest`, `:server:updateOpenApi`, `ng-openapi-gen`).
- **D3:** several scans can exist in memory at once; the start screen lists this session's scans (running or finished) with cancel.
- **D1, DFS-035 decision 1:** this ticket switches `server` from `file-scanner` alone to `implementation(project(":scanner-spring"))`, whose `ScannerAutoConfiguration` provides `StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult` and the `scanExecutor` bean.
- **The core today:** `StartScan.start(locations, config[, listener])` returns `Result<ScanId>` at once and validates the locations (`NO_LOCATIONS`, `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`, `LOCATION_UNREADABLE`); the listener is called on the scanning thread with `ScanEvent`s (`PhaseStarted`, `DirectoryWalked`, `FileWalked`, `HashProgress`, `GroupDone`, `Skipped`). `GetScanStatus` returns a `ScanSnapshot` (status, phase, files walked, groups done/total, failure). `CancelScan` moves QUEUED → CANCELLED and RUNNING → CANCELLING → CANCELLED. There is no port that lists scans.
- **Security (DFS-036):** every `/api/**` call needs the session cookie; state-changing requests need a matching `Origin`. DFS-037 decision 4 left the dev proxy's `Origin` handling to the first ticket with a `POST`, which this is.

## Acceptance criteria

The full ID of each criterion is `DFS-040.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Scan screen

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined>

**Out of scope:**
- Showing a finished scan's result (overlaps, sets, tree: `ui.md` items 6 and 7).
- Saving and reopening scans (U2, a later ticket behind `ScanRepository`).
- The path-inside-roots rule (U10): the folders a user scans define the roots, so this ticket has no path to check against them; the first endpoint that reads a path inside a scan carries it.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- One ticket, or split the server API (endpoints, SSE) from the Angular screen?
- How the user picks folders: typed absolute paths, or a server-side folder browser (which would read the disk outside any scan).
- Listing this session's scans: server-side bookkeeping in `server`, or a `ListScans` port in the core (a request to the backend lane).
- SSE: one stream per scan or one for all; which events (every `ScanEvent`, or throttled snapshots); how a dropped connection resumes; how the generated client copes (DFS-038: Promise-based services, no SSE in the spec).
- Which `ScanConfig` settings the screen exposes (minimum size, default excludes) and their defaults.
- Origin handling through the dev proxy for `POST` (DFS-037 decision 4).
- Archetype: `none` again, or does this ticket wait for D7's endpoint archetype (drafted after the first data endpoint, which this is)?
- Gates, if a pipeline archetype.
