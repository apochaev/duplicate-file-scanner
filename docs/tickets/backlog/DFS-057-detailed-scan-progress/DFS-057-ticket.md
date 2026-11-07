---
id: DFS-057
title: Show the current phase's counts and ETA on the scan screen
status: ready
type: feature
scope: [build]
archetype: component
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [done]
autorun: true
priority: high
lane: frontend
depends_on: []
touches:
  - server/frontend/src/app/scans
  - CLAUDE.md
  - docs/design/ui.md
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
   - **WALK:** "Finding files: 1204 folders · 48210 files · 310 GB found"; the bar is indeterminate (no total exists yet).
   - **PARTIAL_HASH:** "Comparing samples: 3100 of 9400 files · 6300 left · 33%"; the bar and the percentage follow files (`hashedFiles / hashFilesTotal`). Sample bytes are not shown.
   - **FULL_HASH:** "Hashing files: 1204 of 5880 files · 4676 left · 3.1 GB of 12.4 GB · 9.3 GB left · 26%" plus the ETA (decision 2); the bar and the percentage follow bytes (`hashedBytes / hashBytesTotal`).
   - **HARD_LINKS:** "Checking hard links: group 40 of 310" (`groupsDone` of `groupsTotal`); the bar follows groups.
   - In every phase after WALK, a second line keeps "Found: 48210 files · 310 GB" (`files`, `bytes`).
   Each phase shows the numbers that mean something in it, and the found / sampled / hashed totals show how the candidates narrowed. Numbers use the page's existing `plural` and `formatBytes` (`scan-format.ts`). Not one generic line for every hashing phase (shows sample bytes, nothing for walk or hard links, no "left") and not a grid of Done / Total / Left (empty cells in two phases, harder to read at a glance).
2. **The ETA follows the bytes rate over the last 30 seconds, in the full-hash phase only** (2026-11-07). The browser times each `ScanProgress` it receives (the stream carries no times; the clock is injectable so Vitest can drive it). In FULL_HASH, the ETA is `(hashBytesTotal - hashedBytes)` divided by the bytes hashed per second over the received updates of the last 30 s (or since the phase started, if that is shorter). It reads "estimating…" until the phase has run 3 s and some bytes have been hashed, and also while the recent rate is 0; then "about 45 s left" under a minute, "about 4 min left" (whole minutes) under an hour, "about 1 h 20 min left" from there. No ETA in the other phases. It follows a change in disk speed within half a minute, in a few lines testable with a fake clock. Known limit: a tail of many small files makes it optimistic near the end (what DFS-025 fixed in the CLI); the counts beside it stay exact. Not the average since the phase started (slow to follow speed changes) and not a port of the CLI's DFS-025 logic (three times the code, rules in Java and TypeScript that can drift apart); a later ticket can port it if the tail bothers.
3. **Archetype `component`, gates `[done]`** (2026-11-07). A display-only change in `server/frontend/src/app/scans` (the scan row, an ETA helper with an injectable clock, Vitest specs): the `component` archetype (CCW-020), as DFS-049 and DFS-050. Straight to the product-owner review: decisions 1 and 2 fix the wording and the ETA rule, nothing reads or moves files, and the review on a real scan judges it best. Not `[design, done]` (a stop with little left to decide).

## Acceptance criteria

The full ID of each criterion is `DFS-057.AC-<n>`.

- **AC-1** In WALK, an unfinished scan's row shows "Finding files: <directories> folders · <files> files · <bytes> found" and an indeterminate progress bar.
- **AC-2** In PARTIAL_HASH, the row shows "Comparing samples: <hashedFiles> of <hashFilesTotal> files · <left> left · <n>%", where left is `hashFilesTotal - hashedFiles` and the percentage and a determinate bar follow files; no byte count of this phase is shown.
- **AC-3** In FULL_HASH, the row shows "Hashing files: <hashedFiles> of <hashFilesTotal> files · <left> left · <hashedBytes> of <hashBytesTotal> · <bytes left> left · <n>%" and the ETA (AC-6), the percentage and a determinate bar following bytes.
- **AC-4** In HARD_LINKS, the row shows "Checking hard links: group <groupsDone> of <groupsTotal>" with a determinate bar following groups.
- **AC-5** In every phase after WALK, a second line shows "Found: <files> files · <bytes>"; a total of 0 never divides by zero (the percentage reads 0% and the bar is empty).
- **AC-6** In FULL_HASH, the ETA is the bytes left divided by the bytes rate over the updates received in the last 30 s (or since the phase started, if shorter), read from an injectable clock; it reads "estimating…" until the phase has run 3 s with some bytes hashed and while the recent rate is 0, then "about <s> s left" under a minute, "about <m> min left" under an hour, "about <h> h <m> min left" from there, each rounded to the nearest unit shown; a new phase starts its window afresh.
- **AC-7** A COMPLETED, CANCELLED or FAILED scan's row is unchanged from DFS-041 and DFS-049: totals and "Open results", nothing, or the failure message; no phase line, bar or ETA.
- **AC-8** Counts are printed by the page's existing `plural` (no thousands separators, "1 file") and sizes by `formatBytes` (one decimal, binary units).

## Scenarios

```gherkin
Feature: Detailed scan progress

  Background:
    Given the scan screen lists one RUNNING scan whose progress comes from a fake event stream
    And the page's clock is a fake clock

  @AC-1 @AC-8
  Scenario: Walking shows what has been found
    When the stream sends phase WALK with 1204 directories, 48210 files and 330 000 000 000 bytes
    Then the row shows "Finding files: 1204 folders · 48210 files · 307.3 GB found"
    And the progress bar is indeterminate

  @AC-2
  Scenario: Comparing samples shows files done and left
    When the stream sends phase PARTIAL_HASH with hashedFiles 3100, hashFilesTotal 9400, hashedBytes 400 000 000, hashBytesTotal 1 200 000 000
    Then the row shows "Comparing samples: 3100 of 9400 files · 6300 left · 33%"
    And the bar's value is about 33
    And the row shows no byte count for this phase

  @AC-3 @AC-8
  Scenario: Hashing shows files and bytes done and left
    When the stream sends phase FULL_HASH with hashedFiles 1204, hashFilesTotal 5880, hashedBytes 3 328 599 654, hashBytesTotal 13 314 398 618
    Then the row shows "Hashing files: 1204 of 5880 files · 4676 left · 3.1 GB of 12.4 GB · 9.3 GB left · 25%"
    And the bar's value is 25

  @AC-4
  Scenario: Hard links show groups
    When the stream sends phase HARD_LINKS with groupsDone 40 and groupsTotal 310
    Then the row shows "Checking hard links: group 40 of 310"
    And the bar's value is about 12.9

  @AC-5
  Scenario: The found totals stay in view
    When the stream sends phase FULL_HASH with files 48210 and bytes 330 000 000 000
    Then a second line shows "Found: 48210 files · 307.3 GB"
    And in WALK no "Found:" line is shown

  @AC-5
  Scenario: Empty totals do not break the line
    When the stream sends phase PARTIAL_HASH with hashedFiles 0 and hashFilesTotal 0
    Then the row shows "Comparing samples: 0 of 0 files · 0 left · 0%" and the bar's value is 0

  @AC-6
  Scenario: The ETA waits, then follows the recent rate
    Given phase FULL_HASH started at 0 s with hashBytesTotal 6 000 000 000
    When the stream sends hashedBytes 100 000 000 at 2 s
    Then the ETA reads "estimating…"
    When it sends hashedBytes 300 000 000 at 4 s
    Then the ETA reads "about 1 min left" (5 700 000 000 bytes left at 75 000 000 bytes per second: 76 s)

  @AC-6
  Scenario: Only the last 30 seconds count
    Given phase FULL_HASH started at 0 s with hashBytesTotal 6 000 000 000
    And the stream sent hashedBytes 300 000 000 at 4 s and 1 000 000 000 at 30 s
    When it sends hashedBytes 4 000 000 000 at 60 s
    Then the rate is 100 000 000 bytes per second (from the 30 s and 60 s updates only)
    And the ETA reads "about 20 s left"

  @AC-6
  Scenario Outline: The ETA is rounded to a readable time
    When the ETA works out at <seconds> seconds
    Then it reads "<text>"

    Examples:
      | seconds | text                    |
      | 45      | about 45 s left         |
      | 250     | about 4 min left        |
      | 4800    | about 1 h 20 min left   |

  @AC-6
  Scenario: A stalled rate and a new phase
    Given FULL_HASH has shown "about 4 min left"
    When no bytes are hashed for 30 s of updates
    Then the ETA reads "estimating…"
    When the scan moves to HARD_LINKS
    Then no ETA is shown

  @AC-7
  Scenario: A finished scan shows its totals as before
    When the stream ends with status COMPLETED, 48210 files, 330 000 000 000 bytes and 310 sets
    Then the row shows "48210 files · 307.3 GB · 310 duplicate sets" and "Open results"
    And no phase line, progress bar or ETA
```

## Scope

**Touches:**
- `server/frontend/src/app/scans`: the scan row's template and logic, the phase lines, the ETA helper and its clock token, and their Vitest specs (`scan-row.spec.ts`, new spec files beside it).
- `CLAUDE.md`: the scan screen's sentence in the `server` entry.
- `docs/design/ui.md`: U2's description of a running scan's progress.

**Out of scope:**
- Finished phases' totals (DFS-058).
- Any change to `ScanProgress`, the server or the spec.
- The CLI's progress, and porting its DFS-025 ETA rules (decision 2).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] Each test's name given to `it` starts with its criterion ID (`DFS-057.AC-n`)
- [ ] `./gradlew build` is green (includes the ArchUnit tests and the Vitest tests)
- [ ] Every component-archetype checklist item is addressed or marked not applicable with a reason
- [ ] No test this ticket adds waits on real time (the clock is faked)
- [ ] `CLAUDE.md`'s scan screen sentence and `docs/design/ui.md` U2 describe the phase lines and the ETA (the verifier reads them)
- [ ] Started by hand once on Windows: a real folder of several GB scanned in the browser, each phase's line seen, the ETA seen to settle; the browser console free of errors
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
