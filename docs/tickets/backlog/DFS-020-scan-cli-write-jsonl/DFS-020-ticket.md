---
id: DFS-020
title: Scan from the CLI and write JSONL
status: draft
type: feature
scope: [cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-019]
touches: [cli/build.gradle.kts, us.pochaev.dfs.cli]
blocked_reason:
created: 2026-10-07
updated: 2026-10-07
completed:
---

# DFS-020: Scan from the CLI and write JSONL

## Story

As a user of the duplicate file scanner
I want to run the CLI on one or more folders and get the scan's results as JSON Lines
So that I can see the duplicates it finds on real disks, and process them with other tools (jq, scripts) before any delete workflow exists

## Context

The scanner library is complete for scanning. `ScannerFactories.scanUseCases(Executor)` (DFS-019) returns `ScanUseCases(StartScan start, CancelScan cancel, GetScanStatus status, GetScanResult result)`, wired to the real NIO adapters. A completed scan's `ScanResult` holds `sets` (`DuplicateSet`: size, SHA-256 digest, files, and physical copies with hard links grouped) and `skipped` (`WalkEvent.Skipped`: path, `SkipReason` `SYMLINK`/`SPECIAL_FILE`/`UNREADABLE`, detail). `StartScan` rejects bad locations up front with `Failure(NO_LOCATIONS | LOCATION_NOT_FOUND | NOT_A_DIRECTORY | LOCATION_UNREADABLE)` (DFS-018).

`cli` today: `Main` is a stub that prints "not implemented yet"; `cli/build.gradle.kts` uses the plain `application` plugin; Spring Boot is not yet added (CLAUDE.md, "Current state"). CLAUDE.md says `cli` is a Spring Boot CLI and the composition root, that it owns the `@Configuration` whose `@Bean`s call the factories (decision 6), that the executor is supplied by the caller (virtual threads for the CLI), and that progress is reported through a listener, never printed by the core. `cli`'s `ArchitectureTest` forbids depending on anything under `us.pochaev.dfs.scanner` outside `domain`. The CLI framework (Spring Shell vs `ApplicationRunner` vs picocli) is still open in CLAUDE.md.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-020.AC-<n>`.

## Scenarios

```gherkin
Feature: Scanning from the CLI

  Scenario: <placeholder; scenarios are written during refinement>
```

## Scope

**Touches:**
- `cli/build.gradle.kts`: Spring Boot (and JSON) dependencies
- `us.pochaev.dfs.cli`: `Main`, the Spring configuration and the runner that scans and writes JSONL

**Out of scope:**
- Deleting, quarantine, preview (later tickets)
- An interactive shell
- The `scanner-spring` autoconfigure module (CLAUDE.md decision 6, later)
- Changes to `file-scanner`

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `./gradlew :cli:run --args="<absolute dir>"` on a real directory prints valid JSONL (each line parses as JSON)

## Open questions

- Archetype: `use-case` fits poorly (DFS-019.L-5) and CLAUDE.md says CLI wiring gets its own archetype; a new `cli`/`wiring` archetype (a CCW ticket first) or `none` (by hand).
- CLI framework: plain Spring Boot `ApplicationRunner`, Spring Shell or picocli (open in CLAUDE.md); recording the choice in CLAUDE.md would add it to touches.
- JSONL schema: one line per duplicate set and one per skipped path with a `type` field, or sets only with skips elsewhere; which fields per line (size, digest, paths, hard-link grouping); absolute or real paths.
- Output destination: stdout only, or also an `--output <file>` option; where progress goes (stderr) and whether it is on by default.
- Arguments and options: several locations; whether to expose `ScanConfig` (minimum size) as options.
- Exit codes: rejected location, failed scan, success with and without duplicates.
- Waiting for completion: how the runner waits for the async scan (listener plus latch, or polling `status`) and what Ctrl+C does (`CancelScan`).
- Testing: how scenarios run (calling the runner in-process with a `@TempDir` and capturing stdout, or `@SpringBootTest`); what the `:cli:run` done-item proves.
