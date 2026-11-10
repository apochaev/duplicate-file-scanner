---
id: DFS-063
title: Redesign the scan progress row as one four-section bar
status: draft
type: feature
scope: [build]
archetype: component
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
priority: medium
lane: frontend
depends_on: [DFS-064]
touches:
  - server/frontend/src/app/scans
  - docs/design/ui.md
  - CLAUDE.md
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
- Cancel stays the only control on a running row. Counts without thousands separators ("48210 files", "307.3 GB"). The canvas's phone layout is not built (decision 6). Reduced-motion users get no stripe animation.

The current row is `scan-row.ts`/`.html`/`.css` with helpers `scan-phase.ts`, `scan-eta.ts` and `scan-format.ts` (DFS-041, DFS-057, with specs). The ETA helper already uses a 30 s rate and an injectable clock.

**Where the numbers come from** (DFS-064, `ScanProgress`): each finished section's totals line reads `phases` (walk folders, files, bytes; compare's "files in" and "→ candidates" from the compare and hash entries; hash files and bytes; hard links' `groups`), and the last line's "→ N duplicate groups" reads the flat `groupsTotal`, which is the sets count on a COMPLETED scan. "Finishing" is derived on the page: `phase` FULL_HASH with `hashedBytes` equal to `hashBytesTotal` (no server field, DFS-064 decision 1). The scan's duration is `finishedAt` minus `startedAt` on the entry. Row sample text in the canvas: "38886 files · 902.9 GB / found in 4120 folders", "29594 compared / → 13845 candidates", "13845 hashed · 17.1 GB / → 3104 matching groups", "3104 groups checked / → 2987 duplicate groups"; step line "Step 3 of 4 · Hashing files"; failure text "Scan failed while hashing. <message>".

## Decisions

1. **QUEUED and CANCELLING look the way the scan is, not the nearest canvas stage** (2026-11-09). The canvas has eight stages and neither status. QUEUED: all four sections "not started" (dashed), overall "—", the line "Waiting to start", Cancel enabled. CANCELLING: the running section keeps its look, the overall percentage freezes at the last value shown, the status word "Cancelling…" shows and Cancel is disabled; it becomes the "stopped" look (grey hatch, "when stopped") only when the status turns CANCELLED. Rejected: showing CANCELLING as already stopped (claims it while the scan still works) and QUEUED as a striped walk (nothing runs yet).

2. **The calculations are pure helpers with their own specs** (2026-11-09). One new helper file in `scans/` holds the overall percentage and the four section states, taking a `ScanProgress` and returning plain values; `scan-eta.ts` gains the hard-link and finishing cases; `scan-format.ts` gains the totals lines. The row component only renders what they return. The canvas's worked cases are test cases (compare 2954 of 29594 files is 13%, hashing 5.1 of 17.1 GB is 47%, nothing left to read is 90%, hard links 1242 of 3104 groups is 94%). The 30 s rate logic stays in `scan-eta.ts` only; the clock stays injectable (`CLOCK` token), so no test waits on real time. Rejected: calculations in the component's signals (tested through the DOM, a failing rule is hard to find) and one helper replacing `scan-eta.ts` and `scan-phase.ts` (rewrites tested DFS-057 code for no new behaviour).

3. **Time left follows the canvas, not DFS-057's 3 s warm-up** (2026-11-09). "estimating…" for the first 30 s of the phase (DFS-057 used 3 s; `WARM_UP_MS` in `scan-eta.ts` becomes 30 s, and the `DFS-057.AC-6` cases in `scan-eta.spec.ts` that assume 3 s change with it, which is why the spec is in `touches`). The rate window (the last 30 s) and the wording (`formatEta`: "about 45 s left", "about 4 min left", "about 1 h 20 min left") stay. Hashing: bytes left over bytes per second. Hard links: groups left over groups per second, same window, same 30 s warm-up counted from that phase's start, same words. "finishing…" replaces the time whenever the hashing phase has no bytes left to read, even inside the first 30 s. Walk and compare show no time left. Rejected: keeping 3 s (drops the canvas rule; the earliest estimate is the least accurate).

4. **Section text per state** (2026-11-09). The canvas's lines, plus one invented: the running walk uses the shape of its finished line, so a section never changes layout when it completes. Walk running "N files · SIZE found" over "in N folders"; compare "A of B files" over "C left"; hash "A of B GB · C GB left" over "X of Y files · Z left", finishing "B of B GB · nothing left to read" over "Y of Y files · finishing…"; hard links "A of B groups" over "C left"; not started "not started" over "total known after step N" (N the step before it; the walk has none); stopped or failed the running section's counts ("5.1 of 17.1 GB read", "6210 of 13845 files"); sections after a stopped one "not run". Done sections show their totals line (AC-8). Sizes use `scan-format.ts`; no thousands separators. Rejected: a one-line walk text with folders first (the row would change height when the walk ends).

5. **Only a COMPLETED scan collapses** (2026-11-09). The collapsed row is the canvas's one line: a tick, the scan's folders (as the DFS-057 row shows them) with "Completed · started HH:MM", a thin four-part strip over the narrowing-totals line "N files · SIZE found → N compared → N hashed · SIZE → N duplicate groups", "100%" with "of whole scan", "took <duration>" over "finished HH:MM" (from `finishedAt`, DFS-064), and the "Open results" link. The duration is a pure helper in `scan-format.ts`: under a minute "42 s", under an hour "14 min 38 s", from an hour "1 h 05 min" (whole minutes, minutes zero-padded). CANCELLED and FAILED rows stay expanded, since the stopped section's counts and the failure message are what they show. Rejected: collapsing them too (hides the message or squeezes it into one line) and never collapsing (a long list of finished scans gets tall, and the canvas collapses it).

6. **The canvas source is kept in the repo; phone and motion are one criterion each** (2026-11-09). `docs/design/mockups/scan-progress-row-2026-11-08/` holds the canvas files `Main.dc.html` (rules) and `Row.dc.html` (row markup and per-state data), committed with this refinement, so the design does not depend on the external link (the DFS-060 mock-ups are PNGs; these are the design's own source, which also carries the numbers). Under `prefers-reduced-motion` no stripe animates; the stylesheet is tested for no animation under the media query, and the real look is checked in the by-hand run against the canvas. **The phone layout is dropped:** the page is for desktop use (user, 2026-11-09), so AC-14 is retired and the canvas's phone frames are not built. Rejected: no kept copy of the canvas (the by-hand comparison loses its reference).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-063.AC-<n>`.

- **AC-1** A QUEUED scan's row shows the four sections as not started, the overall percentage as "—", the text "Waiting to start" and an enabled Cancel button.
- **AC-2** A CANCELLING scan's row keeps its running section's look, holds the overall percentage at the last value it showed, shows "Cancelling…" and a disabled Cancel button; once the scan is CANCELLED the section shows as stopped.
- **AC-3** The overall percentage is the finished sections' weights (walk 12, compare 18, hash 60, hard links 10) plus the running section's weight times its progress, rounded down: compare by files done over files in, hashing by bytes done over bytes in, hard links by groups done over groups. It reads "—" during the walk, is at most 99 until the scan is COMPLETED, and is 100 only then.
- **AC-4** In the hashing phase the time left reads "estimating…" until the phase has run 30 s with bytes hashed and while the rate over the last 30 s is 0, then "about N min left" (words as `formatEta`) from the bytes left over that rate; the walk and compare phases show no time left.
- **AC-5** In the hard-link phase the time left is the groups left over the groups-per-second rate of the last 30 s, with the same words and the same "estimating…" for the first 30 s of that phase.
- **AC-6** When the hashing phase has no bytes left to read, the time left reads "finishing…" instead of a time, also inside the first 30 s of the phase.
- **AC-7** A running section shows its two lines: walk "N files · SIZE found" over "in N folders"; compare "A of B files" over "C left"; hashing "A of B <size> · C <size> left" over "X of Y files · Z left", or, when no bytes are left, "B of B <size> · nothing left to read" over "Y of Y files · finishing…"; hard links "A of B groups" over "C left".
- **AC-8** A finished section shows its totals from `phases`: walk "N files · SIZE" over "found in N folders"; compare "C compared" over "→ N candidates" (the hash entry's files); hashing "N hashed · SIZE" over "→ N matching groups" (the hard-link entry's groups); hard links "N groups checked" over "→ N duplicate groups" (`groupsTotal` of the COMPLETED scan). A "→" line appears once its number is known.
- **AC-9** A section not yet started shows "not started" over "total known after step N" (N is the step before it; the walk has none); after a stopped section the later ones show "not run" over nothing; a stopped or failed section shows the counts it reached ("5.1 of 17.1 GB read" over "6210 of 13845 files").
- **AC-10** Every section carries its state as text, readable without colour: done with a check, running with "now", not started, stopped with "stopped", failed with "failed"; a section whose total is unknown (the walk) has no percentage.
- **AC-11** A FAILED scan's failing section is tagged "failed" and its row shows "Scan failed while <activity>. <failureMessage>", with the activity "finding files", "comparing samples", "hashing files" or "checking hard links" for the phase the scan was in.
- **AC-12** A COMPLETED scan's row collapses to one line: the scan's folders with "Completed · started HH:MM", a four-part strip, "N files · SIZE found → N compared → N hashed · SIZE → N duplicate groups", "100%", "took <duration>" over "finished HH:MM", and the "Open results" link to the scan's results; a CANCELLED or FAILED row does not collapse.
- **AC-13** A duration reads "42 s" under a minute, "14 min 38 s" under an hour and "1 h 05 min" from an hour, rounded down to the second below an hour and to the minute from there.
- **AC-14 (retired)** Below 600 px of width the row stacks its four sections one per line with the strip above them. Retired 2026-11-09: the page is for desktop use, so the phone layout is not built.
- **AC-15** With `prefers-reduced-motion: reduce` the striped sections (walk running, hashing finishing) show their stripes without animation; otherwise the stripes move.

## Scenarios

```gherkin
Feature: Scan progress row

  @AC-1
  Scenario: A queued scan
    Given a scan entry with status QUEUED and no phase
    When the row renders
    Then it shows four sections labelled not started, the overall percentage "—" and the text "Waiting to start"
    And the Cancel button is enabled and is the row's only button

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

  @AC-4
  Scenario: Hashing waits 30 s, then follows the rate
    Given a hashing scan with 2600 MB to read and a fake clock
    And the phase's first update at 0 s has 0 MB read
    When an update at 29 s has 290 MB read
    Then the time left reads "estimating…"
    When an update at 30 s has 300 MB read
    Then it reads "about 4 min left"

  @AC-4
  Scenario: A stalled rate keeps estimating
    Given a hashing scan 90 s in, 500 MB read at 60 s and still 500 MB at 90 s
    Then the time left reads "estimating…"

  @AC-4
  Scenario: Walk and compare show no time left
    Given a scan in the walk, then one comparing samples
    Then neither row shows a time left

  @AC-5
  Scenario: Hard links follow the groups rate
    Given a hard-link phase with 3000 groups and a fake clock, 0 done at its start
    When an update at 29 s has 580 done
    Then the time left reads "estimating…"
    When an update at 30 s has 600 done
    Then it reads "about 2 min left"

  @AC-6
  Scenario: Nothing left to read is finishing
    Given a hashing scan with 17.1 of 17.1 GB read after 20 min
    Then the time left reads "finishing…"

  @AC-6
  Scenario: A tiny scan finishes inside the warm-up
    Given a hashing scan with 5 of 5 MB read 4 s after the phase started
    Then the time left reads "finishing…", not "estimating…"

  @AC-7
  Scenario: The running walk
    Given a RUNNING scan in the walk with 38886 files, 902.9 GB and 4120 folders found
    Then the walk section reads "38886 files · 902.9 GB found" over "in 4120 folders"

  @AC-7
  Scenario Outline: A running section shows its counts
    Given a RUNNING scan <state>
    Then the running section reads "<line 1>" over "<line 2>"

    Examples:
      | state                                              | line 1                                   | line 2                            |
      | comparing, 2954 of 29594 files                     | 2954 of 29594 files                      | 26640 left                        |
      | hashing, 5.1 of 17.1 GB, 6210 of 13845 files       | 5.1 of 17.1 GB · 12.0 GB left            | 6210 of 13845 files · 7635 left   |
      | hashing, 17.1 of 17.1 GB, 13845 of 13845 files     | 17.1 of 17.1 GB · nothing left to read   | 13845 of 13845 files · finishing… |
      | checking hard links, 1242 of 3104 groups           | 1242 of 3104 groups                      | 1862 left                         |

  @AC-8
  Scenario: A completed scan's totals lines
    Given a COMPLETED scan whose phases hold 38886 files, 902.9 GB and 4120 folders (walk), 29594 files (compare), 13845 files and 17.1 GB (hash), 3104 groups (hard links), with groupsTotal 2987
    Then the four sections read "38886 files · 902.9 GB" over "found in 4120 folders", "29594 compared" over "→ 13845 candidates", "13845 hashed · 17.1 GB" over "→ 3104 matching groups" and "3104 groups checked" over "→ 2987 duplicate groups"

  @AC-8
  Scenario: A finished section waits for the number its arrow needs
    Given a scan whose hashing is finished and whose phases have no hard-link entry yet
    Then the hashing section reads "13845 hashed · 17.1 GB" with no second line

  @AC-9
  Scenario: Sections not started
    Given a scan in the compare phase
    Then the hashing section reads "not started" over "total known after step 2"
    And the hard-link section reads "not started" over "total known after step 3"

  @AC-9
  Scenario: A cancelled scan
    Given a CANCELLED scan stopped while hashing with 5.1 of 17.1 GB and 6210 of 13845 files
    Then the hashing section reads "5.1 of 17.1 GB read" over "6210 of 13845 files"
    And the hard-link section reads "not run"
    And the walk and compare sections show their totals lines

  @AC-10
  Scenario: States are readable without colour
    Given one row for each of: a finished section, the running one, one not started, a stopped one, a failed one
    Then each section's text says done, now, not started, stopped or failed respectively, and the walk section while it runs shows no percentage

  @AC-11
  Scenario: A failed scan
    Given a FAILED scan in the hashing phase with the message "disk read failed"
    Then the hashing section is tagged "failed"
    And the row shows "Scan failed while hashing files. disk read failed"

  @AC-12
  Scenario: A completed scan collapses
    Given a COMPLETED scan started at 14:02 and finished at 14:17 (14 min 38 s), with the totals of AC-8
    Then its row has one line reading "Completed · started 14:02", "38886 files · 902.9 GB found → 29594 compared → 13845 hashed · 17.1 GB → 2987 duplicate groups", "100%", "took 14 min 38 s" and "finished 14:17"
    And an "Open results" link to the scan's results
    And no per-section text and no Cancel button

  @AC-12
  Scenario: Cancelled and failed rows stay expanded
    Given a CANCELLED scan and a FAILED scan
    Then each row still shows its four sections, and the failed row its message

  @AC-13
  Scenario Outline: A duration in words
    Given a scan that took <seconds> seconds
    Then the row reads "took <text>"

    Examples:
      | seconds | text         |
      | 42      | 42 s         |
      | 59      | 59 s         |
      | 878     | 14 min 38 s  |
      | 3599    | 59 min 59 s  |
      | 3900    | 1 h 05 min   |
      | 7230    | 2 h 00 min   |

  @AC-15
  Scenario: Reduced motion stops the stripes
    Given the walk section is running, shown with moving stripes
    When the user prefers reduced motion
    Then the walk section keeps its stripes and no animation runs on it

## Scope

**Touches:**
- `server/frontend/src/app/scans`: `scan-row.ts`/`.html`/`.css` are rebuilt (the sections, the strip, the collapsed row); a new helper file for the overall percentage and section states; `scan-eta.ts` (30 s warm-up, hard links, finishing) and `scan-format.ts` (totals lines, duration) with their specs; `scan-phase.ts` and its spec go if the new helpers replace them; `scan-screen` specs change only where they read the old row text.
- `docs/design/ui.md`: the scan screen's section describes the new row.
- `CLAUDE.md`: the `server` entry's description of the scan row (DFS-057's per-phase lines) is replaced.
- Already committed with this refinement: the canvas source under `docs/design/mockups/scan-progress-row-2026-11-08/`.

**Out of scope:**
- The server's progress fields (DFS-064).
- Plan controls and the results screen.
- The canvas's phone layout (retired AC-14).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the Vitest tests)
- [ ] No test this ticket adds waits on real time (the clock is faked)
- [ ] The production build prints no budget warning
- [ ] `CLAUDE.md` and `docs/design/ui.md` describe the new row (the verifier reads them)
- [ ] Started by hand once on the built jar with a real scan of a large folder (several GB): the row's sections, percentage, time left and collapsed Completed row are read from the page's DOM and compared with the canvas source (desktop only); `verification.md` records it
- [ ] The same run records whether the bar sat still after the last file was read with no phase change (the stall DFS-064 decision 1 explains); if it did, a follow-up ticket is filed with `new`

## Open questions

None.
