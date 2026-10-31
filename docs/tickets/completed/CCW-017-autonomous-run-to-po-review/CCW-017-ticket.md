---
id: CCW-017
title: Run a ready ticket autonomously to product-owner review
status: done
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
lane: harness
depends_on: []
touches:
  - .claude/skills/tickets/SKILL.md
  - .claude/skills/tickets/TEMPLATE.md
  - .claude/skills/story/SKILL.md
  - .claude/agents/verifier.md
  - docs/design/story-orchestration.md
  - CLAUDE.md
  - docs/tickets/backlog/CCW-017-autonomous-run-to-po-review/
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed: 2026-10-31
---

# CCW-017: Run a ready ticket autonomously to product-owner review

## Story

As the product owner of this project
I want to refine a user story interactively and, once I approve it as `ready`, have Claude implement and test it in its own worktree without stopping until it is ready for my review
So that my time goes into deciding what to build and accepting the result, not into approving intermediate pipeline steps

## Context

- **Today's flow:** `/tickets refine <ID>` interviews the user until the `ready` gate holds, then sets `ready` after confirmation. The user then types `/story run <ID>`, which creates the ticket's worktree and branch, runs the phases (`design` → `test` → `implement` → `verify`, then `retro` when warranted) with deterministic checks and per-phase commits, and stops at every gate configured in the archetype's `gates:` and at `blocked`. Every current archetype (`pipeline-stage`, `use-case`, `wiring`) sets `gates: [design, done]`.
- **What changes:** the user stays in the loop for refinement and for the final review, and nothing in between. The settled shape is under Decisions below.
- **Known inconsistency:** `.claude/skills/story/SKILL.md` has `disable-model-invocation: false` in its frontmatter while its text says the skill is `true` and must never be launched on Claude's own judgement.
- Design of the pipeline: `docs/design/story-orchestration.md` (D1–D12).

## Decisions

1. **Gates are a per-ticket decision made in refinement** (2026-10-31). Archetype defaults stay as they are (`[design, done]`) and act only as a fallback; refinement asks every time and records `gates:` on the ticket. This amends D3 in `docs/design/story-orchestration.md`: "final gate only" is now a legitimate per-ticket choice, made knowingly at refinement.
2. **Authorization is a ticket field, `autorun: true`** (2026-10-31), set when the user confirms `ready`. It authorizes `/story run` for that ticket only, never `/story approve`.
3. **Two launch points, no new command** (2026-10-31): `refine` launches right after the ready commit when all dependencies are done; `/tickets done <X>` launches the lowest-ID autorun ticket that X's close made dispatchable and lists the rest. A launch is refused if the ticket changed after the commit that approved it.
4. **Notify at stops with the `PushNotification` tool** (2026-10-31), called by the `story` skill at the review gate, at `blocked` and at a refused autonomous launch. Not a `Stop` hook: it would fire at the end of every turn, refinement questions included. The tool is deferred (load with `ToolSearch` first) and skips itself when the user is at the terminal.
5. **A product-owner review section** (2026-10-31): the verifier writes `## Product owner review` at the top of `verification.md`, shown at the `done` gate in place of `## Summary`. "Decided without you" is the safety valve for dropping the design gate. "Evidence" stays open: recorded demos (CLI output, Playwright screenshots) come with the frontend archetypes. Hand-implemented tickets end with the same section.
6. **No `tools/story-gate` change** (2026-10-31): gates are resolved by the `story` skill when it writes `plan.json`, and `ConsistencyCheck` already accepts `awaiting-approval` at any gate; `gates:` and `autorun:` values are checked by `/tickets validate`.
7. **Proof: spot checks now, then a live run on the next real pipeline ticket** (2026-10-31). The live run uses the `scanner-spring` extraction (`docs/design/ui.md` D1, `wiring` archetype), refined with `gates: [done]` and `autorun: true`. CCW-017 waits `in-review` until that run reaches its `done` gate. `autorun` applies only to pipeline tickets: a hand-implemented (`none`) ticket is implemented by the session that refines it, with no launch step.

## Acceptance criteria

The full ID of each criterion is `CCW-017.AC-<n>`.

- **AC-1** A ticket's frontmatter may carry `gates:` (a list of phase ids or `done`), and `/story run` builds `plan.json`'s gates from it, falling back to the archetype's `gates:` only when the ticket has none.
- **AC-2** `/tickets refine` asks which gates the ticket needs as one of its decisions (recommending the design gate for anything that plans or executes deletion, CLAUDE.md safety invariants) and records the answer as the ticket's `gates:`; the `ready` gate refuses a ticket whose archetype is not `none` and whose `gates:` is unset, naming "gates set".
- **AC-3** A ticket's frontmatter may carry `autorun: true|false` (absent means false); `/tickets validate` reports any other value and reports `autorun: true` on an `archetype: none` ticket (the pipeline refuses those), and the `ready` confirmation in `refine` asks whether to set it for a pipeline ticket.
- **AC-4** When the user confirms `ready` with `autorun: true` and every `depends_on` is `done`, `refine` commits the ticket file alone on `main` (`docs: refine <ID> [<ID>]`, staged by name) and then runs `/story run <ID>` in the same conversation; with a dependency not `done`, it commits and stops, naming the dependency it waits on.
- **AC-5** After `/tickets done <X>` commits the close, it finds every `ready` ticket with `autorun: true` whose `depends_on` are now all `done` and whose branch `tickets/<ID>-<slug>` does not exist; it runs `/story run` for the lowest ID among them and lists the others with the command to start each.
- **AC-6** An autonomous launch (AC-4 or AC-5) is refused, naming the reason and asking the user to re-confirm, when the ticket file has uncommitted changes or differs from the commit that last set `status: ready` with `autorun: true`.
- **AC-7** `.claude/skills/story/SKILL.md` states that Claude may start `run` on its own only through AC-4 or AC-5, and never `approve`; its frontmatter and text agree on whether model invocation is allowed.
- **AC-8** At every stop of an autonomously launched or resumed run (the review gate, `blocked`, and an AC-6 refusal), the `story` skill sends one `PushNotification` (status `proactive`, one line under 200 characters) that starts with the ticket ID and what the user should act on and ends with the exact next command; the skill loads the deferred tool with `ToolSearch` (`select:PushNotification`) before the first call, and a "not sent" result is not treated as an error.
- **AC-9** The verifier opens `verification.md` with a `## Product owner review` section under these headings, in order: What you can do now, Acceptance criteria (each ID with a plain-language verdict), Try it (exact commands and what to look for), Decided without you, Known limits, Evidence, How it went (attempts, defects, retries); the existing `## Summary` follows it.
- **AC-10** At the `done` gate, `/story run` shows the `## Product owner review` section verbatim instead of `## Summary` (falling back to `## Summary` for a `verification.md` that has no such section), then the approve command; the `design` gate is unchanged.
- **AC-11** A hand-implemented (`archetype: none`) ticket, when its implementation finishes, gets a `verification.md` in its folder with the same `## Product owner review` section, written by the implementing session, before `/tickets review` sets it `in-review`.
- **AC-12** A real pipeline ticket refined with `gates: [done]` and `autorun: true` goes from the user's `ready` confirmation to the product-owner review with no further user input: the ready commit lands on `main`, the worktree branches from it, every phase runs, and the run stops only at the `done` gate, showing the AC-9 section and sending the AC-8 notification.

## Scenarios

Each scenario is marked **[mechanical]** (commands run against the real repository, output recorded in `verification.md`), **[read]** (checked by reading the edited instructions; not a walkthrough of every mode) or **[live]** (observed in the AC-12 run).

```gherkin
Feature: Run a ready ticket autonomously to product-owner review

  Background:
    Given the repo checkout is on main, shared with another session that commits to main
    And every story runs in its own worktree ../duplicate-file-scanner-<ID>-<slug>

  # [read]
  @AC-1
  Scenario: The ticket's gates win over the archetype's
    Given a ticket with archetype "wiring" (gates [design, done]) and "gates: [done]"
    When /story run builds plan.json
    Then plan.json's gates are [{"after": "done", "status": "pending"}]

  # [read]
  @AC-1
  Scenario: The archetype's gates apply when the ticket has none
    Given a ticket with archetype "use-case" and no gates field
    When /story run builds plan.json
    Then plan.json's gates are design and done, as before this ticket

  # [read]
  @AC-2
  Scenario: Refinement asks for gates and the ready gate requires them
    Given a pipeline ticket being refined with no gates field
    When every other ready item holds and the user asks for ready
    Then refine refuses, naming "gates set"
    And it asks the gates question, recommending the design gate when the ticket plans or executes deletion

  # [mechanical]
  @AC-3
  Scenario: validate checks the autorun field
    Given a scratch copy of a ticket with "autorun: yes", and another with "archetype: none" and "autorun: true"
    When /tickets validate runs over them
    Then it reports both, naming the ticket and the field, and changes nothing

  # [read]
  @AC-3 @AC-4
  Scenario: Confirming ready with autorun commits and launches
    Given a pipeline ticket DFS-0NN with all dependencies done, being refined
    When the user confirms ready and autorun true
    Then the ticket file alone is committed on main as "docs: refine DFS-0NN [DFS-0NN]"
    And /story run DFS-0NN starts in the same conversation

  # [read]
  @AC-4
  Scenario: A ready autorun ticket waiting on a dependency is committed, not launched
    Given a pipeline ticket whose depends_on includes a ticket still in-progress
    When the user confirms ready and autorun true
    Then the ticket is committed on main and refine stops, naming the dependency

  # [mechanical]
  @AC-5
  Scenario: Closing a dependency finds the autorun tickets it unblocked
    Given two ready autorun tickets that depend only on X, one of whose branches already exists
    When the AC-5 lookup runs right after X's close commit
    Then only the ticket without a branch is selected
    And with several selected, the lowest ID is launched and the others are listed with their commands

  # [mechanical]
  @AC-6
  Scenario: A ticket edited after approval is not launched
    Given a ready autorun ticket whose file was edited and committed after the commit that set ready and autorun
    When an autonomous launch is attempted
    Then git diff against that commit is non-empty and the launch is refused, asking the user to re-confirm

  # [mechanical]
  @AC-6
  Scenario: Uncommitted changes block an autonomous launch
    Given a ready autorun ticket with an uncommitted edit in the repo checkout
    When an autonomous launch is attempted
    Then the launch is refused, naming the uncommitted change

  # [read]
  @AC-7
  Scenario: The story skill states when Claude may start it
    Given .claude/skills/story/SKILL.md after this ticket
    Then its text allows Claude to start run only through AC-4 or AC-5 and never approve
    And its frontmatter's disable-model-invocation agrees with that text

  # [read] [live]
  @AC-8
  Scenario: A stop sends one notification
    Given an autonomously launched run that reaches the done gate
    When the run stops
    Then one PushNotification is sent, starting with the ticket ID and ending with "/story approve <ID>"
    And a "not sent" result does not stop or fail the run

  # [read]
  @AC-8
  Scenario: A blocked run notifies with the next command
    Given an autonomous run whose implement phase fails its checks twice
    When the run sets state blocked
    Then one notification names the ticket, the phase and "/story status <ID>"

  # [read]
  @AC-9 @AC-10
  Scenario: The review gate shows the product-owner section
    Given verification.md opens with "## Product owner review" followed by "## Summary"
    When /story run stops at the done gate
    Then the product-owner section is shown verbatim, with headings in the AC-9 order, then the approve command

  # [read]
  @AC-10
  Scenario: An older verification.md falls back to the summary
    Given a verification.md with only "## Summary"
    When /story run stops at the done gate
    Then the Summary is shown, as before this ticket

  # [mechanical]
  @AC-11
  Scenario: This ticket ends with its own review section
    Given CCW-017 is implemented by hand
    When its implementation finishes
    Then docs/tickets/backlog/CCW-017-autonomous-run-to-po-review/verification.md opens with "## Product owner review" under the AC-9 headings
    And only then is CCW-017 set in-review

  # [live]
  @AC-12 @AC-4 @AC-6 @AC-8 @AC-10
  Scenario: The scanner-spring ticket runs unattended to review
    Given the scanner-spring extraction ticket refined with archetype "wiring", "gates: [done]" and "autorun: true"
    And the backend session has been told that ticket touches cli
    When the user confirms ready
    Then its ready commit lands on main and its worktree branches from that commit
    And design, tests, implement and verify run with no user input
    And the run stops only at the done gate, showing the product-owner section and sending one notification
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md`: `gates:` and `autorun:` fields in the frontmatter table; `refine` asks the gates question and the ready-and-autorun confirmation, commits and launches (AC-2–AC-4, AC-6); `done` launches what it unblocked (AC-5, AC-6); `validate` checks both fields (AC-3); the hand-implemented review section (AC-11).
- `.claude/skills/tickets/TEMPLATE.md`: commented `gates:` and `autorun:` lines.
- `.claude/skills/story/SKILL.md`: ticket gates over archetype gates (AC-1); when Claude may start `run` (AC-7); notifications (AC-8); the done gate shows the product-owner section (AC-10).
- `.claude/agents/verifier.md`: writes `## Product owner review` (AC-9).
- `docs/design/story-orchestration.md`: amend D3 (per-ticket gates, autonomous launch).
- `CLAUDE.md`: the Tickets section names the `gates` and `autorun` fields.
- `docs/tickets/backlog/CCW-017-autonomous-run-to-po-review/`: this ticket and its `verification.md`.

**Out of scope:**
- Frontend support in `tools/story-gate` and the Angular/API archetypes (separate CCW tickets).
- Changing any archetype's `gates:` default (decision 1).
- Running several pipelines in parallel, or a `/tickets dispatch` command.
- `autorun` for hand-implemented tickets (decision 7).
- Writing the scanner-spring ticket itself (it is refined as its own DFS ticket; this ticket only uses its run).

## Definition of done

- [x] Every [read] scenario checked against the edited files, noted in `verification.md` (Read checks table)
- [x] Every [mechanical] scenario run against the real repository (scratch copies, never real tickets edited), commands and output in `verification.md` (AC-3, AC-5, AC-6; AC-6 found and fixed a defect)
- [x] The AC-12 live run reached the scanner-spring ticket's `done` gate with no user input between `ready` and the gate; its `plan.json` and the notification text recorded in `verification.md` (DFS-031, 2026-10-31; one resume after the backend session's usage limit, see `verification.md`)
- [x] `./gradlew build` is green (worktree, exit 0)
- [x] `/tickets validate` reports nothing new (new gates/autorun rules over every ticket: no field set anywhere; the only ready-or-later ticket, CCW-017, is `none`)
- [x] No files changed outside the paths listed under Touches (`git status`: 8 files, all listed)
- [x] `verification.md` opens with the `## Product owner review` section (AC-11)

## Open questions

None.
