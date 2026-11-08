---
id: CCW-032
title: Trim CLAUDE.md to the rules every session needs
status: draft
type: docs
scope: [docs]
archetype:
priority: high
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-08
updated: 2026-11-08
completed:
---

# CCW-032: Trim CLAUDE.md to the rules every session needs

## Story

As the user paying for every request in every session and subagent
I want `CLAUDE.md` cut from about 32 KB to about 13 KB, with the detail moved where only the work that needs it loads it
So that each request carries about 4k fewer tokens, multiplied across every pipeline phase

## Context

Filed 2026-11-08 by dfs-harness from Claude Code's usage report for the last 24 h: "Trim CLAUDE.md · 7k tokens per request" 19% (alongside "Give story subagents a cheaper model" 59%, CCW-031).

- **Measured.** `CLAUDE.md` is 31,981 bytes. By section: "What this project is" (lines 3–13) 8,735 bytes, almost all one paragraph on the `server` subproject and its API, which also repeats one sentence ("registry's listener folds the core's `ScanEvent`s…" appears twice); "Tickets" 4,443; "Current state and open items" 3,506.
- **Stale lines in "Current state".** "Built so far: the file walker…"; "`ScanId`, `ScanStatus`, `CancelScan`, `GetScanStatus` and an empty `ScannerFactories` exist only so the exported packages are non-empty" (the scan use cases and factories are built); "the detection pipeline is DFS-001 to DFS-005" (done).
- **It loads everywhere.** Every session and every pipeline subagent loads it; the implementing agents are told to read "only that ticket and this file" (CLAUDE.md "Tickets", tickets skill "Implementing a ticket"). CCW-022's rule "Keep what loads every turn small: `CLAUDE.md` holds rules, not ticket history" is not yet applied to the file itself.
- **Ideas for refinement (none decided):**
  - The server and API paragraph moves to `server/CLAUDE.md` (loaded when work touches `server/`) and `docs/design/ui.md`, leaving a short summary in the root file.
  - The Tickets section's mechanics (lanes, coordinator, autorun, close flow, context hygiene) move to the `tickets` and `story` skills, which already hold them in full; about five lines stay (statuses, only `ready` is implemented, stay within `touches`, branch naming, namespaces).
  - "Current state" keeps only what is still open; the duplicate sentence goes.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-032.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: CLAUDE.md holds only rules every session needs

  @AC-1
  Scenario: <the root CLAUDE.md is at most N bytes>
    Given <concrete state>
    When <action>
    Then <observable outcome>

  @AC-1
  Scenario: <a fact moved out of CLAUDE.md is still found where its work loads it>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <likely `CLAUDE.md`, a new `server/CLAUDE.md`, `docs/design/ui.md`, `.claude/skills/tickets/SKILL.md`, and any agent prompt that tells an agent to read only the ticket and `CLAUDE.md`>

**Out of scope:**
- Changing any rule's meaning: this ticket moves and shortens, it does not decide.
- Models of the pipeline agents (CCW-031, CCW-030).

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] Every fact removed from `CLAUDE.md` is listed in `verification.md` with where it now lives, or why it was dropped (stale)
- [ ] The next pipeline run after the merge needs nothing that was moved out (no agent reports a missing rule)

## Open questions

1. Does a nested `server/CLAUDE.md` reach pipeline subagents working in a `server/` worktree, or only when they read a file there? Verify before relying on it.
2. Which ticket mechanics must stay in `CLAUDE.md` because agents read only the ticket and this file (for example the `touches` rule, `@DisplayName` with criterion IDs)?
3. Target size: about 13 KB, or a hard cap that a check enforces?
4. Should the "What this project is" server summary keep the endpoint list, or point to `server/frontend/openapi.json`?
