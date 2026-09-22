---
id: DFS-016
title: Introduce a CCW ticket namespace for process and tooling work
status: done
type: feature
scope: [build, docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [.claude/skills/tickets, CLAUDE.md, docs/tickets/completed/DFS-008-tickets-skill-archetypes-and-models, docs/tickets/completed/DFS-009-pipeline-stage-archetype, docs/tickets/completed/DFS-010-story-gate-tools, docs/tickets/backlog/DFS-011-story-agents, docs/tickets/backlog/DFS-012-story-orchestrator-skill, docs/tickets/backlog/DFS-013-run-harness-pilot, docs/tickets/completed/DFS-014-learn-skill-log-and-reflect, docs/tickets/backlog/DFS-015-worktree-aware-tickets-lifecycle, docs/design/story-orchestration.md, docs/tickets/backlog/DFS-001-group-files-by-size/DFS-001-ticket.md, docs/tickets/backlog/DFS-005-start-scan-and-wiring/DFS-005-ticket.md, docs/tickets/backlog/DFS-016-ccw-ticket-namespace]
blocked_reason:
created: 2026-09-22
updated: 2026-09-22
completed: 2026-09-22
---

# DFS-016: Introduce a CCW ticket namespace for process and tooling work

## Story

As the maintainer growing the ticket-driven agent harness alongside the product it builds
I want tickets about the harness itself (skills, agents, archetypes, the tickets/learn machinery) tracked under their own ID sequence, separate from the product backlog
So that the two kinds of work stay easy to tell apart in the backlog, and each keeps its own numbering

## Context

All tickets today share one global sequence, `DFS-NNN`, under `docs/tickets/` (CLAUDE.md's "Tickets" section; the `tickets` skill's `new` mode allocates the next ID as the highest number found in `backlog/` and `completed/` combined, plus one). In practice two different kinds of work already share that sequence: product tickets that build the duplicate-file-scanner itself (DFS-001 to DFS-007, the detection pipeline and CLI wiring), and process/tooling tickets that build or change the harness doing the building (DFS-008 tickets-skill archetypes and models, DFS-009 the `pipeline-stage` archetype, DFS-010 story-gate tools, DFS-014 the `learn` skill and `reflect` agent, DFS-015 worktree-aware ticket lifecycle, and the design-only DFS-011 to DFS-013 story-pipeline tickets). The split was proposed in conversation on 2026-09-22, prompted by that imbalance becoming visible once DFS-015 was refined.

There is already a working precedent for a second, independent ID sequence in this project: `docs/learnings/log.md` uses its own `L-NNN` numbering, entirely separate from the tickets skill's machinery, for a different kind of record (friction found while working, not a unit of work to implement). This ticket proposes the same kind of separate sequence for process/tooling tickets, tentatively prefixed `CCW` (Claude Code Workflow), governed by the same `tickets` skill and lifecycle, alongside the existing `DFS` product sequence.

Three ways to start this were discussed: build it as a ticket now (chosen), design it in conversation first and only write it up afterward, or log it as a `learn` entry. The ticket path was chosen because it is this project's standing convention for any change of substance, including changes to the ticket system itself, and because it forces the refinement interview's discipline (acceptance criteria, scenarios, the `ready` gate) onto a change that touches two `shared`-impact files (`CLAUDE.md`, the `tickets` skill).

**Decided (2026-09-22): a ticket is `CCW` or `DFS` by intent (what its Story is about), not by `touches` alone; `validate` additionally flags, as an informational note rather than an error, a `CCW` ticket whose `touches` reaches into `file-scanner/`/`cli/`, or a `DFS` ticket whose `touches` is confined to `.claude/`, `docs/design/` or `docs/learnings/`.** `touches` alone can't decide a `CLAUDE.md` edit, since that file holds both product design decisions and process rules; intent decides those cleanly, and the mechanical note still catches the ordinary mis-filing mistake.

**Decided (2026-09-22): the prefix is `CCW`.** No collisions found anywhere in the repo or its tooling (checked by grep across markdown, Java, Gradle and YAML). Matches "Claude Code Workflow", and is accurate today since the harness is genuinely built around Claude Code specifically (skills, subagents, `.claude/` layout), not a tool-agnostic workflow engine.

**Decided (2026-09-22): `CCW-NNN` and `DFS-NNN` folders coexist in the same `docs/tickets/{backlog,completed}/` tree**, distinguished only by the ID prefix in the `{ID}-{slug}` folder name — no separate subtree. Smallest change today; a later move to a fully separate tree (e.g. if the harness tooling ever became its own repo) stays a clean `git mv` at that point, not a redesign now.

**Decided (2026-09-22): all eight existing harness tickets are relabeled from `DFS` to `CCW`** — DFS-008, DFS-009, DFS-010, DFS-011, DFS-012, DFS-013, DFS-014 and DFS-015 become `CCW-001` through `CCW-008` respectively, in that same relative order. Chosen over leaving them as historical `DFS` entries, despite that option avoiding every complication below, because internal consistency of the harness's own numbering was preferred. Scope includes DFS-011 to DFS-013 (still bare drafts) alongside the five done/ready ones, so that DFS-011's and DFS-012's `depends_on` lists don't end up mixing prefixes by accident before the cross-namespace question (below) was even decided. What relabeling costs, concretely: 8 ticket folders renamed and moved, `depends_on` updated in the renamed `DFS-011` (named `DFS-009, DFS-010` before the rename) and `DFS-012` (named `DFS-008, DFS-010, DFS-011`), and inline ID mentions updated in `docs/design/story-orchestration.md`, `DFS-001-ticket.md` and `DFS-005-ticket.md`. What it can't fix: commits already made (`[DFS-009]`, `[DFS-010]`, etc.) are permanent under "earlier commits are not rewritten", and `docs/learnings/log.md`'s Evidence lines quote those commit subjects verbatim — both are left as-is, and each renamed ticket's own file states its former `DFS` ID so a `[DFS-009]`-style commit search still leads somewhere.

**Decided (2026-09-22): `DFS-016` (this ticket) is not renamed.** It stays in the `DFS` sequence permanently, as the ticket that introduced `CCW`, the same way DFS-008 stays `archetype: none` forever despite inventing the archetype field — a founding ticket recording when and how the split happened, not retroactively subject to the split itself.

**Decided (2026-09-22): `/tickets new` infers the namespace from the title/context, asking explicitly only when it cannot decide; `/tickets list` keeps one merged table covering both namespaces.**

**Decided (2026-09-22): a `depends_on` link may cross namespaces (a `DFS` ticket depending on a `CCW` ticket, or vice versa); `validate` flags a cross-namespace link as an informational note, not an error.** Matches the light-touch mechanical-safety-net pattern already used for the boundary rule above, and doesn't forbid the real case this ticket's own Context raises (a future product ticket needing a harness feature built first).

**Decided (2026-09-22): `archetype: none`, verified by a walkthrough of every scenario on a scratch copy of `docs/tickets/`**, evidence noted in this ticket (DFS-014's and DFS-015's precedent) — this ticket's actual migration step (renaming eight real ticket folders, rewriting cross-references) needs to run once for real, not just be described correctly in prose.

**Decided (2026-09-22): the migration of the eight tickets is tracked in a file inside this ticket's own folder**, listing for each ticket its old ID, new ID, old path, new path, and which other files' references to it were updated.

## Acceptance criteria

The full ID of each criterion is `DFS-016.AC-<n>`.

- **AC-1** A ticket is classified `CCW` or `DFS` by what its Story is about (harness/tooling versus the duplicate-file-scanner product), not solely by its `touches` list.
- **AC-2** `/tickets validate` reports, as an informational note rather than an error, a `CCW` ticket whose `touches` includes a `file-scanner/` or `cli/` path, or a `DFS` ticket whose `touches` is confined entirely to `.claude/`, `docs/design/` or `docs/learnings/` paths.
- **AC-3** New tickets are allocated `CCW-NNN` or `DFS-NNN` from that prefix's own sequence (the highest number of that prefix found across both `backlog/` and `completed/`, plus one); an ID already used in either sequence, including one later relabeled into the other namespace, is never reused.
- **AC-4** `CCW-NNN` and `DFS-NNN` ticket folders coexist directly under the same `docs/tickets/backlog/` and `docs/tickets/completed/`, with no separate subtree.
- **AC-5** `/tickets new <title>` infers which namespace a new ticket belongs to from the title and conversation context, and asks explicitly only when it cannot decide.
- **AC-6** `/tickets list` shows one merged table covering both namespaces, not two separate tables.
- **AC-7** DFS-008, DFS-009, DFS-010, DFS-011, DFS-012, DFS-013, DFS-014 and DFS-015 are renamed to CCW-001 through CCW-008 respectively: each folder is moved, each ticket's `id` frontmatter is updated, and each renamed ticket's file states its former DFS ID.
- **AC-8** Every `depends_on` entry and inline prose mention of a renamed ticket (in the renamed DFS-011 and DFS-012, and in `docs/design/story-orchestration.md`, `DFS-001-ticket.md`, `DFS-005-ticket.md`) is updated to the ticket's new CCW ID.
- **AC-9** `depends_on` may name an ID from the other namespace; `/tickets validate`'s cycle check and the "dependencies not all done" gate treat IDs uniformly regardless of prefix, and a cross-namespace link is reported only as an informational note.
- **AC-10** DFS-016 itself is not renamed and remains in the `DFS` sequence after the migration.
- **AC-11** `CLAUDE.md`'s "Tickets" section documents both namespaces, the boundary rule (AC-1), and that IDs are never reused within a sequence, including one relabeled into the other namespace.
- **AC-12** A file inside `docs/tickets/backlog/DFS-016-ccw-ticket-namespace/` lists, for each of the eight renamed tickets, its old ID, new ID, old path, new path, and which other files' references to it were updated.

## Scenarios

```gherkin
Feature: CCW ticket namespace

  Background:
    Given a scratch copy of docs/tickets/ seeded with the current backlog

  @AC-1
  Scenario: A ticket about the harness is classified CCW regardless of touches
    Given a draft ticket whose Story is about changing the tickets skill, touching only .claude/skills/tickets
    When it is filed
    Then it is assigned a CCW id

  @AC-1
  Scenario: A ticket about the product is classified DFS
    Given a draft ticket whose Story is about the file-scanner's detection pipeline
    When it is filed
    Then it is assigned a DFS id

  @AC-1
  Scenario: A CLAUDE.md edit is classified by which section it changes
    Given two draft tickets that each touch only CLAUDE.md, one adding a product design decision and one adding a process rule
    When each is filed
    Then the design-decision ticket is DFS and the process-rule ticket is CCW

  @AC-2
  Scenario: validate flags a CCW ticket that touches product code
    Given a CCW ticket whose touches includes a file-scanner path
    When "/tickets validate" runs
    Then it reports an informational note naming the ticket, not an error

  @AC-2
  Scenario: validate flags a DFS ticket confined to harness paths
    Given a DFS ticket whose touches is only under .claude/
    When "/tickets validate" runs
    Then it reports the same kind of informational note

  @AC-3
  Scenario: A new CCW ticket gets the next CCW number
    Given the highest existing CCW id is CCW-008
    When a new harness ticket is filed and inferred as CCW
    Then it is assigned CCW-009

  @AC-3
  Scenario: A relabeled ticket's old id is never reused
    Given DFS-009 was relabeled to CCW-002 during the migration
    When a brand-new ticket is filed and inferred as DFS
    Then its id is not DFS-009

  @AC-4
  Scenario: CCW and DFS folders sit side by side
    Given the migration has run
    When docs/tickets/backlog/ is listed
    Then CCW-* and DFS-* folders both appear directly under it, with no separate subtree

  @AC-5
  Scenario: new infers the namespace without asking
    Given a title that clearly describes a skill change
    When "/tickets new <title>" runs
    Then the ticket is filed as CCW without an explicit prompt

  @AC-5
  Scenario: new asks when the namespace is ambiguous
    Given a title that could plausibly be either product or process work
    When "/tickets new <title>" runs
    Then it asks which namespace applies before filing

  @AC-6
  Scenario: list shows one merged table
    When "/tickets list" runs
    Then a single table lists both CCW-* and DFS-* tickets together

  @AC-7
  Scenario: The eight harness tickets are renamed
    Given DFS-008 through DFS-015 covering the harness work
    When the migration runs
    Then they become CCW-001 through CCW-008 respectively, each ticket file stating its former DFS id

  @AC-8
  Scenario: depends_on cross-references are updated
    Given DFS-011's depends_on names DFS-009 and DFS-010 before the migration
    When the migration runs
    Then the renamed ticket's depends_on names CCW-002 and CCW-003 instead

  @AC-8
  Scenario: Prose mentions are updated
    Given docs/design/story-orchestration.md mentions DFS-010 by id in prose
    When the migration runs
    Then the same passage names CCW-003 instead

  @AC-9
  Scenario: A product ticket may depend on a harness ticket
    Given a DFS ticket with depends_on: [CCW-005]
    When "/tickets validate" runs
    Then no error is reported for the cross-namespace dependency, only an informational note

  @AC-10
  Scenario: DFS-016 keeps its own id
    Given the migration has completed
    When docs/tickets is inspected
    Then DFS-016 is still named DFS-016, not renamed into the CCW sequence

  @AC-11
  Scenario: CLAUDE.md documents the split
    When CLAUDE.md's Tickets section is read after this ticket ships
    Then it states the two namespaces, the boundary rule, and that ids are never reused across a relabel

  @AC-12
  Scenario: The migration is tracked in the ticket's own folder
    Given the eight tickets have been renamed
    When docs/tickets/backlog/DFS-016-ccw-ticket-namespace/ is inspected
    Then it contains a file listing each ticket's old id, new id, old path, new path, and which files' references were updated
```

## Scope

**Touches:**
- `.claude/skills/tickets`: per-prefix ID allocation, `new`'s namespace inference, `list`'s merged table, `validate`'s new informational notes (boundary mismatch, cross-namespace dependency)
- `CLAUDE.md`: the "Tickets" section — both namespaces, the boundary rule, and the ID-reuse rule extended to a relabeled ID
- `docs/tickets/backlog/DFS-008-*` through `DFS-015-*` and their `completed/` counterparts: renamed to `CCW-001` through `CCW-008`
- The renamed `CCW-004` (formerly DFS-011) and `CCW-005` (formerly DFS-012): `depends_on` updated
- `docs/design/story-orchestration.md`, `docs/tickets/backlog/DFS-001-group-files-by-size/DFS-001-ticket.md`, `docs/tickets/backlog/DFS-005-start-scan-and-wiring/DFS-005-ticket.md`: inline ID mentions updated
- `docs/tickets/backlog/DFS-016-ccw-ticket-namespace/`: this ticket's own folder, to add the migration-tracking file (AC-12)

**Out of scope:**
- Editing `docs/learnings/log.md`'s Evidence lines; they quote actual commit subjects, which still say the old `DFS` ID and are left as-is (commits are not rewritten)
- Renaming DFS-016 itself
- The blog-series snapshot repo outside this codebase (its `sources/tickets/` copy is refreshed separately by the `blog-capture` skill)

## Definition of done

- [x] A walkthrough of every scenario above, on a scratch copy of `docs/tickets/`, shows the outcome (evidence noted in this ticket, DFS-014's/DFS-015's precedent)
  - Evidence: see "Walkthrough evidence" below.
- [x] No files changed outside the paths listed under Touches
  - Evidence: `git status --porcelain` (below) lists exactly `.claude/skills/tickets/SKILL.md`, `CLAUDE.md`, `docs/design/story-orchestration.md`, the eight renamed ticket folders, `DFS-001-ticket.md`, `DFS-005-ticket.md`, and this ticket's own folder (its status edit and the new `migration-log.md`) — every one of them named under Touches, nothing else.
- [x] The eight-ticket migration is tracked in a file in this ticket's own folder (AC-12): old id, new id, old path, new path, files updated
  - Evidence: `docs/tickets/backlog/DFS-016-ccw-ticket-namespace/migration-log.md`.
- [x] `CLAUDE.md`'s Tickets section states the boundary rule and both namespaces
  - Evidence: the Tickets paragraph now opens with "one backlog holding two independent ID sequences, `DFS-NNN`... and `CCW-NNN`...", states the boundary rule ("decided by what its Story is about, not by what it `touches` alone"), and the ID-reuse rule extended to a relabeled ID.
- [x] `git status` after the migration shows exactly the renamed/moved ticket folders plus the files named under Touches, nothing else
  - Evidence: see the `git status --porcelain` output quoted in "Walkthrough evidence" below.

## Open questions

None.

## Walkthrough evidence

Run on 2026-09-22. The actual eight-ticket migration (AC-7, AC-8, AC-10) was performed for real, directly on the working backlog, not simulated — DFS-016's whole point is to actually relabel these tickets, the same way DFS-008 made real, permanent edits to DFS-001 and DFS-010 while verifying its general rules on a scratch copy (its own "Walkthrough evidence" section). The skill's *behavioral* rules (new-ticket ID allocation, `validate`'s two new informational checks) were verified separately on a disposable scratch copy of `docs/tickets/` plus three throwaway fixture tickets, deleted afterward, so as not to leave test artifacts in the real backlog.

**Mechanically run, against the real (migrated) backlog:**
- S12 (@AC-7, eight tickets renamed): `git status --porcelain` shows all eight as clean `RM` (100% similarity) renames; each file's `id` frontmatter and heading now read `CCW-00N`, and each carries the "Formerly DFS-0NN" note (`grep` confirmed exactly one such line per file, no other stray old-ID tokens: zero matches for `DFS-0(08|09|10|11|12|13|14|15)` anywhere under `docs/` or `CLAUDE.md` outside `docs/learnings/log.md` and this ticket's own `migration-log.md`, both deliberately excluded).
- S13 (@AC-8, `depends_on` updated): confirmed the final `depends_on` line of all eight files by `grep`; each resolves correctly to the new `CCW-00N` chain (`CCW-002: [CCW-001]`, `CCW-004: [CCW-002, CCW-003]`, `CCW-005: [CCW-001, CCW-003, CCW-004]`, `CCW-006: [CCW-005]`) with no dangling old IDs.
- S14 (@AC-8, prose mentions updated): `docs/design/story-orchestration.md` (31 occurrences), `DFS-001-ticket.md` (1), `DFS-005-ticket.md` (1) all replaced; re-`grep`ped afterward for zero remaining matches.
- S16 (@AC-10, DFS-016 keeps its own id): `git status --porcelain` shows no rename entry for `docs/tickets/backlog/DFS-016-ccw-ticket-namespace/`; the file is still `id: DFS-016`.
- S18 (@AC-12, migration tracked in the ticket's own folder): `docs/tickets/backlog/DFS-016-ccw-ticket-namespace/migration-log.md` exists with the old id/new id/old path/new path table and the references-updated list.

**Mechanically run, on a disposable scratch copy plus throwaway fixtures (`CCW-099`, `DFS-098`, `DFS-097`), deleted after the run:**
- S6 (@AC-3, next CCW number): highest CCW number in the (already-migrated) backlog is 008, so the next allocation is CCW-009, as expected.
- S7 (@AC-3, relabeled id never reused): `DFS-009` no longer exists as a folder/id anywhere in the backlog, so a fresh highest-number scan can never reallocate it; the next `DFS` id is DFS-017 (DFS-016 being the current highest surviving `DFS` ticket).
- S8 (@AC-4, folders side by side): directly observed — `CCW-*` and `DFS-*` folders list together, unsorted by namespace, directly under both `backlog/` and `completed/`.
- S4 (@AC-2, CCW ticket touching product code): fixture `CCW-099` (touches `file-scanner/...`) correctly produced the informational note; the same check run against the whole real backlog produced zero false notes on any genuine `CCW` ticket.
- S5 (@AC-2, DFS ticket confined to harness paths): fixture `DFS-098` (touches only `.claude/skills/tickets`) correctly produced the note. Run against the real backlog, the same check correctly flagged `DFS-016` itself — a true positive, not a false one: DFS-016 legitimately touches only harness paths but was deliberately kept in the `DFS` sequence (decision recorded above), which is exactly the "worth a human glance, not an error" case the rule exists for.
- S15 (@AC-9, cross-namespace dependency is informational only): fixture `DFS-097` (`depends_on: [CCW-005]`) correctly produced the note and no error; run against the real backlog, zero cross-namespace dependencies currently exist (none were created by this round), so no false positives to check.

**Checked by reading only, not run** (per `L-009`: these describe judgment a model applies when interpreting a ticket's Story or a title, which a script cannot decide, so this is a weaker check than the mechanical runs above):
- S1, S2, S3 (@AC-1, classification by intent, including the `CLAUDE.md`-edit case): confirmed the `SKILL.md` "Namespaces" section and `CLAUDE.md`'s updated Tickets paragraph both state the rule in words matching these scenarios; not exercised by actually filing a ticket through a live `/tickets new` invocation.
- S9, S10 (@AC-5, `new` infers vs. asks): confirmed `SKILL.md`'s `new` mode text states both behaviors; not run live.
- S11 (@AC-6, `list` shows one merged table): confirmed `SKILL.md`'s `list` mode text now explicitly says "one table... covering both the `DFS` and `CCW` namespaces together, not a separate table per namespace"; not run live. (The merged-table behavior was already implicit before this ticket, since `list` has always read both folders into one scan — this scenario mainly guards against a future edit accidentally splitting it into two tables.)
- S17 (@AC-11, `CLAUDE.md` documents the split): confirmed by reading the updated Tickets paragraph directly (quoted under Definition of done above).

```
$ git status --porcelain
 M .claude/skills/tickets/SKILL.md
 M CLAUDE.md
 M docs/design/story-orchestration.md
RM docs/tickets/backlog/DFS-011-story-agents/DFS-011-ticket.md -> docs/tickets/backlog/CCW-004-story-agents/CCW-004-ticket.md
RM docs/tickets/backlog/DFS-012-story-orchestrator-skill/DFS-012-ticket.md -> docs/tickets/backlog/CCW-005-story-orchestrator-skill/CCW-005-ticket.md
RM docs/tickets/backlog/DFS-013-run-harness-pilot/DFS-013-ticket.md -> docs/tickets/backlog/CCW-006-run-harness-pilot/CCW-006-ticket.md
RM docs/tickets/backlog/DFS-015-worktree-aware-tickets-lifecycle/DFS-015-ticket.md -> docs/tickets/backlog/CCW-008-worktree-aware-tickets-lifecycle/CCW-008-ticket.md
 M docs/tickets/backlog/DFS-001-group-files-by-size/DFS-001-ticket.md
 M docs/tickets/backlog/DFS-005-start-scan-and-wiring/DFS-005-ticket.md
 M docs/tickets/backlog/DFS-016-ccw-ticket-namespace/DFS-016-ticket.md
RM docs/tickets/completed/DFS-008-tickets-skill-archetypes-and-models/DFS-008-ticket.md -> docs/tickets/completed/CCW-001-tickets-skill-archetypes-and-models/CCW-001-ticket.md
RM docs/tickets/completed/DFS-009-pipeline-stage-archetype/DFS-009-ticket.md -> docs/tickets/completed/CCW-002-pipeline-stage-archetype/CCW-002-ticket.md
RM docs/tickets/completed/DFS-010-story-gate-tools/DFS-010-ticket.md -> docs/tickets/completed/CCW-003-story-gate-tools/CCW-003-ticket.md
RM docs/tickets/completed/DFS-014-learn-skill-log-and-reflect/DFS-014-ticket.md -> docs/tickets/completed/CCW-007-learn-skill-log-and-reflect/CCW-007-ticket.md
?? docs/tickets/backlog/DFS-016-ccw-ticket-namespace/migration-log.md
```
