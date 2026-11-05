---
id: DFS-055
title: Run plan executions one at a time
status: draft
type: fix
scope: [file-scanner]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: backend
depends_on: [DFS-051]
touches: []
blocked_reason:
created: 2026-11-05
updated: 2026-11-05
completed:
---

# DFS-055: Run plan executions one at a time

## Story

As the user executing deletion plans
I want two plan executions never to move files at the same time in a way that could remove every copy of a file
So that "never delete the last copy" holds even when I start a second execution before the first has finished

## Context

Raised by DFS-051's product-owner review (its `verification.md`, "Known limits", 2026-11-05). Must land before M-5 (the server's Execute button, `docs/design/ui.md` "Milestones").

- **The race:** DFS-051's `PlanExecutionService` (on DFS-042's shared `JobRunner`, in `application`) checks each candidate and its keeper, then moves the candidate by atomic rename. A check and its rename are not one atomic step, and two executions are not coordinated. Run 1 keeps Y and quarantines X; run 2 keeps X and quarantines Y: both can pass their checks, then both move, and the set has no copy left outside quarantine. Each plan is valid on its own (DFS-022 validates a plan as a whole, not across plans).
- **CLAUDE.md, "Never delete the last copy":** every plan must leave a verified surviving copy per set. Quarantine can be undone, so nothing is lost for good, but no DFS-051 criterion covers this.
- **Related, from the same review:** later links of a hard-linked file are checked only by size and modified time, never compared (DFS-051 decision 8, accepted). If this ticket revisits the check-then-move step, that is the place to reconsider it.
- **Where things live (DFS-051):** `StartPlanExecution` / `CancelPlanExecution` / `GetPlanExecutionStatus` / `GetPlanExecutionResult` in `domain.usecases`, `PlanExecutionService` and `JobRunner` in `application`, `ScannerFactories.planExecutionUseCases(Executor, ExecutionConfig)`, and in `scanner-spring` the `planExecutionExecutor` and `planExecutionUseCases` beans and `ExecutionProperties` (`dfs.execution.*`).

## Decisions

1. **One plan execution at a time per process** (2026-11-05). `PlanExecutionService` lets at most one of its executions be RUNNING or CANCELLING at a time; what a second start does is decision 2. This closes the race inside a process with one rule that is simple to test, and a local single-user app has no use for two bulk moves at once. Not a lock per duplicate set (parallel runs for unrelated plans, but a digest-keyed lock table with deadlock ordering in a safety path, for parallelism nobody uses) and not a lock per path (two locks per move, mandatory ordering, the most code for no gain over a set lock). A set lock can replace this later behind the same ports. A second process sharing the quarantine (the CLI and the server) is decision 3.

## Acceptance criteria

The full ID of each criterion is `DFS-055.AC-<n>`.

- **AC-1** While one plan execution of a `PlanExecutionService` is RUNNING or CANCELLING, no second execution of that service moves any file; in particular two executions with crosswise plans (run 1 keeps Y and quarantines X, run 2 keeps X and quarantines Y) never leave the set without a copy outside quarantine.

## Scenarios

```gherkin
Feature: Plan executions do not overlap

  @AC-1
  Scenario: Crosswise plans never quarantine both copies
    Given a completed scan with one duplicate set of "a/x.jpg" and "b/x.jpg", 2 MB each
    And plan 1 keeps "a/x.jpg" and quarantines "b/x.jpg", and plan 2 keeps "b/x.jpg" and quarantines "a/x.jpg"
    And the executor runs both executions on separate threads, with plan 1's execution held just after its checks
    When both executions are started
    Then plan 2's execution moves no file while plan 1's is RUNNING
    And afterwards at least one of "a/x.jpg" and "b/x.jpg" is still in place
```

## Scope

**Touches:**
- <to be refined; likely `us.pochaev.dfs.scanner.application` (`PlanExecutionService`, `JobRunner`), possibly `scanner-spring`>

**Out of scope:**
- The Execute button and any UI or CLI entry point (M-5).
- Undo and purge of a quarantine run.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- A second start while one runs: queue it, or refuse it with an error code?
- Two processes sharing a quarantine (the CLI and the server): in scope (a lock file beside the manifests?) or documented as unsupported?
- Revisit DFS-051 decision 8 (later hard links checked by size and time only) or leave it?
- Archetype (`use-case` likely), gates (a deletion ticket: the design gate is recommended) and autorun.
