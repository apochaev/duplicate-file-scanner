---
id: DFS-010
title: Build the story gate tools
status: draft
type: feature
scope: [build]
priority: high
depends_on: []
touches: [tools/story-gate]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-010: Build the story gate tools

## Story

As the story pipeline's orchestrator
I want deterministic checks that return a compact verdict
So that gates never depend on an agent's judgement, and no agent has to read raw build output

## Context

The gates from D2 and D7, written as Java source-launch programs (D10) in `tools/story-gate/` and run as `java tools/story-gate/StoryGate.java <check> <ID>`. Each check prints a verdict of about ten lines and sets an exit code. Multi-file source launch (JDK 22 and later) lets the code split into small files. The launcher uses `$JAVA_HOME`'s `java`, because the older JDKs on the path cannot launch multi-file programs. The JDK has no JSON parser, so plan handling uses a small hand-written reader and validator.

Rule for the whole pipeline: gates are scripts, and an agent reads only a compact verdict. Tokens are spent on what enters an agent's context, not on running tests.

Checks:
- **red:** run the tests and parse the JUnit XML. Every test must fail with the stubs' not-implemented exception. Anything else is reported by test name and reason.
- **green:** the build and test verdict, with failing test names and a short reason, never stack traces.
- **disabled:** no `@Disabled`; a skipped test must come from a platform `Assumptions` check.
- **freeze:** no test file changed since the test-phase commit (`git diff`).
- **touches:** every changed file is inside the ticket's `touches`, its story folder, or its tests.
- **traceability:** every Gherkin scenario title in the ticket has a test with the matching `@DisplayName`, and appears in `test-plan.md`.
- **consistency:** the ticket status matches the plan, and `plan.json` parses.

## Scenarios

```gherkin
Feature: Story gate checks

  Scenario: The red gate passes when every test fails for the right reason
    Given tests that all fail with the stubs' not-implemented exception
    When the red check runs
    Then the verdict is a pass and the exit code is 0

  Scenario: A test that passes against the stubs fails the red gate
    Given a test that passes against the stubs
    When the red check runs
    Then the verdict names the test with the reason "passed against stubs" and the exit code is non-zero

  Scenario: A test that fails for another reason fails the red gate
    Given a test that fails with a NullPointerException
    When the red check runs
    Then the verdict names the test and the exception type

  Scenario: The verdict stays compact
    Given 100 failing tests
    When any check runs
    Then the verdict is about ten lines, with the remainder summarised by a count

  Scenario: The green check reports failures without stack traces
    Given a failing build
    When the green check runs
    Then the verdict lists the failing test names and a short reason and contains no stack trace

  Scenario: Disabled tests are rejected but platform skips are allowed
    Given one test with `@Disabled` and one skipped by an `Assumptions` check
    When the disabled check runs
    Then only the `@Disabled` test is reported

  Scenario: A changed test file fails the freeze check
    Given a test file changed after the test-phase commit
    When the freeze check runs
    Then the verdict names the file

  Scenario: A file outside the allowed paths fails the touches check
    Given a changed file outside the ticket's touches, its story folder and its tests
    When the touches check runs
    Then the verdict names the file

  Scenario: A scenario without a matching test fails the traceability check
    Given a Gherkin scenario title with no test carrying that `@DisplayName`
    When the traceability check runs
    Then the verdict names the scenario

  Scenario: An invalid plan fails the consistency check
    Given a `plan.json` that does not parse, or a ticket status that disagrees with the plan
    When the consistency check runs
    Then the verdict says which

  Scenario: An old JDK gives a clear error
    Given a `java` older than JDK 22 on the path
    When the launcher runs
    Then it uses `$JAVA_HOME`'s `java`, or reports that JDK 22 or later is required
```

## Scope

**Touches:**
- `tools/story-gate/`: the launcher and the checks (new)

**Out of scope:**
- The agents and the `/story` skill that call the gates (DFS-011, DFS-012)
- A Gradle subproject with JUnit tests for the tools (a later ticket, if they grow)
- Mutation testing (parked in the design log)

## Definition of done

- [ ] Every scenario above is demonstrated on fixture inputs, with the evidence recorded in this ticket
- [ ] Each check runs through `java tools/story-gate/StoryGate.java <check> <ID>` and prints a verdict of about ten lines
- [ ] The launcher works from Git Bash and PowerShell on this machine
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Scenarios are a first draft derived from the design log only; review and complete them.
- Stub convention: which exact exception and message does the red gate match (shared with DFS-009)?
- How do `touches` entries (packages or paths) map to files for the touches check?
- The freeze check needs the test-phase commit, which lives in `plan.json`. How much JSON reading does the hand-written reader need beyond validation?
- Traceability convention: must `@DisplayName` equal the scenario title exactly, or is a looser match allowed?
- Where do the JUnit XML reports live in this multi-module build, and how does the red gate select the right module?
- How are Unicode paths handled in verdicts on Windows consoles?
- The tools need tests of their own, since they are the pipeline's safety net. When and how (a later Gradle subproject)?
