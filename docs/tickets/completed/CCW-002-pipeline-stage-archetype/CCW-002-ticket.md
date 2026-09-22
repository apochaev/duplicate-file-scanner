---
id: CCW-002
title: Write the pipeline-stage archetype
status: done
type: docs
scope: [build]
archetype: none
priority: medium
depends_on: [CCW-001]
touches: [.claude/archetypes/pipeline-stage, docs/tickets/backlog/DFS-001-group-files-by-size, docs/tickets/backlog/DFS-002-partial-hash-stage, docs/tickets/backlog/DFS-003-full-hash-stage]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed: 2026-09-21
---

# CCW-002: Write the pipeline-stage archetype

*(Formerly DFS-009; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the story pipeline
I want a `pipeline-stage` archetype holding the templates, guides and edge-case checklist for a detection-pipeline stage
So that the architect, test author, implementer and verifier start from consistent material, and refinement asks the questions we already know matter

## Context

The first archetype in the catalog (D4), under `.claude/archetypes/pipeline-stage/` as plain files that agents read by path. It holds:
- `archetype.md`: frontmatter (`name`, `version`, `phases`, `gates`, per-agent `models`), then when it applies, the edge-case checklist, extra done-items, and where its files go in the hexagonal layers (`application` for the stage, `domain.model` only for exported results, ports in `infra.port`).
- `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md`, and `skeletons/*.java.tmpl`.

The content comes from refining DFS-001, whose decisions are the checklist: accumulation that can be merged (so it can be parallelised later), deterministic output order, immutable results, invalid-argument rejection, the same input appearing twice, configuration defaults and where they live, and memory measured, not assumed. It also draws on the `CLAUDE.md` conventions (records and sealed interfaces, tests against fakes, ArchUnit layering, large fixtures generated in code). Defaults from D3, D9 and D2: gates `[design, done]`, the D9 model tiers, and stubs owned by the architect that throw `new UnsupportedOperationException("not implemented: <TICKET-ID>")` (the convention is decided in CCW-003).

It applies to DFS-001, DFS-002 and DFS-003, which get `archetype: pipeline-stage` here.

Test naming (D11, decided while refining CCW-003): a test's `@DisplayName` starts with the acceptance-criterion ID(s) it proves, for example `DFS-001.AC-3 Files of equal size form one candidate group`. `test-plan.md.tmpl` maps criterion, scenarios and tests, and `test-guide.md` states the convention.

**Decided (2026-09-21): the Java skeletons are structural, not bare shells or worked examples.** Each `skeletons/*.java.tmpl` gives the class/record shape the pipeline-stage pattern requires (a mergeable accumulator with `add`/`merge`/a result accessor, an immutable internal record) with method signatures already present, bodies throwing `new UnsupportedOperationException("not implemented: <TICKET-ID>")` per D2, and Javadoc placeholders pointing at the checklist items (mergeable, deterministic order, immutable result, reject invalid args). The architect renames types and fields and fills in logic; the shape and the reminders are already there.

**Decided (2026-09-21): the "skeleton compiles" check fills the skeleton for a throwaway `SampleStage`, not DFS-001 itself.** DFS-001 is not implemented yet, and pre-filling its real classes here would hand DFS-001's own pipeline run production code it did not write and would pull `us.pochaev.dfs.scanner.application` into this ticket's `touches`. `SampleStage` carries no ticket ID, is built once to prove the skeleton compiles and the ArchUnit layering tests still pass, and is deleted afterward, matching the existing Definition-of-done line.

This ticket edits instructions (archetype templates and guides) rather than product code, so each scenario below notes whether it is checked mechanically or by reading.

## Acceptance criteria

The full ID of each criterion is `CCW-002.AC-<n>`.

- **AC-1** The archetype directory `.claude/archetypes/pipeline-stage/` contains `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md` and `skeletons/`.
- **AC-2** `archetype.md`'s frontmatter declares `name: pipeline-stage`, `version: 1`, `phases` (the four standard pipeline phases: design, test, implement, verify), `gates: [design, done]`, and `models` with the D9 tiers (architect opus/high, verifier opus/medium, implementer sonnet/medium, test-author sonnet/medium, retro sonnet/low).
- **AC-3** The edge-case checklist covers each of DFS-001's six recorded decisions (the mergeable, non-thread-safe accumulator; `CandidateGroup`'s invariants; the minimum-size setting and its default; deterministic largest-first ordering with ascending paths; the plain in-memory map with memory measured; equal-path collapsing).
- **AC-4** A skeleton filled in for a throwaway `SampleStage` (no ticket ID) compiles and the ArchUnit layering tests pass, and the sample is then removed.
- **AC-5** DFS-001, DFS-002 and DFS-003 carry `archetype: pipeline-stage` in their frontmatter.
- **AC-6** `test-guide.md` and `test-plan.md.tmpl` require each test's `@DisplayName` to start with its criterion ID and require `test-plan.md.tmpl` to map criterion, scenarios and tests.

## Scenarios

```gherkin
Feature: The pipeline-stage archetype

  # Verified by: mechanical (list the directory)
  @AC-1
  Scenario: The archetype has every file the catalog layout requires
    Given the directory `.claude/archetypes/pipeline-stage/`
    When its contents are listed
    Then `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md` and `skeletons/` exist

  # Verified by: mechanical (parse the frontmatter)
  @AC-2
  Scenario: The frontmatter carries the agreed defaults
    Given `archetype.md`
    When its frontmatter is read
    Then `name` is `pipeline-stage`, `version` is `1`, `phases` lists design, test, implement and verify, and `gates` is `[design, done]`
    And `models` sets architect to opus/high, verifier to opus/medium, implementer to sonnet/medium, test-author to sonnet/medium, and retro to sonnet/low

  # Verified by: read-through (a judgment mapping between the checklist text and DFS-001's decisions, not a mechanical match)
  @AC-3
  Scenario: The checklist would have prompted the DFS-001 decisions
    Given the edge-case checklist
    When it is compared with DFS-001's six recorded decisions
    Then each decision falls under at least one checklist item

  # Verified by: mechanical (fill the skeleton as SampleStage, run ./gradlew build, then delete it)
  @AC-4
  Scenario: A skeleton filled in for a throwaway sample stage compiles
    Given the accumulator skeleton filled in as a throwaway `SampleStage`, carrying no ticket ID
    When `./gradlew build` runs
    Then it compiles and the ArchUnit layering tests still pass
    And `SampleStage` is then deleted, leaving nothing outside this ticket's `touches`

  # Verified by: mechanical (read each ticket's frontmatter)
  @AC-5
  Scenario: The existing pipeline tickets use the archetype
    Given DFS-001, DFS-002 and DFS-003
    When their frontmatter is read
    Then each has `archetype: pipeline-stage`

  # Verified by: read-through, with a grep spot-check for the required phrases
  @AC-6
  Scenario: The test guide sets the naming convention
    Given `test-guide.md` and `test-plan.md.tmpl`
    When they are read
    Then they require each test's `@DisplayName` to start with its criterion ID, for example `DFS-001.AC-3 Files of equal size form one candidate group`
    And `test-plan.md.tmpl` maps criterion, scenarios and tests
```

## Scope

**Touches:**
- `.claude/archetypes/pipeline-stage/`: the archetype files
- Frontmatter of DFS-001, DFS-002 and DFS-003: the `archetype` field only

**Out of scope:**
- The other archetypes (`port-and-adapter`, `use-case` and so on): written when a story needs them
- Skill support for the `archetype` field (CCW-001)

## Definition of done

- [x] Every scenario above is satisfied — AC-1, AC-2, AC-5 checked mechanically (directory listing, frontmatter parse, `grep` of the three tickets); AC-4 checked mechanically (`./gradlew build`, see below); AC-6 checked mechanically (`grep` for `@DisplayName` and the criterion-ID convention in `test-guide.md` and `test-plan.md.tmpl`); AC-3 checked by reading — each of DFS-001's six decisions maps to a checklist item: mergeable/non-thread-safe accumulator → item 1, `CandidateGroup` invariants → item 2, minimum-size setting/default → item 6, largest-first/ascending-paths ordering → item 3, in-memory map with memory measured → item 7, equal-path collapsing → item 5
- [x] The `SampleStage` skeleton check builds and the ArchUnit tests pass, then `SampleStage` is removed — `SampleStage`/`SampleResult` filled in from `skeletons/Accumulator.java.tmpl` and `skeletons/StageResult.java.tmpl`, `./gradlew build` reported `BUILD SUCCESSFUL` (includes `file-scanner:test`, which runs `ArchitectureTest`), then both files deleted and `./gradlew build` run again, still `BUILD SUCCESSFUL`
- [x] No files changed outside the paths listed under Touches — `git status --porcelain` shows only `.claude/archetypes/` (new) and the `archetype:` line in the DFS-001/002/003 ticket frontmatter, plus this ticket's own file
- [x] The archetype's `version` is 1 and its changes are recorded in `archetype.md` — `version: 1` in the frontmatter; this is the initial version, so `archetype.md` itself is the record (a changelog applies from the first version bump, D6)

## Open questions

None.
