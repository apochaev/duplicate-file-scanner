---
id: CCW-025
title: Drop the coordinator's ready hand-off message
status: cancelled
type: chore
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: [CCW-021]
touches: []
blocked_reason:
created: 2026-11-07
updated: 2026-11-08
completed: 2026-11-08
---

# CCW-025: Drop the coordinator's ready hand-off message

## Story

As the user running parallel sessions to save tokens
I want the coordinator to stop messaging a lane's owner when it makes a ticket ready
So that no session is woken for a full turn at its context size just to learn of a ticket, and I decide when each lane starts what

## Context

Decided by the user on 2026-11-07 in `dfs-product`, after the coordinator sent `dfs-frontend` the ready hand-off for DFS-057 (and `dfs-backend` one for DFS-052): "you sent a message to dfs frontend, we worked on preventing that didnt we?"

- **What exists:** CCW-021 decision 6 and the `tickets` skill's "The ready hand-off" (`.claude/skills/tickets/SKILL.md`, "Sessions and lanes", "Messages" and "The coordinator", and `refine`'s coordinator step): after a `ready` commit in another session's lane, the coordinator sends the owner message 2's form (an unblock, launch point 3) for an `autorun` ticket with every dependency `done`, else message 1's form.
- **Why drop it:** CCW-021 decision 7 chose pull over push because every message wakes a long-lived session for a full turn at its current context size. Decision 7 removed the messages to the coordinator; the coordinator's own message to owners was kept and costs the same. The user already chooses the order within a lane (DFS-057 before DFS-054).
- **The replacement:** the coordinator ends its ready commit by telling the user which session runs `/story run <ID>`; an owner sees ready tickets at its next session start or `/tickets list`. Whether an owner's session start should print its lane's dispatchable tickets (a catch-up line like the coordinator's) is an open question.
- **Interim (2026-11-07):** until this lands, `dfs-product` sends no ready hand-offs and names the command for the user instead.

## Acceptance criteria

The full ID of each criterion is `CCW-025.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: The coordinator does not message on ready

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined; likely `.claude/skills/tickets/SKILL.md`, `CLAUDE.md` (Tickets), `docs/tickets/backlog/CCW-021-coordinator-session/CCW-021-ticket.md` or its successor note>

**Out of scope:**
- Messages 1 to 3 between lane sessions (requests, unblocks on `done`, harness changes landed).
- The coordinator's catch-up (CCW-021).

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Remove the hand-off outright, or keep it opt-in (the user asks for it per ticket)?
- Should an owner's session start print its lane's dispatchable tickets, so a ready ticket is seen without a message?
- CCW-021 is in progress in `dfs-harness`'s worktree: fold this into it, or land after it (`depends_on`)?
- Archetype (`none` likely, as CCW-017, CCW-018, CCW-021).

## Resolution

Cancelled 2026-11-08, superseded by CCW-026: its decision 3 removes lane ownership, messages 1 to 3 and the coordinator's ready hand-off, and decisions 1, 5 and 6 replace them with printed `claude -n` launch lines, which also answers this ticket's open question. Until CCW-026 lands, the coordinator sends no ready hand-offs and names the command for the user.
