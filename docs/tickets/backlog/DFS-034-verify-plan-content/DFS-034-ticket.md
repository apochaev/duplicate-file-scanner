---
id: DFS-034
title: Verify a deletion plan's content against its keepers
status: draft
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: backend
depends_on: [DFS-022]
touches: []
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# DFS-034: Verify a deletion plan's content against its keepers

## Story

As a user about to approve a deletion plan
I want every planned file byte-compared with the copy it is kept against
So that I can trust the plan before anything is moved

## Context

`docs/design/ui.md` U7.3: a read-only, long-running check that byte-compares each entry of a previewed plan (DFS-022) with its keeper through the `ContentComparator` port (CLAUDE.md decision 4), with progress and cancel. It is a dry run of the delete-time proof, not a replacement: execution still re-verifies (safety invariant "re-verify at execution time"). Like a scan, it runs on a caller-supplied executor, and the `use-case` archetype's status, race and executor items apply.

## Acceptance criteria

The full ID of each criterion is `DFS-034.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Plan content verify

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
- Executing or quarantining anything.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Does `ContentComparator` exist yet, or does this ticket add it (port plus NIO adapter)?
- Commands and queries: start, cancel, status, result; shared with the scan's status model or its own?
- Per-entry outcomes (identical, differs, missing, unreadable) and the progress events.
- `gates:` and `autorun:`.
