# consult-001: question

- Request ID: consult-001
- Date: 2026-11-01
- Requested by: dfs-harness (harness lane owner), at the user's request; dfs-product remains owner of advisory intake and formal responses
- Parent Codex session (forked, never resumed): 01a19abb-50b6-77d2-97ca-d3bb6b13bf59
- Inspected revision: main at 8fcc732; advisory/ files uncommitted in the main checkout as of this date; CCW-021 ticket as committed in 697a6a3 (in-progress, uncommitted, in worktree ../duplicate-file-scanner-CCW-021-coordinator-session; nothing implemented)
- Environment: codex-cli 0.166.0, Claude Code 2.1.316, Windows 10, Claude's Bash tool is Git Bash
- Invocation: `codex exec --sandbox read-only -C <repo root> fork 01a19abb-50b6-77d2-97ca-d3bb6b13bf59 -o advisory/consultations/consult-001-answer.md "<prompt below>"`, run under `timeout 900`
- Answer: advisory/consultations/consult-001-answer.md; outcome in consult-001-outcome.md

## Prompt sent

You are a fork of the project's advisor conversation. This is one bounded, read-only exchange. Read advisory/CONSULT-CONTEXT.md first and follow it.

Evidence to inspect (read-only): advisory/README.md, advisory/proposals/ADV-001-r1.md, advisory/proposals/ADV-002-r1.md, docs/tickets/backlog/CCW-021-coordinator-session/CCW-021-ticket.md, docs/tickets/lanes.md, the "Sessions and lanes" section of .claude/skills/tickets/SKILL.md, .claude/settings.json, and the installed plugin skill under ~/.claude/plugins/cache/*/agent-collaboration/*/skills/codex-consult/.

New facts since your proposals: (a) the user named dfs-harness the harness lane owner (lanes.md 8fcc732); dfs-product is the coordinator and owns advisory intake. (b) CCW-021 is ready (697a6a3) and started but unimplemented. (c) Token usage measured from this project's transcripts: long-lived main sessions dominate (dfs-backend about 755M cache-read tokens over 2,915 calls, about 260k context per call; pipeline subagents under 10% of total). Each cross-session message wakes a session for a full turn at its current context size. dfs-harness therefore proposed changing CCW-021 from push messages ("message 4" per turn with ticket changes) to a pull model (the coordinator reads git log --all and git worktree list when it lists); the user has not decided. (d) This consultation itself proves codex exec fork works, or not.

Question: What is the smallest integration that lets dfs-product (1) discover pending advisory proposals at its next turn or session start with zero added context when nothing is pending, and (2) consult Codex with inherited advisor context via a fork of the saved session, while preserving ticket ownership and approval gates and not interfering with other sessions? Specifically:
1. Should discovery be a Claude Code hook (SessionStart and/or UserPromptSubmit running a small deterministic script, scoped to the coordinator session only), an instruction in the tickets skill's coordinator catch-up, or both? How should the hook know it is running in the coordinator session?
2. Should this be folded into CCW-021 (whose coordinator catch-up already reads git state at start) or be a separate CCW ticket depending on it? Does the pull-model change to CCW-021 affect your recommendation?
3. For consultation: use the plugin's codex-consult skill as is, adapt it, or a project-local procedure (the CLAUDE-HANDOFF.md invocation)? Which of ADV-002's six compatibility findings remain blockers given this run's result?
4. Name the smallest validation that proves both parts work.

Do not edit files, run builds, launch agents, or implement changes. Return your answer in the CONSULT-CONTEXT.md return format.
