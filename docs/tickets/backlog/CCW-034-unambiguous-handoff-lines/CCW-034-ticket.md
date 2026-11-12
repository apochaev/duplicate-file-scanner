---
id: CCW-034
title: Make every printed hand-off unambiguous
status: draft
type: chore
scope: [docs]
archetype:
priority: medium
lane: harness
depends_on: []
touches: []
blocked_reason:
created: 2026-11-12
updated: 2026-11-12
completed:
---

# CCW-034: Make every printed hand-off unambiguous

## Story

As the user running one session per ticket
I want every hand-off a skill prints to name one clear next step, a session name that says what it runs, and whether I may close the current session
So that I never have to guess which of several steps comes first or whether closing a session loses anything

## Context

User feedback 2026-11-12 ("not a single occurrence"): a pasted DFS-067 hand-off mixed a pending decision, three chained steps ("run /learn review ..., apply it yourself, then /tickets done") and one launch line, and did not say whether the session could be closed. The session name was the bare ticket ID, so `DFS-067` named three different sessions.

Three changes, to the hand-offs printed by `refine`'s ready commit, every story stop, `done`, `list` and `learn`:

1. Launch lines name the session `<ID>-<command>` (e.g. `DFS-067-learn-review`, `DFS-067-done`, `DFS-067-story-run`, `DFS-067-story-approve`), not the bare ID.
2. A hand-off with several steps numbers them, marks exactly one "NEXT (do this now)" and the rest "later", each with its own launch line. A pending user decision is the only NEXT, with no launch line until answered.
3. A hand-off says whether the current session is safe to close (nothing uncommitted of the next step's, nothing only in chat) or must stay and why.

CLAUDE.md's Tickets paragraph (CCW-026: "named after the ticket", `claude -n <ID>`) must allow `<ID>-<command>` names. The coordinator's catch-up and the `DFS_SESSION` name lookup map names to roles: check that `<ID>-<command>` names do not break it.

Interim: the assistant's own hand-offs already follow this (memory `feedback-always-suggest-next-step`).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-034.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

<!-- To be written in refinement. -->

## Scope

**Touches:** to be settled in refinement. Known candidates, from `grep -l "claude -n"`: `.claude/skills/story/SKILL.md`, `.claude/skills/tickets/SKILL.md`, `.claude/skills/learn/SKILL.md`, `CLAUDE.md`, `docs/design/story-orchestration.md`, `tools/story-gate/CatchUp.java`, `tools/story-gate/CatchUpSelfTest.java`.

**Out of scope:**
- Starting sessions automatically.

## Definition of done

- [ ] Every scenario above is run mechanically or checked by reading, as recorded in refinement
- [ ] Search of `.claude/`, `CLAUDE.md`, `docs/design/` and `tools/` for every other `claude -n` or "named after the ticket" mention, hits recorded in `verification.md`

## Open questions

- Exact command suffixes (`story-run`, `story-approve`, `story-learn`, `done`, `learn-review`, `start`)?
- Does `CatchUp.java`'s `session_title` / `DFS_SESSION` lookup need to change for non-coordinator names?
- Does `/learn` print launch lines today, and where?
- Archetype: `none` or a pipeline archetype? Gates?
