---
id: DFS-056
title: Check each later hard link's identity before moving it
status: draft
type: fix
scope: [file-scanner]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: low
lane: backend
depends_on: [DFS-055]
touches: []
blocked_reason:
created: 2026-11-05
updated: 2026-11-08
completed:
---

# DFS-056: Check each later hard link's identity before moving it

## Story

As the user executing deletion plans
I want every link of a quarantined copy to be proven the same file as the link that was compared against the keeper
So that a file that replaced a hard link after the scan is never moved into quarantine as if it were a duplicate

## Context

Split from DFS-055 (its decision 4, 2026-11-05), raised by DFS-051's product-owner review ("Known limits").

- **The gap:** right before a move, DFS-051 compares a copy's first link byte for byte against the keeper (CLAUDE.md decision 4); its later links are checked only by size and modified time (DFS-051 decision 8). A later link replaced after the scan by a different file of the same size and time moves without a compare. It lands in quarantine, not deletion, and the keeper is untouched, so it can be undone.
- **The suggested fix:** before moving a later link, check that its file identity (`FileIdentity.key`: device and inode on Linux and macOS, volume serial and 128-bit file ID on Windows; CLAUDE.md, "Hard links are not duplicates") still equals the first link's. Equal means it is the very file just compared; different refuses that link like any other drift.
- **Order:** depends on DFS-055, which changes the same `PlanExecutionService` check-then-move step; the two are not run in parallel.

## Acceptance criteria

The full ID of each criterion is `DFS-056.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Later hard links are proven the same file

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `us.pochaev.dfs.scanner.application` (`PlanExecutionService`)>

**Out of scope:**
- Serializing executions (DFS-055).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- What a mismatch does: refuse only that link, or the whole copy (all its links), and how the manifest records it.
- Whether the identity is read through DFS-026's existing `FileIdentity` port, and how a test creates a hard link and then replaces it (created at test time, skipped where the platform refuses, per CLAUDE.md's fixture rules).
- Archetype (`use-case` likely) and gates (a deletion ticket: the design gate is recommended).
