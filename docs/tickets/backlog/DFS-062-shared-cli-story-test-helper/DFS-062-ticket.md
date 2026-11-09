---
id: DFS-062
title: Extract a shared CLI story-test helper
status: draft
type: test
scope: [cli, docs]
archetype:
priority: low
lane: backend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-09
updated: 2026-11-09
completed:
---

# DFS-062: Extract a shared CLI story-test helper

## Story

As a maintainer running the story pipeline
I want one shared helper in the `cli` test tree that rethrows a stub's "not implemented" text found in CLI output as `UnsupportedOperationException`
So that CLI-level story tests meet the red gate without a per-ticket copy left behind as dead scaffolding after green

## Context

DFS-061.L-7 (`docs/tickets/completed/DFS-061-skip-system-folders/learnings.md`), option (a), chosen by the user 2026-11-09. The CLI turns a stub's failure into an "error: scan failed" line (exit 1), so a CLI-level story test fails with `AssertionFailedError` instead of the exception the red gate expects. DFS-061 solved this with a private `sysScan` in `cli/src/test/java/us/pochaev/dfs/cli/ScanCommandTest.java` (around line 1180), which scans, looks for `not implemented: DFS-061` in stderr and stdout, and rethrows the line as `UnsupportedOperationException`. It stays as dead scaffolding after green, and the freeze stops the implementer removing it.

`RedCheck` and `tools/story-gate` stay unchanged.

DFS-061.L-7 stays `proposed` until this helper exists. When it does, `/learn apply DFS-061.L-7` adds a pointer to the helper in `.claude/archetypes/use-case/test-guide.md` (and bumps that archetype's `version`).

## Acceptance criteria

The full ID of each criterion is `DFS-062.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

<!-- To be refined. -->

## Scope

**Touches:** to be refined. Expected: `cli/src/test/java/us/pochaev/dfs/cli/` (new helper; `ScanCommandTest` moves onto it); `.claude/archetypes/use-case/test-guide.md` via `/learn apply DFS-061.L-7`, after this ticket closes.

**Out of scope:**
- Changing `RedCheck` or `tools/story-gate`.

## Definition of done

- [ ] `./gradlew build` is green
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Archetype: probably `none` (a test-tree refactor), to be decided in refine.
- The helper's shape: how it takes the stub marker (`not implemented: <ID>`), and whether it wraps `scan(...)` or takes a `Run`.
- Whether `sysScan` is removed from `ScanCommandTest` here or kept as a thin call.
