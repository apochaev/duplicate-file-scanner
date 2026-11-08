---
id: CCW-032
title: Trim CLAUDE.md to the rules every session needs
status: ready
type: docs
scope: [docs]
archetype: none
priority: high
lane: harness
depends_on: [CCW-026]
touches:
  - CLAUDE.md
  - server/CLAUDE.md
  - cli/CLAUDE.md
  - .claude/archetypes/component/archetype.md
  - .claude/archetypes/endpoint/archetype.md
  - .claude/archetypes/use-case/impl-guide.md
  - .claude/archetypes/pipeline-stage/impl-guide.md
  - .claude/agents/verifier.md
  - .claude/skills/tickets/SKILL.md
blocked_reason:
created: 2026-11-08
updated: 2026-11-08
completed:
---

# CCW-032: Trim CLAUDE.md to the rules every session needs

## Story

As the user paying for every request in every session and subagent
I want the root `CLAUDE.md` cut from about 33 KB to at most 18 KB, with subproject detail moved to `server/CLAUDE.md` and `cli/CLAUDE.md`, where only the work that needs it loads it
So that each request carries about 4k fewer tokens, multiplied across every pipeline phase

## Context

Filed 2026-11-08 by dfs-harness from Claude Code's usage report for the last 24 h: "Trim CLAUDE.md · 7k tokens per request" 19% (alongside "Give story subagents a cheaper model" 59%, CCW-031). Refined 2026-11-08 by the coordinator.

- **Measured (2026-11-08, after CCW-026 and CCW-033).** `CLAUDE.md` is 32,958 bytes. By section: "What this project is" 9,687 (almost all one paragraph on the `server` subproject and its API, which repeats one sentence: "registry's listener folds the core's `ScanEvent`s…" appears twice); "Tickets" 4,468; "Commands" 3,536; "Current state and open items" 3,506; the rest 0.7–2.3 KB each.
- **Why it grows.** The `component` and `endpoint` archetypes' Docs item (line 52 of each) tells every server ticket to update "the `CLAUDE.md` `server` entry".
- **Who reads it.** Every session and subagent loads the root file. Architect, test-author, implementer and verifier read it explicitly (step 2 of each prompt) for the architecture, dependency rules, design decisions, test conventions and safety invariants; those sections stay. Claude Code loads a nested `CLAUDE.md` (for example `server/CLAUDE.md`) only when a session or subagent reads a file in that folder.
- **Dangling pointers.** `.claude/agents/verifier.md:49`, `.claude/archetypes/use-case/impl-guide.md:28` and `.claude/archetypes/pipeline-stage/impl-guide.md:25` cite `CLAUDE.md`, "Implementing a ticket", a section that is in the `tickets` skill, not in `CLAUDE.md`.
- **Stale lines in "Current state".** "Built so far: the file walker…"; "the detection pipeline is DFS-001 to DFS-005"; "`ScanId`, `ScanStatus`, `CancelScan`, `GetScanStatus` and an empty `ScannerFactories` exist only so the exported packages are non-empty"; possibly the `allowEmptyShould(true)` note.

## Acceptance criteria

The full ID of each criterion is `CCW-032.AC-<n>`.

- **AC-1** The root `CLAUDE.md` is at most 18,000 bytes.
- **AC-2** `server/CLAUDE.md` holds the server and API detail moved from "What this project is" (security and session, the scan, result, overlaps and plan endpoints with their codes, the Angular app and its screens, the browser flows, the OpenAPI chain), the server and frontend commands and the frontend-development paragraph; the sentence "registry's listener folds the core's `ScanEvent`s" appears exactly once across the root and `server/CLAUDE.md`.
- **AC-3** The root's `server` entry is at most 3 lines and names `server/CLAUDE.md`; no `/api/` path appears in the root `CLAUDE.md`.
- **AC-4** `cli/CLAUDE.md` holds the `cli` output detail (JSONL line order, `schemaVersion` rules, `--format=text`, `--output`, the `excluded` count, progress modes); `schemaVersion` does not appear in the root `CLAUDE.md`.
- **AC-5** The root's Tickets section is at most 6 lines and states: `status` is authoritative, the two namespaces, only `ready` tickets are implemented, an implementing agent reads only its ticket and `CLAUDE.md` and stays within `touches`, the branch and worktree names, and that the `tickets` skill holds the rest; the words "coordinator", "lanes.md" and "launch line" do not appear in the root `CLAUDE.md`.
- **AC-6** The Docs item of the `component` and `endpoint` archetypes names `server/CLAUDE.md` as the file a server ticket updates.
- **AC-7** "Current state and open items" keeps the guards (the ArchUnit tests, `ModuleExportsTest`, the exported-package rule), the assumed defaults and the open items, and no longer holds a line checked stale against the code.
- **AC-8** The sections "`file-scanner` architecture", "Design decisions (settled)", "Safety invariants (must hold, and be tested)", "Provisional port sketch (revise as it is built)", "Spring-friendliness rules for the scanner", "Code and test conventions" and "Git commits" are byte-identical to their text before this ticket.
- **AC-9** `/tickets validate` reports an informational note, not a finding, when the root `CLAUDE.md` exceeds 20,000 bytes.
- **AC-10** Every citation of a named part of `CLAUDE.md` under `.claude/` (`CLAUDE.md, "<name>"` or `CLAUDE.md`'s "<name>") names text that exists in the root `CLAUDE.md`; the three "Implementing a ticket" citations name the `tickets` skill instead.

## Scenarios

```gherkin
Feature: CLAUDE.md holds only rules every session needs

  @AC-1
  Scenario: The root file is within the cap (mechanical)
    Given the root CLAUDE.md on the ticket's branch
    When `wc -c CLAUDE.md` runs
    Then it prints at most 18000

  @AC-2 @AC-3
  Scenario: Server detail lives in server/CLAUDE.md (mechanical)
    Given CLAUDE.md and server/CLAUDE.md
    When `grep -c "/api/" CLAUDE.md` and `grep -c "/api/scans/{id}/plans" server/CLAUDE.md` run
    Then the first prints 0 and the second at least 1
    And `grep -c "registry's listener folds" CLAUDE.md server/CLAUDE.md` totals 1
    And `grep -n "npmTest\|updateOpenApi\|e2eTest\|npm_run_start" CLAUDE.md` prints nothing, while server/CLAUDE.md has each

  @AC-3
  Scenario: The root's server entry is a pointer (read)
    Given the "What this project is" section of CLAUDE.md
    When it is read
    Then the `server` entry is at most 3 lines, says it listens on 127.0.0.1 behind a token cookie, and names server/CLAUDE.md

  @AC-4
  Scenario: cli detail lives in cli/CLAUDE.md (mechanical)
    Given CLAUDE.md and cli/CLAUDE.md
    When `grep -c "schemaVersion" CLAUDE.md cli/CLAUDE.md` runs
    Then CLAUDE.md counts 0 and cli/CLAUDE.md at least 1
    And cli/CLAUDE.md names `--format=text`, `--output`, `excluded` and `--progress`

  @AC-5
  Scenario: The Tickets section keeps the boundary rules only (mechanical and read)
    Given the "Tickets" section of CLAUDE.md
    When `grep -n -i "coordinator\|lanes.md\|launch line" CLAUDE.md` runs and the section is read
    Then grep prints nothing
    And the section has at most 6 lines naming status, the DFS and CCW namespaces, ready-only, ticket plus CLAUDE.md only, touches, `tickets/<ID>-<slug>`, the worktree path and the tickets skill

  @AC-6
  Scenario: Server tickets document in server/CLAUDE.md (mechanical)
    Given the component and endpoint archetypes
    When `grep -n "Docs" .claude/archetypes/component/archetype.md .claude/archetypes/endpoint/archetype.md` runs
    Then each Docs item names `server/CLAUDE.md`

  @AC-7
  Scenario: Current state keeps what is open (mechanical and read)
    Given the "Current state and open items" section
    When `grep -n "Built so far\|DFS-001 to DFS-005\|exist only so the exported packages" CLAUDE.md` runs
    Then it prints nothing
    And the section still names ArchitectureTest, ModuleExportsTest, the exported-package rule, the assumed defaults, the CLI framework question and the hash cache question

  @AC-8
  Scenario: Rule sections are untouched (mechanical)
    Given the root CLAUDE.md at the ticket's ready commit and on its branch
    When each of the seven named sections is extracted from both (from its heading to the next `## `) and compared with `diff`
    Then every diff is empty

  @AC-9
  Scenario: validate notes a grown CLAUDE.md (read)
    Given the tickets skill's `validate` mode
    When it is read
    Then it lists an informational note for a root CLAUDE.md over 20,000 bytes, among the notes that are not findings

  @AC-10
  Scenario: Citations into CLAUDE.md resolve (mechanical)
    Given `.claude/`
    When `grep -rn "CLAUDE.md\`*'*s* \"\|CLAUDE.md, \"" .claude/` runs and each cited name is searched in CLAUDE.md
    Then every cited name is found
    And verifier.md:49 and the use-case and pipeline-stage impl-guides cite the tickets skill's "Implementing a ticket"
```

## Scope

**Touches:**
- `CLAUDE.md`: the server entry, Commands, Tickets and "Current state" shortened
- `server/CLAUDE.md` (new): server and API detail, server and frontend commands, frontend development
- `cli/CLAUDE.md` (new): the `cli` output detail
- `.claude/archetypes/component/archetype.md`, `.claude/archetypes/endpoint/archetype.md`: the Docs item (line 52) names `server/CLAUDE.md`
- `.claude/archetypes/use-case/impl-guide.md`, `.claude/archetypes/pipeline-stage/impl-guide.md`, `.claude/agents/verifier.md`: the "Implementing a ticket" citation names the `tickets` skill
- `.claude/skills/tickets/SKILL.md`: the `validate` note for a root `CLAUDE.md` over 20,000 bytes

**Out of scope:**
- Changing any rule's meaning: this ticket moves and shortens, it does not decide.
- The design decisions, safety invariants, conventions and port sketch (AC-8).
- Models of the pipeline agents (CCW-031, CCW-030).
- `docs/design/ui.md`.

## Definition of done

- [ ] Every scenario above checked as marked (mechanical or read), with the evidence noted beside this item
- [ ] Every fact removed from the root `CLAUDE.md` is listed in `verification.md` with where it now lives, or with the evidence that it was stale
- [ ] Probe (decision 1): a subagent launched in the repo with no instructions about `server/CLAUDE.md` reads one file under `server/` and quotes a line only `server/CLAUDE.md` holds; result recorded in `verification.md`. If it cannot, stop and report before `review`: decision 1 relies on it
- [ ] `verification.md` opens with the `## Product owner review` section (`.claude/agents/verifier.md`)
- [ ] No files changed outside the paths listed under Touches

## Decisions

1. **Server and API detail moves to a new `server/CLAUDE.md` (decided 2026-11-08).** The "What this project is" paragraph on `server` (about 9 KB) moves there, split into bullets (endpoints, frontend, tests, the OpenAPI chain), its duplicate sentence dropped; the endpoint list stays in it. The root keeps about 3 lines: what `server` is, that it listens on 127.0.0.1 behind a token cookie, and that details are in `server/CLAUDE.md`. The `component` and `endpoint` archetypes' Docs item (line 52 of each), which tells every server ticket to update "the `CLAUDE.md` `server` entry", points to `server/CLAUDE.md`, so the root does not grow back. Relies on Claude Code loading a nested `CLAUDE.md` when a session or subagent reads a file under `server/`; a done-item probes it. Rejected: `docs/design/ui.md` (a decision record, read only when told), shortening in place (3-4 KB, keeps growing).
2. **Tickets section cut to about five lines (decided 2026-11-08).** It keeps: tickets live in `docs/tickets/` and `status` is authoritative; the two namespaces (`DFS` product, `CCW` harness, decided by the Story); only `ready` tickets are implemented, and an implementing agent reads only its ticket and `CLAUDE.md`, stays within `touches` and reports instead of editing outside them; branch `tickets/<ID>-<slug>` and worktree `../<repo-dir>-<ID>-<slug>`; use the `tickets` skill, which holds the rest (lifecycle, coordinator, sessions per ticket and launch lines, lanes, gates, `models`, context hygiene). The phase agents already name `touches` in their own prompts, and `@DisplayName` IDs are in the test-author prompt and the test guides. The "Git commits" section (ticket ID in the subject) is a separate section and stays. Rejected: a coordinator summary in the root (duplicates the skill, stale on the next workflow change), a bare pointer (drops the boundary rule).
3. **`cli` detail to a new `cli/CLAUDE.md`, server commands to `server/CLAUDE.md`, verified-stale lines dropped (decided 2026-11-08).** The first "Current state" bullet's `cli` detail (JSONL line order, `schemaVersion` rules, `--format=text`, `--output`, the `excluded` count, progress modes; about 1.5 KB) moves to `cli/CLAUDE.md`. The server and frontend commands (`bootRun`, `bootJar`, `installBootDist` of `server`, `npmTest`, `updateOpenApi`, `e2eTest`, `npm_run_start`) and the frontend-development paragraph move to `server/CLAUDE.md`; the root's Commands keeps build, test and the `:cli:run` examples. "Current state" keeps the guards (the ArchUnit tests, `ModuleExportsTest`, the exported-package rule), the assumed defaults and the open items. A line is dropped only after it is checked stale against the code ("Built so far: the file walker…", "the detection pipeline is DFS-001 to DFS-005", the first-slice types sentence; the `allowEmptyShould(true)` note only if the code no longer uses it); each drop is listed in `verification.md` with its evidence, and anything uncertain stays. "Provisional port sketch", the design decisions, safety invariants and conventions are not touched (moving them would change meaning). Rejected: dropping only stale lines (about 2.7 KB that `file-scanner` and harness work never use stays).
4. **Cap of 18,000 bytes, a `validate` note over 20,000 (decided 2026-11-08).** The estimate after decisions 1-3 is about 16 KB; AC-1 caps the root at 18,000 bytes, checked with `wc -c`. `/tickets validate` gains an informational note (not a finding) when the root exceeds 20,000 bytes, so regrowth is seen without blocking any ticket. Rejected: a cap without the note (regrowth unseen), a `story-gate` check failing every ticket's gate (product tickets fail over docs; Java and self-tests for a small risk), no number. **Post-close check:** "the next pipeline run needs nothing that was moved out" can only run after the merge, so it is no done-item here (CCW-026.L-1); the coordinator adds it to the next pipeline ticket's Definition of done together with CCW-033 decision 3's Sonnet check.
5. **Archetype `none` (decided 2026-11-08).** Implemented by hand in its own session, like CCW-026 and CCW-033: moves of documentation and one line in a skill. No archetype fits harness work.
6. **Dangling "Implementing a ticket" citations retargeted (decided 2026-11-08).** Found during refinement: three files cite a `CLAUDE.md` section that lives in the `tickets` skill. They are corrected here (AC-10) because this ticket is the one that settles what `CLAUDE.md` holds.

## Open questions

None.
