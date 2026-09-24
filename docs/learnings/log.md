# Learnings log (retired 2026-09-23, CCW-010)

This file is no longer where learnings entries live. It held one global, sequentially-numbered list (`L-001` through `L-033`); two sessions on diverged branches could — and once did — independently compute the same "next" number for two unrelated findings, breaking the log's own "numbers are never reused" rule between the branches before either was merged. See `docs/tickets/completed/DFS-002-partial-hash-stage/learnings.md`, entry `DFS-002.L-8`, for the collision that caused this, and `docs/tickets/completed/CCW-010-avoid-learnings-log-collisions/CCW-010-ticket.md` for the fix.

**Where entries live now:** each ticket keeps its own learnings in its own `docs/tickets/{backlog,completed}/<ID>-<slug>/learnings.md`, identified `<ID>.L-<n>`, numbered only within that one file. A new finding for ticket `<ID>` goes there, never here. `/learn review` (and `/story learn`'s equivalent) lists every open entry by scanning every ticket's own `learnings.md`; see `.claude/skills/learn/SKILL.md`.

**Where the old `L-001`–`L-033` went:** every entry was migrated to its originating ticket's own `learnings.md`, content unchanged except its identifier. A citation to an old number below still resolves — find the ticket, then look for its new local number.

| Old | New | Old | New | Old | New |
|---|---|---|---|---|---|
| L-001 | CCW-003.L-1 | L-012 | CCW-007.L-4 | L-023 | DFS-001.L-3 |
| L-002 | CCW-001.L-1 | L-013 | CCW-004.L-1 | L-024 | DFS-002.L-1 |
| L-003 | CCW-001.L-2 | L-014 | CCW-005.L-1 | L-025 | DFS-002.L-2 |
| L-004 | CCW-001.L-3 | L-015 | CCW-005.L-2 | L-026 | DFS-002.L-3 |
| L-005 | CCW-001.L-4 | L-016 | CCW-005.L-3 | L-027 | DFS-002.L-4 |
| L-006 | CCW-001.L-5 | L-017 | CCW-005.L-4 | L-028 | DFS-002.L-5 |
| L-007 | CCW-007.L-1 | L-018 | CCW-005.L-5 | L-029 | DFS-002.L-6 |
| L-008 | CCW-003.L-2 | L-019 | CCW-005.L-6 | L-030 | DFS-002.L-7 |
| L-009 | CCW-001.L-6 | L-020 | CCW-004.L-2 | L-031 | CCW-008.L-1 |
| L-010 | CCW-007.L-2 | L-021 | DFS-001.L-1 | L-032 | CCW-008.L-2 |
| L-011 | CCW-007.L-3 | L-022 | DFS-001.L-2 | L-033 | DFS-002.L-8 |

Entry bodies were not rewritten to update their own internal `L-<n>` cross-references to other entries (a large, error-prone rewrite for little benefit) — an "L-013" mentioned inside another entry's text still means the old number; use this table to resolve it.
