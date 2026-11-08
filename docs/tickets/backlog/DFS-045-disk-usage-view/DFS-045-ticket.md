---
id: DFS-045
title: Show folder and file sizes as a disk usage view
status: draft
type: feature
scope: []
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: low
lane: frontend
depends_on: [DFS-047, DFS-049]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-08
completed:
---

# DFS-045: Show folder and file sizes as a disk usage view

## Story

As the user of the local DFS server
I want to see how much space each folder and file under a scanned location takes, the way disk usage analyzers (a sized tree, a treemap like GrandPerspective's) show it
So that I can see where my space goes and where duplicates matter most, next to the duplicate results

## Context

A stub, filed 2026-11-01 at the user's request, for later refinement.

What already exists:
- The scanner walks every regular file and reports its size (`FileEntry`, `WalkEvent.FileFound`, `ScanEvent.FileWalked` with bytes).
- `ScanResult.folders` records each folder's direct counted-file count and bytes (CLAUDE.md design decision 1, DFS-030). Recursive totals can be rolled up from it.
- `docs/design/ui.md` already plans a treemap as a later addition to the Overlaps tab (U-section "Charts").
- The server's scan API (DFS-040) and scan screen (DFS-041) exist; a scan's result view does not yet.

Probably a small epic: a backend part (an API serving a scan's folder sizes) and a frontend part (the tree and treemap). If so, refinement splits it, and the backend part goes to the backend lane as a request (CCW-018).

## Acceptance criteria

The full ID of each criterion is `DFS-045.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Disk usage view

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
- <to be refined>

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Which views: a sortable size tree (like WinDirStat or ncdu), a treemap (like GrandPerspective), or both?
- Which sizes count: only the files a scan counts (it leaves out files under the minimum size, excluded names, links and unreadable files), or every file? And how are files the scan skipped shown?
- Where the data comes from: `ScanResult.folders` (direct bytes per folder, rolled up), or per-file sizes the result does not yet keep.
- How duplicates show in the view (for example, the reclaimable bytes per folder highlighted).
- Where it lives in the UI: a tab of the scan result view (which does not exist yet), or the Overlaps tab's planned treemap.
- Whether it is one ticket or split into backend and frontend tickets. Depends on DFS-047 (folder totals per scan) and DFS-049 (the result screen), set 2026-11-01, so it becomes a tab of that screen rather than a second tree.
- Archetype and lane once split.
