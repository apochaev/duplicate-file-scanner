---
id: DFS-048
title: Serve folder summaries and overlap pairs for a finished scan
status: draft
type: feature
scope: [build]
archetype: endpoint
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-046, DFS-047]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-048: Serve folder summaries and overlap pairs for a finished scan

## Story

As the developer of the DFS UI
I want endpoints that answer a finished scan's folder summaries and overlap pairs under any folder, and the overlap of any two folders I pick
So that the Overlaps tab (DFS-050) can rank folders by reclaimable space and show where their copies are

## Context

Milestone M-1 ("see the results of a scan"), step 3 of 5 (`docs/design/ui.md`, "Milestones").

- **U3:** the Overlaps tab is the landing tab of the result view: folder summaries and folder pairs ranked by reclaimable bytes, filtered to the folder selected in the tree.
- **U5b:** the server answers small paged queries (summary for a folder, pairs under a folder, pair for two chosen folders) rather than sending the whole result.
- **DFS-046** provides `OverlapAnalysis.of(ScanResult)` in `us.pochaev.dfs.server.analysis`: `summaries()`, `summary(folder)`, `pairs()`, `pair(x, y)` (which throws `IllegalArgumentException` for equal, nested or out-of-root folders), and the records `FolderSummary`, `PairSide`, `FolderPair`.
- **DFS-047** provides the result feature package (`us.pochaev.dfs.server.result`), the per-scan index kept beside the registry, the path checks (`RELATIVE_PATH`, `PATH_OUTSIDE_ROOTS`), the paging conventions (`page`, `size` 1–500, `INVALID_PAGE`) and the shared error types in `us.pochaev.dfs.server.api`.

## Acceptance criteria

The full ID of each criterion is `DFS-048.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Overlaps API

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `server/src/main/java/us/pochaev/dfs/server`, `server/src/test/java/us/pochaev/dfs/server`, `server/frontend/openapi.json`, `CLAUDE.md`, `docs/design/ui.md`>

**Out of scope:**
- The Overlaps tab itself (DFS-050).
- Charts (overlap matrix, treemap): a later addition (U3).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit tests, `OpenApiSpecTest` and the Vitest tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Endpoint shapes, suggested: `GET /api/scans/{id}/summaries?under=&page=&size=` (the listed summaries inside `under`'s subtree, in DFS-046's order, plus the summary of `under` itself), `GET /api/scans/{id}/pairs?under=&page=&size=` (pairs with at least one side inside `under`), `GET /api/scans/{id}/pair?a=&b=` (any two folders). Tag: `overlaps` or `results`?
- "Under": a pair with only one side inside `under` included or not? (Suggested: included, since the other side is where the copies are.)
- Errors for `pair`: equal or nested folders → `400` with which code (`NESTED_FOLDERS`?); out of roots → `403` `PATH_OUTSIDE_ROOTS` (DFS-047's check).
- When the analysis is computed: on the first overlaps request (lazily, like DFS-047's index) or when the scan completes? How the first request behaves if it takes seconds on a large scan (DFS-046 AC-10 measures it).
- Memory: whether the analysis is kept for the server run, as DFS-047's index is.
- Gates and autorun.
- Docs: `docs/design/ui.md` U5b names DFS-046's package and `OverlapAnalysis.of` (moved here from DFS-046, so DFS-046 and DFS-047 do not both edit `ui.md`).
