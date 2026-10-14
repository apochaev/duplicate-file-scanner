# CCW-015 learnings

## Entries

### CCW-015.L-1: Live replay's counts differed from the ticket's breakdown

- Status: rejected
- Impact: local
- Target: ticket authoring guidance for Definition-of-done replay items
- Change: when a done-item replays an earlier run, check totals rather than hard-coding the context's breakdown, since the gate's rule may split the failures differently.
- Evidence: `CCW-015-ticket.md`'s replay note (commit `deb75dd`): "the automatic rule took the 20 plus 1 whose root cause is the stub; the other 10 only quote the stub in a message and needed declaring with the 13". The context's 20 / 11 / 13 grouped by "stub visible in the trace"; the gate's root-cause rule split the 44 as 21 / 23. The done-item checked the total of 44, which held.
- Recurrences: 1
- Recommendation: reject; nothing went wrong, and the done-item already checked the total rather than the split.
- Decision: rejected 2026-10-14 by the user
