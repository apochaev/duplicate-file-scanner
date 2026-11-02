
- DFS-042 design: the architect ran out of turns before writing design.md. A use case with shared machinery, a generic port, a service and Spring wiring is about 18 files, which is too many for 35 turns once exploration is done. Write the design.md Summary and skeleton before the stubs (archetype "where files go" last bullet), and batch stub writes with the Write tool. A Git Bash heredoc batch failed on quoting and wasted a turn.
- The DFS-042 AC-14 scenario waits on a latch released by the last EntryChecked, then asserts COMPLETED. The final status update happens after that event, so the latch alone is racy. Refinement of real-thread scenarios should name a wait on the task's completion (the Future), not on a listener event.
- DFS-042 verify: the shared `JobRunner` (meant for plan execution too) hard-codes "executor rejected the plan verify" in its rejection message, and the `Error` path (FAILED, then rethrow) has no test; an `Error` thrown by `executor.execute` itself leaves the job QUEUED. The execution ticket that reuses the machinery should make the message generic and pin the `Error` path with a test.
- DFS-042 verify: the Spring tests wait for a job with a bounded `Thread.onSpinWait` loop. The archetype allows a bounded poll, but a spin loop burns a core. Where the job's listener or a wrapping executor's `Future` is reachable (as in AC-14), the test guide could prefer those to a spin poll.

## Entries

### DFS-042.L-1: the archetype's maxTurns never reaches the agent; the architect's own frontmatter binds

- Status: applied
- Impact: shared (touches an agent prompt: `.claude/agents/architect.md`)
- Target: `.claude/agents/architect.md`
- Change: raise the frontmatter `maxTurns` from 25 to 35, so the limit DFS-032.L-1 meant to set actually binds; the Agent tool takes `model` and `effort` but no turn limit, so an archetype's `models.<agent>.maxTurns` is not applied.
- Evidence: DFS-042 plan.json design run: 37 turns over two invocations, the first stopped at 25 before design.md existed; `.claude/agents/architect.md` line 7 `maxTurns: 25` while `.claude/archetypes/use-case/archetype.md` line 7 says 35 (applied by DFS-032.L-1, archetype version 6).
- Recurrences: 2 (DFS-032: stopped at 25, resumed; DFS-042: stopped at 25 after the archetype said 35, resumed)
- Recommendation: apply; the fix DFS-032.L-1 intended has not taken effect, and every use-case architect run since has the same cap.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-042.L-1 and DFS-042.L-2`

### DFS-042.L-2: a real-thread test must wait on the terminal state, not on a listener event, and never spin

- Status: applied
- Impact: shared (touches `.claude/archetypes/`, read by the test-author and in refinement)
- Target: `.claude/archetypes/use-case/test-guide.md`
- Change: in "One real-thread test", add: wait on the task's completion (its `Future`, reached by wrapping the real executor) or on the terminal state itself; a latch released by the last listener event fires before the final status update, so it alone is racy; a bounded poll parks or sleeps briefly per iteration rather than spinning with `Thread.onSpinWait`.
- Evidence: DFS-042 design.md and learnings.md: the ticket's AC-14 scenario waited on the last `EntryChecked`, racing the COMPLETED transition (caught at the design gate); the Spring tests wait with a 30 s `Thread.onSpinWait` loop (verification.md, "Decided without you").
- Recurrences: 1 (DFS-042)
- Recommendation: apply; it prevents a flaky pattern the next async ticket (plan execution) will meet.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-042.L-1 and DFS-042.L-2`

### DFS-042.L-3: the shared JobRunner names plan verify in its rejection message and has an untested Error path

- Status: parked
- Impact: local (a future ticket's scope)
- Target: the plan-execution ticket (not yet filed)
- Change: when the execution ticket is refined, add criteria that `JobRunner`'s rejection message is generic and that the `Error` path (FAILED then rethrow, and an `Error` from `executor.execute` leaving the job QUEUED) is pinned by tests.
- Evidence: DFS-042 verification.md "Known limits" and learnings.md (verifier note).
- Recurrences: 1 (DFS-042)
- Recommendation: park until that ticket is filed; it targets future scope, not a template.
- Decision: parked 2026-11-01 by the user
