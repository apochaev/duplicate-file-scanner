---
id: CCW-014
title: Recognise wrapped stubs in the red gate
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [tools/story-gate/]
blocked_reason:
created: 2026-10-11
updated: 2026-10-11
completed:
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

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-014.AC-<n>`.

## Scenarios

```gherkin
Feature: The red gate sees a stub through a wrapper

  Scenario: <placeholder; scenarios are written during refinement>
```

## Scope

**Touches:**
- `tools/story-gate/`: `Junit.java` and `TestResult.java` (keep the trace, recognise a wrapped stub), `RedCheck.java` if its messages change, and new `selftest/cases/CCW-014.AC-<n>-*` folders

**Out of scope:**
- The `wiring` archetype itself (CCW-013)
- `GreenCheck` and the other checks
- The stub convention (`UnsupportedOperationException("not implemented: <TICKET-ID>")`) itself

## Definition of done

- [ ] Every scenario above is a selftest case and `java tools/story-gate/StoryGate.java selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] Every existing selftest case still matches its `expected.txt` unchanged

## Open questions

- Matching rule: any `Caused by: java.lang.UnsupportedOperationException: not implemented:` line anywhere in the trace, or only the root (deepest) cause? What about a suppressed exception (`Suppressed:` lines)?
- Must the wrapped stub name this ticket (`not implemented: <ID>`), or is the prefix enough, as for an unwrapped stub today?
- What the verdict says for a wrapped stub, and for a wrapped non-stub failure (does `reason()` show the outer exception, the stub line, or both)?
- Which selftest cases: a wrapped stub passes; a wrapped other exception fails; a wrapped UOE without the prefix fails; an unwrapped non-stub still fails; a passing test is still "passed against stubs".
- Archetype: `none` (built by hand), as every earlier CCW ticket?
