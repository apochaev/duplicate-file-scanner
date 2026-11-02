## design (architect)

- Wrote design.md (Summary first) from use-case v9's template, including the c54c837 amendment (decision 14, AC-22, AC-23).
- Stubs (14, each throwing `not implemented: DFS-051`): PlanExecutionService (start x2, cancel, status, result, ExecutionOutcomes x2), PlanIds.of, JobRunner.rejectionMessage, PlanExecutionId.random, NioQuarantineDeleter.quarantinePath/move, JsonLinesManifestWriter.create/open.
- Written for real: the four use-case ports, the domain.model records (unchecked; the checks are listed in design.md), six ErrorCode values, the FileDeleter/ManifestWriter/ManifestLine ports, PlanExecutionProgress, PlanExecutionUseCases, ScannerFactories.planExecutionUseCases, ExecutionProperties, the ScannerAutoConfiguration beans.
- Compile is green (file-scanner, scanner-spring, cli, server). cli:test and server:test pass. Current failures: ScannerAutoConfigurationTest.appExecutorReplacesDefault (declared under Expected breakage) and PlanVerifyServiceTest DFS-042.AC-9, which fails directly on the JobRunner stub.
- Expected commit: `feat(file-scanner): design plan execution into quarantine [DFS-051]` (orchestrator commits).

