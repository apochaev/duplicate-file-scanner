---
id: DFS-052
title: Skip quarantine folders when scanning
status: draft
type: feature
scope: [file-scanner]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 35}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: backend
depends_on: [DFS-051]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-07
completed:
---

# DFS-052: Skip quarantine folders when scanning

## Story

As the user who has executed a deletion plan
I want later scans to leave the app's quarantine folders out
So that quarantined files are never reported as duplicates of the files they were verified against, and a plan can never quarantine a file that is already in quarantine

## Context

DFS-051 moves quarantined files to `<volume root or anchor>/.dfs-quarantine/<run ID>/<path below it>` (DFS-051 decision 5 and its design's `ExecutionConfig.quarantineAnchors`). A later scan whose location contains a volume root or an anchor walks `.dfs-quarantine` like any folder, so every quarantined file reappears as a copy of its keeper. Raised by DFS-051's architect at the design gate (2026-11-01) as a follow-up outside DFS-051's scope, and confirmed by DFS-051's product-owner review (its `verification.md`, "Known limits", design note 7, 2026-11-05): a later scan of a volume root would offer the quarantined files as duplicates, inviting the user to "delete" copies already in quarantine. The anchors are configured as `dfs.execution.quarantine-anchors` (`ExecutionProperties` in `scanner-spring`).

The walker (`NioFileTreeWalker`, `FileTreeWalker.walk`) already leaves things out with `WalkEvent.Skipped(path, SkipReason, detail)`; default name excludes (`.DS_Store`, `._*`) are filtered in `application` (DFS-027) and counted in the summary's `excluded`.

## Decisions

1. **The walker prunes the folder and reports it as skipped** (2026-11-07). `NioFileTreeWalker` never enters a directory named `.dfs-quarantine` (DFS-051's `NioQuarantineDeleter.QUARANTINE_FOLDER`) and reports it once as `WalkEvent.Skipped(path, SkipReason.QUARANTINE, detail)`, a new `SkipReason`; it appears as one `skipped` line per quarantine folder in the JSONL and text reports. Always on, not tied to `--no-default-excludes`: skipping the quarantine is a safety rule, not OS-metadata cleanup. A quarantine of thousands of files costs one directory check, and the report says where a quarantine was left out. The new `reason` value is additive (DFS-030: no `schemaVersion` bump); tests asserting `skipped` lines are searched in the test trees (DFS-030.L-2). Not a per-file filter in `application` like the default excludes (walks the whole quarantine, `--no-default-excludes` would bring quarantined files back, and `excluded` would mix OS metadata with safety) and not a silent prune (files under a scanned folder left out with no trace).

## Acceptance criteria

The full ID of each criterion is `DFS-052.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Skip quarantine folders when scanning

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined>

**Out of scope:**
- Undo and purge of a quarantine run (DFS-051's follow-ups).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Match by the folder name `.dfs-quarantine` anywhere, or only directly under a volume root or a configured anchor?
- A scan whose location is itself inside `.dfs-quarantine`: refuse, or scan it as asked?
- Archetype (`pipeline-stage` or `use-case`, or `none` if it is a one-line skip).
