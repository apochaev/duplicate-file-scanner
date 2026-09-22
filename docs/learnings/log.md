# Learnings log

Friction found while working on this project, with a proposed change and what was decided. This is the manual stand-in for the `retro` agent and `/story learn` (design decision D6 in `docs/design/story-orchestration.md`, built by DFS-011 and DFS-012). When those exist they read and extend this same file, so keep the entry format.

## Rules

- **Nothing is applied without the user's approval.** A proposal stays `proposed` until the user decides.
- **Every entry cites evidence:** a ticket, commit or file, and what happened. No evidence, no entry.
- **Statuses:** `proposed`, `applied` (name the commit), `parked` (wait for it to recur), `rejected` (recorded so it is not proposed again).
- **Recurrence is a lookup:** when the same friction happens again, add a line under Recurrences instead of a new entry. A parked entry that recurs is re-proposed with that evidence.
- **Count recurrences in distinct tickets.** A repeat within the same ticket is noted on that ticket's line and does not raise the count, so one occasion is not counted several times.
- **Shared files are higher impact.** A change to `CLAUDE.md`, an agent prompt or a skill affects every story and every session; say so in the entry.
- **Who adds entries:** anyone who hits friction, in a session, and `/tickets done` asks whether there is any (see the skill). Entries are numbered `L-<n>`, never reused.

## Entry format

```
### L-<n>: <short title>

- Status: proposed | applied | parked | rejected
- Impact: local | shared (touches CLAUDE.md, an agent prompt or a skill)
- Target: <file the change would go in>
- Change: <what would be added or changed, in a sentence or two>
- Evidence: <ticket or commit, and what happened>
- Recurrences: <count of distinct tickets; name each ticket and what happened, repeats within one ticket on its line>
- Recommendation: <apply | park | reject, and why>
- Decision: <date and choice once decided; for applied, the commit>
```

## Entries

### L-001: State counts only from a mechanical count

- Status: applied
- Impact: shared (the `tickets` skill)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a ticket, note or summary states a count (scenarios, criteria, files), take it from a mechanical count such as `grep -c`, never from memory or from an earlier note.
- Evidence: refining DFS-010, the scenario count was stated as 24 and the mechanical check gave 32. Migrating DFS-001 to acceptance criteria (commit `b64bda7`), the stated 21 scenarios were 17.
- Recurrences: 2 (DFS-010 refinement; DFS-001 migration)
- Recommendation: apply. It has already happened twice, and the cost of the rule is one sentence.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-001 and L-005` (the sentence is in the `refine` mode of the skill).

### L-002: A criterion that names a set lists its members

- Status: parked
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when an acceptance criterion or scenario refers to a set ("ready-or-later", "every existing ticket"), the ticket lists the members, so implementation does not have to interpret it.
- Evidence: DFS-008 decision 1 said `validate` flags "ready-or-later" tickets without defining "later". Implementation had to decide that done, cancelled and blocked tickets are not checked, and recorded it as a deviation (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs. One occurrence is not yet a pattern.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-003: Run the check on a sample when a scenario says a generated file passes it

- Status: parked
- Impact: shared (the `tickets` skill, and later the archetype edge-case checklists)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a scenario claims that a generated artifact (a template output, a new ticket) passes a check, refinement builds a small sample and runs the check on it before the ticket becomes `ready`.
- Evidence: DFS-008's scenario said a new draft has "an empty criteria section" and that `validate` reports nothing, but its own tag rules made that impossible (the placeholder scenarios' tags would dangle). Found only at implementation; the scenario was reworded and the template ships a placeholder criterion (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-004: Do not hide errors from staging commands

- Status: applied
- Impact: shared (guidance for every agent that commits, so an agent prompt or `CLAUDE.md`)
- Target: `CLAUDE.md`, "Git commits" (or the implementer prompt once DFS-011 exists)
- Change: run `git add` and similar commands without redirecting stderr, and check `git status` before committing.
- Evidence: preparing commit `ac2fcd1`, `git add` included a path that no longer existed after `git mv`, with stderr sent to `/dev/null`. Staging of the other files silently did not happen, and only the `git status` check showed it.
- Recurrences: 1
- Recommendation: park. It was caught, and the fix is a habit more than a rule. Revisit if it recurs.
- Decision: applied 2026-09-21 by the user, overriding park; commit `docs: apply L-004` (a bullet in `CLAUDE.md`, "Git commits").

### L-005: With parallel sessions, stage explicit paths

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: when the working tree holds changes from another session or worktree, stage the files of the task by name (never `git add -A` or `git commit -a`) and confirm with `git status` that nothing else is staged.
- Evidence: commit `ac2fcd1` was prepared while another session had changed DFS-010's ticket (status `in-progress`) and created `.claude/worktrees/`. Naming the paths kept both out of the commit. This is a practice that worked, recorded so it is not lost, and it becomes more likely to matter once stories run in parallel.
- Recurrences: 1
- Recommendation: apply. It is a rule about a mistake that is easy to make and costly to undo, and the pipeline will make parallel sessions normal.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-001 and L-005` (a bullet in `CLAUDE.md`, "Git commits").

### L-006: A `cd` in a shell command moves the session's working directory

- Status: parked
- Impact: local (working habit)
- Target: `CLAUDE.md`, "Commands" (or agent prompts)
- Change: use `git -C <dir>` or a subshell `(cd <dir> && ...)` instead of a bare `cd`, and prefer absolute paths, because the primary working directory of the session changes and later relative commands run somewhere else.
- Evidence: during the DFS-008 work, the session reported its primary working directory changing after a command that started with `cd` into `docs/tickets/backlog` (three times).
- Recurrences: 2 (DFS-008: three times in one session, counted once; DFS-014 refinement: a `cd` into `docs/tickets/backlog` moved the working directory again, several times)
- Recommendation: park. It caused no failure, only environment notices, and it is behaviour of the tool rather than the project. Revisit if it causes a wrong-directory error.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-007: Commits name their ticket in the subject

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: a commit made for a ticket ends its subject line with the ticket ID in brackets, e.g. `[DFS-001]`.
- Evidence: refining DFS-014 (the learn skill), finding a ticket's commits by searching for its ID: commit `ac2fcd1` names DFS-008 only in its body, and nothing else links a ticket to its commits.
- Recurrences: 1
- Recommendation: apply. The user asked for it, and the `learn` skill's `reflect` mode relies on it.
- Decision: applied 2026-09-21 on the user's instruction; commit `docs: require ticket ID in commit subjects`.

### L-008: The tickets lifecycle is not worktree-aware

- Status: proposed
- Impact: shared (the `tickets` skill: `start`, `review` and `done`; possibly a note in `CLAUDE.md`)
- Target: `.claude/skills/tickets/SKILL.md`, `start`, `review` and `done` modes
- Change: `start` creates or enters the ticket's worktree and marks `in-progress` there, so no status edit is left in the main checkout; `done` runs against `main`, verifies the ticket's work is on `main`, moves the ticket folder there, and removes the worktree and branch after confirmation.
- Evidence: DFS-010 (2026-09-21). `start` edited the main checkout before the worktree existed, and the merge would have been refused until that edit was discarded. `main` advanced during the work (`b64bda7` to `36ab0f7`), so the branch was replayed onto it (commits `bbe0841`, `2fd6948`). After the merge the worktree and two branches were still there.
- Recurrences: 1
- Recommendation: apply, as a ticket (DFS-015) rather than a one-line edit, because it changes three modes and adds a destructive step (removing worktrees and branches) that needs its own safeguards and decisions. DFS-012 (`/story`) needs the same semantics, so the two should share one design.
- Decision:

### L-009: Scenarios about instructions are only read, not run

- Status: applied
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a ticket edits instructions (a skill or an agent prompt), refinement records for each scenario whether it can be run mechanically or only checked by reading, so a read-through is not presented as a walkthrough of every mode.
- Evidence: DFS-008 ticket, "Walkthrough evidence" (commit `ac2fcd1`): "Scenarios about instructions (refine, the implementer rule, `list`, `CLAUDE.md`) were checked by confirming the skill and `CLAUDE.md` state the rule in words, which is a weaker check than running them", against its done-item "A walkthrough of each mode". Found by `/learn reflect DFS-008` on 2026-09-21.
- Recurrences: 3 (DFS-008: see Evidence; DFS-014: agent-side scenarios covered only by the written rules and a stand-in run, "These show the words are present, which is weaker than running them" (2026-09-21); DFS-016: applied the rule, separating six scenarios (S1-S3, S9-S11, S17, about SKILL.md/CLAUDE.md text) as "Checked by reading only, not run (per L-009)" from the mechanically-run ones in its Walkthrough evidence section (2026-09-22))
- Recommendation: apply. Two tickets that edit instructions now state the same limit; DFS-014 already separates what ran from what was only read, which the rule would make routine. The edit is one sentence in `refine`.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-009 and L-012` (a sentence in the `refine` mode of the `tickets` skill).

### L-010: Recurrences names its ticket even at count 1

- Status: parked
- Impact: local (the log's own rules and format; the skill already copes with a bare count)
- Target: docs/learnings/log.md, Rules and Entry format
- Change: an entry's Recurrences field names its originating ticket from the start (`1 (DFS-008)`), so a second ticket has something to append to and the check can compare names with the count.
- Evidence: DFS-014 ticket, "Deviations found while implementing": "Entries such as L-002 have `Recurrences: 1` with no ticket named, so adding a second ticket had nothing to append to." Six entries still have a bare 1 (mechanical count).
- Recurrences: 1 (DFS-014)
- Recommendation: park. The skill's `add` already names the original ticket from Evidence when a second one arrives, and the check accepts a bare 1, so nothing breaks. Apply it if entries keep being written without a ticket name.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-011: A new agent can't be launched in the turn that creates its file

- Status: rejected
- Impact: shared (the `tickets` skill, `refine` guidance for tickets that create agents)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: for a ticket that creates an agent, refinement plans the real-run done-item for after the agent is registered, not in the turn that creates the file, and does not tick it on a stand-in run.
- Evidence: DFS-014 ticket, "Deviations found while implementing": "`Agent` with `subagent_type: reflect` failed with 'Agent type 'reflect' not found'". Commit `033d31c` left the real-run item unticked ("Not yet met"); commit `9cb7c7c` ticked it after the agent had registered later in the same session ("The real run").
- Recurrences: 1 (DFS-014)
- Recommendation: park. One occurrence, handled openly. DFS-011 creates five agents and is where it would recur.
- Decision: rejected 2026-09-21 by the user, overriding park; treated as a one-off of this session's agent-registration timing, not a general rule.

### L-012: A finding declined in `reflect` leaves no record, so it is proposed again

- Status: applied
- Impact: shared (the `learn` skill, `reflect` mode)
- Target: `.claude/skills/learn/SKILL.md`, `reflect` mode, step 4
- Change: for each finding the user answers write, reject or skip. Reject writes the entry with status `rejected` and the decision date, so a later run reports it as "matches rejected L-<n>" and does not propose it again. Skip leaves no trace, as today.
- Evidence: DFS-014 ticket, last paragraph, added in `9cb7c7c`: "`reflect` leaves no trace of a finding the user declines, and nothing records which tickets it has run on".
- Recurrences: 1 (DFS-014)
- Recommendation: apply. The gap follows from the design, and the change is a few lines in one step of the skill.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-009 and L-012` (step 4 of `reflect` mode in the `learn` skill).

### L-013: A filter-stage skeleton's null-check runs before its not-implemented throw

- Status: applied
- Impact: shared (an archetype skeleton every pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/FilterStage.java.tmpl`
- Change: reorder the skeleton's guard so `throw new UnsupportedOperationException("not implemented: <TICKET-ID>")` comes before `Objects.requireNonNull(groups, "groups")`, so every stub method is uniformly red in the test phase regardless of which acceptance criterion a test targets; the implementer moves the null-check back above the real logic during implementation.
- Evidence: `docs/tickets/completed/CCW-004-story-agents/CCW-004-ticket.md`, "Demonstration evidence" — running the pipeline live against fixture ticket FIX-001, the architect's stub followed the skeleton verbatim (`requireNonNull` before the throw), which made FIX-001.AC-3's null-argument test pass outright against the stub instead of failing red. The architect, test-author and verifier each independently noticed and flagged it in the story's `learnings.md`/`verification.md` without being told to look for it.
- Recurrences: 1 (CCW-004)
- Recommendation: apply. Three independent agents converged on the same root cause and the same fix on a single run; it will recur on the first real filter-stage ticket with an argument-validation acceptance criterion. (A related half of this friction — whether a red-gate should tolerate a validation AC passing off the stub — was already addressed separately, in the same ticket, by adding explicit guidance to `.claude/agents/test-author.md`; this entry is only the remaining skeleton-ordering fix, which belongs to the archetype, not the agent.)
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-013` (the stub's `requireNonNull` moved to a guiding comment after the throw in `FilterStage.java.tmpl`; `pipeline-stage`'s `archetype.md` `version` bumped 1 → 2).
