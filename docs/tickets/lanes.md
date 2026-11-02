# Lanes

Which session owns which lanes of the backlog (CCW-018). A session finds its own name with `ListAgents` and looks it up here; see "Sessions and lanes" in `.claude/skills/tickets/SKILL.md`. Each lane is owned by at most one session. Lanes: `backend` (`file-scanner`, `cli`), `frontend` (`server` and its UI), `harness` (every `CCW` ticket).

Name a session at launch with `claude -n <name>`, or rename a running one with `/rename <name>`.

```
dfs-backend: backend
dfs-frontend: frontend
dfs-harness: harness
```
