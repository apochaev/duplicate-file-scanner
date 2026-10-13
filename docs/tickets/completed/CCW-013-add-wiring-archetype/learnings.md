# CCW-013 learnings

## Entries

### CCW-013.L-1: A ticket's own-folder paths go stale when done moves it to completed/

- Status: parked
- Impact: shared (touches a skill: `.claude/skills/tickets/SKILL.md`)
- Target: `.claude/skills/tickets/SKILL.md` (refine, and done step 3)
- Change: When a ticket names a file in its own folder (touches, scenarios, done-items), refine writes it as "this ticket's folder/<file>" rather than a literal `docs/tickets/backlog/...` path; or done step 3 rewrites the ticket's own `backlog/` paths to `completed/` when it moves the folder.
- Evidence: CCW-013 listed `docs/tickets/backlog/CCW-013-add-wiring-archetype/dry-run.md` in touches, AC-7, a scenario and a done-item; commit 027a354 moved the folder to `completed/` and changed only `status` and `completed`, so the closed ticket names that path 4 times and it no longer exists.
- Recurrences: 1 (CCW-013)
- Recommendation: park; one occurrence and cosmetic (nothing reads a closed ticket's touches). Revisit when another ticket that keeps files in its own folder closes with stale paths.
- Decision: parked 2026-10-13 by the user
