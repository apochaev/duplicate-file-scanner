---
id: DFS-021
title: Preview a deletion plan from the CLI
status: draft
type: feature
scope: [cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-020]
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

This ticket is likely the composition-root half (`archetype: wiring`, its first live `/story run`), with the plan logic itself in a separate `use-case` ticket in `file-scanner`; refinement decides the split.

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
- `us.pochaev.dfs.cli`: the preview command, its arguments and output (to be settled in refinement; `file-scanner` changes probably belong to a separate ticket)

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

- Split: a separate `use-case` ticket in `file-scanner` for `PreviewDeletionPlan`, `DeletionPlan` and the rule model (and whether `LocationOverlap` comes first), with this ticket as the `wiring` composition root depending on it? Or one ticket?
- Input: does a preview scan the folders in the same invocation (scan, then plan), or read an earlier scan's JSONL output? The scan state is in-process only today.
- Rule language: which rules first ("delete every file under B that has a copy under A"; "keep the oldest"; "keep the copy under A"), how they are given on the command line, and whether several rules combine.
- Last copy: what the preview shows for a set whose rules would remove every copy (refuse the plan, keep one by a tie-break, or report the conflict), validated across the whole plan.
- Hard links: how a copy with several links is shown and counted (deleting one link frees nothing while another remains).
- Output: JSONL like the scan (a `delete` line per file with its keeper, a `conflict` line, a `summary` with bytes freed), and exit codes.
- Archetype: `wiring` for the CLI half (its first live run), and `use-case` for a `file-scanner` half if split.
