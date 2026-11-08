---
id: CCW-028
title: Apply the read-before-edit rule to the implementer, test-author and verifier
status: draft
type: chore
scope: [docs]
archetype:
priority: medium
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# CCW-028: Apply the read-before-edit rule to the implementer, test-author and verifier

## Story

As the user paying for every pipeline run
I want every pipeline agent that edits files to read them with the Read tool first
So that no agent spends a full-context round trip on a write the tool refuses

## Context

Filed 2026-11-07 by dfs-harness after closing CCW-024, at the user's request to log tickets that further reduce cost.

- **The refusal.** Write and Edit refuse to change an existing file that was not read through the Read tool ("File has not been read yet"); printing it with `cat` or `sed` does not count. Each refused call is another request carrying the agent's whole context. CCW-024 added the rule to `.claude/agents/architect.md` only ("What to read", second paragraph); its Known limits named the other agents as unmeasured.
- **Measured** (all subagent transcripts of this project started on or after 2026-10-25, counted by `agent-*.meta.json` `agentType`, 2026-11-07): refused writes per agent: implementer 29 in 17 runs, test-author 6 in 22, verifier 6 in 19, architect 44 in 18 (before CCW-024; 0 in DFS-054 and DFS-057 after it).
- The implementer edits the architect's stub files, which it has usually only seen through `cat`/`grep` or not at all, so its rate is highest.
- Proposed change: the same paragraph as `architect.md`'s, adapted, in `implementer.md`, `test-author.md` and `verifier.md` (the verifier edits `verification.md` and ticks done-items in the ticket).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-028.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: Read before edit in every editing agent

  @AC-1
  Scenario: <the implementer reads a stub with Read before editing it>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <likely `.claude/agents/implementer.md`, `.claude/agents/test-author.md`, `.claude/agents/verifier.md`>

**Out of scope:**
- The architect (done in CCW-024).
- Changing what the agents read.

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] The next pipeline run after the merge shows no `File has not been read yet` in any phase transcript

## Open questions

1. Put the rule in each agent file, or once in a shared place the agents already read (`CLAUDE.md` "Tickets" or the story skill's launch prompt)?
2. Does the retro or reflect agent need it? Neither has Write or Edit, so probably not.
