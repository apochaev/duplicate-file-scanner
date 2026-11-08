# CCW-026 learnings

## Entries

### CCW-026.L-1: Live-run done-item closed unmet at the user's say-so

- Status: proposed
- Impact: local
- Target: docs/tickets/completed/CCW-026-session-per-ticket-launch/CCW-026-ticket.md (or the tickets skill's done checklist, if made shared)
- Change: When a done-item cannot be run before close (here a live end-to-end run of the new launch flow), record it at refine time as a post-close check or split it into a follow-up ticket, rather than leaving an unticked item to be waived at close.
- Evidence: Commit e28184f changed the last done-item to "- [x] (closed without it at the user's say-so, 2026-11-08) A live run: ...". It was unticked in 529d27d and ab5a054. The ticket was closed with its only end-to-end check never run. After the ready commit 572c2ef there were no scenario or criterion changes, only status moves and this waiver.
- Recurrences: 1 (CCW-026: see Evidence)
- Recommendation: park, a single occurrence and the user chose it on purpose; watch for recurrence on other harness tickets
- Decision:
