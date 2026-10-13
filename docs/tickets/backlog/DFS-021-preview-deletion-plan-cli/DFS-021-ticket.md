---
id: DFS-021
title: Preview a deletion plan from the CLI
status: draft
type: feature
scope: [cli]
archetype: wiring
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-020, DFS-022]
touches: [us.pochaev.dfs.cli]
blocked_reason:
created: 2026-10-13
updated: 2026-10-13
completed:
---

# DFS-021: Preview a deletion plan from the CLI

## Story

As a user of the duplicate file scanner
I want to state a rule such as "delete every file in folder2 that has a confirmed duplicate in folder1" and see exactly which files it would remove and which copy each one keeps
So that I can check a bulk delete before anything touches the disk, and trust it never removes the last copy

## Context

This is the first step of the delete workflow, the reason the tool exists (CLAUDE.md, "What this project is"). It is a preview only: CLAUDE.md's safety invariant "Preview is a pure query; execution is a separate command" means `PreviewDeletionPlan` never touches disk state, and executing a plan (quarantine, decision 2; byte-compare re-verification, decision 4) is a later ticket.

What exists today:

- The CLI (DFS-020) scans folders and writes JSON Lines: `set` lines (`size`, `digest`, `copies` as lists of link paths, hard links grouped as one copy), `skipped` lines and a `summary`. Arguments are parsed by hand in `ScanCommand`; exit codes 0, 1, 2. Scan state lives only in the process (`InMemoryScanRepository`), so a one-shot CLI cannot preview a scan made by an earlier invocation.
- `file-scanner` has no deletion types yet. The provisional port sketch names `PreviewDeletionPlan` (query) and `ExecuteDeletionPlan` (command), and a `DeletionPlan` that must leave at least one verified surviving copy per `DuplicateSet`, validated as a whole, since rules can conflict (CLAUDE.md, "Never delete the last copy"). `LocationOverlap(A, B)` (decision 1) is not built either.
- Safety invariants that apply: never delete the last copy (a plan whose rules would remove every copy of a set must say so, not silently pick a keeper); hard links are not duplicates (deleting one link of a copy frees no space while another link remains, DFS-017); preview is a pure query.

**Decisions (refinement, 2026-10-13):**

1. **Split: this ticket is the CLI half, `archetype: wiring`; the plan logic is DFS-022 (`use-case`).** DFS-022 builds `PreviewDeletionPlan`, `DeletionPlan`, the rule model and the whole-plan "never delete the last copy" validation in `file-scanner`, tested at its own level with fakes. This ticket wires that port into `cli` (bean, arguments, output, exit codes) and shows the safety result comes through. Each half gets the archetype built for it, both run through `/story run`, and this ticket is `wiring`'s first live run (and the first live test of CCW-014's wrapped-stub red gate). Rule language, conflict handling and hard-link counting are decided in DFS-022 and only consumed here.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-021.AC-<n>`.

## Scenarios

```gherkin
Feature: Previewing a deletion plan from the CLI

  Scenario: <placeholder; scenarios are written during refinement>
```

## Scope

**Touches:**
- `us.pochaev.dfs.cli`: the preview command, its arguments and output, and the bean for `PreviewDeletionPlan` (`file-scanner` changes are DFS-022)

**Out of scope:**
- Executing a plan: quarantining or deleting files, undo and purge (CLAUDE.md decision 2; later tickets)
- Byte-for-byte re-verification at execution time (decision 4; part of execution)
- An interactive shell

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check, e.g. a real preview on real folders, with no file on disk changed>

## Open questions

- Input: does a preview scan the folders in the same invocation (scan, then plan), or read an earlier scan's JSONL output? The scan state is in-process only today; DFS-022's input question (a `ScanId` or a `ScanResult`) decides what is possible.
- Command shape: a separate command (`preview ...`) next to the scan, or options on the existing one; this is the CLI's second command, which touches DFS-020 decision 2 (arguments parsed by hand, no framework).
- Rule arguments: how DFS-022's rules are given on the command line.
- Output: JSONL like the scan (a `delete` line per file with its keeper, a `conflict` line, a `summary` with bytes freed), and exit codes (does a plan with conflicts exit non-zero?).
- The `wiring` checklist items 7-11 (streams, exit codes, waiting, interruption, arguments) and item 2 for the new port bean.
