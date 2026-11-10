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

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-063.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Scan progress row

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

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
- Behaviour on a CANCELLING, CANCELLED or FAILED scan: which section freezes and where the failure message sits.
- Phone-width layout details; one frame per state or generic rules.
- Archetype (`component`), gates, and whether the by-hand run needs the design-gate.
