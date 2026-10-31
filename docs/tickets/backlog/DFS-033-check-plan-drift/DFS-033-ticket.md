---
id: DFS-033
title: Check a deletion plan for drift since the scan
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

# DFS-033: Check a deletion plan for drift since the scan

## Story

As a user reviewing a previewed deletion plan
I want to know which of its files changed, moved or vanished since the scan
So that I do not act on a plan built from stale information

## Context

`docs/design/ui.md` U7.2: a cheap, read-only check that re-reads the size and modification time of every entry of a previewed plan (DFS-022), both candidates and keepers, and compares them with what the scan recorded. It is a quick early warning, not the delete-time proof: that stays the byte compare at execution (CLAUDE.md decision 4) and DFS-034's content verify.

## Acceptance criteria

The full ID of each criterion is `DFS-033.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Plan drift check

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
- Byte comparison (DFS-034) and any change to disk.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Input: a plan ID, or the plan itself? Depends on DFS-022's plan model.
- Drift kinds reported (missing, size changed, modified time changed, now a link or unreadable) and the result shape.
- Synchronous query, or asynchronous with progress for large plans?
- `gates:` and `autorun:`.
