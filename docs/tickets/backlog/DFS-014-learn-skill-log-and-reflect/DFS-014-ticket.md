---
id: DFS-014
title: Build the learn skill for the learnings log
status: in-progress
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [.claude/skills/learn, .claude/agents/reflect.md, .claude/skills/tickets/SKILL.md, docs/learnings]
blocked_reason:
created: 2026-09-21
updated: 2026-09-21
completed:
---

# DFS-014: Build the learn skill for the learnings log

## Story

As the maintainer of the project's process
I want a `learn` skill that records friction in the learnings log, lists and decides the open entries, and drafts entries from a finished ticket
So that friction found while working is not lost, recurring friction is counted, and improvements to the process are proposed with evidence and applied only with my approval

## Context

A stand-alone, hand-driven version of the learning loop in D6 (`docs/design/story-orchestration.md`), usable before the pipeline exists. The log is `docs/learnings/log.md` (header: rules, statuses, entry format; entries `L-<n>`, seven so far, L-001, L-005 and L-007 applied). The `tickets` skill's `done` mode already asks whether any friction is worth logging. The `retro` agent (DFS-011) and `/story learn` (DFS-012) are the pipeline versions; they read and extend the same file and keep its format, and they can reuse this skill's procedure.

Rules that carry over from D6: every proposal cites evidence; nothing is applied without the user's approval; a recurring parked entry is re-proposed with the recurrence as evidence; changes to shared files (`CLAUDE.md`, agent prompts, skills) are flagged as higher impact. Evidence comes from files only (tickets, commits, the log), never from a session transcript.

**Decisions made while refining this ticket:**
- **Scope (decided 2026-09-21):** the skill manages the log (`add`, `review`, `decide`, `apply`) and has a `reflect <ID>` mode that finds friction itself: it reads a ticket (deviations, evidence, done-item notes), the commits made for it and the current log, drafts entries with evidence and a recommendation, folds repeats into existing entries, and writes only what the user approves. `reflect` is the only mode that spends tokens, and it runs only when called. Rejected: managing the log only (nothing would notice friction unless someone remembers; the gap the log leaves open); automatic capture through hooks (interrupts every stop, a hook cannot judge what counts as friction, and six entries do not justify it). If `reflect` proves noisy, it can be dropped and the log modes kept.
- **How `reflect` runs (decided 2026-09-21; the mechanism was amended by the next decision):** as a forked skill (`context: fork`) on a cheap model at low effort (Sonnet, low, matching the planned `retro` agent), reading only files. A fork does not receive the conversation history, which enforces the files-only evidence rule structurally, and it returns one compact message of proposals that the main session then walks the user through. The docs (read 2026-09-21, through a summarising fetch, so re-check the exact wording when building) say a skill can set its own `model` and `effort`, and with `context: fork` the model applies to the forked subagent. Rejected: inline in the main session (runs on the expensive model, piles tokens into the main context, and tempts use of what the session remembers, which is how a wrong scenario count was repeated); both with a flag (two code paths to build and test before either is known to be used).
- **Mechanism for `reflect` (decided 2026-09-21, amends the previous decision):** the previous decision assumed a skill can be forked for one mode, but `context: fork` applies to a whole skill (reading of the docs, to confirm when building), and `add`, `decide` and `apply` need a conversation with the user. So `learn` is an inline skill, and `/learn reflect <ID>` launches an agent defined in `.claude/agents/reflect.md` (Sonnet, low effort, 15 turns, reading only the files it is told to). The agent returns a compact list of proposals, and the skill, still in the main session, walks the user through each one with the `add` matching and writes only the approved entries. The goal of the previous decision stands: a fresh, cheap, files-only run. This is the same pattern as D1 (a skill in the main session launching agents), and the agent is the seed of the `retro` agent, so DFS-011 builds on it (noted there). Rejected: two skills, `learn` and a forked `learn-reflect` (two commands, and the forked run ends with one message, so approving each proposal would mean running `add` again); inline `reflect` (loses the fresh context, the cheap model and the files-only guarantee).
- **Relation to `/story learn` (decided 2026-09-21):** `learn` stays the only writer of the log. DFS-012's `/story learn <ID>` is a thin wrapper: it runs the `retro` agent, which appends `proposed` entries in the log's format, then hands over to `/learn review`, `decide` and `apply`. So those modes work on any entries in the log, whoever appended them, and friction from hand-built work and from stories goes through one implementation of ranking, recurrence and editing. Rejected: `/story learn` replacing `learn` (the log modes would be rebuilt inside a skill about stories, and hand-built tickets such as DFS-008 need them too); leaving it to DFS-012 (that ticket may reinvent the log handling). Noted in DFS-012.
- **Scenarios and acceptance test (decided 2026-09-21):** 12 criteria and 21 scenarios (counted mechanically), including AC-12, added because `decide` and D6's "a rejected entry is not proposed again" had no criterion. The skill and the `reflect` agent are prose run by a model, so the tests are walkthroughs, plus one real run of `/learn reflect DFS-008` as the acceptance test of `reflect`'s judgement: every finding must match L-001, L-002 or L-003 as already logged, and nothing may be added or counted again, since it is the same ticket. Rejected: the same without the real run (the part most likely to be wrong would go untested); a core set of about 12 scenarios (drops the edge cases where such a skill goes wrong: the read cap, a declined edit, the version bump, no friction found). A pass on one ticket shows `reflect` is not obviously broken, not that it is good.
- **What `reflect` reads (decided 2026-09-21):** the ticket; the ticket file's own history since the commit that set it `ready` (its diffs, capped at 400 lines as a starting figure, and if more it lists the files and says the read was cut); the list of commits found by `git log --grep=<ID>` (subject and file list, no diffs); and the log. What changed after a ticket was declared fully refined is the best friction signal, including changes nobody wrote down, and it works from the ticket's path. Rejected: the ticket and commit list only (misses unwritten friction, and depends on commit messages naming the ID); an explicit range from the user (pushes finding the range onto the user, and the skill could not run quietly from `done`). **Commit convention (decided 2026-09-21, made in `CLAUDE.md`, see log entry L-007):** a commit made for a ticket ends its subject line with the ticket ID in brackets, so the ID search finds it. Earlier commits are not rewritten; those without the ID are found through their message body or the ticket's history.
- **Recurrence detection in `add` (decided 2026-09-21):** the skill proposes up to three candidate entries by comparing the description with the entries' titles, changes and targets, and shows each candidate's `Change` and `Evidence` lines when asking "same friction?". The user answers yes, no, or show all (the full list of open entries). On yes, it adds a dated line under that entry's Recurrences and increments the count, and writes no new entry. On no, it writes a new entry with the next `L-<n>`. Rejected: asking the user to scan the full list every time (skimmed at scale); matching on the target file only (misses the same cause in another file, and the target is often unknown until the change is worked out). The main risk is a lazy "yes" merging two different frictions; showing the candidate's lines and adding a dated, easily reverted recurrence line limits it.
- **Ranking and counting in `review` (decided 2026-09-21):** recurrences are counted in distinct tickets; a repeat within the same ticket is noted on that ticket's line and does not raise the count. `review` lists the open entries (`proposed` or `parked`) ranked by that count, then shared-impact entries first, then by number, with columns for ID, title, status, recurrences, impact and recommendation. The log's header and entry format were updated with this rule when it was decided, and L-006 (three repeats in one session) was corrected to a count of 1. Rejected: grouping by recommendation then recorded count (keeps the inflated count, and ranks by the skill's own recommendation, the thing being decided); no ranking (D6 asks for ranked proposals, and the list will not stay short).
- **`done` offers `reflect` (decided 2026-09-21):** after closing a ticket, `/tickets done` ends with one line offering `/learn reflect <ID>`, naming the exact command for the user to type (the skill is user-invoked only, see the next decisions), and launches nothing itself. It keeps the existing "any friction worth logging?" question. Rejected: keeping only the prompt (relies on remembering, so the loop stays as manual as today); running `reflect` automatically on every closed ticket (a fork run per ticket including trivial ones, and D6's "only when there is something to learn" condition does not exist yet). If the offer is always accepted, that is the evidence for making it automatic; if always skipped, `reflect` is noisy or not needed.
- **Placement, name and invocation (decided 2026-09-21):** a project skill at `.claude/skills/learn/`, named `learn` (modes as arguments, like `/tickets`), with `disable-model-invocation: true`, so only the user runs it. The `done` offer therefore prints the command for the user to type. Rationale: no standing token cost from a description in every session (I believe this setting keeps the description out of context; confirm in the docs when building), and nothing writes to shared files or spends tokens unless the user typed it. It is committed with the repo, so the pipeline's `retro` can reuse it. Rejected: also model-invocable (a smoother "yes", but a standing description cost that D4 avoided, and the model could start `reflect` or write entries on its own judgement); a user-level skill like `blog-capture` (the log and the files it edits are project files, and the pipeline could not build on it). Can be relaxed later if the offer is always accepted.
- **Checking the log (decided 2026-09-21):** every mode that writes re-reads the entry it changed and the entry numbering, and reports a problem before finishing: a missing field, a reused or skipped number, an `applied` entry with no decision line, a recurrence count that does not match its lines. There is no separate check mode yet. This is a weak check, since the model that wrote the entry also checks it and shares its blind spots; the ticket says so plainly. Rejected: a separate `/learn check` mode (another mode to build and keep in step, run only if remembered, and the log is small); no checking (silent drift, which is what `validate` was added to prevent for tickets). Add the mode if the log drifts.
- **How `apply` works (decided 2026-09-21):** the skill drafts the exact edit for the entry's `Change` line and shows it compactly (the file, and the lines to add or change); the user approves or adjusts it; only then does it write the file. It marks the entry `applied` with the decision date and the planned commit subject `docs: apply L-<n>` (several entries: `docs: apply L-001 and L-005`), and leaves committing to the user, since commits are made only when asked. If the target is under `.claude/archetypes/`, it also bumps that archetype's `version` in `archetype.md` (D6). The planned subject, not a hash, is recorded, because a commit cannot contain its own hash and the subject is searchable. Rejected: writing directly with `git diff` as the preview (the wording lands before the user sees it, and an entry marked `applied` on a rejected edit must be undone in two files); a follow-up commit that fills in the hash (two commits per application, and the subject already finds the commit).

## Acceptance criteria

The full ID of each criterion is `DFS-014.AC-<n>`.

- **AC-1** `reflect <ID>` drafts log entries from the ticket, its commits and the current log, folds repeats into existing entries, and writes only what the user approves.
- **AC-2** `reflect` runs as an agent (`.claude/agents/reflect.md`, Sonnet, low effort, 15 turns) launched by the inline skill, with no access to the conversation history, so its evidence is limited to the files it reads; the skill then walks the user through its proposals in the main session.
- **AC-3** `reflect` reads only the ticket, the ticket file's diffs since it became `ready` (capped), the commit list found by searching the ticket ID, and the log, and says so when the cap cut a read.
- **AC-4** `add` proposes up to three matching entries with their `Change` and `Evidence` lines, and on the user's yes adds a dated line under that entry's Recurrences and increments the count instead of writing a new entry; on no it writes a new entry with the next unused number.
- **AC-5** `apply L-<n>` shows the exact edit before writing anything, writes the target file only after the user approves, then marks the entry `applied` with the date and the planned commit subject `docs: apply L-<n>`, and does not commit.
- **AC-6** `review` lists the open entries ranked by recurrences in distinct tickets, then shared-impact entries first, then by number.
- **AC-7** A recurrence is counted per distinct ticket: a repeat within the same ticket is noted on that ticket's line and does not raise the count.
- **AC-8** `/tickets done` ends with a one-line offer that names the exact command `/learn reflect <ID>`, and runs nothing itself.
- **AC-9** The skill is a project skill at `.claude/skills/learn/` named `learn`, invoked only by the user (`disable-model-invocation: true`), with the modes `add`, `review`, `decide`, `apply` and `reflect`.
- **AC-10** Every mode that writes to the log re-reads the changed entry and the numbering afterwards, and reports a missing field, a reused or skipped number, an `applied` entry without a decision line, or a recurrence count that does not match its lines.
- **AC-11** `review`, `decide` and `apply` work on any entries in the log, whoever appended them, so the pipeline's `/story learn` can delegate to them.
- **AC-12** `decide L-<n> park|reject` records the decision with its date and changes no target file, and `reflect` does not re-propose a rejected entry.

## Scenarios

```gherkin
Feature: The learn skill

  @AC-1
  Scenario: Reflect drafts an entry with evidence and writes nothing before approval
    Given a finished ticket whose file records a deviation that is not yet in the log
    When `/learn reflect <ID>` runs
    Then the proposals include an entry citing the ticket and the deviation as evidence, with a recommendation
    And the log file is unchanged until the user approves

  @AC-1
  Scenario: A repeat is folded into an existing entry
    Given the log holds parked entry L-002 and a second ticket's history shows the same friction
    When reflect runs on the second ticket
    Then it proposes adding that ticket to L-002's Recurrences (count 2) and re-proposes L-002 with the new evidence
    And it does not propose a new entry

  @AC-1
  Scenario: No friction found writes nothing
    Given a ticket with no recorded deviations, no changes after it became `ready`, and nothing new
    When reflect runs
    Then it reports that no friction was found
    And the log is unchanged

  @AC-2
  Scenario: The agent definition states its limits and its inputs
    Given `.claude/agents/reflect.md`
    When it is read
    Then it sets model sonnet, effort low and maxTurns 15
    And it names the files it reads (the ticket, its history, the commit list, the log) and says the conversation is not evidence

  @AC-3
  Scenario: A long ticket history is cut and reported
    Given a ticket file whose diffs since it became `ready` total 900 lines
    When reflect reads the history
    Then it reads 400 lines, lists the files it did not read, and says the read was cut

  @AC-3
  Scenario: Commits are found by ticket ID in the subject or the body
    Given one commit whose subject ends `[DFS-014]` and one commit whose body mentions DFS-014 only
    When reflect lists the ticket's commits
    Then both are listed with their subject and file list, and no diff is read

  @AC-4
  Scenario: A confirmed match adds a recurrence
    Given a friction that resembles L-002 and occurred in a ticket not yet listed there
    When `/learn add` shows L-002's Change and Evidence lines and the user answers yes
    Then a dated line for that ticket is added under L-002's Recurrences and the count rises by one
    And no new entry is written

  @AC-4
  Scenario: No match writes the next unused number
    Given a friction that matches no entry, and the highest existing entry is L-007
    When the user answers no to every candidate
    Then entry L-008 is written with every field and status proposed

  @AC-5
  Scenario: Apply previews, waits for approval and does not commit
    Given entry L-002 recommended apply, with target `.claude/skills/tickets/SKILL.md`
    When `/learn apply L-002` runs
    Then it shows the exact lines to add and writes the file only after the user approves
    And L-002 is marked applied with the date and the planned subject `docs: apply L-002`
    And no commit is made

  @AC-5
  Scenario: A declined edit changes nothing
    Given the preview for `/learn apply L-002` is shown
    When the user declines
    Then neither the target file nor the log has changed, and L-002 keeps its status

  @AC-5
  Scenario: An archetype target gets its version bumped
    Given an entry whose target is `.claude/archetypes/pipeline-stage/impl-guide.md`, and the archetype's `version` is 1
    When the user approves the edit
    Then `archetype.md` shows `version: 2`

  @AC-6
  Scenario: Review ranks by distinct tickets, then shared impact, then number
    Given open entries L-004 (1 ticket, shared), L-006 (2 tickets, local), L-002 (1 ticket, shared) and L-003 (1 ticket, shared)
    When `/learn review` runs
    Then the order is L-006, L-002, L-003, L-004

  @AC-7
  Scenario: A repeat within one ticket does not raise the count
    Given L-006 with Recurrences 1 (DFS-008) and the same friction occurring again during DFS-008
    When it is added
    Then the DFS-008 line records the repeat and the count stays 1

  @AC-7
  Scenario: A repeat in another ticket raises the count
    Given L-006 with Recurrences 1 (DFS-008) and the same friction in DFS-014
    When it is added
    Then a DFS-014 line is added and the count is 2

  @AC-8
  Scenario: Done offers the command and runs nothing
    Given a ticket ready to close with every done-item ticked
    When `/tickets done DFS-014` completes
    Then its last line offers `/learn reflect DFS-014`
    And no agent has been launched and the log is unchanged

  @AC-9
  Scenario: The skill is a project skill that only the user can invoke
    Given `.claude/skills/learn/SKILL.md`
    When its frontmatter and body are read
    Then it has the name `learn` and `disable-model-invocation: true`
    And it describes the modes add, review, decide, apply and reflect

  @AC-10
  Scenario: A reused number is reported after a write
    Given a log in which two entries are numbered L-005
    When any write mode finishes
    Then it reports the duplicate L-005

  @AC-10
  Scenario: An applied entry without a decision line is reported
    Given an entry with status applied and an empty Decision line
    When any write mode finishes
    Then it reports that entry and the missing decision

  @AC-11
  Scenario: Entries appended by another author are handled like any other
    Given entry L-009 appended by the retro agent in the log's format
    When `/learn review`, `decide` and `apply` are run on it
    Then each works as it does for an entry written by `add`

  @AC-12
  Scenario: Decide records park and reject with the date
    Given open entries L-004 and L-006
    When the user runs `/learn decide L-004 park` and `/learn decide L-006 reject`
    Then each entry shows its new status and a Decision line with today's date
    And no target file has changed

  @AC-12
  Scenario: A rejected entry is not proposed again
    Given L-004 has status rejected and a ticket's history shows the same friction
    When reflect runs on that ticket
    Then the finding is not proposed
    And the report says it matches rejected L-004
```

## Scope

**Touches:**
- `.claude/skills/learn/`: the skill
- `.claude/agents/reflect.md`: the agent `reflect` launches; the seed of the `retro` agent (DFS-011 builds on it)
- `.claude/skills/tickets/SKILL.md`: the `done` mode offers `reflect`
- `docs/learnings/`: the log (entries added or decided by the skill; format changes only if a decision requires them)

**Out of scope:**
- The `retro` agent (DFS-011) and `/story learn` (DFS-012)
- Hooks or any automatic capture
- Applying a change without the user's approval

## Definition of done

- [x] A walkthrough of each scenario on a scratch copy of the log and a sample ticket shows its outcome (evidence noted in this ticket)
  - Evidence: see "Walkthrough evidence" below.
- [ ] One real run of `/learn reflect DFS-008` on the finished ticket meets this pass condition, with the result noted here: every finding is matched to L-001, L-002 or L-003 as already logged; nothing is added and no recurrence count changes (same ticket); any other proposal cites evidence a reviewer can verify in the files
  - Not yet met. A stand-in run was made on 2026-09-21 (see "Reflect run" below) because the `reflect` agent is not registered until the session reloads its agent definitions. The real run of `/learn reflect DFS-008` is still to do; tick this item only after it.
- [x] The docs claims this design rests on are confirmed when building: `context: fork` applies to a whole skill, `disable-model-invocation` keeps the description out of context, and an agent's effort can be set (result noted here; if one is wrong, the affected decision is amended)
  - Evidence: read 2026-09-21 on the Claude Code docs (skills and sub-agents pages) with a fetch that asked for verbatim quotes. All three hold, so no decision is amended. `context: fork` is one frontmatter field of the whole skill ("Set to `fork` to run in a forked subagent context"), and the docs say the subagent "doesn't see your conversation history". `disable-model-invocation: true` is listed with "Description not in context, full skill loads when you invoke". A subagent definition takes `effort` (`low` to `max`), `model` and `maxTurns`. Not confirmed by the docs: that a skill can launch a subagent by name (the page does not address it); the `reflect` run below is the test of that. Still a summarising fetch, not a reading of the page.
- [x] The skill writes nothing outside `docs/learnings/` and the target file the user approved
  - Evidence: the skill's Rules limit writes to the log and the approved target (plus `archetype.md` for an archetype target), and forbid commits. In the walkthrough the only files written were the scratch log and the scratch target.
- [x] No files changed outside the paths listed under Touches
  - Evidence: `git status` at the end shows this work changed `.claude/skills/learn/` (new), `.claude/agents/reflect.md` (new), `.claude/skills/tickets/SKILL.md` and this ticket. `docs/learnings/log.md` was not touched by this work (another session's L-008 was already committed as `58f9ee5`).

## Open questions

None.

## Walkthrough evidence

Run on 2026-09-21 on scratch copies (the real log was not written). The skill and the agent are prose, so the checks are of three kinds, and only the first two run anything.

- **The mechanical rules, by a throwaway script** (not kept in the repo): it transcribes the skill's numbering, ranking, recurrence, `decide`, `apply` and check rules and runs them on the log as committed, with L-008 (added by another session while this was built) cut so that "the highest entry is L-007" holds. 29 checks, 0 failed, covering: AC-4 (a yes adds a recurrence and no entry; a no takes number 8), AC-1's repeat (a parked L-002 that recurs becomes `proposed`, count 2, no new entry), AC-5 (declined changes nothing; approved writes the target and marks the entry with the planned subject `docs: apply L-002`; an applied entry cannot be applied again), AC-6 (order L-006, L-002, L-003, L-004), AC-7 (same ticket keeps the count, another ticket raises it), AC-10 (a reused number, a skipped number, an applied entry without a decision, a count that does not match, a missing field, each reported), AC-11 (an entry from another author works in review, decide and apply), AC-12 (park and reject record the date, change nothing else, and take the entry out of review). The archetype version bump is checked only as arithmetic on a sample frontmatter.
- **The git commands `reflect` uses, on a scratch repository** with a ticket that moved from `backlog/` to `completed/`: the commit that set `status: ready` is the oldest one `git log --follow -G'^status: ready'` lists; a history of 947 lines after it is cut to 400 by `head -400` (AC-3, the cut); a commit naming the ID in the subject and one naming it only in the body are both found by `--grep` (AC-3, the ID search); no diff is read.
- **Text checks by `grep`:** the agent file sets `model: sonnet`, `effort: low`, `maxTurns: 15` and says the conversation is not evidence (AC-2); the skill is named `learn`, has `disable-model-invocation: true` and one section for each of the five modes (AC-9); the `done` mode ends with the offer `/learn reflect <ID>` and says to launch nothing (AC-8). These show the words are present, which is weaker than running them.
- **Not run by anything:** the agent-side scenarios "A repeat is folded", "No friction found writes nothing" and "A rejected entry is not proposed again" are covered only by the agent's written matching rules and by the stand-in run's behaviour on DFS-008 (below). "Reflect drafts an entry with evidence and writes nothing before approval" is by construction (the agent is told to write nothing and returns a report); its tools include Bash, so this is an instruction, not a permission.

The script shows the rules are unambiguous. It does not show that a model follows them.

**Deviations found while implementing:**
- **A bare count needs a named ticket.** Entries such as L-002 have `Recurrences: 1` with no ticket named, so adding a second ticket had nothing to append to. Found by the script; the skill now says to name the original ticket from the entry's Evidence first (`2 (DFS-008: see Evidence; DFS-099: ...)`). The check accepts a bare 1, and requires a count above 1 to name that many distinct tickets.
- **"A dated line under Recurrences" is a dated item in the one-line field.** The existing entries keep Recurrences on one line, and rewriting them was outside the ticket. The item carries the ticket, the event and the date.
- **`--reverse` breaks `--follow`.** `git log --follow --reverse` returned only the newest commit, so the agent finds the `ready` commit as the oldest match of an unreversed list, and the skill's prompt says not to add `--reverse`. Found by testing the commands on DFS-008, not from the docs.
- **The log gained L-008 and DFS-015 from another session while this was built.** Not touched here; the walkthrough used the committed log with L-008 cut.
- **`/tickets done` now points at `/learn add` instead of writing the entry itself,** because the ticket says `learn` is the log's only writer and `add` is what checks for recurrences. The ticket's AC-8 asked only for the offer; this follows from the writer decision, and is stated here so it can be reversed.
- **The agent is not registered until agent definitions reload.** `Agent` with `subagent_type: reflect` failed with "Agent type 'reflect' not found" in the session that created the file. The docs page was not checked for how to reload; a new session or `/agents` is the likely route.

## Reflect run

A **stand-in** run, not the real one: a general-purpose Sonnet agent was told to read `.claude/agents/reflect.md` and follow it as its system prompt, on `DFS-008`. It has all tools and no turn cap, so it tests the prompt's judgement, not the agent definition (model, effort, tools, turn limit) or that the skill can launch the agent by name. It took 9 tool calls and about 58,000 tokens.

Result: history 71 of 71 lines, not cut; 3 commits found; 4 findings, all with evidence that was then checked against the files (every quoted line and hash exists).
- Three matched entries already in the log and asked for nothing to be added: L-003 and L-002 (both name DFS-008 and commit `ac2fcd1`), and L-005 (its evidence is the same commit). The first two are the ones the pass condition names; the third is a fourth entry the condition did not list, correctly matched, and applied, so nothing to decide.
- One finding was new: that scenarios about prose instructions are checked only by reading, not running. Its evidence is real (the DFS-008 walkthrough section says so in those words) but it restates a limit the ticket states openly, so whether it is friction or noise is a judgement for the user. It was not written to the log.
- One flaw in the report: it recommended `reject` for the already-applied L-005. The agent prompt now says the recommendation is `none` for `already counted` and `matches rejected`.

Against the pass condition: nothing was added and no count changed; every finding was matched or carries checkable evidence. The condition named L-001, L-002 and L-003; one finding matched L-005 and one was new, so it holds with those two stated. Not a pass of the real run.
