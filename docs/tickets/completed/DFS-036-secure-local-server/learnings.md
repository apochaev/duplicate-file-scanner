# DFS-036 learnings

## Entries

### DFS-036.L-1: Worktree removal fails on Windows when test report paths exceed 260 characters

- Status: applied
- Impact: shared (touches a skill: the `tickets` skill's `done` cleanup step)
- Target: .claude/skills/tickets/SKILL.md
- Change: In `done`'s cleanup step, remove the worktree with `git -c core.longpaths=true worktree remove <path>` on Windows, and if a folder is still left after git has unregistered the worktree, remove it with `rm -rf` from Git Bash and run `git worktree prune` before deleting the branch.
- Evidence: DFS-036's close (788e880): `git worktree remove` failed with "Filename too long" on `server/build/reports/tests/...` paths of 263+ characters produced by the parameterized security tests' display names; git had already unregistered the worktree, and the leftover folder was removed by hand with `rm -rf`. Every later server ticket with parameterized tests builds the same reports.
- Recurrences: 1
- Recommendation: apply; it is a one-line change to the cleanup step, and the failure otherwise recurs on every worktree that ran the server tests.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-036.L-1`. Applied with an addition the user approved: the case where git unregisters the worktree but leaves the folder (seen on DFS-036, DFS-037 and DFS-038), and a busy file held by a review server still running.
