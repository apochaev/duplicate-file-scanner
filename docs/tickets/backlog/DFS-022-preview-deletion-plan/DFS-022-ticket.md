---
id: DFS-022
title: Preview a deletion plan
status: draft
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-019]
touches: [us.pochaev.dfs.scanner.domain.usecases.query, us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.domain.factory]
blocked_reason:
created: 2026-10-13
updated: 2026-10-13
completed:
---

# DFS-022: Preview a deletion plan

## Story

As a user of the duplicate file scanner
I want to apply deletion rules to a completed scan and get back the plan they produce: which files would be removed, which copy each one keeps, and where rules conflict
So that I can review a bulk delete before anything touches the disk, with the guarantee that no plan ever removes the last copy of a file

## Context

Split from DFS-021 (its decision 1): this ticket holds the plan logic in `file-scanner`, run through the pipeline as `use-case`; DFS-021 is the CLI command (`wiring`) that depends on it.

CLAUDE.md applies directly:

- **Never delete the last copy.** Every `DeletionPlan` must leave at least one verified surviving copy per `DuplicateSet`. Rules can conflict (folder1's copy may itself be marked for deletion by another rule), so the plan is validated as a whole, not rule by rule.
- **Preview is a pure query; execution is a separate command.** `PreviewDeletionPlan` never touches disk state. Executing (quarantine, decision 2) and the byte-for-byte re-verification before it (decision 4) are later tickets.
- **Hard links are not duplicates.** A `DuplicateSet` already groups paths into `PhysicalCopy`s (DFS-017): deleting one link of a copy frees no space while another link of that copy remains.
- **Location model** (decision 1): rules are stated by source location, e.g. "delete every file in folder2 that has a confirmed duplicate in folder1", where a folder also covers its subfolders. `LocationOverlap(A, B)` is the second view of a result; it is not built yet.
- Provisional port sketch: `PreviewDeletionPlan` is a query (`domain.usecases.query`); `DeletionPlan` is the type it returns.

What exists: a completed scan's `ScanResult(sets, skipped)` through `GetScanResult` (DFS-018), `DuplicateSet(size, digest, files, copies)`, `PhysicalCopy(links)`, `FileEntry(path, size, lastModified)`, `Result`/`ErrorCode` for expected failures, and `ScannerFactories.scanUseCases(Executor)` returning `ScanUseCases` (DFS-019), which a new port would join.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-022.AC-<n>`.

## Scenarios

```gherkin
Feature: Previewing a deletion plan

  Scenario: <placeholder; scenarios are written during refinement>
```

## Scope

**Touches:**
- `domain.usecases.query`: the `PreviewDeletionPlan` port
- `domain.model`: `DeletionPlan`, the rule types, any conflict or error types
- `application`: the service that builds and validates a plan
- `domain.factory`: exposing the new port (for example through `ScanUseCases` or a new factory method)

**Out of scope:**
- Executing a plan: quarantine, permanent delete, undo, purge (CLAUDE.md decision 2)
- Re-verification at execution time (decision 4)
- The CLI command (DFS-021)
- `LocationOverlap` as its own query, unless refinement decides the rules need it

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches

## Open questions

- Input: what the query takes: the `ScanId` of a completed scan (in-process state, as `GetScanResult` does), or a `ScanResult` value (so a CLI could also preview a scan read back from JSONL; see DFS-021)?
- Rule model: which rules first ("delete every file under B that has a copy under A"; "keep the copy under A"; "keep the oldest or newest"), how a folder matches (path prefix of real paths, covering subfolders), and how several rules combine.
- Last copy: when the rules would remove every copy of a set, does the plan refuse that set (reporting a conflict), refuse the whole plan, or keep one copy by a tie-break? What does the result carry so the caller can show it?
- Keeper: for each file to delete, which surviving copy is its recorded keeper (for decision 4's later byte compare and the quarantine manifest)?
- Hard links: is a deletion per physical copy (all its links) or per path, and how are bytes freed counted when only some links of a copy are covered by a rule?
- Plan shape: the fields of `DeletionPlan` (deletions with keeper, conflicts, bytes freed), ordering, and whether it needs an ID now (execution is later).
- Exposure: add `PreviewDeletionPlan` to `ScanUseCases` (one more component) or a separate factory method; the `use-case` checklist items (unknown or stale IDs, results as values, exported surface).
