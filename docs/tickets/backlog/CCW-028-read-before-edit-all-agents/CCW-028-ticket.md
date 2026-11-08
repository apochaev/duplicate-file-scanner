---
id: CCW-028
title: Apply the read-before-edit rule to the implementer, test-author and verifier
status: ready
type: chore
scope: [docs]
archetype: none
priority: medium
lane: harness
depends_on: []
touches:
  - .claude/agents/implementer.md
  - .claude/agents/test-author.md
  - .claude/agents/verifier.md
blocked_reason:
created: 2026-11-07
updated: 2026-11-07
completed:
---

# CCW-028: Apply the read-before-edit rule to the implementer, test-author and verifier

## Story

As the user paying for every pipeline run
I want every pipeline agent that edits files to read them with the Read tool first
So that no agent spends a full-context round trip on a write the tool refuses

## Context

Filed 2026-11-07 by dfs-harness after closing CCW-024, at the user's request to log tickets that further reduce cost.

- **The refusal.** Write and Edit refuse to change an existing file that was not read through the Read tool ("File has not been read yet"); printing it with `cat` or `sed` does not count. Each refused call is another request carrying the agent's whole context. CCW-024 added the rule to `.claude/agents/architect.md` only ("What to read", second paragraph); its Known limits named the other agents as unmeasured.
- **Measured** (all subagent transcripts of this project started on or after 2026-10-25, counted by `agent-*.meta.json` `agentType`, 2026-11-07): refused writes per agent: implementer 29 in 17 runs, test-author 6 in 22, verifier 6 in 19, architect 44 in 18 (before CCW-024; 0 in DFS-054 and DFS-057 after it).
- **Each refusal, all transcripts** (refine, 2026-11-07; script over every implementer, test-author and verifier transcript, matching each refused result to its tool call and checking for an earlier Read of the same path): 54 refusals, 53 on a path the agent had never opened with Read, 1 on a file Read earlier. The implementer edits the architect's stubs and existing sources (`OverlapIndex.java` 6 times in a row in DFS-048, `KeptPlan.java` 3 times in DFS-054, also `CLAUDE.md`); the test-author edits existing test classes (`ScanCommandTest.java`, `scan-row.spec.ts` 3 times in DFS-057); the verifier edits `verification.md` and the ticket file (DFS-050, DFS-057). Repeated refusals on one file show an agent retrying the Edit instead of reading first.
- **What the agents edit.** The implementer: the architect's stubs and other existing sources in `touches` (`implementer.md` "What to read" item 4). The test-author: new test files, and existing test classes it extends (its "What to read" says "Read nothing else" beyond the stubs, `design.md` and named sources, which does not stop it from editing a test class it never opened). The verifier: `verification.md` and the done-items in the ticket file (`verifier.md` "What you may write").
- **The retro and reflect agents** have no Write or Edit tool (`tools: Read, Grep, Glob, Bash`), so the rule does not apply to them.
- **Self-test pins.** `tools/story-gate/selftest/cases/CCW-020.AC-4-agent-prompts/case.json` pins phrases in all three files; an added paragraph leaves them intact.

## Decisions

1. **Archetype `none`** (2026-11-07, confirmed in refine). An instruction edit like CCW-024: implemented by hand, checked by reading, then proven by the next pipeline run's transcripts. Not a pipeline archetype (none fits, and a story test cannot carry the ID of an instruction edit).
2. **One paragraph in each agent file, naming that agent's own files** (2026-11-07, confirmed in refine). In `implementer.md`, `test-author.md` and `verifier.md`, after "What to read", modelled on `architect.md`'s CCW-024 paragraph: read every existing file with the Read tool before Write or Edit, even one already printed with `cat` or `grep`; after a refusal, read the file and retry once, never repeat the same Edit. Each names the files that agent edits (implementer: the stubs and sources in `touches`; test-author: existing test classes and specs it extends; verifier: `verification.md` and the ticket file). Not once in `CLAUDE.md` or the story skill's launch prompt (both in CCW-022's `touches`, so it would wait for or collide with CCW-022, and a generic rule names none of the files each agent forgets) and not both (duplicates the rule, same overlap).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `CCW-028.AC-<n>`.

- **AC-1** `implementer.md` tells the implementer to read every existing file it will change, naming the architect's stubs and the sources in `touches`, with the Read tool before Write or Edit, even after printing it with a shell command, and after a refusal to read the file and retry instead of repeating the Edit.
- **AC-2** `test-author.md` gives the same rule for the test-author, naming existing test classes and Vitest specs it extends.
- **AC-3** `verifier.md` gives the same rule for the verifier, naming `verification.md` and the ticket file.
- **AC-4** The first pipeline run after CCW-028 merges shows no `File has not been read yet` result in its implementer, test-author and verifier transcripts; their refused-write counts are recorded in this ticket's `verification.md` beside the 54 measured before.

## Scenarios

```gherkin
Feature: Read before edit in every editing agent

  # [read] AC-1 to AC-3 are checked by reading the agent files; [run] AC-4 by the next pipeline run's transcripts.

  @AC-1
  Scenario: [read] The implementer reads a stub before editing it
    Given the architect left a stub `KeptPlan.java` the implementer has only seen through `grep`
    When the implementer needs to change it
    Then implementer.md tells it to open `KeptPlan.java` with the Read tool first and then Edit it

  @AC-1
  Scenario: [read] A refused Edit is not repeated
    Given an Edit of `OverlapIndex.java` came back "File has not been read yet"
    When the implementer retries
    Then implementer.md tells it to Read `OverlapIndex.java` first, not to repeat the same Edit

  @AC-2
  Scenario: [read] The test-author reads an existing test class before extending it
    Given `ScanCommandTest.java` exists and the story adds a test to it
    When the test-author adds the test
    Then test-author.md tells it to Read `ScanCommandTest.java` with the Read tool before the Edit, and the same for a Vitest spec such as `scan-row.spec.ts`

  @AC-3
  Scenario: [read] The verifier reads its own files before editing them
    Given `verification.md` was written earlier in the run and the ticket file holds unticked done-items
    When the verifier updates `verification.md` or ticks a done-item
    Then verifier.md tells it to Read each file with the Read tool before the Edit

  @AC-4
  Scenario: [run] The next pipeline run has no refused write
    Given CCW-028 is merged and a lane session runs its next `/story run`
    When its implementer, test-author and verifier phases finish
    Then none of their transcripts holds a "File has not been read yet" result
    And the counts are recorded in verification.md beside the 54 refusals measured before
```

## Scope

**Touches:**
- `.claude/agents/implementer.md`: the read-before-edit paragraph after "What to read"
- `.claude/agents/test-author.md`: the same, naming existing test classes and specs
- `.claude/agents/verifier.md`: the same, naming `verification.md` and the ticket file

**Out of scope:**
- The architect (done in CCW-024).
- Changing what the agents read.
- The retro and reflect agents (no Write or Edit tool).
- `CLAUDE.md` and the story skill (decision 2).

## Definition of done

- [ ] Every scenario above is checked (mechanically or by reading, as recorded per scenario)
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] No files changed outside the paths listed under Touches
- [ ] AC-1 to AC-3 checked by reading the three agent files, recorded in `verification.md`
- [ ] The AC-4 run is done, its implementer, test-author and verifier transcripts checked and the counts recorded in `verification.md`
- [ ] The phrases pinned by `CCW-020.AC-4-agent-prompts` are unchanged

## Open questions

None.
