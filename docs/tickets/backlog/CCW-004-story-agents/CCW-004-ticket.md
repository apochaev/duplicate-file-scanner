---
id: CCW-004
title: Define the five story agents
status: ready
type: feature
scope: [build]
archetype: none
priority: medium
depends_on: [CCW-002, CCW-003]
touches: [.claude/agents]
blocked_reason:
created: 2026-09-21
updated: 2026-09-22
completed:
---

# CCW-004: Define the five story agents

*(Formerly DFS-011; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*

## Story

As the story orchestrator
I want the architect, implementer, test author, verifier and retro defined as subagents with tight tools, models and instructions
So that each phase runs with the least context and authority it needs

## Context

Five subagent files in `.claude/agents/`, launched by the `/story` skill (D1). Each prompt names exactly the files it reads, and each agent appends progress notes to `progress.md` and friction to `learnings.md`. The orchestrator, not the agents, writes `plan.json` (D5); agents report their outcome in their final message.

| Agent | Job | Model, effort, starting turn cap (D9) |
|---|---|---|
| `architect` | Domain design, ports, package placement, and compilable stubs that throw the not-implemented exception. Writes `design.md`. | opus, high, 25 |
| `test-author` | Writes tests from the acceptance criteria, their scenarios and the stubs, at least one per criterion, with `@DisplayName` starting with the criterion ID (for example `DFS-001.AC-3 Files of equal size form one candidate group`). Writes `test-plan.md`. Stops when the tests compile and fail for the right reason. | sonnet, medium, 30 |
| `implementer` | Makes the red tests pass. Never edits test files. | sonnet, medium, 40 |
| `verifier` | Runs after the deterministic gate passes. Reviews the diff against the scenarios, the `CLAUDE.md` invariants and `design.md`; judges test quality; records a verdict per acceptance criterion; ticks the done-items it verified; writes `verification.md`. No source edits. | opus, medium, 20 |
| `retro` | Runs only when `learnings.md` is non-empty or the story had defects. Reads the plan, `progress.md` and the learnings; proposes diffs; never applies them. | sonnet, low, 15 |

The turn caps are guesses to be replaced by pilot measurements. Escalation on retry (one tier up) is applied by the orchestrator through the per-invocation model, not in these files.

**Decided (2026-09-22): `archetype: none`.** These are agent definition files, not `file-scanner`/`cli` Java code, so `pipeline-stage` (the only archetype that exists) doesn't fit — same precedent as CCW-001, CCW-002 and CCW-003. A "define an agent/skill file" archetype has real future value (this is now the fourth ticket of that shape) but is not this ticket's to invent mid-refinement.

**Decided (2026-09-22): tool allowlists per agent.** `architect`, `test-author` and `implementer` each get `Read, Write, Edit, Glob, Grep, Bash` (all three write source or test artifacts and need `Bash` to compile or run). `retro` gets `Read, Grep, Glob, Bash` — no `Write`, mirroring `reflect.md` exactly: it returns its proposals in its final report and never touches a file, a stronger form of "never applies them" (D6) than a self-imposed prompt rule would be. `verifier` gets `Read, Edit, Write, Grep, Glob, Bash`, needed for the ticket file and `verification.md`.

**Decided (2026-09-22): the verifier's "no source edits" rule (D7) is stated in its prompt; real enforcement is a recommendation for CCW-005, not built here.** Claude Code's `tools:` frontmatter has no per-path scoping — granting `Edit` for the ticket file also grants it for `file-scanner/`. This ticket's `touches` is `.claude/agents` only, so it can neither build a new gate script (`tools/story-gate/` is CCW-003's territory, already `done`) nor wire a hook into the orchestrator (CCW-005's territory, out of scope below). **Recommendation left for CCW-005's refinement:** extend D2's already-proven "test freeze" pattern (a deterministic `git diff` check, no tokens spent) to a "verifier freeze" check — confirm no file outside the ticket file, `verification.md` and the story folder changed during the verifier's phase — rather than a `PreToolUse` hook, which would be a new enforcement mechanism nothing else in D1–D12 uses.

**Decided (2026-09-22): the story folder is the ticket's own folder** (`docs/tickets/{backlog,completed}/{ID}-{slug}/`), matching D5's "Story folder artifacts, next to the ticket" — not a new presumption, just applying already-decided design text. **Its path is given explicitly at the top of every agent's launch prompt by the orchestrator, never derived by the agent itself** (a self-discovery `Glob` would be exactly the "speculative read" D9 says to avoid, and the orchestrator already knows the path definitively when it launches the agent).

**Decided (2026-09-22): `retro` is a new, separate agent file (`.claude/agents/retro.md`) that follows `reflect.md`'s pattern** (read-only tools, a fixed report format, never writes) **with its own inputs** (`plan.json`, `progress.md`, `learnings.md`, not the ticket/git-history/commits `reflect` reads) **and its own report shape**, proposing diffs against archetype files, agent prompts, skills or `CLAUDE.md` rather than learnings-log entries. Matches D6's own wording — `reflect` is "the seed of `retro`", a seed, not the same organism. `retro` does not invoke `reflect` as a step (subagents launching subagents is exactly the nesting fragility this project's own docs flag as limited), and `reflect.md` itself is not edited or renamed (its contract is already shipped and used by `CCW-007`, `done`).

**Decided (2026-09-22): `retro`'s frontmatter sets `omitClaudeMd: true`; no other agent's does.** Verified as a real, current field (Claude Code v2.1.271+, fetched from the docs 2026-09-22): excludes CLAUDE.md from a subagent's context. `retro` reads only `plan.json`/`progress.md`/`learnings.md`, none of which need CLAUDE.md's content — a free saving. The other four agents genuinely use CLAUDE.md's design decisions and invariants and keep it.

**Decided (2026-09-22): evaluating agent prompts beyond the pilot is out of scope; CCW-006 (the pilot, D8) already exists to do this.** A/B on DFS-001, then DFS-002, with recorded metrics, is the evaluation; building a second evaluation mechanism before a single real run exists would be evaluating against nothing.

**Decided (2026-09-22): verification is a manual stand-in, mirroring D6's own precedent** (the manual learnings log stood in for `/story learn` before CCW-007 existed). CCW-005 (the orchestrator) doesn't exist yet — this ticket comes before it in `depends_on` — so "demonstrated on a scratch story" means each agent is launched directly, by hand, against a fixture ticket and a scratch story folder (story-folder path and any upstream artifacts supplied manually, in place of what CCW-005 would normally generate and pass), not read-checked. This is the stronger verification bar `L-009` pushes toward, and there is no instruction-following ambiguity excuse here: these agents genuinely can be launched standalone.

## Acceptance criteria

The full ID of each criterion is `CCW-004.AC-<n>`.

- **AC-1** Each of the five agent files (`architect`, `test-author`, `implementer`, `verifier`, `retro`) has a name, description, tools list, model, effort and turn cap matching the D9 table and the tool allowlists decided above.
- **AC-2** The verifier's prompt states it may only `Edit` the ticket file and `Write` `verification.md`, and must never edit or write any other file, including source; this ticket's Context records the "verifier freeze" gate recommendation for CCW-005.
- **AC-3** Every agent's prompt states the story folder is the ticket's own folder, with its path given explicitly at the top of the launch prompt rather than derived by the agent.
- **AC-4** The architect produces `design.md` and compilable stub files whose methods throw `UnsupportedOperationException` with the message `not implemented: <TICKET-ID>`.
- **AC-5** The test author writes at least one test per acceptance criterion with `@DisplayName` starting with the criterion's full ID, writes `test-plan.md` mapping criteria, scenarios and tests, and stops only once the tests compile and fail for the right reason.
- **AC-6** The implementer makes the tests pass and changes no test file.
- **AC-7** The verifier runs only after the deterministic gate passes, writes `verification.md` with a verdict per acceptance criterion, ticks the done-items it verified, and changes no source file.
- **AC-8** `retro` is its own agent file, read-only (no `Write` tool), returns its proposals in its final report rather than writing any file, and runs only when `learnings.md` is non-empty or the story had defects, reading only `plan.json`, `progress.md` and `learnings.md`.
- **AC-9** `retro`'s frontmatter sets `omitClaudeMd: true`; no other agent's frontmatter sets it.
- **AC-10** Each agent's prompt names exactly the files it reads, nothing speculative.

## Scenarios

```gherkin
Feature: Story agents

  @AC-1
  Scenario: Every agent is defined with the agreed settings
    Given the directory `.claude/agents/`
    When the five agent files are read
    Then each has a name, a description, a tools list, a model, an effort and a turn cap matching the table

  @AC-2
  Scenario: The verifier's prompt forbids source edits
    Given the verifier agent file
    When its prompt is read
    Then it states the verifier may only Edit the ticket file and Write verification.md, and must never edit or write any other file

  @AC-3
  Scenario: Agents are told the story folder, not left to derive it
    Given any of the five agent prompts
    When it is read
    Then it states the story folder is the ticket's own folder and that the orchestrator gives its path explicitly at the top of the launch prompt

  @AC-4
  Scenario: The architect produces a design and compilable stubs
    Given a ready ticket with an archetype
    When the architect runs
    Then `design.md` exists, the stubs compile, and each stub method throws `UnsupportedOperationException` with the message `not implemented: <TICKET-ID>`

  @AC-5
  Scenario: The test author covers every scenario
    Given `design.md` and the stubs
    When the test author runs
    Then every acceptance criterion has at least one test whose display name starts with its ID, and `test-plan.md` maps criteria, scenarios and tests

  @AC-6
  Scenario: The implementer leaves the tests alone
    Given red tests
    When the implementer runs
    Then the tests pass and no test file has changed

  @AC-7
  Scenario: The verifier reviews only after the gate passes
    Given a passing deterministic gate
    When the verifier runs
    Then it writes `verification.md` with a verdict per acceptance criterion, ticks the done-items it verified, and changes no source file

  @AC-8
  Scenario: The retro step is skipped when there is nothing to learn
    Given a story with an empty `learnings.md` and no defects
    When the orchestrator reaches the retro phase
    Then retro is not launched

  @AC-8
  Scenario: retro proposes without writing any file
    Given retro has findings to report
    When it runs
    Then it returns its proposals in its final message and creates or edits no file

  @AC-9
  Scenario: Only retro omits CLAUDE.md
    Given the five agent files
    When their frontmatter is read
    Then only `retro.md` sets `omitClaudeMd: true`

  @AC-10
  Scenario: Agents keep their reads minimal
    Given any agent prompt
    When it is read
    Then it names exactly the files that agent reads and nothing speculative
```

## Scope

**Touches:**
- `.claude/agents/`: the five agent files

**Out of scope:**
- The orchestrator skill that launches them (CCW-005)
- The gate tools (CCW-003) and the archetype content (CCW-002)
- A "verifier freeze" deterministic gate or any `PreToolUse` hook (recommended for CCW-005, not built here)
- Evaluating agent prompts beyond CCW-006's pilot

## Definition of done

- [ ] Every scenario above is demonstrated by launching the relevant agent directly (a manual stand-in for CCW-005, against a fixture ticket and a scratch story folder), with evidence recorded in this ticket
- [ ] Each agent file's frontmatter uses only fields the current docs list (`name`, `description`, `tools`, `model`, `effort`, `maxTurns`, and `omitClaudeMd` on `retro` only)
- [ ] The "verifier freeze" gate recommendation for CCW-005 is recorded in this ticket's Context
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
