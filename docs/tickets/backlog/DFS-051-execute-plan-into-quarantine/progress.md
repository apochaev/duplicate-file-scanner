## design (architect)

- Wrote design.md (Summary first) from use-case v9's template, including the c54c837 amendment (decision 14, AC-22, AC-23).
- Stubs (14, each throwing `not implemented: DFS-051`): PlanExecutionService (start x2, cancel, status, result, ExecutionOutcomes x2), PlanIds.of, JobRunner.rejectionMessage, PlanExecutionId.random, NioQuarantineDeleter.quarantinePath/move, JsonLinesManifestWriter.create/open.
- Written for real: the four use-case ports, the domain.model records (unchecked; the checks are listed in design.md), six ErrorCode values, the FileDeleter/ManifestWriter/ManifestLine ports, PlanExecutionProgress, PlanExecutionUseCases, ScannerFactories.planExecutionUseCases, ExecutionProperties, the ScannerAutoConfiguration beans.
- Compile is green (file-scanner, scanner-spring, cli, server). cli:test and server:test pass. Current failures: ScannerAutoConfigurationTest.appExecutorReplacesDefault (declared under Expected breakage) and PlanVerifyServiceTest DFS-042.AC-9, which fails directly on the JobRunner stub.
- Expected commit: `feat(file-scanner): design plan execution into quarantine [DFS-051]` (orchestrator commits).


## Design gate feedback (2026-11-01, from the user)

- Write the manifest's `end` line from the job's final recorded status (after `JobRunner` settles COMPLETED or CANCELLED), so the manifest never says COMPLETED for a job that ended CANCELLED (design.md "Look closely at" item 3). Everything else in the design is accepted as is, including the `quarantineAnchors` deviation, the early `moving` line and the `run`-only manifest for a run that never started.

## design rework (architect, after the gate feedback)

- The manifest's `end` line now comes from a settle hook run after `JobRunner` records the terminal status, so it states the recorded status (COMPLETED, CANCELLED or FAILED). The work no longer writes `end` or closes the session; a run-local holder shares the session and counts between work and hook.
- New seam in `JobRunner`: `interface Settled<P, T> { void settled(Job<P, T> job); }` and the stub overload `submit(id, initial, work, settled)` (throws `not implemented: DFS-051`). The three-argument `submit`, `runTask` and plan verify are unchanged.
- design.md updated: Summary (AC-9 bullet, "Look closely at" item 3), Layer placement, Behavior (start step 6, work steps 1, 6, 7, new settle-hook block), test-author notes, State and transitions, Concurrency, checklist item 4, Stubs.
- Files: `file-scanner/.../application/JobRunner.java`, `design.md`, this file. `:file-scanner:compileJava` is green. Not committed (orchestrator commits).

## tests (test-author)

- Wrote PlanExecutionServiceTest, PlanExecutionRealThreadTest (application), NioQuarantineDeleterTest, JsonLinesManifestWriterTest (infra.adapter); added the AC-15 test to PlanVerifyServiceTest; added four AC-20 tests and updated the executor-name set in scanner-spring's ScannerAutoConfigurationTest. test-plan.md filled.
- StoryGate test-phase: PASS (54 story tests red with stubs, 23 criteria traced). The one "other" stub failure is the existing PlanVerifyServiceTest DFS-042.AC-9, which hits the JobRunner.rejectionMessage stub.
- Both AC-6 symlink tests abort via Assumptions where symlinks cannot be created (likely on this Windows box), so they are skipped, not red, here.
- Expected commit: `test(file-scanner): add plan execution tests [DFS-051]` (orchestrator commits).

## implement (implementer)

- Implemented all 14 stubs: PlanExecutionService (validation, per-link check-compare-move loop, manifest lines, settle hook for the `end` line, listener detach, snapshot/report), PlanIds.of (encoding moved from PreviewDeletionPlanService, which now calls it), JobRunner (settled overload threaded through runTask, rejectionMessage), PlanExecutionId.random, NioQuarantineDeleter, JsonLinesManifestWriter. Added the record checks design.md lists.
- `./gradlew build` green. No test file changed.
- Expected commit: `feat(file-scanner): execute deletion plans into quarantine [DFS-051]` (orchestrator commits).
