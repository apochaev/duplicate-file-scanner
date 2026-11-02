---
id: DFS-049
title: Show a finished scan as a folder tree with a Sets tab
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-047, CCW-020]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-049: Show a finished scan as a folder tree with a Sets tab

## Story

As the user of the local DFS server
I want to open a finished scan and browse its folders in a tree, seeing the duplicate sets under the folder I select, largest reclaimable first
So that I can find where my duplicates are in the browser instead of reading the CLI report

## Context

Milestone M-1 ("see the results of a scan"), step 4 of 5 (`docs/design/ui.md`, "Milestones"). With this ticket the UI first shows duplicates.

- **U3:** left, a lazily loaded folder tree of the scanned roots; right, tabs filtered to the selected folder. This ticket builds the tree and the **Sets** tab (duplicate sets, largest reclaimable first). The **Overlaps** tab (the landing tab once it exists) is DFS-050.
- **U9:** Angular Material and the CDK (tree, paged tables, tabs).
- **DFS-041:** the scan screen in `server/frontend/src/app/scans/` is one page without a router; it lists this server run's scans. A finished scan needs a way to open its result.
- **DFS-047:** `ResultsService` with `listFolders(id, parent?)` (`FolderNode`: path, name, subtree and direct totals, `hasChildren`) and `listSets(id, under?, page, size)` (`SetPage` of `DuplicateSetView`, every physical copy with its links).
- **CCW-020:** the Angular component archetype, written from the scan screen; this is its first live use.

## Acceptance criteria

The full ID of each criterion is `DFS-049.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Result screen: folder tree and Sets tab

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `server/frontend/src/app`, `CLAUDE.md`, `docs/design/ui.md`>

**Out of scope:**
- The Overlaps tab (DFS-050).
- The folder detail view listed live from disk (U4, milestone M-2).
- Building a deletion plan, single-set picks (U6, milestone M-3).
- Show in Explorer and file previews (U8, milestone M-4).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the Vitest tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Navigation: bring in the Angular router (`/` the scan screen, `/scans/:id` the result), or switch views without a router? (Suggested: the router, so a result has a URL and the back button works.)
- How a finished scan is opened from the scan screen's list (a link on the row; only COMPLETED rows).
- What a tree node shows (name, subtree size, file count) and its default sort (name, as the API returns, or size).
- How a set shows its copies: hard links grouped under one copy; which folder's copies are highlighted; dates.
- Page size and paging control for sets; the empty state ("no duplicates under this folder").
- The archetype (CCW-020's, once done), gates and autorun.
- A Playwright end-to-end flow (D6) here or later.
