---
id: DFS-019
title: Wire ScannerFactories
status: ready
type: feature
scope: [file-scanner]
archetype: use-case
priority: medium
depends_on: [DFS-018]
touches: [us.pochaev.dfs.scanner.domain.factory, file-scanner/src/test/java/us/pochaev/dfs/scanner/ArchitectureTest.java]
blocked_reason:
created: 2026-09-27
updated: 2026-10-07
completed:
---

# DFS-019: Wire ScannerFactories

## Story

As a caller of the scanner library (the CLI's Spring configuration, later a server)
I want static factories that hand me the scan use cases as port interfaces
So that I can use the scanner without depending on its `application` or `infra` classes (swapping a secondary port is deferred, decision 3)

## Context

Split on 2026-09-27 from the original DFS-005. CLAUDE.md design decision 5: plain static factories in `domain.factory` instantiate application and adapter classes directly (compile-time checked, works on the classpath and the module path); no `ServiceLoader`. Spring-friendliness rules: factories return port interfaces and accept optional overrides for secondary ports. `ScannerFactories` exists today as an empty class. `domain.factory` is the one domain package allowed to depend on `application` and `infra.adapter`.

### Decisions (refinement, 2026-10-07)

1. **Archetype `use-case`.** A factory adds no use case, but the archetype's checklist covers what wiring needs (exported surface, executor contract, state placement, testability); the items about status, races and IDs are marked not applicable in `design.md`. Whether wiring deserves its own archetype is left to this story's retro. Running through the pipeline also makes this ticket CCW-012's live run (CCW-012 decision 7).
2. **One build returns a record of the ports.** `ScannerFactories.scanUseCases(Executor executor)` returns `ScanUseCases(StartScan start, CancelScan cancel, GetScanStatus status, GetScanResult result)`, a record in `domain.factory` (already exported), all four backed by one `ScanService` and so one `ScanRepository`. Separate factory calls would give separate repositories, and a scan started through one would be `UNKNOWN_SCAN` to another. A combined interface was rejected: `application` may not depend on `domain.factory`, so `ScanService` could not implement it, and a delegating wrapper would have to track every use case. Spring maps it as one `@Bean ScanUseCases` plus one-line `@Bean`s that unpack it (out of scope here). Each call builds a fresh, independent set.
3. **No secondary-port overrides yet; the executor is the only argument.** `scanUseCases(Executor)` always wires the default adapters: `NioLocationResolver`, `NioFileTreeWalker`, `NioContentHasher`, `NioFileIdentity` and `InMemoryScanRepository`. This defers part of CLAUDE.md's Spring-friendliness rule ("factories ... accept optional overrides for secondary ports"), which no caller outside the module can use today: every secondary port is in `infra.port`, which `module-info` never exports, `ModuleExportsTest` forbids exporting, and `cli`'s ArchUnit rule forbids depending on. Tests inside `file-scanner` build `ScanService` directly and need no override. The override design, including whether `infra.port` or only `ScanRepository` is exported, belongs with its first real consumer, probably a server's persistent `ScanRepository` under CLAUDE.md decision 6's `scanner-spring` module.
4. **The executor is required and passed straight through.** `scanUseCases(null)` throws `NullPointerException` (a caller bug, use-case checklist item 2). There is no default executor and no overload without one: the factory hands the caller's executor to `ScanService` unchanged and creates no thread or executor of its own, so the executor's lifecycle stays with whoever created it (CLAUDE.md, "The executor is supplied by the caller").
5. **`allowEmptyShould(true)` comes off the layer rule and the no-Spring rule, and stays on the top-level `util` rule.** Every layer now holds a real class (Factory holds `ScannerFactories` and, after this ticket, `ScanUseCases`), and the no-Spring rule checks every class in the module, so a mistyped package pattern now fails loudly there (CLAUDE.md, "Current state and open items"). The top-level `util` package holds only `package-info.java`, so its rule keeps the flag, with a one-line comment saying why, until `util` gets a class. A removed flag is a done-item checked by search, not a criterion: no test can carry the ID of its own absence (DFS-003.L-3).
6. **No `module-info.java` change.** `ScanUseCases` goes in `domain.factory`, which is already exported, so no new package and no new `exports` line are needed.

The scanner ArchUnit rules used `allowEmptyShould(true)` so they passed while layers were empty; decision 5 settles which keep it.

## Acceptance criteria

The full ID of each criterion is `DFS-019.AC-<n>`.

- **AC-1** `ScannerFactories.scanUseCases(executor)` returns a `ScanUseCases` whose `start`, `cancel`, `status` and `result` are all non-null (decision 2).
- **AC-2** The four ports of one `ScanUseCases` share one scan state: a scan ID returned by its `start` is known to its `status`, `result` and `cancel` (decision 2).
- **AC-3** Two calls to `scanUseCases` return independent sets: a scan ID from one set's `start` is `Failure(UNKNOWN_SCAN)` to the other set's `status` (decision 2).
- **AC-4** `scanUseCases` wires the real file-system adapters: a scan started through it walks, hashes and compares files on disk, excludes empty files, does not count two hard links to one file as duplicates, and reports a location that does not exist as `Failure(LOCATION_NOT_FOUND)` (decision 3).
- **AC-5** `scanUseCases(null)` throws `NullPointerException` (decision 4).
- **AC-6** Scans run on the executor passed to `scanUseCases`: with an executor that holds submitted tasks, a started scan's `status` is `QUEUED` until the held task is run, then `COMPLETED` (decision 4).

## Scenarios

```gherkin
Feature: Wiring the scanner

  @AC-1
  Scenario: The factory returns all four use cases
    Given an executor that runs each task on the calling thread
    When ScannerFactories.scanUseCases is called with that executor
    Then the returned ScanUseCases has a non-null start, cancel, status and result

  @AC-2
  Scenario: The four ports share one scan state
    Given the ScanUseCases built with an executor that runs each task on the calling thread
    And a temporary directory holding "a.txt" and "b.txt" with the same 10 bytes
    When start is called with that directory and ScanConfig.defaults()
    Then it returns Success with a scan ID
    And status for that ID returns Success with status COMPLETED
    And result for that ID returns Success holding one duplicate set with "a.txt" and "b.txt"
    And cancel for that ID returns Success with status COMPLETED

  @AC-3
  Scenario: Separate builds do not share state
    Given two ScanUseCases built by two calls to scanUseCases
    When a scan is started through the first
    Then status for its ID through the second returns Failure with UNKNOWN_SCAN

  @AC-4
  Scenario: The default adapters read the real disk
    Given the ScanUseCases built with an executor that runs each task on the calling thread
    And a temporary directory holding:
      | file           | content               |
      | dup/a.txt      | the same 10 bytes     |
      | dup/b.txt      | the same 10 bytes     |
      | empty/x.txt    | 0 bytes               |
      | empty/y.txt    | 0 bytes               |
      | unique/c.txt   | 10 different bytes    |
    And "linked/l1.bin" with 20 bytes and "linked/l2.bin" a hard link to it, created at test time (skipped when the platform refuses hard links)
    When a scan of that directory with ScanConfig.defaults() completes
    Then result returns exactly one duplicate set, holding "dup/a.txt" and "dup/b.txt"

  @AC-4
  Scenario: A missing location is rejected by the real resolver
    Given the ScanUseCases built with any executor
    When start is called with a path inside the temporary directory that does not exist
    Then it returns Failure with LOCATION_NOT_FOUND

  @AC-5
  Scenario: A null executor is a caller bug
    When ScannerFactories.scanUseCases is called with null
    Then it throws NullPointerException

  @AC-6
  Scenario: Scans run on the caller's executor
    Given the ScanUseCases built with an executor that holds every submitted task until the test runs it
    And a temporary directory holding "a.txt" and "b.txt" with the same 10 bytes
    When start is called with that directory and ScanConfig.defaults()
    Then status for the returned ID returns Success with status QUEUED
    And exactly one task is held by the executor
    When the test runs the held task
    Then status for that ID returns Success with status COMPLETED
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.factory`: `ScannerFactories`, new record `ScanUseCases`
- `file-scanner/src/test/java/us/pochaev/dfs/scanner/ArchitectureTest.java`: drop `allowEmptyShould(true)` from two rules (decision 5)

**Out of scope:**
- `module-info.java`: no new package is exported (decision 6)
- `CLAUDE.md`'s "Current state and open items" notes on the empty `ScannerFactories` and on `allowEmptyShould(true)`: updated by hand when the ticket closes, outside the pipeline
- Overriding secondary ports through the factory (decision 3): deferred to the first consumer outside the module that needs one
- Any CLI or Spring wiring in the `cli` subproject
- The `scanner-spring` autoconfigure module (CLAUDE.md decision 6, later)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `ArchitectureTest`'s layer rule and no-Spring rule no longer call `allowEmptyShould(true)`, its top-level `util` rule still does with a comment saying why, and all three pass (decision 5; checked by search and by the build)
- [ ] Every item of the `use-case` archetype's edge-case checklist is addressed in `design.md` or marked not applicable with a one-line reason
- [ ] `ScannerFactories` has no fields and `ScanUseCases` holds only the four ports; each call builds a fresh set (decision 2)

## Open questions

None.
