# CCW-004 learnings

Migrated from `docs/learnings/log.md` (2026-09-23, CCW-010) — see that file for the full migration note. Numbers were global (`L-<n>`) before the move; each entry's own number below is local to this file only.

## Entries

### CCW-004.L-1: A filter-stage skeleton's null-check runs before its not-implemented throw

- Status: applied
- Impact: shared (an archetype skeleton every pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/FilterStage.java.tmpl`
- Change: reorder the skeleton's guard so `throw new UnsupportedOperationException("not implemented: <TICKET-ID>")` comes before `Objects.requireNonNull(groups, "groups")`, so every stub method is uniformly red in the test phase regardless of which acceptance criterion a test targets; the implementer moves the null-check back above the real logic during implementation.
- Evidence: `docs/tickets/completed/CCW-004-story-agents/CCW-004-ticket.md`, "Demonstration evidence" — running the pipeline live against fixture ticket FIX-001, the architect's stub followed the skeleton verbatim (`requireNonNull` before the throw), which made FIX-001.AC-3's null-argument test pass outright against the stub instead of failing red. The architect, test-author and verifier each independently noticed and flagged it in the story's `learnings.md`/`verification.md` without being told to look for it.
- Recurrences: 1 (CCW-004)
- Recommendation: apply. Three independent agents converged on the same root cause and the same fix on a single run; it will recur on the first real filter-stage ticket with an argument-validation acceptance criterion. (A related half of this friction — whether a red-gate should tolerate a validation AC passing off the stub — was already addressed separately, in the same ticket, by adding explicit guidance to `.claude/agents/test-author.md`; this entry is only the remaining skeleton-ordering fix, which belongs to the archetype, not the agent.)
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-013` (the stub's `requireNonNull` moved to a guiding comment after the throw in `FilterStage.java.tmpl`; `pipeline-stage`'s `archetype.md` `version` bumped 1 → 2).

### CCW-004.L-2: `tickets done` should commit its own changes and merge the story branch into main

- Status: applied
- Impact: shared (`.claude/skills/tickets/SKILL.md`, `done` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `done` mode
- Change: after moving the ticket folder and updating frontmatter, `done` re-stages that one file by name, confirms with `git diff --cached` that the staged content matches the working tree (`git mv` can silently leave the index holding pre-edit content), and commits it itself as a narrow, documented exception to "commits only when asked." If a branch `tickets/<ID>-<slug>` exists and differs from the branch `done` runs from, merge it in (fast-forward if possible, otherwise an ordinary merge commit; on conflict, stop and tell the user to resolve it by hand) and delete it only once the merge succeeds.
- Evidence: closing CCW-004, the `git mv`-based close commit (`45adfd1`) staged pre-edit frontmatter despite the working tree already having the edit — caught only via a `0 insertions/0 deletions` stat, needing corrective commit `a6a2624`. Closing CCW-005 hit the identical bug, caught this time only because of the CCW-004 lesson. The user then asked directly for `done` to commit and merge, with implementation left to best judgement.
- Recurrences: 2 (CCW-004: original, corrected by `a6a2624`; CCW-005: recurred identically, caught by explicit re-staging)
- Recommendation: apply. Two occurrences of the identical stale-index failure across two tickets is a real mechanical pattern; the merge-into-main half serves the same close-out moment and the user asked for both together. Overlaps with CCW-008 ("Make the tickets lifecycle worktree-aware") — worth a glance when that ticket is next refined, in case it wants to extend this further rather than duplicate it.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-020` (the `done` mode addition in `.claude/skills/tickets/SKILL.md`).
