---
id: CCW-022
title: Add context-hygiene rules for when sessions compact or clear
status: draft
type: docs
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: [CCW-021]
touches:
  - .claude/skills/story/SKILL.md
  - .claude/skills/tickets/SKILL.md
  - CLAUDE.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# CCW-022: Add context-hygiene rules for when sessions compact or clear

## Story

As the user running dfs-backend, dfs-frontend, dfs-harness and the coordinator (dfs-product) as parallel sessions
I want each session to know when to /compact, when to /clear and what has to be written to disk before either
So that context, and with it the per-turn token cost and latency, stays small without losing state between phases or tickets

## Context

Filed 2026-11-01 by dfs-product at the user's request, from a draft the user supplied; split out of CCW-021's re-refinement (the goal: parallel sessions optimized for token usage and speed).

- Every turn resends the whole context. Cached reads cost about 10% of normal input, but a 150k-token session still pays on that whole size every turn, and quality drops in long contexts. Measured from this project's transcripts (consult-001's question, `advisory/consultations/consult-001-question.md`): long-lived main sessions dominate; dfs-backend about 755M cache-read tokens over 2,915 calls, about 260k context per call; pipeline subagents under 10% of the total.
- `/compact`: one full pass over the context to write a summary. Lossy (exact errors, small decisions and standing instructions get dropped) and resets the prompt cache. Auto-compact fires near the limit, at a moment nobody chose.
- `/clear`: free. Keeps only the system prompt, CLAUDE.md and memory, and loses everything not on disk.
- The story pipeline already runs each phase in a subagent and resumes from `plan.json` and `progress.md` (`.claude/skills/story/SKILL.md`, "Resume protocol"). The context that grows is the orchestrating or coordinating main session, not the phase agents. So a lane session can already be cleared at a story stop and resumed with one command, independent of CCW-021.
- At a human gate the resume command is `/story approve <ID>`; a plain `/story run <ID>` only re-shows the gate (resume step 2). After a `blocked` stop it is `/story run <ID>`. Free-text gate feedback is today "read from the conversation" ("Human gate"), which a `/clear` loses; `/story approve <ID> <feedback>` already carries it as an argument.
- CCW-021's Context shows the failure this prevents: an instruction given in chat ("report every ticket change") was "lost when a session restarts or compacts". Any standing instruction must live in a skill, `lanes.md` or CLAUDE.md.
- A session cannot run `/clear` itself. The user does, so the session has to say when it is safe.
- CCW-021 (pull model) gives the coordinator a catch-up script that a SessionStart hook runs at startup and after `/clear`; this ticket relies on it for the coordinator's resume. Lane sessions need nothing from CCW-021.

## Decisions

Proposed, to confirm in refine:

1. **Clear at ticket boundaries.** After `/tickets done`, and at a story stop (human gate, blocked, finished) once `plan.json`, `progress.md` and the ticket are committed. The stop text says `safe to /clear; resume with <command>`, where the command is `/story approve <ID>` (or `/story approve <ID> <feedback>`) at a gate and `/story run <ID>` after `blocked`. Recommend `/clear` at every such stop, not only above a context size: a session cannot reliably see its own size (only the status line can), and the stop is where the user steps in anyway.
2. **Compact only in the middle of a phase or ticket**, when the conversation still matters (a half-debugged failure), and steer it with a fixed template in the story skill: `/compact keep <ID>, phase <phase>, open defect <id>: <summary>, last failing output, current hypothesis`.
3. **Before either: no state only in chat.** Decisions go into the ticket's Decisions section, phase notes into `progress.md`, open questions into the ticket. A session whose working tree holds uncommitted work of its own names those files instead of "safe to /clear".
4. **Resume after a clear is one command, never a reread of the transcript.** Lane sessions: the stop's resume command, or `/tickets list`. The coordinator: CCW-021's catch-up script (run by its SessionStart hook), then `/tickets list`. Not `--resume` of an old transcript.
5. **Keep what loads every turn small.** CLAUDE.md holds rules, not ticket history. The coordinator's `/tickets list` stays compact. Bulk reads (logs, a wide grep) go to a subagent, which returns only the conclusion.

## Acceptance criteria

The full ID of each criterion is `CCW-022.AC-<n>`.

- **AC-1** Each story stop (gate, blocked, finished) ends with a "safe to /clear" line and the resume command for that stop (`/story approve <ID>` at a gate, `/story run <ID>` after `blocked`), or names the uncommitted files that make it unsafe.
- **AC-2** `/tickets done` ends with the same line.
- **AC-3** After a real `/clear` in a lane session mid-ticket, the stop's resume command resumes at the right phase with nothing re-asked.
- **AC-4** After a real `/clear` in dfs-product, the catch-up plus `/tickets list` reports the same backlog state as before.
- **AC-5** No skill or CLAUDE.md text relies on an instruction given only in conversation; gate feedback after a clear is passed as `/story approve <ID> <feedback>`.

## Scenarios

```gherkin
Feature: Context hygiene

  @AC-1
  Scenario: [run] A gate stop says it is safe to clear
    Given a /story run reaches a human gate with plan.json, progress.md and the ticket committed
    Then the stop ends with "safe to /clear; resume with /story approve <ID>"

  @AC-1
  Scenario: [read] A stop with uncommitted work names it
    Given the session's working tree holds uncommitted files of its own at a stop
    Then the stop names those files instead of "safe to /clear"

  @AC-2
  Scenario: [read] done ends with the clear line
    Then the tickets skill's done ends with the "safe to /clear" line

  @AC-3
  Scenario: [run] A lane session resumes after a real clear
    Given a lane session stopped at a gate and the user ran /clear
    When the user runs /story approve <ID>
    Then the run continues at the next phase with nothing re-asked

  @AC-4
  Scenario: [run] The coordinator resumes after a real clear
    Given dfs-product's /tickets list output before a /clear
    When the user runs /clear and then /tickets list
    Then the catch-up and the list report the same backlog state

  @AC-5
  Scenario: [read] No standing instruction lives only in chat
    Then the story skill's gate text names /story approve <ID> <feedback> for feedback after a clear
```

## Scope

**Touches:**
- `.claude/skills/story/SKILL.md`: the stop text (gate, blocked, finished), the steered `/compact` template, gate feedback after a clear.
- `.claude/skills/tickets/SKILL.md`: a "Context hygiene" section, with the `done` ending and the coordinator's resume.
- `CLAUDE.md`: one line in Tickets.

**Out of scope:**
- Measuring token use (CCW-006 left token counting unsettled).
- Automating `/clear`.
- Changing the phase agents.
- The coordinator's catch-up script and hooks (CCW-021).

## Definition of done

- [ ] Every scenario above is checked as marked (`[run]` exercised for real, `[read]` read) and recorded in `verification.md`
- [ ] No files changed outside the paths listed under Touches
- [ ] `verification.md` opens with the `## Product owner review` section

## Open questions

- Confirm decisions 1 to 5 (and the two answers folded into decisions 1 and 2) in refine.
- Re-check the scenarios against CCW-021's final catch-up design once it is ready.
