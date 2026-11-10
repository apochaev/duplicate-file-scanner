# DFS-062 learnings

## Entries

### DFS-062.L-1: Draft ID collision, two different tickets drafted as DFS-062

- Status: parked
- Impact: shared (touches a skill: `.claude/skills/tickets/SKILL.md`)
- Target: `.claude/skills/tickets/SKILL.md` (the `new` step that picks the next ID)
- Change: make `/tickets new` take the next ID from the highest ID across `backlog/` and `completed/` on every branch and worktree, or check for a duplicate ID before the draft commit.
- Evidence: commit `e7f2899` ("docs: draft DFS-062, DFS-063") created `backlog/DFS-062-scan-progress-phase-totals`; commit `bf67179` ("docs: draft DFS-062") drafted this ticket under the same ID; commit `626d9fe` ("docs: renumber draft DFS-062 to DFS-064, ID was already used") fixed it afterwards.
- Recurrences: 1
- Recommendation: park. One occurrence, and the cause (parallel sessions drafting at once) is not confirmed from the files; a fix needs a decision on what "every branch" means for the skill.
- Decision: parked 2026-11-09 by the user

### DFS-062.L-2: Done-item "No files changed outside Touches" is ticked although the ticket's own files change

- Status: applied
- Impact: shared (touches a skill: `.claude/skills/tickets/TEMPLATE.md`)
- Target: `.claude/skills/tickets/TEMPLATE.md` (Definition of done wording), or each `archetype: none` ticket's own done-item
- Change: reword the item to "no source files changed outside Touches (the ticket folder's own files excepted)", or list the ticket folder under Touches for `archetype: none` tickets.
- Evidence: commit `dad315b` changed `ScanCommandTest.java`, `StubFailures.java` and `StubFailuresTest.java`, plus the ticket and `verification.md` under `docs/tickets/`; Touches lists only `cli/src/test/java/us/pochaev/dfs/cli`, yet the item was ticked under its literal wording.
- Recurrences: 1
- Recommendation: park. Low impact: it caused no harm here, and the fix is a one-line wording change.
- Decision: applied 2026-11-09 by the user; commit `docs: apply DFS-062.L-2`
