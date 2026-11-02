---
id: DFS-051
title: Execute a deletion plan into quarantine
status: ready
type: feature
scope: [file-scanner, build]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 35}}
gates: [design, done]
autorun: true
priority: high
lane: backend
depends_on: [DFS-022, DFS-034, DFS-042]
touches:
  - us.pochaev.dfs.scanner.domain.usecases.command
  - us.pochaev.dfs.scanner.domain.usecases.query
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.domain.factory
  - us.pochaev.dfs.scanner.infra.port
  - us.pochaev.dfs.scanner.infra.adapter
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ExecutionProperties.java
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-051: Execute a deletion plan into quarantine

## Story

As the user who has previewed and checked a deletion plan
I want to execute it, moving every planned file into an app-managed quarantine folder with a manifest
So that duplicates actually leave their folders, safely: never the last copy, each re-verified byte for byte just before it moves, and the whole run recorded so it can be undone

## Context

The product's purpose is deleting duplicates safely; today nothing deletes. Every building block before execution exists:

- **DFS-022** `PreviewDeletionPlan` builds a frozen `DeletionPlan` (`id`, a 64-hex digest of its content; `operations`, each an `Operation.Quarantine(digest, size, links, keeper, source)`; `conflicts`; `totals`; `leftBehind`). A plan with conflicts must not be executed (safety invariant: never delete the last copy).
- **DFS-033** `CheckPlanDrift` re-reads size and modification time; **DFS-034** `VerifyPlanContent` compares each candidate byte for byte with its keeper through `ContentComparator`, giving an `OperationCheck` per file (`IDENTICAL`, `DIFFERS`, `CANDIDATE_MISSING`, `KEEPER_MISSING`, `NOT_A_REGULAR_FILE`, `UNREADABLE`).
- **DFS-042** runs plan verify as a job: typed ports (`StartPlanVerify`, `CancelPlanVerify`, `GetPlanVerifyStatus`, `GetPlanVerifyResult`), `JobStatus` (6 states), and the package-private `JobRunner<I,P,T>` over the generic `JobStore<I,R>` port. DFS-042.L-3 (parked) asks this ticket to make `JobRunner`'s rejection message generic and to test its `Error` path.

Settled design (CLAUDE.md): decision 2 (files move to an app-managed quarantine folder with a manifest holding the original path and the keeper it was verified against; the manifest is the audit log; a whole run can be undone; purge frees space; a permanent-delete adapter behind the same `FileDeleter` port only with an explicit flag; never `Desktop.moveToTrash`), decision 4 (byte-for-byte compare against the keeper immediately before each move, no skip flag), the safety invariants (never the last copy, validated over the whole plan; re-verify at execution: keeper and candidate exist, unchanged, identical; hard links are not duplicates; no link following), and the provisional port sketch (`ExecuteDeletionPlan` command, `FileDeleter` secondary port, undo and purge of a quarantine run). `docs/design/ui.md` U1 and U7 put executing in the UI's next version (milestone M-5, "needs the core executor first"): this ticket is that core executor.

### Decisions

1. **Execute and the manifest only; undo and purge are follow-up tickets.** This ticket delivers the `ExecuteDeletionPlan` job, the `FileDeleter` secondary port with its quarantine adapter, and the manifest, written so a later undo can restore a whole run from it alone. Undo and purge are filed separately once this design is approved. Until undo lands, a run is reversible by hand from the manifest; nothing user-facing executes in between (the Execute button is M-5). Rejected: execute plus undo (two job types and their races in one design) and all four at once (too large for one design gate).
2. **Archetype `use-case`** (v9), like DFS-018 and DFS-042: an asynchronous command with state, progress and cancel, plus a new secondary port and adapter (`FileDeleter`, "Where files go"). Its edge-case checklist is settled item by item below. Rejected: `none` (loses the red and green gates and the independent verifier where deletion safety needs proof most) and a separate adapter ticket first (the port's shape is best designed with the use case that drives it).
3. **Input `(ScanResult scan, DeletionPlan plan)`; each file re-checked for drift and content just before it moves.** Immediately before moving a candidate: the candidate's and its keeper's size and last-modified time must still equal what `scan` recorded (the rule DFS-033's `CheckPlanDrift` applies), then the candidate must be byte-identical to the keeper (`ContentComparator`, decision 4). Only then is it moved. A plan not built from `scan` is refused with the existing `PLAN_NOT_FROM_SCAN`. This meets CLAUDE.md's "re-verify at execution time: exist, unchanged, byte-identical" literally and reuses DFS-033's and DFS-034's logic; a future CLI rebuilds the scan from the JSONL export, whose `set` lines record each path's `modified`. Rejected: the plan alone (an edited file still matching its keeper would move unnoticed, so "unchanged" is only half met) and adding modification times to the plan (changes DFS-022's model and its golden plan-ID tests).
4. **A file that fails is skipped and recorded, the run continues; a plan with conflicts is refused up front.** Every operation ends with one outcome: moved, or skipped with a reason (drift: changed or missing, of the candidate or its keeper; content: `DIFFERS`, `NOT_A_REGULAR_FILE`, `UNREADABLE`, reusing DFS-034's `OperationCheck.Outcome` where it fits; or the move itself failed, for example locked or access denied). The run completes with a report holding every operation's outcome, and the manifest lists only what actually moved. A plan whose `conflicts` is not empty is refused with a new `ErrorCode.PLAN_HAS_CONFLICTS` before anything is recorded or moved (safety invariant: never the last copy, validated over the whole plan). Rejected: stopping at the first failure (one stale file halts a large run and still leaves a partial one) and verifying everything before moving anything (reads every file twice, and the per-file check before each move is still needed, so a partial run remains possible).
5. **One quarantine folder per volume; every move is a same-volume atomic rename.** A candidate moves to `<volume root>/.dfs-quarantine/<run ID>/<its path below the volume root>`, for example `E:\Backup\photo.jpg` to `E:\.dfs-quarantine\<run ID>\Backup\photo.jpg`, with `Files.move(..., ATOMIC_MOVE)`: instant, no extra space, never a half-copied file. Mirroring the path keeps names unique and readable. The run's manifest lives in one configured folder (decision 6) and records each file's absolute quarantine path, so undo finds files on any volume. A volume whose root cannot hold the folder (read-only, access denied) skips its files with a "quarantine not writable" reason (decision 4). Quarantine frees no space until purge (decision 2, a later ticket). Rejected: one configured root with copy-then-delete across volumes (slow, needs space on that drive, adds a copy-verify-delete path with its own failure states) and one root that skips other volumes' files (most of a multi-drive scan would skip).
6. **The manifest is append-only JSON Lines, written ahead and flushed per line, in a folder the caller configures.** It is `<manifest folder>/<run ID>.jsonl`; the folder comes from a config record in `domain.model` set by the app (CLAUDE.md, "Spring-friendliness rules"), and the core never picks a default. Lines, in order: one `run` header (run ID, plan ID, scan roots, start time); per operation file, a `moving` line (original path, quarantine path, keeper, digest, size, last-modified time) written and flushed to disk **before** the rename, then a `moved` or `skipped` line (with the reason, decision 4) after it; one `end` line (terminal status and counts). After a crash at any point, every file is at its original path or at the quarantine path its `moving` line names, so undo (a later ticket) can always account for it. Same style as the scan's JSONL export (DFS-030: readers ignore fields and line types they do not know). A manifest that cannot be created refuses the run before anything moves; a manifest line that cannot be written stops the run before the next rename (a move the log cannot record must not happen). Rejected: one JSON document at the end (a crash mid-run leaves no record) and a line only after each rename (a crash between rename and append leaves a moved file unrecorded).
7. **The job model mirrors DFS-042, with three execution rules** (checklist items 1-4, 6, 9, 11, 12). Typed ports `StartPlanExecution` and `CancelPlanExecution` in `domain.usecases.command`, `GetPlanExecutionStatus` and `GetPlanExecutionResult` in `domain.usecases.query`, and a `PlanExecutionId` in `domain.model`, which is also the run ID in the manifest name and quarantine paths (decisions 5, 6). New `ErrorCode`s `UNKNOWN_PLAN_EXECUTION` (cancel, status and result for an ID never issued, the ID in the message) and `PLAN_EXECUTION_NOT_FINISHED` (result before a terminal state). Unchanged from DFS-042: the same `JobStatus` and cancel rules, run by the shared package-private `JobRunner` over the generic `JobStore` port; the job is recorded QUEUED before submission, and a rejected task ends FAILED with `Failure(EXECUTOR_REJECTED)`; one immutable record per job, changed only by atomic updates; validation (decisions 3, 4, 6: plan from the scan, no conflicts, manifest folder usable) on the caller's thread before anything is recorded, a `null` argument a `NullPointerException`; a failure of the work ends FAILED (an `Error` too, then rethrown) and is reported through the status, not as a `Failure` of the query; state kept until the process exits, no eviction. Execution rules: (a) cancel takes effect **between files**, never inside one: the file in progress finishes its check, rename and manifest lines, then the run ends CANCELLED and writes the manifest's `end` line; (b) a CANCELLED run's result reports every operation's outcome, with the ones never reached as not attempted; (c) DFS-042.L-3 is folded in: `JobRunner`'s executor-rejection message becomes generic (it names plan verify today), and its `Error` path (FAILED, then rethrow) is pinned by a test. Rejected: a fresh job design for execution (two job implementations to keep consistent, against DFS-042 decision 1).
8. **Every hard link of an operation moves; content is compared once per operation.** An `Operation.Quarantine`'s `links` are all paths of one physical copy (DFS-017), and its keeper is always a different physical copy (DFS-022). Each link gets its own drift check and its own `moving` and `moved` lines; the byte compare against the keeper runs once, on the first link, since every link has the same bytes. If a later link fails (decision 4's reasons), the links already moved stay moved and recorded, and the operation's outcome is "partly moved", naming which paths moved and why the rest did not. Cancel never splits an operation: all its links are handled before the cancel takes effect (decision 7a). Rejected: all or nothing per operation (a move-back can fail too, adding a rollback path inside the deletion code) and skipping operations with several links (those duplicates could never be cleaned).
9. **Progress pushed and pulled, a full report as the result** (checklist items 7, 8, 10; mirrors DFS-042 decision 3). `StartPlanExecution` has two overloads, with and without an `ExecutionListener`, whose sealed `ExecutionEvent`s are `BytesCompared(long bytesDone, long bytesTotal)` and `OperationDone(<the operation's outcome>, int done, int total)`, forwarded from the executor's thread. `GetPlanExecutionStatus` returns `PlanExecutionSnapshot(JobStatus status, String planId, int operationsDone, int operationsTotal, long bytesDone, long bytesTotal, int filesMoved, long bytesMoved, String failure)`, kept current from the same events (`failure` empty unless FAILED). A listener that throws is detached and its failure recorded, and the run continues (DFS-018 decision 7): a listener must not stop a deletion halfway. `GetPlanExecutionResult` returns a `PlanExecutionReport`: the run ID, the manifest path, every operation's outcome in plan order (moved, partly moved, skipped with its reason, not attempted) and the totals of files and bytes moved and skipped; available for COMPLETED and CANCELLED, `Failure(PLAN_EXECUTION_NOT_FINISHED)` otherwise. Rejected: pull only (the server's live progress would have to poll, unlike scans).
10. **Exposed through the factory and Spring beans, with the first `@ConfigurationProperties`** (checklist items 11, 14; mirrors DFS-042 decision 6). `ScannerFactories.planExecutionUseCases(Executor executor, ExecutionConfig config)` returns `PlanExecutionUseCases(StartPlanExecution start, CancelPlanExecution cancel, GetPlanExecutionStatus status, GetPlanExecutionResult result)`, a record in `domain.factory`, built on the NIO adapters, the quarantine `FileDeleter` adapter and a fresh in-memory job store per call; `ExecutionConfig(Path manifestFolder)` is a record in `domain.model`. `ScannerAutoConfiguration` gains a `planExecutionExecutor` bean (a virtual thread per task, `destroyMethod = "shutdownNow"`, `@ConditionalOnMissingBean(name = "planExecutionExecutor")`), a `PlanExecutionUseCases` bean taking it by `@Qualifier`, and one delegate bean per port, each `@ConditionalOnMissingBean`, as for plan verify. The manifest folder comes from `@ConfigurationProperties("dfs.execution")` with `manifest-folder`, default `${user.home}/.dfs/manifests` (CLAUDE.md decision 6: "`@ConfigurationProperties` arrives with the first app-wide setting"; this is it). The server (M-5) only injects; `scanner-spring` stays wired by the backend lane. Rejected: core and factory only (the frontend lane would edit `scanner-spring` and design the first configuration property) and no default (every app and test context would need the property, the CLI included).
11. **Tests without timing, fakes for every failure path, and an explicit no-link-following rule** (checklist item 13; safety invariants "no symlink or junction following" and "hard links are not duplicates"). `application` tests use fakes: a manual executor (QUEUED), a fake `ContentComparator` blocking on a latch (RUNNING and CANCELLING between files), a fake `FileDeleter` failing on chosen paths (decision 4's skips, decision 8's "partly moved"), and a fake manifest writer failing on a chosen line (the run stops before the next rename, decision 6); the completion-versus-cancel race is tested in both orders. Adapter tests run on real files in a `@TempDir`: the move lands at the mirrored quarantine path, manifest lines appear in order and are flushed, and an unwritable quarantine location gives the "quarantine not writable" skip. Every path is re-checked without following links: a candidate or keeper that has become a symlink or junction is skipped as `NOT_A_REGULAR_FILE`, never read or moved, proven on a symlink whose target would otherwise compare identical (test-guide, DFS-033.L-2), and skipped (`Assumptions`) where the platform does not allow creating one. One end-to-end test runs a real virtual-thread executor, the NIO adapters and `@TempDir` files, waiting on the task's `Future` (test-guide v9, DFS-042.L-2). No `Thread.sleep`. Rejected: relying on the drift check alone for links (a link to an identical file of the same size and time passes it, and its target is what gets read).

12. **Gates `[design, done]`.** This ticket moves the user's files; the deletion path's design (the `FileDeleter` port, the manifest writer, the order of checks per file, where cancel and failures are handled) is reviewed before tests and code (tickets skill: keep the design gate for any ticket that plans or executes deletion).
13. **Small rules that follow from the decisions above.** An empty plan (no operations) is accepted: its run completes with an empty report and a manifest holding only the `run` and `end` lines (as DFS-042 decision 5 accepts an empty plan). A manifest folder that cannot be created or written refuses the run with a new `ErrorCode.MANIFEST_UNWRITABLE` before anything is recorded (decision 6). The manifest folder and each volume's `.dfs-quarantine/<run ID>` folder are created on first use, never at application startup, so a context that never executes writes nothing.

## Acceptance criteria

The full ID of each criterion is `DFS-051.AC-<n>`.

- **AC-1** Executing a plan with no conflicts on files unchanged since the scan moves every link of every operation to `<volume root>/.dfs-quarantine/<run ID>/<its path below the volume root>`, leaves every keeper in place and ends COMPLETED.
- **AC-2** A plan with conflicts is refused with `Failure(PLAN_HAS_CONFLICTS)`: no job is recorded, no manifest is created and no file moves.
- **AC-3** A plan not built from the given scan is refused with `Failure(PLAN_NOT_FROM_SCAN)`, leaving no trace.
- **AC-4** A candidate or keeper that is missing, or whose size or last-modified time differs from the scan, is skipped with a drift reason naming which path and what changed; the candidate is not moved and the run continues.
- **AC-5** A candidate whose content differs from its keeper is skipped as `DIFFERS` and not moved.
- **AC-6** A candidate or keeper that has become a symlink or junction is skipped as `NOT_A_REGULAR_FILE`, never read through the link and never moved.
- **AC-7** A candidate whose move fails (locked, access denied) is skipped with a move-failed reason, and the run goes on with the next operation.
- **AC-8** Every hard link of an operation moves and content is compared once per operation; when a later link fails, the links already moved stay moved and the operation's outcome is partly moved, naming which paths moved.
- **AC-9** The manifest `<manifest folder>/<run ID>.jsonl` holds, in order, one `run` line, per moved or skipped path a `moving` line written and flushed before the rename followed by its `moved` or `skipped` line, and one `end` line with the terminal status and counts.
- **AC-10** A manifest folder that cannot be created or written refuses the run with `Failure(MANIFEST_UNWRITABLE)` before anything is recorded; a manifest line that cannot be written mid-run ends the run FAILED before the next rename.
- **AC-11** A candidate whose volume cannot hold the quarantine folder is skipped with a quarantine-not-writable reason and not moved.
- **AC-12** A started run is QUEUED until the executor picks it up; cancelling it while QUEUED ends it CANCELLED with nothing moved; cancelling it while RUNNING makes it CANCELLING, the operation in progress finishes, and it ends CANCELLED with the remaining operations reported as not attempted and the manifest's `end` line written.
- **AC-13** Completion and cancel are decided in one atomic step: a cancel recorded before completion ends the run CANCELLED, a cancel after completion leaves it COMPLETED, in both orders of the two transitions.
- **AC-14** Cancel, status and result for an ID never issued return `Failure(UNKNOWN_PLAN_EXECUTION)` naming the ID; the result before a terminal state returns `Failure(PLAN_EXECUTION_NOT_FINISHED)`.
- **AC-15** When the executor rejects the task, the run ends FAILED and start returns `Failure(EXECUTOR_REJECTED)` with a message that names no particular use case (the shared `JobRunner`'s message, also for plan verify).
- **AC-16** An `Error` thrown by the work ends the run FAILED and is rethrown on the executor's thread.
- **AC-17** A listener receives `BytesCompared` and `OperationDone` events on the executor's thread; the status snapshot counts the same operations, bytes, files moved and bytes moved; a listener that throws is detached, its failure recorded, and the run continues.
- **AC-18** The result of a COMPLETED or CANCELLED run is a report with the run ID, the manifest path, every operation's outcome in plan order and the totals of files and bytes moved and skipped.
- **AC-19** An empty plan is accepted: its run completes with an empty report and a manifest holding only the `run` and `end` lines; a `null` scan, plan or listener throws `NullPointerException`.
- **AC-20** `ScannerFactories.planExecutionUseCases(executor, config)` and the autoconfigured beans run a plan through the same ports; the manifest folder comes from `dfs.execution.manifest-folder` (default `${user.home}/.dfs/manifests`), and an app's own `planExecutionExecutor` or port bean replaces the default.
- **AC-21** On a real virtual-thread executor with the NIO adapters, a plan over two identical files in a `@TempDir` executes end to end: one file is in quarantine, its keeper in place, and the manifest records the move.

## Scenarios

```gherkin
Feature: Execute a deletion plan into quarantine

  Background:
    Given a scan of the folder "lib" holding "A/photo.jpg" and "B/photo.jpg" with the same 27 bytes
    And a manifest folder "manifests"
    And a plan from that scan with one rule: keep "lib/A", quarantine in "lib/B"

  @AC-1 @AC-9 @AC-18
  Scenario: A plan on unchanged files moves the candidate into quarantine
    When I execute the plan
    Then the run ends COMPLETED
    And "lib/B/photo.jpg" is gone and "<volume root>/.dfs-quarantine/<run ID>/<path to lib>/B/photo.jpg" holds its 27 bytes
    And "lib/A/photo.jpg" is unchanged
    And "manifests/<run ID>.jsonl" holds the lines "run", "moving", "moved", "end" in that order
    And the report lists one operation moved, 1 file and 27 bytes moved, 0 skipped

  @AC-9
  Scenario: The moving line is on disk before the rename
    Given a file deleter that records the manifest's content when asked to move "lib/B/photo.jpg"
    When I execute the plan
    Then the recorded content ends with the "moving" line for "lib/B/photo.jpg"

  @AC-2
  Scenario: A plan with a conflict is refused
    Given a second rule: keep "lib/B", quarantine in "lib/A", so the plan has a conflict
    When I execute the plan
    Then the answer is Failure(PLAN_HAS_CONFLICTS)
    And no run is recorded, "manifests" holds no file and both photos are in place

  @AC-3
  Scenario: A plan from another scan is refused
    Given a plan built from a different scan
    When I execute it with this scan
    Then the answer is Failure(PLAN_NOT_FROM_SCAN)
    And no run is recorded and "manifests" holds no file

  @AC-4
  Scenario Outline: A path that drifted since the scan is skipped
    Given <change>
    When I execute the plan
    Then the operation is skipped with the drift reason "<reason>" naming "<path>"
    And "lib/B/photo.jpg" is <state>
    And the manifest holds a "skipped" line for it and no "moved" line

    Examples:
      | change                                              | reason   | path            | state                |
      | "lib/B/photo.jpg" rewritten with 28 bytes           | changed  | lib/B/photo.jpg | still in place       |
      | "lib/B/photo.jpg" given a new last-modified time    | changed  | lib/B/photo.jpg | still in place       |
      | "lib/A/photo.jpg" deleted                           | missing  | lib/A/photo.jpg | still in place       |
      | "lib/B/photo.jpg" deleted                           | missing  | lib/B/photo.jpg | not created anywhere |

  @AC-5
  Scenario: A candidate whose bytes now differ is skipped
    Given "lib/B/photo.jpg" rewritten with 27 different bytes and its scan size and last-modified time restored
    When I execute the plan
    Then the operation is skipped as DIFFERS and "lib/B/photo.jpg" is still in place

  @AC-6
  Scenario: A candidate that became a symlink is neither read nor moved
    Given "lib/B/photo.jpg" replaced by a symlink to a copy of "lib/A/photo.jpg" with its size and last-modified time
    And the platform allows creating the symlink
    When I execute the plan
    Then the operation is skipped as NOT_A_REGULAR_FILE
    And the symlink is still at "lib/B/photo.jpg" and no comparator read went through it

  @AC-7
  Scenario: A move that fails is skipped and the run continues
    Given a second operation quarantining "lib/C/doc.txt" against "lib/A/doc.txt"
    And a file deleter that fails to move "lib/B/photo.jpg" with "access denied"
    When I execute the plan
    Then "lib/B/photo.jpg" is skipped with the move-failed reason "access denied"
    And "lib/C/doc.txt" is moved into quarantine
    And the run ends COMPLETED

  @AC-8
  Scenario: Every hard link of an operation moves
    Given "lib/B/photo.jpg" and "lib/B/old/photo.jpg" are hard links of one file, one operation with both links
    When I execute the plan
    Then both links are in quarantine
    And the comparator compared that operation once

  @AC-8
  Scenario: A link that fails leaves the operation partly moved
    Given "lib/B/photo.jpg" and "lib/B/old/photo.jpg" are hard links of one file, one operation with both links
    And a file deleter that fails to move "lib/B/old/photo.jpg"
    When I execute the plan
    Then "lib/B/photo.jpg" is in quarantine and "lib/B/old/photo.jpg" is still in place
    And the operation's outcome is partly moved, naming "lib/B/photo.jpg" as moved

  @AC-10
  Scenario: A manifest folder that cannot be written refuses the run
    Given the manifest folder path is an existing regular file
    When I execute the plan
    Then the answer is Failure(MANIFEST_UNWRITABLE)
    And no run is recorded and both photos are in place

  @AC-10
  Scenario: A manifest line that cannot be written stops the run
    Given a second operation quarantining "lib/C/doc.txt"
    And a manifest writer that fails when writing the "moving" line for "lib/C/doc.txt"
    When I execute the plan
    Then the run ends FAILED with that failure's message
    And "lib/B/photo.jpg" is in quarantine and "lib/C/doc.txt" is still in place

  @AC-11
  Scenario: A volume that cannot hold the quarantine folder is skipped
    Given a file deleter that reports the quarantine folder for "lib/B/photo.jpg" as not writable
    When I execute the plan
    Then the operation is skipped with the quarantine-not-writable reason and "lib/B/photo.jpg" is still in place

  @AC-12
  Scenario: Cancelling a queued run moves nothing
    Given a manual executor
    When I execute the plan
    Then the status is QUEUED
    When I cancel the run and the executor then runs the task
    Then the run is CANCELLED, "lib/B/photo.jpg" is in place and the report lists the operation as not attempted

  @AC-12
  Scenario: Cancelling a running run finishes the operation in progress
    Given a second operation quarantining "lib/C/doc.txt"
    And a comparator that blocks on a latch while comparing "lib/B/photo.jpg"
    When I execute the plan and the comparator blocks
    Then the status is RUNNING
    When I cancel the run
    Then the status is CANCELLING
    When the latch is released
    Then the run is CANCELLED
    And "lib/B/photo.jpg" is in quarantine and "lib/C/doc.txt" is still in place, reported as not attempted
    And the manifest ends with an "end" line saying CANCELLED

  @AC-13
  Scenario: A cancel recorded before completion wins
    Given a run whose cancel is recorded before its completion is applied
    Then the run ends CANCELLED

  @AC-13
  Scenario: A cancel after completion changes nothing
    Given a run that completed
    When I cancel it
    Then it stays COMPLETED and its report is unchanged

  @AC-14
  Scenario: An ID never issued is unknown to every operation
    Given a plan execution ID this service never issued
    Then cancel, status and result each return Failure(UNKNOWN_PLAN_EXECUTION) naming that ID

  @AC-14
  Scenario: The result is not available before the run ends
    Given a run still QUEUED on a manual executor
    Then the result returns Failure(PLAN_EXECUTION_NOT_FINISHED)

  @AC-15
  Scenario: A rejected task fails the run with a generic message
    Given an executor that rejects every task
    When I execute the plan
    Then the answer is Failure(EXECUTOR_REJECTED) and the run's status is FAILED
    And neither message names "plan verify" or "plan execution"

  @AC-16
  Scenario: An Error in the work fails the run and is rethrown
    Given a comparator that throws an OutOfMemoryError
    When the executor runs the task
    Then the run is FAILED and the executor's thread sees the OutOfMemoryError

  @AC-17
  Scenario: Progress is pushed and pulled from the same events
    Given a listener recording events and their threads
    When I execute the plan to the end
    Then the listener received BytesCompared events up to 27 of 27 and one OperationDone with 1 of 1
    And every event arrived on the executor's thread, not the test thread
    And the snapshot reports 1 of 1 operations, 27 of 27 bytes, 1 file and 27 bytes moved

  @AC-17
  Scenario: A listener that throws is detached and the run continues
    Given a second operation quarantining "lib/C/doc.txt"
    And a listener that throws on its first event
    When I execute the plan to the end
    Then both candidates are in quarantine, the run is COMPLETED and the listener's failure is recorded
    And the listener received no event after the one that threw

  @AC-18
  Scenario: The report lists every outcome in plan order
    Given three operations: the first moved, the second skipped as DIFFERS, the third skipped as changed
    When the run completes
    Then the report lists them in plan order with those outcomes
    And its totals are 1 file and 27 bytes moved, 2 skipped

  @AC-19
  Scenario: An empty plan completes with an empty report
    Given a plan with no operations
    When I execute it
    Then the run ends COMPLETED with an empty report
    And the manifest holds only a "run" and an "end" line

  @AC-19
  Scenario: A null argument is a caller bug
    Then executing with a null scan, a null plan or a null listener throws NullPointerException

  @AC-20
  Scenario: The factory and the Spring beans execute a plan
    Given the manifest folder property set to a folder in a @TempDir
    When I execute the plan through ScannerFactories.planExecutionUseCases and, separately, through the autoconfigured StartPlanExecution bean
    Then each run ends COMPLETED with its candidate in quarantine
    And with no property set, the manifest folder is "${user.home}/.dfs/manifests" and nothing is created there at startup
    And an app's own planExecutionExecutor bean and its own StartPlanExecution bean replace the defaults

  @AC-21
  Scenario: End to end on a real executor
    Given a real virtual-thread executor and the NIO adapters over a @TempDir
    When I execute the plan and wait on the task's completion with a 10-second bound
    Then "B/photo.jpg" is in that volume's quarantine folder, "A/photo.jpg" is in place
    And the manifest records the move
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.usecases.command`: `StartPlanExecution`, `CancelPlanExecution`
- `us.pochaev.dfs.scanner.domain.usecases.query`: `GetPlanExecutionStatus`, `GetPlanExecutionResult`
- `us.pochaev.dfs.scanner.domain.model`: `PlanExecutionId`, `PlanExecutionSnapshot`, `PlanExecutionReport` and its outcome types, `ExecutionEvent`, `ExecutionListener`, `ExecutionConfig`, four new `ErrorCode` values (`PLAN_HAS_CONFLICTS`, `UNKNOWN_PLAN_EXECUTION`, `PLAN_EXECUTION_NOT_FINISHED`, `MANIFEST_UNWRITABLE`)
- `us.pochaev.dfs.scanner.application`: the plan execution service, `JobRunner`'s generic rejection message and its `Error`-path test, their tests
- `us.pochaev.dfs.scanner.domain.factory`: `PlanExecutionUseCases`, `ScannerFactories.planExecutionUseCases`
- `us.pochaev.dfs.scanner.infra.port`: `FileDeleter` (the quarantine move) and the manifest writer port
- `us.pochaev.dfs.scanner.infra.adapter`: the NIO quarantine adapter, the JSON Lines manifest adapter, the end-to-end test
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`: the executor, use-case and port beans
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ExecutionProperties.java`: new, `dfs.execution.manifest-folder`
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`

**Out of scope:**
- The server API and the UI's Execute button (M-5, frontend lane).
- Undo of a run and purge of the quarantine (decision 1; two follow-up tickets).
- The permanent-delete adapter behind `FileDeleter` and its explicit flag (CLAUDE.md decision 2; a later ticket).
- Executing from the CLI (DFS-021 previews only).
- Moving the scan onto the shared job machinery, and evicting finished jobs (DFS-042's out of scope, unchanged).
- Recovering an interrupted run after a crash (undo reads the manifest; decision 6 only guarantees it can).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `design.md` addresses every item of the `use-case` edge-case checklist or marks it not applicable with a one-line reason (decisions 7-11 give the expected dispositions)
- [ ] No test this ticket adds or changes uses `Thread.sleep`, and no test reads or writes outside a `@TempDir` (the default manifest folder under `${user.home}` is asserted, never written)
- [ ] The plan verify ports and behavior are unchanged apart from `JobRunner`'s rejection message, and the existing plan verify tests pass (a test asserting the old message is updated by the test-author and named in `design.md`)
- [ ] `module-info.java` is unchanged and `ModuleExportsTest` stays green
- [ ] Nothing creates the manifest folder or a `.dfs-quarantine` folder when the CLI or the server starts (decision 13)

## Open questions

None.
