---
id: CCW-031
title: Run the story pipeline's routine phases on cheaper models
status: done
type: chore
scope: [docs]
archetype: none
priority: high
lane: harness
depends_on: []
touches:
  - .claude/archetypes/component/archetype.md
  - .claude/archetypes/endpoint/archetype.md
  - .claude/archetypes/wiring/archetype.md
  - docs/tickets/lanes.md
blocked_reason:
created: 2026-11-08
updated: 2026-11-08
completed: 2026-11-08
---

# CCW-031: Run the story pipeline's routine phases on cheaper models

## Story

As the user paying for every `/story run`
I want the architect on the templated archetypes and the sessions driving `/story` for the backend and frontend lanes to run on cheaper models
So that the largest usage contributor shrinks without weakening design on core tickets

## Context

Filed 2026-11-08 by dfs-harness from Claude Code's usage report for the last 24 h: "Give story subagents a cheaper model" 59%, "Start fresh when you switch tasks" 32%, "Trim CLAUDE.md" 19% (shares overlap).

- **Measured** (every `plan.json` `runs` entry modified since 2026-10-01, grouped by phase and model):

  | Phase | Model | Runs | Turns | Not passed |
  |---|---|---|---|---|
  | design | opus | 26 | 831 | 1 |
  | verify | opus | 24 | 430 | 0 |
  | tests | sonnet | 27 | 522 | 4 |
  | implement | sonnet | 25 | 459 | 5 |
  | tests / implement | opus (escalations) | 6 | 70 | 0 |

  The two Opus phases take about 56% of all turns, and the architect alone takes 36%, at about 32 turns a run against `maxTurns` of 25 to 35.
- **What exists.** `.claude/agents/architect.md`: `opus`/`high`. Every archetype's `models:` sets `architect: {model: opus, effort: high, maxTurns: 25}` (`use-case`: 35). `retro.md` and `reflect.md`: `sonnet`/`low`. The `story` skill resolves model and effort from the ticket's `models:`, then the archetype's, then the agent's frontmatter, and moves a retried phase one tier up (`sonnet`→`opus`).
- **Why the architect can step down on some archetypes:** `component`, `endpoint` and `wiring` have skeletons and a settled "where files go"; `use-case` and `pipeline-stage` carry real domain design (ports, invariants), so they keep Opus.
- **The driving session.** CCW-029 measured main sessions at about three times all subagents together (86.8M against 26.7M input-equivalent tokens since 2026-10-25). The `/story run` loop is mechanical (read `plan.json`, launch an agent, run the gate, record a run), so the session running it could be launched on Sonnet (`claude --model sonnet -n <lane>`).
- **Related, not repeated here:** CCW-030 decides the verifier's model; CCW-029 narrows what the architect reads (it leaves the architect's model out of scope, which this ticket takes).

## Acceptance criteria

The full ID of each criterion is `CCW-031.AC-<n>`.

- **AC-1** `architect` is `{model: sonnet, effort: high, maxTurns: 25}` in the `models:` of the `component`, `endpoint` and `wiring` archetypes, and `{model: opus, effort: high}` in `use-case` and `pipeline-stage` (unchanged).
- **AC-2** `docs/tickets/lanes.md` recommends launching the `backend` and `frontend` sessions with `claude --model sonnet -n <name>`, and keeps `harness` and `dfs-product` on Opus.
- **AC-3** `retro.md` and `reflect.md` keep `model: sonnet` and `effort: low` (decision 2: no change).
- **AC-4** `tools/story-gate/story-gate selftest` passes after the edits.

## Scenarios

Archetype `none`: each scenario is run mechanically (a command) or checked by reading, as recorded.

```gherkin
Feature: Routine pipeline phases run on cheaper models

  @AC-1
  Scenario: Templated archetypes step the architect down (mechanical)
    Given the `models:` block of each archetype.md
    When `grep -n "architect:"` runs over .claude/archetypes/*/archetype.md
    Then component, endpoint and wiring show `model: sonnet, effort: high, maxTurns: 25`

  @AC-1
  Scenario: Core archetypes keep Opus (mechanical)
    Given the same grep
    When it lists use-case and pipeline-stage
    Then use-case shows `model: opus, effort: high, maxTurns: 35` and pipeline-stage `model: opus, effort: high, maxTurns: 25`

  @AC-1
  Scenario: A failed Sonnet design attempt is retried on Opus (read)
    Given the story skill's step 6 rule "one tier above whatever that resolved to"
    When a wiring ticket's first design attempt fails
    Then by that rule the second attempt resolves to model opus, and the verifier's note in verification.md quotes the rule

  @AC-2
  Scenario: Lane note recommends Sonnet for backend and frontend only (mechanical)
    Given docs/tickets/lanes.md
    When `grep -n "model" docs/tickets/lanes.md` runs
    Then the hits name backend and frontend with `--model sonnet`, and harness and dfs-product as staying on Opus

  @AC-3
  Scenario: Retro and reflect are unchanged (mechanical)
    Given .claude/agents/retro.md and reflect.md
    When `git diff main -- .claude/agents` runs
    Then it prints nothing, and their frontmatter reads `model: sonnet`, `effort: low`

  @AC-4
  Scenario: The gate still passes (mechanical)
    Given the edited archetype files
    When `tools/story-gate/story-gate selftest` runs
    Then it exits 0
```

## Scope

**Touches:**
- `.claude/archetypes/component/archetype.md`, `endpoint/archetype.md`, `wiring/archetype.md`: the `architect:` line of `models:`
- `docs/tickets/lanes.md`: the launch-model recommendation

**Out of scope:**
- The verifier's model (CCW-030).
- What the architect reads (CCW-029).
- The test-author and implementer, already on Sonnet; retro and reflect (decision 2).
- `docs/design/story-orchestration.md` line 176 (agent default table, `opus`) and the `models` example in the `tickets` skill and template: they describe the default and an example, not these archetypes, so they stay as they are.

## Definition of done

- [x] Every scenario above is checked (mechanically or by reading, as recorded per scenario) — verification.md, per criterion
- [x] `tools/story-gate/story-gate selftest` passes — PASS, 126 cases
- [x] No files changed outside the paths listed under Touches — `git diff main --stat`: three archetypes, lanes.md, the ticket's own folder
- [x] A search of `.claude/`, `CLAUDE.md` and `docs/design/` for the old `architect: {model: opus` form is recorded in `verification.md`, each remaining hit explained
- [x] `verification.md` opens with the `## Product owner review` section
- [x] After the first Sonnet-driven story, a note records whether its gates and notifications fired as the `story` skill specifies (an unverified assumption, decision 3) — deferred to a follow-up by the user's say-so (2026-11-08): checked on the first Sonnet-driven story after merge

## Open questions

None.

## Decisions

1. **Architect step-down (decided 2026-11-08):** `architect: {model: sonnet, effort: high}` in the `models:` of `component`, `endpoint` and `wiring` only; `use-case` and `pipeline-stage` keep `opus/high`. `maxTurns` is not a lever: the Agent tool cannot pass it, so the architect's own frontmatter (35) binds and already covers every archetype. A failed Sonnet attempt retries on Opus by the existing rule.
2. **Retro and reflect (decided 2026-11-08):** no change. Both are already `sonnet/low`, run at most 15 turns, and `retro` only when there were defects or learnings; `haiku` could weaken proposals against shared harness files. They leave this ticket's scope.
3. **Driving session (decided 2026-11-08):** a recommendation, not enforcement, in `docs/tickets/lanes.md`: the `backend` and `frontend` sessions are launched with `claude --model sonnet -n <name>`; `harness` and the coordinator stay on Opus, since they edit skills, agents and tickets. That a Sonnet session follows the `story` skill's gates and notifications is an unverified assumption, checked by the first Sonnet-driven story (a Definition of done item).
4. **Confirmation (decided 2026-11-08):** the next three design phases on `component`, `endpoint` and `wiring` tickets, whatever the mix, each compared with a recent Opus run of the same archetype (turns, retry to Opus, defects routed to the architect). An archetype goes back to `opus/high` if two of its Sonnet runs needed the Opus retry or caused a design-routed defect. The review is a follow-up the user runs, not part of this ticket.
