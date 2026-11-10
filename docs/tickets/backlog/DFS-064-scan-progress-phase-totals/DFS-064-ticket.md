---
id: DFS-064
title: Keep each scan phase's totals and the finish time in the progress stream
status: ready
type: feature
scope: [build]
archetype: endpoint
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
priority: medium
lane: backend
depends_on: []
touches:
  - us.pochaev.dfs.server.scan
  - server/src/test/java/us/pochaev/dfs/server
  - server/frontend/openapi.json
  - server/frontend/src/app/scans
  - server/frontend/src/app/results
  - CLAUDE.md
blocked_reason:
created: 2026-11-09
updated: 2026-11-09
completed:
---

# DFS-064: Keep each scan phase's totals and the finish time in the progress stream

## Story

As the page that shows a running scan
I want `ScanProgress` to keep the totals of every finished phase
So that the redesigned progress row (DFS-063) can show one totals line per finished phase, and "finishing…" from the phase and the byte counts instead of a frozen bar

## Context

The product owner redesigned the scan progress row in Claude Design on 2026-11-08 after a real scan of a 900 GB drive: the bar and percentage restarted in every phase, and sat at 100% for a long time while files were still being hashed. The design canvas is https://claude.ai/artifact/EuSd18FxAYTobP38cx3JF3 (`project/Main.dc.html` holds the rules). It replaces DFS-057's per-phase lines and absorbs DFS-058's finished-phase history (DFS-058 is cancelled in favour of DFS-064 and DFS-063).

**What the stream already sends** (`ScanProgress`, `us.pochaev.dfs.server.scan`, DFS-040 decision 3): `phase`, walk counts (`directories`, `files`, `bytes`), the current hashing phase's `hashedFiles`/`hashFilesTotal` and `hashedBytes`/`hashBytesTotal`, and `groupsDone`/`groupsTotal`. The 30-second bytes rate and the ETA are computed in the browser (DFS-057 decision 2), so no rate is needed on the server.

**What is missing:** the hash and group counts belong to the *current* phase and are overwritten when the next one starts, so the page cannot show "29594 compared → 13845 candidates" for a finished phase. `ScanRegistry`'s listener folds `ScanEvent`s (`PhaseStarted`, `HashProgress`, `GroupDone`) and could close each phase's totals when the next starts. The "nothing left to read but still finishing" state needs no field (decision 1).

Any change to `ScanProgress` is a spec change (`./gradlew :server:updateOpenApi`, commit `server/frontend/openapi.json`).

## Decisions

1. **No `finishing` field** (2026-11-09). `HashProgress.bytesDone` reaches `bytesTotal` only when the last file ends, and the phase stays `FULL_HASH` until `HARD_LINKS` starts, so the page derives "finishing" from `phase == FULL_HASH && hashedBytes == hashBytesTotal`. A test pins that sequence in `ScanProgress`. The old bar's long stay at 100% is explained by this gap plus the hard-link work and result assembly; that is a hypothesis, confirmed by a by-hand run on a large drive (Definition of done). If the run shows a silent gap with no phase change, a field comes in a follow-up. Rejected: a `finishing` boolean (the server would need a core event or a guess to flip it) and a spike first (blocks DFS-063 on a long manual run).

2. **A `phases` list in `ScanProgress`; the flat fields stay** (2026-11-09). One entry per phase that has started, in run order (`WALK`, `PARTIAL_HASH`, `FULL_HASH`, `HARD_LINKS`), each with `phase`, `finished`, and its own totals: walk `directories`, `files`, `bytes` found; each hash phase `files` and `bytes` of its input (the `HashProgress` totals); hard links `groups`. A phase finishes when the next one starts, or when the scan reaches COMPLETED. An entry keeps its totals after the next phase starts, which the flat hash fields do not (the registry resets them at `PhaseStarted`). The registry's `Counters` fills the list; the core is unchanged. A hash phase that has started but sent no `HashProgress` yet has totals 0, which the page reads as unknown. The architect names the fields that do not apply to a phase (nullable or 0). The flat fields remain so the DFS-057 row works until DFS-063 replaces it; a later ticket may drop them. Rejected: flat per-phase fields (about eight new names beside `hashFilesTotal`, no place for timing) and replacing the flat fields now (breaks the DFS-057 page, forcing DFS-063 into this change).

3. **`phases` stays on a COMPLETED scan; `ScanEntry` gets one `finishedAt`** (2026-11-09). The collapsed Completed row needs the totals and the duration, and the page may open after the scan ended, so the duration cannot be measured in the browser. `finishedAt` is null until the scan reaches COMPLETED, CANCELLED or FAILED, then fixed. The core has no finish time (`ScanSnapshot`, `ScanResult` and the events carry no instant, and the CLI stamps its own), so the server stamps it from an injectable `Clock` in `ScanRegistry` (today `Instant.now()` at `ScanRegistry.java:58`). The stamp is taken when the scan ends, not when someone first reads it; the architect chooses the hook. Rejected: per-phase start and finish times (the canvas shows only a total duration; add them when a screen needs them) and a duration measured by the page (wrong when the page opens late).

4. **The new fields are required in the spec** (2026-11-09). `ScanProgress.phases` is always present (an empty list before the first phase starts) and `ScanEntry.finishedAt` is required and nullable (`requiredMode = REQUIRED`, `nullable`). The generated client types then say what the server does, and the frontend literals that build a `ScanProgress` or `ScanEntry` (`scan-row.spec.ts`, `scan-screen.spec.ts`, `scan-eta.spec.ts`, `scan-phase.spec.ts`, `scan-events.service` users, `result-screen.spec.ts`, `result-test-data.ts`) gain both fields in this ticket, so the frontend build stays green. Overlap with DFS-063 is safe because DFS-063 depends on this ticket. Rejected: optional fields (every reader checks for a case the server never produces).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-064.AC-<n>`.

- **AC-1** After the last file of the full-hash phase is hashed and before the hard-link phase starts, `ScanProgress` reports `phase` `FULL_HASH` with `hashedBytes` equal to `hashBytesTotal` and `hashedFiles` equal to `hashFilesTotal`; it never reports more than `hashBytesTotal`.
- **AC-2** `ScanProgress.phases` lists each phase that has started, in run order, and a phase's entry keeps its totals after the next phase starts: the walk's directories, files and bytes found; each hash phase's input files and bytes; the hard-link phase's groups. Entries of earlier phases have `finished` true, and the current phase's entry `finished` false.
- **AC-3** The flat fields `ScanProgress` already has keep their values and meaning (the walk counts, the current hash phase's counters, `groupsDone` and `groupsTotal`).
- **AC-4** A scan entry's `finishedAt` is null while the scan is RUNNING or CANCELLING, and holds the time the scan ended once it is COMPLETED, CANCELLED or FAILED, not before `startedAt` and the same on every later read; a COMPLETED scan's entry still lists `phases` with their totals.
- **AC-5** `GET /api/scans/{id}`, `GET /api/scans` and the progress events always include `phases` (an empty list for a scan that has not started a phase) and `finishedAt` (null until the scan ends).

## Scenarios

```gherkin
Feature: Phase totals in the progress stream

  @AC-1
  Scenario: Every byte hashed, hard links not started yet
    Given a scan whose full-hash phase has 3 files of 100 bytes
    When the third file's last bytes are reported and no HARD_LINKS phase has started
    Then the progress shows phase "FULL_HASH", hashedFiles 3 of 3 and hashedBytes 300 of 300
    When the HARD_LINKS phase starts
    Then the progress shows phase "HARD_LINKS"
```

  @AC-2
  Scenario: Finished phases keep their totals
    Given a scan that found 3 folders and 5 files of 500 bytes, compared 5 files, hashed 2 candidate files of 200 bytes, and checked 1 group of hard links
    When the HARD_LINKS phase is running
    Then phases lists WALK with 3 folders, 5 files and 500 bytes, finished
    And PARTIAL_HASH with 5 files and 500 bytes, finished
    And FULL_HASH with 2 files and 200 bytes, finished
    And HARD_LINKS with finished false

  @AC-2
  Scenario: Only started phases are listed
    Given a scan whose partial-hash phase is running
    Then phases lists WALK finished and PARTIAL_HASH not finished, and no FULL_HASH or HARD_LINKS entry

  @AC-2
  Scenario: A phase that has sent no hash progress yet has totals 0
    Given the PARTIAL_HASH phase has started and no HashProgress has arrived
    Then its entry has files 0 and bytes 0 and finished false

  @AC-2
  Scenario: A completed scan finishes every entry
    Given a scan that reaches COMPLETED
    Then every entry in phases has finished true and keeps its totals

  @AC-3
  Scenario: The flat fields are unchanged
    Given the same scan as in "Finished phases keep their totals"
    Then directories, files, bytes, hashedFiles, hashFilesTotal, hashedBytes, hashBytesTotal, groupsDone and groupsTotal have the values they had before this ticket

  @AC-4
  Scenario: finishedAt is set when the scan ends
    Given a registry with a fake clock at 10:00:00 and a scan started then
    When the clock reads 10:41:00 as the scan completes
    And the clock moves to 11:30:00 before anyone reads the entry
    Then GET /api/scans/{id} shows startedAt 10:00:00 and finishedAt 10:41:00
    And a second read shows the same finishedAt

  @AC-4
  Scenario: A running scan has no finishedAt
    Given a scan in its FULL_HASH phase
    Then its entry has finishedAt null

  @AC-4
  Scenario Outline: A scan that ends any other way has a finishedAt
    Given a scan that ends <how> at 10:05:00
    Then its entry has status <status> and finishedAt 10:05:00

    Examples:
      | how                       | status    |
      | when the user cancels it  | CANCELLED |
      | with a read error in core | FAILED    |

  @AC-5
  Scenario: A queued scan has an empty phases list and no finishedAt
    Given a scan that is QUEUED because the executor is held
    When the client reads GET /api/scans/{id}, GET /api/scans and the first progress event
    Then each shows "phases": [] and "finishedAt": null, not a missing field

## Scope

**Touches:**
- `us.pochaev.dfs.server.scan`: `ScanProgress` gains `phases`, `ScanEntry` gains `finishedAt`, `ScanRegistry` fills them (its `Counters` keep each phase's totals; an injectable `Clock` stamps `finishedAt`), `ScanEventStreams` and `ScanController` only if the design needs them.
- `server/src/test/java/us/pochaev/dfs/server`: `ScanApiTest` and a registry test for the clock and phase scenarios.
- `server/frontend/openapi.json`: regenerated by `:server:updateOpenApi`.
- `server/frontend/src/app/scans` and `server/frontend/src/app/results`: only the literals that build a `ScanProgress` or `ScanEntry` in specs and test data gain the two fields (decision 4); no behaviour change.
- `CLAUDE.md`: the `server` entry names `phases` and `finishedAt`.

**Out of scope:**
- The page (DFS-063).
- Any change to `file-scanner`: the core's events already carry what is needed.
- Per-phase start and finish times, a `finishing` field, and dropping the flat progress fields (decisions 1 to 3).
- Reading the new fields in the page: the DFS-057 row is untouched.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] `OpenApiSpecTest` passes with `openapi.json` regenerated by `:server:updateOpenApi`
- [ ] No files changed outside the paths listed under Touches
- [ ] Started by hand once: a small scan through `bootRun` and `curl` shows `phases` growing and `finishedAt` set on the entry at the end, the server log free of warnings and errors
- [ ] `CLAUDE.md`'s `server` entry names `phases` and `finishedAt` (the verifier reads it)

## Open questions

None.

## Post-close check

The old bar's long stay at 100% (decision 1) is confirmed on a real large scan, by the product owner, after DFS-063 lands, because the redesigned row is what makes the gap visible. The result goes into DFS-063's `verification.md`; if the row shows a silent gap with no phase change, a follow-up ticket adds a field.
