---
id: DFS-057
title: Show the current phase's counts and ETA on the scan screen
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# DFS-057: Show the current phase's counts and ETA on the scan screen

## Story

As the user watching a running scan in the browser
I want to see, for the current phase, how many files and bytes are done, how many are left, the percentage and an estimated time left
So that I know how far a long scan has got and how long to wait, as the CLI's progress line tells me

## Context

Asked by the product owner on 2026-11-07 while running a real scan: "progress bar does not give a lot of info; looking for each phase total files, remaining to process files".

- **The data is already sent:** `GET /api/scans/{id}/events` streams `ScanProgress` (DFS-040 decision 3; `us.pochaev.dfs.server.scan`) at most every 250 ms: `phase` (WALK, PARTIAL_HASH, FULL_HASH, HARD_LINKS), `directories`, `files`, `bytes` (walk counts), `hashedFiles` / `hashFilesTotal` and `hashedBytes` / `hashBytesTotal` (the current hashing phase), `groupsDone` / `groupsTotal` (the current phase's groups). No API change is needed.
- **What the page shows today** (DFS-041, `server/frontend/src/app/scans/scan-row.html`): the phase label, the walk counts and, while hashing, a bare `mat-progress-bar` from `hashedBytes / hashBytesTotal`.
- **The CLI's model** (DFS-024, DFS-025; `cli/.../ProgressDisplay.java`): folders, files and bytes found while walking; files done while sampling; files, bytes, percentage and ETA while hashing, the ETA following the recent rate.
- A list of finished phases with their totals needs the server to keep them: DFS-058, decided after this ticket is seen on a real scan.

## Acceptance criteria

The full ID of each criterion is `DFS-057.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Detailed scan progress

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `server/frontend/src/app/scans`>

**Out of scope:**
- Finished phases' totals (DFS-058).
- Any change to `ScanProgress` or the server.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- What the line shows in each phase (walk, partial hash, full hash, hard links), and in what words.
- The ETA: computed in the browser from which rate (bytes, recent window as DFS-025), when it is shown, and what it says before it is known.
- Archetype (`component` likely), gates and autorun.
