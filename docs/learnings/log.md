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

- Status: proposed
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when an acceptance criterion or scenario refers to a set ("ready-or-later", "every existing ticket"), the ticket lists the members, so implementation does not have to interpret it.
- Evidence: DFS-008 decision 1 said `validate` flags "ready-or-later" tickets without defining "later". Implementation had to decide that done, cancelled and blocked tickets are not checked, and recorded it as a deviation (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs. One occurrence is not yet a pattern.
- Decision:

### L-003: Run the check on a sample when a scenario says a generated file passes it

- Status: proposed
- Impact: shared (the `tickets` skill, and later the archetype edge-case checklists)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a scenario claims that a generated artifact (a template output, a new ticket) passes a check, refinement builds a small sample and runs the check on it before the ticket becomes `ready`.
- Evidence: DFS-008's scenario said a new draft has "an empty criteria section" and that `validate` reports nothing, but its own tag rules made that impossible (the placeholder scenarios' tags would dangle). Found only at implementation; the scenario was reworded and the template ships a placeholder criterion (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs.
- Decision:

### L-004: Do not hide errors from staging commands

- Status: proposed
- Impact: shared (guidance for every agent that commits, so an agent prompt or `CLAUDE.md`)
- Target: `CLAUDE.md`, "Git commits" (or the implementer prompt once DFS-011 exists)
- Change: run `git add` and similar commands without redirecting stderr, and check `git status` before committing.
- Evidence: preparing commit `ac2fcd1`, `git add` included a path that no longer existed after `git mv`, with stderr sent to `/dev/null`. Staging of the other files silently did not happen, and only the `git status` check showed it.
- Recurrences: 1
- Recommendation: park. It was caught, and the fix is a habit more than a rule. Revisit if it recurs.
- Decision:

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

- Status: proposed
- Impact: local (working habit)
- Target: `CLAUDE.md`, "Commands" (or agent prompts)
- Change: use `git -C <dir>` or a subshell `(cd <dir> && ...)` instead of a bare `cd`, and prefer absolute paths, because the primary working directory of the session changes and later relative commands run somewhere else.
- Evidence: during the DFS-008 work, the session reported its primary working directory changing after a command that started with `cd` into `docs/tickets/backlog` (three times).
- Recurrences: 2 (DFS-008: three times in one session, counted once; DFS-014 refinement: a `cd` into `docs/tickets/backlog` moved the working directory again, several times)
- Recommendation: park. It caused no failure, only environment notices, and it is behaviour of the tool rather than the project. Revisit if it causes a wrong-directory error.
- Decision:

### L-007: Commits name their ticket in the subject

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: a commit made for a ticket ends its subject line with the ticket ID in brackets, e.g. `[DFS-001]`.
- Evidence: refining DFS-014 (the learn skill), finding a ticket's commits by searching for its ID: commit `ac2fcd1` names DFS-008 only in its body, and nothing else links a ticket to its commits.
- Recurrences: 1
- Recommendation: apply. The user asked for it, and the `learn` skill's `reflect` mode relies on it.
- Decision: applied 2026-09-21 on the user's instruction; commit `docs: require ticket ID in commit subjects`.
