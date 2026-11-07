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
2. **The ETA follows the bytes rate over the last 30 seconds, in the full-hash phase only** (2026-11-07). The browser times each `ScanProgress` it receives (the stream carries no times; the clock is injectable so Vitest can drive it). In FULL_HASH, the ETA is `(hashBytesTotal - hashedBytes)` divided by the bytes hashed per second over the received updates of the last 30 s (or since the phase started, if that is shorter). It reads "estimating…" until the phase has run 3 s and some bytes have been hashed, and also while the recent rate is 0; then "about 45 s left" under a minute, "about 4 min left" (whole minutes) under an hour, "about 1 h 20 min left" from there. No ETA in the other phases. It follows a change in disk speed within half a minute, in a few lines testable with a fake clock. Known limit: a tail of many small files makes it optimistic near the end (what DFS-025 fixed in the CLI); the counts beside it stay exact. Not the average since the phase started (slow to follow speed changes) and not a port of the CLI's DFS-025 logic (three times the code, rules in Java and TypeScript that can drift apart); a later ticket can port it if the tail bothers.

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

- Archetype (`component` likely), gates and autorun.
