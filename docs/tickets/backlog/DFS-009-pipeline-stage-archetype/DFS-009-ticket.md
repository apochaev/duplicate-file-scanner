---
id: DFS-009
title: Write the pipeline-stage archetype
status: draft
type: docs
scope: [build]
priority: medium
depends_on: [DFS-008]
touches: [.claude/archetypes/pipeline-stage, docs/tickets/backlog/DFS-001-group-files-by-size, docs/tickets/backlog/DFS-002-partial-hash-stage, docs/tickets/backlog/DFS-003-full-hash-stage]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-009: Write the pipeline-stage archetype

## Story

As the story pipeline
I want a `pipeline-stage` archetype holding the templates, guides and edge-case checklist for a detection-pipeline stage
So that the architect, test author, implementer and verifier start from consistent material, and refinement asks the questions we already know matter

## Context

The first archetype in the catalog (D4), under `.claude/archetypes/pipeline-stage/` as plain files that agents read by path. It holds:
- `archetype.md`: frontmatter (`name`, `version`, `phases`, `gates`, per-agent `models`), then when it applies, the edge-case checklist, extra done-items, and where its files go in the hexagonal layers (`application` for the stage, `domain.model` only for exported results, ports in `infra.port`).
- `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md`, and `skeletons/*.java.tmpl`.

The content comes from refining DFS-001, whose decisions are the checklist: accumulation that can be merged (so it can be parallelised later), deterministic output order, immutable results, invalid-argument rejection, the same input appearing twice, configuration defaults and where they live, and memory measured, not assumed. It also draws on the `CLAUDE.md` conventions (records and sealed interfaces, tests against fakes, ArchUnit layering, large fixtures generated in code). Defaults from D3, D9 and D2: gates `[design, done]`, the D9 model tiers, and stubs owned by the architect that throw a recognisable not-implemented exception.

It applies to DFS-001, DFS-002 and DFS-003, which get `archetype: pipeline-stage` here.

## Scenarios

```gherkin
Feature: The pipeline-stage archetype

  Scenario: The archetype has every file the catalog layout requires
    Given the directory `.claude/archetypes/pipeline-stage/`
    When its contents are listed
    Then `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md` and `skeletons/` exist

  Scenario: The frontmatter carries the agreed defaults
    Given `archetype.md`
    When its frontmatter is read
    Then it has a name, a version of 1, the phases, gates of design and done, and the D9 models

  Scenario: The checklist would have prompted the DFS-001 decisions
    Given the edge-case checklist
    When it is compared with the six recorded decisions of DFS-001
    Then each decision falls under at least one checklist item

  Scenario: A skeleton produces a compiling stage
    Given a skeleton filled in for a sample stage
    When the project is built
    Then it compiles and the architecture tests still pass

  Scenario: The existing pipeline tickets use the archetype
    Given DFS-001, DFS-002 and DFS-003
    When their frontmatter is read
    Then each has `archetype: pipeline-stage`
```

## Scope

**Touches:**
- `.claude/archetypes/pipeline-stage/`: the archetype files
- Frontmatter of DFS-001, DFS-002 and DFS-003: the `archetype` field only

**Out of scope:**
- The other archetypes (`port-and-adapter`, `use-case` and so on): written when a story needs them
- Skill support for the `archetype` field (DFS-008)

## Definition of done

- [ ] Every scenario above is satisfied
- [ ] The sample-stage skeleton check builds and the ArchUnit tests pass, then the sample is removed
- [ ] No files changed outside the paths listed under Touches
- [ ] The archetype's `version` is 1 and its changes are recorded in `archetype.md`

## Open questions

- Scenarios are a first draft derived from the design log only; review and complete them.
- How detailed should the Java skeletons be (a class shell, or a full test scaffold)?
- Stub convention: which exact exception and message does the red gate (DFS-010) match?
- Which stage will the sample-stage check use, given DFS-001 is not implemented yet?
