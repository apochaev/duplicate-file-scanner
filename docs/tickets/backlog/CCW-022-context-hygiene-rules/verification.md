## Product owner review

### What you can do now
Every stop (gate, blocked, finished, `done`, `refine`'s ready commit) ends with `safe to /clear; resume with <command>`, and `plan.json` is committed first, so you can `/clear` and resume with one command. A lane session's `/tickets list` shows its own started tickets with their next command.

### Acceptance criteria
- AC-1 met by reading: story skill "Every stop", gate/blocked/finish text, uncommitted-files form. The live gate run is not done.
- AC-2 met by reading: tickets skill `done` and `refine` endings.
- AC-3 not exercised: needs a real `/clear` at a gate, then `/story approve <ID>`.
- AC-4 not exercised: needs a real `/clear` in dfs-product.
- AC-5 met: gate feedback is `/story approve <ID> <feedback>`; the search below finds no "read from the conversation".
- AC-6 met by reading, live run pending: "Every stop" step 1 commits `plan.json` as `docs: record <ID> <stop> in plan.json [<ID>]`.
- AC-7 met by reading: one `/compact` template in the tickets skill's "Context hygiene"; the story skill points to it.
- AC-8 met: `catch-up --session <name> --lanes` and its self-test cases pass (own-lane ticket shown, other lane's left out, coordinator and unlisted names get nothing); `list` runs it in a lane session.
- AC-9 met by reading: the three rules are in "Context hygiene".

### Try it
`tools/story-gate/story-gate catch-up --session dfs-harness --lanes` prints CCW-022 with `next: /tickets start CCW-022 --worktree` before a plan exists. `tools/story-gate/story-gate selftest` passes.

### Decided without you
- The lane view is a separate flag, `--lanes`, so `--session <name>` alone and `--hook` stay silent in lane sessions (CCW-021 AC-12 unchanged).
- The finish stop's commit subject uses `<stop>` = `done`: `docs: record <ID> done in plan.json [<ID>]`.
- `done` skips the clear line when step 4a launched a story.
- Lane filter reads `lane:` and `archetype:` from the ticket frontmatter by regex in `CatchUp`, not by changing `Ticket`.

### Known limits
Live `[run]` scenarios (gate stop commit, real `/clear` resumes in a lane session and in dfs-product) are not run; they need real sessions.

### Evidence
`story-gate selftest`: PASS, 126 cases, includes the new catch-up cases (`CatchUpSelfTest`, unit and `laneCase`). Old-text search of `.claude/`, `CLAUDE.md`, `docs/design/` for "read from the conversation", "from the conversation, not a flag" and "again after approval": no hits after the edits (the three hits before were story SKILL "Human gate" and D12).

### How it went
Hand-implemented (archetype `none`); no plan.json.

Not ready for approval until the live scenarios are run.
