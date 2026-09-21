---
id: DFS-005
title: Start scans and wire the factories
status: draft
type: feature
scope: [file-scanner]
priority: medium
depends_on: [DFS-004]
touches: [us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.domain.usecases.command, us.pochaev.dfs.scanner.domain.usecases.query, us.pochaev.dfs.scanner.domain.factory, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-005: Start scans and wire the factories

## Story

As a caller of the scanner library (the CLI, later a server)
I want to start a scan and receive a scan ID immediately
So that I can track progress, cancel it, and read the duplicate sets once it finishes, without blocking

## Context

Composes the walker and the stages from DFS-001 to DFS-004 into a scan. Provisional port sketch (CLAUDE.md): commands `StartScan` (returns a scan ID immediately) and `CancelScan`; queries `GetScanStatus` and `ListDuplicateSets`; secondary port `ScanRepository` with an in-memory adapter first. Spring-friendliness rules apply: the executor is supplied by the caller, scan state lives behind `ScanRepository` so several scans run concurrently (no state in singleton fields), progress goes through a listener and is never printed, and a config record in `domain.model` (minimum size, symlink policy, quarantine folder, parallelism) is what `@ConfigurationProperties` can map onto. `ScannerFactories` (design decision 5) returns port interfaces and accepts optional secondary-port overrides. `ScanId`, `ScanStatus`, `CancelScan`, `GetScanStatus` and the empty `ScannerFactories` already exist as a first slice and are not final designs.

This ticket is probably too large for one agent and is expected to be split during refinement (config record; `ScanRepository`; `StartScan` with progress and cancel; `ListDuplicateSets`; factories).

## Scenarios

```gherkin
Feature: Starting and tracking a scan

  Scenario: Starting a scan returns immediately
    Given a start location containing identical files in two folders
    When StartScan is invoked with a caller-supplied executor
    Then a scan ID is returned before the scan has finished

  Scenario: A finished scan exposes its duplicate sets
    Given a started scan over that location
    When the scan finishes
    Then GetScanStatus reports it as completed
    And ListDuplicateSets returns one duplicate set with both paths

  Scenario: Concurrent scans do not share state
    Given two scans started over different locations
    When both finish
    Then each scan's duplicate sets contain only files from its own location

  Scenario: Cancelling stops a running scan
    Given a running scan
    When CancelScan is invoked
    Then the walker is told to stop
    And GetScanStatus reports it as cancelled

  Scenario: Progress goes through the listener
    Given a scan started with a progress listener
    When the scan runs
    Then the listener receives progress events
    And nothing is printed by the core
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: scan config record, status and result types
- `us.pochaev.dfs.scanner.domain.usecases.command` and `.query`: `StartScan`, `CancelScan`, `GetScanStatus`, `ListDuplicateSets`
- `us.pochaev.dfs.scanner.domain.factory`: `ScannerFactories`
- `us.pochaev.dfs.scanner.application`: scan orchestration
- `us.pochaev.dfs.scanner.infra.port` and `.adapter`: `ScanRepository` and its in-memory adapter

**Out of scope:**
- Location overlaps, deletion plans, quarantine and undo/purge
- Any CLI or Spring wiring in the `cli` subproject

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `allowEmptyShould(true)` in the scanner ArchUnit rules is reconsidered now that `application` and `infra` have classes

## Open questions

- Scenarios are a first draft derived from CLAUDE.md only; review and complete them.
- Split into smaller tickets (see Context)?
- Status model (which states), progress listener shape, and config record fields with their defaults. The config record's minimum size defaults to 1 (empty files excluded) and is passed explicitly to `SizeIndex(long minSize)` (DFS-001), which has its own no-arg default of 1.
- `ListDuplicateSets` query shape: paging, ordering, filtering.
- Handling of partial failures and skipped entries: how are they surfaced to the caller?
- Threading: this ticket decides how `SizeIndex` (DFS-001, mergeable and not thread-safe) is used: one instance sequentially now. Keep the walker's listener contract open to per-worker sinks, because DFS-006 wants one index per worker merged at the end.
- Start locations: normalize them up front by dropping any location nested inside another (or equal to another), so a file is never walked twice. `SizeIndex` (DFS-001) collapses equal paths within a group only as a safeguard.
