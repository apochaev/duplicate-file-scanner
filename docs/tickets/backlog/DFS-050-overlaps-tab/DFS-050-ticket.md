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
updated: 2026-11-05
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

## Decisions

1. **One Overlaps tab, stacked: the selected folder's summary, a Folders table, a Folder pairs table** (2026-11-05). With a folder selected in the tree, a header line shows that folder's own summary (`SummaryPage.folder` from `/summaries?under=<path>`); under "All folders" there is no header. Below it a "Folders" table of summaries (`/summaries?under=`), then a "Folder pairs" table (`/pairs?under=`), each with its own paginator, both filtered to the selection. Both questions, "can this folder go?" and "where are the copies?", are on screen at once. Not a "Folders | Pairs" toggle (a click to see the other half) and not the pairs table alone (it loses the ranking of the most redundant folders, and coverage spread over several folders shows in no single pair: `folder-overlap-analysis.md` decision 1).

## Acceptance criteria

The full ID of each criterion is `DFS-050.AC-<n>`.

- **AC-1** The Overlaps tab shows, top to bottom, the selected folder's own summary as a header (none under "All folders"), a "Folders" table of summaries and a "Folder pairs" table, each table with its own paginator, both requested with `under=<the selected folder's path>` and without `under` for "All folders".

## Scenarios

```gherkin
Feature: Overlaps tab

  @AC-1
  Scenario: The tab shows the selection's summary, its folders and its pairs
    Given the result screen of COMPLETED scan "7f3c…" is shown with "All folders" selected
    Then the Overlaps tab shows no folder header, a "Folders" table and a "Folder pairs" table, each with a paginator
    And summaries and pairs were requested without under
    When the user selects folder "D:\Backup"
    Then summaries and pairs are requested with under "D:\Backup"
    And a header above the "Folders" table shows the summary of "D:\Backup"
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

- How full and partial sides are shown (for example "340 / 340 · full"), and how internal duplicates are shown apart from copies elsewhere.
- How two folders are picked for the comparison (from the tree, from a pair row, by typing).
- Wording that keeps "covered by another folder" from reading as "safe to delete" (decision 1).
- Becoming the landing tab: Overlaps first, Sets second (U3).
- Archetype, gates and autorun.
