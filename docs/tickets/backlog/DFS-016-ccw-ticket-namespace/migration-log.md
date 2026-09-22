# DFS-016 migration log

Tracks the one-time relabeling of the eight existing harness/process tickets from the `DFS` sequence into the new `CCW` sequence, done as part of implementing DFS-016 (2026-09-22), per DFS-016.AC-7, AC-8 and AC-12.

## Renamed tickets

| Old ID | New ID | Old path | New path |
|---|---|---|---|
| DFS-008 | CCW-001 | `docs/tickets/completed/DFS-008-tickets-skill-archetypes-and-models/DFS-008-ticket.md` | `docs/tickets/completed/CCW-001-tickets-skill-archetypes-and-models/CCW-001-ticket.md` |
| DFS-009 | CCW-002 | `docs/tickets/completed/DFS-009-pipeline-stage-archetype/DFS-009-ticket.md` | `docs/tickets/completed/CCW-002-pipeline-stage-archetype/CCW-002-ticket.md` |
| DFS-010 | CCW-003 | `docs/tickets/completed/DFS-010-story-gate-tools/DFS-010-ticket.md` | `docs/tickets/completed/CCW-003-story-gate-tools/CCW-003-ticket.md` |
| DFS-011 | CCW-004 | `docs/tickets/backlog/DFS-011-story-agents/DFS-011-ticket.md` | `docs/tickets/backlog/CCW-004-story-agents/CCW-004-ticket.md` |
| DFS-012 | CCW-005 | `docs/tickets/backlog/DFS-012-story-orchestrator-skill/DFS-012-ticket.md` | `docs/tickets/backlog/CCW-005-story-orchestrator-skill/CCW-005-ticket.md` |
| DFS-013 | CCW-006 | `docs/tickets/backlog/DFS-013-run-harness-pilot/DFS-013-ticket.md` | `docs/tickets/backlog/CCW-006-run-harness-pilot/CCW-006-ticket.md` |
| DFS-014 | CCW-007 | `docs/tickets/completed/DFS-014-learn-skill-log-and-reflect/DFS-014-ticket.md` | `docs/tickets/completed/CCW-007-learn-skill-log-and-reflect/CCW-007-ticket.md` |
| DFS-015 | CCW-008 | `docs/tickets/backlog/DFS-015-worktree-aware-tickets-lifecycle/DFS-015-ticket.md` | `docs/tickets/backlog/CCW-008-worktree-aware-tickets-lifecycle/CCW-008-ticket.md` |

Each move was a plain `git mv` of the folder, then a second `git mv` renaming `{OLD-ID}-ticket.md` to `{NEW-ID}-ticket.md` inside it; `git status` showed all eight as clean renames (similarity 100%), so no content was lost in the move step itself. In each renamed file: the `id` frontmatter field was updated, `updated` was bumped to 2026-09-22, and a note — `*(Formerly {OLD-ID}; relabeled 2026-09-22 by DFS-016, which introduced the CCW namespace.)*` — was inserted directly under the `# {NEW-ID}: <title>` heading (DFS-016.AC-7).

DFS-016 itself is not renamed (decided during refinement); it stays in the `DFS` sequence as the ticket that introduced `CCW`.

## References updated

**`depends_on` fields**, updated to the new IDs:

| Ticket | Old `depends_on` | New `depends_on` |
|---|---|---|
| CCW-002 (formerly DFS-009) | `[DFS-008]` | `[CCW-001]` |
| CCW-004 (formerly DFS-011) | `[DFS-009, DFS-010]` | `[CCW-002, CCW-003]` |
| CCW-005 (formerly DFS-012) | `[DFS-008, DFS-010, DFS-011]` | `[CCW-001, CCW-003, CCW-004]` |
| CCW-006 (formerly DFS-013) | `[DFS-012]` | `[CCW-005]` |

**Deviation from DFS-016 as refined:** DFS-016.AC-8 named only the renamed DFS-011 and DFS-012 as needing their own `depends_on` updated. Implementation additionally found and fixed two more: CCW-002's (formerly DFS-009) `depends_on: [DFS-008]`, and CCW-006's (formerly DFS-013) `depends_on: [DFS-012]` — both were already visible in the `depends_on` grep run while refining decision 4, but AC-8's text only named DFS-011 and DFS-012. Leaving these two as-is would have made `validate`'s "`depends_on` pointing at a missing ticket" check fire on tickets this same migration renamed away, which contradicts the point of the migration. This is a correction of an incomplete AC found during implementation, not a scope expansion: it is the same operation (updating a `depends_on` reference to a renamed ticket) AC-8 already describes, applied to the two instances the ticket's text happened to miss.

**Inline prose mentions of the eight renamed IDs**, updated wherever they appeared as bare `DFS-0NN` tokens (word-boundary match, so only the exact ID token, never a substring of another word):

- Within each of the eight renamed tickets' own body text (self-references to sibling harness tickets, e.g. CCW-005 mentioning "the gate tools (CCW-003, CCW-004, CCW-002)" or CCW-006's `touches` path). This goes beyond DFS-016.AC-8's literal wording, which named only three external files — but leaving these internal cross-references stale inside the very files this migration renamed would have directly undercut the ticket's own goal (telling the two kinds of work apart cleanly) and is squarely within DFS-016's `touches` (the eight ticket folders are listed there in full). Treated as included in AC-8's intent, not as out-of-touches scope creep.
- `docs/design/story-orchestration.md` (31 token occurrences updated, including the "Build tickets" table and its dependency column)
- `docs/tickets/backlog/DFS-001-group-files-by-size/DFS-001-ticket.md` (1 occurrence: a mention of DFS-008 in the "Already done during refinement" note, now CCW-001)
- `docs/tickets/backlog/DFS-005-start-scan-and-wiring/DFS-005-ticket.md` (1 occurrence: a mention of DFS-010, now CCW-003)

**Deliberately left unchanged** (per DFS-016's "Out of scope"):

- `docs/learnings/log.md`'s Evidence lines, which quote actual commit subjects verbatim (e.g. `"docs: add pipeline-stage archetype [DFS-009]"`). Those commits are real, already-made history; CLAUDE.md's "earlier commits are not rewritten" rule means the commit subjects themselves will say `[DFS-009]`, `[DFS-010]`, etc. forever, regardless of this migration. Each renamed ticket's own "Formerly DFS-NNN" note (above) is what lets a reader connect a `[DFS-009]`-style commit search back to its ticket's new home, `CCW-002`.
- The blog-series snapshot repo (`us.pochaev/blog/2026-09-21-agent-harness/sources/tickets/`), outside this codebase; refreshed separately by the `blog-capture` skill.
- DFS-016 itself is not renamed (see above).

## Verification

`git status --porcelain` after all edits below shows only: the eight renamed/moved ticket folders, `.claude/skills/tickets/SKILL.md`, `CLAUDE.md`, `docs/design/story-orchestration.md`, `DFS-001-ticket.md`, `DFS-005-ticket.md`, and this ticket's own folder (including this file) — matching DFS-016's `touches` exactly. See the Walkthrough evidence section of `DFS-016-ticket.md` for the scratch-copy scenario walkthrough.
