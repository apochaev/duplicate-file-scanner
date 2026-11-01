---
id: DFS-034
title: Verify a deletion plan's content against its keepers
status: done
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
autorun: true
priority: medium
lane: backend
depends_on: [DFS-022, DFS-033]
touches:
  - us.pochaev.dfs.scanner.domain.usecases.query
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.domain.factory
  - us.pochaev.dfs.scanner.infra.port
  - us.pochaev.dfs.scanner.infra.adapter
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java
blocked_reason:
created: 2026-10-31
updated: 2026-11-01
completed: 2026-11-01
---

# DFS-034: Verify a deletion plan's content against its keepers

## Story

As a user about to approve a deletion plan
I want every planned file byte-compared with the copy it is kept against
So that I can trust the plan before anything is moved

## Context

`docs/design/ui.md` U7.3: a read-only, long-running check that byte-compares each entry of a previewed plan (DFS-022) with its keeper through the `ContentComparator` port (CLAUDE.md decision 4), with progress and cancel. It is a dry run of the delete-time proof, not a replacement: execution still re-verifies (safety invariant "re-verify at execution time").

What exists: `DeletionPlan(id, operations, conflicts, totals, leftBehind)` whose only operation is `Operation.Quarantine(digest, size, links, keeper, source)` (DFS-022); DFS-033 adds `FileAttributesReader` (attributes read without following links) and the drift check. `ContentComparator` is only in CLAUDE.md's port sketch: this ticket adds it. The scan's asynchronous model (`StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult`, a six-state `ScanStatus`, `ScanRepository`) is DFS-018's.

### Decisions

1. **Synchronous verify here; the asynchronous job model is DFS-042.** `VerifyPlanContent.verify(DeletionPlan plan, VerifyListener listener, BooleanSupplier cancelled)` runs on the caller's thread, reports progress through the listener and stops when `cancelled` returns true. DFS-042 (draft) adds one asynchronous job model (start, status, cancel, result, state behind a port) that runs this verify now and plan execution later, so the scan's six-state model is not copied twice. The safety-critical compare gets a ticket of its own, with no concurrency in the review.
2. **Input: the plan alone.** The compare needs only each operation's links, keeper and size, all in the plan; the recorded times are DFS-033's concern. A `null` plan, listener or cancel signal throws `NullPointerException`. A plan with no operations returns a complete report with no checks.
3. **One check per operation, comparing its first link with its keeper.** The links of one operation are one physical file (DFS-017, DFS-022 decision 4), so comparing one of them proves them all. `VerifyReport(String planId, List<OperationCheck> checks, boolean cancelled, String listenerFailure)`, checks in plan order. `OperationCheck(String digest, Path candidate, Path keeper, Outcome outcome, String detail)` with `Outcome`, in this precedence: `CANDIDATE_MISSING`, `KEEPER_MISSING`, `NOT_A_REGULAR_FILE` (either is now a symlink, directory, junction or other special file; `detail` says which), `UNREADABLE` (either cannot be read; `detail` holds the exception's message, or its `toString()` when blank), `DIFFERS` (`detail` is `sizes differ: <candidate> vs <keeper>` or `first difference at byte <n>`), `IDENTICAL` (empty `detail`). One bad entry never stops the verify.
4. **The compare: attributes first, then chunked bytes, nothing followed.** For each operation, both paths' attributes are read without following links (DFS-033's `FileAttributesReader`); a missing or non-regular path is decided from them and its content is never opened, so a cloud placeholder (a reparse point) is never downloaded. Different sizes give `DIFFERS` without reading content. Otherwise `ContentComparator` (new port in `infra.port`; NIO adapter in `infra.adapter`) reads both files in chunks of 1 MiB (the design may make the size injectable for tests), stops at the first differing byte, reports bytes compared after each chunk, and checks `cancelled` between chunks, so a cancel takes effect within one chunk even in a multi-gigabyte file.
5. **Progress events.** `VerifyListener` receives `VerifyEvent`s: `BytesCompared(long bytesDone, long bytesTotal)` after each chunk, and `EntryChecked(OperationCheck check, int done, int total)` after each operation. `bytesTotal` is the sum of the operations' sizes; for an all-identical plan `bytesDone` reaches it, and an entry decided without reading content adds its size at once, so the bar always ends full. Events arrive on the caller's thread. A listener that throws is detached at its first exception, the verify continues, and `listenerFailure` holds that exception's message (empty when none), as with the scan listener (DFS-018 decision 7).
6. **Cancel.** `cancelled` is read before each operation and between chunks. When it returns true, `verify` stops and returns `Success` with `cancelled` true and the checks of the operations finished before; the interrupted operation is not reported. A verify cancelled before it starts returns no checks.
7. **Read-only; exposure as before.** Nothing is written, moved or deleted. `ScannerFactories.verifyPlanContent()` returns a `VerifyPlanContent` on the NIO adapters, and `ScannerAutoConfiguration` gains a `VerifyPlanContent` bean with `@ConditionalOnMissingBean`. `depends_on: [DFS-033]`: it reuses `FileAttributesReader`, and both tickets edit `ScannerFactories` and `ScannerAutoConfiguration`. Use-case checklist items 1, 3, 4, 6, 9, 11 and 12 do not apply (no ID, no lifecycle, no state, no executor: DFS-042's); item 7 is decision 5, item 8 is decision 5's listener rule, item 13 is plain file tests with a cancel signal set from the listener.
8. **Gates `[design, done]`.** This is the content proof shown to the user before deleting, so the design is reviewed before tests and code (CLAUDE.md safety invariants).

## Acceptance criteria

The full ID of each criterion is `DFS-034.AC-<n>`.

- **AC-1** For a plan whose candidates are byte-identical to their keepers, `verify` returns `Success` with the plan's id, `cancelled` false, and one `IDENTICAL` check per operation, in plan order, each naming the operation's first link and its keeper.
- **AC-2** A candidate whose bytes differ from its keeper's at the same size is `DIFFERS` with `first difference at byte <n>` (the right offset, also in a file larger than one chunk); one whose size differs is `DIFFERS` with `sizes differ`.
- **AC-3** A missing candidate is `CANDIDATE_MISSING` and a missing keeper `KEEPER_MISSING`, and the other operations are still checked.
- **AC-4** A candidate or keeper that is now a symlink, a directory or (on Windows) a junction is `NOT_A_REGULAR_FILE`, and no content behind it is read.
- **AC-5** A path that cannot be read is `UNREADABLE` with a non-empty detail, and the other operations are still checked.
- **AC-6** The listener receives one `EntryChecked` per operation in plan order with the right `done` and `total`, and `BytesCompared` events whose `bytesDone` grows to `bytesTotal`, the sum of the operations' sizes; a listener that throws is detached, the verify completes, and `listenerFailure` holds its message.
- **AC-7** When `cancelled` turns true during a file larger than one chunk, `verify` returns `Success` with `cancelled` true, having compared fewer bytes than that file's size, with the earlier operations' checks and without the interrupted one; cancelled before the start, it returns no checks.
- **AC-8** A plan with no operations returns `Success` with no checks and `cancelled` false; a `null` plan, listener or cancel signal throws `NullPointerException`.
- **AC-9** Verifying changes nothing on disk: names, sizes and last-modified times of the compared files are the same before and after.
- **AC-10** `ScannerFactories.verifyPlanContent()` returns a working `VerifyPlanContent`, and `ScannerAutoConfiguration` provides a `VerifyPlanContent` bean that backs off when the application declares its own.

## Scenarios

In every scenario, "a scan of T" is a `ScanResult` built from the real files in a temp folder T, and "the plan" is what `ScannerFactories.previewDeletionPlan()` returns for it and the request given; files are changed only after the plan is built.

```gherkin
Feature: Plan content verify

  @AC-1
  Scenario: Identical copies verify as identical
    Given temp folder T with A/x.jpg and B/x.jpg (identical, 5 bytes) and A/y.bin and B/y.bin (identical, 3 MiB)
    And the plan for rule KeepAndQuarantine(T/A, T/B) on a scan of T
    When the plan is verified with no cancel
    Then it returns Success with the plan's id, cancelled false and two IDENTICAL checks: T/B/x.jpg against T/A/x.jpg, then T/B/y.bin against T/A/y.bin

  @AC-2
  Scenario: A difference is found and located
    Given the plan above
    And afterwards the last byte of T/B/y.bin is changed, keeping its size
    And afterwards one byte is appended to T/B/x.jpg
    When the plan is verified
    Then T/B/y.bin is DIFFERS with detail "first difference at byte 3145727"
    And T/B/x.jpg is DIFFERS with detail "sizes differ: 6 vs 5"

  @AC-3
  Scenario: Missing files are reported and the rest still checked
    Given temp folder T with sets x, y and z, each a copy under A and under B, and the plan for rule KeepAndQuarantine(T/A, T/B)
    And afterwards T/B/x.jpg and T/A/y.jpg are deleted
    When the plan is verified
    Then x is CANDIDATE_MISSING, y is KEEPER_MISSING and z is IDENTICAL

  @AC-4
  Scenario: A file replaced by a link or a folder is not followed
    Given the plan of the first scenario
    And afterwards T/B/x.jpg is replaced by a symlink to a file with the same 5 bytes (skipped when the platform does not allow it)
    And T/A/y.bin is replaced by a folder
    When the plan is verified
    Then x is NOT_A_REGULAR_FILE naming the candidate, and y is NOT_A_REGULAR_FILE naming the keeper
    And on Windows a candidate replaced by a junction is also NOT_A_REGULAR_FILE (skipped on other platforms)

  @AC-5
  Scenario: An unreadable file does not stop the verify
    Given the plan of the first scenario
    And a comparator that fails with AccessDeniedException for T/B/x.jpg only (a fake ContentComparator, as design.md decides)
    When the plan is verified
    Then x is UNREADABLE with a non-empty detail and y is IDENTICAL

  @AC-6
  Scenario: Progress reaches the total
    Given the plan of the first scenario, whose sizes sum to 3145733 bytes
    When the plan is verified with a recording listener
    Then the listener received EntryChecked for x (done 1, total 2) and then for y (done 2, total 2)
    And the BytesCompared events never decrease and the last has bytesDone 3145733 and bytesTotal 3145733

  @AC-6
  Scenario: A failing listener is detached
    Given the plan of the first scenario
    When the plan is verified with a listener that throws IllegalStateException("boom") on its first event
    Then it returns Success with two IDENTICAL checks and listenerFailure "boom"
    And the listener was called exactly once

  @AC-7
  Scenario: Cancel takes effect inside a large file
    Given the plan of the first scenario
    And a listener that sets the cancel signal on the first BytesCompared event during T/B/y.bin
    When the plan is verified
    Then it returns Success with cancelled true and one check, T/B/x.jpg IDENTICAL
    And the last BytesCompared event before the return reported fewer than 3145733 bytes done

  @AC-7
  Scenario: Cancelled before the start
    Given the plan of the first scenario and a cancel signal that is already true
    When the plan is verified
    Then it returns Success with cancelled true and no checks

  @AC-8
  Scenario: An empty plan and null inputs
    Given the plan for a request with no rules, exceptions or picks
    When it is verified
    Then it returns Success with no checks and cancelled false
    And verify(null, listener, cancel), verify(plan, null, cancel) and verify(plan, listener, null) each throw NullPointerException

  @AC-9
  Scenario: Verifying changes nothing
    Given the plan of the first scenario, and the names, sizes and last-modified times of T's files recorded
    When the plan is verified
    Then T's names, sizes and last-modified times are unchanged

  @AC-10
  Scenario: The factory and the Spring wiring expose the verify
    Given the plan of the first scenario
    When ScannerFactories.verifyPlanContent() verifies it
    Then it returns two IDENTICAL checks
    And an application context with ScannerAutoConfiguration has one VerifyPlanContent bean that verifies the same plan
    And a context that declares its own VerifyPlanContent bean uses that one instead
```

## Scope

**Touches:**
- `domain.usecases.query`: the `VerifyPlanContent` port
- `domain.model`: `VerifyReport`, `OperationCheck`, `Outcome`, `VerifyEvent` and `VerifyListener`
- `application`: the service implementing `VerifyPlanContent`
- `domain.factory`: `ScannerFactories.verifyPlanContent()`
- `infra.port`: `ContentComparator`
- `infra.adapter`: the NIO `ContentComparator` adapter
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`: the `VerifyPlanContent` bean
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: its bean and back-off tests

**Out of scope:**
- The asynchronous job model with start, status, cancel and result (DFS-042).
- Executing, quarantining or deleting anything; the delete-time re-verification stays with execution (CLAUDE.md decision 4).
- Detecting that a candidate became a hard link of its keeper since the scan (moving it would be harmless: the keeper stays).
- Checking recorded sizes and times against the scan (DFS-033).
- The server endpoint (frontend lane).

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `design.md` addresses every item of the `use-case` edge-case checklist or marks it not applicable with a one-line reason (decision 7 lists the expected dispositions)
- [x] Files larger than one chunk are generated in the test, not committed
- [x] Symlinks and junctions are created at test time and their tests skip through `Assumptions` where the platform or privileges do not allow them; nothing platform-specific is committed under `fixtures/`
- [x] No test this ticket adds or changes uses `Thread.sleep`, and no test reads or writes outside a `@TempDir`
- [x] `module-info.java` is unchanged and `ModuleExportsTest` stays green

## Open questions

None.
