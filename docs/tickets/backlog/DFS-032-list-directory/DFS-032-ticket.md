---
id: DFS-032
title: List one directory level
status: draft
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: backend
depends_on: []
touches: []
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# DFS-032: List one directory level

## Story

As a user exploring a scan in the local UI
I want to see every entry of one folder, including files that have no duplicate
So that I can judge a folder as a whole before deciding what to delete in it

## Context

A scan report lists only duplicated files, so the UI's folder detail view (`docs/design/ui.md` U4, server ticket 8) needs to read a folder itself. The use case is `ListDirectory(path)`: one level deep, read-only, following the same rules as `FileTreeWalker`/`NioFileTreeWalker`: regular files and plain directories only; symlinks, junctions and other reparse points reported, never followed; unreadable entries reported (CLAUDE.md safety invariants). A query port in `domain.usecases.query` (`ui.md`, "What the UI needs from the core").

## Acceptance criteria

The full ID of each criterion is `DFS-032.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: List one directory level

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
- Recursion, and any write to disk.
- The server endpoint (frontend session).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- What each entry carries (name, kind, size, modification time, skip reason?), and in which order.
- Results for a missing path, a file instead of a folder, an unreadable folder, a link given as the path.
- Whether it reuses `FileTreeWalker` (with a depth limit) or a new secondary port.
- Whether it is limited to the scan's roots (the server also checks this, `ui.md` U10).
- `gates:` and `autorun:`.
