
## design (architect, attempt 1)

- Wrote design.md and the stubs: the four plan verify ports, PlanVerifyId, JobStatus, PlanVerifySnapshot and two ErrorCode values (domain.model); JobStore (infra.port) and InMemoryJobStore (infra.adapter); Job, PlanVerifyProgress, JobRunner and PlanVerifyService (application); PlanVerifyUseCases and ScannerFactories.planVerifyUseCases (domain.factory, real); planVerifyExecutor and five beans in ScannerAutoConfiguration (real).
- Every behavior method throws `not implemented: DFS-042`. `:file-scanner:compileJava :scanner-spring:compileJava` passes.
- The test-author must edit ScannerAutoConfigurationTest line 143 (a second ExecutorService bean now exists).
- Expected commit: `feat(file-scanner): design plan verify jobs with stubs [DFS-042]`.

## tests (test-author, attempt 1)

- Wrote PlanVerifyServiceTest, PlanVerifyRealThreadTest (application), InMemoryJobStoreTest (infra.adapter), three DFS-042.AC-13 tests in ScannerAutoConfigurationTest, plus the one-line edit of appExecutorReplacesDefault; test-plan.md filled in.
- All 28 new tests fail only with `UnsupportedOperationException: not implemented: DFS-042`; no Thread.sleep.
- Expected commit: `test(file-scanner): add plan verify job tests [DFS-042]`.

## Implement (attempt 1)
Implemented InMemoryJobStore, JobRunner, PlanVerifyService, VerifyOutcomes and the record compact-constructor checks per design.md. Build green. No deviations.
