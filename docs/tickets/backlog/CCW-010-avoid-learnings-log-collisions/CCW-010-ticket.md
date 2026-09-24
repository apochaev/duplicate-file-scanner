---
id: CCW-010
title: Avoid learnings-log number collisions across parallel sessions
status: draft
type: fix
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [.claude/skills/learn/SKILL.md, .claude/skills/story/SKILL.md]
blocked_reason:
created: 2026-09-23
updated: 2026-09-23
completed:
---

# CCW-010: Avoid learnings-log number collisions across parallel sessions

## Story

As two sessions working on diverged branches at the same time
I want the learnings log's `L-<n>` numbers to never collide
So that closing or merging either session's work never forces a hand-resolved renumbering

## Context

Spun off from `docs/learnings/log.md` L-033 rather than applied directly — real design questions (where a draft entry lives before it has a global number, exactly when that number is assigned, how that interacts with CCW-008's "one true `main` tree" serialization) across two skills, not a one-line edit. Follows the precedent L-008 (→ CCW-008) and L-027 (→ CCW-009) already set for the same reason.

Both `/learn add`/`apply`/`reflect` (`.claude/skills/learn/SKILL.md`) and `/story learn` (`.claude/skills/story/SKILL.md`'s `learn` mode) allocate a new entry's number the same way: `grep -o '^### L-[0-9]*'` against the session's own copy of `docs/learnings/log.md`, highest plus one. Two sessions on diverged branches each see only their own branch's copy, so each can independently compute the same "next" number for two entirely unrelated findings.

This happened for real closing DFS-002 (2026-09-23): a parallel session had built and closed CCW-008 directly on `main`, proposing its own two learnings the same way this session did. Both sessions landed on `L-024` and `L-025` for four unrelated findings between them. Discovered only at merge time (`git merge` conflicted on `docs/learnings/log.md`); resolved by hand, renumbering the `main`-side pair to `L-031`/`L-032` after the fact. The log's own rule ("numbers are never reused") was already broken between the two branches before either was merged — this ticket exists so the next collision doesn't need a human to notice and fix it during a merge.

The general shape already exists for this kind of problem: each story already keeps its own free-text `learnings.md` (raw friction notes, D6) separate from the shared, numbered `docs/learnings/log.md`. The open question is whether a *structured* draft (the log's eight fields, but no number yet) belongs in that same per-story file, and — the harder half — when a draft actually becomes a numbered entry in the shared log: at `/tickets done`'s existing merge-to-`main` step (which CCW-008 already serializes through one true `main` tree), at a dedicated `/learn <mode>` step, or some other point. This ticket has none of that decided yet.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-010.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: <name>

  Background:
    Given <shared precondition>

  @AC-1
  Scenario: <happy path>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `.claude/skills/learn/SKILL.md`: `add`, `apply`, `reflect` — wherever a new entry's number is allocated
- `.claude/skills/story/SKILL.md`: `learn` mode — same allocation point

**Out of scope:**
- Any change to `docs/learnings/log.md`'s own entry format (the eight fields) — only when/how a number is assigned
- Retroactively fixing any other stale cross-branch reference outside the learnings log

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

<!-- Must read "None." before status can be `ready`. -->
- Where does a draft (unnumbered) structured entry live before it has a global number — inside each story's existing `learnings.md`, or a new file?
- Exactly when is the global number assigned: at `/tickets done`'s merge-to-`main` step, at a dedicated `/learn` step, or somewhere else?
- Does this reuse CCW-008's "one true `main` tree" serialization directly, or does it need its own coordination point?
