## design (architect)

- DFS-051.L-design-1: Decision 5 (quarantine at each volume root) conflicts with the DoD rule that no test writes outside a `@TempDir`. AC-20 and AC-21 go through the factory and Spring beans, where no test-only seam can reach, and on Linux and macOS `/` is not writable. I resolved it with an `ExecutionConfig.quarantineAnchors` deviation, flagged at the design gate. Refinement should check any filesystem-placement decision against the `@TempDir` rule.
- DFS-051.L-design-2: A large bash heredoc batch (about 10 files in one command) failed with "syntax error near unexpected token newline" partway through, leaving some files written. Batching with the Write tool (several calls in one turn) is safer for stub files.
- DFS-051.L-design-3: The ticket was amended mid-design (c54c837). The coordinator's message was enough to fold it in, but an amendment that lands after the architect has started is easy to miss: worth a re-read step before the design is written.


## Orchestrator notes (design phase, 2026-11-01)

- Ticket size: 14 decisions, 23 criteria and 30 scenarios made one architect run expensive (the resumed invocation alone used about 179k subagent tokens and 45 tool uses; the user asked why). The user's direction: split tickets of this size before launch, for example the per-file move-and-manifest core in one ticket and the job wrapper and Spring wiring in another.
- Mid-run amendment: dfs-product's review (plan ID recomputation, keeper as a separate physical copy) arrived after the architect had started; the ticket was amended on the branch in c54c837 and the running architect told by message. It worked, but a review before the ready commit would have avoided the extra round trip.
- Usage-limit stop: the first architect invocation ended with an API 429 (5-hour limit) before writing anything; it was resumed after the reset with SendMessage, which re-sends the agent's whole context. The plan.json run records both invocations as one run.
