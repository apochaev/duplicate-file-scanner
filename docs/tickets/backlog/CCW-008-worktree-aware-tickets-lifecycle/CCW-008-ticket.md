---
id: CCW-008
title: Make the tickets lifecycle worktree-aware
status: in-review
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

**Superseded 2026-09-22 (see "Revised" below): `done` only verifies; it never merges, fast-forwards, or rebases anything itself.** If neither check above passes, it refuses, reports which check failed, and tells the user to merge or rebase and re-run `done`. This keeps CLAUDE.md's "commits only when the user asks" rule intact without a carve-out — a fast-forward moves `main`'s ref, which is the kind of state change that rule protects — and it means "`main` moved during the work" (the ticket's open question 5) is the same situation as "who merges" (question 2), not a separate case: `done` refuses and says so either way.

**Decided (2026-09-21): cleanup removes the worktree and deletes the branch with `git branch -d` (the non-force, safe delete), after refusing on a dirty worktree or on a branch `git branch -d` itself refuses (unmerged commits), and one confirmation before deleting either.** Rebase backup refs (e.g. `refs/original/*`) are left untouched — sweeping them isn't this ticket's job, and doing so would remove the user's own recovery path for the rebase itself.

**Decided (2026-09-21), subject superseded 2026-09-22 (see "Revised" below): `done` commits the ticket-folder move and frontmatter update on `main` itself**, files staged by name, subject `docs(tickets): close DFS-nnn [DFS-nnn]`, with no separate confirmation prompt for that specific commit. Running `/tickets done DFS-nnn` is itself the user asking; this follows the same precedent `/learn apply`'s own `docs: apply L-n` commit already sets for a mode that is itself an explicit user command.

**Decided (2026-09-21), naming superseded 2026-09-22 (see "Revised" below): `start` keeps today's plain in-place behavior when no worktree is involved, and only creates-or-enters a worktree when one already exists for the ticket or is requested.** The worktree is named `worktree-dfs-nnn-<slug>` on a new branch `dfs-nnn-<slug>`, sibling to the repo checkout, matching CCW-003's own naming (`worktree-dfs-010-story-gate`, branch `dfs-010-story-gate-tools`). The `in-progress` edit is made inside that worktree, not the tree `start` was invoked from — the exact step CCW-003 got wrong. The plain case (a ticket worked directly on `main`) is unchanged, satisfying the ticket's own constraint that it must keep working.

**Decided (2026-09-21): CCW-008 lands first; CCW-005 (`/story run`), when it is refined, adds `depends_on: [CCW-008]` and calls the same skill modes rather than reinventing worktree handling.** CCW-005 isn't refined yet (design-only per `docs/design/story-orchestration.md`), while CCW-008 fixes a bug that is live today, independent of the pipeline.

**Decided (2026-09-21): `archetype: none`, verified by a walkthrough of every scenario on a scratch worktree/branch, evidence noted in this ticket (CCW-007's precedent), not a read-through of the skill text alone.** `pipeline-stage` (the only existing archetype) is a Java-code template set and does not fit an instructions-only change. This ticket adds real git operations with real failure modes (`git branch -d`'s refusal, a dirty worktree, a squash-merged branch) that only running them can actually exercise — reading the words alone is exactly the weaker check `L-009` was written to close.

**Revised (2026-09-22), at implementation time.** L-020 (applied 2026-09-22, commit `7bc54cc`, after this ticket was refined) had already changed `done` in the skill to merge the `tickets/<ID>-<slug>` branch itself and commit its own close as `docs: close <ID> [<ID>]`. Three decisions were re-taken with the maintainer to reconcile this ticket with it:

1. **`done` keeps L-020's merge.** The AC-1 checks decide whether the branch has already landed (then no merge); otherwise `done` merges it into `main` (fast-forward if possible, else an ordinary merge commit), stops on a conflict with nothing closed or cleaned up, and never rebases or force-resolves. AC-2 and AC-3 are retired, replaced by AC-12 and AC-13. AC-4 and AC-10 are restated as AC-14 and AC-17 to fit. `cancel` never merges (cancelled work must not land on `main`); its `git branch -d` then refuses an unmerged branch and that refusal is reported.
2. **One naming convention:** branch `tickets/<ID>-<slug>` (the one `/story run` and L-020 already use), worktree `../<repo-dir>-<ID>-<slug>` beside the checkout. AC-9 is retired, replaced by AC-16.
3. **Close-commit subject stays `docs: close <ID> [<ID>]`** (the skill's and history's convention; `tickets` is not a CLAUDE.md scope). AC-7 is retired, replaced by AC-15.

Two details settled in implementation, no new decision: `done` finds the tree with `main` checked out and works there with `git -C` (refusing if none has), so it also works when invoked from the ticket's worktree; and the squash fallback greps only `<branch>..main`, so ticket commits made on `main` before the branch forked (refinement commits) cannot count as a landing. A ticket with no `tickets/<ID>-<slug>` branch closes in place as before (AC-18).

4. **Parallel work (decided 2026-09-22, after the first review).** The point of this ticket is to let several tickets run and land in parallel, and the first version still blocked it: `done` refused when no tree had `main` checked out (the state the repo was in while this ticket was built, with the checkout on DFS-002's branch), and `list` offered a ticket already started in another worktree, because `start`'s uncommitted edit leaves `main`'s copy `ready`. Chosen: both a convention and a fallback. The convention: keep the repo checkout on `main`, one worktree per ticket. The fallback: `done` opens a temporary close tree `../<repo-dir>-close-<ID>` on `main` when no tree has it, reuses one left by a conflict or interruption, and removes it at the end (AC-19). `list` shows a ticket whose branch exists under "Started elsewhere" (AC-20). A shared `main` checkout with another session's uncommitted files is used as is, with only the ticket file staged (AC-21). `main`'s ref is never moved from outside the tree that has it checked out.

## Acceptance criteria

The full ID of each criterion is `CCW-008.AC-<n>`.

- **AC-1** Before doing anything else, `done` checks whether the ticket's branch is on `main`: `git merge-base --is-ancestor <branch> main`, falling back to a grep of `main`'s log for the ticket ID if the ancestor check fails.
- **AC-2 (retired)** If neither check in AC-1 passes, `done` refuses, changes nothing (no commit, no folder move, no worktree/branch removal), and reports which check failed and that the user must merge or rebase and re-run `done`. Retired 2026-09-22: superseded by L-020's merge; see AC-12.
- **AC-3 (retired)** `done` never creates a commit on `main` other than its own close commit (AC-7), never fast-forwards `main`, and never rebases the ticket's branch. Retired 2026-09-22: superseded by L-020's merge; see AC-13.
- **AC-4 (retired)** Once the check in AC-1 passes, `done` asks for one confirmation, then removes the ticket's worktree and deletes its branch with `git branch -d`. Retired 2026-09-22: the branch may now land by `done`'s own merge, not only AC-1; restated as AC-14.
- **AC-5** `done` refuses the worktree/branch removal (while still completing the rest of the close) if the worktree has uncommitted changes, or if `git branch -d` itself refuses because the branch has commits not reachable from `main`.
- **AC-6** `done`'s cleanup never removes rebase backup refs (e.g. `refs/original/*`).
- **AC-7 (retired)** `done` commits the ticket-folder move and frontmatter update on `main` itself, files staged by name, subject `docs(tickets): close DFS-nnn [DFS-nnn]`, without a separate confirmation prompt for that commit. Retired 2026-09-22: subject kept as `docs: close <ID> [<ID>]`; restated as AC-15.
- **AC-8** `start`, run with no worktree existing or requested for the ticket, edits status in place in whatever tree it was invoked from, exactly as before this ticket.
- **AC-9 (retired)** `start`, run when a worktree for the ticket already exists or one is requested, creates-or-enters `worktree-dfs-nnn-<slug>` (branch `dfs-nnn-<slug>`, sibling to the repo checkout) and makes the `in-progress` edit inside that worktree, not the tree `start` was invoked from. Retired 2026-09-22: naming changed to `tickets/<ID>-<slug>`; restated as AC-16.
- **AC-10 (retired)** `cancel` follows the same verification (AC-1, AC-2), cleanup (AC-4 to AC-6) and commit (AC-7) rules as `done`, since it shares `done`'s logic. Retired 2026-09-22: `cancel` must not merge; restated as AC-17.
- **AC-11** `review` operates on whatever tree and branch it is invoked in; it does not create, enter, or remove a worktree.
- **AC-12** If neither check in AC-1 passes, `done` merges `tickets/<ID>-<slug>` into `main` (fast-forward when possible, otherwise an ordinary merge commit). On a conflict it stops with the merge unresolved, makes no close commit, moves no folder and removes nothing, and tells the user to resolve, commit and re-run `done`.
- **AC-13** `done` never rebases the ticket's branch, never force-resolves a conflict, and makes no commit on `main` other than AC-12's merge and AC-15's close commit.
- **AC-14** Once the branch is on `main` (by AC-1 or AC-12), `done` asks for one confirmation, then removes the ticket's worktree with `git worktree remove` (never `--force`) and deletes its branch with `git branch -d` (never `-D`).
- **AC-15** `done` commits the ticket-folder move and frontmatter update on `main` itself, the ticket file staged by name and checked with `git diff --cached`, subject `docs: close <ID> [<ID>]`, without a separate confirmation prompt for that commit.
- **AC-16** `start`, run when a worktree for the ticket already exists or one is requested (`--worktree`), creates-or-enters `../<repo-dir>-<ID>-<slug>` on branch `tickets/<ID>-<slug>` (a new branch off `main` when none exists) and makes the `in-progress` edit inside that worktree, not the tree `start` was invoked from.
- **AC-17** `cancel` follows `done`'s landing check (AC-1), cleanup (AC-5, AC-6, AC-14) and commit (AC-15) rules, except it never merges the branch; an unmerged branch then survives cleanup through `git branch -d`'s own refusal, which is reported.
- **AC-18** `done` on a ticket with no `tickets/<ID>-<slug>` branch closes in place in the tree it was invoked from, with no landing check, merge or cleanup, as before this ticket.
- **AC-19** When no tree has `main` checked out, `done` (and `cancel`) opens a temporary close tree `../<repo-dir>-close-<ID>` on `main`, does its merge and close there, and removes it at the end without asking; after a merge conflict it leaves the close tree for the user to resolve in, and a re-run reuses it (stopping first if it still has an unresolved merge or other uncommitted changes).
- **AC-20** `list` leaves out of "Dispatchable now" any ticket whose branch `tickets/<ID>-<slug>` exists, showing it under "Started elsewhere" with its worktree path (or "branch only").
- **AC-21** When `main` is checked out in a shared tree holding another session's uncommitted changes, `done` merges and closes there without staging, committing or discarding those changes.

## Scenarios

```gherkin
Feature: Worktree-aware tickets lifecycle

  Background:
    Given a ticket XYZ-001 with slug "demo" worked on branch tickets/XYZ-001-demo in worktree ../repo-XYZ-001-demo
    And main is checked out in the repo checkout

  @AC-1 @AC-14
  Scenario: A fast-forwarded branch is recognized as landed
    Given the branch tickets/XYZ-001-demo is an ancestor of main
    When "/tickets done XYZ-001" runs and the user confirms cleanup
    Then the ancestor check passes, no merge is made, and done proceeds
    And the worktree is removed and the branch is deleted with "git branch -d"

  @AC-1 @AC-5
  Scenario: A squash-merged branch is recognized as landed
    Given the branch tickets/XYZ-001-demo is not an ancestor of main
    And a squash commit on main, made after the branch forked, contains "[XYZ-001]" in its subject
    When "/tickets done XYZ-001" runs and the user confirms cleanup
    Then the ancestor check fails, the fallback grep finds the squash commit, and done closes without merging
    And "git branch -d" refuses the branch (its commits are not reachable from main), which is reported and the branch is kept

  @AC-1 @AC-12
  Scenario: A refinement commit on main does not count as a landing
    Given a commit "docs(tickets): refine XYZ-001 [XYZ-001]" on main made before the branch forked
    And the branch tickets/XYZ-001-demo has unmerged commits
    When "/tickets done XYZ-001" runs
    Then the fallback grep over tickets/XYZ-001-demo..main finds nothing, and done merges the branch

  @AC-12 @AC-13
  Scenario: main moved and the branch has not landed
    Given main has a commit the branch lacks, and the branch has a commit main lacks, with no conflict between them
    When "/tickets done XYZ-001" runs
    Then done makes an ordinary merge commit on main, then the close commit, and nothing else
    And the branch's own commits are unchanged (not rebased)

  @AC-12 @AC-13
  Scenario: A merge conflict stops done
    Given the branch and main change the same line of the same file differently
    When "/tickets done XYZ-001" runs
    Then done stops with the merge unresolved, makes no close commit, moves no folder, and removes neither worktree nor branch
    And it tells the user to resolve, commit and re-run done

  @AC-5
  Scenario: Cleanup refuses on a dirty worktree
    Given the branch tickets/XYZ-001-demo is an ancestor of main
    And the worktree ../repo-XYZ-001-demo has an uncommitted change
    When "/tickets done XYZ-001" runs
    Then done still moves the ticket folder and commits the close on main
    And it refuses to remove the worktree or the branch, reporting why

  @AC-6 @AC-14
  Scenario: A rebase backup ref survives cleanup
    Given the branch tickets/XYZ-001-demo is an ancestor of main
    And "refs/original/refs/heads/tickets/XYZ-001-demo" exists from an earlier rewrite
    When "/tickets done XYZ-001" runs and cleanup proceeds
    Then "refs/original/refs/heads/tickets/XYZ-001-demo" still exists afterwards

  @AC-15
  Scenario: The close commit lands on main without a separate prompt
    Given the branch tickets/XYZ-001-demo is an ancestor of main
    When "/tickets done XYZ-001" runs
    Then a commit "docs: close XYZ-001 [XYZ-001]" is made on main with only the moved ticket file staged
    And no additional yes/no prompt is shown before that commit

  @AC-18
  Scenario: done on a ticket worked directly on main is unchanged
    Given no branch tickets/XYZ-002-plain exists for ticket XYZ-002
    When "/tickets done XYZ-002" runs on main
    Then the ticket is closed and committed in place, with no landing check, merge or cleanup

  @AC-8
  Scenario: start on a ticket with no worktree involved is unchanged
    Given no worktree or branch exists for XYZ-001
    And the command is run on main with no --worktree flag
    When "/tickets start XYZ-001" runs
    Then the ticket's status is set to in-progress in the current checkout
    And no worktree or branch is created

  @AC-16
  Scenario: start creates the worktree when none exists
    Given no worktree or branch exists for XYZ-001
    When "/tickets start XYZ-001 --worktree" runs from the repo checkout
    Then ../repo-XYZ-001-demo is created on a new branch tickets/XYZ-001-demo off main
    And the ticket's status is set to in-progress inside that worktree, and the repo checkout's copy is unchanged

  @AC-16
  Scenario: start enters an existing worktree
    Given ../repo-XYZ-001-demo and branch tickets/XYZ-001-demo already exist for XYZ-001
    When "/tickets start XYZ-001" runs again from the repo checkout
    Then start edits inside the existing worktree rather than creating a second one

  @AC-17
  Scenario: cancel never merges
    Given the branch tickets/XYZ-001-demo is not an ancestor of main and no commit on main after the fork mentions "[XYZ-001]"
    When "/tickets cancel XYZ-001 some-reason" runs and the user confirms cleanup
    Then no merge is made, the close is committed on main as cancelled, the worktree is removed
    And "git branch -d" refuses the unmerged branch, which is reported and kept

  @AC-19
  Scenario: No tree has main checked out
    Given the repo checkout is on branch other-ticket and nothing has main checked out
    When "/tickets done XYZ-001" runs
    Then done opens ../repo-close-XYZ-001 on main, merges and commits the close there, and removes it at the end
    And the repo checkout is still on other-ticket

  @AC-19
  Scenario: A conflict in the close tree is resolved there and the re-run reuses it
    Given nothing has main checked out and the branch conflicts with main
    When "/tickets done XYZ-001" runs
    Then done stops with the conflict unresolved inside ../repo-close-XYZ-001, which is kept
    And a re-run before resolving stops at step 1
    And after the user commits the resolution there, a re-run reuses the close tree, closes, and removes it

  @AC-20
  Scenario: list hides a ticket started in another worktree
    Given "/tickets start XYZ-001 --worktree" ran, so main's copy of XYZ-001 still says ready
    When "/tickets list" runs
    Then XYZ-001 is shown under "Started elsewhere" with its worktree, not under "Dispatchable now"

  @AC-21
  Scenario: A shared main checkout keeps another session's uncommitted work
    Given main's checkout has an unrelated modified file and an untracked file from another session
    When "/tickets done XYZ-001" runs
    Then the merge and close commit land on main with only the ticket file staged
    And the other session's modified and untracked files are still there, uncommitted

  @AC-11
  Scenario: review touches no worktree
    Given the ticket XYZ-001 is in-progress inside ../repo-XYZ-001-demo
    When "/tickets review XYZ-001" runs from inside that worktree
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
- Making `/story run` work in a worktree instead of checking its branch out in place (it lives in the `story` skill; a follow-up for CCW-005's owner, so that the parallel-work convention also holds for pipeline tickets)

## Definition of done

- [x] A walkthrough of every scenario above, on a scratch worktree/branch, shows the outcome (evidence noted in this ticket, CCW-007's precedent). Evidence: see Walkthrough below.
- [x] No files changed outside the paths listed under Touches. Evidence: `git status` on the branch lists only `.claude/skills/tickets/SKILL.md`, `CLAUDE.md` and this ticket's own folder (ticket, `walkthrough.sh`, `walkthrough.log`).
- [x] The worktree and branch naming convention (`../<repo-dir>-<ID>-<slug>`, `tickets/<ID>-<slug>`) is stated in the skill text, matching `/story run`'s branch name (revised 2026-09-22, decision 2). Evidence: SKILL.md, section "Branches and worktrees".
- [x] `CLAUDE.md` and the skill state the parallel-work convention (repo checkout on `main`, one worktree per ticket) and the close-tree fallback. Evidence: SKILL.md "Branches and worktrees", "Parallel work" paragraph; CLAUDE.md, same paragraph as the next item.
- [x] `CLAUDE.md` states that `done`'s close commit needs no separate confirmation, since running `/tickets done` is itself the request. Evidence: the paragraph after the Tickets section's first paragraph.

## Walkthrough (2026-09-22)

`walkthrough.sh` in this folder runs the git steps of the new `start`, `review`, `done` and `cancel` text literally, against a throwaway repo it creates and deletes (ticket `XYZ-001`, slug `demo`, plus `XYZ-002` worked on `main`). One block per scenario (S14 to S17 added with decision 4); the full output is `walkthrough.log`. Re-run with `bash walkthrough.sh`.

Limits, stated plainly: the script is a transcription of the skill's steps into shell, not the skill run by an agent. Two things are agent behavior that no script can exercise and were checked by reading only: that the cleanup confirmation is asked exactly **once** (the script takes it as a parameter; S4 exercises "declined"), and that **no** yes/no prompt precedes the close commit (AC-15).

| Block | Tags | Observed |
|---|---|---|
| S1 | AC-1, AC-14, AC-15, AC-16 | `start --worktree` created `repo-XYZ-001-demo` on `tickets/XYZ-001-demo`; repo checkout's copy stayed `ready`, worktree's became `in-progress`. After a fast-forward: ancestor check passed, no merge; close commit `docs: close XYZ-001 [XYZ-001]` staged only the ticket file (staged == working tree); worktree removed, branch deleted with `-d` |
| S2 | AC-1, AC-5 | Squash-merged: ancestor check failed, `<branch>..main` grep found the squash commit, no merge; close committed; worktree removed; `git branch -d` refused ("not fully merged"), branch kept and reported |
| S3 | AC-1, AC-12 | An unranged grep would have hit the pre-fork `refine XYZ-001 [XYZ-001]` commit; the ranged grep found nothing, so `done` merged (fast-forward) |
| S4 | AC-12, AC-13 | `main` moved without conflict: ordinary merge commit (two parents), then the close commit, and nothing else added to `main`; branch tip unchanged (not rebased); cleanup declined, both kept |
| S5 | AC-12, AC-13 | Conflict on `a.txt`: stopped with exit 2, conflict left unresolved, ticket still in `backlog/`, no close commit, worktree and branch intact |
| S6 | AC-5 | Untracked file in the worktree: close committed, whole cleanup refused listing `?? dirty.txt`, worktree and branch kept |
| S7 | AC-6, AC-14 | `refs/original/refs/heads/tickets/XYZ-001-demo` still present after worktree removal and branch deletion |
| S8 | AC-18 | `XYZ-002` with no branch: closed and committed in place, no landing check, merge or cleanup |
| S9 | AC-8 | `start` without `--worktree` on `main`: `in-progress` in place, still one worktree, only branch `main` |
| S10 | AC-16 | Second `start` entered the existing worktree (edit landed there, repo copy untouched), still two worktrees |
| S11 | AC-17 | `cancel` on an unmerged branch: "never merges", close committed as `cancelled` with a Resolution section, worktree removed, `git branch -d` refused and branch kept |
| S12 | AC-11 | `review` inside the worktree: `in-review` on `tickets/XYZ-001-demo`, repo copy untouched, worktree count unchanged |
| S13 | (step 1) | `done` invoked from inside the ticket's worktree: found `main`'s tree, merged, closed there, and removed the worktree it was invoked from (Windows, Git Bash) |
| S14 | AC-19 | Repo checkout on `other-ticket`, nothing on `main`: `done` opened `repo-close-XYZ-001`, merged and closed there, cleaned up, removed the close tree; checkout still on `other-ticket` |
| S15 | AC-19 | Conflict with nothing on `main`: stopped (exit 2) with the close tree kept (three worktrees); re-run before resolving stopped at step 1 (exit 3); after the resolution was committed in the close tree, the re-run reused it, found the branch landed, closed, and removed it |
| S16 | AC-20 | Before `start`, both tickets dispatchable; after `start --worktree` (`main`'s copy still `ready`), XYZ-001 listed as "started elsewhere: in repo-XYZ-001-demo" |
| S17 | AC-21 | `main`'s checkout had ` M a.txt` and `?? other-session.txt` from "another session": merge and close landed staging only the ticket file; both files still uncommitted afterwards |

## Open questions

None.
