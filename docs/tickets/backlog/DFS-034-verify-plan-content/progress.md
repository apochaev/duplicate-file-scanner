## design (architect, attempt 1)

- Wrote design.md (use-case v6): synchronous `VerifyPlanContent` query, new `ContentComparator` port + `NioContentComparator` (NOFOLLOW_LINKS channels, 1 MiB chunks, injectable), `VerifyReport`/`OperationCheck`(+`Outcome`)/`VerifyEvent`/`VerifyListener` in `domain.model`, `VerifyPlanContentService` in `application`.
- Stubs (throw `not implemented: DFS-034`): `VerifyPlanContentService.verify`, `NioContentComparator.compare`.
- Real code: records/interfaces, constructors, `ScannerFactories.verifyPlanContent()`, `ScannerAutoConfiguration.verifyPlanContent()` bean.
- `:file-scanner:compileJava :scanner-spring:compileJava` green. module-info unchanged. Expected breakage: none.
- Expected commit: `feat(file-scanner): design plan content verify stubs [DFS-034]` (orchestrator commits).

## Tests phase (attempt 1)
Wrote VerifyPlanContentServiceTest, NioContentComparatorTest, 2 ScannerAutoConfigurationTest tests, test-plan.md. All fail with the stub exception. Expected commit: test(file-scanner): add plan content verify tests [DFS-034]
