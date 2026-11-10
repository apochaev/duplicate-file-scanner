---
id: DFS-062
title: Extract a shared CLI story-test helper
status: in-review
type: test
scope: [cli]
archetype: none
priority: low
lane: backend
depends_on: []
touches:
  - cli/src/test/java/us/pochaev/dfs/cli
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

## Decisions

1. (2026-11-09) Shape: a static method on the output texts, `StubFailures.rethrow(String out, String err)`, in the `cli` test package. It matches the generic prefix `not implemented:` (the one `RedCheck` keys off), with no ticket ID argument, and throws `UnsupportedOperationException` carrying the stub's line. A test class that wants a one-call shape keeps a one-line local wrapper around its own `scan`. `Run` and `scan` stay private to `ScanCommandTest`.

2. (2026-11-09) `sysScan` in `ScanCommandTest` is deleted and its 10 call sites become `scan(...)`, so no DFS-061 scaffolding is left. The DFS-061 tests keep passing unchanged in meaning; nothing else in `ScanCommandTest` changes. DFS-062 cannot be checked against a live stub, so its own tests use plain strings (AC-1, AC-2).

3. (2026-11-09) Archetype `none`: a hand-implemented test-tree refactor, so no `gates:`. Launch: `/tickets start DFS-062 --worktree`.

## Acceptance criteria

The full ID of each criterion is `DFS-062.AC-<n>`.

- **AC-1** `StubFailures.rethrow` throws `UnsupportedOperationException` whose message is the first line from the `not implemented:` marker to its end, when that marker is in the `err` text or in the `out` text.
- **AC-2** `StubFailures.rethrow` returns normally, throwing nothing, when neither text contains `not implemented:`.

## Scenarios

Run mechanically: all three are plain JUnit tests in `StubFailuresTest` (`@DisplayName` starts with the criterion ID).

```gherkin
Feature: Shared CLI story-test helper

  @AC-1
  Scenario: The stub text is in stderr
    Given err is "error: scan failed: not implemented: DFS-999
" and out is ""
    When StubFailures.rethrow(out, err) is called
    Then it throws UnsupportedOperationException with message "not implemented: DFS-999"

  @AC-1
  Scenario: The stub text is in stdout only
    Given out is "line one
report: not implemented: DFS-998 extra
line three
" and err is ""
    When StubFailures.rethrow(out, err) is called
    Then it throws UnsupportedOperationException with message "not implemented: DFS-998 extra"

  @AC-2
  Scenario: No stub text anywhere
    Given out is "{\"type\":\"summary\"}
" and err is "Report written
"
    When StubFailures.rethrow(out, err) is called
    Then it returns without throwing
```

## Scope

**Touches:**
- `cli/src/test/java/us/pochaev/dfs/cli`: new `StubFailures` and `StubFailuresTest`; `ScanCommandTest` loses `sysScan` and its 10 calls become `scan(...)`.

**Out of scope:**
- Changing `RedCheck` or `tools/story-gate`.
- The pointer in `.claude/archetypes/use-case/test-guide.md`: `/learn apply DFS-061.L-7` adds it after this ticket closes.
- Moving `Run`, `scan` or `run` out of `ScanCommandTest`.

## Definition of done

- [x] `./gradlew build` is green (full build exit 0, incl. 3 StubFailuresTest tests and the Vitest run)
- [x] `sysScan` no longer exists in `ScanCommandTest`, and its 10 call sites call `scan(...)` (`grep -c sysScan` prints 0; verified, prints 0)
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` written with the product-owner review (hand-implemented ticket)
- [ ] Post-close: the user runs `/learn apply DFS-061.L-7` (the guide pointer); recorded in that entry's Decision line

## Open questions

None.
