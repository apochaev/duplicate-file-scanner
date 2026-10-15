---
id: DFS-029
title: Write the report to a file with --output
status: ready
type: feature
scope: [cli]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: []
touches: [cli, CLAUDE.md]
blocked_reason:
created: 2026-10-14
updated: 2026-10-15
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

**Decisions (refinement, 2026-10-15):**

1. **`--output` replaces stdout, and stderr names the file.** With `--output=<file>`, the report (either `--format`) goes only to the file and nothing is written to stdout; after the `Done in …` line, stderr gets `Report written to <absolute path>`, in `--progress=live` and `--progress=log`, not with `--progress=off`. The absolute path matters because `:cli:run` starts in `cli/`, so a relative `--output` lands under `cli/` (CLAUDE.md, Commands). Rejected: replacing silently (no confirmation of where a relative path landed) and writing both (the console copy is mangled by the same PowerShell re-encoding, and a long report floods the terminal).
2. **An existing file is overwritten**, as `> file` does; the option replaces that redirect, so it keeps its meaning, and re-running a scan needs no new name. Rejected: refusing with exit 2 (every re-run needs a new name or a delete) and refusing unless `--force` (a second option that becomes habit).
3. **A bad output location is a usage error before scanning.** Before the scan starts, the program checks that `--output` is a valid path, that its parent folder exists and is a folder, that the path itself is not a folder, and that a file can be created in that parent (a test write that leaves nothing behind). Any failure is a usage error (exit 2, no scan); an invalid path syntax reads `not a valid path`, as for folder arguments (DFS-028). A write that still fails after the scan (disk full, the folder removed meanwhile) exits 1 with the error on stderr. The real Media scan took 3:18:40, so a problem found only at the end costs hours. Rejected: checking only that the folder exists (a read-only folder is found after the scan) and checking nothing up front (a typo costs the whole scan).
4. **The report goes through a temporary file renamed at the end.** Before the scan, a temporary file is created in the target's folder (for example `report.txt.part`); this is decision 3's test write. When the scan completes, the report is written into it and it is moved over the target (`ATOMIC_MOVE`, `REPLACE_EXISTING`). If the scan or the write fails, or the run is interrupted with Ctrl+C (the JVM's shutdown hooks run), the temporary file is deleted and an existing target is left untouched. Only a hard kill (Task Manager, power loss) can leave a stray temporary file. Rejected: writing straight to the target (a failed write leaves a truncated report, which for `--format=text` has no end marker, after the old one is already gone).
5. **`CLAUDE.md` documents both ways.** The Commands block keeps the `> out.jsonl` line (correct in Git Bash and cmd) and gains one with `--output=` and an absolute path; the paragraph below it gains a sentence: in PowerShell use `--output` (an absolute path) instead of `>`, which re-encodes the report to UTF-16. The "Current state" paragraph mentions `--output`. This is documentation, so it is a Definition of done item rather than a criterion. Rejected: replacing the `>` example (hides a redirect that is still correct elsewhere) and leaving `CLAUDE.md` alone (the trap that cost the Media scan a redo stays undocumented).
6. **Archetype `none`**: implemented by hand, like the earlier CLI-option tickets (DFS-023, DFS-024, DFS-025, DFS-028). The change sits in `ScanCommand`, its tests and `CLAUDE.md`; `wiring`'s checklist (factories, injection by type) would mostly not apply. Every failure path (AC-3 to AC-5) gets its own test. Rejected: `wiring` through `/story run` (several agent runs and two gates for an option and a file write).
7. **`--output` follows the existing options' conventions**: given more than once, or with an empty value (`--output=`), it is a usage error, as for `--format` and `--progress`.

## Acceptance criteria

The full ID of each criterion is `DFS-029.AC-<n>`.

- **AC-1** With `--output=<file>`, the report is written to that file as UTF-8, in either `--format`, and nothing is written to stdout; stderr ends with `Report written to <absolute path>` unless `--progress=off` (decision 1).
- **AC-2** When the `--output` file already exists, a completed scan replaces its content with the new report (decision 2).
- **AC-3** An `--output` that is not a valid path, whose parent folder does not exist or is not a folder, that names a folder, or where no file can be created is a usage error: exit 2 before any scanning, with no file left behind (decision 3).
- **AC-4** When writing the report fails after the scan, the program exits 1 with the error on stderr (decision 3).
- **AC-5** The `--output` file only ever holds a complete report: when the scan fails, or writing the report fails, an existing file keeps its earlier content, a missing one is not created, and no temporary file is left in its folder (decision 4).
- **AC-6** `--output` given more than once, or with an empty value, is a usage error: exit 2 before any scanning (decision 7).

## Scenarios

```gherkin
Feature: Report written to a file

  Background:
    Given a folder "<tmp>/Café" holding "a.txt" and "b.txt" with the same 2,000 bytes
    And a separate empty folder "<tmp>/out"

  @AC-1
  Scenario: The text report goes to the file as UTF-8 and not to stdout
    When the scan runs with "<tmp>/Café --format=text --progress=log --output=<tmp>/out/report.txt"
    Then the exit code is 0
    And stdout is empty
    And "<tmp>/out/report.txt" holds, as UTF-8 bytes, the same report the same scan writes to stdout without --output, including the path "<tmp>/Café/a.txt"
    And the last line on stderr is "Report written to <absolute path of <tmp>/out/report.txt>", after the "Done in" line

  @AC-1
  Scenario: The JSONL report goes to the file
    When the scan runs with "<tmp>/Café --progress=log --output=<tmp>/out/report.jsonl"
    Then the exit code is 0
    And stdout is empty
    And "<tmp>/out/report.jsonl" holds one "set" line naming both files, then one "summary" line

  @AC-1
  Scenario: No confirmation line with progress off
    When the scan runs with "<tmp>/Café --progress=off --output=<tmp>/out/report.jsonl"
    Then the exit code is 0
    And stderr is empty
    And "<tmp>/out/report.jsonl" holds the report

  @AC-2
  Scenario: An existing file is replaced
    Given "<tmp>/out/report.txt" holds "old report"
    When the scan runs with "<tmp>/Café --format=text --output=<tmp>/out/report.txt"
    Then the exit code is 0
    And "<tmp>/out/report.txt" holds the new report and not "old report"

  @AC-3
  Scenario Outline: A bad output location is a usage error before scanning
    When the scan runs with "<tmp>/Café --progress=log --output=<output>"
    Then the exit code is 2
    And stderr starts with "error: " and names the problem, followed by the usage line
    And stderr holds no progress line (nothing was scanned)
    And no file is created in "<tmp>/out"

    Examples:
      | output                         | problem                    |
      | <tmp>/out/missing/report.txt   | the folder does not exist  |
      | <tmp>/out/file.txt/report.txt  | the parent is a file       |
      | <tmp>/out                      | the path is a folder       |
      | <tmp>/out/a<b.txt              | not a valid path (Windows) |

    (For the "parent is a file" row, "<tmp>/out/file.txt" exists beforehand as a file.)

  @AC-3
  Scenario: A folder where no file can be created is a usage error before scanning
    Given "<tmp>/out" is made read-only for the current user (skipped with Assumptions where the platform does not allow it)
    When the scan runs with "<tmp>/Café --output=<tmp>/out/report.txt"
    Then the exit code is 2
    And nothing was scanned
    And "<tmp>/out" is still empty

  @AC-4 @AC-5
  Scenario: Writing fails after the scan
    Given "<tmp>/out/report.txt" holds "old report"
    And writing the report fails once the scan has completed (through a test seam, for example the report writer throwing an IOException)
    When the scan runs with "<tmp>/Café --format=text --output=<tmp>/out/report.txt"
    Then the exit code is 1
    And stderr ends with an "error: " line naming "<tmp>/out/report.txt" and the cause
    And "<tmp>/out/report.txt" still holds "old report"
    And "<tmp>/out" holds no other file

  @AC-5
  Scenario: A refused scan leaves no file
    When the scan runs with "<tmp>/does-not-exist --output=<tmp>/out/report.txt"
    Then the exit code is 1
    And "<tmp>/out" is still empty

  @AC-6
  Scenario Outline: --output given twice or empty
    When the scan runs with "<tmp>/Café <options>"
    Then the exit code is 2
    And stderr starts with "error: <message>" followed by the usage line
    And nothing was scanned

    Examples:
      | options                                         | message                         |
      | --output=<tmp>/out/a.txt --output=<tmp>/out/b.txt | --output given more than once |
      | --output=                                       | --output needs a file path      |
```

## Scope

**Touches:**
- `cli`: `ScanCommand` (the option, `USAGE_LINE`) and its tests
- `CLAUDE.md`: the Commands section and the "Current state" paragraph (decision 5)

**Out of scope:**
- The three output bugs (DFS-028).
- Any change to the report's content or the JSONL schema.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `CLAUDE.md` has the `--output` example, the PowerShell sentence and the "Current state" mention (decision 5)
- [ ] A real run from PowerShell 5.1 with `--format=text --output=<file>` over a folder with a non-ASCII name produces a file that is byte-identical to the same run's stdout redirected from cmd

## Open questions

None.
