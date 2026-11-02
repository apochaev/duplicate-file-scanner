## design (architect)

- DFS-051.L-design-1: Decision 5 (quarantine at each volume root) conflicts with the DoD rule that no test writes outside a `@TempDir`. AC-20 and AC-21 go through the factory and Spring beans, where no test-only seam can reach, and on Linux and macOS `/` is not writable. I resolved it with an `ExecutionConfig.quarantineAnchors` deviation, flagged at the design gate. Refinement should check any filesystem-placement decision against the `@TempDir` rule.
- DFS-051.L-design-2: A large bash heredoc batch (about 10 files in one command) failed with "syntax error near unexpected token newline" partway through, leaving some files written. Batching with the Write tool (several calls in one turn) is safer for stub files.
- DFS-051.L-design-3: The ticket was amended mid-design (c54c837). The coordinator's message was enough to fold it in, but an amendment that lands after the architect has started is easy to miss: worth a re-read step before the design is written.


## Orchestrator notes (design phase, 2026-11-01)

- Ticket size: 14 decisions, 23 criteria and 30 scenarios made one architect run expensive (the resumed invocation alone used about 179k subagent tokens and 45 tool uses; the user asked why). The user's direction: split tickets of this size before launch, for example the per-file move-and-manifest core in one ticket and the job wrapper and Spring wiring in another.
- Mid-run amendment: dfs-product's review (plan ID recomputation, keeper as a separate physical copy) arrived after the architect had started; the ticket was amended on the branch in c54c837 and the running architect told by message. It worked, but a review before the ready commit would have avoided the extra round trip.
- Usage-limit stop: the first architect invocation ended with an API 429 (5-hour limit) before writing anything; it was resumed after the reset with SendMessage, which re-sends the agent's whole context. The plan.json run records both invocations as one run.

## verify (verifier)

- DFS-051.L-verify-1: The orchestrator's launch prompt and progress.md said the two AC-6 symlink tests skip on this Windows machine; the test results show they ran and passed (symlink creation is allowed here). State "skips" only from the test XML (`<skipped>`), not from expectation.
- DFS-051.L-verify-2: `./gradlew :file-scanner:test --tests ... --rerun` replaces the whole test-results folder, so the other classes' XML (ModuleExportsTest etc.) disappears until the next full run. Read the full-run XML before a filtered rerun.
- DFS-051.L-verify-3: Not covered by any criterion: two runs at the same time over overlapping files (X keep Y, Y keep X) can both pass their checks and both move, since check and rename are not atomic across runs. Worth a criterion when the server's Execute endpoint (M-5) is refined.

## Retro entries

### DFS-051.L-1: Filesystem-placement decisions must say how tests reach them inside a `@TempDir`

- Status: applied
- Impact: shared
- Target: the `tickets` skill's refine checklist
- Change: add a refinement question: any decision that places files outside a test-controlled directory (a volume root, a home folder) states the seam tests use instead.
- Evidence: DFS-051.L-design-1: decision 5 put the quarantine at each volume root; the architect added an `ExecutionConfig.quarantineAnchors` deviation at the design gate.
- Recurrences: 1 (DFS-051)
- Recommendation: apply.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-051.L-1, L-2, L-3`

### DFS-051.L-2: Split oversized tickets before launch

- Status: applied
- Impact: shared
- Target: the `tickets` skill's refine
- Change: when a ticket has more than about 8 decisions or 15 criteria, refine proposes a split at a natural seam (for example the core use case versus the job wrapper and wiring) before the ready commit.
- Evidence: orchestrator notes (design phase): 14 decisions, 23 criteria, 30 scenarios; design took 45 turns, about 179k subagent tokens and a 429 stop; the user's own direction to split tickets of this size.
- Recurrences: 1 (DFS-051)
- Recommendation: apply; settle the threshold at decide.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-051.L-1, L-2, L-3`

### DFS-051.L-3: Get the product review in before the ready commit

- Status: applied
- Impact: shared
- Target: the `tickets` skill's refine (ready step)
- Change: a review the session is still waiting for blocks `ready`, so no amendment lands while the architect is running.
- Evidence: DFS-051.L-design-3 and the orchestrator note: amendment c54c837 (decision 14, AC-22, AC-23) landed mid-design and the architect was told by message.
- Recurrences: 1 (DFS-051)
- Recommendation: apply.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-051.L-1, L-2, L-3`

### DFS-051.L-4: Raise the verifier's frontmatter `maxTurns` (DFS-026.L-7 never took effect)

- Status: applied
- Impact: shared
- Target: `.claude/agents/verifier.md` frontmatter
- Change: raise `maxTurns` from 20 to 30, and instruct the verifier to write `verification.md`'s skeleton early so a cap stop keeps its work.
- Evidence: plan.json verify run 1: the first invocation stopped at the 20-turn cap with nothing written (29 tool uses) and was resumed with SendMessage. DFS-026.L-7 raised the cap only in `pipeline-stage/archetype.md`'s `models:`, which DFS-042.L-1 found never reaches the agent; `verifier.md` still says `maxTurns: 20`.
- Recurrences: 2 (DFS-026, DFS-051)
- Recommendation: apply.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-051.L-4, L-5, L-6`; the maxTurns raise had already landed in c928b52 (DFS-047.L-4), so only the write-verification.md-early instruction was added here

### DFS-051.L-5: Verifier reports test results only from the XML, and reads it before a filtered rerun

- Status: applied
- Impact: shared
- Target: `.claude/agents/verifier.md`
- Change: state that a test skipped only when its XML has `<skipped>`; read the full-run XML before any `--tests ... --rerun`, which replaces the other classes' results.
- Evidence: DFS-051.L-verify-1 (AC-6 symlink tests said to skip, actually ran and passed) and DFS-051.L-verify-2.
- Recurrences: 1 (DFS-051)
- Recommendation: apply.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-051.L-4, L-5, L-6`

### DFS-051.L-6: Architect writes stubs with several Write calls, not one large bash heredoc

- Status: applied
- Impact: shared
- Target: `.claude/agents/architect.md`
- Change: one line in the stub-writing guidance: write stub files with several Write calls in one turn, not one multi-file bash heredoc.
- Evidence: DFS-051.L-design-2: a heredoc batch of about 10 files failed partway with "syntax error near unexpected token newline", leaving some files written.
- Recurrences: 1 (DFS-051)
- Recommendation: apply.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-051.L-4, L-5, L-6`
