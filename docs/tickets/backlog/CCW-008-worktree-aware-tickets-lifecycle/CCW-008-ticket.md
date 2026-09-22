---
id: CCW-008
title: Make the tickets lifecycle worktree-aware
status: ready
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [.claude/skills/tickets, CLAUDE.md]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed:
---

# CCW-008: Make the tickets lifecycle worktree-aware

*(Formerly DFS-015; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the maintainer running several tickets from separate sessions and worktrees
I want `start`, `review` and `done` to know which tree and branch a ticket's work lives in
So that a closed ticket's files end up on `main`, and no worktree or branch is left behind

## Context

The `tickets` skill changes a ticket's status and moves its folder in whichever working tree it runs in. It has no notion of branches or worktrees, and `done` neither checks that the work reached `main` nor cleans up. Working a ticket in a worktree (which parallel sessions and the story pipeline both do) exposed three gaps on CCW-003 (2026-09-21), recorded as `L-008` in `docs/learnings/log.md`:

- **`start` ran in the wrong tree.** It set `in-progress` in the main checkout before the worktree existed. The branch then carried its own change to the same ticket file, and the merge would have been refused until that uncommitted edit was discarded.
- **`main` moved during the work.** A fast-forward was no longer possible, so the branch's commits were replayed onto the new `main` (and reworded to follow the ticket-ID subject rule that arrived meanwhile).
- **`done` has no cleanup.** After the merge the worktree and two branches (`worktree-dfs-010-story-gate`, `dfs-010-story-gate-tools`) remained.

Related work: the story pipeline (D5, CCW-005) records a `branch` per story in `plan.json` and needs the same start and done semantics, so this ticket and CCW-005 must agree on one implementation (see Open questions). Constraints already in `CLAUDE.md` bind the skill: commits only when the user asks, the ticket ID ends the commit subject, and files are staged by name whenever the working tree holds other changes.

Intended shape, still to be refined: `start` creates or enters the worktree and marks `in-progress` there; `review` records its status on the branch; `done` runs against `main`, verifies the ticket's work is on `main`, moves the ticket folder to `completed/` there, and removes the worktree and branch after confirmation.

**Decided (2026-09-21): `done`'s "is this on `main`" check is `git merge-base --is-ancestor <branch> main`, falling back to a grep of `main`'s log for the ticket ID if that fails.** The plain ancestor check alone covers CCW-003's own rebase-and-fast-forward landing; the fallback catches a squash merge, where the branch tip is never an ancestor even though its content landed. The maintainer's landing style varies by ticket ("depends"), so both checks are needed rather than just the cheaper one.

**Decided (2026-09-21): `done` only verifies; it never merges, fast-forwards, or rebases anything itself.** If neither check above passes, it refuses, reports which check failed, and tells the user to merge or rebase and re-run `done`. This keeps CLAUDE.md's "commits only when the user asks" rule intact without a carve-out — a fast-forward moves `main`'s ref, which is the kind of state change that rule protects — and it means "`main` moved during the work" (the ticket's open question 5) is the same situation as "who merges" (question 2), not a separate case: `done` refuses and says so either way.

**Decided (2026-09-21): cleanup removes the worktree and deletes the branch with `git branch -d` (the non-force, safe delete), after refusing on a dirty worktree or on a branch `git branch -d` itself refuses (unmerged commits), and one confirmation before deleting either.** Rebase backup refs (e.g. `refs/original/*`) are left untouched — sweeping them isn't this ticket's job, and doing so would remove the user's own recovery path for the rebase itself.

**Decided (2026-09-21): `done` commits the ticket-folder move and frontmatter update on `main` itself**, files staged by name, subject `docs(tickets): close DFS-nnn [DFS-nnn]`, with no separate confirmation prompt for that specific commit. Running `/tickets done DFS-nnn` is itself the user asking; this follows the same precedent `/learn apply`'s own `docs: apply L-n` commit already sets for a mode that is itself an explicit user command.

**Decided (2026-09-21): `start` keeps today's plain in-place behavior when no worktree is involved, and only creates-or-enters a worktree when one already exists for the ticket or is requested.** The worktree is named `worktree-dfs-nnn-<slug>` on a new branch `dfs-nnn-<slug>`, sibling to the repo checkout, matching CCW-003's own naming (`worktree-dfs-010-story-gate`, branch `dfs-010-story-gate-tools`). The `in-progress` edit is made inside that worktree, not the tree `start` was invoked from — the exact step CCW-003 got wrong. The plain case (a ticket worked directly on `main`) is unchanged, satisfying the ticket's own constraint that it must keep working.

**Decided (2026-09-21): CCW-008 lands first; CCW-005 (`/story run`), when it is refined, adds `depends_on: [CCW-008]` and calls the same skill modes rather than reinventing worktree handling.** CCW-005 isn't refined yet (design-only per `docs/design/story-orchestration.md`), while CCW-008 fixes a bug that is live today, independent of the pipeline.

**Decided (2026-09-21): `archetype: none`, verified by a walkthrough of every scenario on a scratch worktree/branch, evidence noted in this ticket (CCW-007's precedent), not a read-through of the skill text alone.** `pipeline-stage` (the only existing archetype) is a Java-code template set and does not fit an instructions-only change. This ticket adds real git operations with real failure modes (`git branch -d`'s refusal, a dirty worktree, a squash-merged branch) that only running them can actually exercise — reading the words alone is exactly the weaker check `L-009` was written to close.

## Acceptance criteria

The full ID of each criterion is `CCW-008.AC-<n>`.

- **AC-1** Before doing anything else, `done` checks whether the ticket's branch is on `main`: `git merge-base --is-ancestor <branch> main`, falling back to a grep of `main`'s log for the ticket ID if the ancestor check fails.
- **AC-2** If neither check in AC-1 passes, `done` refuses, changes nothing (no commit, no folder move, no worktree/branch removal), and reports which check failed and that the user must merge or rebase and re-run `done`.
- **AC-3** `done` never creates a commit on `main` other than its own close commit (AC-7), never fast-forwards `main`, and never rebases the ticket's branch.
- **AC-4** Once the check in AC-1 passes, `done` asks for one confirmation, then removes the ticket's worktree and deletes its branch with `git branch -d`.
- **AC-5** `done` refuses the worktree/branch removal (while still completing the rest of the close) if the worktree has uncommitted changes, or if `git branch -d` itself refuses because the branch has commits not reachable from `main`.
- **AC-6** `done`'s cleanup never removes rebase backup refs (e.g. `refs/original/*`).
- **AC-7** `done` commits the ticket-folder move and frontmatter update on `main` itself, files staged by name, subject `docs(tickets): close DFS-nnn [DFS-nnn]`, without a separate confirmation prompt for that commit.
- **AC-8** `start`, run with no worktree existing or requested for the ticket, edits status in place in whatever tree it was invoked from, exactly as before this ticket.
- **AC-9** `start`, run when a worktree for the ticket already exists or one is requested, creates-or-enters `worktree-dfs-nnn-<slug>` (branch `dfs-nnn-<slug>`, sibling to the repo checkout) and makes the `in-progress` edit inside that worktree, not the tree `start` was invoked from.
- **AC-10** `cancel` follows the same verification (AC-1, AC-2), cleanup (AC-4 to AC-6) and commit (AC-7) rules as `done`, since it shares `done`'s logic.
- **AC-11** `review` operates on whatever tree and branch it is invoked in; it does not create, enter, or remove a worktree.

## Scenarios

```gherkin
Feature: Worktree-aware tickets lifecycle

  Background:
    Given a ticket DFS-nnn worked on branch dfs-nnn-slug in worktree worktree-dfs-nnn-slug

  @AC-1
  Scenario: A fast-forwarded branch is recognized as landed
    Given the branch dfs-nnn-slug is an ancestor of main
    When "/tickets done DFS-nnn" runs
    Then the ancestor check passes and done proceeds without needing the squash fallback

  @AC-1
  Scenario: A squash-merged branch is recognized as landed
    Given the branch dfs-nnn-slug is not an ancestor of main
    And a commit on main contains "[DFS-nnn]" in its subject
    When "/tickets done DFS-nnn" runs
    Then the ancestor check fails, the fallback grep finds the commit, and done proceeds

  @AC-2
  Scenario: main moved and the branch has not landed
    Given the branch dfs-nnn-slug is not an ancestor of main
    And no commit on main mentions "[DFS-nnn]"
    When "/tickets done DFS-nnn" runs
    Then done refuses, reports that neither check passed, and tells the user to merge or rebase and re-run done
    And the ticket file, main, the worktree and the branch are all unchanged

  @AC-3
  Scenario: done never merges on the user's behalf
    Given the branch dfs-nnn-slug is not an ancestor of main
    When "/tickets done DFS-nnn" runs
    Then done does not fast-forward main, create a merge commit, or rebase the branch

  @AC-4
  Scenario: Cleanup after a clean landing
    Given the branch dfs-nnn-slug is an ancestor of main
    And the worktree worktree-dfs-nnn-slug has no uncommitted changes
    When "/tickets done DFS-nnn" runs and the user confirms cleanup
    Then the worktree is removed and the branch dfs-nnn-slug is deleted with "git branch -d"

  @AC-5
  Scenario: Cleanup refuses on a dirty worktree
    Given the branch dfs-nnn-slug is an ancestor of main
    And the worktree worktree-dfs-nnn-slug has an uncommitted change
    When "/tickets done DFS-nnn" runs
    Then done still moves the ticket folder and commits the close on main
    And it refuses to remove the worktree or the branch, reporting why

  @AC-6
  Scenario: A rebase backup ref survives cleanup
    Given the branch dfs-nnn-slug is an ancestor of main
    And "refs/original/refs/heads/dfs-nnn-slug" exists from an earlier rebase
    When "/tickets done DFS-nnn" runs and cleanup proceeds
    Then "refs/original/refs/heads/dfs-nnn-slug" still exists afterwards

  @AC-7
  Scenario: The close commit lands on main without a separate prompt
    Given the branch dfs-nnn-slug is an ancestor of main
    When "/tickets done DFS-nnn" runs
    Then a commit "docs(tickets): close DFS-nnn [DFS-nnn]" is made on main with only the moved ticket file staged
    And no additional yes/no prompt is shown before that commit

  @AC-8
  Scenario: start on a ticket with no worktree involved is unchanged
    Given no worktree or branch exists for DFS-nnn
    And the command is run on main with no --worktree flag
    When "/tickets start DFS-nnn" runs
    Then the ticket's status is set to in-progress in the current checkout
    And no worktree or branch is created

  @AC-9
  Scenario: start creates the worktree when none exists
    Given no worktree or branch exists for DFS-nnn
    And the command is run with a request for a worktree
    When "/tickets start DFS-nnn" runs
    Then worktree-dfs-nnn-slug is created on a new branch dfs-nnn-slug
    And the ticket's status is set to in-progress inside that worktree, not in the tree start was invoked from

  @AC-9
  Scenario: start enters an existing worktree
    Given worktree-dfs-nnn-slug and branch dfs-nnn-slug already exist for DFS-nnn
    When "/tickets start DFS-nnn" runs again
    Then start operates inside the existing worktree rather than creating a second one

  @AC-10
  Scenario: cancel follows the same rules as done
    Given the branch dfs-nnn-slug is not an ancestor of main and no commit on main mentions "[DFS-nnn]"
    When "/tickets cancel DFS-nnn some-reason" runs
    Then cancel refuses in the same way done would, unchanged by the ticket's cancel-specific Resolution section

  @AC-11
  Scenario: review touches no worktree
    Given the ticket DFS-nnn is in-progress inside worktree-dfs-nnn-slug
    When "/tickets review DFS-nnn" runs from inside that worktree
    Then the ticket's status is set to in-review on the current branch
    And no worktree is created, entered, or removed
```

## Scope

**Touches:**
- `.claude/skills/tickets`: the `start`, `review` and `done` modes (and `cancel`, which shares `done`'s verification, cleanup and commit rules)
- `CLAUDE.md`: a short note on working a ticket in a worktree (naming convention, and that `done`'s close commit is pre-authorized by the `done` command itself)

**Out of scope:**
- The `/story` skill's own branch handling (CCW-005); CCW-005 depends on this ticket instead
- Pushing or pull requests
- Sweeping rebase backup refs (`refs/original/*`)

## Definition of done

- [ ] A walkthrough of every scenario above, on a scratch worktree/branch, shows the outcome (evidence noted in this ticket, CCW-007's precedent)
- [ ] No files changed outside the paths listed under Touches
- [ ] The worktree and branch naming convention (`worktree-dfs-nnn-<slug>`, `dfs-nnn-<slug>`) is stated in the skill text, matching CCW-003's own naming
- [ ] `CLAUDE.md` states that `done`'s close commit needs no separate confirmation, since running `/tickets done` is itself the request

## Open questions

None.
