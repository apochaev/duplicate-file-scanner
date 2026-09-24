# CCW-001 learnings

Migrated from `docs/learnings/log.md` (2026-09-23, CCW-010) — see that file for the full migration note. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

## Entries

### CCW-001.L-1: A criterion that names a set lists its members

- Status: parked
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when an acceptance criterion or scenario refers to a set ("ready-or-later", "every existing ticket"), the ticket lists the members, so implementation does not have to interpret it.
- Evidence: DFS-008 decision 1 said `validate` flags "ready-or-later" tickets without defining "later". Implementation had to decide that done, cancelled and blocked tickets are not checked, and recorded it as a deviation (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs. One occurrence is not yet a pattern.
- Decision: parked 2026-09-21 by the user, as recommended.

### CCW-001.L-2: Run the check on a sample when a scenario says a generated file passes it

- Status: parked
- Impact: shared (the `tickets` skill, and later the archetype edge-case checklists)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a scenario claims that a generated artifact (a template output, a new ticket) passes a check, refinement builds a small sample and runs the check on it before the ticket becomes `ready`.
- Evidence: DFS-008's scenario said a new draft has "an empty criteria section" and that `validate` reports nothing, but its own tag rules made that impossible (the placeholder scenarios' tags would dangle). Found only at implementation; the scenario was reworded and the template ships a placeholder criterion (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs.
- Decision: parked 2026-09-21 by the user, as recommended.

### CCW-001.L-3: Do not hide errors from staging commands

- Status: applied
- Impact: shared (guidance for every agent that commits, so an agent prompt or `CLAUDE.md`)
- Target: `CLAUDE.md`, "Git commits" (or the implementer prompt once DFS-011 exists)
- Change: run `git add` and similar commands without redirecting stderr, and check `git status` before committing.
- Evidence: preparing commit `ac2fcd1`, `git add` included a path that no longer existed after `git mv`, with stderr sent to `/dev/null`. Staging of the other files silently did not happen, and only the `git status` check showed it.
- Recurrences: 1
- Recommendation: park. It was caught, and the fix is a habit more than a rule. Revisit if it recurs.
- Decision: applied 2026-09-21 by the user, overriding park; commit `docs: apply L-004` (a bullet in `CLAUDE.md`, "Git commits").

### CCW-001.L-4: With parallel sessions, stage explicit paths

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: when the working tree holds changes from another session or worktree, stage the files of the task by name (never `git add -A` or `git commit -a`) and confirm with `git status` that nothing else is staged.
- Evidence: commit `ac2fcd1` was prepared while another session had changed DFS-010's ticket (status `in-progress`) and created `.claude/worktrees/`. Naming the paths kept both out of the commit. This is a practice that worked, recorded so it is not lost, and it becomes more likely to matter once stories run in parallel.
- Recurrences: 1
- Recommendation: apply. It is a rule about a mistake that is easy to make and costly to undo, and the pipeline will make parallel sessions normal.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-001 and L-005` (a bullet in `CLAUDE.md`, "Git commits").

### CCW-001.L-5: A `cd` in a shell command moves the session's working directory

- Status: parked
- Impact: local (working habit)
- Target: `CLAUDE.md`, "Commands" (or agent prompts)
- Change: use `git -C <dir>` or a subshell `(cd <dir> && ...)` instead of a bare `cd`, and prefer absolute paths, because the primary working directory of the session changes and later relative commands run somewhere else.
- Evidence: during the DFS-008 work, the session reported its primary working directory changing after a command that started with `cd` into `docs/tickets/backlog` (three times).
- Recurrences: 2 (DFS-008: three times in one session, counted once; DFS-014 refinement: a `cd` into `docs/tickets/backlog` moved the working directory again, several times)
- Recommendation: park. It caused no failure, only environment notices, and it is behaviour of the tool rather than the project. Revisit if it causes a wrong-directory error.
- Decision: parked 2026-09-21 by the user, as recommended.

### CCW-001.L-6: Scenarios about instructions are only read, not run

- Status: applied
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a ticket edits instructions (a skill or an agent prompt), refinement records for each scenario whether it can be run mechanically or only checked by reading, so a read-through is not presented as a walkthrough of every mode.
- Evidence: DFS-008 ticket, "Walkthrough evidence" (commit `ac2fcd1`): "Scenarios about instructions (refine, the implementer rule, `list`, `CLAUDE.md`) were checked by confirming the skill and `CLAUDE.md` state the rule in words, which is a weaker check than running them", against its done-item "A walkthrough of each mode". Found by `/learn reflect DFS-008` on 2026-09-21.
- Recurrences: 3 (DFS-008: see Evidence; DFS-014: agent-side scenarios covered only by the written rules and a stand-in run, "These show the words are present, which is weaker than running them" (2026-09-21); DFS-016: applied the rule, separating six scenarios (S1-S3, S9-S11, S17, about SKILL.md/CLAUDE.md text) as "Checked by reading only, not run (per L-009)" from the mechanically-run ones in its Walkthrough evidence section (2026-09-22))
- Recommendation: apply. Two tickets that edit instructions now state the same limit; DFS-014 already separates what ran from what was only read, which the rule would make routine. The edit is one sentence in `refine`.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-009 and L-012` (a sentence in the `refine` mode of the `tickets` skill).
