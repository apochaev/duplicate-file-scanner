---
id: CCW-022
title: Add context-hygiene rules for when sessions compact or clear
status: in-review
type: docs
scope: [docs, build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: [CCW-021]
touches:
  - .claude/skills/story/SKILL.md
  - .claude/skills/tickets/SKILL.md
  - CLAUDE.md
  - tools/story-gate
  - docs/design/story-orchestration.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-07
completed:
---

# CCW-022: Add context-hygiene rules for when sessions compact or clear

## Story

As the user running dfs-backend, dfs-frontend, dfs-harness and the coordinator (dfs-product) as parallel sessions
I want each session to know when to /compact, when to /clear and what has to be written to disk before either
So that context, and with it the per-turn token cost and latency, stays small without losing state between phases or tickets

## Context

Refined 2026-11-02 against CCW-021 as made ready in `ba09b6a` (catch-up at session start and in the coordinator's `list`, decisions 8 to 13). Filed 2026-11-01 by dfs-product at the user's request, from a draft the user supplied; split out of CCW-021's re-refinement (the goal: parallel sessions optimized for token usage and speed).

- Every turn resends the whole context. Cached reads cost about 10% of normal input, but a 150k-token session still pays on that whole size every turn, and quality drops in long contexts. Measured from this project's transcripts (consult-001's question, `advisory/consultations/consult-001-question.md`): long-lived main sessions dominate; dfs-backend about 755M cache-read tokens over 2,915 calls, about 260k context per call; pipeline subagents under 10% of the total.
- `/compact`: one full pass over the context to write a summary. Lossy (exact errors, small decisions and standing instructions get dropped) and resets the prompt cache. Auto-compact fires near the limit, at a moment nobody chose.
- `/clear`: free. Keeps only the system prompt, CLAUDE.md and memory, and loses everything not on disk.
- The story pipeline already runs each phase in a subagent and resumes from `plan.json` and `progress.md` (`.claude/skills/story/SKILL.md`, "Resume protocol"). The context that grows is the orchestrating or coordinating main session, not the phase agents. So a lane session can already be cleared at a story stop and resumed with one command, independent of CCW-021.
- At a human gate the resume command is `/story approve <ID>`; a plain `/story run <ID>` only re-shows the gate (resume step 2). After a `blocked` stop it is `/story run <ID>`. Free-text gate feedback is today "read from the conversation" ("Human gate"), which a `/clear` loses; `/story approve <ID> <feedback>` already carries it as an argument.
- CCW-021's Context shows the failure this prevents: an instruction given in chat ("report every ticket change") was "lost when a session restarts or compacts". Any standing instruction must live in a skill, `lanes.md` or CLAUDE.md.
- A session cannot run `/clear` itself. The user does, so the session has to say when it is safe.
- CCW-021 (pull model) gives the coordinator a catch-up script that a SessionStart hook runs at startup and after `/clear`; this ticket relies on it for the coordinator's resume. Lane sessions need nothing from CCW-021.

## Decisions

1. **Clear at ticket boundaries; the orchestrator commits `plan.json` before every stop** (2026-11-02, confirmed in refine). After `/tickets done`, after `refine`'s ready commit (the coordinator's ticket boundary: it never runs `done` or `/story`, and CCW-021 decision 9 relies on it clearing after every refine), and at a story stop (human gate, blocked, finished). As the last step before every stop, the `story` skill commits `plan.json` and any other story-folder bookkeeping it wrote in the worktree, staged by name, as `docs: record <ID> <stop> in plan.json [<ID>]`, then prints `safe to /clear; resume with <command>`: `/story approve <ID>` (or `/story approve <ID> <feedback>`) at a gate, `/story run <ID>` after `blocked`, and at the finish `/story learn <ID>` when there is something to learn, otherwise `/tickets done <ID>`. Today no rule requires that commit; sessions made it ad hoc (DFS-047 `e11c5ef`, DFS-051 `e1ba3ee`, `ccae32e`), and the line would be false without it. Recommend `/clear` at every such stop, not only above a context size: a session cannot reliably see its own size (only the status line can), and the stop is where the user steps in anyway. Not a dirty-file warning at every stop with commits left to the user ("not safe: plan.json" at almost every stop) and not folding `plan.json` into the phase commit (it records that commit's own hash).
2. **Compact only in the middle of a ticket, with one shared template in the tickets skill** (2026-11-02, confirmed in refine). `/compact` is for the middle of a ticket, when the conversation still matters (a half-debugged failure). The template lives once, in the tickets skill's "Context hygiene" section: `/compact keep <ID>, step <current step or phase>, [open defect <id>: <summary>,] last failing output, current hypothesis, uncommitted files`; the story skill points to it in one line. Reason: during a pipeline phase the context that grows is the phase agent's (a subagent the user cannot compact), so the long sessions that hit auto-compact are mostly hand-implemented tickets and an orchestrator debugging a `blocked` phase. Not a template in the story skill only (misses hand-implemented tickets) and not "write it down and clear, never compact" (exact failing output and a half-formed hypothesis cost more to write than to keep).
3. **Unsafe means state only in chat; uncommitted files are listed, not blocking** (2026-11-02, confirmed in refine). `/clear` empties the conversation; files in the working tree survive it, committed or not. So before the line the session writes to disk anything the next session needs that lives only in the conversation: decisions into the ticket's Decisions section, phase notes into `progress.md`, open questions into the ticket. Uncommitted files of its own are listed beside the line as information: `safe to /clear (uncommitted, on disk: <files>); resume with <command>`. Gate feedback is given as `/story approve <ID> <feedback>`, replacing the story skill's "read from the conversation, not a flag" (`.claude/skills/story/SKILL.md`, "Human gate"; the only such mention under `.claude/`, `CLAUDE.md` and `docs/design/` on 2026-11-02). Not "any uncommitted file makes it unsafe" (wrong about what `/clear` loses, withholds the line for safe work) and not committing everything first (commits half-finished hand-implemented work, outside the narrow commit exceptions).
4. **Resume after a clear is one command; a lane session's `/tickets list` runs the catch-up for its own lanes** (2026-11-02, confirmed in refine; settles the question CCW-021 decisions 10 and 11 left here). Lane sessions: the stop's resume command, or `/tickets list`, which in a lane session also runs `story-gate catch-up` limited to that session's lanes and shows, for each started ticket in them, its worktree, `plan.json` state and phase, and the next command (`/story approve <ID>` at a gate, `/story run <ID>` when blocked or running, `/story learn <ID>` or `/tickets done <ID>` when done, `/tickets start <ID> --worktree` for an `archetype: none` ticket with no `plan.json`). The lane filter is a new option of the subcommand CCW-021 builds, with its own story-gate self-test cases. The coordinator: CCW-021's catch-up (its SessionStart hook), then `/tickets list`. Never `--resume` of an old transcript. Not the SessionStart hook in lane sessions (lines at every start, nothing in flight or not, and against CCW-021's AC-12) and not leaving it to the user's memory of the command (which `/clear` is what takes away). The worktree logic stays only in the script (CCW-021 decision 11).
5. **Keep what loads every turn small: three rules in the tickets skill, `CLAUDE.md` not slimmed here** (2026-11-02, confirmed in refine). The "Context hygiene" section states: `CLAUDE.md` holds rules, not ticket history; the coordinator's `/tickets list` (with its catch-up output) stays compact, IDs and states rather than ticket content; bulk reads (logs, a wide grep) go to a subagent that returns only its conclusion, a standing instruction that makes the harness's "spawn agents only when asked" default explicit for that case. Moving the existing history out of `CLAUDE.md` (its `server` paragraph, DFS-035 to DFS-041) is a separate later ticket, since that text is product design. Not dropping the rules (the coordinator's growing `list` is the likeliest to drift) and not slimming `CLAUDE.md` here (rewrites product text from a harness ticket).

## Acceptance criteria

The full ID of each criterion is `CCW-022.AC-<n>`.

- **AC-1** Each story stop (gate, blocked, finished) ends with a "safe to /clear" line and the resume command for that stop (`/story approve <ID>` at a gate, `/story run <ID>` after `blocked`, `/story learn <ID>` or `/tickets done <ID>` at the finish), and lists any uncommitted files of the session's own beside it as information (`safe to /clear (uncommitted, on disk: <files>)`); the line is withheld only while state the next session needs exists only in the conversation, which the session first writes to the ticket or `progress.md`.
- **AC-2** `/tickets done` and `refine`'s ready commit end with the same line (`safe to /clear; resume with <the next command>`), following decision 3's rule for uncommitted files.
- **AC-3** After a real `/clear` in a lane session mid-ticket, the stop's resume command resumes at the right phase with nothing re-asked.
- **AC-4** After a real `/clear` in dfs-product, the catch-up plus `/tickets list` reports the same backlog state as before.
- **AC-5** No skill or CLAUDE.md text relies on an instruction given only in conversation; gate feedback after a clear is passed as `/story approve <ID> <feedback>`.
- **AC-6** Before every story stop the `story` skill commits `plan.json` (and any other story-folder file it wrote) in the worktree as `docs: record <ID> <stop> in plan.json [<ID>]`, so `git -C <worktree> status --porcelain` prints nothing at the stop unless a phase agent left uncommitted work.
- **AC-7** The tickets skill's "Context hygiene" section holds the one `/compact` template (`<ID>`, current step or phase, optional open defect, last failing output, current hypothesis, uncommitted files) for the middle of a ticket, and the story skill refers to it instead of repeating it.
- **AC-8** In a lane session, `/tickets list` runs `story-gate catch-up` limited to that session's lanes and shows, for each started ticket in them, its worktree, `plan.json` state and phase, and its next command; tickets of other lanes do not appear in that output, and story-gate's self-tests include passing cases for the lane filter (a ticket in the lane shown, one in another lane left out).
- **AC-9** The tickets skill's "Context hygiene" section states that `CLAUDE.md` holds rules, not ticket history; that the coordinator's `list` output stays IDs and states, not ticket content; and that bulk reads (logs, a wide grep) go to a subagent that returns only its conclusion.

## Scenarios

```gherkin
Feature: Context hygiene

  @AC-1
  Scenario: [run] A gate stop says it is safe to clear
    Given a /story run reaches a human gate with plan.json, progress.md and the ticket committed
    Then the stop ends with "safe to /clear; resume with /story approve <ID>"

  @AC-1
  Scenario: [read] A stop with uncommitted work lists it beside the line
    Given the session's working tree holds uncommitted files of its own at a stop
    Then the stop says "safe to /clear (uncommitted, on disk: <files>); resume with <command>"
    And a decision agreed only in conversation is written to the ticket's Decisions section before that line

  @AC-6
  Scenario: [run] The stop commits plan.json first
    Given a /story run reaches a human gate in its worktree
    Then the newest commit on the ticket branch is "docs: record <ID> gate in plan.json [<ID>]"
    And git -C <worktree> status --porcelain prints nothing

  @AC-1
  Scenario: [read] The finish names learn or done
    Then the story skill's "Finishing" ends with "safe to /clear; resume with /story learn <ID>" when there is something to learn, otherwise with /tickets done <ID>

  @AC-7
  Scenario: [read] One compact template, shared
    Then the tickets skill's "Context hygiene" section holds the /compact template with ID, step, optional open defect, last failing output, current hypothesis and uncommitted files
    And the story skill names that section instead of repeating the template

  @AC-8
  Scenario: [run] A cleared lane session finds its stories with one command
    Given dfs-backend has a story worktree whose plan.json state is awaiting-approval after its design gate, and dfs-frontend has one of its own
    When the user runs /clear in dfs-backend and then /tickets list
    Then the output names dfs-backend's worktree with state awaiting-approval, gate design, and "/story approve <ID>"
    And it does not name dfs-frontend's worktree

  @AC-8
  Scenario: [run] story-gate's self-tests cover the lane filter
    When story-gate's self-test harness runs
    Then it passes, including a case where a ticket in the requested lane is shown and one in another lane is left out

  @AC-9
  Scenario: [read] The per-turn load rules are written down
    Then the tickets skill's "Context hygiene" section states the three rules: CLAUDE.md holds rules not ticket history, the coordinator's list stays IDs and states, bulk reads go to a subagent that returns its conclusion

  @AC-2
  Scenario: [read] done and refine end with the clear line
    Then the tickets skill's done and refine's ready commit end with the "safe to /clear" line and the next command

  @AC-3
  Scenario: [run] A lane session resumes after a real clear
    Given a lane session stopped at a gate and the user ran /clear
    When the user runs /story approve <ID>
    Then the run continues at the next phase with nothing re-asked

  @AC-4
  Scenario: [run] The coordinator resumes after a real clear
    Given dfs-product's /tickets list output before a /clear
    When the user runs /clear and then /tickets list
    Then the catch-up and the list report the same backlog state

  @AC-5
  Scenario: [read] No standing instruction lives only in chat
    Then the story skill's gate text names /story approve <ID> <feedback> for feedback after a clear
    And no text under .claude/ or CLAUDE.md still says gate feedback is read from the conversation
```

## Scope

**Touches:**
- `.claude/skills/story/SKILL.md`: the `plan.json` commit before every stop, the stop text (gate, blocked, finished), a pointer to the `/compact` template, gate feedback after a clear.
- `.claude/skills/tickets/SKILL.md`: a "Context hygiene" section (the `/compact` template), with the `done` ending, the coordinator's resume, and `list` running the catch-up for a lane session's own lanes.
- `CLAUDE.md`: one line in Tickets.
- `docs/design/story-orchestration.md`: an addendum to D12 ("Every stop shows a next step"), whose gate line still reads "`/story run <ID>` again after approval": the stop ends with the "safe to /clear" line and the resume commands of decision 1, and `plan.json` is committed before the stop. Found by searching `.claude/`, `CLAUDE.md` and `docs/design/` for the stop text and gate feedback (CCW-018.L-1).
- `tools/story-gate`: a lane filter on the `catch-up` subcommand (built by CCW-021) and its self-test cases.

**Out of scope:**
- Measuring token use (CCW-006 left token counting unsettled).
- Automating `/clear`.
- Changing the phase agents.
- The coordinator's catch-up script and hooks (CCW-021), except the lane filter on `catch-up` (decision 4).
- Moving the existing ticket history out of `CLAUDE.md` (decision 5; a later ticket).

## Definition of done

- [x] Every scenario above is checked as marked (`[run]` exercised for real, `[read]` read) and recorded in `verification.md` (user accepted AC-1, AC-3, AC-4 and AC-6 as read-only on 2026-11-07; AC-8 was run, see verification.md)
- [x] No files changed outside the paths listed under Touches
- [x] story-gate's self-tests pass, including the lane-filter cases (AC-8)
- [x] The search of `.claude/`, `CLAUDE.md` and `docs/design/` for the old stop text and "read from the conversation" is repeated before `review` and its hits recorded in `verification.md` (CCW-018.L-1)
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
