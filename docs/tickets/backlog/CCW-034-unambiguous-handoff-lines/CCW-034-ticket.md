---
id: CCW-034
title: Make every printed hand-off unambiguous
status: ready
type: chore
scope: [docs]
archetype: none
priority: medium
lane: harness
depends_on: []
touches:
  - .claude/skills/tickets/SKILL.md
  - .claude/skills/story/SKILL.md
  - .claude/skills/learn/SKILL.md
  - CLAUDE.md
  - docs/tickets/lanes.md
  - docs/design/story-orchestration.md
  - tools/story-gate/CatchUp.java
  - tools/story-gate/CatchUpSelfTest.java
blocked_reason:
created: 2026-11-12
updated: 2026-11-12
completed:
---

# CCW-034: Make every printed hand-off unambiguous

## Story

As the user running one session per ticket
I want every hand-off a skill prints to name one clear next step, a session name that says what it runs, and whether I may close the current session
So that I never have to guess which of several steps comes first or whether closing a session loses anything

## Context

User feedback 2026-11-12 ("not a single occurrence"): a pasted DFS-067 hand-off mixed a pending decision, three chained steps ("run /learn review ..., apply it yourself, then /tickets done") and one launch line, and did not say whether the session could be closed. The session name was the bare ticket ID, so `DFS-067` named three different sessions.

Three changes, to the hand-offs printed by `refine`'s ready commit, every story stop, `done`, `list` and `learn`:

1. Launch lines name the session `<ID>-<command>` (see Decision 1), not the bare ID.
2. A hand-off with several steps numbers them, marks exactly one "NEXT (do this now)" and the rest "later", each with its own launch line. A pending user decision is the only NEXT, with no launch line until answered.
3. A hand-off says whether the current session is safe to close (nothing uncommitted of the next step's, nothing only in chat) or must stay and why.

CLAUDE.md's Tickets paragraph (CCW-026: "named after the ticket", `claude -n <ID>`) must allow `<ID>-<command>` names. `CatchUp.sessionName` maps only `dfs-product` to `coordinator` (`lanes.md`), so `<ID>-<command>` names cannot collide with it; AC-3 pins that.

`CatchUp.launchLine` (`CatchUp.java:106`) builds the line from the bare ID and is used by `laneLine`; `CatchUpSelfTest.java:200-202` asserts the old form. `learn/SKILL.md` prints no launch line today.

Interim: the assistant's own hand-offs already follow this (memory `feedback-always-suggest-next-step`).

## Decisions

1. **Session-name suffixes mirror the command** (2026-11-12): `<ID>-story-run`, `<ID>-story-approve`, `<ID>-story-learn`, `<ID>-start`, `<ID>-done`, `<ID>-learn-review`, `<ID>-learn-reflect`. Checked: `CatchUp.sessionName` maps only `dfs-product` to `coordinator`, so these names cannot collide with it. `learn/SKILL.md` prints no launch lines today, so it gains a hand-off section.
2. **Archetype `none`** (2026-11-12): implemented by hand, no pipeline, so no `gates`. The `CatchUp.java` line builder is checked mechanically by extending `CatchUpSelfTest`; skill and doc wording is checked by reading or by grep, recorded per scenario.
3. **One ticket** (2026-11-12): the `CatchUp.java` change is not split off, so the printed lines and the code that generates them change together.

## Acceptance criteria

The full ID of each criterion is `CCW-034.AC-<n>`.

- **AC-1** `CatchUp.launchLine` names the session `<ID>-<suffix>` from the command it runs: `/story run` `story-run`, `/story approve` `story-approve`, `/story learn` `story-learn`, `/tickets start` `start`, `/tickets done` `done`, `/learn review` `learn-review`, `/learn reflect` `learn-reflect`; `--model sonnet` still appears only for a pipeline archetype.
- **AC-2** `catch-up --lanes` prints each started ticket's next step as a launch line with the `<ID>-<suffix>` name.
- **AC-3** The coordinator mapping is unchanged: a session titled `dfs-product` still gets the catch-up, and sessions titled `DFS-067-done` or `CCW-034-story-run` get nothing from the hook (also through the `DFS_SESSION` fallback).
- **AC-4** No launch-line example or rule in `.claude/skills/`, `CLAUDE.md`, `docs/tickets/lanes.md` or `docs/design/story-orchestration.md` still names a session by the bare ID (`claude -n <ID>`, `claude -n <TICKET-ID>`, `claude -n CCW-026`).
- **AC-5** The `tickets` skill defines one "Hand-offs" rule, which the `story` and `learn` skills refer to: several steps are numbered, exactly one is marked "NEXT (do this now)", the rest "later", each with its own launch line.
- **AC-6** The rule says a pending user decision is the only NEXT step and carries no launch line until the user has answered.
- **AC-7** The rule requires every hand-off to end with "Safe to close this session: yes" (nothing uncommitted of the next step's, nothing only in chat) or "no: <reason>".
- **AC-8** `refine`'s ready commit, every story stop, `done`, `list` and the `learn` modes `review` and `reflect` each name the rule in their instructions; `learn` gains the launch lines `<ID>-learn-review` and `<ID>-learn-reflect`.
- **AC-9** CLAUDE.md's Tickets paragraph and `lanes.md` state that a ticket session is named `<ID>-<command>`.

## Scenarios

Run mechanically = `CatchUpSelfTest` or a grep, run before `review`. Read = checked by reading the instruction, recorded in `verification.md`.

```gherkin
Feature: Unambiguous hand-offs

  Background:
    Given the repository checkout with tickets DFS-067 (archetype none) and CCW-062 (archetype pipeline)

  # mechanical: CatchUpSelfTest
  @AC-1
  Scenario: Each command gets its own session name
    When launchLine is called for DFS-067 with "/tickets done DFS-067"
    Then the line is `claude -n DFS-067-done --permission-mode auto "/tickets done DFS-067"`
    And launchLine for "/story run", "/story approve", "/story learn", "/tickets start DFS-067 --worktree", "/learn review" and "/learn reflect" use the suffixes story-run, story-approve, story-learn, start, learn-review, learn-reflect

  # mechanical: CatchUpSelfTest
  @AC-1
  Scenario: A pipeline ticket keeps --model sonnet
    When launchLine is called for CCW-062 with "/story run CCW-062" and archetype "wiring"
    Then the line is `claude -n CCW-062-story-run --model sonnet --permission-mode auto "/story run CCW-062"`

  # mechanical: CatchUpSelfTest
  @AC-2
  Scenario: catch-up --lanes prints the new names
    Given DFS-061 has a plan.json awaiting approval at its design gate
    When `catch-up --lanes` runs
    Then its next line is `claude -n DFS-061-story-approve --model sonnet --permission-mode auto "/story approve DFS-061"`

  # mechanical: CatchUpSelfTest
  @AC-3
  Scenario: Only the coordinator name triggers the hook
    When the hook input's session_title is "dfs-product"
    Then the hook prints the catch-up
    When the session_title is "DFS-067-done", or is missing and DFS_SESSION is "CCW-034-story-run"
    Then the hook prints nothing

  # mechanical: grep over the touched files
  @AC-4
  Scenario: No bare-ID launch line is left
    When `grep -rnE 'claude -n (<ID>|<TICKET-ID>|[A-Z]{3}-[0-9]{3})[ "]'` runs over .claude/skills, CLAUDE.md, docs/tickets/lanes.md and docs/design/story-orchestration.md
    Then it finds no match

  # read
  @AC-5
  Scenario: The hand-off rule is defined once and reused
    When the tickets skill's "Hand-offs" section is read
    Then it requires numbered steps, exactly one "NEXT (do this now)", the rest "later", each with a launch line
    And the story and learn skills point to it instead of restating it

  # read
  @AC-6
  Scenario: A pending decision is the only NEXT
    Given a hand-off with an open question and two later commands
    When the rule is applied
    Then the question is step 1 "NEXT (do this now)" with no launch line, and both commands are "later"

  # read
  @AC-7
  Scenario: The hand-off says whether to close the session
    Given a story stop whose plan.json was committed and nothing else is uncommitted
    When the hand-off is printed
    Then it ends with "Safe to close this session: yes"
    Given a decision agreed only in chat
    Then it ends with "Safe to close this session: no: <reason>"

  # read
  @AC-8
  Scenario: Every printing mode applies the rule
    When refine (ready commit), story "Every stop", done (step 4a), list and learn review and reflect are read
    Then each names the "Hand-offs" rule
    And done's several unblocked tickets are numbered with one NEXT and the others "later", or marked independent when their touches do not overlap

  # read
  @AC-9
  Scenario: The naming rule is stated where sessions are defined
    When CLAUDE.md's Tickets paragraph and lanes.md are read
    Then both say a ticket session is named `<ID>-<command>`, and CLAUDE.md no longer says "named after the ticket" alone
```

No safety invariant of `CLAUDE.md` (deletion, hard links, symlinks) applies: the ticket changes no product code.

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md`: "Hand-offs" rule; launch-line shape in `refine`, `list`, `done` and "Sessions per ticket"
- `.claude/skills/story/SKILL.md`: "Every stop" and the stop sections
- `.claude/skills/learn/SKILL.md`: hand-off for `review` and `reflect`
- `CLAUDE.md`: Tickets paragraph naming
- `docs/tickets/lanes.md`: naming sentence
- `docs/design/story-orchestration.md`: the two mentions of the launch line
- `tools/story-gate/CatchUp.java`: `launchLine`
- `tools/story-gate/CatchUpSelfTest.java`: new and updated assertions

**Out of scope:**
- Starting sessions automatically.
- Changing which session runs which command, or the coordinator's rules.
- The assistant's own memory feedback files (outside the repo).

## Definition of done

- [ ] Every scenario above is run mechanically or checked by reading, as marked, and recorded in `verification.md`
- [ ] `tools/story-gate/story-gate selftest` (which runs `CatchUpSelfTest`) passes
- [ ] Search of `.claude/`, `CLAUDE.md`, `docs/design/` and `tools/` for every other `claude -n` or "named after the ticket" mention, hits recorded in `verification.md`
- [ ] No files changed outside `touches` (the ticket folder's own files excepted)

## Open questions

None.
