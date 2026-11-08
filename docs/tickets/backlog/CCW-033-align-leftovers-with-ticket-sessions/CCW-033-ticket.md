---
id: CCW-033
title: Align archetypes and the launch model with sessions per ticket
status: draft
type: docs
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: medium
lane: harness
depends_on: [CCW-026]
touches:
  - .claude/archetypes/component/archetype.md
  - .claude/archetypes/endpoint/archetype.md
  - docs/tickets/lanes.md
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

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-033.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: Harness leftovers follow sessions per ticket

  @AC-1
  Scenario: <to refine>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `.claude/archetypes/component/archetype.md`: line 19, the cross-lane endpoint case as a draft in `depends_on`
- `.claude/archetypes/endpoint/archetype.md`: line 19, the same for a needed use case
- `docs/tickets/lanes.md`: the CCW-031 "Launch model" paragraph rewritten for sessions per ticket, or removed
- possibly `.claude/skills/tickets/SKILL.md` and `tools/story-gate/CatchUp.java` (with `CatchUpSelfTest.java`), if launch lines gain `--model` (open question 1)

**Out of scope:**
- Changing which model each pipeline agent runs on (CCW-031, the archetypes' `models:`).
- Trimming `CLAUDE.md` (CCW-032).

## Definition of done

- [ ] `grep -rn -i "lane that owns\|request to the" .claude/` finds nothing
- [ ] `lanes.md` names no `dfs-backend`, `dfs-frontend` or `dfs-harness` session
- [ ] `tools/story-gate/story-gate selftest` passes, if `CatchUp.java` changed
- [ ] No files changed outside the paths listed under Touches

## Open questions

1. Do printed launch lines carry the model? Options: (a) `--model sonnet` in every `/story run` line for a `backend` or `frontend` ticket, `harness` lines without (the `story` skill, `refine`, `done` step 4a, `list` and `CatchUp.java` all print lines); (b) one line for all, the recommendation kept as a sentence in `lanes.md` for the user to add by hand; (c) drop the Sonnet driving recommendation. Coordinator's leaning: (a), since it keeps CCW-031 decision 3 without the user remembering a flag, at the cost of touching the skill and the script again.
2. Archetype or `none`: two one-line archetype edits and a paragraph are `none`; option 1(a) adds a script change with self-tests, still small enough to do by hand.
3. Where CCW-031's deferred Sonnet check is recorded once the first Sonnet-driven ticket session runs.
