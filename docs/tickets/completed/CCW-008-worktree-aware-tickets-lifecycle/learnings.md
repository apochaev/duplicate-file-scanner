# CCW-008 learnings

Migrated from `docs/learnings/log.md` (2026-09-23, CCW-010) — see that file for the full migration note. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

## Entries

### CCW-008.L-1: A refined ticket had to be reconciled mid-implementation with a same-day learning applied to the same skill

- Status: rejected
- Impact: local (no target; a watch-only observation)
- Target: none
- Change: none; watch whether a ticket that waits in the backlog while `/learn apply` changes its target skill needs re-reconciling again.
- Evidence: `docs/tickets/completed/CCW-008-worktree-aware-tickets-lifecycle/CCW-008-ticket.md`, "Revised (2026-09-22), at implementation time": L-020 (commit `7bc54cc`) changed `done` after CCW-008 was refined, so the merge behavior, naming convention and close-commit subject were re-decided, six criteria retired (AC-2, AC-3, AC-4, AC-7, AC-9, AC-10) and replaced by AC-12 to AC-17.
- Recurrences: 1 (CCW-008)
- Recommendation: park. Single occurrence, corrected within the same ticket; L-020's own Recommendation had already flagged the overlap with CCW-008, so the existing safeguard worked.
- Decision: rejected 2026-09-22 by the user.

*(History: written as `L-024` on `main`. A parallel session on a diverged branch (working DFS-002) independently proposed an unrelated finding under the same number before either branch saw the other's commits; discovered only when the branches were merged (2026-09-23), and resolved by hand at the time by renumbering this entry to `L-031` — DFS-002's own colliding entry kept `L-024`. This migration (CCW-010) then moved it here as `CCW-008.L-1`, and removed the shared global numbering that made the collision possible in the first place; see DFS-002's own learnings.md for the entry it collided with, and for the entry proposing this fix.)*

### CCW-008.L-2: `/story run` checks its branch out in place instead of using a worktree

- Status: applied
- Impact: shared (`.claude/skills/story/SKILL.md`, a skill)
- Target: `.claude/skills/story/SKILL.md`, Preflight step 2
- Change: Preflight step 2 creates the story branch off `main` (not the current branch tip). It then runs `/tickets start <ID> --worktree`, so the branch lives in its own worktree `../<repo-dir>-<ID>-<slug>`, where every phase agent then works. The repo checkout stays on `main`, as `CLAUDE.md`'s parallel-work convention (CCW-008) expects.
- Evidence: Preflight step 2 on `main` reads "Create it off the current branch tip if it does not exist; check it out if it does." `/story run DFS-002` left the repo checkout on `tickets/DFS-002-partial-hash-stage`. While CCW-008 was being built, no tree had `main` checked out. The first version of CCW-008's `done` refused in exactly that state, and closing CCW-008 needed the temporary close tree (`docs/tickets/completed/CCW-008-worktree-aware-tickets-lifecycle/CCW-008-ticket.md`, decision 4 and Out of scope). Branching "off the current branch tip" also means a story started from another ticket's branch inherits that branch's unmerged commits.
- Recurrences: 2 (CCW-008: see Evidence; DFS-018: `/tickets start DFS-018 --worktree` set the ticket `in-progress` without a `plan.json`, so `/story run`'s preflight would have refused it; the status edit was reverted, and the run was driven from the main checkout's session with the worktree path passed to every phase agent by hand (2026-10-06))
- Recommendation: apply, as a ticket rather than a one-line edit. The preflight, the resume protocol and the per-phase commits all assume one checkout, and agents would have to be pointed at the worktree path, which `/story run` needs to test for real (L-017).
- Decision: applied 2026-10-07 by the user, through ticket CCW-012 (story skill preflight off `main` in its own worktree, `git -C`/`--root` addressing, working-tree block; commit `f1fa795 docs: make /story run work each story in its own worktree [CCW-012]`, proven by the DFS-019 live run); commit `docs: apply CCW-008.L-2`

*(History: written as `L-025` on `main`, renumbered to `L-032` at the same merge, for the same reason as the entry above; then moved here as `CCW-008.L-2` by this migration.)*
