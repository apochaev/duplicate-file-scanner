---
id: DFS-029
title: Write the report to a file with --output
status: draft
type: feature
scope: [cli]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [cli]
blocked_reason:
created: 2026-10-14
updated: 2026-10-14
completed:
---

# DFS-029: Write the report to a file with --output

## Story

As a user scanning large folders from PowerShell
I want to write the report straight to a file
So that a long scan's result arrives intact as UTF-8, without the shell re-encoding it

## Context

Split from DFS-028 during its refinement (2026-10-14, DFS-028 decision 1).

In Windows PowerShell 5.1, `> file` re-encodes a native program's stdout through the console code page and writes UTF-16 LE, so the UTF-8 report (CLAUDE.md, Commands: "Stdout is always UTF-8") arrives damaged: non-ASCII paths are mangled and the file is twice the size. The real Media scan's report (`C:/Media D:/Media E:/Media F:/Media`, 3:18:40, 2026-10-13) had to be redone by hand (`media-report.txt`, then `media-report.utf8.txt`, both left untracked in the repo root). A `--output=<file>` option would make the program write the report itself, as UTF-8, in either `--format`.

Today the options are parsed by hand in `ScanCommand.run` (`--min-size=`, `--format=`, `--progress=`; `USAGE_LINE`), progress and errors go to stderr, and exit codes are 0 completed, 1 refused or failed scan, 2 usage error (DFS-020 decision 6).

## Acceptance criteria

The full ID of each criterion is `DFS-029.AC-<n>`.

- **AC-1** <placeholder: `--output=<file>` writes the report to that file as UTF-8, in either format>

## Scenarios

```gherkin
Feature: Report written to a file

  @AC-1
  Scenario: <placeholder>
    Given <placeholder>
    When <placeholder>
    Then <placeholder>
```

## Scope

**Touches:**
- `cli`: `ScanCommand` (the option, `USAGE_LINE`) and its tests; `CLAUDE.md`'s Commands section if the recommended invocation changes

**Out of scope:**
- The three output bugs (DFS-028).
- Any change to the report's content or the JSONL schema.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] A real run from PowerShell 5.1 with `--format=text --output=<file>` over a folder with a non-ASCII name produces a file that is byte-identical to the same run's stdout redirected from cmd

## Open questions

1. Does `--output` replace stdout entirely, or also print the report?
2. What if the file exists: overwrite, refuse, or a `--force`?
3. What if the parent folder is missing or the file is not writable: a usage error (2) before scanning, or a failure (1) after?
4. Is the file written only when the scan completes (for example through a temporary file renamed at the end), so a failed or interrupted scan never leaves a partial report?
5. Does `CLAUDE.md`'s Commands section switch its examples from `> out.jsonl` to `--output=`? If so, `CLAUDE.md` joins `touches`.
6. Archetype: `wiring` (the composition root, `cli`), or `none`?
