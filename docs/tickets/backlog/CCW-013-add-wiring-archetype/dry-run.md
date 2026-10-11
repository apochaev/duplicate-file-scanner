# CCW-013 dry run: the wiring archetype against DFS-019 and DFS-020

CCW-013 decision 6: the checklist and the skeleton are applied after the fact to the two wiring tickets they were drawn from. This shows the checklist asks the questions those tickets met, and when each was answered; it does not show the pipeline running a wiring ticket (the next wiring ticket's own `/story run` and retro do that).

Sources: `docs/tickets/completed/DFS-019-wire-scanner-factories/DFS-019-ticket.md` (refinement decisions 1-6; design `a728d62`, implementation `1c6ba85`) and `docs/tickets/completed/DFS-020-scan-cli-write-jsonl/DFS-020-ticket.md` (refinement decisions 1-9; implementation `73d444f`).

## Checklist items

| Item | DFS-019 (domain factory) | DFS-020 (composition root) |
|---|---|---|
| 1. Instances | Decision 2: one `scanUseCases` call builds one `ScanService`, so all four ports share one `ScanRepository`; each call is a fresh, independent set. | Decision 2 and AC-1: one `@Bean ScanUseCases` from one factory call, so the four port beans share one repository (tested by behavior in `ScannerConfigurationTest`). |
| 2. Returned objects as a container sees them | Not applicable to the factory itself, but decision 2 already predicted the Spring side ("one `@Bean ScanUseCases` plus one-line `@Bean`s that unpack it") without asking how by-type injection sees one object behind four ports. | **Not asked; found during implementation**, commit `73d444f`: the first `:cli:run` failed with "expected single matching bean but found 2" (`startScan`, `getScanStatus`), because the four beans were one `ScanService`. Fixed by wrapping each port in a delegate of its own interface, and AC-1's scenario was reworded from object identity to shared behavior. Item 2, asked at refinement, raises exactly this. |
| 3. Executor | Decision 4: required, `null` throws `NullPointerException`, passed straight through; the caller owns its lifecycle. | Decision 7 settled Ctrl+C (left to the JVM). The bean's destroy method was **not asked; found during implementation**, commit `73d444f`: `ExecutorService.close()` waits for running tasks, so the bean is `destroyMethod = "shutdownNow"`. Item 3's "does shutdown block on running work" raises it. |
| 4. Defaults and overrides | Decision 3: default adapters only; overrides deferred, since `infra.port` is not exported and no outside caller could name one. | Not applicable: the CLI uses the factory's defaults and cannot override (DFS-019 decision 3). |
| 5. Exported surface | Decision 6: `ScanUseCases` in the already exported `domain.factory`; no `module-info` change. | Answered by an existing guard, no decision needed: `cli`'s `ArchitectureTest` forbids the scanner's `application` and `infra`, and stayed green. |
| 6. Guard tests | Decision 5: `allowEmptyShould(true)` removed from the layer and no-Spring rules, kept on the `util` rule; the edit became DFS-019.L-2 (owned by the test-author). | Not applicable: no factory change. |
| 7. Streams | Not applicable: no composition root. | Decision 4: JSONL on stdout only, progress and errors on stderr; banner off and console logging to stderr; `-q` for Gradle's lines. |
| 8. Exit codes | Not applicable: no composition root. | Decision 6: 0, 1, 2 (130 left to the JVM); a non-zero code for "duplicates found" rejected because Gradle's `run` would fail the build. |
| 9. Waiting for asynchronous work | Not applicable: no composition root. | Decision 7: `ScanListener` has no completion event, so the runner polls `GetScanStatus` about every 100 ms; nothing on stdout for a failed scan. |
| 10. Interruption | Not applicable: no composition root. | Decision 7: Ctrl+C left to the JVM; a read-only scan needs no orderly stop; `CancelScan` stays unused. |
| 11. Arguments | Not applicable: no composition root. | Decision 5: folders plus `--min-size=<bytes>`; usage errors on stderr before any scan starts. |

Of the 22 cells, two were met only during implementation, both in DFS-020 and both in commit `73d444f`: item 2 (the bean-injection failure) and half of item 3 (the blocking `close`). Both are questions the checklist now asks at refinement. DFS-019's own `use-case` run marked 9 of 14 items not applicable; here a factory ticket answers items 1 to 6 and skips the composition-root section in one line.

## The skeleton filled with DFS-020's names

`skeletons/PortBeansConfiguration.java.tmpl` with `<app package>` = `us.pochaev.dfs.cli`, `<ConfigurationName>` = `ScannerConfiguration`, `<Factories>` = `ScannerFactories`, `<factoryMethod>` = `scanUseCases`, `<FactoryResult>` = `ScanUseCases`, `<executorBeanName>` = `scanExecutor`, `<factoryResultBeanName>` = `scanUseCases`, the multi-method port `StartScan` (two `start` overloads) and the single-method ports `CancelScan`, `GetScanStatus` and `GetScanResult` (Javadoc and imports omitted):

```java
@Configuration(proxyBeanMethods = false)
public class ScannerConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService scanExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ScanUseCases scanUseCases(ExecutorService scanExecutor) {
        return ScannerFactories.scanUseCases(scanExecutor);
    }

    @Bean
    StartScan startScan(ScanUseCases ports) {
        StartScan port = ports.start();
        return new StartScan() {
            @Override
            public Result<ScanId> start(List<Path> locations, ScanConfig config) {
                return port.start(locations, config);
            }

            @Override
            public Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener) {
                return port.start(locations, config, listener);
            }
        };
    }

    @Bean
    CancelScan cancelScan(ScanUseCases ports) {
        return ports.cancel()::cancel;
    }

    @Bean
    GetScanStatus getScanStatus(ScanUseCases ports) {
        return ports.status()::status;
    }

    @Bean
    GetScanResult getScanResult(ScanUseCases ports) {
        return ports.result()::result;
    }
}
```

Compared with the real `cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java` (commit `73d444f`): the same six beans in the same order and shapes, `proxyBeanMethods = false`, the executor bean with `destroyMethod = "shutdownNow"` returning `Executors.newVirtualThreadPerTaskExecutor()`, one `ScanUseCases` bean from one factory call, `StartScan` as an anonymous class delegating both overloads, and `CancelScan`, `GetScanStatus` and `GetScanResult` as method references.

Differences, each without effect on behavior:

- Parameter and local names: the real class names the factory-result parameter `useCases` and the delegate's local `start`; the skeleton uses `ports` and `port`, which read the same for any factory.
- Javadoc wording: the real class's comments name the scan repository and DFS-020 decision 7; the skeleton's name the checklist items instead, so they stay true for any composition root.
