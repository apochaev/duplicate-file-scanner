---
id: DFS-069
title: Mark folder groups as dealt with
status: draft
type: feature
scope: []
archetype:
priority: medium
lane: frontend
depends_on: [DFS-068]
touches: []
blocked_reason:
created: 2026-11-13
updated: 2026-11-13
completed:
---

# DFS-069: Mark folder groups as dealt with

## Story

As a user working through the Overlaps tab's folder groups
I want to tick a group as dealt with and have it move out of the way
So that the list shrinks as I progress and I always see which groups are still open

## Context

DFS-068 lists folder groups from `/groups` (paged 10/25/50). A long list gives no way to remember which groups were already handled.

Proposal (agreed in chat, option 1): a checkbox "Mark as dealt with" on each group header (one click, does not expand the group). Ticked groups leave the main list and appear in a "Dealt with (N)" expander below it, collapsed by default and shown muted (dimmed, green check); unticking returns a group to the main list. The header shows "N groups left · M dealt with" and the reclaimable bytes still open. Client-side only: no API, no OpenAPI change.

Identity: a group has no server ID; its key is the group's exact folder paths, sorted and joined. Storage: `localStorage` behind an injection token (like `DENSITY_STORAGE`, so Vitest fakes it), keyed per scan ID plus group key; every read and write in try/catch; the tab works without storage.

Known limits: marks live in one browser; groups are hidden client-side after each page loads, so page sizes and totals can drift (a server-side `done` filter is the later alternative).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-069.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

<!-- Filled during refinement. -->

## Scope

**Touches:**
- `server/frontend/src/app/results/` (overlaps tab, new storage token and helper)

**Out of scope:**
- Server-side storage of the marks, a `done` filter on `/groups`, sharing marks between browsers.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)

## Open questions

- Archetype and gates (component, as DFS-068?).
- Does the "N groups left" count cover only the loaded page or all matching groups (`totalItems` minus marked)?
- Should "Show dealt with in the list" (inline dimmed toggle) be in this ticket?
- Do marked groups still count toward the paginator and `hiddenSmall`?
- Does the Overlaps browser flow (Playwright) need a case?
