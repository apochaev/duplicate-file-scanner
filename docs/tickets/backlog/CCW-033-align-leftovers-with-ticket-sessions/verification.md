## Product owner review

### What you can do now
Launch lines printed for pipeline tickets now carry `--model sonnet`, so backend and frontend stories run on the cheaper model; `archetype: none` tickets stay on the default model. The archetypes and `lanes.md` no longer mention lane owners or lane sessions.

### Acceptance criteria
- AC-1 met: component archetype line 19 names a draft in the endpoint's lane added to `depends_on`.
- AC-2 met: endpoint archetype line 19 names a draft in the backend lane added to `depends_on`.
- AC-3 met: every launch line in the `tickets` and `story` skills has `-n <ID>` first; pipeline lines have `--model sonnet`, `none` lines have none.
- AC-4 met: `launchLine(id, archetype, command)` in `CatchUp.java`; selftest asserts DFS-061 (pipeline-stage) gets `--model sonnet`, CCW-062 (none) does not.
- AC-5 met: `lanes.md` names only `dfs-product`, states the archetype rule and reason, keeps the coordinator on the default model.
- AC-6 met: `CLAUDE.md` and `story-orchestration.md` give the rule where they show the line.
- AC-7 met: `tools/story-gate/story-gate selftest` passes (126 cases).

### Try it
`tools/story-gate/story-gate selftest`, then `tools/story-gate/story-gate catch-up --session CCW-099 --lanes` and look at the launch lines.

### Decided without you
- The self-test's CCW-062 fixture archetype changed from `pipeline-stage` to `none` to exercise the no-model case.
- A blank or missing archetype is treated like `none` (no `--model`).
- Leftover hits for `dfs-backend` remain only as the `validate` example in the `tickets` skill and an invalid-lane-line self-test input; both are intentional.

### Known limits
CCW-031's Sonnet check moves to the next pipeline ticket (decision 3).

### Evidence
Selftest PASS (126 cases). Old-form search over `.claude/`, `CLAUDE.md`, `docs/design/`, `docs/tickets/lanes.md`, `tools/story-gate/` for `lane that owns`, `request to the`, `dfs-backend`, `dfs-frontend`, `dfs-harness`: only the two intentional hits above. `grep "claude -n" .claude/skills`: every hit has `-n <ID>` first and `--permission-mode auto`; pipeline lines have `--model sonnet` between them. `git diff --stat` shows only Touches paths plus this ticket.

### How it went
Hand-implemented, no pipeline. One failed edit attempt on the self-test file (fixed with sed).

Ready for the user's approval.
