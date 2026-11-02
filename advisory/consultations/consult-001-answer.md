1. **Recommendation: revise, then proceed with a separate harness ticket depending on CCW-021.**

Use a small deterministic discovery script, coordinator-scoped hooks, and the project-local fork procedure. Preserve the plugin’s single-exchange review pattern without using its launcher unchanged. This session has inherited the advisor conversation, demonstrating that contextual forking works; successful exit and captured output remain for the caller to verify.

2. **Material findings**

- **High — Discovery scope and session identity.** `.claude/settings.json` currently contains permissions only. `lanes.md` identifies owners, but cannot tell a shell hook which session invoked it; the existing skill obtains identity through `ListAgents`. Do not infer coordinator identity from working directory, since sessions share it. Use explicit coordinator-only launch configuration—such as an environment marker supplied when launching `dfs-product`—and validate its named role against the canonical lane map. Missing/mismatched identity must produce no advisory context. Avoid undocumented transcript parsing.

  Use **SessionStart and UserPromptSubmit calling the same script**. Add only a short coordinator catch-up instruction pointing to that script as a fallback, not a second implementation. Empty intake exits successfully with zero stdout. These hooks cover startup and user-prompt turns; they do **not establish discovery on every autonomous/message-driven turn**. Define v1 accordingly, or invoke discovery at explicit coordinator catch-up boundaries too.

- **High — CCW-021 scope and pull correctness.** CCW-021 is ready, specifies push notifications, and excludes polling Git alone. Adding executable hooks, settings, and consultation transport expands its declared scope. Keep integration separate; any push-to-pull change requires the user’s decision and revised criteria. Pull is consistent with advisory discovery, but `git log --all` plus worktree inventory misses **uncommitted ticket changes and uncommitted pipeline state**. Catch-up must inspect relevant ticket/plan files in active worktrees. Existing necessary owner/unblock messages need not disappear with coordinator reporting messages.

- **Medium — Consultation transport.** The installed plugin explicitly runs fresh `npx ... exec`, so it cannot supply inherited context as written. Use `CLAUDE-HANDOFF.md` as the project-local procedure: explicit parent ID, fork, read-only sandbox, explicit root, unique output, one exchange. Do not edit plugin cache. ADV-002 findings **1–2 are no longer blockers for the actual launcher that successfully started this run**, but that does not validate the plugin’s original launcher. **3** still needs successful output/exit verification; **4** is irrelevant if plugin `tmp/` paths are unused; **5** remains a timeout-policy correction; **6** needs unique request IDs.

- **Medium — Stale advisory wording.** ADV-002 prohibits claiming inherited memory, which predates the fork solution. Publish a superseding revision distinguishing inherited saved context from live synchronization. The main lane map still lacks the coordinator entry pending CCW-021.

3. **Changed assumptions / missing evidence**

The supplied transcript measurements shift priority toward avoiding unnecessary main-session turns and growing context, rather than eliminating subagents first. I have not independently audited those measurements. Forking also carries substantial inherited context: use it selectively, not automatically for every proposal. Installed hook identity/output behavior still needs a live check.

4. **Smallest validation and tradeoff**

Exercise the script with empty intake, one proposal, `received`, `evaluated`, superseded revision, and non-coordinator identity. Assert zero stdout for empty/non-coordinator cases and no duplicate ticket creation.

Then run one actual coordinator startup and one user-prompt discovery after publishing a new revision. Verify a lane session receives nothing.

Use **this consultation** as the fork smoke test: verify completion, captured answer, inherited context, and no reviewer edits. Test invalid-session failure separately without another model call.

This adds cheap local checks while avoiding idle model turns. No savings percentage is established. Implementation still requires approval.