---
id: CCW-024
title: Cut the architect's token cost
status: in-review
type: chore
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: []
touches:
  - .claude/agents/architect.md
  - .claude/archetypes/use-case/archetype.md
  - .claude/archetypes/wiring/archetype.md
  - .claude/archetypes/endpoint/archetype.md
  - .claude/archetypes/pipeline-stage/archetype.md
blocked_reason:
created: 2026-11-06
updated: 2026-11-06
completed:
---

# CCW-024: Cut the architect's token cost

## Story

As the user paying for story pipeline runs
I want the architect phase to stop spending Opus tokens on rework and on reading tests it only needs to run
So that a design phase costs less without a weaker design or an extra pipeline phase

## Context

The architect (`.claude/agents/architect.md`, Opus at high effort in every archetype) is the most expensive phase per run, and its tool-call counts recorded in `plan.json` have roughly tripled across recent tickets: 16–40 calls up to DFS-042, then 57 (DFS-047), 63 (DFS-055) and 79 (DFS-049). On 2026-11-06 the dfs-harness session read two architect transcripts:

| | DFS-049 (`component`) | DFS-055 (`use-case`) |
|---|---|---|
| Tool calls / model requests | 79 / 31 | 63 / 30 |
| Output tokens (incl. thinking) | 70.9k | 72.4k |
| Cache reads / cache writes | 2.70M / 149k | 3.15M / 172k |
| Context at the end | ~147k | ~172k |

At standard API price ratios (output 5× input, cache read 0.1×, cache write 1.25×), roughly 41–45% of each run's cost is output, 33–35% cache reads and 22–24% cache writes. Each request re-sends the whole context, so anything that adds requests or adds context early is paid for again on every later request.

Two causes were found:

1. **Read-before-edit refusals, in both runs.** The architect inspects files with `cat`/`sed` through Bash, but the Write and Edit tools only accept a change to an existing file that was read with the Read tool (`File has not been read yet. Read it first before writing to it.`). DFS-049: 19 of 20 stub writes refused (`results/` stubs already existed in the worktree); the architect deleted them and wrote all 19 again, wasting about 16k output tokens plus about 7 extra requests at ~100k context each. DFS-055: 7 of its edits refused, then 8 Reads to recover (about 4k output tokens, one extra round trip).
2. **Finding Expected breakage by reading tests.** Step 4 of `architect.md` and the "Read nothing else" exception (line 27) let the architect search the test trees for callers and read the tests it finds. DFS-055 did that: 9 calls, test code grew the context from 77k to 127k, and that ~45k was then re-sent on about 18 later requests (roughly 25% of the run's cache reads). DFS-049 instead ran `:server:npmTest` once and read the failures, because the `component` archetype already says to (its `archetype.md` checklist item 3, added after DFS-049): one call. The four Java archetypes (`use-case`, `wiring`, `endpoint`, `pipeline-stage`) have no such rule.

Some of DFS-055's test reading was design work, not breakage hunting (how an existing test starts a child JVM, calls 16–17, for testing a lock across processes). This ticket does not forbid reading a test for design (decision 2); it changes how breakage is found.

Considered and not pursued here: a separate, cheaper "stub writer" phase after an Opus design phase. In DFS-055 the stubs were edits woven into existing classes, so any writer would need the whole context the architect already holds. Thinner `component` stubs (HTML and CSS left to the Sonnet implementer) would capture most of that saving for frontend tickets; that waits for evidence (decision 5).

## Acceptance criteria

The full ID of each criterion is `CCW-024.AC-<n>`.

- **AC-1** `architect.md` tells the architect to read with the Read tool, not with `cat`, `sed` or another shell command, every existing file it will change with Write or Edit, before changing it.
- **AC-2** `architect.md` step 4 has the architect find Expected breakage by running the archetype's named test task once after the stubs compile, with `--continue` and its output filtered to the failing test classes, then checking each failing class's report for `not implemented: <TICKET-ID>` and declaring each class (or spec file) whose failure or root cause lacks it, instead of searching for and reading test files.
- **AC-3** The "Read nothing else" exception in `architect.md` no longer permits reading tests to find Expected breakage; it permits reading an existing test only when the design depends on how that test is built, and requires each test so read to be named in `design.md`'s `## Summary` with a one-line reason.
- **AC-4** Each Java archetype's `archetype.md` names the test task the architect runs for step 4, covering the changed project and every project that depends on it, the way the `component` archetype names `:server:npmTest`: `use-case` and `pipeline-stage` `./gradlew test --continue`, `wiring` `./gradlew :scanner-spring:test :cli:test :server:test --continue`, `endpoint` `./gradlew :server:test --continue`.
- **AC-5** The first design phase of a ticket with a Java archetype (`use-case`, `wiring`, `endpoint`, `pipeline-stage`) run after CCW-024 merges shows, in its architect transcript, no `File has not been read yet` result, exactly one run of the archetype's named test task, and no grep or Read into a test source tree other than tests named in its `design.md` Summary; its tool calls, model requests and output tokens are recorded in this ticket's `verification.md` beside DFS-049's and DFS-055's as context, not as a pass condition.

## Scenarios

How each is checked: the seven scenarios tagged AC-1 to AC-4 are checked by reading the edited `architect.md` and `archetype.md` files at review, and observed for real in the AC-5 run; the two AC-5 scenarios are run mechanically, by reading the live architect transcript and its `.meta.json`. No scenario is a walkthrough of a run made for this ticket.

```gherkin
Feature: Architect avoids rework and test reading

  @AC-1
  Scenario: An existing file is read with Read before it is edited
    Given a ticket whose stubs change the existing file "JobStore.java"
    When the architect runs the design phase
    Then its transcript shows a Read of "JobStore.java" before the first Edit of it
    And no Write or Edit call returns "File has not been read yet"

  @AC-1
  Scenario: Leftover stub files in the worktree are read, not deleted and rewritten
    Given the worktree already holds "results/folder-tree.ts" from an earlier attempt
    When the architect writes its stub for "results/folder-tree.ts"
    Then it reads the file with Read first
    And it does not delete the file with a shell command to get around the refusal

  @AC-2 @AC-4
  Scenario: Breakage in a Java ticket is found by one test run
    Given a use-case ticket whose stubs make an existing CLI test fail with exit code 1
    When the architect finishes the stubs and they compile
    Then it runs the archetype's named test task once
    And lists that test class under "## Expected breakage" with a one-line reason
    And makes no grep or Read call into the test source trees to find it

  @AC-2 @AC-4
  Scenario: Breakage in a dependent project is found in the same run
    Given a use-case ticket whose file-scanner stub makes the existing "CliApplicationTest" in cli fail with exit code 1
    And makes an existing file-scanner test fail first
    When the architect runs "./gradlew test --continue"
    Then the cli tests still run after the file-scanner failure
    And "CliApplicationTest" is listed under "## Expected breakage"

  @AC-2
  Scenario: A test failing on the stub itself is not declared
    Given the test run fails one existing test with "not implemented: CCW-024" as its root cause
    When the architect fills "## Expected breakage"
    Then that test class is not listed

  @AC-3
  Scenario: A test read for the design is named with its reason
    Given a ticket whose stub must be reachable from a test that starts a child JVM, as in DFS-055
    When the architect reads "Fixtures.java" to see how existing tests start one
    Then design.md's "## Summary" names "Fixtures.java" with a one-line reason

  @AC-3
  Scenario: No test is read to find breakage
    Given a ticket whose stubs change "PlanExecutionService"
    When the architect fills "## Expected breakage"
    Then it does not grep the test trees for callers of "PlanExecutionService"
    And every test it read is one named in the Summary for a design reason

  @AC-5
  Scenario: The first Java design phase after the merge passes the three signals
    Given CCW-024 is merged and dfs-backend has reread architect.md after message 3
    And dfs-backend runs the design phase of the next Java-archetype ticket, likely DFS-054
    When dfs-harness reads that run's architect transcript
    Then no tool result reads "File has not been read yet"
    And the archetype's named test task ran exactly once
    And every test file read or grepped is named in design.md's Summary with a reason
    And verification.md records its tool calls, model requests and output tokens beside DFS-049 (79 calls, 31 requests, 70.9k) and DFS-055 (63 calls, 30 requests, 72.4k)

  @AC-5
  Scenario: A failed signal keeps the ticket open
    Given that transcript shows one Edit refused with "File has not been read yet"
    When dfs-harness records the result
    Then verification.md names the refused call and the file
    And CCW-024 returns to in-progress instead of done
```

## Scope

**Touches:**
- `.claude/agents/architect.md`: the read-before-edit rule, step 4 rewritten, the "Read nothing else" exception narrowed, the `## Summary` section gains the tests read for the design.
- `.claude/archetypes/use-case/archetype.md`, `wiring/archetype.md`, `endpoint/archetype.md`, `pipeline-stage/archetype.md`: name the test task for step 4, and bump each one's `version:` (decision 6).

**Out of scope:**
- Thinner `component` stubs, and a separate stub-writing phase on a cheaper model (see Context).
- Changing any agent's model, effort or `maxTurns`.
- The two story-gate selftest cases already failing on `main` on 2026-11-06 (`CCW-020.AC-1-archetype-files`, `CCW-020.AC-12-docs`: they expect archetype versions that later edits bumped).
- Having `story-gate` compute Expected breakage itself.
- The other pipeline agents (test-author, implementer, verifier), which may hit the same read-before-edit refusal; not measured.

## Definition of done

- [x] `architect.md` and the four archetype files carry the changes in AC-1 to AC-4 (checked by reading) — commit `db54a27`; verdicts in `verification.md`
- [x] Search for every other mention of how Expected breakage is found (`.claude/`, `CLAUDE.md`, `docs/design/`) and record the hits in `verification.md` (CCW-018.L-1) — recorded under Evidence; nothing else needed changing
- [ ] The live run in AC-5 is done, its three signals checked and its figures recorded in `verification.md`
- [ ] If a `component` design phase has run after the merge by the time AC-5 is verified, its stub-writing share of output is recorded in `verification.md`; otherwise `verification.md` says none ran (decision 5)
- [x] `tools/story-gate/story-gate selftest` shows no failure beyond the two listed under Out of scope (`CCW-015.AC-7-instructions` passes) — 2026-11-06, `FAIL selftest: 2 problems`, the same two
- [x] The four Java archetypes' `version:` is bumped — use-case 10, pipeline-stage 17, wiring 4, endpoint 4
- [x] No files changed outside the paths listed under Touches — `git diff --stat main` shows only those five files and this ticket's folder

## Decisions

1. **Archetype `none`** (2026-11-06): implemented by hand in a worktree. The ticket edits instructions only, so no pipeline phase has code to test, and spending an Opus architect run on a ticket meant to cut architect cost defeats it. AC-1 to AC-4 are checked by reading the edited files; AC-5 by the live run. A hand-written `verification.md` opens with the product owner review.
2. **Tests read for the design stay allowed, with a stated reason** (2026-11-06): the architect may read an existing test only when the design depends on how it is built (DFS-055 calls 16–17), never to find breakage, and names each such test in `design.md`'s `## Summary` with a one-line reason. The Summary is defined in `architect.md` itself and shown verbatim at the design gate, so no archetype `design.md.tmpl` changes, and any over-reading is visible to the user and to the retro agent. A strict "only tests in `touches`" rule was rejected because refinement rarely foresees which tests the design needs.
3. **The test task covers dependent projects, with `--continue` and filtered output** (2026-11-06): measured from the 2026-11-01 reports, test bodies take 14 s (`file-scanner`, 330 tests), 2 s (`scanner-spring`), 7 s (`cli`), 35 s (`server`), so a full Java run is about 1–2 minutes of wall-clock time and almost no tokens once filtered. Running only the changed project was rejected: it misses CLI and server tests that the red gate then stops on as undeclared regressions. One `./gradlew test` for every archetype was rejected as running `server` for core-only changes and dropping AC-4's per-archetype naming.
4. **AC-5 is proven by the first Java-archetype design phase after the merge** (2026-11-06), run by dfs-backend as usual (a backend ticket's run belongs to that lane), likely DFS-054 but not tied to it. Pass or fail rests on three yes-or-no signals in the transcript; calls, requests and tokens are recorded only as context, since ticket size swings them more than this fix does. CCW-024 waits in `in-review` until then; nothing depends on it. Assumptions the run exercises: `done` step 2's message 3 makes dfs-backend reread `architect.md` before its next `/story`; the architect transcript is found under `~/.claude/projects/<project>/<session>/subagents/agent-*.jsonl` by its `agent-*.meta.json` (`"agentType":"architect"`, `description` naming the ticket), where DFS-049's and DFS-055's were found. A same-ticket replay of DFS-055 was rejected as spending the Opus run being cut; retiring AC-5 as leaving the "So that" unproven.
5. **Thinner `component` stubs wait for evidence** (2026-11-06): about half of DFS-049's ~30k stub-writing output was the double write AC-1 removes, so the remaining saving (~13k per frontend ticket) is measured before it becomes a ticket, against its cost of less layout shown at the design gate. When AC-5 is verified, the latest `component` design phase run after the merge, if any, has its stub-writing share of output recorded in `verification.md`; a ticket is filed only if that share is still large. A draft now was rejected as resting on one transcript; dropping it as giving up a likely saving unmeasured.
6. **Refinement findings, recorded without a separate decision** (2026-11-06):
   - **Selftest phrases.** `tools/story-gate/selftest/cases/CCW-015.AC-7-instructions/case.json` asserts four phrases in `architect.md`: "Declare under `design.md`'s `## Expected breakage` every existing test class", "simple class name only", "Write \"None.\" when there is none" and "every class declared under `## Expected breakage`, by name". The rewritten step 4 keeps all four verbatim, so the case and `tools/story-gate/` stay out of `touches`.
   - **Archetype versions.** `docs/design/story-orchestration.md` (line 116) bumps an archetype's `version` whenever it is changed; the four Java archetypes go from use-case 9, wiring 3, endpoint 3, pipeline-stage 16 to one above.
   - **Selftest baseline.** `tools/story-gate/story-gate selftest` on `main` on 2026-11-06 fails two cases unrelated to this ticket (see Out of scope); the done check is "no failure beyond those two".
   - **Other mentions searched** (CCW-018.L-1): `Expected breakage`, `grep -rl` and "search the test" across `.claude/`, `CLAUDE.md` and `docs/design/`. Hits: `architect.md` (changed here), the `component` `archetype.md` checklist item 3 (already says to run `:server:npmTest`; consistent, unchanged), the five `design.md.tmpl` files (describe the section's format, not how it is found; unchanged), and the tickets skill's refine rule on output formats (about refinement, unrelated).

## Open questions

None.
