---
id: CCW-013
title: Add a wiring archetype
status: draft
type: feature
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-019, DFS-020]
touches: [.claude/archetypes/wiring/, docs/tickets/completed/DFS-019-wire-scanner-factories/learnings.md]
blocked_reason:
created: 2026-10-09
updated: 2026-10-09
completed:
---

# CCW-013: Add a wiring archetype

## Story

As the person running tickets through the story pipeline
I want an archetype for wiring tickets (factories, and the composition root that turns them into a running app)
So that the next wiring ticket gets a checklist that asks the questions DFS-019 and DFS-020 only met during implementation, instead of a `use-case` checklist that mostly does not apply

## Context

DFS-019.L-5 (proposed, 2 recurrences) asks for this. Two real wiring tickets are its evidence:

- **DFS-019** (wire `ScannerFactories.scanUseCases`), run through the pipeline as `use-case`: `design.md` marked 9 of 14 `use-case` checklist items not applicable, and the shared-versus-fresh-instances question was in no checklist.
- **DFS-020** (scan from the CLI and write JSONL), built by hand as `archetype: none`: the factory returns one `ScanService` behind all four ports, so Spring injection by type failed ("expected single matching bean but found 2") and every `@Bean` had to wrap its port in a delegate of that interface alone (commit 73d444f). Refinement also had to discover that Spring Boot's banner, startup log and Gradle's lifecycle lines all go to stdout by default, that `ExecutorService.close()` as a bean destroy method would block Ctrl+C on a running scan, and that `ScanListener` has no completion event.

L-5's Change lists the checklist items so far: shared versus fresh instances per build, a null or missing executor, default adapters versus overrides, the exported return type; its re-proposal adds "does the returned object implement more than the port it is returned as?" and "how the consumer's container (Spring) sees the returned types".

Existing archetypes to model on: `.claude/archetypes/use-case/` (version 3: `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `test-guide.md`, `impl-guide.md`) and `pipeline-stage` (version 12). CLAUDE.md already expects CLI wiring to get its own archetype. Applying L-5 is part of this ticket: mark it `applied` via `/learn`, pointing here.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-013.AC-<n>`.

## Scenarios

```gherkin
Feature: The wiring archetype

  Scenario: <placeholder; scenarios are written during refinement>
```

## Scope

**Touches:**
- `.claude/archetypes/wiring/`: the new archetype (`archetype.md` with its checklist, templates and guides)
- `docs/tickets/completed/DFS-019-wire-scanner-factories/learnings.md`: DFS-019.L-5 marked `applied` (via `/learn apply`)

**Out of scope:**
- Changing the `use-case` or `pipeline-stage` archetypes
- Changing the story skill, the agents or `tools/story-gate` (unless refinement finds the pipeline cannot run a wiring ticket without it)
- Product code

## Definition of done

- [ ] Every scenario above is checked the way it says (mechanical, reading or live) and passes, with the evidence noted beside this item
- [ ] No files changed outside the paths listed under Touches
- [ ] DFS-019.L-5 is marked `applied` with a pointer to this ticket (via `/learn`)

## Open questions

- Name and reach: `wiring` (factories plus composition roots) or narrower (`factory`) / broader (`cli-app`); is a Spring composition root (DFS-020) the same archetype as a domain factory (DFS-019)?
- Checklist: which items, beyond L-5's six, come from DFS-020 (stdout purity for machine-readable output, bean destroy methods vs blocking close, exit codes, how the caller waits for async work)?
- Files: does it need all five of use-case's files, and what do the test guide's rules say about Spring-level tests (`OutputCaptureExtension`, real `SpringApplication` starts) and fake ports?
- Pipeline fit: can the existing agents and `story-gate` checks (red gate, freeze, stubs) run a wiring ticket as they are, e.g. what is the "stub" of a `@Configuration`? If not, what must change, and is that in touches?
- Proof: how the archetype is shown to work. There is no wiring ticket in the backlog to run it on; a dry read against DFS-019 and DFS-020, or wait for the next one (e.g. a server or the delete CLI)?
- Archetype for this ticket itself: `none` (built by hand), as earlier archetype tickets were?
