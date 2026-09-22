---
id: CCW-001
title: Extend the tickets skill for archetypes, models and verifier ticking
status: done
type: feature
scope: [build]
archetype: none
priority: high
depends_on: []
touches: [.claude/skills/tickets, CLAUDE.md]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed: 2026-09-21
---

# CCW-001: Extend the tickets skill for archetypes, models and verifier ticking

*(Formerly DFS-008; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the maintainer of the ticket workflow
I want tickets to declare their archetype and optional per-agent model settings, and the done-item ticking to belong to the verifier
So that the story pipeline can pick templates and models per ticket, and an agent never grades its own work

## Context

Implements the ticket-side changes decided in `docs/design/story-orchestration.md`:
- **D4:** each ticket gets an `archetype:` frontmatter field naming a directory under `.claude/archetypes/`. The `ready` gate adds "archetype set", and `refine` reuses the archetype's edge-case checklist.
- **D9:** an optional `models:` field, resolved in this order: the ticket, then the archetype, then the agent's own frontmatter. Shape: `models: {architect: {model: opus, effort: high, maxTurns: 25}}`, any subset of `model`, `effort` and `maxTurns` per agent.
- **D7:** done-items are ticked by the verifier after it has verified them, not by the implementer, so the "Implementing a ticket (for agents)" rules in the skill change.
- **Acceptance criteria (D11, decided while refining CCW-003):** refinement produces a numbered list, `## Acceptance criteria`, between Context and Scenarios. Each criterion is a one-line testable statement with the ID `<TICKET-ID>.AC-<n>` (for example `CCW-004.AC-1`). Scenarios carry `@AC-n` tags; every scenario needs at least one tag and every criterion at least one scenario. IDs are never renumbered or reused (a retired criterion keeps its number and is marked retired). The `ready` gate adds "every scenario tagged and every criterion covered".

The skill is instructions only (no script), so "tests" here are walkthroughs of each mode on a scratch copy of the backlog.

**Decisions made while refining this ticket:**
- **Enforcement (decided 2026-09-21):** the `ready` gate requires the archetype and the criterion and tag coverage at promotion. `validate` additionally flags every `ready`-or-later ticket that lacks them, and grandfathers drafts, since refinement is what produces these fields. Today that reports DFS-001 (no archetype, no criteria) and CCW-003 (no archetype) until this ticket and CCW-002 fix them. Rejected: checking at promotion only (a `ready` ticket can drift below the gate unnoticed); flagging drafts too (noise about gaps refinement fills by design).

- **Tickets outside the pipeline (decided 2026-09-21):** `archetype: none` is a reserved value meaning "implemented by hand, not run through the story pipeline". It satisfies the `ready` gate's "archetype set" and `validate` accepts it without looking for a directory; `/story run` (CCW-005) refuses a `none` ticket. CCW-001 and CCW-003 are `none`; DFS-001 becomes `pipeline-stage` in CCW-002. Rejected: an archetype for every kind of ticket (builds unused archetypes against D4 and forces the design, test and stub templates onto work they do not fit); an optional field (drops the gate item, and a forgotten field looks like a deliberate choice). If a kind of ticket keeps showing up as `none`, that is the evidence for writing its archetype.

- **Ticking done-items (decided 2026-09-21):** two paths in the skill's rules for implementers. Pipeline tickets: the verifier ticks each item after verifying it and the implementer never does. `archetype: none` tickets, implemented by hand: the implementing session ticks each item as it is met and notes the evidence, and `done` still requires every item ticked or the user's say-so. Rejected: verifier-only with the user as the verifier until one exists (an extra step on every hand-built ticket for a mechanical check); keeping the current rule until CCW-005 (the skill would state the wrong rule when the pipeline goes live, and it is easy to forget).

- **Validating `models` (decided 2026-09-21):** the valid agent names are a fixed list in `SKILL.md` (architect, test-author, implementer, verifier, retro), and the valid fields are `model`, `effort` and `maxTurns`. Rejected: reading `.claude/agents/` (nothing to read until CCW-004, and that directory may hold agents that are not pipeline roles); fixed list now and a switch later (a follow-up added to another ticket). A sixth agent would be a design change that edits the skill anyway.

- **Template shape (decided 2026-09-21):** `TEMPLATE.md` has a blank `archetype:` line (visible, filled during refinement, like `blocked_reason:`) and a commented `# models: {architect: {model: opus, effort: high, maxTurns: 25}}` example; the body gains an `## Acceptance criteria` section between Context and Scenarios, and the scenario examples carry `@AC-n` tags. Rejected: both fields blank (a blank `models:` parses as null and needs its own rule, and every ticket carries a key nobody uses); both commented (the required archetype would not show up as a gap in the file).

- **Criterion format and retirement (decided 2026-09-21):** a criterion is a line `- **AC-<n>** text` under a line stating the full ID (`<TICKET-ID>.AC-<n>`), the same shape as CCW-003; the tools derive the full ID from the ticket ID. A retired criterion stays in the list as `- **AC-<n> (retired)** text`, followed by the date and reason. It keeps its number, needs no scenario or test, and a tag that names it is an error. `validate` checks that numbers run 1 to n without gaps (a gap means a criterion was deleted, not retired), without duplicates. One case cannot be caught inside the file: deleting the last criterion and reusing its number; only git history shows it, so the skill states it as a rule for whoever refines. CCW-003's traceability check is extended to match (retired criteria skipped). Rejected: the full ID on every line (repetitive, differs from the `@AC-n` tags, would rewrite CCW-003); a separate `### Retired` subsection (every gap and duplicate check would read two places, and retiring becomes a move).

- **Coverage of the remaining scenarios (decided 2026-09-21):** criteria AC-7 to AC-13 cover the rest, including two gaps found in review: `list` shows an archetype column and marks `none` tickets as manual in "Dispatchable now" (so the sets do not promise what `/story run` refuses), and the `CLAUDE.md` Tickets section is checked by a scenario, not only a done-item. Rejected: leaving both to the done-items (the `list` gap stays open and the deliverable is not traceable). No scanner safety invariant applies to a skill-editing ticket, so the gate's "every invariant that applies" is vacuous here and no scenario is invented for it.

## Acceptance criteria

The full ID of each criterion is `CCW-001.AC-<n>`.

- **AC-1** `validate` reports every `ready`-or-later ticket that lacks an archetype, criteria, or tags on its scenarios, and reports nothing for drafts.
- **AC-2** `archetype: none` satisfies the `ready` gate and `validate`, and the skill documents it as "implemented by hand, not run through the pipeline".
- **AC-3** The skill's rules for implementers say that on a pipeline ticket only the verifier ticks done-items, and on an `archetype: none` ticket the implementing session ticks each item as it is met, with evidence noted.
- **AC-4** `validate` reports a `models` entry that names an agent outside the fixed list, or a field other than `model`, `effort` and `maxTurns`, and accepts any well-formed subset.
- **AC-5** `TEMPLATE.md` shows a blank `archetype:` line, a commented `models` example, an `## Acceptance criteria` section and `@AC-n` tags, and `new` creates a draft from it that `validate` accepts.
- **AC-6** Acceptance criteria are written `- **AC-<n>** text` under a line stating the full ID, a retired one is written `- **AC-<n> (retired)** text` with the date and reason, and `validate` reports duplicate numbers, gaps in the numbering, and scenario tags that name a missing or retired criterion.
- **AC-7** The `ready` gate requires an archetype (a name or `none`), a tag on every scenario and a scenario for every live criterion, and names each unmet item.
- **AC-8** `validate` checks an archetype name against the directories under `.claude/archetypes/`.
- **AC-9** `refine` turns each unresolved item of the archetype's edge-case checklist into an interview question.
- **AC-10** `refine` gives each closed decision a criterion numbered one above the highest number used, retired criteria included.
- **AC-11** `validate` gives an informational note for `touches` entries that resolve to nothing.
- **AC-12** `list` shows an archetype column and marks `archetype: none` tickets as manual in "Dispatchable now".
- **AC-13** The `CLAUDE.md` Tickets section names the archetype and models fields, `archetype: none` and the numbered acceptance criteria.

## Scenarios

```gherkin
Feature: Ticket workflow with archetypes and models

  @AC-7
  Scenario: A ticket without an archetype cannot become ready
    Given a draft ticket with no `archetype` field
    When the ready gate is checked
    Then "archetype set" is reported as unmet

  @AC-8
  Scenario: A known archetype is accepted
    Given a ticket with `archetype: pipeline-stage` and the directory `.claude/archetypes/pipeline-stage/` exists
    When validate runs
    Then no archetype finding is reported

  @AC-8
  Scenario: An unknown archetype is reported
    Given a ticket with `archetype: nonexistent`
    When validate runs
    Then a missing-archetype finding names the ticket and the archetype

  @AC-4
  Scenario: Well-formed models are accepted
    Given a ticket with `models: {architect: {model: opus, effort: high, maxTurns: 25}}`
    When validate runs
    Then no models finding is reported

  @AC-4
  Scenario: An unknown agent in models is reported with the valid names
    Given a ticket with `models: {architet: {model: opus}}`
    When validate runs
    Then a models finding names the ticket and `architet`, and lists architect, test-author, implementer, verifier and retro as the valid agents

  @AC-4
  Scenario: An unknown field in models is reported
    Given a ticket with `models: {architect: {temperature: 0}}`
    When validate runs
    Then a models finding names the ticket and `temperature`, and lists model, effort and maxTurns as the valid fields

  @AC-9
  Scenario: Refinement uses the archetype's checklist
    Given a ticket with an archetype whose edge-case checklist has open items
    When refine runs
    Then each unresolved checklist item becomes a question in the interview

  @AC-3
  Scenario: The implementer does not tick done-items on a pipeline ticket
    Given a ticket whose archetype is a pipeline archetype
    When an implementer finishes its work
    Then it leaves the done-items unticked and the verifier ticks each one it has verified

  @AC-3
  Scenario: A hand-built ticket is ticked by the implementing session
    Given a ticket with `archetype: none` being implemented in a plain session
    When a done-item is met
    Then the session ticks it and notes the evidence, and `done` still requires every item ticked or the user's say-so

  @AC-10
  Scenario: Refinement produces numbered acceptance criteria
    Given a ticket being refined
    When the interview closes a decision
    Then the ticket gains or updates a criterion numbered one above the highest number used, retired criteria included

  @AC-7
  Scenario: An untagged scenario blocks the ready gate
    Given a scenario with no `@AC-n` tag
    When the ready gate is checked
    Then "every scenario tagged" is reported as unmet

  @AC-7
  Scenario: A criterion without a scenario blocks the ready gate
    Given a criterion that no scenario is tagged with
    When the ready gate is checked
    Then "every criterion covered" is reported as unmet

  @AC-6
  Scenario: Validate flags duplicate or dangling criterion IDs
    Given a ticket with two criteria numbered AC-2, or a scenario tagged with a criterion that does not exist
    When validate runs
    Then each problem is reported with the ticket and the ID

  @AC-6
  Scenario: Validate flags a gap in the criterion numbering
    Given a ticket with criteria AC-1 and AC-3 and no AC-2
    When validate runs
    Then a finding names the ticket and the missing AC-2, as deleted instead of retired

  @AC-6
  Scenario: A retired criterion needs no scenario
    Given a ticket whose AC-2 is written `- **AC-2 (retired)** ...` and no scenario carries `@AC-2`
    When the ready gate is checked and validate runs
    Then "every criterion covered" is met and nothing is reported about AC-2

  @AC-6
  Scenario: A scenario tagged with a retired criterion is reported
    Given a ticket whose AC-2 is retired and a scenario tagged `@AC-2`
    When validate runs
    Then a finding names the ticket, the scenario and AC-2

  @AC-11
  Scenario: Validate notes touches entries that resolve to nothing
    Given a ticket whose `touches` names a path or package that matches no existing file or directory
    When validate runs
    Then an informational note names the ticket and the entry, since entries for new files are expected to resolve to nothing before the work

  @AC-1
  Scenario: Validate flags a ready ticket that lacks the new fields
    Given a `ready` ticket with no archetype, or with scenarios that carry no `@AC-n` tag
    When validate runs
    Then each missing item is reported with the ticket and what is missing

  @AC-1
  Scenario: Validate does not flag a draft that lacks the new fields
    Given a `draft` ticket with no archetype and no criteria
    When validate runs
    Then nothing is reported about the archetype or the criteria

  @AC-5
  Scenario: A new ticket starts from the updated template
    Given the updated `TEMPLATE.md`
    When `new` creates a draft ticket
    Then the file has a blank `archetype:` line, a commented `models` example and an `## Acceptance criteria` section with one placeholder criterion (AC-1, which the placeholder scenarios are tagged with), and `validate` reports nothing about it

  @AC-2
  Scenario: A ticket with archetype none passes the gate and validate
    Given a ticket with `archetype: none` and no directory named `none` under `.claude/archetypes/`
    When the ready gate is checked and validate runs
    Then "archetype set" is met and no archetype finding is reported

  @AC-12
  Scenario: List marks tickets outside the pipeline as manual
    Given two ready tickets with no dependencies, one with `archetype: pipeline-stage` and one with `archetype: none`
    When list runs
    Then the table has an archetype column, and the `none` ticket is marked manual in "Dispatchable now"

  @AC-13
  Scenario: The CLAUDE.md Tickets section names the new conventions
    Given the updated `CLAUDE.md`
    When its Tickets section is read
    Then it mentions the `archetype` and `models` fields, the `archetype: none` value and the numbered acceptance criteria
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md` and `TEMPLATE.md`: the new fields, the gate item, the `validate` checks, the verifier rule
- `CLAUDE.md`: the Tickets section mentions the archetype, models and acceptance-criteria fields

**Already done during refinement (2026-09-21):** DFS-001 was given criteria AC-1 to AC-7 (mapping confirmed by the author) and `@AC-n` tags on its 17 scenarios, since the pilot compares against DFS-001. CCW-003 was given `archetype: none` and the retired-criterion rule. Neither is part of the implementation.

**Out of scope:**
- Writing any archetype (CCW-002) or agent (CCW-004)
- Assigning `archetype` to existing tickets (CCW-002)

## Definition of done

- [x] `SKILL.md` and `TEMPLATE.md` describe the new fields, the `ready` gate item, the `validate` checks and the verifier rule
  - Evidence: `SKILL.md` gains the `archetype` and `models` rows, the gate items, an "Acceptance criteria" section, and the `refine`, `list`, `validate` and implementer rules; `TEMPLATE.md` gains the blank `archetype:`, the commented `models` example, the criteria section and tags.
- [x] A walkthrough of each mode on a scratch copy of the backlog shows every scenario's outcome, with no false findings on existing tickets (evidence noted in this ticket)
  - Evidence: see "Walkthrough evidence" below.
- [x] The `CLAUDE.md` Tickets section mentions the archetype and models fields
  - Evidence: the Tickets paragraph now names `archetype` (and `none`), `models`, the numbered criteria and the `@AC-n` tags.
- [x] The walkthrough of `validate` shows DFS-001 and CCW-003 with no criteria or tag findings (only the archetype finding for DFS-001, until CCW-002)
  - Evidence: on the scratch copy `validate` reports only "no archetype" for DFS-001; CCW-003 and CCW-001 have no findings; no draft is flagged.
- [x] No files changed outside the paths listed under Touches
  - Evidence: `git status` shows this implementation touched only `SKILL.md`, `TEMPLATE.md`, `CLAUDE.md` and this ticket. The one-line status change in CCW-003 and the untracked `.claude/worktrees/` come from another session, not from this work.

## Open questions

None.

## Walkthrough evidence

Run on 2026-09-21 on a scratch copy of `docs/tickets/`, `.claude/skills/` and `CLAUDE.md` (the real backlog was not touched). The skill is prose, so a throwaway script transcribed its written `validate` and gate rules literally and applied them to one fixture ticket per scenario and to every existing ticket. The script is not kept in the repo. It shows the rules are unambiguous and produce no false findings on the existing backlog; it does not show that a model follows them. Scenarios about instructions (refine, the implementer rule, `list`, `CLAUDE.md`) were checked by confirming the skill and `CLAUDE.md` state the rule in words, which is a weaker check than running them.

- **Fixtures:** 26 checks, 0 failed, covering all 23 scenarios (S1 to S23): a ticket with no archetype is unmet at the gate; known, unknown and `none` archetypes; well-formed, unknown-agent and unknown-field `models`; untagged scenario and uncovered criterion at the gate; duplicate, gap, dangling and retired-tag findings; a retired criterion needing no scenario; the touches note; ready-or-later flagged and drafts not; a draft made from `TEMPLATE.md` validates cleanly.
- **Existing tickets:** DFS-001 (`ready`): one finding, "no archetype", as predicted until CCW-002. CCW-001 and CCW-003: no findings. DFS-002 to DFS-007 and CCW-002, CCW-004 to CCW-006 (drafts): no findings. Touches notes only for entries that name files not yet created (DFS-007, CCW-002, CCW-003, CCW-004, CCW-005); DFS-001's package resolves.

**Two deviations found while implementing, both small:**
- The template ships one placeholder criterion (AC-1) that the placeholder scenarios are tagged with. An empty criteria section would leave those tags dangling, and `validate` would flag every new draft. The scenario "A new ticket starts from the updated template" was reworded to match.
- "Ready-or-later" in `validate` means `ready`, `in-progress` and `in-review`. Done and cancelled tickets are history and are not checked, since a done ticket that predates the rule could never be fixed. Blocked tickets are not checked either, because a blocked ticket may never have been ready.

