# CCW-018 learnings

## Entries

### CCW-018.L-1: Instruction edits that change a count miss other mentions of it

- Status: applied
- Impact: shared (touches a skill: `.claude/skills/tickets/SKILL.md`, `refine` and "Implementing a ticket")
- Target: .claude/skills/tickets/SKILL.md
- Change: when a ticket that edits instructions changes a count, a name or a list (for example "two launch points"), search the skills, agents and CLAUDE.md for every other mention of the old form, including spelled-out words like "two", "both", "either", and edit or list each one; record the search and its hits in `verification.md`.
- Evidence: commit 1f95b25 changed the "Autonomous launch" section of `.claude/skills/tickets/SKILL.md` from "these two launch points" to "three", but left the Frontmatter `autorun` row ("through the two launch points", line 53 at 8c32c9b) and "Before either launch" (line 103); the read checks in CCW-018's `verification.md` passed without catching them.
- Recurrences: 1
- Recommendation: apply. It costs one search, and stale text in a skill contradicts the new rule in a file Claude follows to the letter. Fixing the two leftover phrases themselves is separate work (a small CCW ticket or part of the next harness ticket).
- Decision: applied 2026-10-31 by the user; commit `docs: apply CCW-018.L-1`. Same commit fixes the stale mentions its search found: two in the target, plus `docs/design/story-orchestration.md` D3 and `CLAUDE.md` Tickets, approved by the user.

### CCW-018.L-2: A live criterion that depended on another session was accepted in a different form at close

- Status: parked
- Impact: shared (touches a skill: `.claude/skills/tickets/SKILL.md`, `refine`)
- Target: .claude/skills/tickets/SKILL.md
- Change: when refining, a [live] criterion that depends on another session's behaviour names a fallback or is split from the mechanical work, so its done-item is not left partly met at review.
- Evidence: commit 1f95b25 marked CCW-018's AC-12 done-item "(partly: the backend drafted the requests itself; ...)"; commit 8c32c9b ticked it as "accepted in a different form by the product owner at close", while AC-12 still reads "this session files the three new backend requests through AC-7".
- Recurrences: 1
- Recommendation: park; one occurrence, and the backend's request messages arrived for real right after the close. Watch for other [live] criteria that end up waived.
- Decision: parked 2026-10-31 by the user, as recommended.
