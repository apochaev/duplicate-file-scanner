---
id: DFS-008
title: Extend the tickets skill for archetypes, models and verifier ticking
status: draft
type: feature
scope: [build]
priority: high
depends_on: []
touches: [.claude/skills/tickets, CLAUDE.md, docs/tickets/backlog/DFS-001-group-files-by-size]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-008: Extend the tickets skill for archetypes, models and verifier ticking

## Story

As the maintainer of the ticket workflow
I want tickets to declare their archetype and optional per-agent model settings, and the done-item ticking to belong to the verifier
So that the story pipeline can pick templates and models per ticket, and an agent never grades its own work

## Context

Implements the ticket-side changes decided in `docs/design/story-orchestration.md`:
- **D4:** each ticket gets an `archetype:` frontmatter field naming a directory under `.claude/archetypes/`. The `ready` gate adds "archetype set", and `refine` reuses the archetype's edge-case checklist.
- **D9:** an optional `models:` field, resolved in this order: the ticket, then the archetype, then the agent's own frontmatter. Shape: `models: {architect: {model: opus, effort: high, maxTurns: 25}}`, any subset of `model`, `effort` and `maxTurns` per agent.
- **D7:** done-items are ticked by the verifier after it has verified them, not by the implementer, so the "Implementing a ticket (for agents)" rules in the skill change.
- **Acceptance criteria (D11, decided while refining DFS-010):** refinement produces a numbered list, `## Acceptance criteria`, between Context and Scenarios. Each criterion is a one-line testable statement with the ID `<TICKET-ID>.AC-<n>` (for example `DFS-011.AC-1`). Scenarios carry `@AC-n` tags; every scenario needs at least one tag and every criterion at least one scenario. IDs are never renumbered or reused (a retired criterion keeps its number and is marked retired). The `ready` gate adds "every scenario tagged and every criterion covered".

The skill is instructions only (no script), so "tests" here are walkthroughs of each mode on a scratch copy of the backlog.

## Scenarios

```gherkin
Feature: Ticket workflow with archetypes and models

  Scenario: A ticket without an archetype cannot become ready
    Given a draft ticket with no `archetype` field
    When the ready gate is checked
    Then "archetype set" is reported as unmet

  Scenario: A known archetype is accepted
    Given a ticket with `archetype: pipeline-stage` and the directory `.claude/archetypes/pipeline-stage/` exists
    When validate runs
    Then no archetype finding is reported

  Scenario: An unknown archetype is reported
    Given a ticket with `archetype: nonexistent`
    When validate runs
    Then a missing-archetype finding names the ticket and the archetype

  Scenario: Well-formed models are accepted
    Given a ticket with `models: {architect: {model: opus, effort: high, maxTurns: 25}}`
    When validate runs
    Then no models finding is reported

  Scenario: Malformed models are reported
    Given a ticket whose `models` names an unknown agent or an unknown field
    When validate runs
    Then a models finding names the ticket and the offending key

  Scenario: Refinement uses the archetype's checklist
    Given a ticket with an archetype whose edge-case checklist has open items
    When refine runs
    Then each unresolved checklist item becomes a question in the interview

  Scenario: The implementer does not tick done-items
    Given the rules for implementing agents
    When an implementer finishes its work
    Then it leaves the done-items unticked and the verifier ticks each one it has verified

  Scenario: Refinement produces numbered acceptance criteria
    Given a ticket being refined
    When the interview closes a decision
    Then the ticket gains or updates a criterion with the next unused number

  Scenario: An untagged scenario blocks the ready gate
    Given a scenario with no `@AC-n` tag
    When the ready gate is checked
    Then "every scenario tagged" is reported as unmet

  Scenario: A criterion without a scenario blocks the ready gate
    Given a criterion that no scenario is tagged with
    When the ready gate is checked
    Then "every criterion covered" is reported as unmet

  Scenario: Validate flags duplicate or dangling criterion IDs
    Given a ticket with two criteria numbered AC-2, or a scenario tagged with a criterion that does not exist
    When validate runs
    Then each problem is reported with the ticket and the ID

  Scenario: Validate notes touches entries that resolve to nothing
    Given a ticket whose `touches` names a path or package that matches no existing file or directory
    When validate runs
    Then an informational note names the ticket and the entry, since entries for new files are expected to resolve to nothing before the work
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md` and `TEMPLATE.md`: the new fields, the gate item, the `validate` checks, the verifier rule
- `CLAUDE.md`: the Tickets section mentions the archetype, models and acceptance-criteria fields
- `docs/tickets/backlog/DFS-001-group-files-by-size/DFS-001-ticket.md`: acceptance criteria derived from its scenarios (the author confirms the mapping) and `@AC-n` tags on the scenarios, since the pilot compares against DFS-001

**Out of scope:**
- Writing any archetype (DFS-009) or agent (DFS-011)
- Assigning `archetype` to existing tickets (DFS-009)

## Definition of done

- [ ] `SKILL.md` and `TEMPLATE.md` describe the new fields, the `ready` gate item, the `validate` checks and the verifier rule
- [ ] A walkthrough of each mode on a scratch copy of the backlog shows every scenario's outcome, with no false findings on existing tickets (evidence noted in this ticket)
- [ ] The `CLAUDE.md` Tickets section mentions the archetype and models fields
- [ ] DFS-001 has confirmed acceptance criteria and tagged scenarios
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Scenarios are a first draft derived from the design log only; review and complete them.
- Existing `ready` and draft tickets have no `archetype` yet. Is the requirement enforced only when a ticket is promoted to `ready` (and DFS-009 assigns the existing ones), or does `validate` flag every ticket without one?
- Where does `validate` get the list of valid agent names for `models`: a fixed list in the skill, or the files in `.claude/agents/` (which do not exist until DFS-011)?
- Does the template show `archetype` and `models` as commented examples?
- Migration: DFS-001 gets criteria derived from its 21 scenarios (the author confirms the mapping); drafts are grandfathered until refined. Does `validate` flag drafts without criteria, or only tickets promoted to `ready`?
- Where exactly do retired criteria appear in a ticket, and how does `validate` recognise a reused number beyond duplicates?
