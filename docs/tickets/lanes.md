# Lanes

Which session owns which lanes of the backlog (CCW-018). A session finds its own name with `ListAgents` and looks it up here; see "Sessions and lanes" in `.claude/skills/tickets/SKILL.md`. Each lane is owned by at most one session. Lanes: `backend` (`file-scanner`, `cli`), `frontend` (`server` and its UI), `harness` (every `CCW` ticket).

`coordinator` is not a lane but a role, held by at most one session and never mixed with lanes on its line (CCW-021). The coordinator owns no lane: it writes and refines tickets in every lane but never starts, launches or closes one. No session reports to it; at every session start (startup, resume, `/clear`, compact) a SessionStart hook (`tools/story-gate/story-gate catch-up --hook`) prints its picture of the backlog from git and the working trees, and its `/tickets list` prints the same picture under the table.

Name a session at launch with `claude -n <name>`, or rename a running one with `/rename <name>`. The hook finds the coordinator by that name (the hook input's `session_title`); when that is missing it falls back to the environment variable `DFS_SESSION`, so launching the coordinator as `$env:DFS_SESSION='dfs-product'; claude -n dfs-product` (PowerShell) or `DFS_SESSION=dfs-product claude -n dfs-product` (Git Bash) works either way. Moving the role is a one-line edit here, like moving a lane.

Launch model (CCW-031): launch the `backend` and `frontend` sessions on Sonnet, `claude --model sonnet -n dfs-backend` and `claude --model sonnet -n dfs-frontend`: their `/story run` loop is mechanical (read `plan.json`, launch a phase's agent, run the gate, record the run), and each phase's agent still runs on the model its archetype sets. Keep the `harness` session (`dfs-harness`) and the coordinator (`dfs-product`) on Opus, the default model: they edit skills, agents and tickets. This is a recommendation, not enforced; that a Sonnet session follows the `story` skill's gates and notifications is checked on its first story.

```
dfs-backend: backend
dfs-frontend: frontend
dfs-harness: harness
dfs-product: coordinator
```
