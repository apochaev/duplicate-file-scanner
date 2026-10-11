# wiring — test-author guide

You write every test for this wiring ticket against the architect's stubs, before the implementer writes any real logic. Every story test must fail against the stubs for one reason: the stub's `UnsupportedOperationException("not implemented: <TICKET-ID>")`, either thrown directly or as the root cause of a framework's wrapper (CCW-014). That is the red gate (D2); a test that passes against a stub, or fails for another reason, is sent back to you.

## Naming convention (D11)

Every test's `@DisplayName` starts with the full ID(s) of the criteria it proves, then a readable title:

```java
@Test
@DisplayName("DFS-020.AC-1 The Spring context provides the four scan ports")
void contextProvidesTheFourPorts() { ... }
```

A test that proves more than one criterion lists every ID, comma-separated, at the very start: `DFS-020.AC-2, DFS-020.AC-5 <title>`. Nothing may come before the ID(s). A `@ParameterizedTest` needs the ID in each invocation's name as well: `@ParameterizedTest(name = "<TICKET-ID>.AC-<n> <title> {0}")`, since the gates read per-invocation names (DFS-018). Fill the same mapping into `test-plan.md` as you write each test.

## Testing a domain factory

- Call the factory and assert on what it returns through the exported ports only: a request started through one returned port is known to the others (shared state, checklist item 1), and two calls give independent sets when the design says so.
- One real-thread test with a real virtual-thread executor proves the wiring end to end on a `@TempDir`, waiting for the terminal state with a bounded poll (a deadline of a few seconds), never an unbounded loop.
- A `null` executor or other rejected argument: `try { ...; fail(...); } catch (NullPointerException expected) {}`, never `assertThrows` (the stub's exception must propagate uncaught; CCW-005.L-3, DFS-019.L-1).
- Guard-test edits the design specifies (`ArchitectureTest`, `allowEmptyShould`) are yours to make; they are not story tests and carry no criterion ID.

## Testing a composition root: two levels

Keep the command logic in a Spring-free class (for example `ScanCommand.run(String[] args, PrintStream out, PrintStream err)` returning the exit code) and test most scenarios there; start Spring only for what a real context proves (DFS-020 decision 8).

**Level 1, the command class without Spring.** Most scenarios:

- **Hand-written fake ports** (no Mockito): a small class implementing the ports, returning scripted statuses in turn, recording calls (`starts`, the order of `status` and `result` calls) and returning a chosen result. Use them for outcomes a real scan cannot be steered into (a failed scan, a scan that stays running for two polls) and for "no work started" (usage errors).
- **Real ports** from the factory with a virtual-thread executor, on `@TempDir` folders, for scenarios about real output (the JSON lines a real scan produces). Shut the executor down after each test.
- Pass `PrintStream`s over byte arrays for `out` and `err`, and assert each stream separately: exact lines on stdout, the expected error and usage lines on stderr, and an empty stdout where nothing must be written.
- Pass a short poll interval (or `Duration.ZERO` with fakes), so no test waits on a real clock.
- Assert the returned exit code, never call `System.exit`.

**Level 2, Spring.** Only what a real context proves:

- **Bean wiring** (checklist items 1 and 2): start just the `@Configuration` (`new AnnotationConfigApplicationContext(<Configuration>.class)`) and assert exactly one bean of each port type, and that a request started through one bean is known to the others. Assert behavior, not object identity: the beans are delegates by design.
- **Stdout purity and the real streams** (checklist item 7): start the real `SpringApplication` with arguments, with `@ExtendWith(OutputCaptureExtension.class)` and a `CapturedOutput` parameter. Assert that every `getOut()` line is the machine-readable output and nothing else (no banner, no log line), and check `getErr()` for progress or errors. Get the exit code with `SpringApplication.exit(context)`.
- Against stubs, these tests fail with the stub wrapped (`BeanCreationException`, `IllegalStateException`); the red gate reads the trace's root cause, so do not catch and unwrap it yourself.

## Cover, per scenario in the ticket

- The happy path and the stated edge cases, exactly as the scenarios describe them. Do not invent scenarios; report a gap instead.
- Every checklist item the design doc's disposition commits to, at the level the test plan names.
- Files with links (symlinks, hard links, junctions): create them at test time and skip with `Assumptions` when the platform or privileges forbid it (CLAUDE.md, "Test fixtures"). Build expected paths from `tempDir.toRealPath()` when the code under test resolves real paths.

## What not to do

- Do not start Spring for a scenario the command class can prove with fakes.
- Do not assert that two beans are the same object; the delegates are deliberately distinct.
- Do not touch a stub's production code.
- Do not wait with an unbounded loop or a bare `Thread.sleep` for a state; use a bounded poll with a deadline, or fakes that need no waiting.
