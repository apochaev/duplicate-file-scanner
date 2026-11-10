---
id: DFS-063
title: Redesign the scan progress row as one four-section bar
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: medium
lane: frontend
depends_on: [DFS-064]
touches: []
blocked_reason:
created: 2026-11-09
updated: 2026-11-09
completed:
---

# DFS-063: Redesign the scan progress row as one four-section bar

## Story

As the user watching a long scan in the browser
I want one progress bar with a fixed section per phase, an overall percentage that never jumps back, and a time left once hashing starts
So that I can see which phases are done, how far the whole scan has got, and how long to wait

## Context

Design: https://claude.ai/artifact/EuSd18FxAYTobP38cx3JF3 (Claude Design, 2026-11-08; `project/Main.dc.html` rules, `project/Row.dc.html` row markup and per-state data). It is a mock-up; the page is rebuilt with Angular Material in `server/frontend/src/app/scans/`. It replaces DFS-057's per-phase lines. DFS-058 is cancelled; its finished-phase totals come from DFS-064.

Rules the canvas sets:

- **One bar, four fixed sections:** walk 12%, compare 18%, hash 60%, hard links 10%. Sections never move or resize.
- **Overall %** = finished sections plus the running section times its progress (hashing measured in bytes). It shows "—" during the walk, rounds down, and stays at 99% or lower until hard links finish; 100% appears only on a COMPLETED scan.
- **Time left** from hashing on only: "estimating…" for the first 30 s, then "about N min left" from the last 30 s rate; "finishing…" when no bytes are left (hard links: groups left over groups per second).
- **Section states readable without colour:** done (solid, check), running (outlined, fill, bold, "now"), total unknown (pale moving stripes), finishing (full, moving stripes), not started (dashed, grey), stopped or failed (grey hatch; failed adds a red outline and the error message).
- Each finished section keeps a short totals line (replaces DFS-057's "Found:" line).
- A COMPLETED row collapses to one line: a thin strip, the narrowing totals, the duration and the Open results link.
- Cancel stays the only control on a running row. Counts without thousands separators ("48210 files", "307.3 GB"). Stacked layout at phone width. Reduced-motion users get no stripe animation.

The current row is `scan-row.ts`/`.html`/`.css` with helpers `scan-phase.ts`, `scan-eta.ts` and `scan-format.ts` (DFS-041, DFS-057, with specs). The ETA helper already uses a 30 s rate and an injectable clock.

**Where the numbers come from** (DFS-064, `ScanProgress`): each finished section's totals line reads `phases` (walk folders, files, bytes; compare's "files in" and "→ candidates" from the compare and hash entries; hash files and bytes; hard links' `groups`), and the last line's "→ N duplicate groups" reads the flat `groupsTotal`, which is the sets count on a COMPLETED scan. "Finishing" is derived on the page: `phase` FULL_HASH with `hashedBytes` equal to `hashBytesTotal` (no server field, DFS-064 decision 1). The scan's duration is `finishedAt` minus `startedAt` on the entry. Row sample text in the canvas: "38886 files · 902.9 GB / found in 4120 folders", "29594 compared / → 13845 candidates", "13845 hashed · 17.1 GB / → 3104 matching groups", "3104 groups checked / → 2987 duplicate groups"; step line "Step 3 of 4 · Hashing files"; failure text "Scan failed while hashing. <message>".

## Decisions

1. **QUEUED and CANCELLING look the way the scan is, not the nearest canvas stage** (2026-11-09). The canvas has eight stages and neither status. QUEUED: all four sections "not started" (dashed), overall "—", the line "Waiting to start", Cancel enabled. CANCELLING: the running section keeps its look, the overall percentage freezes at the last value shown, the status word "Cancelling…" shows and Cancel is disabled; it becomes the "stopped" look (grey hatch, "when stopped") only when the status turns CANCELLED. Rejected: showing CANCELLING as already stopped (claims it while the scan still works) and QUEUED as a striped walk (nothing runs yet).

2. **The calculations are pure helpers with their own specs** (2026-11-09). One new helper file in `scans/` holds the overall percentage and the four section states, taking a `ScanProgress` and returning plain values; `scan-eta.ts` gains the hard-link and finishing cases; `scan-format.ts` gains the totals lines. The row component only renders what they return. The canvas's worked cases are test cases (compare 2954 of 29594 files is 13%, hashing 5.1 of 17.1 GB is 47%, nothing left to read is 90%, hard links 1242 of 3104 groups is 94%). The 30 s rate logic stays in `scan-eta.ts` only; the clock stays injectable (`CLOCK` token), so no test waits on real time. Rejected: calculations in the component's signals (tested through the DOM, a failing rule is hard to find) and one helper replacing `scan-eta.ts` and `scan-phase.ts` (rewrites tested DFS-057 code for no new behaviour).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-063.AC-<n>`.

- **AC-1** A QUEUED scan's row shows the four sections as not started, the overall percentage as "—", the text "Waiting to start" and an enabled Cancel button.
- **AC-2** A CANCELLING scan's row keeps its running section's look, holds the overall percentage at the last value it showed, shows "Cancelling…" and a disabled Cancel button; once the scan is CANCELLED the section shows as stopped.
- **AC-3** The overall percentage is the finished sections' weights (walk 12, compare 18, hash 60, hard links 10) plus the running section's weight times its progress, rounded down: compare by files done over files in, hashing by bytes done over bytes in, hard links by groups done over groups. It reads "—" during the walk, is at most 99 until the scan is COMPLETED, and is 100 only then.

## Scenarios

```gherkin
Feature: Scan progress row

  @AC-1
  Scenario: A queued scan
    Given a scan entry with status QUEUED and no phase
    When the row renders
    Then it shows four sections labelled not started, the overall percentage "—" and the text "Waiting to start"
    And the Cancel button is enabled

  @AC-2
  Scenario: A scan being cancelled keeps its last percentage
    Given a RUNNING scan in the hashing phase showing 47%
    When its status becomes CANCELLING and the bytes hashed grow further
    Then the row still shows 47%, the text "Cancelling…" and a disabled Cancel button
    And the hashing section is still shown as running

  @AC-2
  Scenario: A cancelled scan shows its stopped section
    Given the scan above
    When its status becomes CANCELLED
    Then the hashing section is shown as stopped and the percentage is labelled "when stopped"
```

  @AC-3
  Scenario Outline: The overall percentage follows the canvas
    Given a RUNNING scan <state>
    When the row renders
    Then the overall percentage reads "<overall>"

    Examples:
      | state                                                          | overall |
      | in the walk, 38886 files found so far                          | —       |
      | comparing, 2954 of 29594 files done                            | 13%     |
      | hashing, 5.1 of 17.1 GB read (6210 of 13845 files)             | 47%     |
      | hashing, 17.1 of 17.1 GB read, nothing left                    | 90%     |
      | checking hard links, 1242 of 3104 groups                       | 94%     |
      | checking hard links, 3104 of 3104 groups, not yet COMPLETED    | 99%     |

  @AC-3
  Scenario: Only a completed scan reads 100%
    Given the scan above
    When its status becomes COMPLETED
    Then the overall percentage reads "100%"

  @AC-3
  Scenario: The percentage is the same on both sides of a phase change
    Given a scan comparing with 29594 of 29594 files done, reading "30%"
    When the hashing phase starts and its totals are still 0
    Then the overall percentage still reads "30%" and is not "NaN%"

## Scope

**Touches:**
- <to be refined; likely `server/frontend/src/app/scans`, `CLAUDE.md`, `docs/design/ui.md`>

**Out of scope:**
- The server's progress fields (DFS-064).
- Plan controls and the results screen.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the Vitest tests)
- [ ] No test this ticket adds waits on real time (the clock is faked)
- [ ] <ticket-specific check, including a by-hand run on a real scan compared with the canvas>

## Open questions

- Save the canvas as screenshots under `docs/design/mockups/` (as DFS-060 did), so the ticket does not depend on an external link?
- The overall-percentage and time-left calculations as pure helpers with their own specs (the canvas's worked examples, such as 12 + 0.10 × 18 = 13%, become test cases).
- Walk-phase section: striped with "—", plus growing counts; what the totals line shows once the walk ends.
- CANCELLED and FAILED details beyond decision 1: follow the canvas (stopped section hatched, failed adds a red outline and the message); to be written as criteria.
- Phone-width layout details; one frame per state or generic rules.
- Archetype (`component`), gates, and whether the by-hand run needs the design-gate.
