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

## Decisions

1. **One line per phase, like the CLI, with the found totals kept underneath** (2026-11-07). While a scan is not finished, its row shows, by `phase`:
   - **WALK:** "Finding files: 1,204 folders · 48,210 files · 310 GB found"; the bar is indeterminate (no total exists yet).
   - **PARTIAL_HASH:** "Comparing samples: 3,100 of 9,400 files · 6,300 left · 33%"; the bar and the percentage follow files (`hashedFiles / hashFilesTotal`). Sample bytes are not shown.
   - **FULL_HASH:** "Hashing files: 1,204 of 5,880 files · 4,676 left · 3.1 GB of 12.4 GB · 9.3 GB left · 26%" plus the ETA (decision 2); the bar and the percentage follow bytes (`hashedBytes / hashBytesTotal`).
   - **HARD_LINKS:** "Checking hard links: group 40 of 310" (`groupsDone` of `groupsTotal`); the bar follows groups.
   - In every phase after WALK, a second line keeps "Found: 48,210 files · 310 GB" (`files`, `bytes`).
   Each phase shows the numbers that mean something in it, and the found / sampled / hashed totals show how the candidates narrowed. Numbers use the page's existing `plural` and `formatBytes` (`scan-format.ts`). Not one generic line for every hashing phase (shows sample bytes, nothing for walk or hard links, no "left") and not a grid of Done / Total / Left (empty cells in two phases, harder to read at a glance).

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

- The ETA: computed in the browser from which rate (bytes, recent window as DFS-025), when it is shown, and what it says before it is known.
- Archetype (`component` likely), gates and autorun.
