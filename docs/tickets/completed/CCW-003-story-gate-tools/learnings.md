# CCW-003 learnings

Migrated from `docs/learnings/log.md` (2026-09-23, CCW-010) — see that file for the full migration note. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

## Entries

### CCW-003.L-1: State counts only from a mechanical count

- Status: applied
- Impact: shared (the `tickets` skill)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a ticket, note or summary states a count (scenarios, criteria, files), take it from a mechanical count such as `grep -c`, never from memory or from an earlier note.
- Evidence: refining DFS-010, the scenario count was stated as 24 and the mechanical check gave 32. Migrating DFS-001 to acceptance criteria (commit `b64bda7`), the stated 21 scenarios were 17.
- Recurrences: 2 (DFS-010 refinement; DFS-001 migration)
- Recommendation: apply. It has already happened twice, and the cost of the rule is one sentence.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-001 and L-005` (the sentence is in the `refine` mode of the skill).

### CCW-003.L-2: The tickets lifecycle is not worktree-aware

- Status: applied
- Impact: shared (the `tickets` skill: `start`, `review` and `done`; possibly a note in `CLAUDE.md`)
- Target: `.claude/skills/tickets/SKILL.md`, `start`, `review` and `done` modes
- Change: `start` creates or enters the ticket's worktree and marks `in-progress` there, so no status edit is left in the main checkout; `done` runs against `main`, verifies the ticket's work is on `main`, moves the ticket folder there, and removes the worktree and branch after confirmation.
- Evidence: DFS-010 (2026-09-21). `start` edited the main checkout before the worktree existed, and the merge would have been refused until that edit was discarded. `main` advanced during the work (`b64bda7` to `36ab0f7`), so the branch was replayed onto it (commits `bbe0841`, `2fd6948`). After the merge the worktree and two branches were still there.
- Recurrences: 1
- Recommendation: apply, as a ticket (DFS-015) rather than a one-line edit, because it changes three modes and adds a destructive step (removing worktrees and branches) that needs its own safeguards and decisions. DFS-012 (`/story`) needs the same semantics, so the two should share one design.
- Decision: applied 2026-09-23 by the user, through ticket CCW-008 (the Recommendation's "as a ticket") rather than `/learn apply`; commits `e2238ac`, `dc17636`, closed by `2f8224e`. The shipped design differs from Change in naming (`tickets/<ID>-<slug>`) and in `done` merging the branch itself (L-020), per CCW-008's revised decisions.
