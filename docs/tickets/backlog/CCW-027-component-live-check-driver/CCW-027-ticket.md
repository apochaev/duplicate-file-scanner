---
id: CCW-027
title: Ship a live-check browser driver with the component archetype
status: draft
type: chore
scope: [build]
archetype:
priority: low
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# CCW-027: Ship a live-check browser driver with the component archetype

## Story

As the verifier of a `component` ticket
I want a ready-made, tested scratch browser driver in the archetype
So that the by-hand live check (checklist item 11) costs a few commands, not the turns spent writing and debugging a driver

## Context

Requested after DFS-057 (2026-11-07), learning `DFS-057.L-3`, applied in `161f11a` without the driver: the checklist now names the recipe and a quick-to-hash folder, but the driver itself was left open.

- **What happened:** the verifier of DFS-057 hit its 30-turn cap and was resumed once. Part of the spend was building a Playwright-for-Java program from nothing: the jars (`playwright`, `driver`, `driver-bundle`, `gson`) come from the Gradle cache, `setChannel("msedge")` avoids a Chromium download, and under Git Bash `-cp` needs `C:/` paths, not `/c/`. The live check still ended partial.
- **A first version exists:** the verifier's 43-line `LiveCheck.java` (in that session's scratchpad, `.../scratchpad/live/LiveCheck.java`; it will not survive, so the author copies it from the DFS-057 verifier's work or rewrites it). It starts from a server's `?token=` link, drives the page and prints DOM text, and was run once on a real scan.
- **Where it would live:** next to the archetype (`.claude/archetypes/component/`), e.g. a `live-check/` folder, referenced from checklist item 11. Whether it is Java, a Gradle task or a small script is a decision for refinement.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-027.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

<!-- Written in refinement; none are invented here. -->

## Scope

**Touches:**
- `.claude/archetypes/component/`: the driver and its use in checklist item 11.

**Out of scope:**
- Changing what the live check must show (each ticket's own done-item).
- The Playwright flows in `server/src/test` (DFS-053), which run in `e2eTest`.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Form of the driver: a Java single-file program run with `java`, a Gradle task in `server`, or a Node script? (The jars are already in the Gradle cache; Node is downloaded by the build into `server/.gradle/`.)
- Can the driver be tested without a real server and browser, or is its done-item a by-hand run only?
- Should it print only the DOM text and console error count, or take the selectors to read as arguments?
- Does it belong in `server/` (frontend lane files) rather than `.claude/archetypes/`? That moves it out of the harness lane.
