# CCW-018 verification

## Product owner review

### What you can do now

Run two sessions side by side, named `dfs-frontend` and `dfs-backend`. Each one picks up, launches and lists as dispatchable only the tickets in its own lanes. When a frontend ticket needs core work, the frontend session files a draft ticket in the backend lane and rings the backend session once. When the backend closes that ticket, its `done` doesn't start the frontend ticket itself: it messages the frontend session, which runs the approval check and starts it. Harness changes announce themselves when they merge. Shared-file edits send no messages, so you get fewer approval clicks.

### Acceptance criteria

- **AC-1** met (mechanical): validate flags a missing lane, the lane `ui`, and a `CCW` ticket in `backend`, and leaves a `done` ticket without a lane alone.
- **AC-2** met (mechanical): a lane owned twice and an unknown lane `ops` are both flagged. The real `lanes.md` passes.
- **AC-3** met (read and live): after `/rename`, `ListAgents` reports "This session is dfs-frontend" and the peer as `dfs-backend` ("was DFS-main until 1m ago"); `lanes.md` gives this session frontend and harness without asking.
- **AC-4** met (mechanical): closing DFS-950 in `dfs-backend` launches DFS-951 (backend) and sends one message to `dfs-frontend` naming DFS-952 and DFS-953.
- **AC-5** met (mechanical): `dfs-frontend` ignores DFS-951 (backend), launches DFS-952 (approved, unchanged), and refuses DFS-953 (edited after approval).
- **AC-6** met (mechanical for `done`, read for `list`): with no frontend owner running, DFS-952 and DFS-953 are named as waiting for lane frontend.
- **AC-7** met (read). Not yet exercised live, see AC-12.
- **AC-8** met (read), live once this branch merges (see Evidence).
- **AC-9** met (read): there are exactly three message moments, a shared-file edit sends nothing, and the message formats name IDs and commits only.
- **AC-10** met (read): `list` shows a lane column, this session's lanes first, then "Other lanes", and both dispatch lists are limited to its own lanes.
- **AC-11** met (mechanical): every open ticket has a lane, and the real backlog shows no findings.
- **AC-12** partly met. The backend session drafted the three requests itself (DFS-032 ListDirectory, DFS-033 drift check, DFS-034 content verify), not through AC-7. They're uncommitted in its working tree, waiting for its user. The exchange about them did happen, and it was acknowledged in both directions. The AC-8 message goes out on merge.

### Try it

1. Restart or rename the two sessions: `/rename dfs-frontend` here and `/rename dfs-backend` there, or start them with `claude -n <name>`.
2. Then run `/tickets list` here. It should show frontend and harness first and the backend tickets under "Other lanes", without asking which lanes this session owns.

### Decided without you

- **Story skill added to `touches`.** `.claude/skills/story/SKILL.md` listed only CCW-017's two launch points, so read strictly it would have forbidden the third one (an unblock message). I added one clause to it and added the file to the ticket's `touches`. Refinement should have caught this (CCW-005.L-5).
- **AC-12 accepted in a different form.** The backend session filed the three requests on its own before CCW-018 existed, and asked me to leave them alone. I didn't duplicate them through AC-7. The first real `new --for` request will exercise AC-7 live.
- **Request form syntax:** `/tickets new <title> --for <lane> --by <requesting ID>`.
- **Session names** are set with `claude -n <name>` (verified in `claude --help`) or `/rename <name>` in a running session (verified live 2026-10-31 for both sessions).
- **Lane-map commit subject:** `docs: map <name> to <lanes>`, for the AC-3 fallback.

### Known limits

- DFS-031 to DFS-034 will need a `lane: backend` line when the backend commits them. The template now has the field, but those drafts predate it. I'll mention it in the AC-8 message.
- `/tickets validate` was run for the lane rules only. I didn't re-run the other rules, which this ticket doesn't change.
- The read checks confirm the instructions say the right thing, not that a real cross-lane launch works end to end. The first one (expected when DFS-032 closes) gets a learnings entry.
- Messages to a session in another permission mode wait for that user's approval, so they can be slow.

### Evidence

- Mechanical runs below (scratch repo `ccw018-repo` in the session scratchpad; no real ticket edited by the runs).
- `./gradlew build` in the worktree: see Summary.
- `git status` on the branch shows only paths listed under Touches.
- Exchange with `DFS-main`: asked whether DFS-031..034 were theirs. They replied: "Yes, the untracked drafts DFS-031 to DFS-034 are mine … Please leave them alone … I'll commit each draft alone, and I'll message you then." I acknowledged and asked for one request-format message per draft on commit.

### How it went

Implemented by hand in one session, no retries. One out-of-scope dependency was found during implementation (the story skill's launch-point list) and fixed with a `touches` extension. One live-exchange plan didn't hold, because the other session got there first.

## Summary

12 criteria: 11 met (6 mechanically, 5 by reading, AC-3 also live; AC-8 sent on merge `1f95b25`), and AC-12 met in a different form. Build: green. Scope: Touches plus `.claude/skills/story/SKILL.md`, as decided above.

## Mechanical runs

Checker `ccw018_check.py` (scratchpad) implements the lane, lane-map, `done` 4a and receive rules as written in `tickets/SKILL.md`, and the receive step uses CCW-017's launch check against git. Scratch tickets: DFS-940 (draft, no lane), DFS-941 (`lane: ui`), CCW-942 (`lane: backend`), DFS-943 (done, no lane), DFS-944 (frontend); DFS-950 (backend, done), DFS-951 (backend, ready, autorun), DFS-952 and DFS-953 (frontend, ready, autorun), all depending on DFS-950, each with a `docs: refine` commit, and DFS-953 edited after approval.

```
== AC-1/AC-11 validate lanes (scratch)
FINDING CCW-942: CCW ticket lane 'backend' is not harness
FINDING DFS-940: lane '' missing or not backend|frontend|harness
FINDING DFS-941: lane 'ui' missing or not backend|frontend|harness
== AC-2 validate lanes-bad.md
FINDING lanes.md: lane 'frontend' owned by dfs-frontend and dfs-other
FINDING lanes.md line 'dfs-other: frontend, ops': unknown lane 'ops'
== AC-2 real lanes.md: {'dfs-backend': ['backend'], 'dfs-frontend': ['frontend', 'harness']} findings: none
== AC-11 real backlog: no findings
== AC-4 done DFS-950 in dfs-backend, dfs-frontend running
launch: DFS-951
message to dfs-frontend: DFS-950 done in <close sha>: unblocks DFS-952 (frontend, autorun), DFS-953 (frontend, autorun); run the launch check and start the lowest
== AC-6 same, dfs-frontend not running
launch: DFS-951
DFS-952 waits for lane frontend (no running owner)
DFS-953 waits for lane frontend (no running owner)
== AC-5 dfs-frontend receives message naming DFS-951, DFS-952, DFS-953
ignore DFS-951: lane backend not mine
DFS-952: PASS (approved in 8d8846b)
DFS-953: REFUSE: changed since approval a8d4937
launch: DFS-952
```

## Read checks

| Scenario | Where (`.claude/skills/tickets/SKILL.md` unless named) |
|---|---|
| AC-3 own lanes from name; unknown-name question | "Sessions and lanes", "This session's lanes" |
| AC-7/AC-9 filing a request | `new` request form; "Messages" item 1 |
| AC-8/AC-9 harness change announces itself | "Messages" item 3; `done` step 2 |
| AC-9 shared-file edits send nothing | "Messages", last paragraph |
| AC-10 list by lane | `list` |
| AC-4/AC-5 third launch point | "Autonomous launch"; "Receiving an unblock"; `done` 4a; `story/SKILL.md` usage paragraph |
| AC-1 lane field, ready gate, template | Frontmatter table; ready gate; `TEMPLATE.md` |
