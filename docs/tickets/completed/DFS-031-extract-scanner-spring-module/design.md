# DFS-031: Extract the scanner wiring into a scanner-spring module — design

## Summary

Shape: a composition root, moved. `cli`'s `ScannerConfiguration` (DFS-020) becomes `ScannerAutoConfiguration`, an `@AutoConfiguration` in the new library subproject `scanner-spring` (package `us.pochaev.dfs.scannerspring`, outside `us.pochaev.dfs.scanner..`). Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. `cli` depends on it and has no scanner beans of its own. No factory change, no `module-info` change, no `@ConfigurationProperties` (decision 2).

- **AC-1**: six `@Bean`s, all stubs. `scanUseCases` makes one `ScannerFactories.scanUseCases` call, and the four port beans (`startScan`, `cancelScan`, `getScanStatus`, `getScanResult`) each wrap their port in a delegate of that interface alone, so they share one scan repository. The registration test loads the `.imports` file through Spring's `ImportCandidates` and starts a context from it, so it reaches the stubs.
- **AC-2**: `scanExecutor()`, a virtual-thread `ExecutorService` closed with `shutdownNow`, under `@ConditionalOnMissingBean(name = "scanExecutor")`. `scanUseCases` takes it with `@Qualifier("scanExecutor")`, never by type.
- **AC-3**: `@ConditionalOnMissingBean` (by return type) on `scanUseCases` and on each port bean. The Javadoc says that a replaced port no longer shares the repository.
- **AC-4**: `scanner-spring`'s `ArchitectureTest` holds one `DFS-031.AC-4` test with two parts. First an ArchUnit rule: no class in `us.pochaev.dfs.scannerspring` depends on `..scanner.application..` or `..scanner.infra..`. Then a runtime check: no port bean's class lives in those packages. **The runtime check is added by the design.** A static rule alone passes against stubs and fails the red gate (open note 3).
- **AC-5**: `ScannerConfiguration` and `ScannerConfigurationTest` are **already deleted** in this design phase, and `cli/build.gradle.kts` already adds `implementation(project(":scanner-spring"))`. So the real CLI reaches the autoconfiguration's stubs at red (open note 2).
- Placement: `scanner-spring/` uses `java-library` plus the Boot BOM. It has `api(project(":file-scanner"))` and `implementation("org.springframework.boot:spring-boot-autoconfigure")`, but not the Boot plugin. Its test dependencies are `spring-boot-test` and `assertj-core`.
- Not applicable: item 6 (no factory change; the new `ArchitectureTest` has no `allowEmptyShould`). Items 7, 8, 9 and 11 are unchanged from DFS-020, since the runner, `Main` and `ScanCommand` are not touched. Item 10 relies on item 3's `shutdownNow`.
- Expected breakage: none. `CliApplicationTest`'s existing test fails at red with the stub as its root cause, which the gate accepts without a declaration.
- Look closely at: the `Executor` parameter type (open note 1), the two design-phase deletions (open note 2) and the AC-4 runtime check (open note 3).

## Public API

- `@AutoConfiguration public class ScannerAutoConfiguration` in `us.pochaev.dfs.scannerspring` (module `scanner-spring`)
- `@Bean(destroyMethod = "shutdownNow") @ConditionalOnMissingBean(name = "scanExecutor") ExecutorService scanExecutor()`: `Executors.newVirtualThreadPerTaskExecutor()`
- `@Bean @ConditionalOnMissingBean ScanUseCases scanUseCases(@Qualifier("scanExecutor") Executor scanExecutor)`: `ScannerFactories.scanUseCases(scanExecutor)`
- `@Bean @ConditionalOnMissingBean StartScan startScan(ScanUseCases useCases)`: anonymous class delegating both `start` overloads to `useCases.start()`
- `@Bean @ConditionalOnMissingBean CancelScan cancelScan(ScanUseCases useCases)`: `useCases.cancel()::cancel`
- `@Bean @ConditionalOnMissingBean GetScanStatus getScanStatus(ScanUseCases useCases)`: `useCases.status()::status`
- `@Bean @ConditionalOnMissingBean GetScanResult getScanResult(ScanUseCases useCases)`: `useCases.result()::result`

The bodies are those of the deleted `cli` `ScannerConfiguration` (`git show HEAD:cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java`), except for the qualified `Executor` parameter.

## What is wired to what

| Port or bean | Built from | Shares with |
|---|---|---|
| `scanExecutor` (`ExecutorService`) | `Executors.newVirtualThreadPerTaskExecutor()`, or the app's bean of that name | used only by `scanUseCases`, by name |
| `ScanUseCases` | `ScannerFactories.scanUseCases(scanExecutor)`, or the app's own | the four ports (one scan repository) |
| `StartScan` | delegate of `useCases.start()`, or the app's own | the repository, unless replaced |
| `CancelScan` | `useCases.cancel()::cancel`, or the app's own | the repository, unless replaced |
| `GetScanStatus` | `useCases.status()::status`, or the app's own | the repository, unless replaced |
| `GetScanResult` | `useCases.result()::result`, or the app's own | the repository, unless replaced |

## Layer and package placement

- `domain.factory` (exported): unchanged (`ScannerFactories`, `ScanUseCases`).
- `application` / `infra.adapter`: unchanged. `scanner-spring` never names them.
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring`: `ScannerAutoConfiguration` (stub, written).
- `scanner-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: one line, `us.pochaev.dfs.scannerspring.ScannerAutoConfiguration` (written; this is registration, not behaviour).
- `scanner-spring/build.gradle.kts`: written in full, including test dependencies. `settings.gradle.kts` includes `scanner-spring`. `cli/build.gradle.kts` adds `implementation(project(":scanner-spring"))` and keeps `implementation(project(":file-scanner"))`, since `ScanCommand` uses the ports directly.
- `us.pochaev.dfs.cli`: `ScannerConfiguration` deleted. `Main` (`@SpringBootApplication`), `ScanRunner` and `ScanCommand` are unchanged.
- `cli/src/main/resources`: unchanged.
- No `module-info` (a classpath-only Spring library); `file-scanner`'s `module-info.java` is unchanged.
- Docs (implementer). `CLAUDE.md`: "Two Gradle subprojects" becomes three, with a `scanner-spring` bullet (autoconfigure library; `cli` and the future `server` depend on it). Planned `server` changes from "a third subproject" to "a fourth". Decision 6 becomes "done in DFS-031: `scanner-spring` holds `ScannerAutoConfiguration` ...; `@ConfigurationProperties` arrives with the first app-wide setting". "Current state" names the module and its `ArchitectureTest`. `README.md` "Project layout" gets a `scanner-spring/` bullet.

## Runtime behavior (composition root only)

- Streams: unchanged from DFS-020. The autoconfiguration writes nothing, and `cli`'s banner and log settings stay as they are.
- Exit codes: unchanged (0, 1, 2 through `ScanRunner`'s `ExitCodeGenerator`).
- Waiting: unchanged (`ScanCommand` polls `GetScanStatus`).
- Interruption: unchanged. Ctrl+C is left to the JVM, and the default `scanExecutor` is closed with `shutdownNow`, so the context's shutdown does not wait for a running scan.
- Arguments: unchanged (`ScanCommand`).

## Checklist disposition

1. Instances: one `ScannerFactories.scanUseCases` call per context, in the `ScanUseCases` bean. All four port beans delegate to it and share one scan repository (AC-1). A port that the app replaces is outside that sharing, and the class Javadoc says so (decision 4).
2. Returned objects as a container sees them: each port bean is a delegate of its interface alone. `StartScan` is an anonymous class because it has two overloads; the other three are method references. So `getBeansOfType` finds one per port, and `@ConditionalOnMissingBean(X.class)` cannot be met by another port's bean. AC-1 asserts one bean per port. The `ScanUseCases` record bean implements no port.
3. Executor: required. The default is `scanExecutor`, virtual threads, `destroyMethod = "shutdownNow"`, and the container closes it. The factory receives it by name (`@Qualifier("scanExecutor")`), so `applicationTaskExecutor` or any other `Executor` bean is never injected. An app's bean named `scanExecutor` replaces it, and the app owns that bean's shutdown (AC-2).
4. Defaults and overrides: the only overrides are at bean level: `scanExecutor` by name, and `ScanUseCases` and each port by type (AC-3). Secondary ports (`infra.port`) are not exported and cannot be overridden through Spring. That is unchanged and out of scope.
5. Exported surface: `scanner-spring` names only `domain.factory`, `domain.usecases.*` and `domain.model` types. Its `ArchitectureTest` enforces that (AC-4). `cli`'s `ArchitectureTest` is unchanged and still green (`scannerspring` is not under `us.pochaev.dfs.scanner.`). `ModuleExportsTest` is unaffected.
6. Guard tests: not applicable, no factory change. The new `scanner-spring` `ArchitectureTest` checks a non-empty package, so it needs no `allowEmptyShould`.
7. Streams: not applicable, `cli`'s runner and output are unchanged. Nothing in the autoconfiguration writes to stdout.
8. Exit codes: not applicable, unchanged (`ScanRunner`).
9. Waiting for asynchronous work: not applicable, unchanged (`ScanCommand` polls).
10. Interruption: unchanged behaviour, preserved by item 3's `shutdownNow` on the default executor.
11. Arguments: not applicable, unchanged (`ScanCommand`).

## Guard-test edits

None. `cli`'s `ArchitectureTest` stays as it is (a done-item). The new `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ArchitectureTest.java` is the AC-4 story test, described under "Notes for the test-author" below.

## Expected breakage

None.

## Open design notes (as of the design phase)

1. **`scanUseCases` takes `@Qualifier("scanExecutor") Executor`, not `ExecutorService`.** The factory takes `Executor`, and this accepts any executor bean of that name. An app's `ExecutorService` named `scanExecutor` (AC-2) qualifies, and the parameter does not narrow what an app may supply.
2. **Design-phase deletions.** `cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java` and `cli/src/test/java/us/pochaev/dfs/cli/ScannerConfigurationTest.java` are already removed (staged with `git rm`). If `ScannerConfiguration` stayed, its beans would make every `@ConditionalOnMissingBean` back off. AC-5 would then pass against the stubs and fail the red gate, and removing only the configuration would leave the test that compiles against it broken. `ScannerConfigurationTest`'s assertions return as `ScannerAutoConfigurationTest`'s AC-1 test (decision 5).
3. **AC-4 needs a stub-reaching part.** A pure ArchUnit test passes against the stubs, and the red gate rejects a story test that passes. The AC-4 test therefore also starts the autoconfiguration and asserts that none of the four port beans' classes is in `..scanner.application..` or `..scanner.infra..`. This is the runtime side of "depends on nothing in application or infra": a bean returning the factory's service object undelegated would expose an `application` class. It reaches the stubs at red and is a real check at green. Logged in `learnings.md`.
4. The Spring Boot version `4.1.1` is now written twice: in `cli`'s plugin and in `scanner-spring`'s BOM coordinate. A comment in `scanner-spring/build.gradle.kts` says to keep them in step.
5. On `scanner-spring`'s test classpath, the Boot BOM raises `junit-jupiter` to 6.0.3, above the root JUnit BOM's 6.0.0. That is harmless and probably already the case in `cli`.

### Notes for the test-author (stub reached per criterion)

All four port beans and `ScanUseCases` are singletons, created when the context starts. So every test that starts a context with the autoconfiguration fails at red with `BeanCreationException`, whose root cause is `UnsupportedOperationException: not implemented: DFS-031` (CCW-014). With `ApplicationContextRunner`, touch the context through `context.getBean(...)` or `context.getBeansOfType(...)` before using any AssertJ `assertThat(context)` assertion. On a failed context those calls throw `IllegalStateException` caused by the startup failure, so the root cause reaches the report. AssertJ's `hasSingleBean` instead reports the failure as message text, which the gate does not read as a root cause.

- AC-1 (four ports, shared repository): `scanExecutor`, then `scanUseCases`. Use `new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ScannerAutoConfiguration.class))`, scan an empty `@TempDir`, poll `GetScanStatus` until it reaches a final state, and expect `GetScanResult` to return `Success`. The polling pattern is the deleted `ScannerConfigurationTest`'s.
- AC-1 (registration): load the list with `ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())` (`org.springframework.boot.context.annotation.ImportCandidates`). Assert that it contains `us.pochaev.dfs.scannerspring.ScannerAutoConfiguration`. Then start a runner with `AutoConfigurations.of(<those classes>)` and `getBean(StartScan.class)`, which reaches `scanExecutor`.
- AC-2 (virtual threads): `scanExecutor`. Get the bean named `scanExecutor`, submit a task and check `Thread.currentThread().isVirtual()`.
- AC-2 (app's executor): `scanUseCases`. The user bean is named `scanExecutor` and wraps a virtual-thread executor, counting `execute` calls.
- AC-3 (app's port): the stubs of the three remaining ports and of `scanUseCases`. The user's `GetScanResult` is a lambda returning `Failure(UNKNOWN_SCAN)`. Assert `getBeansOfType(GetScanResult.class)` has size 1 and is the same instance as the user's.
- AC-3 (app's `ScanUseCases`): the four port stubs. Build the user bean from `ScannerFactories.scanUseCases(countingExecutor)`.
- AC-4: `ArchitectureTest`. First the ArchUnit rule, modelled on `cli`'s: `importPackages("us.pochaev.dfs.scannerspring")` with `DO_NOT_INCLUDE_TESTS`, and `noClasses().should().dependOnClassesThat(resideInAnyPackage("us.pochaev.dfs.scanner.application..", "us.pochaev.dfs.scanner.infra.."))`. Then the runtime check from open note 3, which reaches the stubs. A lambda's `getClass().getPackageName()` is the declaring class's package.
- AC-5: a new test in `CliApplicationTest`. `SpringApplication.run(Main.class, tmp.toString())` reaches the stubs. Assert one `set` line naming `a.txt` and `b.txt`, and exit code 0. It may also assert that `Class.forName("us.pochaev.dfs.cli.ScannerConfiguration")` throws `ClassNotFoundException`. The existing `stdoutIsPureJsonl` also fails at red on the stub root cause, which the gate accepts without a declaration.

## Stubs (as of the design phase)

- Files stubbed: `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`. The methods are `scanExecutor`, `scanUseCases`, `startScan`, `cancelScan`, `getScanStatus` and `getScanResult`.
- Each throws `new UnsupportedOperationException("not implemented: DFS-031")` per D2. Spring-level tests see it wrapped, and the red gate accepts the root cause (CCW-014).
- Commit: filled in after the design commit.
