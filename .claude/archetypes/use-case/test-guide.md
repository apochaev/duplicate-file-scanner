# use-case — test-author guide

You write every test for this use case against the architect's stubs, before the implementer writes any real logic. Every story test must fail against the stubs for exactly one reason: the stub's `UnsupportedOperationException("not implemented: <TICKET-ID>")`. That is the red gate (D2); a test that passes against a stub, or fails for another reason, is a vacuous or broken test and is sent back to you.

## Naming convention (D11)

Every test's `@DisplayName` starts with the full ID(s) of the criteria it proves, then a readable title:

```java
@Test
@DisplayName("DFS-018.AC-7 Cancelling a queued scan cancels it at once")
void cancellingAQueuedScanCancelsItAtOnce() { ... }
```

A test that proves more than one criterion lists every ID, comma-separated, at the very start: `DFS-018.AC-1, DFS-018.AC-2 <title>`. Nothing may come before the ID(s) — `TraceabilityCheck` reads the run of IDs at the very start of the display name and stops at the first token that is not `<TICKET-ID>.AC-<n>`. Fill the same mapping into `test-plan.md` (from `test-plan.md.tmpl`) as you write each test; the traceability gate cross-checks both.

A `@ParameterizedTest` needs the ID in each invocation's name too: `RedCheck` and `TraceabilityCheck` read the per-invocation name, and JUnit's default `[n] VALUE` hides the ID, so a correct stub failure is counted as a regression. Set `@ParameterizedTest(name = "<TICKET-ID>.AC-<n> <title> {0}")`, as well as the container's `@DisplayName` (DFS-018: 9 such "regressions" failed the red gate).

## What to test against

- The service's own logic (`application`): fakes of every port and seam it depends on (a scripted runner for a composed collaborator, a fake or the in-memory state port), and a manual executor, never real threads or the real filesystem. Follow CLAUDE.md's fixture rules if you use the shared fixtures (`Fixtures.copyInto` into a `@TempDir`).
- A new adapter (`infra.adapter`), such as an in-memory state port: tested directly, including its atomicity under real threads if the design relies on it.
- A scenario that checks what the state port holds ("the repository holds no record") uses a test fake of the port that exposes its records. Do not add a listing method to the production port for tests (DFS-018's `ListingRepository`).
- A factory method or Spring bean exposing the use case: call the use case through it (and through the bean) before asserting the wiring (the bean exists, an app's own bean wins), so the test fails red on the use case's stub. A pure wiring assertion passes against stubs, and the red gate rejects it (DFS-022.L-1).
- Expected paths in a real-file test: when the code under test resolves paths with `toRealPath()` (as `NioLocationResolver` does), build expected paths from `tempDir.toRealPath()`, not the raw `@TempDir`. The raw path passes on Windows but fails on macOS (`/var` resolves to `/private/var`) or with 8.3 short temp names (DFS-005, AC-17).
- A CLI-level story test (one that runs `ScanCommand` and reads its stdout and stderr): the CLI turns a stub's failure into an "error: scan failed" line (exit 1), so the test would fail with `AssertionFailedError`, which the red gate rejects. Pass the run's output to `StubFailures.rethrow(out, err)` (`cli/src/test/java/us/pochaev/dfs/cli/StubFailures.java`) before asserting. It rethrows the first `not implemented:` line as `UnsupportedOperationException`, and does nothing once the feature works, so there is no per-ticket copy to leave behind as dead scaffolding (DFS-061.L-7, DFS-062).

## Testing asynchronous behavior without timing

A use case that runs work on an executor has states that exist only between two events (queued, running, stopping). Pin each one deterministically:

- **Queued:** pass a manual executor, a small test class that keeps submitted tasks in a list until the test calls something like `runNext()`. Assert the queued state, act on it (for example cancel), then run the task and assert what it did or did not do.
- **Running and stopping:** give the fake collaborator a latch it blocks on at a chosen point (for example after its third event). Run the task on a separate thread you control, assert the running state, act (for example cancel), assert the stopping state, then release the latch and assert the terminal state.
- **Races:** test a race by applying the two transitions in both orders, one test per order (for example "cancel, then completion" and "completion, then cancel"), not by starting two threads and hoping they collide.
- **One real-thread test:** prove the wiring on a real executor (virtual threads) once, waiting for the terminal state with a bounded timeout (a few seconds) on a future, a latch or a bounded poll. Wait on the task's completion (its `Future`, reached by wrapping the real executor) or on the terminal state itself: a latch released by the last listener event fires before the final status update, so on its own it races the terminal transition. A bounded poll parks briefly each iteration (`LockSupport.parkNanos`) and never spins with `Thread.onSpinWait` (DFS-042.L-2).
- **No `Thread.sleep`.** Not to wait for a state, not to "give the executor time". A sleep makes a test slow and flaky, and a flaky red gate is worse than none.
- **Which thread:** if the design says callbacks run on the executor's thread, record the thread in the fake listener and assert it is not the test thread.

## Cover, per scenario in the ticket

- The happy path and the stated edge cases, exactly as scenarios describe them — do not invent new scenarios; if you find a gap, report it rather than silently adding one (the ticket's scenarios are what was reviewed and approved at `ready`).
- Whatever the design doc's checklist disposition and "State and transitions" table commit to: every listed transition, and what each command does in each state, including the terminal ones.
- Results: assert the exact `Success` value or the exact `Failure` code (and message where the ticket names it), not only that "it failed".
- Invalid input: whatever the design doc's public API section says is rejected has a test that expects rejection — a `Failure` value for an expected outcome, a `NullPointerException` for a `null` argument.
- Injected failures: a test that injects a failure mid-run arms it before the run starts and names the line or step it fails on, never after the run is already blocked past that step. Where it depends on step order (for example `Moving` is written before the compare), `test-plan.md` states the order (DFS-055.L-9).
- Ordering tests: for "X is checked before Y", first make a sound input reach the stubbed step (for example against a lock held elsewhere), then show that a refused input leaves Y untouched. A refused input alone passes against the stub, because the check fires before the stub is reached (DFS-055.L-10).
- An expected-exception test (a `null` argument, or any rejection the design says throws) runs, in the test phase, against a stub that always throws `UnsupportedOperationException` first, so never write it with `assertThrows`: the type mismatch reports as `assertThrows`'s own `AssertionFailedError`, the stub's exception survives only as its cause, and the red gate reads only the top-level failure type, so it rejects the test as "not a stub failure". Write `try { ...; fail(...); } catch (ExpectedType expected) {}` instead, so the stub's exception propagates uncaught (CCW-005.L-3, DFS-019.L-1). Data records have no checks at design, so a test that a `null` component or list element throws builds the record inside that `try` block, together with the stubbed call. Built before the `try`, the record's check is absent at red (the call reaches the stub), but at green it throws outside the `try` and fails the test (DFS-022.L-2).
- Discrimination: for any adapter whose job is to distinguish inputs (a hasher, a comparator, any port mapping distinct inputs to distinct outputs), at least one test that feeds it two different real inputs and asserts the outputs actually differ — not only that identical inputs produce matching outputs. A suite proving only the matching case cannot catch a constant-returning or partially-ignoring implementation (found on DFS-002: `NioContentHasher.sampleHash` reached the done gate proven only for identical bytes; a constant-returning implementation would have passed the whole suite).
- Exact encodings: when `design.md` commits to an exact ID, hash or format, add golden-value tests whose expected value is built independently of the code (a literal computed from the documented encoding, or the encoding assembled by hand in the test), one per variant the encoding has (for DFS-022's plan ID: the empty plan, a rule source, a pick source, a conflict) (DFS-022.L-3).
- Shared helpers: when `design.md` moves existing code onto a new shared helper and claims identical behavior, add a unit test pinning the helper's mapping over fake inputs (each input combination to its result, reason and detail text), so equivalence is tested rather than argued (DFS-032.L-3).
- Data reused across test classes: when a scenario reuses another scenario's data on a second surface (for example "the bean checks the same plan" in the Spring test), reproduce that data exactly, or build what the second class can reach and record the deviation in `test-plan.md`. Fixture helpers private to one test class are not a reason to change what the scenario checks silently (DFS-033.L-2).
- Proving a link is not followed: a test whose outcome is the same whether a link is followed or not proves nothing about following. A followed junction is a directory, which is "not a regular file" too, so carry the no-follow proof on a symlink whose target would give a different outcome (same bytes, size and mtime), or observe it directly, for example with a recording reader asserting `isOther()` (DFS-033.L-2).

## Testing a port method deferred to a later ticket

Some use cases stub a port method that is not this ticket's job to implement (for example `ContentHasher.fullHash`, deferred by DFS-002 to DFS-003 — see `archetype.md`, "Where files go"). At the point your tests run, the implementer's permanent replacement message does not exist yet: the stub still reads the generic `not implemented: <TICKET-ID>`. Do not assert the eventual, differently-worded message — checked against the still-generic stub, that turns today's legitimate stub failure into your own `AssertionFailedError`, which `RedCheck` rejects (it requires the raw stub exception, not an assertion failure). A bare `catch (UnsupportedOperationException expected) {}` does not fix this either: it also swallows the stub's own exception and passes vacuously, before the method is ever really implemented.

Instead, catch the exception and re-throw it unchanged only while its message still starts with `not implemented:`:

```java
try {
    hasher.fullHash(file);
    fail("expected fullHash to throw UnsupportedOperationException");
} catch (UnsupportedOperationException expected) {
    if (expected.getMessage() != null && expected.getMessage().startsWith("not implemented:")) {
        throw expected;
    }
}
```

This fails red now (the generic stub message starts with `not implemented:`, so the catch rethrows it and `RedCheck` recognizes it as the stub failure), and turns green on its own once the implementer's permanent message lands (the message no longer starts with `not implemented:`, so the catch no longer rethrows). Leave the exact final wording for the verifier to confirm by eye against the ticket's Definition of done, not an automated assertion.

**When your ticket is the one that implements the deferred method.** The deferring ticket's stand-in test (the conditional rethrow above) guards a state your ticket ends. Remove that test method; `design.md` names its file. Left in place, it fails the red gate as a `regression` once your architect re-stubs the method, and fails `green` once the method works. Replace it with tests for the method's real behavior, each named with your own criterion IDs (DFS-003.L-2).

## What not to do

- Do not write a test that only restates the implementation ("calls method X"); assert on behavior and output.
- Do not test the port's real adapter from `application`-level tests, or vice versa.
- Do not touch a stub's production code — that is the implementer's job, done after your tests are frozen.
- Do not use `Thread.sleep` anywhere in a test (see "Testing asynchronous behavior without timing").
