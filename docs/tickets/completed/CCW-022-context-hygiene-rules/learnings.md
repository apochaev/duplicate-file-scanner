# CCW-022 learnings

## Entries

### CCW-022.L-1: `[run]` scenarios downgraded to read-only at review

- Status: parked
- Impact: shared (touches the `tickets` skill, `refine` and `review` modes)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` and `review` modes
- Change: say how a scenario that needs a live `/clear` or a live session (such as AC-1, AC-3, AC-4 and AC-6 here) is handled: run for real, waived explicitly at review, or tagged `[read]` at refinement, so a `[run]` tag is not left on a scenario that cannot be run.
- Evidence: commit `b082dbd` changed the CCW-022 done-item to "Every scenario above is checked as marked (`[run]` exercised for real, `[read]` read) ... (user accepted AC-1, AC-3, AC-4 and AC-6 as read-only on 2026-11-07; AC-8 was run, see verification.md)". The scenarios themselves stayed tagged `[run]`, so four of the nine criteria were not run as marked. Related but distinct from `CCW-001.L-6` (applied), which asks refinement to record what can only be read; CCW-022 shows that rule still lets live-session scenarios be tagged `[run]` and waived later.
- Recurrences: 1
- Recommendation: park. One occurrence; apply if it recurs on another harness ticket.
- Decision: parked 2026-11-07 by the user
