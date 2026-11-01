---
id: DFS-041
title: Add the scan screen to start, follow and cancel scans
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-040]
touches: []
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

`docs/design/ui.md` item 5, second half (split 2026-11-01; DFS-040 is the server API it uses). U2: a start screen takes folders and settings, shows live progress from the scanner's events, then opens the result (opening the result arrives with `ui.md` items 6 and 7). D3: the start screen lists this session's scans (running or finished) with cancel. The page calls the API only through the client generated from `server/frontend/openapi.json` (DFS-038); the progress stream's client side is whatever DFS-040 decides for SSE.

## Acceptance criteria

The full ID of each criterion is `DFS-041.AC-<n>`.

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
- Showing a finished scan's result (`ui.md` items 6 and 7).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- How the user picks folders: typed absolute paths, or a server-side folder browser (which would read the disk outside any scan).
- Layout: one page with the form, the list and the progress, or a route per scan (adds `@angular/router` back, DFS-037 left it out).
- Which settings the form shows, from what DFS-040 exposes.
- Archetype: `none`, or D7's Angular component archetype if it exists by then.
