# CCW-007 learnings

Migrated from `docs/learnings/log.md` (2026-09-23, CCW-010) — see that file for the full migration note. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

## Entries

### CCW-007.L-1: Commits name their ticket in the subject

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: a commit made for a ticket ends its subject line with the ticket ID in brackets, e.g. `[DFS-001]`.
- Evidence: refining DFS-014 (the learn skill), finding a ticket's commits by searching for its ID: commit `ac2fcd1` names DFS-008 only in its body, and nothing else links a ticket to its commits.
- Recurrences: 1
- Recommendation: apply. The user asked for it, and the `learn` skill's `reflect` mode relies on it.
- Decision: applied 2026-09-21 on the user's instruction; commit `docs: require ticket ID in commit subjects`.

### CCW-007.L-2: Recurrences names its ticket even at count 1

- Status: parked
- Impact: local (the log's own rules and format; the skill already copes with a bare count)
- Target: docs/learnings/log.md, Rules and Entry format
- Change: an entry's Recurrences field names its originating ticket from the start (`1 (DFS-008)`), so a second ticket has something to append to and the check can compare names with the count.
- Evidence: DFS-014 ticket, "Deviations found while implementing": "Entries such as L-002 have `Recurrences: 1` with no ticket named, so adding a second ticket had nothing to append to." Six entries still have a bare 1 (mechanical count).
- Recurrences: 1 (DFS-014)
- Recommendation: park. The skill's `add` already names the original ticket from Evidence when a second one arrives, and the check accepts a bare 1, so nothing breaks. Apply it if entries keep being written without a ticket name.
- Decision: parked 2026-09-21 by the user, as recommended.

### CCW-007.L-3: A new agent can't be launched in the turn that creates its file

- Status: rejected
- Impact: shared (the `tickets` skill, `refine` guidance for tickets that create agents)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: for a ticket that creates an agent, refinement plans the real-run done-item for after the agent is registered, not in the turn that creates the file, and does not tick it on a stand-in run.
- Evidence: DFS-014 ticket, "Deviations found while implementing": "`Agent` with `subagent_type: reflect` failed with 'Agent type 'reflect' not found'". Commit `033d31c` left the real-run item unticked ("Not yet met"); commit `9cb7c7c` ticked it after the agent had registered later in the same session ("The real run").
- Recurrences: 1 (DFS-014)
- Recommendation: park. One occurrence, handled openly. DFS-011 creates five agents and is where it would recur.
- Decision: rejected 2026-09-21 by the user, overriding park; treated as a one-off of this session's agent-registration timing, not a general rule.

### CCW-007.L-4: A finding declined in `reflect` leaves no record, so it is proposed again

- Status: applied
- Impact: shared (the `learn` skill, `reflect` mode)
- Target: `.claude/skills/learn/SKILL.md`, `reflect` mode, step 4
- Change: for each finding the user answers write, reject or skip. Reject writes the entry with status `rejected` and the decision date, so a later run reports it as "matches rejected L-<n>" and does not propose it again. Skip leaves no trace, as today.
- Evidence: DFS-014 ticket, last paragraph, added in `9cb7c7c`: "`reflect` leaves no trace of a finding the user declines, and nothing records which tickets it has run on".
- Recurrences: 1 (DFS-014)
- Recommendation: apply. The gap follows from the design, and the change is a few lines in one step of the skill.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-009 and L-012` (step 4 of `reflect` mode in the `learn` skill).
