# Lanes

Sessions no longer own lanes (CCW-026). Each ticket runs in a session of its own, named after the ticket and started from a printed `claude -n <ID> --permission-mode auto "<command>"` line; see "Sessions per ticket" in `.claude/skills/tickets/SKILL.md`. A ticket's `lane` (`backend`: `file-scanner`, `cli`; `frontend`: `server` and its UI; `harness`: every `CCW` ticket) is a label that groups `/tickets list`, not an owner.

The only entry left is the role `coordinator` (CCW-021), held by at most one session. The coordinator writes and refines tickets in every lane but never starts, launches or closes one. At every session start (startup, resume, `/clear`, compact) a SessionStart hook (`tools/story-gate/story-gate catch-up --hook`) prints its picture of the backlog from git and the working trees, and its `/tickets list` prints the same picture under the table. The hook prints nothing in any other session.

Name a session at launch with `claude -n <name>`, or rename a running one with `/rename <name>`. The hook finds the coordinator by that name (the hook input's `session_title`); when that is missing it falls back to the environment variable `DFS_SESSION`, so launching the coordinator as `$env:DFS_SESSION='dfs-product'; claude -n dfs-product` (PowerShell) or `DFS_SESSION=dfs-product claude -n dfs-product` (Git Bash) works either way. Moving the role is a one-line edit here.

Launch model (CCW-031): launch the `backend` and `frontend` sessions on Sonnet, `claude --model sonnet -n dfs-backend` and `claude --model sonnet -n dfs-frontend`: their `/story run` loop is mechanical (read `plan.json`, launch a phase's agent, run the gate, record the run), and each phase's agent still runs on the model its archetype sets. Keep the `harness` session (`dfs-harness`) and the coordinator (`dfs-product`) on Opus, the default model: they edit skills, agents and tickets. This is a recommendation, not enforced; that a Sonnet session follows the `story` skill's gates and notifications is checked on its first story.

```
dfs-product: coordinator
```
