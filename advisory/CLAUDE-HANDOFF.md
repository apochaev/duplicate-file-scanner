# Handoff to Claude coordinator: consult the contextual advisor

The user requests that consultations inherit the existing advisor conversation,
not start without its context. Use the installed collaboration plugin's bounded
single-exchange consultation pattern with this project-specific invocation.

## Instructions

1. Read advisory/README.md and the proposal or decision being evaluated.
2. For a consultation, fork this explicit saved Codex session:
   01a19abb-50b6-77d2-97ca-d3bb6b13bf59
   Never use --last. Never resume or write into the original session: it remains
   the user's interactive advisor conversation.
3. Use the installed codex executable, an explicit project working directory,
   read-only sandboxing, and a unique output filename. Do not select a different
   model, bypass sandbox/approvals, or modify plugin settings for this one-off call.
4. Give the fork one precise question, the relevant proposal revision and current
   evidence paths, and a request for evidence-backed findings. Tell it to read
   advisory/CONSULT-CONTEXT.md. Do not attach the entire Claude conversation.
5. Capture the final response under advisory/consultations/ using a unique request
   ID. Record the exact question, parent session ID, inspected revision, and outcome
   alongside it. The invoking CLI writes the output; the reviewer must not edit
   repository files or launch builds/agents.
6. Read the response and record what you accept, reject, or defer in the existing
   coordinator response/ticket workflow. Advice never approves implementation.
7. If the session cannot be forked or the call fails, report that explicitly.
   Do not silently substitute a fresh session or claim contextual consultation
   succeeded. Do not retry indefinitely or terminate unrelated processes.

## Invocation example

Run from the repository root. First create advisory/consultations/ if absent and
choose an unused output name. This example name must not overwrite an earlier run.
The CLI options were verified with installed codex-cli 0.166.0 help; the end-to-end
fork has not yet been tested.

```powershell
codex exec --sandbox read-only -C "C:/Users/apoch/Projects/us.pochaev/dfs/duplicate-file-scanner" fork 01a19abb-50b6-77d2-97ca-d3bb6b13bf59 -o "advisory/consultations/consult-001-answer.md" "You are a fork of the project's advisor conversation. Read advisory/CONSULT-CONTEXT.md and advisory/proposals/ADV-002-r1.md. Assess how to integrate bounded contextual consultations with the coordinator workflow. Identify material gaps and the smallest useful validation. Inspect current relevant files read-only. Do not edit files, run builds, launch agents, or implement changes. Return concise evidence-backed findings."
```

## Context limits

The fork inherits saved context available at launch; it does not receive future
turns automatically. Its answer does not automatically appear in the parent chat.
Durable consultation files let the parent advisor catch up on its next turn.
This is a one-off handoff instruction, not an installed automation or permanent
plugin default. Future automation remains subject to the agreed evaluation and
implementation-approval workflow.
