---
id: DFS-018
title: Track scans asynchronously
status: draft
type: feature
scope: [file-scanner]
archetype:
priority: medium
depends_on: [DFS-005]
touches: [us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.domain.usecases.command, us.pochaev.dfs.scanner.domain.usecases.query, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-27
updated: 2026-09-27
completed:
---

# DFS-018: Track scans asynchronously

## Story

As a caller of the scanner library (the CLI, later a server)
I want to start a scan and receive a scan ID immediately
So that I can track progress, cancel it, and read the duplicate sets once it finishes, without blocking

## Context

Split on 2026-09-27 from the original DFS-005. Runs DFS-005's synchronous scan pipeline on a caller-supplied executor and tracks each scan by `ScanId`. Provisional port sketch (CLAUDE.md): commands `StartScan` (returns a scan ID immediately) and `CancelScan`; queries `GetScanStatus` and `ListDuplicateSets`; secondary port `ScanRepository` with an in-memory adapter first. Spring-friendliness rules apply: the executor is supplied by the caller, and scan state lives behind `ScanRepository` so several scans run concurrently (no state in singleton fields). `ScanId`, `ScanStatus` (RUNNING, COMPLETED, CANCELLED, FAILED), `CancelScan` and `GetScanStatus` already exist as a first slice and are not final designs.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-018.AC-<n>`.

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
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: status and result types, if they change
- `us.pochaev.dfs.scanner.domain.usecases.command` and `.query`: `StartScan`, `CancelScan`, `GetScanStatus`, `ListDuplicateSets`
- `us.pochaev.dfs.scanner.application`: the scan lifecycle service implementing the use cases
- `us.pochaev.dfs.scanner.infra.port` and `.adapter`: `ScanRepository` and its in-memory adapter

**Out of scope:**
- The scan pipeline itself (DFS-005)
- `ScannerFactories`, `module-info` exports, `allowEmptyShould(true)` (DFS-019)
- Location overlaps, deletion plans, quarantine and undo/purge
- Any CLI or Spring wiring in the `cli` subproject

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Archetype.
- Scenarios are a first draft derived from CLAUDE.md only; review and complete them.
- Status model: are the existing four `ScanStatus` values enough (for example, a queued state before the executor picks the scan up)?
- `ListDuplicateSets` query shape: paging, ordering, filtering; what it returns for a scan that is still running, cancelled or failed.
- What `GetScanStatus` and `CancelScan` do for an unknown `ScanId`.
- If this ticket adds a new exported package, `file-scanner/src/main/java/module-info.java` must be listed explicitly in `touches` (a package entry does not cover it; see CCW-003).
