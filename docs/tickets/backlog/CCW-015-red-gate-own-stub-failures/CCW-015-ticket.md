---
id: CCW-015
title: Make the red gate accept existing tests broken by the ticket's own stub
status: in-progress
type: feature
scope: []
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [tools/story-gate, .claude/agents/architect.md, .claude/archetypes/pipeline-stage/design.md.tmpl, .claude/archetypes/pipeline-stage/archetype.md, .claude/archetypes/use-case/design.md.tmpl, .claude/archetypes/use-case/archetype.md, .claude/archetypes/wiring/design.md.tmpl, .claude/archetypes/wiring/archetype.md]
blocked_reason:
created: 2026-10-14
updated: 2026-10-14
completed:
---

# CCW-015: Make the red gate accept existing tests broken by the ticket's own stub

## Story

As the person running tickets through the story pipeline
I want the red gate to accept existing tests that fail only because the current ticket's stub sits in their path
So that a ticket that reshapes a stage already wired into every scan can go through `/story run` without a manual override, while real regressions still block the tests phase

## Context

Found running DFS-026 (2026-10-14). DFS-026 replaces `FileIdentity.isSameFile` with `key` and stubs `HardLinkDetector.regroup` and `NioFileIdentity.key`. Every scan reaches the hard-link stage, so every existing test that produces a duplicate set hits the stub. At the tests phase, `test-phase` reported `FAIL red DFS-026: 44 problems`. All 44 were `regression:` lines, while the 9 story tests failed from the stub as intended. The 44 broke down like this:

- **20** fail with exactly `java.lang.UnsupportedOperationException: not implemented: DFS-026` (`HardLinkDetectorTest`'s DFS-004 tests, `ScanPipelineTest`, `StageDropReportingTest`, `ScanPipelineEndToEndTest`).
- **11** fail with another exception whose trace contains the stub. Examples: `ExecutionException` with the stub as root cause (`ScanPipelineTest` DFS-005.AC-18), and assertion failures whose message quotes the CLI's `error: scan failed: java.lang.UnsupportedOperationException: not implemented: DFS-026` (`ScanCommandTest`, `CliApplicationTest`, `ScanProgressSnapshotTest`).
- **13** fail with no trace of the stub, for example `expected: <3> but was: <0>`, `expected: <COMPLETED> but was: <FAILED>`, `IndexOutOfBoundsException` (`ScanCommandTest`, `ScannerFactoriesTest`, `ScanServiceRealThreadTest`, `ScanRunnerTest`). The scan failed on the stub, and the caller reduced that to a status, an exit code or an empty result.

Counterfactual: in a scratch copy of the worktree, the two stubs were replaced with a stand-in implementation. All 44 tests passed: cli 63 of 63, and in file-scanner only the story test DFS-026.AC-8 still failed. So the stub was the only cause. The DFS-026 design gate approved a narrow manual override instead (accept only failures that are exactly the stub exception), and the 24 indirect failures fall outside it.

How the gate decides today (read from `tools/story-gate`, after CCW-014):

- `RedCheck.evaluate`: a test whose display name starts with `<ID>.AC-` is a story test and must fail from a stub (`TestResult.failedWithStub()`: the failure itself, or its last unindented `Caused by:`, is `UnsupportedOperationException` with a message starting `not implemented:`). Every other failed test is a `regression:`, whatever its cause.
- `GreenCheck` at the implement phase needs every test to pass for real. So a pre-existing test the red gate lets through still has to pass once the stub is implemented.

A throwing stub cannot avoid this when an existing, wired stage is reshaped, so it will recur for any ticket of that kind, not only DFS-026.

**Decisions (refinement, 2026-10-14):**

1. **Both rules: the ticket's own stub automatically, a declaration for the rest.** An existing test (not a story test) whose failure, or its root cause as CCW-014 reads it, is the current ticket's stub is accepted without any declaration. An existing test that fails some other way is accepted only if the architect declared it in `design.md` as expected to break. Every other failure stays a `regression:`. The automatic rule covers the common case, a test that calls the stubbed class directly (DFS-026's 20). The declaration covers end-to-end and CLI tests, where the stub is reduced to a status, an exit code or an empty result that no gate can trace (DFS-026's 13, and those of the 11 that only quote the stub in a message). Declaring at design time puts the expected breakage in front of the user at the design gate. `GreenCheck` still proves every accepted test at the implement phase. Rejected: the automatic rule alone (DFS-026 would still have needed a manual override), the declaration alone (every reshape needs a list, even when the stub is plainly visible), and no change (rests on the orchestrator's judgement each time).
2. **Declared by test class, with a reason per class.** Each declaration names one test class (simple name, for example `ScanCommandTest`) and a one-line reason (for example "drives a full scan through the CLI; the scan fails on the stub"). The gate matches the class name only; the reason is for the human at the design gate, so an unexpected entry stands out. Any failure in a declared class is accepted. Only class names can be predicted honestly at design time, before tests exist; `GreenCheck` still needs every test in a declared class to pass at the implement phase, so a regression hidden there is caught late, never shipped. Rejected: class names without reasons (nothing to review at the gate), and single tests (display names are not known before the tests phase, and parameterized cases have generated names).
3. **The automatic rule requires the current ticket's ID.** An existing test's failure counts as the ticket's own stub only when the stub message is `not implemented: <current ID>` (the failure itself, or its root cause as CCW-014 reads it). A stub naming another ticket is not waived: it is a `regression:` unless the test's class is declared. The automatic rule waives the regression check without anyone declaring anything, so it is kept as narrow as possible, and the check makes "the ticket's own stub" literally true. Story tests keep CCW-014's rule (the prefix `not implemented:` alone), since they must fail from a stub anyway. Today `StubsCheck` and branch isolation already keep foreign stubs out of a story's tree, so this guards against a future gap rather than a known one. Rejected: the prefix alone, as CCW-014 does for story tests (one stub test everywhere, but the waiver would then rest on other checks).
4. **A fixed `## Expected breakage` section in `design.md`.** Each declaration is one bullet, `` - `ScanCommandTest`: <reason> ``. The gate reads only bullets under that exact heading, and only the backticked name at the start of each. "None." means nothing is declared. A missing or misspelled section declares nothing, so the gate can only get stricter, never looser. All three archetypes' `design.md.tmpl` (`pipeline-stage`, `use-case`, `wiring`) gain the section, and each `archetype.md` `version` goes up by one. The `architect` prompt says when to fill it: when a stub sits in the path of existing tests that cannot trace it. The design gate shows only `design.md`'s `## Summary`, so the prompt also has the Summary name the declared classes, which puts them in front of the user. Rejected: a separate `expected-breakage.txt` (a second file for the gate to show, and it contradicts decision 1), and the section in `pipeline-stage` only (`use-case` and `wiring` can reshape a wired stage too). (Updated 2026-10-14, implementing: the architect prompt's read limit would have kept it from finding the tests to declare, so it now has one exception: it may search the test source trees for the stubbed types and their callers and read the tests found. A nested test class is matched by its outer class's simple name.)
5. **A passing verdict reports counts only, split by rule, on its summary line.** For example: `PASS red DFS-026: 9 story tests fail with stubs, 20 other tests fail on the ticket's stub, 24 fail in declared classes, 156 other tests pass`. It fits any number of failures, and the split shows how much rests on the declaration rather than the stub. A pass is read by the orchestrator, which needs only the outcome; the class-level picture was already in front of the user at the design gate, and `green` still proves every accepted test. A failing verdict is unchanged: one `regression:` line each, under the existing cap. Rejected: a line per declared class with its failure count (useful only for auditing declarations after the fact), and a line per accepted test (cut to 8 lines plus `... and N more` at DFS-026's scale).
6. **Archetype `none`.** Implemented by hand like CCW-001 to CCW-014: no archetype describes `tools/story-gate`'s single-file scripts and selftest cases, and running the red gate's own change through the red gate would be circular. Every scenario except AC-7's is a selftest case; the selftest case format gains a way to give `red` a `design.md`. (Updated 2026-10-14, implementing: `selftest` requires a case for every scenario of a ticket that has any, so AC-7 got one too, through a new `contains` case type that checks the instruction files in the repository for the required phrases.)

## Acceptance criteria

The full ID of each criterion is `CCW-015.AC-<n>`.

- **AC-1** An existing test (display name not starting with `<ID>.AC-`) whose failure, or its root cause, is `UnsupportedOperationException` with the message `not implemented: <current ID>` is accepted by `red` without any declaration (decisions 1 and 3).
- **AC-2** An existing test that fails some other way is accepted by `red` only when `design.md` declares its test class as expected to break (decisions 1 and 2).
- **AC-3** An existing test that fails neither from the current ticket's stub nor under a declaration is still reported as `regression:` (decision 1).
- **AC-4** A declaration names a class by its simple name and carries a reason; a failure in a declared class is accepted whatever the test, and a failure in a class of the same name in another package is accepted as well (decision 2).
- **AC-5** An existing test whose failure is a stub naming another ticket (`not implemented: <other ID>`) is reported as `regression:` unless its class is declared; a story test failing from such a stub is still accepted, as CCW-014 decided (decision 3).
- **AC-6** `red` reads declarations only from bullets under the `## Expected breakage` heading of the story's `design.md`, each starting with a backticked class name; a story folder with no `design.md`, a `design.md` with no such section, or a section reading "None." declares nothing, and none of these is an error (decision 4).
- **AC-7** The `architect` prompt and all three archetypes' `design.md.tmpl` ask for the `## Expected breakage` section, and the prompt has the `## Summary` name every declared class (decision 4).
- **AC-8** A passing `red` verdict's summary line gives, as separate counts, the story tests failing with stubs, the existing tests accepted on the ticket's own stub, the existing tests accepted under a declaration, and the other tests that pass (decision 5).

## Scenarios

```gherkin
Feature: Red gate and the ticket's own stub in existing tests

  Background:
    Given the ticket under the red gate is DFS-026
    And the story test "DFS-026.AC-1 Hard links share one key" fails with
      java.lang.UnsupportedOperationException "not implemented: DFS-026"

  @AC-1 @AC-8
  Scenario: An existing test failing with the ticket's own stub is accepted
    Given the existing test HardLinkDetectorTest "DFS-004.AC-1 Hard links are grouped" fails with
      java.lang.UnsupportedOperationException "not implemented: DFS-026"
    And 3 other existing tests pass
    And design.md has no "## Expected breakage" section
    When red evaluates the results
    Then the verdict is "PASS red DFS-026: 1 story tests fail with stubs, 1 other tests fail on the ticket's stub, 0 fail in declared classes, 3 other tests pass"

  @AC-1
  Scenario: An existing test whose root cause is the ticket's own stub is accepted
    Given the existing test ScanPipelineTest "DFS-005.AC-18 Cancels mid-scan" fails with
      java.util.concurrent.ExecutionException whose last unindented "Caused by:" is
      java.lang.UnsupportedOperationException "not implemented: DFS-026"
    And design.md has no "## Expected breakage" section
    When red evaluates the results
    Then the verdict is PASS and counts 1 other test failing on the ticket's stub

  @AC-2 @AC-8
  Scenario: An assertion failure in a declared class is accepted
    Given design.md has a "## Expected breakage" section with the bullet
      - `ScanCommandTest`: drives a full scan through the CLI; the scan fails on the stub
    And the existing test us.pochaev.dfs.cli.ScanCommandTest "exits 0 on duplicates" fails with
      org.opentest4j.AssertionFailedError "expected: <0> but was: <1>"
    And 3 other existing tests pass
    When red evaluates the results
    Then the verdict is "PASS red DFS-026: 1 story tests fail with stubs, 0 other tests fail on the ticket's stub, 1 fail in declared classes, 3 other tests pass"

  @AC-4
  Scenario: A declaration matches by simple name in any package and covers every test in the class
    Given design.md declares `ScanCommandTest` under "## Expected breakage"
    And the existing test us.pochaev.dfs.cli.ScanCommandTest "exits 0 on duplicates" fails with
      org.opentest4j.AssertionFailedError "expected: <0> but was: <1>"
    And the existing test other.pkg.ScanCommandTest "prints a summary" fails with
      java.lang.IndexOutOfBoundsException "Index 0 out of bounds for length 0"
    When red evaluates the results
    Then the verdict is PASS and counts 2 failures in declared classes

  @AC-3
  Scenario: An undeclared assertion failure is still a regression
    Given design.md declares `ScanCommandTest` under "## Expected breakage"
    And the existing test us.pochaev.dfs.scanner.ScannerFactoriesTest "builds a working scanner" fails with
      org.opentest4j.AssertionFailedError "expected: <COMPLETED> but was: <FAILED>"
    When red evaluates the results
    Then the verdict is FAIL with one problem line starting "regression: " and naming ScannerFactoriesTest

  @AC-5
  Scenario: An existing test failing with another ticket's stub is a regression
    Given design.md has no "## Expected breakage" section
    And the existing test HardLinkDetectorTest "DFS-004.AC-1 Hard links are grouped" fails with
      java.lang.UnsupportedOperationException "not implemented: DFS-999"
    When red evaluates the results
    Then the verdict is FAIL with one problem line starting "regression: " and naming HardLinkDetectorTest

  @AC-5
  Scenario: A story test failing with another ticket's stub is still accepted
    Given the story test "DFS-026.AC-2 Plain copies get distinct keys" fails with
      java.lang.UnsupportedOperationException "not implemented: DFS-999"
    When red evaluates the results
    Then the verdict is "PASS red DFS-026: 2 story tests fail with stubs, 0 other tests fail on the ticket's stub, 0 fail in declared classes, 0 other tests pass"

  @AC-6
  Scenario: A section reading None declares nothing
    Given design.md has a "## Expected breakage" section whose body is "None."
    And the existing test ScanCommandTest "exits 0 on duplicates" fails with
      org.opentest4j.AssertionFailedError "expected: <0> but was: <1>"
    When red evaluates the results
    Then the verdict is FAIL with one problem line starting "regression: " and naming ScanCommandTest

  @AC-6
  Scenario: A class named outside the section declares nothing
    Given design.md has no "## Expected breakage" section
    And design.md names `ScanCommandTest` in a bullet under "## Open design notes"
    And design.md has a section "## Expected Breakages" listing `ScanCommandTest`
    And the existing test ScanCommandTest "exits 0 on duplicates" fails with
      org.opentest4j.AssertionFailedError "expected: <0> but was: <1>"
    When red evaluates the results
    Then the verdict is FAIL with one problem line starting "regression: " and naming ScanCommandTest

  @AC-6
  Scenario: A missing design.md declares nothing and is not an error
    Given the story folder has no design.md
    And the existing test HardLinkDetectorTest "DFS-004.AC-1 Hard links are grouped" fails with
      java.lang.UnsupportedOperationException "not implemented: DFS-026"
    When red evaluates the results
    Then the verdict is PASS and counts 1 other test failing on the ticket's stub

  @AC-7
  Scenario: The architect is asked for the section and to name it in the Summary
    Given the architect prompt and the design.md.tmpl of pipeline-stage, use-case and wiring
    When they are read
    Then each template has a "## Expected breakage" section whose instruction says to list one backticked
      simple class name and a one-line reason per bullet, or "None."
    And the prompt says to declare every existing test class a stub breaks without the stub in its trace
    And the prompt says the "## Summary" names every declared class
    And each of the three archetype.md files has its version raised by one
```

How each scenario is checked: every scenario tagged AC-1 to AC-6 and AC-8 is run mechanically as a selftest case (`selftest/cases/CCW-015.AC-<n>-...`, `check: red`). The AC-7 scenario was planned as a read-through, but is also run mechanically, by the `contains` case `CCW-015.AC-7-instructions`, which checks the four instruction files for the required phrases and the three `version` lines; reading them confirms the wording makes sense, which no phrase check can.

## Scope

**Touches:**
- `tools/story-gate`: `RedCheck` (and possibly `TestResult`), reading `design.md`, plus selftest cases `selftest/cases/CCW-015.AC-<n>-...`
- `.claude/agents/architect.md`: when and how to fill `## Expected breakage`, and naming the declared classes in `## Summary`
- `.claude/archetypes/{pipeline-stage,use-case,wiring}/design.md.tmpl`: the `## Expected breakage` section; each `archetype.md`: `version` up by one

**Out of scope:**
- Changing what counts as a story test's stub failure (CCW-014's rules stay).
- `GreenCheck`: every test must still pass for real at the implement phase.

## Definition of done

- [x] Every scenario above is a selftest case and `selftest` passes: `PASS selftest: 103 cases match their expected verdicts; every scenario of CCW-003, CCW-009, CCW-014, CCW-015 is covered`. The six earlier `red` PASS cases had their expected summary updated to the AC-8 format, nothing else.
- [x] No files changed outside the paths listed under Touches
- [x] The AC-7 scenario checked by reading `architect.md`, the three `design.md.tmpl` and the three `archetype.md` versions
- [x] Live replay of DFS-026 (its `plan.json` keeps only the totals, 44 = 20 + 11 + 13, so the per-test results are regenerated): in a scratch worktree at DFS-026's tests commit `64f5fc8` (stubs plus tests) with this ticket's `tools/story-gate` copied in, `red DFS-026` is run twice. First with DFS-026's `design.md` as committed (no `## Expected breakage` section): every `regression:` line is a test whose failure neither is nor has as root cause `not implemented: DFS-026`, and none of the 20 exact-stub failures appears. Then with a section declaring exactly the classes named on those `regression:` lines, each with a reason: the verdict is PASS with 9 story tests, and the own-stub and declared counts add up to 44. Both verdicts and the declared classes are recorded in this ticket. The scratch worktree is removed afterwards and nothing from it is committed.
  - Done 2026-10-14 in a detached worktree at `64f5fc8`. Run 1, DFS-026's `design.md` as committed: `FAIL red DFS-026: 23 problems`, every one a `regression:`. From the JUnit reports, with the gate's rule: 9 story failures, 21 failing on `not implemented: DFS-026` itself or as root cause (none of them among the 23), and 23 others in 6 classes: `ScanCommandTest` 15, `ScannerFactoriesTest` 3, `ScanRunnerTest` 2, `CliApplicationTest` 1, `ScanProgressSnapshotTest` 1, `ScanServiceRealThreadTest` 1. Run 2, with those 6 declared under `## Expected breakage`, each with a reason: `PASS red DFS-026: 9 story tests fail with stubs, 21 other tests fail on the ticket's stub, 23 fail in declared classes, 201 other tests pass (1 skipped)`; 21 + 23 = 44. Against DFS-026's breakdown (20 exact, 11 with the stub in the trace, 13 without): the automatic rule took the 20 plus 1 whose root cause is the stub; the other 10 only quote the stub in a message and needed declaring with the 13.

## Open questions

None.
