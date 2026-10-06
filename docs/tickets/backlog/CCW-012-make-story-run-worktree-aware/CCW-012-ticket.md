---
id: CCW-012
title: Make /story run worktree-aware
status: draft
type: feature
scope: [docs]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: []
touches: [.claude/skills/story/SKILL.md]
blocked_reason:
created: 2026-10-06
updated: 2026-10-06
completed:
---

# CCW-012: Make /story run worktree-aware

## Story

As the person running the story pipeline
I want `/story run` to work each story in its own worktree, branched off `main`
So that the repo checkout stays on `main`, several stories can run in parallel, and no run needs paths passed by hand or a status edit undone

## Context

From CCW-008.L-2 (`docs/tickets/completed/CCW-008-worktree-aware-tickets-lifecycle/learnings.md`), proposed, 2 recurrences:

- **CCW-008 / DFS-002:** preflight step 2 of `.claude/skills/story/SKILL.md` reads "Create it off the current branch tip if it does not exist; check it out if it does." `/story run DFS-002` left the repo checkout on the ticket branch, so no tree had `main`; closing CCW-008 needed `done`'s temporary close tree. Branching "off the current branch tip" also lets a story started from another ticket's branch inherit that branch's unmerged commits.
- **DFS-005:** the same in-place checkout; `/tickets done DFS-005` had to open a close tree and the checkout was switched back to `main` by hand before the branch could be deleted.
- **DFS-018 (2026-10-06):** `/tickets start DFS-018 --worktree` created the worktree but set the ticket `in-progress` without a `plan.json`, so preflight would have refused it ("`in-progress` with a `plan.json` already present"); the status edit was reverted. The run was then driven from the main checkout's session, with an "IMPORTANT, working tree" block naming the worktree root added by hand to every phase agent's launch prompt (architect, test-author twice, implementer, verifier, retro), and every `story-gate` check and commit run with `cd` into the worktree. That worked end to end: the repo checkout stayed on `main` throughout and `/tickets done DFS-018` merged without a close tree.

What already exists (CCW-008): `/tickets start <ID> --worktree` creates `../<repo-dir>-<ID>-<slug>` on branch `tickets/<ID>-<slug>` off `main`, or enters it if it exists; `/tickets done` merges into whichever tree has `main` and removes the worktree after one confirmation. The `story` skill never mentions worktrees today.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-012.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: <name>

  @AC-1
  Scenario: <to be written during refinement>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `.claude/skills/story/SKILL.md`: preflight, the resume protocol and the agent launch prompts (to be confirmed in refinement)

**Out of scope:**
- `/tickets start`, `review` and `done`'s own worktree handling (CCW-008), unless refinement finds a change there is required
- Running several stories concurrently from one session

## Definition of done

- [ ] Every scenario above is checked the way it says (mechanical or reading) and passes
- [ ] A real `/story run` on a ready ticket, started from the repo checkout on `main`, works the story in its own worktree and leaves the repo checkout on `main` and clean
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Archetype (likely `none`: an instructions-only change to a skill, implemented by hand).
- Preflight versus `/tickets start --worktree`: does preflight call `start --worktree` itself (as CCW-008.L-2 proposes), and what happens when the worktree already exists with the ticket still `ready`, or `in-progress` without a `plan.json` (the DFS-018 case)?
- Where the orchestrating session runs: from the repo checkout with every path pointed at the worktree (what DFS-018 did), or from a session started inside the worktree. Which `cd`, `git -C` and `story-gate` invocation form does the skill prescribe, given that a `cd` moves the session's working directory (CCW-001.L-5)?
- How the worktree root reaches each phase agent: a fixed block in every launch prompt (DFS-018's "IMPORTANT, working tree" text), or a change to the five agent definitions (which would widen `touches`).
- Whether `story-gate` needs to know about the worktree, or works unchanged when run from the worktree root (it did for DFS-018).
- Whether the branch is always created off `main` rather than the current branch tip, and what preflight does if the current checkout is not `main`.
- Whether `/story learn` and `/story status` need to find the worktree (`git worktree list --porcelain`) when invoked from the repo checkout.
- What the "Parallel work" paragraph in `.claude/skills/tickets/SKILL.md` ("as `/story run` does today") should say afterwards, and whether that edit belongs here.
- The live-run Definition of done item: which ticket it uses (a real next story, such as DFS-019, or a throwaway fixture ticket).
