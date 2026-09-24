---
id: CCW-010
title: Avoid learnings-log number collisions across parallel sessions
status: in-review
type: fix
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [.claude/skills/learn/SKILL.md, .claude/skills/story/SKILL.md, .claude/agents/reflect.md, docs/tickets, docs/learnings/log.md]
blocked_reason:
created: 2026-09-23
updated: 2026-09-23
completed:
---

# CCW-010: Avoid learnings-log number collisions across parallel sessions

## Story

As two sessions working on diverged branches at the same time
I want the learnings log's `L-<n>` numbers to never collide
So that closing or merging either session's work never forces a hand-resolved renumbering

## Context

Spun off from `docs/learnings/log.md` L-033 rather than applied directly — real design questions (where entries live, how they stay uniquely identified without coordination, what happens to the 33 that already exist) across two skills, not a one-line edit. Follows the precedent L-008 (→ CCW-008) and L-027 (→ CCW-009) already set for the same reason.

Both `/learn add`/`apply`/`reflect` (`.claude/skills/learn/SKILL.md`) and `/story learn` (`.claude/skills/story/SKILL.md`'s `learn` mode) allocate a new entry's number the same way: `grep -o '^### L-[0-9]*'` against the session's own copy of `docs/learnings/log.md`, highest plus one. Two sessions on diverged branches each see only their own branch's copy, so each can independently compute the same "next" number for two entirely unrelated findings.

This happened for real closing DFS-002 (2026-09-23): a parallel session had built and closed CCW-008 directly on `main`, proposing its own two learnings the same way this session did. Both sessions landed on `L-024` and `L-025` for four unrelated findings between them. Discovered only at merge time (`git merge` conflicted on `docs/learnings/log.md`); resolved by hand, renumbering the `main`-side pair to `L-031`/`L-032` after the fact. The log's own rule ("numbers are never reused") was already broken between the two branches before either was merged — this ticket exists so the next collision doesn't need a human to notice and fix it during a merge.

**Decided (2026-09-23): identity comes from the file path, not a coordinated counter.** Each story already has its own `docs/tickets/{backlog,completed}/<ID>-<slug>/learnings.md` (raw friction notes, D6). Structured entries move into that same file, permanently — not the shared `docs/learnings/log.md` — and are identified `<TICKET-ID>.L-<n>` (e.g. `DFS-002.L-1`), with `<n>` counted only within that one file. Since a given ticket is only legitimately worked by one session at a time, local numbering can never collide; since two different tickets' entries live in two different files, two sessions on different tickets never touch the same file at all, so a merge between them can't conflict on learnings content, let alone silently reuse a number. This removes the earlier idea of a serialization point (assigning the number at merge or close time) entirely — there is no shared counter left to serialize.

**Decided (2026-09-23): no separate consolidated file; `/learn review` (and `/story learn`'s equivalent) scans every ticket's own `learnings.md` live.** Ranking (recurrences, impact, number) works the same as today, just built from many small files instead of one. Accepted trade-off: this scan grows with the backlog's size; not a concern yet, revisit if it ever is.

**Decided (2026-09-23): a recurrence of an existing entry in a later ticket still edits that entry where it lives — in its *originating* ticket's own `learnings.md`.** This is a deliberate exception to "never edit another ticket's file": it is the same operation `/learn add` already does today (appending to an existing entry's Recurrences field), just now located in a specific ticket's folder instead of the shared log. Collision risk is low in practice (a recurrence almost always names an already-`completed` ticket, which no other session is concurrently working), and even a collision here is an ordinary single-file git conflict, not a silent same-identifier-different-meaning collision — the actual failure this ticket exists to prevent. Stage and commit that file by name, per `CLAUDE.md`'s existing parallel-session convention.

**Decided (2026-09-23): all 33 entries currently in `docs/learnings/log.md` are migrated, not left as a frozen legacy list.** Each moves into its originating ticket's own `learnings.md` (the ticket named in its own Evidence field), renumbered locally (`<TICKET-ID>.L-<n>`), every other field unchanged. `docs/learnings/log.md` is retired once migration completes — replaced with a short pointer noting the new convention, not deleted outright, so a link to an old `L-<n>` citation (in a commit message, another ticket's text) still leads somewhere.

## Acceptance criteria

The full ID of each criterion is `CCW-010.AC-<n>`.

- **AC-1** A newly proposed learnings entry for ticket `<ID>` is written to that ticket's own `docs/tickets/{backlog,completed}/<ID>-<slug>/learnings.md`, identified `<ID>.L-<n>` with `<n>` counted only within that file.
- **AC-2** Two sessions proposing new entries for two different tickets, on diverged branches, never edit the same file; merging their branches produces no conflict on learnings content.
- **AC-3** `/learn review` (and `/story learn`'s equivalent step) lists every open (`proposed`/`parked`) entry across every ticket, built by scanning each ticket's own `learnings.md`, ranked the same way as today (recurrences in distinct tickets, then shared before local, then by number).
- **AC-4** A recurrence of an existing entry in a later ticket is recorded by editing that entry in its originating ticket's own `learnings.md`, staged and committed by name — not by creating a duplicate entry elsewhere.
- **AC-5** Every entry currently in `docs/learnings/log.md` is migrated into its originating ticket's own `learnings.md`, renumbered locally, with every other field preserved unchanged; `docs/learnings/log.md` is replaced with a short pointer to the new convention.
- **AC-6** `/learn apply`'s mark-applied-and-list-changed-files step, and the mechanical check (numbering has no gaps or duplicates *within a file*, every `applied`/`parked`/`rejected` entry has a filled `Decision`), operate per-ticket-file instead of against one shared file.

## Scenarios

```gherkin
Feature: Per-ticket learnings identity

  @AC-1
  Scenario: A new entry is written to its own ticket's file
    Given ticket DFS-020 has no learnings entries yet
    When a new finding is proposed for DFS-020
    Then it is written to docs/tickets/backlog/DFS-020-*/learnings.md as "DFS-020.L-1"
    And docs/learnings/log.md is not touched

  @AC-1
  Scenario: A second entry in the same ticket continues its own local sequence
    Given ticket DFS-020's learnings.md already has "DFS-020.L-1"
    When a second, unrelated finding is proposed for DFS-020
    Then it is written as "DFS-020.L-2"

  @AC-2
  Scenario: Two tickets on diverged branches never collide
    Given session A proposes a new entry for ticket DFS-021 on branch tickets/DFS-021-x
    And session B proposes a new entry for ticket CCW-011 on branch tickets/CCW-011-y, diverged from A's branch
    When both branches are merged into main, in either order
    Then no merge conflict occurs on either ticket's learnings.md
    And DFS-021's entry is still "DFS-021.L-1" and CCW-011's is still "CCW-011.L-1"

  @AC-3
  Scenario: review lists entries from several tickets, ranked
    Given DFS-001's learnings.md has one proposed entry and CCW-003's has one parked entry recurring in two tickets
    When "/learn review" runs
    Then both entries appear in one table
    And the entry recurring in more distinct tickets is ranked first

  @AC-4
  Scenario: A recurrence edits the original ticket's file
    Given DFS-001.L-3 exists in docs/tickets/completed/DFS-001-*/learnings.md
    When the same friction recurs while working DFS-030
    Then DFS-001.L-3's Recurrences field is edited in place, naming DFS-030
    And no new entry is created in DFS-030's own learnings.md for this friction

  @AC-5
  Scenario: Existing entries are migrated to their originating ticket
    Given docs/learnings/log.md's L-021 cites DFS-001-group-files-by-size as its Evidence
    When migration runs
    Then "DFS-001.L-<n>" appears in docs/tickets/completed/DFS-001-group-files-by-size/learnings.md with L-021's content unchanged except its identifier
    And L-021 no longer appears in docs/learnings/log.md

  @AC-5
  Scenario: The old shared log becomes a pointer
    Given migration has completed
    When docs/learnings/log.md is read
    Then it states the new per-ticket convention and does not list any entries itself

  @AC-6
  Scenario: The mechanical check runs per file
    Given a ticket's learnings.md has entries "X.L-1" (applied, Decision filled) and "X.L-2" (proposed, Decision empty)
    When the check runs after a write to that file
    Then it reports no problem
    And it never compares that file's numbering against any other ticket's file
```

## Scope

**Touches:**
- `.claude/skills/learn/SKILL.md`: every mode (`add`, `review`, `decide`, `apply`, `reflect`) and the check, rewritten for per-ticket files and `<ID>.L-<n>` identity
- `.claude/skills/story/SKILL.md`: `learn` mode's write step, same change
- `.claude/agents/reflect.md`: reads its own ticket's `learnings.md` instead of the shared log, and matches only within it — found while implementing, not in the original refinement: the agent's own "Matching against the log" step is a real functional dependency on the shared file, not just stale wording, and retiring that file without fixing it would make `reflect` unable to detect any recurrence at all
- `docs/tickets`: every existing ticket's `learnings.md` gains migrated entries where `docs/learnings/log.md`'s Evidence names it
- `docs/learnings/log.md`: replaced with a short pointer to the new convention

**Out of scope:**
- Any change to an entry's own eight fields (Status, Impact, Target, Change, Evidence, Recurrences, Recommendation, Decision) — only where entries live and how they're identified
- Retroactively fixing any other stale cross-branch reference outside the learnings log (found during DFS-002's close, not this ticket's concern)
- A UI or tool for the cross-ticket scan (`/learn review`) beyond what the skill text itself specifies — plain reading of each file

## Definition of done

- [x] Every scenario above is checked by reading the skill text and a real walkthrough. Evidence: created a scratch ticket `ZZZ-999`, wrote `ZZZ-999.L-1` to its own `learnings.md` (AC-1); ran the actual cross-ticket scan `/learn review` performs (`grep`-based, across every `docs/tickets/{backlog,completed}/*/learnings.md`) and confirmed it correctly listed 11 open entries across 6 real tickets plus the scratch one, each with its full `<ID>.L-<n>` (AC-3); deleted the scratch ticket afterward with no trace (`git status` confirmed clean). AC-5 (migration) was run for real against the actual 33 entries, not a scratch copy — see the next item.
- [x] No files changed outside the paths listed under Touches. Evidence: `git status --porcelain` after all edits shows only `.claude/agents/reflect.md`, `.claude/skills/learn/SKILL.md`, `.claude/skills/story/SKILL.md`, `docs/learnings/log.md`, and files under `docs/tickets` — nothing else.
- [x] Every one of the 33 entries currently in `docs/learnings/log.md` is accounted for after migration. Evidence: mechanical count (`grep -rho '^### [A-Z]*-[0-9]*\.L-[0-9]*'` across `docs/tickets/{backlog,completed}`) gives exactly 33, no duplicate or gapped local number within any one ticket's file, and every entry's Status/Decision pairing checked consistent.
- [x] `docs/learnings/log.md`'s replacement pointer is readable on its own and states the new convention clearly enough that a session unfamiliar with this ticket can find where a given ticket's learnings live. Evidence: rewritten with a plain-language explanation, the new file-path/identifier convention, and a full old-`L-<n>`-to-new-identifier lookup table for all 33 migrated entries.

## Open questions

None.
