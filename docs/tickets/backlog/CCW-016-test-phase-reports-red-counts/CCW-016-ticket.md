---
id: CCW-016
title: Report the red counts when test-phase passes
status: in-progress
type: fix
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: low
depends_on: []
touches: [tools/story-gate, .claude/skills/story/SKILL.md]
blocked_reason:
created: 2026-10-19
updated: 2026-10-19
completed:
---

# CCW-016: Report the red counts when test-phase passes

## Story

As the story orchestrator recording a tests phase in `plan.json`
I want `test-phase` to print the red check's counts as well as traceability's line when both pass
So that the red gate's evidence (how many story tests fail on stubs, how many other tests the ticket's stub breaks, how many pass) is recorded from the gate run itself, without a second `red` run

## Context

From DFS-027.L-7 (proposed, 2026-10-19). `test-phase` is CCW-003's composite gate: `TestPhaseGate.run` (`tools/story-gate/TestPhaseGate.java:13-17`) runs `RedCheck`, returns its verdict if it is not PASS, and otherwise returns `TraceabilityCheck`'s verdict alone. `RedCheck`'s pass summary (`RedCheck.java:59`, "N story tests fail with stubs, …") is discarded, so a passing `test-phase` prints only one line, e.g. `PASS traceability DFS-001: 3 criteria traced through 3 scenarios to tests and test-plan.md` (the self-test case `selftest/cases/CCW-014.AC-1-test-phase-wrapped/expected.txt`). For DFS-027, the counts in `plan.json`'s tests run note ("47 story tests fail with stubs, 114 other tests fail on the ticket's stub, 0 in declared classes, 148 pass (2 skipped)") had to come from a separate `red` run.

`Verdict` (`Verdict.java`) is one status, check name, subject, summary and up to ten problem lines, rendered in at most `MAX_LINES` lines of `MAX_WIDTH` characters. `evaluate` (used by the self-tests through `SelfTestCase.java:84`) mirrors `run`.

**Decisions (refinement, 2026-10-19):**

1. **One `test-phase` verdict with a detail line per check.** When both checks pass, `test-phase` prints:

   ```
   PASS test-phase <ID>: red and traceability pass
   - red: <red's pass summary>
   - traceability: <traceability's pass summary>
   ```

   with exit code 0. `Verdict.pass` gains an overload taking detail lines, rendered as `- ` lines like a FAIL's problems. When either check fails, the output is that check's verdict alone, as today. Rejected: two full verdict lines (`PASS red …`, `PASS traceability …`; breaks the one-verdict shape, and red's line is about 151 characters for DFS-027's counts, close to the 160-character cut) and keeping traceability's header with red as a detail line (the header would still name a check other than the one run).
2. **The story skill records every passing gate's output.** Resume step 7 (`.claude/skills/story/SKILL.md`) gains one sentence: on a pass, the attempt's `runs` entry gets a `note` holding the check output's lines (summary and `- ` detail lines, joined with `; `), so a tests run carries red's counts and traceability's line in `plan.json`. This applies to every phase, not only `test-phase`. Rejected: changing only the tool (the counts would again be kept only if the orchestrator thought to, as in DFS-027).
3. **Archetype `none`**: built by hand, like CCW-014 and CCW-015. `story-gate` is tested by its own `selftest` cases (`case.json` plus `expected.txt`), not by JUnit tests with criterion IDs, so the pipeline's `red` and `traceability` gates cannot judge it. Rejected: any of the three archetypes (each assumes JUnit tracing in the scanner or CLI).

## Acceptance criteria

The full ID of each criterion is `CCW-016.AC-<n>`.

- **AC-1** When red and traceability both pass, `test-phase` exits 0 and prints exactly three lines: `PASS test-phase <ID>: red and traceability pass`, then `- red: ` followed by red's pass summary, then `- traceability: ` followed by traceability's pass summary (decision 1).
- **AC-2** When red fails, `test-phase` prints red's verdict alone and exits 1; when red passes and traceability fails, it prints traceability's verdict alone and exits 1 (decision 1, unchanged behaviour).
- **AC-3** The story skill's resume step 7 says that on a passing check the attempt's `runs` entry gets a `note` holding the check output's lines joined with `; ` (decision 2).

## Scenarios

```gherkin
Feature: test-phase reports both checks on a pass

  # AC-1 and AC-2 run mechanically as story-gate self-test cases; AC-3 is checked by reading the skill.

  @AC-1
  Scenario: Both checks pass
    Given the self-test case "AC-8-phase-gate-passes": every story test fails with the stub, and every criterion is traced
    When "story-gate selftest" runs it with check "test-phase"
    Then the output is exactly three lines:
      """
      PASS test-phase DFS-001: red and traceability pass
      - red: <red's pass summary for that case>
      - traceability: <traceability's pass summary for that case>
      """
    And the exit code is 0

  @AC-1
  Scenario: A wrapped test name still passes with both lines
    Given the self-test case "CCW-014.AC-1-test-phase-wrapped"
    When "story-gate selftest" runs it
    Then its first line is "PASS test-phase DFS-001: red and traceability pass"
    And its last line is "- traceability: 3 criteria traced through 3 scenarios to tests and test-plan.md"

  @AC-2
  Scenario: Red fails
    Given the self-test case "AC-8-phase-gate-red-fails"
    When "story-gate selftest" runs it
    Then the output is unchanged: "FAIL red DFS-001: 1 problem" and its one problem line, exit code 1

  @AC-2
  Scenario: Red passes and traceability fails
    Given the self-test case "AC-8-phase-gate"
    When "story-gate selftest" runs it
    Then the output is unchanged: "FAIL traceability DFS-001: 1 problem" and "- AC-3: no test display name starts with DFS-001.AC-3", exit code 1

  @AC-3
  Scenario: The skill records a passing check's output (checked by reading)
    Given ".claude/skills/story/SKILL.md", resume step 7
    When it is read
    Then it says that on a passing check the attempt's runs entry gets a "note" holding the check output's lines joined with "; "
```

## Scope

**Touches:**
- `tools/story-gate`: `TestPhaseGate.java`, `Verdict.java` (a `pass` overload with detail lines), the passing `test-phase` self-test cases' `expected.txt` (`CCW-014.AC-1-test-phase-wrapped`, `AC-8-phase-gate-passes`) and a new case for AC-2 if no existing one covers it
- `.claude/skills/story/SKILL.md`: one sentence in resume step 7 (decision 2)

**Out of scope:**
- What `red` or `traceability` count or decide.
- `test-phase`'s output when either check fails (the failing verdict alone, unchanged).

## Definition of done

- [x] Every scenario above is an automated test and passes: AC-1 and AC-2 by the four `test-phase` self-test cases; AC-3 checked by reading resume step 7 of `.claude/skills/story/SKILL.md`
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests): 329 tests, 0 failed, 3 skipped
- [x] No files changed outside the paths listed under Touches: `Verdict.java`, `TestPhaseGate.java`, three self-test cases, the story skill and this ticket
- [x] The story-gate self-tests pass (`java tools/story-gate/StoryGate.java selftest` or its launcher), with only the two passing `test-phase` cases' `expected.txt` re-blessed: 103 cases pass. One more case changed: `CCW-015.AC-7-instructions` pinned `pipeline-stage` at `version: 14`, failing since 8998094 raised it to 15 (DFS-027.L-1 to L-4); the pin is now 15.
- [x] A real `test-phase` run on a finished story's tests commit prints the three-line pass (or, if no stub remains to fail against, the run is described instead)
  - Run 2026-10-19 on DFS-027's tests commit d96c5e1, in a temporary detached worktree: exit 0, `PASS test-phase DFS-027: red and traceability pass`, `- red: 47 story tests fail with stubs, 114 other tests fail on the ticket's stub, 0 fail in declared classes, 148 other tests pass (2 skipped)`, `- traceability: 6 criteria traced through 12 scenarios to tests and test-plan.md`; the red counts match DFS-027's `plan.json` note.

## Open questions

None.
