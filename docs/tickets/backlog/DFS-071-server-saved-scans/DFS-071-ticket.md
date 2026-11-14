---
id: DFS-071
title: Server saved scans
status: draft
type: feature
scope: []
archetype:
priority: high
lane: frontend
depends_on: [DFS-070]
touches: []
blocked_reason:
created: 2026-11-14
updated: 2026-11-14
completed:
---

# DFS-071: Server saved scans

## Story

As a person exploring scan results in the browser
I want to open any saved scan, including ones made by the CLI, without scanning again
So that I can iterate on the dashboard and review results across server restarts

## Context

Builds on DFS-070 (`ScanArchive`, `LoadScan`, snapshots in `~/.dfs/scans/`). `ScanRegistry` keeps scans only for the server run and the result, overlaps, groups and plan APIs serve a COMPLETED scan.

Decisions made with the user (2026-11-14):
- `ScanRegistry` can hold a scan loaded from a snapshot as COMPLETED, so the existing result APIs work unchanged.
- The server lists the snapshots in the snapshot folder (so CLI scans appear); `POST /api/scans/import {path}` and `--dfs.server.load=<file>` at startup load one.
- The scan screen gets a "Saved scans" list; "Open results" works without a rescan.
- A stable snapshot id (a hash of the header) so the client-side "dealt with" marks (`dfs.results.done.<scanId>`) survive a restart.
- Plan execution already re-verifies against disk, so a stale snapshot is safe; a test shows a changed file is refused.
- `openapi.json` updated with `updateOpenApi`; the dev loop (`bootRun --args="--dfs.server.load=... --server.port=8080"` plus `npm_run_start`) documented in `CLAUDE.md`.

## Acceptance criteria

The full ID of each criterion is `DFS-071.AC-<n>`.

<!-- To be written in refine. -->

## Scenarios

<!-- To be written in refine. -->

## Scope

**Touches:** (to be fixed in refine; expected)
- `server`: `us.pochaev.dfs.server.scan` (registry, import, list), `server/frontend/src/app/scans/`, `server/frontend/openapi.json`
- `CLAUDE.md`: dev loop

**Out of scope:**
- Saving scans (DFS-070)
- Pruning or deleting snapshots from the UI
- SQLite or DuckDB storage

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] `./gradlew :server:e2eTest` passes (the scan and result flow changes)
- [ ] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)

## Open questions

- Is a path-based import a safe API on a localhost server (restrict to the snapshot folder)?
- Does an imported scan appear in `GET /api/scans` with its original start and finish times?
- Archetype, gates, acceptance criteria and scenarios (refine)
