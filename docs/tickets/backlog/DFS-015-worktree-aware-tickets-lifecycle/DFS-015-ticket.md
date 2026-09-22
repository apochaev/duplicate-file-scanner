---
id: DFS-015
title: Make the tickets lifecycle worktree-aware
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [.claude/skills/tickets, CLAUDE.md]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-015: Make the tickets lifecycle worktree-aware

## Story

As the maintainer running several tickets from separate sessions and worktrees
I want `start`, `review` and `done` to know which tree and branch a ticket's work lives in
So that a closed ticket's files end up on `main`, and no worktree or branch is left behind

## Context

The `tickets` skill changes a ticket's status and moves its folder in whichever working tree it runs in. It has no notion of branches or worktrees, and `done` neither checks that the work reached `main` nor cleans up. Working a ticket in a worktree (which parallel sessions and the story pipeline both do) exposed three gaps on DFS-010 (2026-09-21), recorded as `L-008` in `docs/learnings/log.md`:

- **`start` ran in the wrong tree.** It set `in-progress` in the main checkout before the worktree existed. The branch then carried its own change to the same ticket file, and the merge would have been refused until that uncommitted edit was discarded.
- **`main` moved during the work.** A fast-forward was no longer possible, so the branch's commits were replayed onto the new `main` (and reworded to follow the ticket-ID subject rule that arrived meanwhile).
- **`done` has no cleanup.** After the merge the worktree and two branches (`worktree-dfs-010-story-gate`, `dfs-010-story-gate-tools`) remained.

Related work: the story pipeline (D5, DFS-012) records a `branch` per story in `plan.json` and needs the same start and done semantics, so this ticket and DFS-012 must agree on one implementation (see Open questions). Constraints already in `CLAUDE.md` bind the skill: commits only when the user asks, the ticket ID ends the commit subject, and files are staged by name whenever the working tree holds other changes.

Intended shape, still to be refined: `start` creates or enters the worktree and marks `in-progress` there; `review` records its status on the branch; `done` runs against `main`, verifies the ticket's work is on `main`, moves the ticket folder to `completed/` there, and removes the worktree and branch after confirmation.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-015.AC-<n>`.

- **AC-1** <one testable statement>

## Scenarios

```gherkin
Feature: Worktree-aware tickets lifecycle

  Background:
    Given <shared precondition>

  @AC-1
  Scenario: <happy path>
    Given <concrete state>
    When <action>
    Then <observable outcome>

  @AC-1
  Scenario: <edge case>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- `.claude/skills/tickets`: the `start`, `review` and `done` modes (and `cancel`, which shares `done`), and the template if it needs a field
- `CLAUDE.md`: a short note on working a ticket in a worktree, if the refinement decides one is needed

**Out of scope:**
- <to be decided during refinement; candidates: the `/story` skill's own branch handling (DFS-012), pushing or pull requests>

## Definition of done

- [ ] <a skill has no automated tests: decide how each scenario is verified, then list it here>
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

Decisions to close during refinement, one at a time:

1. **What "in main" means.** The branch tip is an ancestor of `main` (fast-forward or merge commit only), or squash merges are accepted too.
2. **Who merges.** `done` performs the fast-forward itself, or it only verifies and the user merges.
3. **What cleanup removes and with what safeguards.** The worktree and the branch, using a safe branch delete (`git branch -d`), after a confirmation, and refusing on a dirty worktree or unmerged commits. What happens to backup branches left by a rebase.
4. **Where the close is committed.** A `docs(tickets): close DFS-nnn [DFS-nnn]` commit on `main` with the files staged by name, and whether the skill makes it or asks first (commits are only made when the user asks).
5. **`main` moved.** `done` rebases the branch, or refuses and says so.
6. **Where `start` runs.** It creates the worktree itself, or requires being run inside one; and the plain case of a ticket worked directly on `main` must keep working unchanged.
7. **Order with DFS-012.** This ticket lands first and DFS-012 depends on it, or the two share a design.
8. **Archetype and verification.** The archetype (a name, or `none` for a hand-built ticket) and how a change to skill text is verified without automated tests.
