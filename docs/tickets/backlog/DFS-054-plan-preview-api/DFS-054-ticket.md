---
id: DFS-054
title: Preview a deletion plan through the server API
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: backend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-05
updated: 2026-11-05
completed:
---

# DFS-054: Preview a deletion plan through the server API

## Story

As the user of the local DFS server
I want to send rules ("keep A, quarantine in B"), exceptions and single-set picks for a finished scan and get back the plan they produce
So that the UI can show me exactly what a bulk delete would move, which copy each file keeps, and where my rules conflict, before anything touches the disk

## Context

Milestone M-3 ("build and check a plan"), first step (`docs/design/ui.md`, "Milestones"; U6, U7). The endpoint half of the plan builder; a frontend ticket for the plan screen follows and depends on this one.

- **The core is built:** `PreviewDeletionPlan.preview(ScanResult, PlanRequest)` returns `Result<DeletionPlan>` (DFS-022): rules `KeepAndQuarantine(keep, quarantineIn)`, exceptions, picks `KeepCopy(digest, keep)`; the plan has a content-derived `id`, `Quarantine` operations with their `Source`, `conflicts`, `PlanTotals` and `leftBehind`; invalid requests fail with `INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET` or `DUPLICATE_PICK`. Drift check (DFS-033), content verify (DFS-034) and asynchronous verify (DFS-042) take a previewed plan and are later steps of M-3.
- **CLAUDE.md safety invariants that apply:** "Preview is a pure query" (the endpoint never touches disk state); "Never delete the last copy" (conflicts are reported and block confirming, U7.1); "Hard links are not duplicates" (an operation moves a physical copy with all its links).
- **The server's conventions (DFS-040, DFS-047, DFS-048):** a COMPLETED scan only (`409 SCAN_NOT_COMPLETED`), `404 UNKNOWN_SCAN`, paths absolute (`400 RELATIVE_PATH`) and inside a scan root (`403 PATH_OUTSIDE_ROOTS`), `{code, message}` errors, the API's own records (never core types), the spec committed as `server/frontend/openapi.json` and `./gradlew :server:updateOpenApi`.
- **Analysis decision 1** (`docs/design/folder-overlap-analysis.md`): pairs are never combined into a verdict; only the whole-plan check says what is safe. This endpoint is that check.

## Decisions

1. **Previewing creates a frozen plan the server keeps** (2026-11-05). `POST /api/scans/{id}/plans` takes the whole request (rules, exceptions, picks), previews it with `PreviewDeletionPlan`, keeps the resulting plan with the scan in memory (lost on restart, like scans) and answers `201` with `Location: /api/scans/{id}/plans/{planId}` and the plan's summary; `GET` on that location reads it back. The plan ID is DFS-022's content-derived ID, so the same request previews to the same ID. The draft lives in the browser; editing it and previewing again posts a new request and gets a new ID (U7, D4). The later drift, verify and execute endpoints name `{planId}`, so the browser never resends or rebuilds a plan, and a big plan's operations can be paged from the kept copy. Not a stateless preview answering the whole plan (every later step recomputes, no paging, and "frozen" rests on the client resending the same request) and not a server-held draft edited rule by rule (many endpoints and a mutable plan on the server, against D4, where only previewed plans are fixed).

2. **The server keeps only the latest previewed plan per scan** (2026-11-05). A preview replaces the scan's kept plan; reading or using an older plan ID of that scan answers `410` `PLAN_SUPERSEDED`, and an ID never previewed for that scan answers `404` `UNKNOWN_PLAN`. Previewing the same request again gives the same ID (DFS-022), which becomes the latest again. One plan per scan is the "current" one, and memory stays bounded by the number of scans. Accepted consequence: two browser tabs on one scan replace each other's plan, and a later drift, verify or execute step whose plan was replaced is refused with `PLAN_SUPERSEDED` (the browser re-previews). Not every plan for the server run (memory grows with each distinct preview, and several plans of one scan could be acted on) and not the latest N (an eviction rule, and a plan still on screen can disappear).

## Acceptance criteria

The full ID of each criterion is `DFS-054.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Plan preview API

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `us.pochaev.dfs.server` (a new plan package), `server/frontend/openapi.json`, `CLAUDE.md`, `docs/design/ui.md`>

**Out of scope:**
- The plan screen in the browser (a frontend ticket depending on this one).
- Drift check and content verify endpoints (later M-3 tickets on DFS-033, DFS-034, DFS-042).
- Executing a plan (M-5; DFS-051).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Response size on a big scan: the whole operation list, or totals plus paged operations grouped by rule?
- How the left-behind counts and conflicts are presented in the API's records.
- Archetype (`endpoint` likely), gates (a deletion-planning ticket: the design gate is recommended) and autorun.
