---
id: CCW-012
title: Make /story run worktree-aware
status: done
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: []
touches: [.claude/skills/story/SKILL.md, .claude/skills/story/plan.json.tmpl, .claude/skills/tickets/SKILL.md]
blocked_reason:
created: 2026-10-06
updated: 2026-10-07
completed: 2026-10-07
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

Checked against the real components (2026-10-06): `tools/story-gate` takes `--root <dir>` and otherwise uses `git rev-parse --show-toplevel` of the current directory (`StoryGate.java`, `Git.topLevel`), so it can check a worktree from the repo checkout with no change. The phase agents' definitions (`.claude/agents/*.md`) run relative commands (`./gradlew`, `java tools/story-gate/StoryGate.java`), and a subagent's shell starts in the session's directory, so an agent is not in the worktree unless its launch prompt says so.

Decisions (refinement, 2026-10-06):

1. **Archetype `none`, proven by a live run.** This ticket changes instructions only (precedents: CCW-008, CCW-011). It is implemented by hand; its main evidence is the Definition of done's live `/story run`, which exercises the change for real.
2. **Preflight sets up the worktree itself, always off `main`.** It finds the ticket's branch and worktree with `git worktree list --porcelain` and handles each starting state: (a) no branch: `git worktree add -b tickets/<ID>-<slug> ../<repo-dir>-<ID>-<slug> main`; (b) a worktree already holds the branch and the ticket is `ready`: reuse it; (c) a worktree holds the branch and the ticket is `in-progress` with no `plan.json` (the user ran `/tickets start --worktree` first, the DFS-018 case): treat it as a fresh start, not a refusal; (d) the branch exists without a worktree: `git worktree add` without `-b`; (e) the branch is checked out in place in another checkout: refuse, naming that checkout's path and telling the user to switch it back to `main` and rerun (moving someone's checkout is too risky to do automatically). Then it runs the `tickets` skill's `start <ID>`, which, since a worktree now holds the branch, makes its `in-progress` edit inside the worktree (CCW-008's existing rule). The branch is never created off the current branch tip, so a story never inherits another ticket's unmerged commits.
3. **The orchestrating session stays in the repo checkout and addresses the worktree explicitly, never with `cd`.** Every git command runs as `git -C <worktree> ...`; every check as `java tools/story-gate/StoryGate.java <check> <ID> --root <worktree>` (the flag `story-gate` already has); `plan.json` and other story files are read and written by absolute worktree path. Each mode (`run`, `approve`, `status`, `learn`) finds the worktree with `git worktree list --porcelain` (the line `branch refs/heads/tickets/<ID>-<slug>`), the rule the `tickets` skill already uses, since `plan.json` itself lives inside the worktree and cannot be the way to find it. Preflight records the worktree path in a new `plan.json` field, `worktree`, and every later mode refuses if it disagrees with what `git worktree list` reports (a moved or recreated worktree). No step relies on how the shell treats `cd` (CCW-001.L-5); DFS-018's run used `cd` and worked only because this harness resets the directory after each call.
4. **Every launch prompt carries a fixed working-tree block.** Resume step 6 gives each phase agent, right after the story-folder and ticket lines, a block whose exact wording `SKILL.md` states: the worktree root and its branch, "read and write every file under that root, and run every git and Gradle command from that root", and "never read or edit the repo checkout", with its path. `/story learn` gives the retro the same block. This is the text DFS-018 added by hand to all six agent launches, which every agent respected. The five agent definitions are not changed: agents already take their paths from the launch prompt and never derive them (resume step 6), and the block follows that contract.
5. **`story-gate` is not changed.** Every check is run with `--root <worktree>` (decision 3), which it already supports. DFS-018's checks were run from inside the worktree root; `--root` selects the same root from the repo checkout.
6. **The `tickets` skill's two sentences about in-place `/story run` are fixed here.** In "Parallel work", "(a checkout was switched to a ticket branch in place, as `/story run` does today)" becomes "(for example, a checkout was switched to a ticket branch by hand)". In `start`'s "Otherwise" branch, the sentence "This is also the case when `/story run` has already checked out the branch in place." is deleted: `/story run`'s preflight now calls `start` with a worktree already holding the branch, so `start` takes its first branch. Left alone, the two skills would contradict each other (CCW-005.L-4).
7. **Proven by a live run up to the design gate.** The next product ticket to reach `ready` (probably DFS-019) is run with the new `/story run` from the repo checkout; preflight, the design phase, the `touches` check with `--root`, the `git -C` commit and the stop at the design gate exercise every new rule once. CCW-012 closes on that evidence; the story then continues normally, and a gap in a later phase is caught by its retro. CCW-012 therefore waits in `in-review` until a product ticket is `ready` and has reached its design gate.

## Acceptance criteria

The full ID of each criterion is `CCW-012.AC-<n>`.

- **AC-1** With no branch for the ticket, preflight creates the worktree `../<repo-dir>-<ID>-<slug>` on a new branch `tickets/<ID>-<slug>` created off `main`, and the repo checkout stays on `main` (decision 2, state a).
- **AC-2** `SKILL.md`'s preflight handles states (b) to (e) as decision 2 states: reuse a worktree holding the branch when the ticket is `ready`; treat `in-progress` with no `plan.json` as a fresh start; `git worktree add` without `-b` when the branch has no worktree; refuse, naming the checkout's path, when the branch is checked out in place.
- **AC-3** The branch is created off `main`, never off the current branch tip: the new branch's first parent is `main`'s tip at preflight time (decision 2).
- **AC-4** Preflight's `tickets start` makes the `in-progress` edit inside the worktree, not in the repo checkout (decision 2).
- **AC-5** `plan.json.tmpl` has a `worktree` field, and preflight writes the worktree's path into it; `SKILL.md` tells every later mode to refuse when it disagrees with `git worktree list --porcelain` (decision 3).
- **AC-6** `SKILL.md` runs every git command against the story as `git -C <worktree>` and every `story-gate` check with `--root <worktree>`, and never tells the orchestrator to `cd` (decision 3).
- **AC-7** `SKILL.md`'s `run`, `approve`, `status` and `learn` modes find the worktree with `git worktree list --porcelain` by the branch line (decision 3).
- **AC-8** `SKILL.md` gives the exact wording of a working-tree block that resume step 6 puts in every phase agent's launch prompt, and that `/story learn` puts in the retro's (decision 4).
- **AC-9** The `tickets` skill no longer says `/story run` checks branches out in place: the "Parallel work" sentence reads "(for example, a checkout was switched to a ticket branch by hand)", and `start`'s "This is also the case when `/story run` has already checked out the branch in place." is gone (decision 6).
- **AC-10** A live `/story run` of the next ready product ticket, started from the repo checkout on `main`, reaches the design gate with the design commit on the ticket branch in its worktree, the architect's launch prompt carrying the working-tree block, `touches` run with `--root`, and the repo checkout still on `main` and clean (decision 7).

## Scenarios

Each scenario says how it is checked: **mechanical** (a command whose output decides it), **reading** (the skill text is read; not a walkthrough of a run), or **live** (observed in the decision-7 run up to the design gate).

```gherkin
Feature: /story run works each story in its own worktree

  Background:
    Given the repo checkout "duplicate-file-scanner" is on "main" and clean

  # check: live (git worktree list --porcelain, git -C <repo checkout> branch --show-current)
  @AC-1 @AC-10
  Scenario: A fresh story gets its own worktree and the repo checkout stays on main
    Given a ready product ticket <ID> with no branch "tickets/<ID>-<slug>"
    When /story run <ID> is invoked from the repo checkout
    Then "git worktree list --porcelain" lists "../duplicate-file-scanner-<ID>-<slug>" on "refs/heads/tickets/<ID>-<slug>"
    And the repo checkout's current branch is still "main"

  # check: live (git merge-base / rev-parse)
  @AC-3
  Scenario: The story branch starts at main's tip
    Given main's tip at the moment /story run <ID> is invoked
    When preflight has created the branch
    Then the branch's first commit's parent is that tip of main

  # check: live (git -C <worktree> status, git -C <repo checkout> status)
  @AC-4
  Scenario: The in-progress edit lands in the worktree
    When preflight has run tickets start <ID>
    Then the ticket file in the worktree has "status: in-progress"
    And the repo checkout's copy of the ticket still has "status: ready" and the repo checkout has no changes

  # check: mechanical (grep plan.json.tmpl); live (compare plan.json's worktree with git worktree list)
  @AC-5
  Scenario: plan.json records the worktree
    When preflight has written plan.json
    Then ".claude/skills/story/plan.json.tmpl" contains a "worktree" field
    And plan.json's "worktree" equals the worktree path that "git worktree list --porcelain" gives for the branch

  # check: reading
  @AC-5
  Scenario: A later mode refuses a worktree that does not match plan.json
    When SKILL.md's run, approve, status and learn modes are read
    Then each says to refuse when plan.json's "worktree" disagrees with "git worktree list --porcelain"

  # check: reading
  @AC-2
  Scenario Outline: Preflight handles every starting state
    When SKILL.md's preflight is read for state <state>
    Then it says <action>

    Examples:
      | state                                                          | action                                                                |
      | a worktree holds the branch and the ticket is ready            | reuse the worktree                                                    |
      | a worktree holds the branch, in-progress, no plan.json         | treat it as a fresh start, not a refusal                              |
      | the branch exists without a worktree                           | git worktree add without -b                                           |
      | the branch is checked out in place in another checkout         | refuse, naming that checkout's path, and say to switch it to main     |

  # check: mechanical (grep SKILL.md: every "git " command line against the story uses "-C", every StoryGate call has "--root", no "cd " instruction)
  @AC-6
  Scenario: The orchestrator addresses the worktree without cd
    When ".claude/skills/story/SKILL.md" is searched
    Then every git command it prescribes against the story uses "git -C <worktree>"
    And every "StoryGate.java" invocation it prescribes carries "--root <worktree>"
    And it never tells the orchestrator to "cd" into the worktree

  # check: reading
  @AC-7
  Scenario: Every mode finds the worktree the same way
    When SKILL.md's run, approve, status and learn modes are read
    Then each finds the worktree with "git worktree list --porcelain" by the line "branch refs/heads/tickets/<ID>-<slug>"

  # check: reading (SKILL.md); live (the architect's launch prompt in the decision-7 run)
  @AC-8 @AC-10
  Scenario: Every agent is told its working tree
    When SKILL.md's resume step 6 and learn mode are read
    Then both give the same exact working-tree block, naming the worktree root, its branch, "run every git and Gradle command from that root" and the repo checkout not to touch
    And the architect's launch prompt in the live run carries that block

  # check: mechanical (grep the tickets skill)
  @AC-9
  Scenario: The tickets skill no longer describes an in-place /story run
    When ".claude/skills/tickets/SKILL.md" is searched for "as `/story run` does today" and "has already checked out the branch in place"
    Then neither is found
    And the "Parallel work" paragraph contains "(for example, a checkout was switched to a ticket branch by hand)"

  # check: live (story-gate output, git log in the worktree, git status in both trees)
  @AC-10
  Scenario: The live run reaches the design gate cleanly
    Given the next product ticket to reach ready
    When /story run <ID> is invoked from the repo checkout and the architect returns
    Then "StoryGate.java touches <ID> --root <worktree>" passes
    And the design commit is on "tickets/<ID>-<slug>" in the worktree
    And plan.json's state is "awaiting-approval"
    And the repo checkout is on "main" with no changes
```

## Scope

**Touches:**
- `.claude/skills/story/SKILL.md`: preflight, the resume protocol and the agent launch prompts (to be confirmed in refinement)
- `.claude/skills/tickets/SKILL.md`: two sentences that describe the in-place `/story run` (decision 6)
- `.claude/skills/story/plan.json.tmpl`: the new `worktree` field (decision 3); `ConsistencyCheck` reads only the keys it knows, so an extra field is safe

**Out of scope:**
- `/tickets start`, `review` and `done`'s own worktree handling (CCW-008), beyond the two sentences of decision 6
- Running several stories concurrently from one session

## Implementation notes (2026-10-07)

Built by hand on `tickets/CCW-012-make-story-run-worktree-aware`, in its worktree.

- `.claude/skills/story/SKILL.md`: a new "Worktree (CCW-012)" section (finding the worktree with `git worktree list --porcelain`, the `plan.json` `worktree` cross-check, `git -C` and `--root` instead of `cd`, where the story folder is, and the exact working-tree block); the `plan.json` shape example gains `worktree`; preflight rewritten as six steps with states (a) to (e), always off `main`; resume step 1 finds the worktree and refuses a mismatch; step 6 adds the block; step 7 adds `--root`; step 8 stages, commits and reads `HEAD` with `git -C`; Finishing runs `review` against the worktree's ticket; `run`, `status`, `approve` and `learn` find the worktree; the reopened-tests `green` command adds `--root`; "never does" gains the no-in-place, no-`cd` rule.
- `.claude/skills/story/plan.json.tmpl`: the `worktree` field.
- `.claude/skills/tickets/SKILL.md`: the two sentences of decision 6.
- **Checks done (2026-10-07):** AC-5 (template field), AC-6 and AC-9 mechanically, in Python: every inline `git` command in the story skill uses `git -C` or is a repo-level `git worktree`/`git branch --list` lookup; all three `StoryGate.java` invocations carry `--root <worktree>`; the four `cd` mentions are two prohibitions, the agents' working-tree block and its explanation, none telling the orchestrator to `cd`; both old tickets-skill phrases are gone and the new "Parallel work" wording is present. AC-2, AC-5 (refusal), AC-7 and AC-8 by reading: preflight lines for states (b) to (e); `run`, `approve`, `status` and `learn` each find the worktree and refuse on a mismatch; the block is stated once and required by resume step 6 and `learn`.
- **Live run (decision 7), 2026-10-07: DFS-019** ("Wire ScannerFactories", archetype `use-case`), `/story run DFS-019` invoked from the repo checkout on `main` at `8cc78bf`.
  - AC-1: preflight found no branch (state a) and ran `git worktree add -b tickets/DFS-019-wire-scanner-factories ../duplicate-file-scanner-DFS-019-wire-scanner-factories main`; `git worktree list --porcelain` listed `C:/Users/apoch/Projects/us.pochaev/dfs/duplicate-file-scanner-DFS-019-wire-scanner-factories` on `refs/heads/tickets/DFS-019-wire-scanner-factories`, and the repo checkout stayed on `main`.
  - AC-3: the new branch's HEAD was `8cc78bf`, `main`'s tip at preflight; the design commit's parent is `8cc78bf`.
  - AC-4: after `tickets start`, the worktree's ticket read `status: in-progress`; the repo checkout's copy still read `status: ready`, and `git status` there was empty.
  - AC-5 (live half): `plan.json`'s `worktree` equalled the path `git worktree list --porcelain` printed for the branch.
  - AC-10: the architect's launch prompt carried the working-tree block verbatim; `StoryGate.java touches DFS-019 --root <worktree>` passed (7 files); the design commit `a728d62` was made with `git -C <worktree>` on the ticket branch; `plan.json` state was `awaiting-approval`; the repo checkout was on `main` with no changes.
  - Beyond the design gate (not required by decision 7): the same rules carried the story through tests (one red-gate defect, a test fault unrelated to worktrees), implement, verify, `/story learn` and `/tickets done DFS-019`, which fast-forwarded `main` to `bc514d7` and closed it as `4e4b739`. No worktree gap was found, so no follow-up commit was needed.
- **Sequencing deviation from decision 7 (2026-10-07, the user's choice):** skills load from the repo checkout on `main`, so the live run can only exercise the new `/story run` once this branch is on `main`. The branch is therefore merged into `main` before the live run, with the ticket `in-review` and its live done-items still open; the live run's evidence is recorded on `main` afterwards, and `/tickets done CCW-012` then finds the branch already landed (its ancestor check) and only closes. A gap the live run finds is fixed by a follow-up commit on `main` tagged `[CCW-012]`.

## Definition of done

- [x] Every scenario above is checked the way it says (mechanical, reading or live) and passes, with the evidence noted beside this item: mechanical and reading checks above (Checks done); the live scenarios in the DFS-019 live run above
- [x] The live run of decision 7 reached the design gate of a real product ticket, started from the repo checkout on `main`; its ticket ID, worktree path and design commit are noted here: DFS-019, worktree `../duplicate-file-scanner-DFS-019-wire-scanner-factories`, design commit `a728d62`
- [x] CCW-008.L-2 is marked `applied` with a pointer to this ticket (via `/learn`): done 2026-10-07
- [x] No files changed outside the paths listed under Touches: `git show --stat f1fa795` lists the three touched files plus this ticket

## Open questions

None.
