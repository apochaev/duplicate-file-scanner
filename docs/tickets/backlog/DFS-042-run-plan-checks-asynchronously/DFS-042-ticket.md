---
id: DFS-042
title: Run plan checks asynchronously with status and cancel
status: draft
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: backend
depends_on: [DFS-034]
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# DFS-042: Run plan checks asynchronously with status and cancel

## Story

As a user verifying a large deletion plan in the local UI
I want the content verify to run in the background, with a status I can poll and a cancel button
So that a check of many gigabytes does not block the page and can be stopped at any time

## Context

Split from DFS-034 (its decision 1): DFS-034 builds the synchronous `VerifyPlanContent.verify(plan, listener, cancelled)` with progress events and a cancel signal; this ticket wraps it in an asynchronous job a server can start, poll and cancel (`docs/design/ui.md` U7.3), on a caller-supplied executor (CLAUDE.md "Spring-friendliness rules"), with per-job state behind a port ("no state in singleton fields"). Executing a plan (quarantine, CLAUDE.md decision 2) will be long-running too, so the job model should be designed to run it later without a copy, rather than repeating the scan's model (DFS-018: `StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult`, six-state `ScanStatus`, `ScanRepository`).

## Acceptance criteria

The full ID of each criterion is `DFS-042.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Asynchronous plan checks

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
- Plan execution itself (a later ticket runs it through this job model).
- The server endpoints (frontend lane).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- A generic job model (job ID, status, progress snapshot, result) shared by verify and later execution, or one specific to verify? Does the scan's model move onto it?
- States and transitions (queued, running, cancelling, completed, cancelled, failed), and what cancel does in each (use-case checklist items 3 and 4).
- Where job state lives (a port plus in-memory adapter, like `ScanRepository`) and how long it is kept (checklist item 12).
- What a status snapshot carries (entries done and total, bytes done and total) and the result once complete (checklist items 7 and 10).
- Unknown job IDs, and an executor that rejects the task (checklist items 1 and 11).
- `gates:` and `autorun:`.
