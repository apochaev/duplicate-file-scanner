---
id: CCW-033
title: Align archetypes and the launch model with sessions per ticket
status: draft
type: docs
scope: [docs]
archetype: none
priority: medium
lane: harness
depends_on: [CCW-026]
touches:
  - .claude/archetypes/component/archetype.md
  - .claude/archetypes/endpoint/archetype.md
  - docs/tickets/lanes.md
  - .claude/skills/tickets/SKILL.md
  - .claude/skills/story/SKILL.md
  - tools/story-gate/CatchUp.java
  - tools/story-gate/CatchUpSelfTest.java
  - CLAUDE.md
  - docs/design/story-orchestration.md
blocked_reason:
created: 2026-11-08
updated: 2026-11-08
completed:
---

# CCW-033: Align archetypes and the launch model with sessions per ticket

## Story

As the user running the backlog with one session per ticket (CCW-026)
I want the harness files outside CCW-026's `touches` to describe that model, and the cheaper driving model of CCW-031 to reach the printed launch lines
So that no archetype or lane note sends a session looking for a lane owner, and backend and frontend stories still run on Sonnet

## Context

Filed 2026-11-08 by the coordinator from CCW-026's `verification.md` ("Known limits") and a read of the merge of CCW-026 with `main`.

- **Archetypes name the old lane-ownership model.** `.claude/archetypes/component/archetype.md:19`: "a screen that needs a new or changed endpoint is two tickets, the endpoint first, or a request to the lane that owns it". `.claude/archetypes/endpoint/archetype.md:19`: "the `use-case` one first, or a request to the backend lane". After CCW-026 the `new` mode has no request form and no messages: work a ticket needs in another lane is a draft in that lane, added to the ticket's `depends_on`.
- **CCW-031's launch model goes stale on merge.** CCW-031 (closed 2026-11-08, after CCW-026 branched) added a "Launch model" paragraph to `docs/tickets/lanes.md`: launch `dfs-backend` and `dfs-frontend` with `claude --model sonnet -n <name>`, keep `dfs-harness` and `dfs-product` on Opus. CCW-026 rewrites `lanes.md` to hold only `dfs-product: coordinator`; git merges the two cleanly (checked with `git merge-tree`), so the paragraph survives but names sessions that no longer exist. CCW-026's launch lines are `claude -n <ID> --permission-mode auto "<command>"`, with no `--model`, so every story session runs on the default model (Opus) and CCW-031 decision 3's saving is lost.
- **CCW-031's open check.** Its done-item "after the first Sonnet-driven story, a note records whether its gates and notifications fired" was deferred by the user to "the first Sonnet-driven story after merge". With no `--model sonnet` in any printed line, no such story happens.
- Not in scope here, already handled on `main`: the commented `# autorun: false` lines in five drafts (commit 4eb4e50) and CCW-032's scope wording (fb89689).

## Acceptance criteria

The full ID of each criterion is `CCW-033.AC-<n>`.

- **AC-1** `.claude/archetypes/component/archetype.md` says a screen that needs a new or changed endpoint is two tickets, the endpoint first, or a draft in the endpoint's lane added to the screen ticket's `depends_on`; no file under `.claude/` says "the lane that owns it".
- **AC-2** `.claude/archetypes/endpoint/archetype.md` says a ticket that needs a core use case is two tickets, the `use-case` one first, or a draft in the backend lane added to the ticket's `depends_on`; no file under `.claude/` says "a request to the backend lane".
- **AC-3** Every launch line the `tickets` and `story` skills print for a ticket whose `archetype` is not `none` reads `claude -n <ID> --model sonnet --permission-mode auto "<command>"`, for every command of that ticket (`/story run`, `/story approve`, `/story learn`, `/tickets done`); a line for an `archetype: none` ticket reads `claude -n <ID> --permission-mode auto "<command>"`.
- **AC-4** `story-gate catch-up --session <name> --lanes` prints `--model sonnet` in the launch line of a started ticket whose archetype is not `none`, and no `--model` in the line of an `archetype: none` ticket.
- **AC-5** `docs/tickets/lanes.md` names no `dfs-backend`, `dfs-frontend` or `dfs-harness` session, states the archetype rule of AC-3 with its reason (a pipeline session only drives the loop; a hand-implemented ticket edits files), and keeps the coordinator on the default model.
- **AC-6** `CLAUDE.md` and `docs/design/story-orchestration.md`, where they give the launch line's shape, give the AC-3 rule.
- **AC-7** `tools/story-gate/story-gate selftest` passes.

## Scenarios

```gherkin
Feature: Harness leftovers follow sessions per ticket

  @AC-1 @AC-2
  Scenario: Archetypes name no lane owner (mechanical)
    Given the archetype files on the ticket's branch
    When `grep -rn -i "lane that owns\|request to the" .claude/` runs
    Then it prints nothing
    And line 19 of the component and endpoint archetypes names a draft added to `depends_on`

  @AC-3
  Scenario: A pipeline ticket's lines carry Sonnet (read)
    Given the `tickets` skill's "Sessions per ticket", `refine`, `list` and `done` step 4a, and the `story` skill's usage line and "Every stop" step 3
    When each is read for a ticket with `archetype: component`
    Then each launch line reads `claude -n <ID> --model sonnet --permission-mode auto "<command>"`

  @AC-3
  Scenario: A hand-implemented ticket's line stays on the default model (read)
    Given the same places
    When each is read for a ticket with `archetype: none`
    Then the line reads `claude -n <ID> --permission-mode auto "/tickets start <ID> --worktree"`, with no `--model`

  @AC-3
  Scenario: Every printed launch line has one of the two shapes (mechanical)
    Given `.claude/skills/`
    When `grep -n "claude -n" .claude/skills/` runs
    Then every hit has `-n <ID>` first and `--permission-mode auto`, and each pipeline line has `--model sonnet` between them

  @AC-4 @AC-7
  Scenario: Catch-up picks the model by archetype (mechanical, selftest)
    Given a worktree for DFS-061 (`archetype: component`, at the design gate) and one for CCW-062 (`archetype: none`, `in-progress`) in the selftest's fixture
    When `story-gate catch-up --session CCW-099 --lanes` runs
    Then DFS-061's line reads `claude -n DFS-061 --model sonnet --permission-mode auto "/story approve DFS-061"`
    And CCW-062's line has no `--model`

  @AC-5
  Scenario: The lane note follows the rule (mechanical)
    Given `docs/tickets/lanes.md`
    When `grep -n "dfs-backend\|dfs-frontend\|dfs-harness\|--model" docs/tickets/lanes.md` runs
    Then no session name other than `dfs-product` appears, and the `--model sonnet` hit states the archetype rule

  @AC-6
  Scenario: Prose matches the rule (mechanical)
    Given `CLAUDE.md` and `docs/design/story-orchestration.md`
    When `grep -n "permission-mode auto" CLAUDE.md docs/design/story-orchestration.md` runs
    Then every hit that gives the line's shape also says pipeline tickets launch with `--model sonnet`
```

## Scope

**Touches:**
- `.claude/archetypes/component/archetype.md`: line 19, the cross-lane endpoint case as a draft in `depends_on`
- `.claude/archetypes/endpoint/archetype.md`: line 19, the same for a needed use case
- `docs/tickets/lanes.md`: the CCW-031 "Launch model" paragraph rewritten to the archetype rule (decision 1)
- `.claude/skills/tickets/SKILL.md`: the launch-line shape ("Sessions per ticket"), `refine`, `list`, `done` step 4a
- `.claude/skills/story/SKILL.md`: the usage line and "Every stop" step 3
- `tools/story-gate/CatchUp.java`: `launchLine` takes the archetype; `CatchUpSelfTest.java`: its expected lines
- `CLAUDE.md`: the Tickets section's mention of the launch line
- `docs/design/story-orchestration.md`: its two mentions of the launch line

**Out of scope:**
- Changing which model each pipeline agent runs on (CCW-031, the archetypes' `models:`).
- Trimming `CLAUDE.md` (CCW-032).

## Definition of done

- [ ] Every scenario above checked as marked (mechanical or read), with the evidence noted beside this item
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] The old-form search repeated before `review` (CCW-018.L-1): `claude -n`, `permission-mode auto`, `--model`, `dfs-backend`, `dfs-frontend`, `dfs-harness`, `lane that owns`, `request to the` over `.claude/`, `CLAUDE.md`, `docs/design/`, `docs/tickets/lanes.md`, `tools/story-gate/`; the search and its hits recorded in `verification.md`
- [ ] `verification.md` opens with the `## Product owner review` section (`.claude/agents/verifier.md`)
- [ ] No files changed outside the paths listed under Touches

## Decisions

1. **Launch model keyed on archetype (decided 2026-11-08).** Every launch line of a ticket whose archetype is not `none` carries `--model sonnet`, in any lane and for every command of that ticket (the same session runs it to `done`); an `archetype: none` line has no `--model` and runs on the default model (Opus). CCW-031 kept `dfs-harness` on Opus because it edited skills by hand; under sessions per ticket that work is an `archetype: none` ticket, while a pipeline session only drives the loop whatever its lane. `CatchUp` already reads each ticket's `archetype`, so no new field is parsed. Rejected: keying on lane (CCW-031's literal rule; every printer would need lane and archetype), a flag the user adds by hand, dropping the recommendation. Flag order: `-n <ID>` first, then `--model sonnet`, then `--permission-mode auto` (CCW-026.AC-10 checks the ID after `-n`).
2. **Archetype `none` (decided 2026-11-08).** Implemented by hand in its own session, like CCW-026: instruction edits plus a small `CatchUp` change with self-test cases. No archetype fits harness work; all five build product code. No gates (not a pipeline ticket). By decision 1 its session runs on the default model.
3. **CCW-031's Sonnet check moves to the next pipeline ticket (decided 2026-11-08).** It can only run after this ticket merges, so it is no done-item here (CCW-026.L-1). When the coordinator next refines a pipeline ticket after CCW-033 lands, it adds to that ticket's Definition of done: "the session ran on `--model sonnet` (its session header); every gate stopped and notified as the `story` skill specifies; recorded under 'How it went' in `verification.md`".

## Open questions

None.
