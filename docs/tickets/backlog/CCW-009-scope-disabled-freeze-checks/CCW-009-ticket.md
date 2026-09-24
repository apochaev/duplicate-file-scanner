---
id: CCW-009
title: Scope DisabledCheck and FreezeCheck to the running story
status: in-progress
type: fix
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [tools/story-gate/DisabledCheck.java, tools/story-gate/FreezeCheck.java, tools/story-gate/Story.java, tools/story-gate/SelfTest.java, tools/story-gate/selftest/cases]
blocked_reason:
created: 2026-09-23
updated: 2026-09-23
completed:
---

# CCW-009: Scope DisabledCheck and FreezeCheck to the running story

## Story

As the orchestrator running the story pipeline
I want `DisabledCheck` and `FreezeCheck` to judge only the ticket currently running
So that a file another, already-closed ticket committed to the test tree cannot fail this ticket's gate

## Context

Spun off from `docs/learnings/log.md` L-027 rather than applied directly — a real code change to `tools/story-gate` with genuine edge cases (how a ticket's own test scope is determined, what "the ticket's own files" means for a check that currently just walks the whole test tree), not a one-line edit. Follows the precedent L-008 set for the same reason.

`DisabledCheck` scans the entire test source tree for `@Disabled`/unexplained-skip and `FreezeCheck` diffs the entire test tree against the `tests`-phase commit recorded in `plan.json` (`Story.testsCommit`) — neither is scoped to the ticket currently being run. Running DFS-002, both checks failed on `SizeIndexHeapBenchmark.java`, a file DFS-001 had already committed and closed, unrelated to anything DFS-002 touched (`docs/tickets/completed/DFS-001-group-files-by-size/plan.json`, defect 2; `docs/tickets/backlog/DFS-002-partial-hash-stage/plan.json`, defect 2). The orchestrator worked around it by hand each time — fixed the file directly (not routed to DFS-002's implementer, since it wasn't that phase's defect) and moved `plan.json`'s recorded `tests`-phase commit forward — but a story with no orchestrator able to make that judgment call would simply be blocked by an unrelated ticket's file.

**Decided (2026-09-23): scope by `plan.json`'s `tests`-phase `artifacts` list, not by `touches`.** Checked against the actual reproduced bug first: `touches`-based scoping (reusing `TouchesCheck`'s existing package/path pattern logic) would *not* have fixed it, because DFS-001 and DFS-002 both list `us.pochaev.dfs.scanner.application` in `touches`, and `SizeIndexHeapBenchmark.java` sits in that shared package — a touches filter would still treat it as "in scope." The `tests`-phase `artifacts` list is the one source precise enough to exclude it, since it names the literal files that phase's own test-author wrote, regardless of which package they're in.

**Decided (2026-09-23): generalize `tools/story-gate/SelfTest.java`'s coverage check to more than one ticket, rather than add unenforced cases outside it.** `SelfTest.java`'s `coverage()` is hardcoded to one ticket (`static final String TICKET = "DFS-010"`) and cross-checks every case folder's bare `AC-<n>-...` name against only that ticket's own scenarios. Two consequences, found while refining this ticket, not designed in advance:
- That constant is already stale and `selftest` is currently broken as a result: `DFS-010` was relabeled to `CCW-003` by DFS-016's namespace migration (2026-09-22), which updated every other inline reference except this one. Confirmed live: `java tools/story-gate/StoryGate.java selftest` fails with `ERROR selftest: no ticket folder for DFS-010 under docs/tickets/{backlog,completed}`.
- Even fixed to `CCW-003`, the single-ticket design gives CCW-009 no way to add its own regression cases without either breaking the `AC-<n>-...` naming (a prefixed name like `CCW-009.AC-1-...` fails the current regex) or falsely presenting them as new criteria of CCW-003, an already-closed ticket.

Fix: let a case folder optionally carry a ticket-ID prefix (`<TICKET-ID>.AC-<n>-<slug>`), and have `coverage()` resolve which ticket's scenarios to check each case against from that prefix, defaulting to `CCW-003` for the existing unprefixed `AC-<n>-...` folders. This is the durable fix, not just CCW-009's workaround: it is the only way any future ticket adds a verifiable case to this shared harness. Bundled into this ticket rather than spun off separately, since CCW-009 cannot prove its own work through a `selftest` that currently errors out entirely, and the fix is what CCW-009 itself needs to add its regression cases.

## Acceptance criteria

The full ID of each criterion is `CCW-009.AC-<n>`.

- **AC-1** `DisabledCheck` reports a problem only for a test file listed in the current ticket's `plan.json` `tests`-phase `artifacts`; a `@Disabled` test file belonging to a different, unrelated ticket is not reported.
- **AC-2** A test file that *is* listed in the current ticket's `tests`-phase `artifacts` is still correctly flagged by `DisabledCheck` when it carries `@Disabled` — the new scoping does not weaken the check for the ticket's own files.
- **AC-3** `FreezeCheck` reports a problem only for a change to a test file listed in the current ticket's `tests`-phase `artifacts`; a change to a test file not in that list is not reported, even when it falls inside the diff window since the recorded `tests`-phase commit.
- **AC-4** A test file that *is* listed in the current ticket's `tests`-phase `artifacts` is still correctly flagged by `FreezeCheck` when it has changed since the recorded commit.
- **AC-5** When the `tests`-phase `artifacts` list is empty or absent (an older or hand-edited `plan.json`), both `DisabledCheck` and `FreezeCheck` fall back to checking every test file in the tree, exactly as before this ticket, rather than silently checking nothing.
- **AC-6** The DFS-001/DFS-002 false positive is reproduced directly and confirmed fixed: a test file that belongs to one ticket's `tests`-phase artifacts, committed and closed, does not fail `disabled` or `freeze` for a second, later ticket that never touched it, even when both tickets' `touches` name the same package.
- **AC-7** `selftest`'s coverage check resolves each case folder's ticket from an optional `<TICKET-ID>.` prefix (`CCW-009.AC-1-...`), defaulting to `CCW-003` for existing unprefixed `AC-<n>-...` folders, and checks each case only against its own ticket's scenarios.
- **AC-8** `selftest` runs successfully end-to-end again: the stale `DFS-010` reference is fixed to `CCW-003`, and every scenario of both CCW-003 (its existing, unprefixed cases) and CCW-009 (this ticket's new, prefixed cases) is covered.

## Scenarios

```gherkin
Feature: Scoped disabled/freeze checks and a multi-ticket selftest harness

  @AC-1
  Scenario: An unrelated ticket's disabled test file does not fail this ticket's check
    Given a test source tree containing "OldTicketBenchmark.java" with an @Disabled annotation
    And the current ticket's tests-phase artifacts list does not name "OldTicketBenchmark.java"
    When the disabled check runs for the current ticket
    Then it passes, with no problem reported for "OldTicketBenchmark.java"

  @AC-2
  Scenario: This ticket's own disabled test file still fails the check
    Given a test source tree containing "OwnTest.java" with an @Disabled annotation
    And the current ticket's tests-phase artifacts list names "OwnTest.java"
    When the disabled check runs for the current ticket
    Then it fails, naming "OwnTest.java"

  @AC-3
  Scenario: An unrelated ticket's changed test file does not fail this ticket's freeze check
    Given a git change to "OldTicketBenchmark.java" since the commit recorded for the tests phase
    And the current ticket's tests-phase artifacts list does not name "OldTicketBenchmark.java"
    When the freeze check runs for the current ticket
    Then it passes, with no problem reported for "OldTicketBenchmark.java"

  @AC-4
  Scenario: This ticket's own changed test file still fails the freeze check
    Given a git change to "OwnTest.java" since the commit recorded for the tests phase
    And the current ticket's tests-phase artifacts list names "OwnTest.java"
    When the freeze check runs for the current ticket
    Then it fails, naming "OwnTest.java"

  @AC-5
  Scenario: A plan.json with no recorded artifacts falls back to checking every test file
    Given a plan.json whose tests-phase artifacts list is empty
    And a test source tree containing an @Disabled test file
    When the disabled check runs
    Then it fails, naming that file, exactly as it would have before this ticket

  @AC-6
  Scenario: The exact DFS-001/DFS-002 shape is reproduced and fixed
    Given two tickets that both list the same package in touches
    And the first ticket's tests-phase committed and closed a benchmark file with @Disabled in that package
    And the second ticket's tests-phase artifacts list does not name that file
    When the disabled check and the freeze check run for the second ticket
    Then both pass, with no problem reported for the first ticket's file

  @AC-7
  Scenario: A prefixed case is checked against its own ticket's scenarios
    Given a case folder named "CCW-009.AC-1-unrelated-ticket-file" claiming a scenario tagged @AC-1 in CCW-009
    When selftest's coverage check runs
    Then it resolves the case against CCW-009's own ticket, not CCW-003's, and finds it covered

  @AC-7
  Scenario: An unprefixed case still defaults to CCW-003
    Given the existing case folder "AC-5-disabled" claiming a scenario tagged @AC-5 in CCW-003
    When selftest's coverage check runs
    Then it resolves the case against CCW-003, exactly as before this ticket

  @AC-8
  Scenario: selftest runs clean end-to-end after the fix
    Given the full selftest suite, including CCW-003's existing cases and CCW-009's new cases
    When "java tools/story-gate/StoryGate.java selftest" runs
    Then it exits 0, and every scenario of both CCW-003 and CCW-009 is reported covered
```

## Scope

**Touches:**
- `tools/story-gate/DisabledCheck.java`: scope the `@Disabled`/unexplained-skip scan to the running ticket's own `tests`-phase artifacts, falling back to the whole tree when that list is empty
- `tools/story-gate/FreezeCheck.java`: scope the freeze diff the same way
- `tools/story-gate/Story.java`: add a way to read the `tests`-phase `artifacts` list out of a parsed `plan.json`, alongside the existing `testsCommit`
- `tools/story-gate/SelfTest.java`: fix the stale `DFS-010` reference to `CCW-003`; generalize `coverage()` and `CASE_NAME` to resolve a case's ticket from an optional `<TICKET-ID>.` prefix
- `tools/story-gate/selftest/cases`: add this ticket's own prefixed regression cases (`CCW-009.AC-<n>-<slug>`)

**Out of scope:**
- Any change to `RedCheck`/`TestPhaseGate` (a separate, already-parked question — see L-026, folded into L-025's `SKILL.md` fix instead of a code change)
- Retroactively adding criteria to CCW-003 itself, or renaming its existing unprefixed cases

## Definition of done

- [x] `selftest` passes and covers every scenario of this ticket through its own prefixed fixture cases, alongside CCW-003's existing unprefixed ones (matching the precedent CCW-003 itself set for this item) — 2026-09-23: 13 `CCW-009.AC-*` case folders (`AC-1` x2, `AC-2`, `AC-3`, `AC-4`, `AC-5` x3, `AC-6` x2, `AC-7` x2, `AC-8`); `selftest` reports "every scenario of CCW-003, CCW-009 is covered". AC-1/2/5/6's disabled cases run through `cli` (the real `DisabledCheck.run`, reading `plan.json` from the ticket folder), since the map-based `disabled` case type lives in `SelfTestCase.java`, outside touches, and passes no plan. The `AC-5-*-empty-artifacts` cases reuse AC-1's and AC-3's exact inputs with only the artifacts list emptied, and fail, so the scoped cases genuinely discriminate.
- [x] Each touched check still runs through `java tools/story-gate/StoryGate.java <check> <ID>` and prints a verdict of about ten lines — 2026-09-23, live on this repo: `disabled`/`freeze` for DFS-001 and DFS-002 each print one `PASS` line ("no @Disabled tests in the 2|3 test files of the tests phase", "none of the 2|3 test files of the tests phase changed since fb842f03|421f402d"), exit 0. Fallback (no artifacts) output is byte-identical to before: every pre-existing `AC-5-*`/`AC-6-*` case passes unchanged.
- [x] No files changed outside the paths listed under Touches — 2026-09-23: `git status` shows only the four `.java` files, `selftest/cases/**`, and this ticket's own file. Inside `selftest/cases`, the three `AC-12-*` nested fixture tickets were moved from `DFS-010-story-gate-tools/DFS-010-ticket.md` to `CCW-003-story-gate-tools/CCW-003-ticket.md` (`id:` updated), and `AC-12-passes/expected.txt` now names CCW-003. This follows the stale-reference fix, and no unprefixed case folder was renamed. Two harmless `DFS-010` mentions remain outside touches: `SelfTestCase.java:317` (the launcher cases' fake-JDK argument, never resolved to a ticket) and the `StoryGate.java` class javadoc.
- [x] The DFS-001/DFS-002 false positive (AC-6) is reproduced directly as a fixture case and confirmed fixed — 2026-09-23: `CCW-009.AC-6-dfs-001-002-disabled` (both tickets' `touches: [us.pochaev.dfs.scanner.application]`, DFS-001's committed `SizeIndexHeapBenchmark.java` carrying `@Disabled`) and `CCW-009.AC-6-dfs-001-002-freeze` (the benchmark edited after DFS-002's tests commit, as the orchestrator's hand fix did) both `PASS` for DFS-002.
- [x] `selftest` itself runs clean end-to-end (AC-8) — not just the individual checks it exercises — 2026-09-23: `java tools/story-gate/StoryGate.java selftest` → "PASS selftest: 84 cases match their expected verdicts; every scenario of CCW-003, CCW-009 is covered", exit 0 (it previously errored with "no ticket folder for DFS-010").

## Open questions

None.
