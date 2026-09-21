---
id: DFS-010
title: Build the story gate tools
status: ready
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

The gates from D2 and D7, written as Java source-launch programs (D10) in `tools/story-gate/` and run as `java tools/story-gate/StoryGate.java <check> <ID>`. Each check prints a verdict of about ten lines and sets an exit code. Multi-file source launch (JDK 22 and later) lets the code split into small files. The launcher uses `$JAVA_HOME`'s `java`, because the older JDKs on the path cannot launch multi-file programs. The JDK has no JSON parser, so plan handling uses a small but complete hand-written JSON parser (one file, about 150 lines).

Rule for the whole pipeline: gates are scripts, and an agent reads only a compact verdict. Tokens are spent on what enters an agent's context, not on running tests.

Checks:
- **red:** run `./gradlew cleanTest test --continue` and parse the JUnit XML of every module. Story tests (display name starting with the ticket ID) must each fail with the stubs' not-implemented exception, and every other test must pass. Anything else is reported by test name and reason.
- **green:** the build and test verdict, with failing test names and a short reason, never stack traces.
- **disabled:** no `@Disabled`; a skipped test must come from a platform `Assumptions` check.
- **freeze:** no test file changed since the test-phase commit (`git diff`).
- **touches:** every changed file is inside the ticket's `touches`, its story folder, or its tests.
- **traceability:** the chain criterion, scenario, test holds. Every criterion has at least one tagged scenario, every scenario carries at least one `@AC-n` tag naming an existing criterion, and every criterion ID (`<TICKET-ID>.AC-<n>`) appears at the start of at least one test's display name in the XML reports and in `test-plan.md`.
- **stubs:** no `not implemented:` marker remains in production source.
- **consistency:** the ticket status matches the plan, and `plan.json` parses.

**Decided (2026-09-21): the stub convention.** A stub throws `new UnsupportedOperationException("not implemented: <TICKET-ID>")`. The red check accepts a failure only when the exception type is `java.lang.UnsupportedOperationException` and the message starts with `not implemented:`. The prefix matters because real code also throws `UnsupportedOperationException` (immutable collections, which the scanner's own immutability tests exercise), so the type alone could mistake a broken test for a stub. The `stubs` check greps production source for the marker at the end, proving every stub was replaced. The architect's template and prompt use the same convention (DFS-009, DFS-011). Rejected: a dedicated exception class (it would live in production source and stay unused afterwards); the type alone (the immutable-collection trap); stack-trace inspection against `design.md` (the most complex, and it couples the gate to design parsing).

**Decided (2026-09-21): traceability by acceptance-criterion ID (D11).** Every acceptance criterion has an ID of the form `<TICKET-ID>.AC-<n>` (for example `DFS-011.AC-1`). Scenarios carry `@AC-n` tags. A test's `@DisplayName` starts with the ID(s) it proves, followed by a readable title, for example `@DisplayName("DFS-001.AC-3 Files of equal size form one candidate group")`. The traceability check reads the JUnit XML, where the display name is the test case name, and matches by ID, so retitling a scenario does not break the link. One criterion may have several tests, and a test may name several criteria. The check also runs at the end of the test-author phase, off the XML the red gate produced, so a missing criterion is caught before the implementer starts. The ticket template, the skill (DFS-008), the archetype (DFS-009) and the test author (DFS-011) follow the same convention.

**Decided (2026-09-21): how `touches` maps to files.** An entry is a path if it contains a slash or already exists at the repository root; otherwise a lowercase dotted name is a Java package, mapped to `<module>/src/{main,test}/java/<package as path>/**`, recursively, in whichever module has it. Tests of a touched package and the ticket's own story folder are always allowed. Everything else must be listed as an explicit path: `module-info.java` (a new exported package needs an `exports` line, and the file sits outside every package folder), test resources such as fixtures, and build files. The rule is a heuristic, but its failure is loud: a changed file that no entry covers is named by the check, so a misread entry cannot let an edit slip through. `validate` (DFS-008) gives an informational note for entries that resolve to nothing. Rejected: paths only (verbose, hard-codes the module layout into every ticket, and means rewriting the existing tickets); typed entries such as `pkg:` and `path:` (unambiguous, but noisier, and the same rewrite).

**Decided (2026-09-21): a complete small JSON parser.** One file of about 150 lines parses any valid JSON into maps, lists, strings, numbers, booleans and null. The consistency check uses it to validate `plan.json` and compare statuses, and the freeze check reads the test phase's `commit` through it. Rejected: the validator plus regex extraction (fragile: key order, nesting, or a brace inside a string can return the wrong commit, and a wrong value could make the freeze check pass silently); passing the commit as an argument (the consistency check needs the parser anyway, and the value then depends on the orchestrator passing the right one); a JSON library (breaks the JDK-only reason for D10). The parser gets its own tests when the tools get a test project.

**Decided (2026-09-21): story tests are identified by the ID prefix.** The red check runs `./gradlew cleanTest test --continue` and reads `*/build/test-results/test/TEST-*.xml` from every module, so no module selection is needed. `cleanTest` clears earlier results, so stale reports cannot be mistaken for new ones. Tests whose display name starts with `<TICKET-ID>.AC-` are the story's tests and must each fail with a stub's not-implemented exception. Every other test must pass, which also catches a stub or a change that breaks existing code. Gradle's own output is captured by the tool and never shown to an agent. If the suite grows, the run can later be narrowed to the touched modules. Rejected: running only the touched modules and treating every test there as a story test (pre-existing tests would violate the rule, and a change in one module can break another); identifying story tests by git (a changed file also holds older tests, and it ignores the display-name convention).

**Decided (2026-09-21): UTF-8 by construction.** Verdicts are written through an explicit UTF-8 stream that the tool creates itself, independent of console settings, and git is called with `-z` (raw, NUL-separated paths) and its output decoded as UTF-8. This keeps non-ASCII names exact in every verdict, and stops git's default quoting (octal escapes) from mangling paths in the touches and freeze checks. Someone running a check by hand in a legacy console may see garbled non-ASCII characters; that is cosmetic. Rejected: ASCII-escaped output (unreadable for people, and agents must interpret the escapes); leaving the defaults (`?` substitution and git's quoting fail silently, and the fixtures contain exactly such names).

**Decided (2026-09-21): a fixture-driven self-test built into the tools.** `java tools/story-gate/StoryGate.java selftest` runs each check on small fixture cases and compares each verdict with an expected file. Each check is split into a thin runner (which runs Gradle or git) and a core that evaluates plain inputs. The fixtures feed the cores pre-made JUnit XML, sample plans, sample tickets, and a temporary git repository that the self-test creates and modifies. The cases are the evidence for the definition of done, and each maps to a scenario. The orchestrator can run `selftest` as a pre-flight before trusting the gates. The cases translate directly if the tools are later promoted to a Gradle subproject with JUnit. Rejected: deferring tests to a follow-up (the safety net would ship unverified); a Gradle subproject with JUnit now (it couples the tools to the product build, which D10 avoided, and a bug in the gate tests could break the `./gradlew build` that the green gate relies on).

## Acceptance criteria

The full ID of each criterion is `DFS-010.AC-<n>`.

- **AC-1** The red check passes only when every story test (display name starting with the ticket ID) fails with `UnsupportedOperationException("not implemented: …")` and every other test passes, and otherwise names each offending test with its reason.
- **AC-2** The stubs check fails while any `not implemented:` marker remains in production source.
- **AC-3** Every check prints a verdict of about ten lines, summarising the remainder by a count.
- **AC-4** The green check reports failing test names with a short reason and never a stack trace.
- **AC-5** The disabled check rejects `@Disabled` tests and allows skips from platform `Assumptions` checks.
- **AC-6** The freeze check fails when a test file has changed since the test-phase commit.
- **AC-7** The touches check fails when a changed file is outside the ticket's touches (paths, and Java packages mapped to their source and test folders), its story folder and its tests.
- **AC-8** The traceability check enforces the chain criterion, scenario, test by ID, and also runs at the end of the test-author phase.
- **AC-9** The consistency check fails when `plan.json` does not parse or the ticket status disagrees with the plan.
- **AC-10** The launcher uses `$JAVA_HOME`'s `java` and reports clearly when JDK 22 or later is not available.
- **AC-11** Verdicts report non-ASCII file and test names exactly, and the touches and freeze checks see paths with non-ASCII names correctly.
- **AC-12** `selftest` runs every check on fixture cases and passes only when each verdict matches its expected file, with each scenario in this ticket covered by a case.

## Scenarios

```gherkin
Feature: Story gate checks

  @AC-1
  Scenario: The red gate passes when every test fails for the right reason
    Given tests that all fail with `UnsupportedOperationException` whose message starts with `not implemented:`
    When the red check runs
    Then the verdict is a pass and the exit code is 0

  @AC-1
  Scenario: A test that passes against the stubs fails the red gate
    Given a test that passes against the stubs
    When the red check runs
    Then the verdict names the test with the reason "passed against stubs" and the exit code is non-zero

  @AC-1
  Scenario: A test that fails for another reason fails the red gate
    Given a test that fails with a NullPointerException
    When the red check runs
    Then the verdict names the test and the exception type

  @AC-1
  Scenario: An unrelated UnsupportedOperationException is not mistaken for a stub
    Given a test that fails with an `UnsupportedOperationException` whose message does not start with `not implemented:`
    When the red check runs
    Then the verdict names the test and reports that the failure did not come from a stub

  @AC-1
  Scenario: Existing tests must still pass during the red phase
    Given a story test failing with a stub and an existing test that fails
    When the red check runs
    Then the verdict names the existing test as a regression

  @AC-1
  Scenario: Only story tests are held to the stub rule
    Given existing tests that pass and story tests named with the ticket ID that fail with stubs
    When the red check runs
    Then the verdict is a pass

  @AC-1
  Scenario: Stale reports are ignored
    Given XML reports left over from an earlier run
    When the red check runs
    Then it clears earlier results first and evaluates only the fresh run

  @AC-2
  Scenario: Leftover stubs fail the stubs check
    Given a production source file that still contains `not implemented:`
    When the stubs check runs
    Then the verdict names the file

  @AC-2
  Scenario: No leftover stubs passes the stubs check
    Given production source with no `not implemented:` marker
    When the stubs check runs
    Then the verdict is a pass

  @AC-3
  Scenario: The verdict stays compact
    Given 100 failing tests
    When any check runs
    Then the verdict is about ten lines, with the remainder summarised by a count

  @AC-4
  Scenario: The green check reports failures without stack traces
    Given a failing build
    When the green check runs
    Then the verdict lists the failing test names and a short reason and contains no stack trace

  @AC-5
  Scenario: Disabled tests are rejected but platform skips are allowed
    Given one test with `@Disabled` and one skipped by an `Assumptions` check
    When the disabled check runs
    Then only the `@Disabled` test is reported

  @AC-6
  Scenario: A changed test file fails the freeze check
    Given a test file changed after the test-phase commit
    When the freeze check runs
    Then the verdict names the file

  @AC-7
  Scenario: A file outside the allowed paths fails the touches check
    Given a changed file outside the ticket's touches, its story folder and its tests
    When the touches check runs
    Then the verdict names the file

  @AC-7
  Scenario: A package entry covers its sources and tests
    Given a touches entry `us.pochaev.dfs.scanner.application` and changed files under that package in `src/main/java` and `src/test/java`
    When the touches check runs
    Then no file is reported

  @AC-7
  Scenario: A package entry does not cover module-info.java
    Given only package entries and a changed `module-info.java`
    When the touches check runs
    Then the verdict names `module-info.java` and says it must be listed as a path

  @AC-7
  Scenario: A path entry covers the files beneath it
    Given a touches entry `tools/story-gate` and a changed file `tools/story-gate/StoryGate.java`
    When the touches check runs
    Then no file is reported

  @AC-7
  Scenario: A root file is recognised as a path
    Given a touches entry `CLAUDE.md` that exists at the repository root and a change to it
    When the touches check runs
    Then no file is reported

  @AC-8
  Scenario: An acceptance criterion without a test fails the traceability check
    Given an acceptance criterion ID that no test's display name starts with
    When the traceability check runs
    Then the verdict names the criterion

  @AC-8
  Scenario: Retitling a scenario does not break traceability
    Given a test whose display name starts with `DFS-001.AC-3` and a scenario retitled in the ticket
    When the traceability check runs
    Then the criterion is still matched by its ID

  @AC-8
  Scenario: A scenario without a criterion tag fails the traceability check
    Given a scenario with no `@AC-n` tag, or a tag naming a criterion that does not exist
    When the traceability check runs
    Then the verdict names the scenario

  @AC-8
  Scenario: A criterion without a scenario fails the traceability check
    Given an acceptance criterion that no scenario is tagged with
    When the traceability check runs
    Then the verdict names the criterion

  @AC-8
  Scenario: A missing criterion is caught at the end of the test phase
    Given the red check has produced its XML reports and one criterion has no test
    When the test-author phase gate runs
    Then the phase fails before the implementer starts

  @AC-9
  Scenario: An invalid plan fails the consistency check
    Given a `plan.json` that does not parse, or a ticket status that disagrees with the plan
    When the consistency check runs
    Then the verdict says which

  @AC-9
  Scenario: Valid plans parse whatever their layout
    Given a `plan.json` with keys in a different order, extra whitespace, nested objects and a string containing braces
    When the consistency check runs
    Then it parses and compares the statuses correctly

  @AC-6
  Scenario: The freeze check reads its commit from the plan
    Given a `plan.json` whose `tests` phase records a commit
    When the freeze check runs
    Then it compares the working tree against that commit and does not need the commit passed in

  @AC-10
  Scenario: An old JDK gives a clear error
    Given a `java` older than JDK 22 on the path
    When the launcher runs
    Then it uses `$JAVA_HOME`'s `java`, or reports that JDK 22 or later is required

  @AC-11
  Scenario: A Unicode file name is reported exactly
    Given a changed file whose name contains characters outside the Windows ANSI code page
    When the touches check reports it
    Then the verdict shows the name exactly, with no substituted characters

  @AC-11
  Scenario: Git's quoting does not hide a changed file
    Given a changed file with a non-ASCII name that lies outside the ticket's touches
    When the touches check runs
    Then the file is reported, and it is not missed because of git's path quoting

  @AC-12
  Scenario: The self-test passes when every verdict matches
    Given fixture cases with expected verdict files
    When `selftest` runs
    Then every case matches its expected verdict and the exit code is 0

  @AC-12
  Scenario: The self-test fails when a verdict changes
    Given a fixture case whose check now produces a different verdict
    When `selftest` runs
    Then the case is named with a short difference and the exit code is non-zero

  @AC-12
  Scenario: The self-test covers the ticket's scenarios
    Given the scenarios of this ticket
    When the fixture cases are listed
    Then each scenario has at least one case, named after its criterion ID
```

## Scope

**Touches:**
- `tools/story-gate/`: the launcher, the checks, the self-test and its fixture cases (new)

**Out of scope:**
- The agents and the `/story` skill that call the gates (DFS-011, DFS-012)
- A Gradle subproject with JUnit tests for the tools (a later ticket, if they grow; the fixture cases translate directly)
- Mutation testing (parked in the design log)

## Definition of done

- [ ] `selftest` passes and covers every scenario of this ticket through its fixture cases
- [ ] Each check runs through `java tools/story-gate/StoryGate.java <check> <ID>` and prints a verdict of about ten lines
- [ ] The launcher works from Git Bash and PowerShell on this machine
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
