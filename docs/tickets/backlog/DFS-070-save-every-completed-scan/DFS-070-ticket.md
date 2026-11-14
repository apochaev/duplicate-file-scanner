---
id: DFS-070
title: Save every completed scan
status: draft
type: feature
scope: [file-scanner, cli]
archetype:
priority: high
lane: backend
depends_on: [DFS-030]
touches: []
blocked_reason:
created: 2026-11-14
updated: 2026-11-14
completed:
---

# DFS-070: Save every completed scan

## Story

As a person who scans large disks
I want every completed scan, from the CLI or the server, saved to a file automatically
So that I can look at it again, or build on it, without repeating the expensive scan

## Context

Today a scan lives only in memory (`InMemoryScanRepository`, `ScanRegistry`); the CLI writes JSONL only when asked (`--output` or stdout redirect) and the server never writes results. The DFS-030 JSONL (schemaVersion 2: `scan`, `folder`, `set`, `skipped`, `summary`) is already a complete export, read by `tools/analysis/overlaps.sql` in DuckDB.

Decisions made with the user (2026-11-14):
- JSONL is the only snapshot format; no SQLite or DuckDB store now.
- The save belongs in the core so CLI and server cannot forget it: a `ScanArchive` secondary port (`infra.port`) with a `JsonlScanArchive` adapter (`infra.adapter`), the export writer moved out of `cli` into the core, `ScanService` saving on COMPLETED, wired by `ScannerFactories` and `ScannerAutoConfiguration`.
- Default folder `~/.dfs/scans/`, files `<yyyyMMdd-HHmmss>-<short id>.jsonl`; override `--snapshot-dir` (cli) and `dfs.snapshots.dir` (server); `--no-save` opts out in the cli.
- Atomic write (temp file, then rename, as DFS-029); only COMPLETED scans are saved; a failed save is reported but never fails the scan or changes the exit code.
- `--output` and `--format` are unchanged.
- A `LoadScan` use case reads a snapshot back into a `ScanResult`, for DFS-071.
- No pruning (a later `dfs scans list/prune`).
- First step: verify the JSONL carries everything the server index needs, above all hard-link identity (the "N links" badge). Anything missing is added as new fields without a `schemaVersion` bump.

## Acceptance criteria

The full ID of each criterion is `DFS-070.AC-<n>`.

<!-- To be written in refine. -->

## Scenarios

<!-- To be written in refine. -->

## Scope

**Touches:** (to be fixed in refine; expected)
- `file-scanner`: `ScanArchive` port, `JsonlScanArchive`, `SaveScan`/`LoadScan` use cases, `ScanService`, `ScannerFactories`
- `scanner-spring`: `ScannerAutoConfiguration`
- `cli`: `--snapshot-dir`, `--no-save`, export writer moved to the core

**Out of scope:**
- Server snapshot list, import and UI (DFS-071)
- Pruning old snapshots
- SQLite or DuckDB storage

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)
- [ ] Round trip: a saved snapshot loads back into an equal `ScanResult`, hard-link groups included

## Open questions

- Does the JSONL already carry hard-link identity and everything the server index uses? (first task)
- Short id: random, or a hash of the header? (DFS-071 wants a stable snapshot id)
- Archetype, gates, acceptance criteria and scenarios (refine)
