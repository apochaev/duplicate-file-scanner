# DFS-025 learnings

## Entries

### DFS-025.L-1: An estimate's parameter tuned on one recorded run was off by 2.7x on the next

- Status: parked
- Impact: shared (a skill: `.claude/skills/tickets/SKILL.md`)
- Target: `.claude/skills/tickets/SKILL.md`
- Change: in refine, when a decision fixes a numeric parameter of an estimate (a window, a threshold) from a replay, replay it on at least two recorded runs when two exist, or else state in the decision that the Definition of done's real run may amend it.
- Evidence: DFS-025 refine commit a7167cd chose a 10 s files-rate window, replayed on one log (1:43 at 3:16 against 1:40 real); the first real run after implementing read `ETA 3:10` with 1:10 left (2.7x), and decision 1 was amended to 5 s (`DFS-025-ticket.md`, decision 1 amendment; commit 0e945d8).
- Recurrences: 1 (DFS-025)
- Recommendation: park; the Definition of done's real run caught it as designed, at the cost of one extra run, and one occurrence doesn't yet show a pattern.
- Decision: parked 2026-10-19 by the user
