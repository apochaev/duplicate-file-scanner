---
id: CCW-023
title: Wrap the contextual Codex consultation in one bounded command
status: draft
type: feature
scope: [docs, build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: low
lane: harness
depends_on: [CCW-021]
touches: []
blocked_reason:
created: 2026-11-02
updated: 2026-11-08
completed:
---

# CCW-023: Wrap the contextual Codex consultation in one bounded command

## Story

As the coordinator (dfs-product) evaluating advice
I want one command that asks a fork of the saved advisor conversation one read-only question and records the answer, or records "no review" when it fails
So that a consultation is repeatable, bounded in time and cost, and never mistaken for approval

## Context

Filed 2026-11-02 by dfs-product from ADV-002-r1 (`advisory/proposals/ADV-002-r1.md`) and consult-001 (`advisory/consultations/consult-001-*.md`); split out of CCW-021's re-refinement (CCW-021 decision 7).

- The procedure exists as a one-off handoff, `advisory/CLAUDE-HANDOFF.md`: `codex exec --sandbox read-only -C <repo root> fork <parent session id> -o <unique output> "<prompt>"`, never `--last`, never resuming the parent. consult-001 ran it once from Claude's Git Bash under `timeout 900`: completed, exit 0, answer captured, no repository edits, the parent session not written to.
- The installed agent-collaboration plugin's codex-consult runs a fresh `npx ... exec`, so it cannot carry the advisor's context; consult-001 recommends the project-local procedure, not the plugin's launcher, and not editing the plugin cache.
- ADV-002's compatibility findings still open after consult-001: 3 (deterministic output capture and exit verification; failure or timeout recorded as no review, never as approval), 5 (a deadline is a budget, not proof of a hang; stop only the launched process; no automatic paid retry), 6 (unique request IDs, no collisions). Findings 1 and 2 no longer block the launcher consult-001 used; 4 is moot if the plugin's `tmp/` is unused.
- Cost: consult-001 used 543,536 Codex tokens, mostly the inherited parent context. Use selectively, never for every proposal.
- Not tested yet: failure on an invalid session ID.
- consult-001's answer also suggests a superseding ADV-002 revision that distinguishes inherited saved context from live synchronization; that is the advisor's to publish, not this ticket's.

## Acceptance criteria

The full ID of each criterion is `CCW-023.AC-<n>`.

- **AC-1** <to refine: one command allocates a unique request ID, writes the question record, runs the fork read-only under a time budget, and captures the answer at a known path>

## Scenarios

```gherkin
Feature: Contextual consultation

  @AC-1
  Scenario: <to refine>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to refine: a script location (for example `tools/advisory/`), the coordinator's instructions, possibly `advisory/CLAUDE-HANDOFF.md`>

**Out of scope:**
- Discovery of pending advice (CCW-021).
- Background polling, automatic consultations, or any consultation that approves a gate or implementation.
- Changing the agent-collaboration plugin.

## Definition of done

- [ ] <to refine>
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Archetype: `none` (a script plus instructions, like CCW-021) or a pipeline archetype?
- Script language and place: a story-gate subcommand like CCW-021's catch-up, or a separate shell wrapper around `codex exec`?
- Time budget and what is recorded on timeout; whether a confirmation is required before every paid run (the user's say-so per consultation, as today).
- How the invalid-session failure is tested without a paid model call.
- Where the parent session ID lives (in `advisory/CLAUDE-HANDOFF.md` today) and how a new advisor conversation replaces it.
