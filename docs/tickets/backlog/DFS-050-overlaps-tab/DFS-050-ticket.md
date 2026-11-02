---
id: DFS-050
title: Add the Overlaps tab to the result screen
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-048, DFS-049]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-050: Add the Overlaps tab to the result screen

## Story

As the user of the local DFS server
I want an Overlaps tab that ranks the folders under my selection by how much of them is duplicated elsewhere, shows which folder pairs share copies, and compares any two folders I pick
So that I can decide which folder can go, for example "every file in folder2 has a copy in folder1"

## Context

Milestone M-1 ("see the results of a scan"), step 5 of 5 (`docs/design/ui.md`, "Milestones"). It completes the milestone.

- **U3:** the Overlaps tab is the landing tab of the result view, filtered to the folder selected in the tree: folder summaries and folder pairs ranked by reclaimable bytes. Charts (overlap matrix, treemap) come later.
- **`folder-overlap-analysis.md` decision 1:** pairs are never combined into a verdict; two folders each fully covered by the other cannot both be deleted. The tab must not suggest otherwise.
- **DFS-048:** the summaries, pairs and two-folder pair endpoints.
- **DFS-049:** the result screen with its tree and tabs.

## Acceptance criteria

The full ID of each criterion is `DFS-050.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Overlaps tab

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `server/frontend/src/app`, `docs/design/ui.md`>

**Out of scope:**
- Charts: overlap matrix, treemap (U3, later; see also DFS-045).
- Building a rule from a pair ("keep A, quarantine in B"): milestone M-3 (U6).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the Vitest tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Layout: summaries and pairs as two tables in one tab, or a summary header for the selected folder above a pairs table?
- How full and partial sides are shown (for example "340 / 340 · full"), and how internal duplicates are shown apart from copies elsewhere.
- How two folders are picked for the comparison (from the tree, from a pair row, by typing).
- Wording that keeps "covered by another folder" from reading as "safe to delete" (decision 1).
- Becoming the landing tab: Overlaps first, Sets second (U3).
- Archetype, gates and autorun.
