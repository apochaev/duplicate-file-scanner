---
id: CCW-014
title: Recognise wrapped stubs in the red gate
status: done
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [tools/story-gate/]
blocked_reason:
created: 2026-10-11
updated: 2026-10-11
completed: 2026-10-11
---

# CCW-014: Recognise wrapped stubs in the red gate

## Story

As the person running tickets through the story pipeline
I want the red gate to accept a story test that fails because of an architect's stub even when a framework wraps the stub's exception
So that composition-root tickets (Spring `@Configuration`, an `ApplicationRunner`) can go through `/story run` with honest Spring-level tests, as the `wiring` archetype (CCW-013) needs

## Context

Split out of CCW-013 (its decisions 2 and 3): `SelfTest.coverage` requires every scenario of a ticket that has a selftest case to be claimed by one, so a gate change cannot share a ticket with read-only archetype scenarios.

How the gate decides today (read from `tools/story-gate`):

- `RedCheck.storyProblem` accepts a failed story test only when `TestResult.failedWithStub()` holds: `failureType` is exactly `java.lang.UnsupportedOperationException` (`TestResult.STUB_EXCEPTION`) and `failureMessage` starts with `not implemented:` (`STUB_PREFIX`). Any other failure is reported as "failed with <reason>"; a UOE without the prefix as "did not fail from a stub".
- `Junit.testCase` takes `failureType` from the `<failure>`/`<error>` element's `type` attribute and `failureMessage` from its `message` attribute (or the first line of the element's text); the rest of the element's text, the full stack trace including its `Caused by:` lines, is read but not kept.
- `TestPhaseGate` (the `test-phase` composite) runs `RedCheck`, so it inherits whatever `RedCheck` accepts. `GreenCheck` is unaffected: it needs real passes.

Why it matters: Spring wraps a stub. A stubbed `@Bean` method fails context startup with `BeanCreationException` (or `UnsatisfiedDependencyException`), and a stubbed runner makes `SpringApplication.run` throw `IllegalStateException("Failed to execute ApplicationRunner")`; the stub's `UnsupportedOperationException: not implemented: <ID>` appears only as a `Caused by:` line in the trace. DFS-020 (built by hand) had three such Spring-level tests. Other wrappers behave the same (`ExecutionException` from a future).

Selftest cases can carry real JUnit XML reports (`"reports": "<folder>"` in `case.json`, as `AC-4-green-no-traces` does), so a case can hold a `<failure>` whose text has a `Caused by:` stub line; no harness change is needed for that. Case folders for this ticket are named `CCW-014.AC-<n>-...`.

**Decisions (refinement, 2026-10-11):**

1. **Root cause only.** A failed story test also counts as a stub failure when the **last** `Caused by:` line at the start of a line (not indented) in the `<failure>`/`<error>` element's text is `Caused by: java.lang.UnsupportedOperationException: not implemented:...`. Tab-indented `Suppressed:` blocks, and the indented `Caused by:` lines inside them, are ignored. A stub's exception has no cause, so when a stub is involved it is always the deepest exception: this is today's rule ("the failure is the stub") carried one or more levels down. The existing top-level rule (`type` attribute plus `message` prefix) stays as it is.
2. **The prefix is enough.** A wrapped stub needs only `not implemented:`, exactly like an unwrapped one (`TestResult.STUB_PREFIX`); the ticket ID after it is not checked. The wrapped rule is the existing rule applied to the root cause, nothing more. A leftover stub of another ticket is kept out of production by `StubsCheck`. Requiring the ID for all stubs would change what every ticket relies on and re-bless existing cases such as `AC-1-red-passes`; that is a separate decision for a separate ticket if ever wanted.
3. **Failure reasons name the root cause.** `TestResult.reason()`, used by `RedCheck` (story problems and regressions) and `GreenCheck`, gives the outer exception as today and, when the trace has an unindented `Caused by:`, gives `<OuterSimpleType>; root cause <SimpleType>: <first line of its message>` instead, capped by the existing 100-code-point truncation. Found while implementing: with the wrapper's own message kept, `Verdict.MAX_WIDTH` (160) cut every Spring case's root-cause message short, since a wrapper's message is long boilerplate ("Failed to execute ApplicationRunner"); so a wrapped failure drops the outer message and keeps only its type (user's choice, 2026-10-11). Unwrapped failures read exactly as before. No existing selftest fixture contains a `Caused by:` line, so no existing `expected.txt` changes. `GreenCheck`'s logic is untouched; its output gains the root cause through `reason()`.
4. **Two more cases: an unrelated wrapped UOE, and the `test-phase` composite.** A wrapped `UnsupportedOperationException` without the prefix (an immutable collection's `read-only view`) must not count, mirroring the existing unwrapped case `AC-1-unrelated-uoe`. And `/story run`'s tests phase calls `test-phase` (`TestPhaseGate`: red, then traceability on the same reports), not `red` alone, so one case runs `test-phase` on wrapped stubs, using `selftest/shared`'s DFS-001 `ticket.md` (AC-1 to AC-3) and `test-plan.md`, modelled on `AC-8-phase-gate-passes`. Unwrapped non-stub failures and tests passing against stubs stay covered by the existing cases `AC-1-other-failure` and `AC-1-passed-against-stubs`, kept unchanged.
5. **Archetype `none`.** Built by hand, like CCW-003 and CCW-009 (the earlier gate tickets) and every other CCW ticket: `tools/story-gate` is proven by its own `selftest`, not by Gradle tests against architect stubs. Each done-item is ticked with the `selftest` output as evidence.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-014.AC-<n>`.

- **AC-1** A story test whose failure trace's last unindented `Caused by:` line is `Caused by: java.lang.UnsupportedOperationException: not implemented:...` counts as failing from a stub, at any wrapping depth, so `red` passes when every story test fails that way (decision 1).
- **AC-2** A story test whose failure is wrapped but whose last unindented `Caused by:` is any other exception still fails `red` as "failed with <reason>" (decision 1).
- **AC-3** A stub that appears only inside an indented `Suppressed:` block, not as the root cause, does not count, so `red` fails for that test (decision 1).
- **AC-4** A wrapped stub is recognised by the `not implemented:` prefix alone, whatever ticket ID follows it, the same as an unwrapped stub (decision 2).
- **AC-5** A failure reason (in `red` and in `green`) for a test whose trace has an unindented `Caused by:` reads `<OuterType>; root cause <RootType>: <message>`; a failure without one reads exactly as before (decision 3).

## Scenarios

```gherkin
Feature: The red gate sees a stub through a wrapper

  # check: mechanical (selftest case CCW-014.AC-1-wrapped-once, a real JUnit report)
  @AC-1
  Scenario: A stub wrapped once by a runner failure passes red
    Given a JUnit report where story test "DFS-001.AC-1 Runs the scan" failed with type "java.lang.IllegalStateException", message "Failed to execute ApplicationRunner", and a trace ending in the unindented line "Caused by: java.lang.UnsupportedOperationException: not implemented: DFS-001"
    When the red check runs for DFS-001
    Then it passes

  # check: mechanical (selftest case CCW-014.AC-1-wrapped-twice)
  @AC-1
  Scenario: A stub wrapped twice by bean creation passes red
    Given a JUnit report where story test "DFS-001.AC-1 Context starts" failed with type "org.springframework.beans.factory.BeanCreationException", whose trace has "Caused by: org.springframework.beans.BeanInstantiationException: Failed to instantiate" and then the last unindented line "Caused by: java.lang.UnsupportedOperationException: not implemented: DFS-001"
    When the red check runs for DFS-001
    Then it passes

  # check: mechanical (selftest case CCW-014.AC-1-wrapped-unrelated-uoe)
  @AC-1
  Scenario: A wrapped UnsupportedOperationException that is not a stub fails red
    Given a JUnit report where story test "DFS-001.AC-1 Runs the scan" failed with type "java.lang.IllegalStateException", message "Failed to execute ApplicationRunner", and a trace whose last unindented line is "Caused by: java.lang.UnsupportedOperationException: read-only view"
    When the red check runs for DFS-001
    Then it fails, naming that test as "failed with IllegalStateException; root cause UnsupportedOperationException: read-only view"

  # check: mechanical (selftest case CCW-014.AC-1-test-phase-wrapped, with selftest/shared's DFS-001 ticket.md and test-plan.md)
  @AC-1
  Scenario: The test-phase gate passes on wrapped stubs
    Given a JUnit report where story tests "DFS-001.AC-1 Equal sizes", "DFS-001.AC-2 Different sizes" and "DFS-001.AC-3 Empty files are excluded" each failed with type "java.lang.IllegalStateException" and a trace whose last unindented line is "Caused by: java.lang.UnsupportedOperationException: not implemented: DFS-001"
    When the test-phase check runs for DFS-001 with the shared ticket and test plan
    Then it passes, red and traceability both

  # check: mechanical (selftest case CCW-014.AC-2-wrapped-other)
  @AC-2 @AC-5
  Scenario: A wrapped failure that is not a stub fails red
    Given a JUnit report where story test "DFS-001.AC-1 Runs the scan" failed with type "java.lang.IllegalStateException", whose trace's last unindented line is "Caused by: java.io.IOException: disk gone"
    When the red check runs for DFS-001
    Then it fails, naming that test as "failed with IllegalStateException; root cause IOException: disk gone"

  # check: mechanical (selftest case CCW-014.AC-3-stub-only-suppressed)
  @AC-3
  Scenario: A stub only in a suppressed block does not count
    Given a JUnit report where story test "DFS-001.AC-1 Runs the scan" failed with type "java.lang.AssertionError", message "expected 0 but was 1", and a trace whose only stub line is the tab-indented "	Suppressed: java.lang.UnsupportedOperationException: not implemented: DFS-001"
    When the red check runs for DFS-001
    Then it fails, naming that test as "failed with AssertionError: expected 0 but was 1", with no root cause appended

  # check: mechanical (selftest case CCW-014.AC-5-green-root-cause)
  @AC-5
  Scenario: Green names the root cause of a wrapped failure
    Given a JUnit report where test "DFS-001.AC-1 Context starts" failed with type "java.lang.IllegalStateException", message "Failed to load ApplicationContext", and a trace whose last unindented line is "Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'StartScan'"
    When the green check runs for DFS-001
    Then it fails, naming that test with "IllegalStateException; root cause NoSuchBeanDefinitionException: No qualifying bean of type 'StartScan'"

  # check: mechanical (selftest case CCW-014.AC-4-other-ticket-id)
  @AC-4
  Scenario: A wrapped stub naming another ticket still counts
    Given a JUnit report where story test "DFS-001.AC-1 Runs the scan" failed with type "java.lang.IllegalStateException", whose trace's last unindented line is "Caused by: java.lang.UnsupportedOperationException: not implemented: DFS-017"
    When the red check runs for DFS-001
    Then it passes
```

## Scope

**Touches:**
- `tools/story-gate/`: `Junit.java` and `TestResult.java` (keep the trace, recognise a wrapped stub), `RedCheck.java` if its messages change, and new `selftest/cases/CCW-014.AC-<n>-*` folders

**Out of scope:**
- The `wiring` archetype itself (CCW-013)
- `GreenCheck`'s logic and the other checks (`GreenCheck`'s output gains the root cause only through `TestResult.reason()`, decision 3)
- The stub convention (`UnsupportedOperationException("not implemented: <TICKET-ID>")`) itself

## Definition of done

- [x] Every scenario above is a selftest case and `java tools/story-gate/StoryGate.java selftest` passes: "PASS selftest: 92 cases match their expected verdicts; every scenario of CCW-003, CCW-009, CCW-014 is covered" (84 existing + 8 `CCW-014.AC-*` cases, each a real JUnit report under its `reports/`)
- [x] No files changed outside the paths listed under Touches: `git status` shows only `tools/story-gate/Junit.java`, `TestResult.java`, the 8 new `selftest/cases/CCW-014.*` folders, and this ticket
- [x] Every existing selftest case still matches its `expected.txt` unchanged: no existing `expected.txt` appears in `git status`, and selftest passed before the change (84 cases) and after it

## Open questions

None.
