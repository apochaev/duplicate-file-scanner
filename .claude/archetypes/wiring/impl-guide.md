# wiring — implementer guide

You are making the architect's stubs pass the tests the test-author already wrote and froze. The tests do not change (the verifier checks this with `git diff`); if a test looks wrong, stop and report it as a defect instead of editing it.

## Before you start

- Read the ticket, `design.md` (this story's filled-in `design.md.tmpl`), and the failing tests. The design doc's "What is wired to what", "Runtime behavior" and "Checklist disposition" sections are your spec.
- Every stub you touch throws `new UnsupportedOperationException("not implemented: <TICKET-ID>")`. Replace the body; leave no `not implemented:` anywhere under `src/main` (`StubsCheck`).

## Conventions: a domain factory

- Instantiate the `application` service and its `infra.adapter` adapters directly (CLAUDE.md decision 5); no `ServiceLoader`, no reflection.
- Return only exported types: ports, `domain.model` values, and the result record in `domain.factory` (checklist item 5).
- Pass the caller's executor through unchanged; create no thread or executor of your own (checklist item 3). Reject a `null` executor with `Objects.requireNonNull` when the design says it is required.
- Build one service per call when the ports must share state, and hand out its ports from that one instance (checklist item 1).

## Conventions: a composition root

- `@Configuration(proxyBeanMethods = false)`, shaped like `skeletons/PortBeansConfiguration.java.tmpl`: one bean for the factory's result, and one bean per port that is a delegate of that port's interface alone, an anonymous class for a port with several methods and a method reference for a single-method port (checklist item 2). Never return the factory's object itself as a port bean.
- The executor bean is declared `@Bean(destroyMethod = "shutdownNow")` so closing the context never waits for running work (checklist item 3).
- Keep argument parsing, waiting and output in the Spring-free command class; the `ApplicationRunner` only passes `System.out`, `System.err` and the arguments to it and keeps the exit code for `ExitCodeGenerator`. Only `Main` calls `System.exit(SpringApplication.exit(...))` (checklist item 8).
- Machine-readable stdout carries nothing else: turn the banner and startup log off in `application.properties`, and route console logging to stderr in `logback-spring.xml` (checklist item 7).
- Validate every argument before calling any port; a usage error writes to stderr and returns the usage exit code (checklist item 11).
- Wait as the design says: poll the status query at the design's interval until a terminal status, then read the result; write nothing to stdout for work that did not complete (checklist item 9).
- Progress goes through the port's listener to stderr, never stdout; the core never prints (CLAUDE.md).

## When you are done

- `./gradlew build` is green, including the ArchUnit tests (the consuming app's rule against the scanner's `application` and `infra`) and `ModuleExportsTest`.
- No stub markers remain (`StubsCheck`).
- You have not edited a test file, or anything outside the ticket's `touches`.
- Leave the done-items unticked; the verifier ticks each one it has verified.
- If you deviated from `design.md` for a reason the architect did not anticipate, add a line to `learnings.md` (D6) rather than only fixing it silently.
