# CCW-010 learnings

## Entries

### CCW-010.L-1: Two other files describe the retired shared-log convention and were left unfixed

- Status: proposed
- Impact: shared (`.claude/skills/tickets/SKILL.md` is a skill; `docs/design/story-orchestration.md` is the design doc every future harness ticket reads for context)
- Target: `.claude/skills/tickets/SKILL.md`, `done` mode's pre-close friction prompt ("ask whether any friction... worth recording in `docs/learnings/log.md`... the `learn` skill is the log's only writer"); `docs/design/story-orchestration.md`, D6 ("Decisions are recorded in `docs/learnings/log.md`...")
- Change: update both to describe the per-ticket `learnings.md` convention (CCW-010) instead of the retired shared file. Unlike `.claude/agents/reflect.md` (fixed directly in this ticket, since it was a genuine functional break — the agent could no longer find any entries to match against), both of these are stale *wording* only: the command they name (`/learn add`) still works correctly under the new scheme, so nothing is actually broken, just described inaccurately.
- Evidence: found while implementing CCW-010 itself, 2026-09-23 — a `grep -rn "docs/learnings/log.md"` across `.claude/` and `docs/design/` after retiring the shared file surfaced these two, in addition to `reflect.md` (fixed) and `learn`/`story`'s own `SKILL.md` files (already the point of this ticket).
- Recurrences: 1 (CCW-010)
- Recommendation: apply. Small, low-risk, one-or-two-sentence edits each; leaving stale documentation about a file this same ticket just retired is exactly the kind of gap this project's own learnings log exists to catch.
- Decision:
