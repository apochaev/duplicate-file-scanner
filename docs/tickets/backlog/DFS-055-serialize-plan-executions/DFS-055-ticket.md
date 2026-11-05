---
id: DFS-055
title: Run plan executions one at a time
status: ready
type: fix
scope: [file-scanner, build, docs]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
autorun: true
priority: high
lane: backend
depends_on: [DFS-051]
touches:
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.infra.port
  - us.pochaev.dfs.scanner.infra.adapter
  - us.pochaev.dfs.scanner.domain.factory
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring
  - CLAUDE.md
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

1. **One plan execution at a time per process** (2026-11-05). `PlanExecutionService` lets at most one of its executions be unfinished (QUEUED, RUNNING or CANCELLING) at a time; what a second start does is decision 2. This closes the race inside a process with one rule that is simple to test, and a local single-user app has no use for two bulk moves at once. Not a lock per duplicate set (parallel runs for unrelated plans, but a digest-keyed lock table with deadlock ordering in a safety path, for parallelism nobody uses) and not a lock per path (two locks per move, mandatory ordering, the most code for no gain over a set lock). A set lock can replace this later behind the same ports. A second process sharing the quarantine (the CLI and the server) is decision 3.
2. **A second start is refused, not queued** (2026-11-05). While an execution is unfinished, `StartPlanExecution.start` returns `Failure` with a new `ErrorCode` `EXECUTION_ALREADY_RUNNING`, whose message names the unfinished execution's ID; no job is created and nothing is checked or moved. The slot is free again as soon as that execution is recorded COMPLETED, CANCELLED or FAILED, including a job cancelled while QUEUED or rejected by the executor (DFS-042), so a start right after succeeds. Nothing moves that the user did not start at that moment, the user sees the first run's outcome before the second, and the new code is one check at start. Not a queue (files move later than asked, against a disk the first run just changed: safe, since DFS-051 re-checks each keeper before each move, but a run partly refused for reasons the user did not see coming, and a job hand-off in a safety path with its own cancel and failure cases). The future Execute button shows the refusal (M-5).
3. **Across processes: an OS file lock in the manifest folder** (2026-11-05). An execution holds an exclusive lock on `<manifest folder>/execution.lock` (`FileChannel.tryLock`; the folder is `ExecutionConfig`'s manifest folder, `dfs.execution.manifest-folder`) from its start, before its job is created, until it is recorded COMPLETED, CANCELLED or FAILED. A start in another process while the lock is held returns decision 2's `EXECUTION_ALREADY_RUNNING`, with the message "another process is executing a plan". The OS releases the lock when a process ends, so a crash leaves no stale lock; the lock file itself stays and is reused. Failing to create or lock the file for any other reason refuses the start with DFS-051's `MANIFEST_UNWRITABLE`. The in-process check of decision 2 stays (a JVM cannot `tryLock` a region it already holds). The lock is file I/O, so it sits behind a new secondary port in `infra.port` with a `FileChannel` adapter in `infra.adapter`, wired by `ScannerFactories.planExecutionUseCases` and overridable as a `scanner-spring` bean like DFS-051's ports. Tests stay inside a `@TempDir`: the manifest folder is configured (DFS-051.L-1's seam); the cross-process case spawns a second Java process that holds the lock. Known limit: processes configured with different manifest folders are not coordinated. Not a lock per quarantine anchor (several locks per plan in a fixed order, files created on volumes before anything moves) and not a documented rule only (the invariant would rest on the user's memory, and M-5 would reopen this).
4. **DFS-051 decision 8's hard-link gap goes to its own ticket, DFS-056** (2026-11-05). Later links of a copy stay checked by size and modified time only in this ticket; DFS-056 adds a file-identity check against the compared first link. The race breaks "never delete the last copy"; the hard-link gap can only quarantine a non-duplicate, which can be undone. Keeping this ticket to the race gets it to M-5 sooner. DFS-056 depends on this ticket, since both change the same check-then-move step. Not folded in here (a criterion, tests and Windows and POSIX identity cases added to a ticket that must land before M-5) and not only recorded (the gap would stay).
5. **The slot is claimed in the job store, after the plan checks and the file lock, in one atomic step** (2026-11-05). A start runs, in this order: (a) DFS-051's existing checks (plan from this scan, not edited, no conflicts), refusing with their codes; (b) take the file lock (decision 3); (c) one atomic `JobStore` update that creates the QUEUED record only if no plan-execution record is unfinished, else releases the lock and refuses with `EXECUTION_ALREADY_RUNNING`. The lock is released when the job is recorded COMPLETED, CANCELLED or FAILED (DFS-051's `Settled` hook), and also when it is cancelled while QUEUED or rejected by the executor, where `Settled` is not called. Every refusal leaves no record, no ID and no lock held (archetype checklist items 4, 5, 6). `JobStore` (`infra.port`, implemented by `InMemoryJobStore` in `infra.adapter`) gains one conditional-create operation. Not an `AtomicBoolean` or lock field in `PlanExecutionService` (breaks CLAUDE.md's "no state in singleton fields" and the archetype's done-item; two service instances would not see each other) and not a separate in-memory slot port (two stores to keep consistent, for no gain).
6. **Archetype `use-case`, gates `[design, done]`** (2026-11-05). A change to a core use case's behaviour with a new secondary port and adapter, its wiring and tests against fakes and a `@TempDir`: the `use-case` archetype. The pipeline pauses after the architect: this ticket guards "never delete the last copy", and the lock port, the conditional create and the release paths are checked before tests depend on them (a lock freed too early is the riskiest defect). Not `[done]` (a wrong release path found only at the end).

## Acceptance criteria

The full ID of each criterion is `DFS-055.AC-<n>`.

- **AC-1** While one plan execution of a `PlanExecutionService` is unfinished (QUEUED, RUNNING or CANCELLING), no second execution of that service moves any file; in particular two executions with crosswise plans (run 1 keeps Y and quarantines X, run 2 keeps X and quarantines Y) never leave the set without a copy outside quarantine.
- **AC-2** A start while an execution is unfinished returns `Failure` with `EXECUTION_ALREADY_RUNNING` and a message naming the unfinished execution's ID, creates no execution and moves nothing; once that execution is COMPLETED, CANCELLED or FAILED (also when cancelled while QUEUED or rejected by the executor), the next start succeeds.
- **AC-3** While another process holds the lock on `<manifest folder>/execution.lock`, a start returns `Failure` with `EXECUTION_ALREADY_RUNNING` and the message "another process is executing a plan" and moves nothing; once that process releases the lock (or ends), a start succeeds; and an execution holds the lock from its start until it is recorded COMPLETED, CANCELLED or FAILED.
- **AC-4** A start checks the plan before the lock and the lock before claiming the slot: a plan with conflicts, an edited plan or one from another scan is refused with its DFS-051 code even while another execution is unfinished; every refused start leaves no execution record, no ID and the lock not held; of two starts issued at the same moment with nothing unfinished, exactly one succeeds and the other fails with `EXECUTION_ALREADY_RUNNING`; and a job cancelled while QUEUED or rejected by the executor releases the lock.

## Scenarios

```gherkin
Feature: Plan executions do not overlap

  @AC-1
  Scenario: Crosswise plans never quarantine both copies
    Given a completed scan with one duplicate set of "a/x.jpg" and "b/x.jpg", 2 MB each
    And plan 1 keeps "a/x.jpg" and quarantines "b/x.jpg", and plan 2 keeps "b/x.jpg" and quarantines "a/x.jpg"
    And the executor runs both executions on separate threads, with plan 1's execution held just after its checks
    When both executions are started
    Then plan 2's start is refused and moves no file while plan 1's execution is RUNNING
    And afterwards at least one of "a/x.jpg" and "b/x.jpg" is still in place

  @AC-2
  Scenario: A second start is refused while the first is unfinished
    Given execution "e1" of plan 1 is RUNNING
    When plan 2's execution is started
    Then the start fails with EXECUTION_ALREADY_RUNNING and a message containing "e1"
    And no execution for plan 2 exists and no file of plan 2 was moved

  @AC-2
  Scenario: A start right after the first finishes succeeds
    Given execution "e1" of plan 1 was RUNNING and is now recorded COMPLETED
    When plan 2's execution is started
    Then the start succeeds with a new execution ID

  @AC-2
  Scenario: A first execution that never ran frees the slot
    Given execution "e1" of plan 1 was cancelled while QUEUED
    When plan 2's execution is started
    Then the start succeeds
    And given instead that the executor rejected "e1", a start of plan 2 also succeeds

  @AC-3
  Scenario: Another process is executing
    Given a second Java process holds the lock on "<manifest folder>/execution.lock" in a temporary folder
    When an execution is started with that manifest folder
    Then the start fails with EXECUTION_ALREADY_RUNNING and the message "another process is executing a plan"
    And no file was moved
    When the second process ends
    Then a new start succeeds

  @AC-3
  Scenario: The lock is held exactly while the execution is unfinished
    Given an execution with manifest folder "<tmp>/m" is RUNNING
    Then a second Java process cannot lock "<tmp>/m/execution.lock"
    When the execution is recorded COMPLETED
    Then the second Java process can lock "<tmp>/m/execution.lock"

  @AC-4
  Scenario: The plan's own problems are reported first
    Given execution "e1" of plan 1 is RUNNING
    When a plan with a conflict is started
    Then the start fails with PLAN_HAS_CONFLICTS, not EXECUTION_ALREADY_RUNNING
    And no execution record for that plan exists

  @AC-4
  Scenario: Two simultaneous starts, one winner
    Given no execution is unfinished
    When two threads start plan 1 and plan 2 at the same moment, released by one latch
    Then exactly one start succeeds and the other fails with EXECUTION_ALREADY_RUNNING
    And exactly one execution record exists

  @AC-4
  Scenario: A refused or never-run start leaves the lock free
    Given execution "e1" of plan 1 is RUNNING
    When plan 2 is started and refused with EXECUTION_ALREADY_RUNNING
    Then the lock is still held only on behalf of "e1"
    When "e1" is recorded COMPLETED and an execution "e2" is started on a manual executor and cancelled while QUEUED
    Then a second Java process can lock "<manifest folder>/execution.lock"
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.application`: `PlanExecutionService` (the start order of decision 5, the lock release paths) and `JobRunner` if the release needs a hook for cancelled-while-QUEUED and rejected jobs; their tests.
- `us.pochaev.dfs.scanner.domain.model`: `ErrorCode.EXECUTION_ALREADY_RUNNING`.
- `us.pochaev.dfs.scanner.infra.port`: the execution-lock port, and `JobStore`'s conditional create.
- `us.pochaev.dfs.scanner.infra.adapter`: the `FileChannel` lock adapter and `InMemoryJobStore`'s conditional create; their tests, including the cross-process one.
- `us.pochaev.dfs.scanner.domain.factory`: `ScannerFactories.planExecutionUseCases` wires the lock adapter.
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring`, `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring`: the lock as a `@ConditionalOnMissingBean` port bean, like DFS-051's ports; `ScannerAutoConfigurationTest`.
- `CLAUDE.md`: one line in the safety invariants or current state: one plan execution at a time, per process and per manifest folder.

**Out of scope:**
- The Execute button and any UI or CLI entry point (M-5).
- Undo and purge of a quarantine run.
- Checking later hard links' identity before a move (DFS-056, decision 4).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] Every item of the `use-case` archetype's checklist is addressed in `design.md` or marked not applicable with a one-line reason
- [ ] No test this ticket adds or changes uses `Thread.sleep`; waiting is on a latch, a future or a bounded poll
- [ ] `PlanExecutionService` has no field other than its ports, its seams and the executor
- [ ] `ModuleExportsTest` stays green, and no new package is exported
- [ ] The cross-process tests start their child Java process and stop it within the test, bounded by a timeout, and leave no process running
- [ ] `CLAUDE.md` states that one plan execution runs at a time, per process and per manifest folder

## Open questions

None.
