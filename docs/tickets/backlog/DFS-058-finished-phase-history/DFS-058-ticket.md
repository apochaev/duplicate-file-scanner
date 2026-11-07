---
id: DFS-058
title: Show each finished scan phase's totals
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: low
lane: frontend
depends_on: [DFS-057]
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# DFS-058: Show each finished scan phase's totals

## Story

As the user watching a running scan in the browser
I want each finished phase listed with its totals (files walked, files sampled and how many were left, files fully hashed)
So that I can see how the scan narrowed the candidates down, not only where it is now

## Context

Split from DFS-057 by the product owner on 2026-11-07 (option 3: the current phase first, the history decided after seeing it on a real scan).

- `ScanProgress` (DFS-040 decision 3) carries only the current phase's hash and group counts; the counts of a finished phase are overwritten. Keeping them needs the server: `ScanRegistry`'s listener folds `ScanEvent`s (`PhaseStarted`, `HashProgress`, `GroupDone`) into counts, so it could close each phase's totals when the next starts, and `ScanProgress` would carry them (a spec change, `./gradlew :server:updateOpenApi`).
- The page shows them in the scan row (DFS-041, DFS-057).

## Acceptance criteria

The full ID of each criterion is `DFS-058.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Finished phase history

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `server/src/main/java/us/pochaev/dfs/server/scan`, `server/frontend/openapi.json`, `server/frontend/src/app/scans`, `CLAUDE.md`>

**Out of scope:**
- The current phase's line (DFS-057).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Whether to do this at all, after DFS-057 is seen on a real scan.
- Which totals per phase, and whether a COMPLETED scan's row keeps showing them.
- Archetype (`endpoint` plus the component, or two tickets), gates and autorun.
