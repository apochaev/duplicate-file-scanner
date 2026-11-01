---
id: DFS-033
title: Check a deletion plan for drift since the scan
status: in-review
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [done]
autorun: true
priority: medium
lane: backend
depends_on: [DFS-022]
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
completed:
---

# DFS-033: Check a deletion plan for drift since the scan

## Story

As a user reviewing a previewed deletion plan
I want to know which of its files changed, moved or vanished since the scan
So that I do not act on a plan built from stale information

## Context

`docs/design/ui.md` U7.2: a cheap, read-only check that re-reads the size and modification time of every entry of a previewed plan (DFS-022), both candidates and keepers, and compares them with what the scan recorded. It is a quick early warning, not the delete-time proof: that stays the byte compare at execution (CLAUDE.md decision 4) and DFS-034's content verify.

What exists (DFS-022, DFS-032, both on `main`): `DeletionPlan(id, operations, conflicts, totals, leftBehind)` whose only operation is `Operation.Quarantine(digest, size, links, keeper, source)`, holding paths but no modification times; the recorded size and time of each path live in the scan, as `FileEntry(path, size, lastModified)` inside each `DuplicateSet`'s `copies`. There is no plan repository: the caller keeps the plan it previewed (DFS-022 decision 1). `PreviewDeletionPlan` and `ListDirectory` are each exposed by their own `ScannerFactories` method and a `ScannerAutoConfiguration` bean; `ErrorCode` holds the expected failures.

### Decisions

1. **Input: the scan and the plan, like the preview.** `CheckPlanDrift.check(ScanResult scan, DeletionPlan plan)` returns `Result<DriftReport>`. The plan carries paths but not recorded times, and DFS-022's model and pinned plan-ID encoding stay unchanged; the server holds both values already (`GetScanResult`, then the previewed plan). Checking existence and size alone would miss a file edited in place at the same size, which U7.2 asks to catch.
2. **One entry per drifted path, with one kind.** `DriftReport(String planId, int checked, List<PathDrift> drifts)`: `planId` is the plan's `id`, `checked` the number of distinct paths read, `drifts` only the paths that drifted (a clean plan has none). `PathDrift(Path path, Role role, String digest, DriftKind kind, String detail)`; `Role` is `CANDIDATE` (a link of an operation) or `KEEPER`; `DriftKind` is, in this precedence when several apply: `MISSING` (no longer exists), `NOT_A_REGULAR_FILE` (now a symlink, directory, junction or other special file), `UNREADABLE` (its attributes cannot be read; `detail` holds the exception's message, or its `toString()` when blank), `SIZE_CHANGED`, `MODIFIED_CHANGED` (same size, different last-modified time). `detail` is empty except for `UNREADABLE`. Times compare exactly as `Instant`s: both readings come from the same file system through the same NIO call.
3. **Order and de-duplication.** Paths are checked in plan order: for each operation, its links in order, then its keeper. A path met again (a keeper shared by several operations) is checked and reported once, at its first position, and counted once in `checked`. A plan's `conflicts` have no operations, so they add nothing to check.
4. **A plan that does not match the scan is refused before any read.** If an operation's digest is not a set of the scan, or one of its links or its keeper is not a path of that set's copies, `check` returns `Failure(PLAN_NOT_FROM_SCAN)` (new `ErrorCode`) naming the first such path or digest, and reads nothing from disk. A plan with no operations returns `Success` with `checked` 0 and no drifts. A `null` argument throws `NullPointerException`.
5. **Read-only, nothing followed, synchronous.** Each path's attributes are read once, without following links; no file content is read (so a cloud placeholder is not downloaded) and nothing is written. One bad path never fails the check. The check runs on the caller's thread: a plan of a few thousand paths costs a few thousand attribute reads, so no executor, progress or cancel (asynchronous checking is out of scope).
6. **A new secondary port.** `infra.port.FileAttributesReader` (one method reading one path's `BasicFileAttributes` without following links, throwing `IOException`) and its adapter `infra.adapter.NioFileAttributesReader` (`Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)`). The application maps a `NoSuchFileException` to `MISSING`, any other `IOException` to `UNREADABLE`, and attributes that are not a regular file to `NOT_A_REGULAR_FILE`. The service has no field other than its port.
7. **Exposure like DFS-022 and DFS-032.** `ScannerFactories.checkPlanDrift()` returns a `CheckPlanDrift` on the NIO adapter, and `ScannerAutoConfiguration` gains a `CheckPlanDrift` bean with `@ConditionalOnMissingBean`. Use-case checklist items 1, 3, 4, 6, 7, 8, 9, 11 and 12 do not apply: the check takes no ID to look up, keeps no state, calls no caller code and runs synchronously.
8. **Gates `[done]`.** The check only reads; it neither plans nor deletes, and execution still re-verifies (CLAUDE.md decision 4).

## Acceptance criteria

The full ID of each criterion is `DFS-033.AC-<n>`.

- **AC-1** For a plan whose files are all unchanged since the scan, `check` returns `Success` with the plan's id, `checked` equal to the number of distinct candidate and keeper paths, and no drifts.
- **AC-2** A candidate or keeper that no longer exists is a `PathDrift` of kind `MISSING` with its role and its operation's digest.
- **AC-3** A file whose size changed is `SIZE_CHANGED`; one whose size is the same but whose last-modified time changed is `MODIFIED_CHANGED`; one where both changed is a single `SIZE_CHANGED` entry.
- **AC-4** A path that is now a symlink, a directory or (on Windows) a junction is `NOT_A_REGULAR_FILE`, and nothing behind it is read.
- **AC-5** A path whose attributes cannot be read is `UNREADABLE` with a non-empty detail, and every other path is still checked.
- **AC-6** Drifts appear in plan order (each operation's links, then its keeper), and a keeper shared by several operations is checked, counted and reported once.
- **AC-7** A plan with a digest not in the scan, or a link or keeper not among that set's copies, returns `Failure(PLAN_NOT_FROM_SCAN)` and reads nothing from disk; a plan with no operations returns `Success` with `checked` 0; a `null` scan or plan throws `NullPointerException`.
- **AC-8** Checking changes nothing on disk: names, sizes and last-modified times of the checked files are the same before and after.
- **AC-9** `ScannerFactories.checkPlanDrift()` returns a working `CheckPlanDrift`, and `ScannerAutoConfiguration` provides a `CheckPlanDrift` bean that backs off when the application declares its own.

## Scenarios

In every scenario, "a scan of T" is a `ScanResult` built from the real files in a temp folder T, recording each file's size and last-modified time as they were when it was built, and "the plan" is the `DeletionPlan` that `ScannerFactories.previewDeletionPlan()` returns for the request given.

```gherkin
Feature: Plan drift check

  @AC-1
  Scenario: An unchanged plan has no drift
    Given temp folder T with A/x.jpg and B/x.jpg (5 bytes each, one set d1) and A/y.jpg and B/y.jpg (7 bytes each, set d2)
    And a scan of T and the plan for rule KeepAndQuarantine(T/A, T/B)
    When the plan is checked against the scan
    Then it returns Success with the plan's id, checked 4 and no drifts

  @AC-2
  Scenario: A deleted candidate and a deleted keeper are both reported
    Given the scan and plan above
    And T/B/x.jpg and T/A/y.jpg are deleted afterwards
    When the plan is checked
    Then the drifts are T/B/x.jpg (CANDIDATE, d1, MISSING) and T/A/y.jpg (KEEPER, d2, MISSING)

  @AC-3
  Scenario: Size and time changes are told apart
    Given temp folder T with A/x.jpg, B/x.jpg (set d1), A/y.jpg, B/y.jpg (set d2) and A/z.jpg, B/z.jpg (set d3), a scan of T and the plan for rule KeepAndQuarantine(T/A, T/B)
    And afterwards one byte is appended to T/B/x.jpg and its last-modified time is set back to the recorded one
    And T/B/y.jpg gets a new last-modified time with its bytes unchanged
    And one byte is appended to T/B/z.jpg and its last-modified time moves forward
    When the plan is checked
    Then T/B/x.jpg is SIZE_CHANGED, T/B/y.jpg is MODIFIED_CHANGED and T/B/z.jpg is a single SIZE_CHANGED entry

  @AC-4
  Scenario: A file replaced by a link or a folder is not followed
    Given the scan and plan of the first scenario
    And afterwards T/B/x.jpg is replaced by a symlink to a file holding different bytes (skipped when the platform does not allow it)
    And T/B/y.jpg is replaced by a folder
    When the plan is checked
    Then both are NOT_A_REGULAR_FILE
    And on Windows a candidate replaced by a junction is also NOT_A_REGULAR_FILE (skipped on other platforms)

  @AC-5
  Scenario: An unreadable path does not stop the check
    Given the scan and plan of the first scenario
    And an attributes reader that fails with AccessDeniedException for T/B/x.jpg only (a fake FileAttributesReader, as design.md decides)
    When the plan is checked
    Then T/B/x.jpg is UNREADABLE with a non-empty detail
    And T/A/x.jpg, T/A/y.jpg and T/B/y.jpg were still checked, giving checked 4

  @AC-6
  Scenario: A shared keeper is reported once, in plan order
    Given temp folder T with K/x.jpg, B/x.jpg and C/x.jpg in one set d1, a scan of T
    And the plan for rules KeepAndQuarantine(T/K, T/B) and KeepAndQuarantine(T/K, T/C)
    And T/K/x.jpg is deleted afterwards
    When the plan is checked
    Then checked is 3 and the drifts are exactly one entry, T/K/x.jpg (KEEPER, d1, MISSING)

  @AC-6
  Scenario: Drifts follow plan order
    Given the scan and plan of the first scenario, with T/B/y.jpg and T/B/x.jpg both deleted afterwards
    When the plan is checked
    Then the drifts list T/B/x.jpg before T/B/y.jpg, the order of the plan's operations

  @AC-7
  Scenario: A plan from another scan is refused without reading the disk
    Given a scan of T and the plan of the first scenario
    And a second scan of another temp folder U holding the same names
    When the plan is checked against the scan of U, through an attributes reader that records every call
    Then it returns Failure with code PLAN_NOT_FROM_SCAN naming a path of T
    And the reader recorded no call

  @AC-7
  Scenario: An empty plan and null inputs
    Given a scan of T and the plan for a request with no rules, exceptions or picks
    When the plan is checked
    Then it returns Success with checked 0 and no drifts
    And check(null, plan) and check(scan, null) each throw NullPointerException

  @AC-8
  Scenario: Checking changes nothing
    Given the scan and plan of the first scenario, and the names, sizes and last-modified times of T's files recorded
    When the plan is checked
    Then T's names, sizes and last-modified times are unchanged

  @AC-9
  Scenario: The factory and the Spring wiring expose the check
    Given the scan and plan of the first scenario
    When ScannerFactories.checkPlanDrift() checks the plan
    Then it returns Success with checked 4
    And an application context with ScannerAutoConfiguration has one CheckPlanDrift bean that checks the same plan
    And a context that declares its own CheckPlanDrift bean uses that one instead
```

## Scope

**Touches:**
- `domain.usecases.query`: the `CheckPlanDrift` port
- `domain.model`: `DriftReport`, `PathDrift`, `Role` and `DriftKind` (as nested or top-level types, the architect's choice), `ErrorCode.PLAN_NOT_FROM_SCAN`
- `application`: the service implementing `CheckPlanDrift`
- `domain.factory`: `ScannerFactories.checkPlanDrift()`
- `infra.port`: `FileAttributesReader`
- `infra.adapter`: `NioFileAttributesReader`
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`: the `CheckPlanDrift` bean
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: its bean and back-off tests

**Out of scope:**
- Reading or comparing file content (DFS-034), and any change to disk.
- Asynchronous checking with progress or cancel (decision 5).
- Storing plans or looking them up by ID (DFS-022 decision 1).
- Changing `DeletionPlan` or its plan-ID encoding.
- The server endpoint (frontend lane).

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `design.md` addresses every item of the `use-case` edge-case checklist or marks it not applicable with a one-line reason (decision 7 lists the ones expected not to apply)
- [x] Symlinks and junctions are created at test time and their tests skip through `Assumptions` where the platform or privileges do not allow them; nothing platform-specific is committed under `fixtures/`
- [x] No test this ticket adds or changes uses `Thread.sleep`, and no test reads or writes outside a `@TempDir`
- [x] `module-info.java` is unchanged and `ModuleExportsTest` stays green

## Open questions

None.
