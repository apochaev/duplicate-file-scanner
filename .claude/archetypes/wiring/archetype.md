---
name: wiring
version: 2
phases: [design, test, implement, verify]
gates: [design, done]
models:
  architect: {model: opus, effort: high, maxTurns: 25}
  verifier: {model: opus, effort: medium, maxTurns: 20}
  implementer: {model: sonnet, effort: medium, maxTurns: 40}
  test-author: {model: sonnet, effort: medium, maxTurns: 40}
  retro: {model: sonnet, effort: low, maxTurns: 15}
---

# wiring

Applies to a ticket that wires built parts together, in either of two shapes:

- **A domain factory**: a static method in `domain.factory` that builds an `application` service from its `infra.adapter` adapters and returns its ports (CLAUDE.md decision 5). Example: DFS-019, `ScannerFactories.scanUseCases(Executor)` returning the `ScanUseCases` record.
- **A composition root** in a consuming app: a Spring `@Configuration` whose `@Bean`s call the factories (CLAUDE.md decision 6), plus the runner that turns one process invocation into a run (arguments, output streams, exit code, waiting for asynchronous work). Example: DFS-020, `cli`'s `ScannerConfiguration`, `ScanRunner` and `ScanCommand`.

It does not apply to a ticket that adds a use case or its service (`use-case`) or a detection stage (`pipeline-stage`). A ticket that adds a use case *and* exposes it in the CLI is two tickets, or a `use-case` ticket followed by a `wiring` one.

Written from DFS-019 (a factory, run through the pipeline as `use-case`, which marked 9 of its 14 checklist items not applicable) and DFS-020 (a composition root, built by hand, where the factory's one object behind four ports broke Spring's injection by type; commit 73d444f). DFS-019.L-5 asked for it (CCW-013). One skeleton, `skeletons/PortBeansConfiguration.java.tmpl`, carries the composition-root pattern DFS-020 had to discover.

## Where files go

- A factory method and the record it returns live in `domain.factory`, the one domain package allowed to depend on `application` and `infra.adapter`. It is already exported; a new exported package needs at least one real type plus an `exports` line in `module-info.java` (CLAUDE.md, "Current state and open items").
- A factory returns only exported types: ports from `domain.usecases`, values from `domain.model`, and its own result record from `domain.factory`. Never an `application` or `infra` type.
- A composition root lives in the consuming app's own package (for the CLI, `us.pochaev.dfs.cli`): the `@Configuration`, the Spring runner (`ApplicationRunner` plus `ExitCodeGenerator`), `Main`, and a Spring-free command class holding the argument parsing, waiting and output (DFS-020 decision 8). Spring configuration files (`application.properties`, `logback-spring.xml`) go in the app's `src/main/resources`, which the ticket lists in `touches`.
- The consuming app depends only on the scanner's `domain` packages; its `ArchitectureTest` forbids anything under `..scanner.application..` and `..scanner.infra..` (CLAUDE.md, "Important caveat").
- Stubs are owned by the architect and throw `new UnsupportedOperationException("not implemented: <TICKET-ID>")` (D2). For a factory: the factory method. For a composition root: each `@Bean` method and the command class's run method (`run(String[] args, PrintStream out, PrintStream err)` or its equivalent); `Main` and the Spring runner stay thin and real, since they only delegate.
- A Spring-level story test of a composition root fails against those stubs with the stub wrapped: a stubbed `@Bean` fails context startup with `BeanCreationException`, a stubbed command makes `SpringApplication.run` throw `IllegalStateException("Failed to execute ApplicationRunner")`, and the stub is only the trace's root cause (`Caused by: java.lang.UnsupportedOperationException: not implemented: ...`). The red gate accepts that as a stub failure since CCW-014, so such tests need no unwrapping helper.
- A data record (the factory's result record) is not stubbed: write its components and leave its compact constructor without checks, listing the checks it needs in `design.md` for the implementer (as `use-case` does).
- An edit to an existing guard test named in `touches` (an `ArchitectureTest` rule, `allowEmptyShould`) belongs to the test-author; the architect specifies it in `design.md` (DFS-019).
- The architect copies `skeletons/PortBeansConfiguration.java.tmpl` for a composition root's `@Configuration`, replacing every `<...>`.

## Edge-case checklist

Work through every item that applies; skip one that does not with a one-line reason. A factory ticket skips the composition-root section with one line, and a composition-root ticket marks item 6 not applicable unless it also changes a factory. Settle as many as possible at refinement, so the architect does not invent them.

### Shared

1. **Instances.** Does each factory call build a fresh, independent set, or share something across calls? What do the ports returned by one call share (one state repository, one resolver), so a request started through one port is known to the others? (DFS-019 decision 2.)
2. **Returned objects as a container sees them.** Does an object returned as one port also implement other ports? Spring's injection by type matches a bean's runtime type, so one service behind several ports makes every port bean match every port type ("expected single matching bean but found 2"). Wrap each port in a delegate of that interface alone (the skeleton's pattern). Which other lookups (`getBeansOfType`, a future `ServiceLoader`) see the runtime type? (DFS-020, commit 73d444f.)
3. **Executor.** Is it required or optional, and what does a `null` executor do? Who creates it and who closes it? If a container closes it, does shutdown block on running work (`ExecutorService.close()` waits for tasks; `shutdownNow` does not), and does that make Ctrl+C hang? (DFS-019 decision 4; DFS-020 decision 7.)
4. **Defaults and overrides.** Which secondary ports get default adapters, and which can a caller override? Can the caller even name an override, given that `infra.port` is not exported? (CLAUDE.md, "Spring-friendliness rules"; DFS-019 decision 3; DFS-019.L-5.)
5. **Exported surface.** Do only `domain` types cross the boundary, with the factory's result record in `domain.factory`? Do `ModuleExportsTest` and the consuming app's `ArchitectureTest` stay green? (DFS-019 decision 6; CLAUDE.md, "Important caveat".)

### Domain factory

6. **Guard tests.** Which `ArchitectureTest` rules does this ticket make non-empty, so `allowEmptyShould(true)` can come off them, and which keep it, with a comment saying why? The test-author makes the edit the design specifies. (DFS-019 decision 5.)

### Composition root

7. **Streams.** What goes to stdout and what to stderr? List every default that writes to stdout: the framework's banner and startup log (Spring Boot: `spring.main.banner-mode`, a `logback-spring.xml` console appender), and the build tool's own lines (Gradle: run with `-q`). Machine-readable stdout carries nothing else. (DFS-020 decision 4.)
8. **Exit codes.** Which values, and how do they reach the process (`ExitCodeGenerator`, `SpringApplication.exit`, `System.exit` only in `Main`)? How does the build tool react (Gradle's `run` fails the build on a non-zero exit, so the normal outcome must be 0)? (DFS-020 decision 6.)
9. **Waiting for asynchronous work.** Does the port offer a completion event, or does the runner poll a status query, and how often? What is written when the work ends failed or cancelled instead of completed? (DFS-020 decision 7.)
10. **Interruption.** What does Ctrl+C do: the JVM's default, a shutdown hook calling a cancel port, or the container's own shutdown? Does the work have side effects that need an orderly stop, or is an abrupt end harmless (a read-only scan)? (DFS-020 decision 7.)
11. **Arguments.** Which arguments and options, with which defaults? Is every usage error (none given, unknown option, invalid value) reported on stderr before any work starts? (DFS-020 decision 5.)

## Extra done-items

Beyond the standard checks (every scenario automated and passing, `./gradlew build` green including ArchUnit and module-export tests, no files changed outside `touches`):

- Every item of the checklist above is either addressed in the design doc or marked not applicable with a one-line reason.
- A composition root's port beans each expose exactly one port type: a test asserts one bean of each port type in the context (checklist item 2).
- A composition root whose stdout is machine-readable has a test that starts the real application and finds nothing on stdout but that output (checklist item 7), and the ticket's real run uses the build tool's quiet mode.
- The consuming app's `ArchitectureTest` still forbids the scanner's `application` and `infra` packages (checklist item 5).
